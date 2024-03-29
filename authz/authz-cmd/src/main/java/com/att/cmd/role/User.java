/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cmd.role;

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

import aaf.v2_0.UserRoleRequest;

/**
 * p
 *
 */
public class User extends Cmd {
	private final static String[] options = {"add","del","setTo","extend"};
	public User(Role parent) {
		super(parent,"user", 
				new Param(optionsToString(options),true),
				new Param("role",true),
				new Param("id[,id]* (not required for setTo)",false)); 
	}

	@Override
	public int _exec(final int index, final String ... args) throws CadiException, APIException, LocatorException {
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				int idx = index;
				String realm = getOrgRealm();
				String action = args[idx++];
				int option = whichOption(options, action);
				UserRoleRequest urr = new UserRoleRequest();
				urr.setRole(args[idx++]);
				// Set Start/End commands
				setStartEnd(urr);
				
				Future<?> fp = null;
				
				if (option != 2) {
					String[] ids = args[idx++].split(",");
					String verb=null,participle=null;
					// You can request to be added or removed from role.
					setQueryParamsOn(client);

					for(String id: ids) {
						if (id.indexOf('@') < 0 && realm != null) id += '@' + realm;
						urr.setUser(id);
						switch(option) {
							case 0:
								fp = client.create(
										"/authz/userRole", 
										getDF(UserRoleRequest.class), 
										urr);
								verb = "Added";
								participle = "] to Role [" ;
								break;
							case 1:
								fp = client.delete(
										"/authz/userRole/"+urr.getUser()+'/'+urr.getRole(), 
										Void.class);
								verb = "Removed";
								participle = "] from Role [" ;
								break;
						    case 3:
								fp = client.update("/authz/userRole/extend/" + urr.getUser() + '/' + urr.getRole());
								verb = "Extended";
								participle = "] in Role [" ;
								break;

							default: // actually, should never get here...
								throw new CadiException("Invalid action [" + action + ']');
						}
						if(fp.get(AAFcli.timeout())) {
							pw().print(verb);
							pw().print(" User [");
							pw().print(urr.getUser());
							pw().print(participle);
							pw().print(urr.getRole());
							pw().println(']');
						} else {
							switch(fp.code()) {
								case 202:
									pw().print("User Role ");
									pw().print(action);
									pw().println(" is Accepted, but requires Approvals before actualizing");
									break;
								case 404:
									if(option==3) {
										pw().println("Failed with code 404: UserRole is not found, or you do not have permission to view");
										break;
									}
								default:
									error(fp);
							}
						}
					}
				} else {
					String allUsers = "";
					if (idx < args.length) 
						allUsers = args[idx++];
					StringBuilder finalUsers = new StringBuilder();	
					for (String u : allUsers.split(",")) {
						if (u != "") {
							if (u.indexOf('@') < 0 && realm != null) u += '@' + realm;
							if (finalUsers.length() > 0) finalUsers.append(",");
							finalUsers.append(u);
						}
					}

					urr.setUser(finalUsers.toString());
					fp = client.update(
							"/authz/userRole/role", 
							getDF(UserRoleRequest.class), 
							urr);
					if(fp.get(AAFcli.timeout())) {
						pw().println("Set the Role to Users [" + allUsers + "]");
					} else {
						error(fp);
					}		
				}
				return fp==null?0:fp.code();
			}
		});
	}
	
	@Override
	public void detailedHelp(int indent, StringBuilder sb) {
		detailLine(sb,indent,"Add OR Delete a User to/from a Role OR");
		detailLine(sb,indent,"Set a User's Roles to the roles supplied");
		detailLine(sb,indent+2,"role  - Name of Role to create");
		detailLine(sb,indent+2,"id(s) - ID or IDs to add to the Role");
		sb.append('\n');
		detailLine(sb,indent+2,"Note: this is the same as \"user role add...\" except allows");
		detailLine(sb,indent+2,"assignment of role to multiple userss");
		detailLine(sb,indent+2,"WARNING: Users supplied with setTo will be the ONLY users attached to this role");
		detailLine(sb,indent+2,"If no users are supplied, the users attached to this role are reset.");
		api(sb,indent,HttpMethods.POST,"authz/userRole",UserRoleRequest.class,true);
		api(sb,indent,HttpMethods.DELETE,"authz/userRole/<user>/<role>",Void.class,false);
		api(sb,indent,HttpMethods.PUT,"authz/userRole/<role>",UserRoleRequest.class,false);
	}

}
