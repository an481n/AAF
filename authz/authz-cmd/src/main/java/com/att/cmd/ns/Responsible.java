/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cmd.ns;

import com.att.aft.dme2.internal.jetty.http.HttpMethods;
import com.att.cadi.CadiException;
import com.att.cadi.LocatorException;
import com.att.cadi.client.Future;
import com.att.cadi.client.Rcli;
import com.att.cadi.client.Retryable;
import com.att.cmd.AAFcli;
import com.att.cmd.BaseCmd;
import com.att.cmd.Param;
import com.att.inno.env.APIException;

public class Responsible extends BaseCmd<NS> {
	private final static String[] options = {"add","del"};

	public Responsible(NS ns) throws APIException {
		super(ns,"responsible",
				new Param(optionsToString(options),true),
				new Param("name",true),
				new Param("id[,id]*",true)
		);
	}

	@Override
	public int _exec(int _idx, final String ... args) throws CadiException, APIException, LocatorException {
	    	int idx = _idx;

		final int option = whichOption(options, args[idx++]);
		final String ns = args[idx++];
		final String ids[] = args[idx++].split(",");
		final String realm = getOrgRealm();
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				Future<Void> fp=null;
				for(String id : ids) {
					if (id.indexOf('@') < 0 && realm != null) id += '@' + realm;
					String verb;
					switch(option) {
						case 0: 
							fp = client.create("/authz/ns/"+ns+"/responsible/"+id,Void.class);
							verb = " is now ";
							break;
						case 1: 
							fp = client.delete("/authz/ns/"+ns+"/responsible/"+id,Void.class);
							verb = " is no longer ";
							break;
						default:
							throw new CadiException("Bad Argument");
					};
				
					if(fp.get(AAFcli.timeout())) {
						pw().append(id);
						pw().append(verb);
						pw().append("responsible for ");
						pw().println(ns);
					} else {
						error(fp);
						return fp.code();
					}
				}
				return fp==null?500:fp.code();
			}
		});
	}

	@Override
	public void detailedHelp(int _indent, StringBuilder sb) {
	    	int indent = _indent;
		detailLine(sb,indent,"Add or Delete Responsible person to/from Namespace");
		indent+=2;
		detailLine(sb,indent,"Responsible persons receive Notifications and approve Requests ");
		detailLine(sb,indent,"regarding this Namespace. Companies have Policies as to who may");
		detailLine(sb,indent,"take on this responsibility");

		indent+=2;
		detailLine(sb,indent,"name - Name of Namespace");
		detailLine(sb,indent,"id   - Credential of Person(s) to be made responsible");
		sb.append('\n');
		detailLine(sb,indent,"aafcli will call API on each ID presented.");
		indent-=4;
		api(sb,indent,HttpMethods.POST,"authz/ns/<ns>/responsible/<id>",Void.class,true);
		api(sb,indent,HttpMethods.DELETE,"authz/ns/<ns>/responsible/<id>",Void.class,false);
	}


}
