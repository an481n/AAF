/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cmd.perm;

import com.att.aft.dme2.internal.jetty.http.HttpMethods;
import com.att.aft.dme2.internal.jetty.http.HttpStatus;
import com.att.cadi.CadiException;
import com.att.cadi.LocatorException;
import com.att.cadi.client.Future;
import com.att.cadi.client.Rcli;
import com.att.cadi.client.Retryable;
import com.att.cmd.AAFcli;
import com.att.cmd.Cmd;
import com.att.cmd.Param;
import com.att.inno.env.APIException;

import aaf.v2_0.PermRequest;
import aaf.v2_0.RoleRequest;

/**
 * 
 *
 */
public class Create extends Cmd {
	public Create(Perm parent) {
		super(parent,"create", 
				new Param("type",true), 
				new Param("instance",true),
				new Param("action", true),
				new Param("role[,role]* (to Grant to)", false)
				);
	}

	@Override
	public int _exec(final int index, final String ... args) throws CadiException, APIException, LocatorException {
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				int idx = index;
				final PermRequest pr = new PermRequest();  
				pr.setType(args[idx++]);
				pr.setInstance(args[idx++]);
				pr.setAction(args[idx++]);
				String roleCommas = (args.length>idx)?args[idx++]:null;
				String[] roles = roleCommas==null?null:roleCommas.split("\\s*,\\s*");
				boolean force = aafcli.forceString()!=null;
				int rv;
				
				if(roles!=null && force) { // Make sure Roles are Created
					RoleRequest rr = new RoleRequest();
					for(String role : roles) {
						rr.setName(role);;
						Future<RoleRequest> fr = client.create(
							"/authz/role",
							getDF(RoleRequest.class),
							rr
							);
						fr.get(AAFcli.timeout());
						switch(fr.code()){
							case 201:
								pw().println("Created Role [" + role + ']');
								break;
							case 409:
								break;
							default: 
								pw().println("Role [" + role + "] does not exist, and cannot be created.");
								return HttpStatus.PARTIAL_CONTENT_206;
						}
					}
				}

				// Set Start/End commands
				setStartEnd(pr);
				setQueryParamsOn(client);
				Future<PermRequest> fp = client.create(
						"/authz/perm",
						getDF(PermRequest.class),
						pr
						);
				if(fp.get(AAFcli.timeout())) {
					rv = fp.code();
					pw().println("Created Permission");
					if(roles!=null) {
						if(aafcli.forceString()!=null) { // Make sure Roles are Created
							RoleRequest rr = new RoleRequest();
							for(String role : roles) {
								rr.setName(role);;
								Future<RoleRequest> fr = client.create(
									"/authz/role",
									getDF(RoleRequest.class),
									rr
									);
								fr.get(AAFcli.timeout());
								switch(fr.code()){
									case 201:
									case 409:break;
									default: 
										
								}
							}
						}
						
						try {
							if(201!=(rv=((Perm)parent)._exec(0, 
									new String[] {"grant",pr.getType(),pr.getInstance(),pr.getAction(),roleCommas}))) {
								rv = HttpStatus.PARTIAL_CONTENT_206;
							}
						} catch (LocatorException e) {
							throw new CadiException(e);
						}
					}
				} else {
					rv = fp.code();
					if(rv==409 && force) {
						rv = 201;
					} else if(rv==202) {
						pw().println("Permission Creation Accepted, but requires Approvals before actualizing");
						if (roles!=null)
							pw().println("You need to grant the roles after approval.");
					} else {
						error(fp);
					}
				}
				return rv;
			}
		});
	}
	
	@Override
	public void detailedHelp(int _indent, StringBuilder sb) {
	        int indent = _indent;
		detailLine(sb,indent,"Create a Permission with:");
		detailLine(sb,indent+=2,"type     - A Namespace qualified identifier identifying the kind of");
		detailLine(sb,indent+11,"resource to be protected");
		detailLine(sb,indent,"instance - A name that distinguishes a particular instance of resource");
		detailLine(sb,indent,"action   - What kind of action is allowed");
		detailLine(sb,indent,"role(s)  - Perms granted to these Comma separated Role(s)");
		detailLine(sb,indent+11,"Nonexistent role(s) will be created, if in same namespace");
		sb.append('\n');
		detailLine(sb,indent+2,"Note: Instance and Action can be a an '*' (enter \\\\* on Unix Shell)");
		api(sb,indent,HttpMethods.POST,"authz/perm",PermRequest.class,true);
	}

}
