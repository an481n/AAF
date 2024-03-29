/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.authz.service.validation;

import java.util.regex.Pattern;

import com.att.authz.cadi.DirectAAFLur.PermPermission;
import com.att.authz.env.AuthzTrans;
import com.att.authz.layer.Result;
import com.att.authz.org.Organization;
import com.att.dao.aaf.cass.CredDAO;
import com.att.dao.aaf.cass.DelegateDAO;
import com.att.dao.aaf.cass.Namespace;
import com.att.dao.aaf.cass.PermDAO;
import com.att.dao.aaf.cass.RoleDAO;
import com.att.dao.aaf.cass.UserRoleDAO;

/**
 * Validator
 * Consistently apply content rules for content (incoming)
 * 
 * Note: We restrict content for usability in URLs (because RESTful service), and avoid 
 * issues with Regular Expressions, and other enabling technologies. 
 *
 */
public class Validator {
	private static final PermPermission grandfatherPerm = new PermPermission("com.att.aaf","validator","swm_scamper","*");
	// SCAMPER uses "isPrintableASCII(), then uses RegEx "[^~|,?*]
	// * x2A	, x2C	? x3F	| x7C	 ~ x7E 
	private static final String GRANDFATHERED="\\x20-\\x29\\x2B\\x2D-\\x3E\\x40-\\x7B\\x7D";
	// AAF will allow "," "*" in certain positions
	public static final Pattern GRANDFATHERED_ACTION_CHARS=Pattern.compile(
			"["+GRANDFATHERED+"\\x2C]+" +	// All AlphaNumeric+
			"|\\*"						// Just Star
			);

	public static final Pattern GRANDFATHERED_INST_CHARS=Pattern.compile(
			"["+GRANDFATHERED+"]+[\\*]*" +					// All AlphaNumeric+
			"|\\*" +									// Just Star
			"|(([:/]\\*)|([:/]["+GRANDFATHERED+"]+[\\*]*))+"	// Key :asdf:*:sdf
			);
    
	// % () ,-. 0-9 =A-Z _a-z
	private static final String ESSENTIAL="\\x25\\x28\\x29\\x2C-\\x2E\\x30-\\x39\\x3D\\x40-\\x5A\\x5F\\x61-\\x7A";
	private static final Pattern ESSENTIAL_CHARS=Pattern.compile("["+ESSENTIAL+"]+");
	
	// Must be 1 or more of Alphanumeric or the following  :._-
	// '*' only allowed when it is the only character, or the only element in a key separator
	//  :* :hello:* :hello:*:there  etc
	public static final Pattern ACTION_CHARS=Pattern.compile(
			"["+ESSENTIAL+"]+" +	// All AlphaNumeric+
			"|\\*"						// Just Star
			);

	public static final Pattern INST_CHARS=Pattern.compile(
			"["+ESSENTIAL+"]+[\\*]*" +				// All AlphaNumeric+ possibly ending with *
			"|\\*" +								// Just Star
			"|(([:/]\\*)|([:/][!]{0,1}["+ESSENTIAL+"]+[\\*]*[:/]*))+"	// Key :asdf:*:sdf*:sdk
			);
	
	// Must be 1 or more of Alphanumeric or the following  ._-, and be in the form id@domain
	public static final Pattern ID_CHARS=Pattern.compile("[\\w.-]+@[\\w.-]+");
	// Must be 1 or more of Alphanumeric or the following  ._-
	public static final Pattern NAME_CHARS=Pattern.compile("[\\w.-]+");
	
	private final Pattern actionChars;
	private final Pattern instChars;
	private StringBuilder msgs;

	/**
	 * Default Validator does not check for non-standard Action/Inst chars
	 * 
	 * 
	 * IMPORTANT: Use ONLY when the Validator is doing something simple... NullOrBlank
	 */
	public Validator() {
		actionChars = ACTION_CHARS;
		instChars = INST_CHARS;
	}
	
	/**
	 * When Trans is passed in, check for non-standard Action/Inst chars
	 * 
	 * Use for any Object method passed (i.e. role(RoleDAO.Data d) ), to ensure fewer bugs.
	 * 
	 * @param trans
	 */
	public Validator(AuthzTrans trans) {
		if(trans.fish(grandfatherPerm)) {
			actionChars = GRANDFATHERED_ACTION_CHARS;
			instChars = GRANDFATHERED_INST_CHARS;
			trans.info().log(trans.user(), "is given grandfathered user");
		} else {
			actionChars = ACTION_CHARS;
			instChars = INST_CHARS;
		}
	}


	public Validator perm(Result<PermDAO.Data> rpd) {
		if(rpd.notOK()) {
			msg(rpd.details);
		} else {
			perm(rpd.value);
		}
		return this;
	}


	public Validator perm(PermDAO.Data pd) {
		if(pd==null) {
			msg("Perm Data is null.");
		} else {
			ns(pd.ns);
			permType(pd.type,pd.ns);
			permInstance(pd.instance);
			permAction(pd.action);
			if(pd.roles!=null) { 
				for(String role : pd.roles) {
					role(role);
				}
			}
		}
		return this;
	}

	public Validator role(Result<RoleDAO.Data> rrd) {
		if(rrd.notOK()) {
			msg(rrd.details);
		} else {
			role(rrd.value);
		}
		return this;
	}

	public Validator role(RoleDAO.Data pd) {
		if(pd==null) {
			msg("Role Data is null.");
		} else {
			ns(pd.ns);
			role(pd.name);
			if(pd.perms!=null) {
				for(String perm : pd.perms) {
					String[] ps = perm.split("\\|");
					if(ps.length!=3) {
						msg("Perm [" + perm + "] in Role [" + pd.fullName() + "] is not correctly separated with '|'");
					} else {
						permType(ps[0],null);
						permInstance(ps[1]);
						permAction(ps[2]);
					}
				}
			}
		}
		return this;
	}

	public Validator delegate(Organization org, Result<DelegateDAO.Data> rdd) {
		if(rdd.notOK()) {
			msg(rdd.details);
		} else {
			delegate(org, rdd.value);
		}
		return this;
	}

	public Validator delegate(Organization org, DelegateDAO.Data dd) {
		if(dd==null) {
			msg("Delegate Data is null.");
		} else {
			user(org,dd.user);
			user(org,dd.delegate);
		}
		return this;
	}


	public Validator cred(Organization org, Result<CredDAO.Data> rcd, boolean isNew) {
		if(rcd.notOK()) {
			msg(rcd.details);
		} else {
			cred(org,rcd.value,isNew);
		}
		return this;
	}

	public Validator cred(Organization org, CredDAO.Data cd, boolean isNew) {
		if(cd==null) {
			msg("Cred Data is null.");
		} else {
			if(nob(cd.id,ID_CHARS)) {
				msg("ID [" + cd.id + "] is invalid");
			}
			if(!org.isValidCred(cd.id)) {
				msg("ID [" + cd.id + "] is invalid for a cred");
			}
			String str = cd.id;
			int idx = str.indexOf('@');
			if(idx>0) {
				str = str.substring(0,idx);
			}
			
			if(cd.id.endsWith(org.getRealm())) {
				if(isNew && (str=org.isValidID(str)).length()>0) {
					msg(cd.id,str);
				}
			}
	
			if(cd.type==null) {
				msg("Credential Type must be set");
			} else {
				switch(cd.type) {
					case CredDAO.BASIC_AUTH_SHA256:
						// ok
						break;
					default:
						msg("Credential Type [",Integer.toString(cd.type),"] is invalid");
				}
			}
		}
		return this;
	}


	public Validator user(Organization org, String user) {
		if(nob(user,ID_CHARS)) {
			msg("User [",user,"] is invalid.");
		}
		//TODO Change when Multi-Org solution is created
//		if(org instanceof ATT) {
//			if(!user.endsWith("@csp.att.com") &&
//			   !org.isValidCred(user)) 
//					msg("User [",user,"] is not valid ID for Credential in ",org.getRealm());
//		}
		return this;
	}

	public Validator ns(Result<Namespace> nsd) {
		notOK(nsd);
		ns(nsd.value.name);
		for(String s : nsd.value.admin) {
			if(nob(s,ID_CHARS)) {
				msg("Admin [" + s + "] is invalid.");		
			}
			
		}
		for(String s : nsd.value.owner) {
			if(nob(s,ID_CHARS)) {
				msg("Responsible [" + s + "] is invalid.");		
			}
			
		}
		return this;
	}


	public Validator ns(String ns) {
		if(nob(ns,NAME_CHARS)){
			msg("NS [" + ns + "] is invalid.");
		}
		return this;
	}

	public String errs() {
		return msgs.toString();
	}


	public Validator permType(String type, String ns) {
		// TODO check for correct Splits?  Type|Instance|Action ?
		if(nob(type,NAME_CHARS)) {
			msg("Perm Type [" + (ns==null?"":ns+(type.length()==0?"":'.'))+type + "] is invalid.");
		}
		return this;
	}

	public Validator permInstance(String instance) {
		// TODO check for correct Splits?  Type|Instance|Action ?
		if(nob(instance,instChars)) {
			msg("Perm Instance [" + instance + "] is invalid.");
		}
		return this;
	}

	public Validator permAction(String action) {
		// TODO check for correct Splits?  Type|Instance|Action ?
		if(nob(action, actionChars)) {
			msg("Perm Action [" + action + "] is invalid.");
		}
		return this;
	}

	public Validator role(String role) {
		if(nob(role, NAME_CHARS)) {
			msg("Role [" + role + "] is invalid.");
		}
		return this;
	}

	public Validator user_role(UserRoleDAO.Data urdd) {
		if(urdd==null) {
			msg("UserRole is null");
		} else {
			role(urdd.role);
			nullOrBlank("UserRole.ns",urdd.ns);
			nullOrBlank("UserRole.rname",urdd.rname);
		}
		return this;
	}

	public Validator nullOrBlank(String name, String str) {
		if(str==null) {
			msg(name + " is null.");
		} else if(str.length()==0) {
			msg(name + " is blank.");
		}
		return this;
	}
	
	public Validator nullOrBlank(PermDAO.Data pd) {
		if(pd==null) {
			msg("Permission is null");
		} else {
			nullOrBlank("NS",pd.ns).
			nullOrBlank("Type",pd.type).
			nullOrBlank("Instance",pd.instance).
			nullOrBlank("Action",pd.action);
		}
		return this;
	}

	public Validator nullOrBlank(RoleDAO.Data rd) {
		if(rd==null) {
			msg("Role is null");
		} else {
			nullOrBlank("NS",rd.ns).
			nullOrBlank("Name",rd.name);
		}
		return this;
	}

	// nob = Null Or Not match Pattern
	private boolean nob(String str, Pattern p) {
		return str==null || !p.matcher(str).matches(); 
	}

	private void msg(String ... strs) {
		if(msgs==null) {
			msgs=new StringBuilder();
		}
		for(String str : strs) {
			msgs.append(str);
		}
		msgs.append('\n');
	}
	
	public boolean err() {
		return msgs!=null;
	}


	public Validator notOK(Result<?> res) {
		if(res==null) {
			msgs.append("Result object is blank");
		} else if(res.notOK()) {
			msgs.append(res.getClass().getSimpleName() + " is not OK");
		}
		return this;
	}

	public Validator key(String key) {
		if(nob(key,NAME_CHARS)) {
			msg("NS Prop Key [" + key + "] is invalid");
		}
		return this;
	}
	
	public Validator value(String value) {
		if(nob(value,ESSENTIAL_CHARS)) {
			msg("NS Prop value [" + value + "] is invalid");
		}
		return this;
	}


}
