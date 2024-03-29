package com.osaaf.defOrg;

import java.io.IOException;
import java.util.List;

import com.att.authz.env.AuthzTrans;
import com.att.authz.local.AbsData.Reuse;
import com.att.authz.org.Organization;
import com.att.authz.org.Organization.Identity;
import com.att.authz.org.OrganizationException;
import com.att.cadi.config.Config;
import com.osaaf.defOrg.Identities.Data;

/**
 * Org Users are essential representations of Identities within the Org.  Since this is a highly individual 
 * thing for most Orgs, i.e. some use LDAP, some need feed, some use something else, this object will allow
 * the Organization to connect to their own Identity systems...
 * 
 *
 */
public class DefaultOrgIdentity implements Identity {
    private final static int TIMEOUT = Integer.parseInt(Config.AAF_CONN_TIMEOUT_DEF);
	private DefaultOrg org;
	private Data identity;
	private Identity owner;

	public DefaultOrgIdentity(AuthzTrans trans, String key, DefaultOrg dorg) throws OrganizationException {
		org = dorg;
		identity=null;
		try {
			org.identities.open(trans, TIMEOUT);
			try {
				Reuse r = org.identities.reuse();
				identity = org.identities.find(key, r);
				if(identity!=null) {
					if("a".equals(identity.status)) {
						owner = new DefaultOrgIdentity(trans,identity.responsibleTo,org);
					} else {
						owner = null;
					}
				}
			} finally {
				org.identities.close(trans);
			}
		} catch (IOException e) {
			throw new OrganizationException(e);
		}
	}
	
	@Override
	public boolean equals(Object b) {
		if(b instanceof DefaultOrgIdentity) {
			return identity.id.equals(((DefaultOrgIdentity)b).identity.id);
		}
		return false;
	}

	@Override
	public String id() {
		return identity.id;
	}

	@Override
	public String fullID() {
		return identity.id+'@'+org.getDomain();
	}

	@Override
	public String type() {
		switch(identity.status) {
			case "e": return DefaultOrg.Types.Employee.name();
			case "c": return DefaultOrg.Types.Contractor.name();
			case "a": return DefaultOrg.Types.Application.name();
			case "n": return DefaultOrg.Types.NotActive.name();
			default:
				return "Unknown";
		}
	}

	@Override
	public String responsibleTo() {
		return identity.responsibleTo;
	}

	@Override
	public List<String> delegate() {
		//NOTE:  implement Delegate system, if desired
		return DefaultOrg.NULL_DELEGATES;
	}

	@Override
	public String email() {
		return identity.email;
	}

	@Override
	public String fullName() {
		return identity.name;
	}

	@Override
	public boolean isResponsible() {
		return "e".equals(identity.status); // Assume only Employees are responsible for Resources.  
	}

	@Override
	public boolean isFound() {
		return identity!=null;
	}

	@Override
	public Identity owner() throws OrganizationException {
		return owner;
	}

	@Override
	public Organization org() {
		return org;
	}

}
