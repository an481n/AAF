/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cadi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Random;

import com.att.cadi.Access.Level;
import com.att.cadi.config.Config;

/**
 * Key Conversion, primarily "Base64"
 * 
 * Base64 is required for "Basic Authorization", which is an important part of the overall CADI Package.
 * 
 * Note: This author found that there is not a "standard" library for Base64 conversion within Java.  
 * The source code implementations available elsewhere were surprisingly inefficient, requiring, for 
 * instance, multiple string creation, on a transaction pass.  Integrating other packages that might be
 * efficient enough would put undue Jar File Dependencies given this Framework should have none-but-Java 
 * dependencies.
 * 
 * The essential algorithm is good for a symmetrical key system, as Base64 is really just
 * a symmetrical key that everyone knows the values.  
 * 
 * This code is quite fast, taking about .016 ms for encrypting, decrypting and even .08 for key 
 * generation. The speed quality, especially of key generation makes this a candidate for a short term token 
 * used for identity.
 * 
 * It may be used to easily avoid placing Clear-Text passwords in configurations, etc. and contains 
 * supporting functions such as 2048 keyfile generation (see keygen).  This keyfile should, of course, 
 * be set to "400" (Unix) and protected as any other mechanism requires. 
 * 
 * However, this algorithm has not been tested against hackers.  Until such a time, utilize more tested
 * packages to protect Data, especially sensitive data at rest (long term). 
 *
 */
public class Symm {
	private static final byte[] DOUBLE_EQ = new byte[] {'=','='}; 
	public static final String ENC = "enc:";
	private static final SecureRandom random = new SecureRandom();
	
	public final char[] codeset;
	private final int splitLinesAt;
	private final String encoding;
	private final Convert convert;
	private final boolean endEquals;
	private AES aes = null;  // only initialized from File, and only if needed for Passwords
	
	/**
	 * This is the standard base64 Key Set.
	 * RFC 2045
	 */
	public static final Symm base64 = new Symm(
			"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
			,76, Config.UTF_8,true);

	public static final Symm base64noSplit = new Symm(
			"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
			,Integer.MAX_VALUE, Config.UTF_8,true);

	/**
	 * This is the standard base64 set suitable for URLs and Filenames
	 * RFC 4648
	 */
	public static final Symm base64url = new Symm(
			"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray()
			,76, Config.UTF_8,true);

	/**
	 * A Password set, using US-ASCII
	 * RFC 4648
	 */
	public static final Symm encrypt = new Symm(base64url.codeset,1024, "US-ASCII", false);

	/**
	 * A typical set of Password Chars
	 * Note, this is too large to fit into the algorithm. Only use with PassGen
	 */
	private static char passChars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+!@#$%^&*(){}[]?:;,.".toCharArray();
			


	/**
	 * Use this to create special case Case Sets and/or Line breaks
	 * 
	 * If you don't know why you need this, use the Singleton Method
	 * 
	 * @param codeset
	 * @param split
	 */
	public Symm(char[] codeset, int split, String charset, boolean useEndEquals) {
		this.codeset = codeset;
		splitLinesAt = split;
		encoding = charset;
		endEquals = useEndEquals;
		char prev = 0, curr=0, first = 0;
		int offset=Integer.SIZE; // something that's out of range for integer array
		
		// There can be time efficiencies gained when the underlying keyset consists mainly of ordered 
		// data (i.e. abcde...).  Therefore, we'll quickly analyze the keyset.  If it proves to have
		// too much entropy, the "Unordered" algorithm, which is faster in such cases is used.
		ArrayList<int[]> la = new ArrayList<int[]>();
		for(int i=0;i<codeset.length;++i) {
			curr = codeset[i];
			if(prev+1==curr) { // is next character in set
				prev = curr;
			} else {
				if(offset!=Integer.SIZE) { // add previous range 
					la.add(new int[]{first,prev,offset});
				}
				first = prev = curr;
				offset = curr-i;
			}
		}
		la.add(new int[]{first,curr,offset});
		if(la.size()>codeset.length/3) {
			convert = new Unordered(codeset);
		} else { // too random to get speed enhancement from range algorithm
			int[][] range = new int[la.size()][];
			la.toArray(range);
			convert = new Ordered(range);
		}
	}
	
	public Symm copy(int lines) {
		return new Symm(codeset,lines,encoding,endEquals);
	}
	
	/**
	 * Obtain a special ASCII version for Scripting, with base set of base64url use in File Names, etc. (no "/")
	 */
	public static final Symm baseCrypt() {
		return encrypt;
	}

	synchronized private AES getAES() throws IOException {
		if(aes == null) {
			try {
				byte[] bytes = new byte[AES.AES_KEY_SIZE/8];
				int offset = (Math.abs(codeset[0])+47)%(codeset.length-bytes.length);
				for(int i=0;i<bytes.length;++i) {
					bytes[i] = (byte)codeset[i+offset];
				}
				aes = new AES(bytes,0,bytes.length);
			} catch (Exception e) {
				throw new IOException(e);
			}
		}
		return aes;
	}
	
    public byte[] encode(byte[] toEncrypt) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream((int)(toEncrypt.length*1.25));
		encode(new ByteArrayInputStream(toEncrypt),baos);
		return baos.toByteArray();
	}

    public byte[] decode(byte[] encrypted) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream((int)(encrypted.length*1.25));
		decode(new ByteArrayInputStream(encrypted),baos);
		return baos.toByteArray();
	}

	/**
     *  Helper function for String API of "Encode"
     *  use "getBytes" with appropriate char encoding, etc.
     *  
     * @param str
     * @return
     * @throws IOException
     */
    public String encode(String str) throws IOException {
    	byte[] array;
    	try { 
    		array = str.getBytes(encoding);
    	} catch (IOException e) {
    		array = str.getBytes(); // take default
    	}
    	// Calculate expected size to avoid any buffer expansion copies within the ByteArrayOutput code
    	ByteArrayOutputStream baos = new ByteArrayOutputStream((int)(array.length*1.363)); // account for 4 bytes for 3 and a byte or two more
    	
    	encode(new ByteArrayInputStream(array),baos);
    	return baos.toString(encoding);
    }
    
    /**
     * Helper function for the String API of "Decode"
     * use "getBytes" with appropriate char encoding, etc.
     * @param str
     * @return
     * @throws IOException
     */
    public String decode(String str) throws IOException {
    	byte[] array;
    	try { 
    		array = str.getBytes(encoding);
    	} catch (IOException e) {
    		array = str.getBytes(); // take default
    	}
    	// Calculate expected size to avoid any buffer expansion copies within the ByteArrayOutput code
    	ByteArrayOutputStream baos = new ByteArrayOutputStream((int)(array.length*.76)); // Decoding is 3 bytes for 4.  Allocate slightly more than 3/4s
    	decode(new ByteArrayInputStream(array), baos);
    	return baos.toString(encoding);
	}

	/**
     * Convenience Function
     * 
     * encode String into InputStream and call encode(InputStream, OutputStream)
     * 
     * @param string
     * @param out
     * @throws IOException
     */
  	public void encode(String string, OutputStream out) throws IOException {
  		encode(new ByteArrayInputStream(string.getBytes()),out);
	}

	/**
	 * Convenience Function
	 * 
	 * encode String into InputStream and call decode(InputStream, OutputStream)
	 * 
	 * @param string
	 * @param out
	 * @throws IOException
	 */
	public void decode(String string, OutputStream out) throws IOException {
		decode(new ByteArrayInputStream(string.getBytes()),out);
	}

    public void encode(InputStream is, OutputStream os, byte[] prefix) throws IOException {
    	os.write(prefix);
    	encode(is,os);
    }

	/** 
     * encode InputStream onto Output Stream
     * 
     * @param is
     * @param estimate
     * @return
     * @throws IOException
     */
    public void encode(InputStream is, OutputStream os) throws IOException {
    	// StringBuilder sb = new StringBuilder((int)(estimate*1.255)); // try to get the right size of StringBuilder from start.. slightly more than 1.25 times 
    	int prev=0;
    	int read, idx=0, line=0;
    	boolean go;
    	do {
    		read = is.read();
    		if(go = read>=0) {
    			if(line>=splitLinesAt) {
	    			os.write('\n');
	    			line = 0;
	    		}
	    		switch(++idx) { // 1 based reading, slightly faster ++
	    			case 1: // ptr is the first 6 bits of read
	    				os.write(codeset[read>>2]);
	    				prev = read;
	    				break;
	    			case 2: // ptr is the last 2 bits of prev followed by the first 4 bits of read
	    				os.write(codeset[((prev & 0x03)<<4) | (read>>4)]);
    					prev = read;
	    				break;
	    			default: //(3+) 
	    					// Char 1 is last 4 bits of prev plus the first 2 bits of read
	    				    // Char 2 is the last 6 bits of read
	    				os.write(codeset[(((prev & 0xF)<<2) | (read>>6))]);
	    				if(line==splitLinesAt) { // deal with line splitting for two characters
	    					os.write('\n');
	    					line=0;
	    				}
	    				os.write(codeset[(read & 0x3F)]);
	    				++line;
	    				idx = 0;
	    				prev = 0;
	    		}
	    		++line;
    		} else { // deal with any remaining bits from Prev, then pad
    			switch(idx) {
    				case 1: // just the last 2 bits of prev
	    				os.write(codeset[(prev & 0x03)<<4]);
    					if(endEquals)os.write(DOUBLE_EQ);
    					break;
    				case 2: // just the last 4 bits of prev
	    				os.write(codeset[(prev & 0xF)<<2]);
	    				if(endEquals)os.write('=');
	    				break;
    			}
    			idx = 0;
    		}
    		
    	} while(go);
    }

    public void decode(InputStream is, OutputStream os, int skip) throws IOException {
    	is.skip(skip);
    	decode(is,os);
    }

    /**
	 * Decode InputStream onto OutputStream
	 * @param is
	 * @param os
	 * @throws IOException
	 */
    public void decode(InputStream is, OutputStream os) throws IOException {
	   int read, idx=0;
	   int prev=0, index;
	   	while((read = is.read())>=0) {
	   		index = convert.convert(read);
	   		if(index>=0) {
	    		switch(++idx) { // 1 based cases, slightly faster ++
	    			case 1: // index goes into first 6 bits of prev
	    				prev = index<<2; 
	    				break;
	    			case 2: // write second 2 bits of into prev, write byte, last 4 bits go into prev
	    				os.write((byte)(prev|(index>>4)));
	    				prev = index<<4;
	    				break;
	    			case 3: // first 4 bits of index goes into prev, write byte, last 2 bits go into prev
	    				os.write((byte)(prev|(index>>2)));
	    				prev = index<<6;
	    				break;
	    			default: // (3+) | prev and last six of index
	    				os.write((byte)(prev|(index&0x3F)));
	    				idx = prev = 0;
	    		}
	   		}
	   	};
	   	os.flush();
   }
   
   /**
    * Interface to allow this class to choose which algorithm to find index of character in Key
    *
    */
   private interface Convert {
	   public int convert(int read) throws IOException;
   }

   /**
    * Ordered uses a range of orders to compare against, rather than requiring the investigation
    * of every character needed.
    *
    */
   private static final class Ordered implements Convert {
	   private int[][] range;
	   public Ordered(int[][] range) {
		   this.range = range;
	   }
	   public int convert(int read) throws IOException {
		   switch(read) {
			   case -1: 
			   case '=':
			   case '\n': 
				   return -1;
		   }
		   for(int i=0;i<range.length;++i) {
			   if(read >= range[i][0] && read<=range[i][1]) {
				   return read-range[i][2];
			   }
		   }
		   throw new IOException("Unacceptable Character in Stream");
	   }
   }
   
   /**
    * Unordered, i.e. the key is purposely randomized, simply has to investigate each character
    * until we find a match.
    *
    */
   private static final class Unordered implements Convert {
	   private char[] codec;
	   public Unordered(char[] codec) {
		   this.codec = codec;
	   }
	   public int convert(int read) throws IOException {
		   switch(read) {
			   case -1: 
			   case '=':
			   case '\n': 
				   return -1;
		   }
		   for(int i=0;i<codec.length;++i) {
			   if(codec[i]==read)return i;
		   }
		  // don't give clue in Encryption mode
		  throw new IOException("Unacceptable Character in Stream");
	   }
   }

   /**
    * Generate a 2048 based Key from which we extract our code base
    * 
    * @return
    * @throws IOException
    */
   public byte[] keygen() throws IOException {
		byte inkey[] = new byte[0x600];
		new SecureRandom().nextBytes(inkey);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(0x800);
		base64url.encode(new ByteArrayInputStream(inkey), baos);
		return baos.toByteArray();
   }
   
   /**
    * Obtain a Symm from "keyfile" (Config.KEYFILE) property
    * 
    * @param acesss
    * @return
    */
   public static Symm obtain(Access access) {
		Symm symm = Symm.baseCrypt();

		String keyfile = access.getProperty(Config.CADI_KEYFILE,null);
		if(keyfile!=null) {
			File file = new File(keyfile);
			try {
				access.log(Level.INIT, Config.CADI_KEYFILE,"points to",file.getCanonicalPath());
			} catch (IOException e1) {
				access.log(Level.INIT, Config.CADI_KEYFILE,"points to",file.getAbsolutePath());
			}
			if(file.exists()) {
				try {
					FileInputStream fis = new FileInputStream(file);
					try {
						symm = Symm.obtain(fis);
					} finally {
						try {
						   fis.close();
						} catch (IOException e) {
						}
					}
				} catch (IOException e) {
					access.log(e, "Cannot load keyfile");
				}
			}
		}
		return symm;
   }
  /**
   *  Create a new random key 
   */
  public Symm obtain() throws IOException {
		byte inkey[] = new byte[0x800];
		new Random().nextBytes(inkey);
		return obtain(inkey);
  }
  
  /**
   * Obtain a Symm from 2048 key from a String
   * 
   * @param key
   * @return
   * @throws IOException
   */
  public static Symm obtain(String key) throws IOException {
	  return obtain(new ByteArrayInputStream(key.getBytes()));
  }
  
  /**
   * Obtain a Symm from 2048 key from a Stream
   * 
   * @param is
   * @return
   * @throws IOException
   */
  public static Symm obtain(InputStream is) throws IOException {
	  ByteArrayOutputStream baos = new ByteArrayOutputStream();
	  try {
		  base64url.decode(is, baos);
	  } catch (IOException e) {
		  // don't give clue
		  throw new IOException("Invalid Key");
	  }
	  byte[] bkey = baos.toByteArray();
	  if(bkey.length<0x88) { // 2048 bit key
		  throw new IOException("Invalid key");
	  }
	  return baseCrypt().obtain(bkey);
  }

  /**
   * Convenience for picking up Keyfile
   * 
   * @param f
   * @return
   * @throws IOException
   */
  public static Symm obtain(File f) throws IOException {
	  FileInputStream fis = new FileInputStream(f);
	  try {
		  return obtain(fis);
	  } finally {
		  fis.close();
	  }
  }
  /**
   * Decrypt into a String
   *
   *  Convenience method
   * 
   * @param password
   * @return
   * @throws IOException
   */
  public String enpass(String password) throws IOException {
	  ByteArrayOutputStream baos = new ByteArrayOutputStream();
	  enpass(password,baos);
	  return new String(baos.toByteArray());
  }

  /**
   * Create an encrypted password, making sure that even short passwords have a minimum length.
   * 
   * @param password
   * @param os
   * @throws IOException
   */
  public void enpass(String password, OutputStream os) throws IOException {
	  // reimplement for Opensource
  }

  /**
   * Decrypt a password into a String
   * 
   * Convenience method
   * 
   * @param password
   * @return
   * @throws IOException
   */
  public String depass(String password) throws IOException {
	  if(password==null)return null;
	  ByteArrayOutputStream baos = new ByteArrayOutputStream();
	  depass(password,baos);
	  return new String(baos.toByteArray());
  }
  
  /**
   * Decrypt a password
   * 
   * Skip Symm.ENC
   * 
   * @param password
   * @param os
   * @return
   * @throws IOException
   */
  public long depass(String password, OutputStream os) throws IOException {
	  // reimplement for Open Source
	  return 0;
  }

  public static String randomGen(int numBytes) {
	  return randomGen(passChars,numBytes);  
  }
  
  public static String randomGen(char[] chars ,int numBytes) {
	    int rint;
	    StringBuilder sb = new StringBuilder(numBytes);
	    for(int i=0;i<numBytes;++i) {
	    	rint = random.nextInt(chars.length);
	    	sb.append(chars[rint]);
	    }
	    return sb.toString();
  }
  public Symm obtain(byte[] key) throws IOException {
	  // reimplement for Open Source
	  return base64;
	}
}