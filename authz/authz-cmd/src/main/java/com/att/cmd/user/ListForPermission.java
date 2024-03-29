/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cmd.user;

import java.util.Comparator;

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

import aaf.v2_0.Users;
import aaf.v2_0.Users.User;
import edu.emory.mathcs.backport.java.util.Collections;

/**
 * p
 *
 */
public class ListForPermission extends Cmd {
	private static final String HEADER = "List Users for Permission";
	public ListForPermission(List parent) {
		super(parent,"perm", 
				new Param("type",true),
				new Param("instance",true),
				new Param("action",true)); 
	}

	@Override
	public int _exec(final int index, final String ... args) throws CadiException, APIException, LocatorException {
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				int idx = index;
				String type = args[idx++];
				String instance = args[idx++];
				if("\\*".equals(instance))instance="*";
				String action = args[idx++];
				if("\\*".equals(action))action="*";
				Future<Users> fp = client.read(
						"/authz/users/perm/"+type+'/'+instance+'/'+action, 
						getDF(Users.class)
						);
				if(fp.get(AAFcli.timeout())) {
					if (aafcli.isTest())
						Collections.sort(fp.value.getUser(), new Comparator<User>() {
							@Override
							public int compare(User u1, User u2) {
								return u1.getId().compareTo(u2.getId());
							}			
						});
					((com.att.cmd.user.List)parent).report(fp.value,false,HEADER,type+"|"+instance+"|"+action);
					if(fp.code()==404)return 200;
				} else {
					error(fp);
				}
				return fp.code();
			}
		});
	}
	
	@Override
	public void detailedHelp(int _indent, StringBuilder sb) {
	        int indent = _indent;
		detailLine(sb,indent,HEADER);
		indent+=2;
		detailLine(sb,indent,"This report lists the users associated to Permissions.  Since Users");
		detailLine(sb,indent,"are associated to Roles, and Roles have Permissions, this report");
		detailLine(sb,indent,"accomodates all these linkages.");
		sb.append('\n');
		detailLine(sb,indent,"The URL must contain the Permission's type,instance and action, and ");
		detailLine(sb,indent,"may include \"*\"s (type in as \\\\*).");
		detailLine(sb,indent,"See Perm Create Documentation for definitions.");
		indent-=2;
		api(sb,indent,HttpMethods.GET,"authz/users/perm/<type>/<instance>/<action>",Users.class,true);
	}
}
