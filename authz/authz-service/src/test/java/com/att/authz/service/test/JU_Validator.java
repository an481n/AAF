/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.authz.service.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.att.authz.service.validation.Validator;

public class JU_Validator {


	@Test
	public void test() {
		assertTrue(Validator.ACTION_CHARS.matcher("HowdyDoody").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("Howd?yDoody").matches());
		assertTrue(Validator.ACTION_CHARS.matcher("_HowdyDoody").matches());
		assertTrue(Validator.INST_CHARS.matcher("HowdyDoody").matches());
		assertFalse(Validator.INST_CHARS.matcher("Howd?yDoody").matches());
		assertTrue(Validator.INST_CHARS.matcher("_HowdyDoody").matches());

		//		
		assertTrue(Validator.ACTION_CHARS.matcher("*").matches());
		assertTrue(Validator.INST_CHARS.matcher("*").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(":*").matches());
		assertTrue(Validator.INST_CHARS.matcher(":*").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(":*:*").matches());
		assertTrue(Validator.INST_CHARS.matcher(":*:*").matches());
		
		assertFalse(Validator.ACTION_CHARS.matcher(":hello").matches());
		assertTrue(Validator.INST_CHARS.matcher(":hello").matches());
		assertFalse(Validator.INST_CHARS.matcher("hello:").matches());
		assertFalse(Validator.INST_CHARS.matcher("hello:d").matches());
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher("hello:").matches());
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher("hello:d").matches());

		assertFalse(Validator.ACTION_CHARS.matcher(":hello:*").matches());
		assertTrue(Validator.INST_CHARS.matcher(":hello:*").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(":hello:d*:*").matches());
		assertFalse(Validator.INST_CHARS.matcher(":hello:d*d:*").matches());
		assertTrue(Validator.INST_CHARS.matcher(":hello:d*:*").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("HowdyDoody*").matches());
		assertFalse(Validator.INST_CHARS.matcher("Howdy*Doody").matches());
		assertTrue(Validator.INST_CHARS.matcher("HowdyDoody*").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("*HowdyDoody").matches());
		assertFalse(Validator.INST_CHARS.matcher("*HowdyDoody").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(":h*").matches());
		assertFalse(Validator.INST_CHARS.matcher(":h*h*").matches());
		assertTrue(Validator.INST_CHARS.matcher(":h*").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(":h:h*:*").matches());
		assertTrue(Validator.INST_CHARS.matcher(":h:h*:*").matches());
		assertFalse(Validator.INST_CHARS.matcher(":h:h*h:*").matches());
		assertFalse(Validator.INST_CHARS.matcher(":h:h*h*:*").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(":h:*:*h").matches());
		assertFalse(Validator.INST_CHARS.matcher(":h:*:*h").matches());
		assertTrue(Validator.INST_CHARS.matcher(":com.test.*:ns:*").matches());

		
		assertFalse(Validator.ACTION_CHARS.matcher("1234+235gd").matches());
		assertTrue(Validator.ACTION_CHARS.matcher("1234-235gd").matches());
		assertTrue(Validator.ACTION_CHARS.matcher("1234-23_5gd").matches());
		assertTrue(Validator.ACTION_CHARS.matcher("1234-235g,d").matches());
		assertTrue(Validator.ACTION_CHARS.matcher("1234-235gd(Version12)").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("123#4-23@5g:d").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("123#4-23@5g:d").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("1234-23 5gd").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("1234-235gd ").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(" 1234-235gd").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(" ").matches());

		// Allow % and =   (Needed for Escaping & Base64 usages) jg 
		assertTrue(Validator.ACTION_CHARS.matcher("1234%235g=d").matches());
		assertFalse(Validator.ACTION_CHARS.matcher(":1234%235g=d").matches());
		assertTrue(Validator.INST_CHARS.matcher("1234%235g=d").matches());
		assertTrue(Validator.INST_CHARS.matcher(":1234%235g=d").matches());
		assertTrue(Validator.INST_CHARS.matcher(":1234%235g=d:%20==").matches());
		assertTrue(Validator.INST_CHARS.matcher(":1234%235g=d:==%20:=%23").matches());
		assertTrue(Validator.INST_CHARS.matcher(":1234%235g=d:*:=%23").matches());
		assertTrue(Validator.INST_CHARS.matcher(":1234%235g=d:==%20:*").matches());
		assertTrue(Validator.INST_CHARS.matcher(":*:==%20:*").matches());

		// Allow / instead of :  (more natural instance expression) jg 
		assertFalse(Validator.INST_CHARS.matcher("1234/a").matches());
		assertTrue(Validator.INST_CHARS.matcher("/1234/a").matches());
		assertTrue(Validator.INST_CHARS.matcher("/1234/*/a/").matches());
		assertTrue(Validator.INST_CHARS.matcher("/1234//a").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("1234/a").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("/1234/*/a/").matches());
		assertFalse(Validator.ACTION_CHARS.matcher("1234//a").matches());


		assertFalse(Validator.INST_CHARS.matcher("1234+235gd").matches());
		assertTrue(Validator.INST_CHARS.matcher("1234-235gd").matches());
		assertTrue(Validator.INST_CHARS.matcher("1234-23_5gd").matches());
		assertTrue(Validator.INST_CHARS.matcher("1234-235g,d").matches());
		assertTrue(Validator.INST_CHARS.matcher("m1234@shb.dd.com").matches());
		assertTrue(Validator.INST_CHARS.matcher("1234-235gd(Version12)").matches());
		assertFalse(Validator.INST_CHARS.matcher("123#4-23@5g:d").matches());
		assertFalse(Validator.INST_CHARS.matcher("123#4-23@5g:d").matches());
		assertFalse(Validator.INST_CHARS.matcher("").matches());

		
		// TEST star with Grandfather
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher("/1234/234*/a/").matches());
		assertFalse(Validator.GRANDFATHERED_ACTION_CHARS.matcher("/1234/234*/a/").matches());
		assertFalse(Validator.GRANDFATHERED_INST_CHARS.matcher("/1234/234*234/a/").matches());
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher("*").matches());
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher("2345*").matches());
		assertFalse(Validator.GRANDFATHERED_INST_CHARS.matcher("2345*d").matches());
		
		
		
		for( char c=0x20;c<0x7F;++c) {
			boolean b;
			switch(c) {
				case '?':
				case '|':
				case '*':
					continue; // test separately
				case '~':
				case ',':
					b = false;
					break;
				default:
					b=true;
			}
//			System.out.println("ABC"+c+"123");
			assertEquals(b,Validator.GRANDFATHERED_INST_CHARS.matcher("ABC"+c+"123").matches());
			assertEquals(b,Validator.GRANDFATHERED_INST_CHARS.matcher(c+"123").matches());
			assertEquals(b,Validator.GRANDFATHERED_INST_CHARS.matcher("123"+c).matches());
			assertEquals(b,Validator.GRANDFATHERED_INST_CHARS.matcher("/123"+c).matches());
			assertEquals(b,Validator.GRANDFATHERED_INST_CHARS.matcher("/123/"+c+"/").matches());
			assertEquals(b,Validator.GRANDFATHERED_INST_CHARS.matcher("/123/*/"+c+"/").matches());
//			if(b!=b2) {
//				System.out.println("ABC"+c+"123");
//			}
//			assertEquals(b,b2);

		}
		
		assertFalse(Validator.ID_CHARS.matcher("abc").matches());
		assertFalse(Validator.ID_CHARS.matcher("").matches());
		assertTrue(Validator.ID_CHARS.matcher("abc@att.com").matches());
		assertTrue(Validator.ID_CHARS.matcher("ab-me@att.com").matches());
		assertTrue(Validator.ID_CHARS.matcher("ab-me_.x@att._-com").matches());
		
		assertFalse(Validator.NAME_CHARS.matcher("ab-me_.x@att._-com").matches());
		assertTrue(Validator.NAME_CHARS.matcher("ab-me").matches());
		assertTrue(Validator.NAME_CHARS.matcher("ab-me_.xatt._-com").matches());

		
		// Test Reported Bugs 
		// AAF 680
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher(
				"UserStory00059_IT_usersCanNotChangeASettingForAnAcreIfTheyHaveACR_WritePrivilegesButNoEnvWritePrivilege_a"
				).matches());
		assertTrue(Validator.GRANDFATHERED_ACTION_CHARS.matcher(
				"Administrator"
				).matches());
	
		// SCMPR...
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher(
				"_()`!@#\\$%^=+][{}<>/.-valid.app.name-is_good"
				).matches());
		// SWM AAF-713
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher(
				":something:somethingelse"
				).matches());
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher(
				"Nonstart colon:something:somethingelse"
				).matches());

		// Benita 10/20/2015
		assertTrue(Validator.GRANDFATHERED_INST_CHARS.matcher(
				"com.att.aft.swm:swm-cli"
				).matches());

		// JG1555 7/22/2016
		assertTrue(Validator.INST_CHARS.matcher(
				"/!com.att.*/role/write").matches());
		assertTrue(Validator.INST_CHARS.matcher(
				":!com.att.*:role:write").matches());

	}

}
