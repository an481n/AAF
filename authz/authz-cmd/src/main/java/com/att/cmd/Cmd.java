/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cmd;

import java.io.PrintWriter;
import java.io.StringReader;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import com.att.cadi.CadiException;
import com.att.cadi.LocatorException;
import com.att.cadi.client.Future;
import com.att.cadi.client.Rcli;
import com.att.cadi.client.Retryable;
import com.att.cadi.http.HMangr;
import com.att.inno.env.APIException;
import com.att.inno.env.Data.TYPE;
import com.att.inno.env.Env;
import com.att.inno.env.util.Chrono;
import com.att.rosetta.env.RosettaDF;
import com.att.rosetta.env.RosettaEnv;

import aaf.v2_0.Error;
import aaf.v2_0.History;
import aaf.v2_0.History.Item;
import aaf.v2_0.Request;


public abstract class Cmd {
	private static final String AAF_DEFAULT_REALM = "aaf_default_realm";
	
	private static final DateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
	protected static final String BLANK = "";
	protected static final String COMMA = ","; // for use in splits

	protected static final int lineLength = 80;

	private final static String hformat = "%-23s %-5s %-20s %-35s\n";

	public static final String STARTDATE = "startdate";
	public static final String ENDDATE = "enddate";
	
	private String name;
	private final Param[] params;
	private int required;
	protected final Cmd parent;
	protected final List<Cmd> children;
	private final ConcurrentHashMap<Class<?>,RosettaDF<?>> dfs = new ConcurrentHashMap<Class<?>,RosettaDF<?>>();
	public final AAFcli aafcli;
	protected Env env;

	public Cmd(AAFcli aafcli, String name, Param ... params) {
		this(aafcli,null, name,params);
	}

	public Cmd(Cmd parent, String name, Param ... params) {
		this(parent.aafcli,parent, name,params);
	}

	Cmd(AAFcli aafcli, Cmd parent, String name, Param ... params) {
		this.parent = parent;
		this.aafcli = aafcli;
		this.env = aafcli.env;
		if(parent!=null) {
			parent.children.add(this);
		}
		children = new ArrayList<Cmd>();
		this.params = params;
		this.name = name;
		required=0;
		for(Param p : params) {
			if(p.required) {
				++required;
			}
		}
	}
	
	public final int exec(int idx, String ... args) throws CadiException, APIException, LocatorException {
		if(args.length-idx<required) {
			throw new CadiException(build(new StringBuilder("Too few args: "),null).toString());
		}
		return _exec(idx,args);
	}
	
	protected abstract int _exec(int idx, final String ... args) throws CadiException, APIException, LocatorException;
	
	public void detailedHelp(int indent,StringBuilder sb) {
	}

	protected void detailLine(StringBuilder sb, int length, String s) {
		multiChar(sb,length,' ',0);
		sb.append(s);
	}

	public void apis(int indent,StringBuilder sb) {
	}

	protected void api(StringBuilder sb, int _indent, String meth, String pathInfo, Class<?> cls,boolean head) {
	        int indent = _indent;
		if(head) {
			sb.append('\n');
			detailLine(sb,indent,"APIs:");
		}
		indent+=2;
		multiChar(sb,indent,' ',0);
		sb.append(meth);
		sb.append(' ');
		sb.append(pathInfo);
		String cliString = aafcli.typeString(cls,true);
		if(indent+meth.length()+pathInfo.length()+cliString.length()+2>80) {
			sb.append(" ...");
			multiChar(sb,indent+3+meth.length(),' ',0);
		} else { // same line
			sb.append(' ');
		}
		sb.append(cliString);
	}

	protected void multiChar(StringBuilder sb, int length, char c, int indent) {
		sb.append('\n');
		for(int i=0;i<indent;++i)sb.append(' ');
		for(int i=indent;i<length;++i)sb.append(c);
	}

	public StringBuilder build(StringBuilder sb, StringBuilder detail) {
		if(name!=null) {
			sb.append(name);
			sb.append(' ');
		}
		int line = sb.lastIndexOf("\n")+1;
		if(line<0) {
			line=0;
		}
		int indent = sb.length()-line;
		for(Param p : params) {
			sb.append(p.required?'<':'[');
			sb.append(p.tag);
			sb.append(p.required?"> ": "] ");
		}
		
		boolean first = true;
		for(Cmd child : children) {
			if(first) {
				first = false;
			} else if(detail==null) {
				multiChar(sb,indent,' ',0);
			} else {
				// Write parents for Detailed Report
				Stack<String> stack = new Stack<String>();
				for(Cmd c = child.parent;c!=null;c=c.parent) {
					if(c.name!=null) {
						stack.push(c.name);
					}
				}
				if(!stack.isEmpty()) {
					sb.append("  ");
					while(!stack.isEmpty()) {
						sb.append(stack.pop());
						sb.append(' ');
					}
				}
			}
			child.build(sb,detail);
			if(detail!=null) {
				child.detailedHelp(4, detail);
				// If Child wrote something, then add, bracketing by lines
				if(detail.length()>0) {
					multiChar(sb,80,'-',2);
					sb.append(detail);
					sb.append('\n');
					multiChar(sb,80,'-',2);
					sb.append('\n');
					detail.setLength(0); // reuse
				} else {
					sb.append('\n');
				}
			}
		}
		return sb;
	}
	
	protected void error(Future<?> future) {
		StringBuilder sb = new StringBuilder("Failed");
		String desc = future.body();
		int code = future.code();
		if(desc==null || desc.length()==0) {
			withCode(sb,code);
		} else if(desc.startsWith("{")) {
			StringReader sr = new StringReader(desc);
			try {
				// Note: 11-18-2013, JG1555.  This rather convoluted Message Structure required by TSS Restful Specs, reflecting "Northbound" practices.
				Error err = getDF(Error.class).newData().in(TYPE.JSON).load(sr).asObject();
				sb.append(" [");
				sb.append(err.getMessageId());
				sb.append("]: ");
				String messageBody = err.getText();
				List<String> vars = err.getVariables();
				int pipe;
				for (int varCounter=0;varCounter<vars.size();) {
					String var = vars.get(varCounter);
					++varCounter;
					if (messageBody.indexOf("%" + varCounter) >= 0) {
						if((pipe = var.indexOf('|'))>=0) {  // In AAF, we use a PIPE for Choice
							if (aafcli.isTest()) {
								String expiresStr = var.substring(pipe);
								var = var.replace(expiresStr, "[Placeholder]");
							} else {
								StringBuilder varsb = new StringBuilder(var);
								varsb.deleteCharAt(pipe);
								var = varsb.toString();
							}
							messageBody = messageBody.replace("%" + varCounter, varCounter-1 + ") " + var);
						} else {
							messageBody = messageBody.replace("%" + varCounter, var);
						}
					}
				}
				sb.append(messageBody);
			} catch (Exception e) {
				withCode(sb,code);
				sb.append(" (Note: Details cannot be obtained from Error Structure)");
			}
		} else if(desc.startsWith("<html>")){ // Core Jetty, etc sends HTML for Browsers
			withCode(sb,code);
		} else {
			sb.append(" with code ");
			sb.append(code);
			sb.append(", ");
			sb.append(desc);
		}
		pw().println(sb);
	}

	
	private void withCode(StringBuilder sb, Integer code) {
		sb.append(" with code ");
		sb.append(code);
		switch(code) {
			case 401:
				sb.append(" (HTTP Not Authenticated)");
				break;
			case 403:
				sb.append(" (HTTP Forbidden)");
				break;
			case 404:
				sb.append(" (HTTP Not Found)");
				break;
			default:
		}
	}

	/**
	 * Consistently set start and end dates from Requests (all derived from Request)
	 * @param req
	 */
	protected void setStartEnd(Request req) {
		// Set Start/End Dates, if exist
		String str;
		if((str = env.getProperty(Cmd.STARTDATE,null))!=null) {
			req.setStart(Chrono.timeStamp(Date.valueOf(str)));
		}
		
		if((str = env.getProperty(Cmd.ENDDATE,null))!=null) {
			req.setEnd(Chrono.timeStamp(Date.valueOf(str)));
		}
	}

	@SuppressWarnings("unchecked")
	protected<T> RosettaDF<T> getDF(Class<T> cls) throws APIException {
		RosettaDF<T> rdf = (RosettaDF<T>)dfs.get(cls);
		if(rdf == null) {
			rdf = env().newDataFactory(cls);
			dfs.put(cls, rdf);
		}
		return rdf;
	}

	public void activity(History history, String header) {
		if (history.getItem().isEmpty()) {
			int start = header.indexOf('[');
			if (start >= 0) {
				pw().println("No Activity Found for " + header.substring(start));
			}
		} else {
			pw().println(header);
			for(int i=0;i<lineLength;++i)pw().print('-');
			pw().println();
								
			pw().format(hformat,"Date","Table","User","Memo");
			for(int i=0;i<lineLength;++i)pw().print('-');
			pw().println();
	
			// Save Server time by Sorting locally
			List<Item> items = history.getItem();
			java.util.Collections.sort(items, new Comparator<Item>() {
				@Override
				public int compare(Item o1, Item o2) {
					return o2.getTimestamp().compare(o1.getTimestamp());
				}
			});
			
			for(History.Item item : items) {
				GregorianCalendar gc = item.getTimestamp().toGregorianCalendar();
				pw().format(hformat,
					dateFmt.format(gc.getTime()),
					item.getTarget(),
					item.getUser(),
					item.getMemo());
			}
		}
	}
	
	/**
	 * Turn String Array into a | delimited String
	 * @param options
	 * @return
	 */
	public static String optionsToString(String[] options) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for(String s : options) {
			if(first) {
				first = false;
			} else {
				sb.append('|');
			}
			sb.append(s);
		}
		return sb.toString();
	}
	
	/**
	 * return which index number the Option matches.
	 * 
	 * throws an Exception if not part of this Option Set
	 * 
	 * @param options
	 * @param test
	 * @return
	 * @throws Exception
	 */
	public int whichOption(String[] options, String test) throws CadiException {
		for(int i=0;i<options.length;++i) {
			if(options[i].equals(test)) {
				return i;
			}
		}
		throw new CadiException(build(new StringBuilder("Invalid Option: "),null).toString());
	}

	protected RosettaEnv env() {
		return aafcli.env;
	}

	protected HMangr hman() {
		return aafcli.hman;
	}

	public<RET> RET same(Retryable<RET> retryable) throws APIException, CadiException, LocatorException {
		// We're storing in AAFCli, because we know it's always the same, and single threaded
		if(aafcli.prevCall!=null) {
			retryable.item(aafcli.prevCall.item());
			retryable.lastClient=aafcli.prevCall.lastClient;
		}
		
		RET ret = aafcli.hman.same(aafcli.ss,retryable);
		
		// Store last call in AAFcli, because Cmds are all different instances.
		aafcli.prevCall = retryable;
		return ret;
	}

	public<RET> RET all(Retryable<RET> retryable) throws APIException, CadiException, LocatorException {
		this.setQueryParamsOn(retryable.lastClient);
		return aafcli.hman.all(aafcli.ss,retryable);
	}

	public<RET> RET oneOf(Retryable<RET> retryable,String host) throws APIException, CadiException, LocatorException {
		this.setQueryParamsOn(retryable.lastClient);
		return aafcli.hman.oneOf(aafcli.ss,retryable,true,host);
	}

	protected PrintWriter pw() {
		return AAFcli.pw;
	}

	public String getName() {
		return name;
	}
	
	public void reportHead(String ... str) {
		pw().println();
		boolean first = true;
		int i=0;
		for(String s : str) {
			if(first) {
				if(++i>1) {
					first = false;
					pw().print("[");
				}
			} else {
				pw().print("] [");
			}
			pw().print(s);
		}
		if(!first) {
			pw().print(']');
		}
		pw().println();
		reportLine();
	}
	
	public String reportColHead(String format, String ...  args) {
		pw().format(format,(Object[])args);
		reportLine();
		return format;
	}

	public void reportLine() {
		for(int i=0;i<lineLength;++i)pw().print('-');
		pw().println();
	}
	
	protected void setQueryParamsOn(Rcli<?> rcli) {
		StringBuilder sb=null;
		String force;
		if((force=aafcli.forceString())!=null) {
			sb = new StringBuilder("force=");
			sb.append(force);
		}
		if(aafcli.addRequest()) {
			if(sb==null) {
				sb = new StringBuilder("request=true");
			} else {
				sb.append("&request=true");
			}
		}
		if(sb!=null && rcli!=null) {
			rcli.setQueryParams(sb.toString());
		}
	}
//
//	/**
//	 * If Force is set, will return True once only, then revert to "FALSE".
//	 *  
//	 * @return
//	 */
//	protected String checkForce() {
//		if(TRUE.equalsIgnoreCase(env.getProperty(FORCE, FALSE))) {
//			env.setProperty(FORCE, FALSE);
//			return "true";
//		}
//		return FALSE;
//	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(parent==null) { // ultimate parent
			build(sb,null);
			return sb.toString();
		} else {
			return parent.toString();
		}
	}
	
	public String getOrgRealm() {
		return env.getProperty(AAF_DEFAULT_REALM);
	}
}
