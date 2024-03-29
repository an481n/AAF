package com.osaaf.defOrg;

import java.io.File;
import java.io.IOException;

import com.att.authz.local.AbsData;
import com.att.authz.local.DataFile.Token.Field;

/*
 * Example User Data file, which can be modified for many different kinds of Data Feeds.
 * 
 * Note: This has shown to be extremely effective in AT&T, an acknowledged very large organizations, 
 * 	     because there is no need to synchronize records.  AAF simply receives a Data Feed in Organization
 * 		 defined intervals.  (You might want to check for validity, such as size, etc), then is copied into
 * 		 Data Directory.  You will want to do so first creating a "lock" file.  Assuming the File name is "users.dat",
 * 		 the Lock File is "users.lock".  
 * 
 * 		 After the movement of the Datafile into place, it is best to remove the Index File, then remove the lock file.
 * 
 * 		 Note, Any AAF Programs needing this data WILL wait on the Lock file, so you should get fresh Data files
 *       in a "stage" directory, from WEB, or wherever, and then, after it is correct, do the following as fast as feasible.
 *       
 *       	a) lock
 *          b) copy from stage
 *          c) remove idx
 *          d) unlock
 * 
 * 	     If the Index File is either non-existent or out of date from the Data File, it will be reindexed, which
 * 		 has proven to be a very quick function, even with large numbers of entries.
 * 
 * This Sample Feed is set for a file with delimiter of "|".  512 is maximum expected line length. The "0" is the
 *       field offset for the "key" to the record,  which, for user, should be the unique Organization Identity.
 *       
 */
public class Identities extends AbsData {
	public Identities(File users) {
		super(users,'|',512,0);
	}

	/*
	 * Example Field Layout.  note, in this example, Application IDs and People IDs are mixed.  You may want to split
	 *   out AppIDs, choose your own status indicators, or whatever you use.
	 * 0 - unique ID
	 * 1 - full name
	 * 2 - first name
	 * 3 - last name
	 * 4 - phone
	 * 5 - official email
	 * 6 - employment status e=employee, c=contractor, a=application, n=no longer with company
	 * 7 - responsible to (i.e Supervisor for People, or AppOwner, if it's an App ID)
	 */
	public class Data {
		public final String id;
		public final String name;
		public final String fname;
		public final String lname;
		public final String phone;
		public final String email;
		public final String status;
		public final String responsibleTo;
		
		private Data(Field f) {
			f.reset();
			id=f.next();
			name=f.next();
			fname=f.next();
			lname=f.next();
			phone=f.next();
			email=f.next();
			status=f.next();
			responsibleTo =f.next();
		}
		
		public String toString() {
			return  id + '|' +
					name + '|' +
					lname + '|' +
					fname + '|' +
					phone + '|' +
					email + '|' +
					status + '|' +
					responsibleTo;
		}
		
		// Here, make up your own Methods which help you easily determine your Organization's structure
		// in your Organization Object
        public boolean hasStatus(String possible) {
            return possible.contains(status);
	    }

	    public boolean isEmployee() {
	            return "e".equals(status);
	    }
	
	    public boolean isContractor() {
	            return "c".equals(status);
	    }
	
	    public boolean isApplication() {
	            return "a".equals(status);
	    }
	}
	
    public Data find(Object key,Reuse r) throws IOException {
        r.getFieldData().reset();
        // These are new, to allow for Thread Safety
        int rec = ti.find(key,r.getTokenData(),r.getFieldData(),0);
        if(rec<0) {
            return null;
        }
        r.getTokenData().pos(rec);
        return new Data(r.getFieldData());
}
}
