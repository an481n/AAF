/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cmd.perm;

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

import aaf.v2_0.Pkey;
import aaf.v2_0.RolePermRequest;

/**
 * 
 *
 */
public class Grant extends Cmd {
	private final static String[] options = {"grant","ungrant","setTo"};

	public Grant(Perm parent) {
		super(parent,null,
			new Param(optionsToString(options),true),
			new Param("type",true),
			new Param("instance",true),
			new Param("action",true),
			new Param("role[,role]* (!REQ S)",false)
			); 
	}

	@Override
	public int _exec(final int index, final String ... args) throws CadiException, APIException, LocatorException {
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				int idx = index;
				String action = args[idx++];
				int option = whichOption(options, action);
		
				RolePermRequest rpr = new RolePermRequest();
				Pkey pk = new Pkey();
				pk.setType(args[idx++]);
				pk.setInstance(args[idx++]);
				pk.setAction(args[idx++]);
				rpr.setPerm(pk);
				setStartEnd(rpr);
				
				Future<RolePermRequest> frpr = null;
		
				if (option != 2) {
					String[] roles = args[idx++].split(",");
					String strA,strB;
					for(String role : roles) {
						rpr.setRole(role);
						if(option==0) {
							// You can request to Grant Permission to a Role
							setQueryParamsOn(client);
							frpr = client.create(
									"/authz/role/perm", 
									getDF(RolePermRequest.class),
									rpr
									);
							strA = "Granted Permission [";
							strB = "] to Role [";
						} else {
							// You can request to UnGrant Permission to a Role
							setQueryParamsOn(client);
							frpr = client.delete(
									"/authz/role/" + role + "/perm", 
									getDF(RolePermRequest.class),
									rpr
									);
							strA = "UnGranted Permission [";
							strB = "] from Role [";
						}
						if(frpr.get(AAFcli.timeout())) {
							pw().println(strA + pk.getType() + '|' + pk.getInstance() + '|' + pk.getAction() 
									+ strB + role +']');
						} else {
							if (frpr.code()==202) {
								pw().print("Permission Role ");
								pw().print(option==0?"Granted":"Ungranted");
								pw().println(" Accepted, but requires Approvals before actualizing");
							} else {
								error(frpr);
								idx=Integer.MAX_VALUE;
							}			
						}
					}
				} else {
					String allRoles = "";
					if (idx < args.length) 
						allRoles = args[idx++];
						
					rpr.setRole(allRoles);
					frpr = client.update(
							"/authz/role/perm", 
							getDF(RolePermRequest.class), 
							rpr);
					if(frpr.get(AAFcli.timeout())) {
						pw().println("Set Permission's Roles to [" + allRoles + "]");
					} else {
						error(frpr);
					}			
				} 
				return frpr==null?0:frpr.code();
			}
		});
	}

	@Override
	public void detailedHelp(int indent, StringBuilder sb) {
		detailLine(sb,indent,"Grant a Permission to a Role or Roles  OR");
		detailLine(sb,indent,"Ungrant a Permission from a Role or Roles  OR");
		detailLine(sb,indent,"Set a Permission's roles to roles supplied.");
		detailLine(sb,indent+4,"WARNING: Roles supplied with setTo will be the ONLY roles attached to this permission");
		detailLine(sb,indent+8,"If no roles are supplied, permission's roles are reset.");
		detailLine(sb,indent,"see Create for definitions of type,instance and action");
		api(sb,indent,HttpMethods.POST,"authz/role/perm",RolePermRequest.class,true);
		api(sb,indent,HttpMethods.DELETE,"authz/role/<role>/perm",RolePermRequest.class,false);
		api(sb,indent,HttpMethods.PUT,"authz/role/perm",RolePermRequest.class,false);

	}

}
