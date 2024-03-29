/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.authz.org;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.att.authz.env.AuthzTrans;

/**
 * Organization
 * 
 * There is Organizational specific information required which we have extracted to a plugin
 * 
 * It supports using Company Specific User Directory lookups, as well as supporting an
 * Approval/Validation Process to simplify control of Roles and Permissions for large organizations
 * in lieu of direct manipulation by a set of Admins. 
 *  
 *
 */
public interface Organization {
	public static final String N_A = "n/a";

	public interface Identity {
		public String id();
		public String fullID();					// Fully Qualified ID (includes Domain of Organization)
		public String type(); 					// Must be one of "IdentityTypes", see below
		public String responsibleTo(); 	        // Chain of Command, Comma Separated if required
		public List<String> delegate(); 		// Someone who has authority to act on behalf of Identity
		public String email();
		public String fullName();
		public boolean isResponsible();			// Is id passed belong to a person suitable to be Responsible for content Management
		public boolean isFound();				// Is Identity found in Identity stores
		public Identity owner() throws OrganizationException;					// Identity is directly responsible for App ID
		public Organization org(); 				// Organization of Identity
	}


	/**
	 * Name of Organization, suitable for Logging
	 * @return
	 */
	public String getName();

	/**
	 * Realm, for use in distinguishing IDs from different systems/Companies
	 * @return
	 */
	public String getRealm();

	String getDomain();

	/**
	 * Get Identity information based on userID
	 * 
	 * @param id
	 * @return
	 */
	public Identity getIdentity(AuthzTrans trans, String id) throws OrganizationException;
	

	/**
	 * Does the ID pass Organization Standards
	 * 
	 * Return a Blank (empty) String if empty, otherwise, return a "\n" separated list of 
	 * reasons why it fails
	 * 
	 * @param id
	 * @return
	 */
	public String isValidID(String id);

	/**
	 * Return a Blank (empty) String if empty, otherwise, return a "\n" separated list of 
	 * reasons why it fails
	 *  
	 *  Identity is passed in to allow policies regarding passwords that are the same as user ID
	 *  
	 *  any entries for "prev" imply a reset
	 *  
	 * @param id
	 * @param password
	 * @return
	 */
	public String isValidPassword(String user, String password, String ... prev);


	/**
	 * Does your Company distinguish essential permission structures by kind of Identity?
	 * i.e. Employee, Contractor, Vendor 
	 * @return
	 */
	public Set<String> getIdentityTypes();

	public enum Notify {
		Approval(1),
		PasswordExpiration(2),
        RoleExpiration(3);

		final int id;
		Notify(int id) {this.id = id;}
		public int getValue() {return id;}
		public static Notify from(int type) {
			for(Notify t : Notify.values()) {
				if(t.id==type) {
					return t;
				}
			}
			return null;
		}
	}

	public enum Response{
		OK,
		ERR_NotImplemented,
		ERR_UserNotExist,
		ERR_NotificationFailure,
		};
		
	public enum Expiration {
		Password,
		TempPassword, 
		Future,
		UserInRole,
		UserDelegate, 
		ExtendPassword
	}
	
	public enum Policy {
		CHANGE_JOB, 
		LEFT_COMPANY, 
		CREATE_MECHID, 
		CREATE_MECHID_BY_PERM_ONLY,
		OWNS_MECHID,
		AS_EMPLOYEE, 
		MAY_EXTEND_CRED_EXPIRES
	}
	
	/**
	 * Notify a User of Action or Info
	 * 
	 * @param type
	 * @param url
	 * @param users (separated by commas)
	 * @param ccs (separated by commas)
	 * @param summary
	 */

    public Response notify(AuthzTrans trans, Notify type, String url, String ids[], String ccs[], String summary, Boolean urgent);

	/**
	 * (more) generic way to send an email
	 * 
	 * @param toList
	 * @param ccList
	 * @param subject
	 * @param body
	 * @param urgent
	 */

	public int sendEmail(AuthzTrans trans, List<String> toList, List<String> ccList, String subject, String body, Boolean urgent) throws OrganizationException;

	/**
	 * whenToValidate
	 * 
	 * Authz support services will ask the Organization Object at startup when it should
	 * kickoff Validation processes given particular types. 
	 * 
	 * This allows the Organization to express Policy
	 * 
	 * Turn off Validation behavior by returning "null"
	 * 
	 */
	public Date whenToValidate(Notify type, Date lastValidated);

	
	/**
	 * Expiration
	 * 
	 * Given a Calendar item of Start (or now), set the Expiration Date based on the Policy
	 * based on type.
	 * 
	 * For instance, "Passwords expire in 3 months"
	 * 
	 * The Extra Parameter is used by certain Orgs.
	 * 
	 * For Password, the extra is UserID, so it can check the Identity Type
	 * 
	 * @param gc
	 * @param exp
	 * @return
	 */
	public GregorianCalendar expiration(GregorianCalendar gc, Expiration exp, String ... extra);
	
	/**
	 * Get Email Warning timing policies
	 * @return
	 */
	public EmailWarnings emailWarningPolicy();

	/**
	 * 
	 * @param trans
	 * @param user
	 * @return
	 */
	public List<Identity> getApprovers(AuthzTrans trans, String user) throws OrganizationException ;
	
	/*
	 * 
	 * @param user
	 * @param type
	 * @param users
	 * @return
	public Response notifyRequest(AuthzTrans trans, String user, Approval type, List<User> approvers);
	*/
	
	/**
	 * 
	 * @return
	 */
	public String getApproverType();

	/*
	 * startOfDay - define for company what hour of day business starts (specifically for password and other expiration which
	 *   were set by Date only.)
	 *    
	 * @return
	 */
	public int startOfDay();

    /**
     * implement this method to support any IDs that can have multiple entries in the cred table
     * NOTE: the combination of ID/expiration date/(encryption type when implemented) must be unique.
     * 		 Since expiration date is based on startOfDay for your company, you cannot create many
     * 		 creds for the same ID in the same day.
     * @param id
     * @return
     */
    public boolean canHaveMultipleCreds(String id);
    
    /**
     * 
     * @param id
     * @return
     */
    public boolean isValidCred(String id);
    
    /**
     * If response is Null, then it is valid.  Otherwise, the Organization specific reason is returned.
     *  
     * @param trans
     * @param policy
     * @param executor
     * @param vars
     * @return
     * @throws OrganizationException
     */
    public String validate(AuthzTrans trans, Policy policy, Executor executor, String ... vars) throws OrganizationException;

	boolean isTestEnv();

	public void setTestMode(boolean dryRun);

	public static final Organization NULL = new Organization() 
	{
		private final GregorianCalendar gc = new GregorianCalendar(1900, 1, 1);
		private final List<Identity> nullList = new ArrayList<Identity>();
		private final Set<String> nullStringSet = new HashSet<String>();
		private final Identity nullIdentity = new Identity() {
			List<String> nullIdentity = new ArrayList<String>();
			@Override
			public String type() {
				return N_A;
			}
			@Override
			public String responsibleTo() {
				return N_A;
			}
			@Override
			public boolean isResponsible() {
				return false;
			}
			
			@Override
			public boolean isFound() {
				return false;
			}
			
			@Override
			public String id() {
				return N_A;
			}
			
			@Override
			public String fullID() {
				return N_A;
			}
			
			@Override
			public String email() {
				return N_A;
			}
			
			@Override
			public List<String> delegate() {
				return nullIdentity;
			}
			@Override
			public String fullName() {
				return N_A;
			}
			@Override
			public Identity owner() {
				return null;
			}
			@Override
			public Organization org() {
				return NULL;
			}
		};

		@Override
		public String getName() {
			return N_A;
		}
	
		@Override
		public String getRealm() {
			return N_A;
		}
	
		@Override
		public String getDomain() {
			return N_A;
		}
	
		@Override
		public Identity getIdentity(AuthzTrans trans, String id) {
			return nullIdentity;
		}
	
		@Override
		public String isValidID(String id) {
			return N_A;
		}
	
		@Override
		public String isValidPassword(String user, String password,String... prev) {
			return N_A;
		}
	
		@Override
		public Set<String> getIdentityTypes() {
			return nullStringSet;
		}
	
		@Override
		public Response notify(AuthzTrans trans, Notify type, String url,
				String[] users, String[] ccs, String summary, Boolean urgent) {
			return Response.ERR_NotImplemented;
		}
	
		@Override
		public int sendEmail(AuthzTrans trans, List<String> toList, List<String> ccList,
				String subject, String body, Boolean urgent) throws OrganizationException {
			return 0;
		}
	
		@Override
		public Date whenToValidate(Notify type, Date lastValidated) {
			return gc.getTime();
		}
	
		@Override
		public GregorianCalendar expiration(GregorianCalendar gc,
				Expiration exp, String... extra) {
			return gc;
		}
	
		@Override
		public List<Identity> getApprovers(AuthzTrans trans, String user)
				throws OrganizationException {
			return nullList;
		}
	
		@Override
		public String getApproverType() {
			return "";
		}
	
		@Override
		public int startOfDay() {
			return 0;
		}
	
		@Override
		public boolean canHaveMultipleCreds(String id) {
			return false;
		}
	
		@Override
		public boolean isValidCred(String id) {
			return false;
		}
	
		@Override
		public String validate(AuthzTrans trans, Policy policy, Executor executor, String ... vars)
				throws OrganizationException {
			return "Null Organization rejects all Policies";
		}
	
		@Override
		public boolean isTestEnv() {
			return false;
		}
	
		@Override
		public void setTestMode(boolean dryRun) {
		}

		@Override
		public EmailWarnings emailWarningPolicy() {
			return new EmailWarnings() {

				@Override
			    public long credEmailInterval()
			    {
			        return 604800000L; // 7 days in millis 1000 * 86400 * 7
			    }
			    
				@Override
			    public long roleEmailInterval()
			    {
			        return 604800000L; // 7 days in millis 1000 * 86400 * 7
			    }
				
				@Override
				public long apprEmailInterval() {
			        return 259200000L; // 3 days in millis 1000 * 86400 * 3
				}
			    
				@Override
			    public long  credExpirationWarning()
			    {
			        return( 2592000000L ); // One month, in milliseconds 1000 * 86400 * 30  in milliseconds
			    }
			    
				@Override
			    public long roleExpirationWarning()
			    {
			        return( 2592000000L ); // One month, in milliseconds 1000 * 86400 * 30  in milliseconds
			    }

				@Override
			    public long emailUrgentWarning()
			    {
			        return( 1209600000L ); // Two weeks, in milliseconds 1000 * 86400 * 14  in milliseconds
			    }

			};
		}
	};
}


