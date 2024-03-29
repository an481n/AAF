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

/**
 * p
 *
 */
public class Delete extends Cmd {
	public Delete(NS parent) {
		super(parent,"delete", 
				new Param("name",true)); 
	}

	@Override
	public int _exec(final int idx, final String ... args) throws CadiException, APIException, LocatorException {
		return same(new Retryable<Integer>() {
			@Override
			public Integer code(Rcli<?> client) throws CadiException, APIException {
				int index = idx;
				StringBuilder path = new StringBuilder("/authz/ns/");
				path.append(args[index++]);
				
				// Send "Force" if set
				setQueryParamsOn(client);
				Future<Void> fp = client.delete(path.toString(),Void.class);
				
				if(fp.get(AAFcli.timeout())) {
					pw().println("Deleted Namespace");
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
		detailLine(sb,indent,"Delete a Namespace");
		indent+=4;
		detailLine(sb,indent,"Namespaces cannot normally be deleted when there are still credentials,");
		detailLine(sb,indent,"permissions or roles associated with them. These can be deleted");
		detailLine(sb,indent,"automatically by setting \"force\" property.");
		detailLine(sb,indent,"i.e. set force=true or just starting with \"force\"");
		detailLine(sb,indent," (note force is unset after first use)");
		sb.append('\n');
		detailLine(sb,indent,"If \"set force=move\" is set, credentials are deleted, but ");
		detailLine(sb,indent,"Permissions and Roles are assigned to the Parent Namespace instead of");
		detailLine(sb,indent,"being deleted.  Similarly, Namespaces can be created even though there");
		detailLine(sb,indent,"are Roles/Perms whose type starts with the requested sub-namespace.");
		detailLine(sb,indent,"They are simply reassigned to the Child Namespace");
		indent-=4;
		api(sb,indent,HttpMethods.DELETE,"authz/ns/<ns>[?force=true]",Void.class,true);
	}

}
