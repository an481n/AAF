/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.dao.aaf.hl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.att.authz.env.AuthzTrans;
import com.att.authz.layer.Result;
import com.att.dao.aaf.cass.PermDAO;
import com.att.dao.aaf.cass.RoleDAO;
import com.att.dao.aaf.cass.Status;
import com.att.dao.aaf.cass.UserRoleDAO;

/**
 * PermLookup is a Storage class for the various pieces of looking up Permission 
 * during Transactions to avoid duplicate processing
 * 
 *
 */
// Package on purpose
class PermLookup {
	private AuthzTrans trans;
	private String user;
	private Question q;
	private Result<List<UserRoleDAO.Data>> userRoles = null;
	private Result<List<RoleDAO.Data>> roles = null;
	private Result<Set<String>> permNames = null;
	private Result<List<PermDAO.Data>> perms = null;
	
	private PermLookup() {}
	
	static PermLookup get(AuthzTrans trans, Question q, String user) {
		PermLookup lp=null;
		Map<String, PermLookup> permMap = trans.get(Question.PERMS, null);
		if (permMap == null) {
			trans.put(Question.PERMS, permMap = new HashMap<String, PermLookup>());
		} else {
			lp = permMap.get(user);
		}

		if (lp == null) {
			lp = new PermLookup();
			lp.trans = trans;
			lp.user = user;
			lp.q = q;
			permMap.put(user, lp);
		}
		return lp;
	}
	
	public Result<List<UserRoleDAO.Data>> getUserRoles() {
		if(userRoles==null) {
			userRoles = q.userRoleDAO.readByUser(trans,user);
			if(userRoles.isOKhasData()) {
				List<UserRoleDAO.Data> lurdd = new ArrayList<UserRoleDAO.Data>();
				Date now = new Date();
				for(UserRoleDAO.Data urdd : userRoles.value) {
					if(urdd.expires.after(now)) { // Remove Expired
						lurdd.add(urdd);
					}
				}
				if(lurdd.size()==0) {
					return userRoles = Result.err(Status.ERR_UserNotFound,
								"%s not found or not associated with any Roles: ",
								user);
				} else {
					return userRoles = Result.ok(lurdd);
				}
			} else {
				return userRoles;
			}
		} else {
			return userRoles;
		}
	}

	public Result<List<RoleDAO.Data>> getRoles() {
		if(roles==null) {
			Result<List<UserRoleDAO.Data>> rur = getUserRoles();
			if(rur.isOK()) {
				List<RoleDAO.Data> lrdd = new ArrayList<RoleDAO.Data>();
				for (UserRoleDAO.Data urdata : rur.value) {
					// Gather all permissions from all Roles
					    if(urdata.ns==null || urdata.rname==null) {
					    	trans.error().printf("DB Content Error: nulls in User Role %s %s", urdata.user,urdata.role);
					    } else {
							Result<List<RoleDAO.Data>> rlrd = q.roleDAO.read(
									trans, urdata.ns, urdata.rname);
							if(rlrd.isOK()) {
								lrdd.addAll(rlrd.value);
							}
					    }
					}
				return roles = Result.ok(lrdd);
			} else {
				return roles = Result.err(rur);
			}
		} else {
			return roles;
		}
	}

	public Result<Set<String>> getPermNames() {
		if(permNames==null) {
			Result<List<RoleDAO.Data>> rlrd = getRoles();
			if (rlrd.isOK()) {
				Set<String> pns = new TreeSet<String>();
				for (RoleDAO.Data rdata : rlrd.value) {
					pns.addAll(rdata.perms(false));
				}
				return permNames = Result.ok(pns);
			} else {
				return permNames = Result.err(rlrd);
			}
		} else {
			return permNames;
		}
	}
	
	public Result<List<PermDAO.Data>> getPerms(boolean lookup) {
		if(perms==null) {
			// Note: It should be ok for a Valid user to have no permissions -
			// jg1555 8/12/2013
			Result<Set<String>> rss = getPermNames();
			if(rss.isOK()) {
				List<PermDAO.Data> lpdd = new ArrayList<PermDAO.Data>();
				for (String perm : rss.value) {
					if(lookup) {
						Result<String[]> ap = PermDAO.Data.decodeToArray(trans, q, perm);
						if(ap.isOK()) {
							Result<List<PermDAO.Data>> rlpd = q.permDAO.read(perm,trans,ap);
							if (rlpd.isOKhasData()) {
								for (PermDAO.Data pData : rlpd.value) {
									lpdd.add(pData);
								}
							}
						} else {
							trans.error().log("In getPermsByUser, for", user, perm);
						}
					} else {
						Result<PermDAO.Data> pr = PermDAO.Data.decode(trans, q, perm);
						if (pr.notOK()) {
							trans.error().log("In getPermsByUser, for", user, pr.errorString());
						} else {
							lpdd.add(pr.value);
						}
					}

				}
				return perms = Result.ok(lpdd);
			} else {
				return perms = Result.err(rss);
			}
		} else {
			return perms;
		}
	}
}