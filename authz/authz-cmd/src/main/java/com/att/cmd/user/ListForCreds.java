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
 * List for Creds
 *
 */
public class ListForCreds extends Cmd {
	private final static String[] options = {"ns","id"};

	private static final String HEADER = "List creds for ";
	public ListForCreds(List parent) {
		super(parent,"cred",
				new Param(optionsToString(options),true),
				new Param("value",true)); 
	}

	@Override
	public int _exec(int _idx, final String ... args) throws CadiException, APIException, LocatorException {
	        int idx = _idx;
		final int option = whichOption(options, args[idx++]);
		final String which = options[option];
		final String value = args[idx++];
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				Future<Users> fp = client.read(
						"/authn/creds/"+which+'/'+value, 
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
					((com.att.cmd.user.List)parent).report(fp.value,option==1,HEADER+which,value);
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
		detailLine(sb,indent,"This report lists the users associated to Roles.");
		detailLine(sb,indent,"role - the Role name");
		indent-=2;
		api(sb,indent,HttpMethods.GET,"authz/users/role/<role>",Users.class,true);
	}

}
