/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cmd.user;

import com.att.aft.dme2.internal.jetty.http.HttpMethods;
import com.att.cadi.CadiException;
import com.att.cadi.LocatorException;
import com.att.cadi.client.Future;
import com.att.cadi.client.Rcli;
import com.att.cadi.client.Retryable;
import com.att.cmd.AAFcli;
import com.att.cmd.Cmd;
import com.att.cmd.Param;
import com.att.inno.env.APIException;

import aaf.v2_0.CredRequest;

public class Cred extends Cmd {
		private static final String CRED_PATH = "/authn/cred";
		private static final String[] options = {"add","del","reset","extend"/*,"clean"*/};
//		private Clean clean;
		public Cred(User parent) {
			super(parent,"cred",
					new Param(optionsToString(options),true),
					new Param("id",true),
					new Param("password (! D|E)",false),
					new Param("entry# (if multi)",false)
			);
//			clean = new Clean(this);
		}

		@Override
		public int _exec(int _idx, final String ... args) throws CadiException, APIException, LocatorException { 
		    int idx = _idx;
			String key = args[idx++];
			final int option = whichOption(options,key);

			final CredRequest cr = new CredRequest();
			cr.setId(args[idx++]);
			if(option!=1 && option!=3) {
				if(idx>=args.length) throw new CadiException("Password Required");
				cr.setPassword(args[idx++]);
			}
			if(args.length>idx)
				cr.setEntry(args[idx++]);
			
			// Set Start/End commands
			setStartEnd(cr);
//			final int cleanIDX = _idx+1;
			Integer ret = same(new Retryable<Integer>() {
				@Override
				public Integer code(Rcli<?> client) throws CadiException, APIException {
					Future<CredRequest> fp=null;
					String verb =null;
					switch(option) {
						case 0:
							fp = client.create(
								CRED_PATH, 
								getDF(CredRequest.class), 
								cr
								);
							verb = "Added Credential [";
							break;
						case 1:
//							if(aafcli.addForce())cr.setForce("TRUE");
							setQueryParamsOn(client);
							fp = client.delete(CRED_PATH,
								getDF(CredRequest.class),
								cr
								);
							verb = "Deleted Credential [";
							break;
						case 2:
							fp = client.update(
								CRED_PATH,
								getDF(CredRequest.class),
								cr
								);
							verb = "Reset Credential [";
							break;
						case 3:
							fp = client.update(
								CRED_PATH+"/5",
								getDF(CredRequest.class),
								cr
								);
							verb = "Extended Credential [";
							break;
//						case 4:
//							return clean.exec(cleanIDX, args);
					}
					if(fp.get(AAFcli.timeout())) {
						pw().print(verb);
						pw().print(cr.getId());
						pw().println(']');
					} else if(fp.code()==202) {
							pw().println("Credential Action Accepted, but requires Approvals before actualizing");
					} else if(fp.code()==406 && option==1) {
							pw().println("You cannot delete this Credential");
					} else {
						error(fp);
					}
					return fp.code();
				}
			});
			if(ret==null)ret = -1;
			return ret;
		}
		
		@Override
		public void detailedHelp(int _indent, StringBuilder sb) {
		        int indent = _indent;
			detailLine(sb,indent,"Add, Delete or Reset Credential");
			indent+=2;
			detailLine(sb,indent,"id       - the ID to create/delete/reset within AAF");
			detailLine(sb,indent,"password - Company Policy compliant Password (not required for Delete)");
			detailLine(sb,indent,"entry    - selected option when deleting/resetting a cred with multiple entries");
			sb.append('\n');
			detailLine(sb,indent,"The Domain can be related to any Namespace you have access to *");
			detailLine(sb,indent,"The Domain is in reverse order of Namespace, i.e. ");
			detailLine(sb,indent+2,"NS of com.att.myapp can create user of XY1234@myapp.att.com");
			sb.append('\n');
			detailLine(sb,indent,"NOTE: AAF does support multiple creds with the same ID. Check with your org if you");
			detailLine(sb,indent+2,"have this implemented. (For example, this is implemented for MechIDs at AT&T)");
			sb.append('\n');			
			detailLine(sb,indent,"Delegates can be listed by the User or by the Delegate");
			indent-=2;
			api(sb,indent,HttpMethods.POST,"authn/cred",CredRequest.class,true);
			api(sb,indent,HttpMethods.DELETE,"authn/cred",CredRequest.class,false);
			api(sb,indent,HttpMethods.PUT,"authn/cred",CredRequest.class,false);
		}
}
