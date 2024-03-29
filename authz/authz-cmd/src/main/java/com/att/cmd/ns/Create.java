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
import com.att.cmd.Cmd;
import com.att.cmd.Param;
import com.att.inno.env.APIException;

import aaf.v2_0.NsRequest;

/**
 * p
 *
 */
public class Create extends Cmd {
	private static final String COMMA = ",";

	public Create(NS parent) {
		super(parent,"create", 
				new Param("name",true),
				new Param("responsible (id[,id]*)",true), 
				new Param("admin (id[,id]*)",false));
	}

	@Override
	public int _exec(int _idx, final String ... args) throws CadiException, APIException, LocatorException {
	    	int idx = _idx;

		final NsRequest nr = new NsRequest();
		
		String realm = getOrgRealm();
		
		nr.setName(args[idx++]);
		String[] responsible = args[idx++].split(COMMA);
		for(String s : responsible) {
			if (s.indexOf('@') < 0 && realm != null) s += '@' + realm;
			nr.getResponsible().add(s);
		}
		String[] admin;
		if(args.length>idx) {
			admin = args[idx++].split(COMMA);
		} else {
			admin = responsible;
		}
		for(String s : admin) {
			if (s.indexOf('@') < 0 && realm != null) s += '@' + realm;
			nr.getAdmin().add(s);
		}
		
		// Set Start/End commands
		setStartEnd(nr);
		
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				// Requestable
				setQueryParamsOn(client);
				Future<NsRequest> fp = client.create(
						"/authz/ns", 
						getDF(NsRequest.class),
						nr
						);
				if(fp.get(AAFcli.timeout())) {
					pw().println("Created Namespace");
				} else {
					if(fp.code()==202) {
						pw().println("Namespace Creation Accepted, but requires Approvals before actualizing");
					} else {
						error(fp);
					}
				}
				return fp.code();
			}
		});
	}

	@Override
	public void detailedHelp(int _indent, StringBuilder sb) {
	    	int indent = _indent;
		detailLine(sb,indent,"Create a Namespace");
		indent+=2;
		detailLine(sb,indent,"name        - Namespaces are dot-delimited, ex com.att.myapp");
		detailLine(sb,indent+14,"and must be created with parent credentials.");
		detailLine(sb,indent+14,"Ex: to create com.att.myapp, you must be admin for com.att");
		detailLine(sb,indent+14,"or com");
		detailLine(sb,indent,"responsible - This is the person(s) who receives Notifications and");
		detailLine(sb,indent+14,"approves Requests regarding this Namespace. Companies have");
		detailLine(sb,indent+14,"Policies as to who may take on this responsibility");
		detailLine(sb,indent,"admin       - These are the people who are allowed to make changes on");
		detailLine(sb,indent+14,"the Namespace, including creating Roles, Permissions");
		detailLine(sb,indent+14,"and Credentials");
		sb.append('\n');
		detailLine(sb,indent,"Namespaces can be created even though there are Roles/Permissions which");
		detailLine(sb,indent,"start with the requested sub-namespace.  They are reassigned to the");
		detailLine(sb,indent,"Child Namespace");
		indent-=2;
		api(sb,indent,HttpMethods.POST,"authz/ns",NsRequest.class,true);
	}

}
