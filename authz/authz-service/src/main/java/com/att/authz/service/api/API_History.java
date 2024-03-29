/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.authz.service.api;

import static com.att.authz.layer.Result.OK;
import static com.att.cssa.rserv.HttpMethods.GET;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.att.aft.dme2.internal.jetty.http.HttpStatus;
import com.att.authz.env.AuthzTrans;
import com.att.authz.facade.AuthzFacade;
import com.att.authz.layer.Result;
import com.att.authz.service.AuthAPI;
import com.att.authz.service.Code;
import com.att.authz.service.mapper.Mapper.API;
import com.att.dao.aaf.cass.Status;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * Pull certain types of History Info
 * 
 * Specify yyyymm as 
 * 	single - 201504
 *  commas 201503,201504
 *  ranges 201501-201504
 *  combinations 201301,201401,201501-201504
 *  
 *
 */
public class API_History {
	/**
	 * Normal Init level APIs
	 * 
	 * @param authzAPI
	 * @param facade
	 * @throws Exception
	 */
	public static void init(AuthAPI authzAPI, AuthzFacade facade) throws Exception {
		/**
		 * Get History
		 */
		authzAPI.route(GET,"/authz/hist/user/:user",API.HISTORY,new Code(facade,"Get History by User", true) {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				int[] years;
				int descend;
				try {
					years = getYears(req);
					descend = decending(req);
				} catch(Exception e) {
					context.error(trans, resp, Result.err(Status.ERR_BadData, e.getMessage()));
					return;
				}

				Result<Void> r = context.getHistoryByUser(trans, resp, pathParam(req,":user"),years,descend);
				switch(r.status) {
					case OK:
						resp.setStatus(HttpStatus.OK_200); 
						break;
					default:
						context.error(trans,resp,r);
				}
			}
		});

		/**
		 * Get History by NS
		 */
		authzAPI.route(GET,"/authz/hist/ns/:ns",API.HISTORY,new Code(facade,"Get History by Namespace", true) {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				int[] years;
				int descend;
				try {
					years = getYears(req);
					descend = decending(req);
				} catch(Exception e) {
					context.error(trans, resp, Result.err(Status.ERR_BadData, e.getMessage()));
					return;
				}
				
				Result<Void> r = context.getHistoryByNS(trans, resp, pathParam(req,":ns"),years,descend);
				switch(r.status) {
					case OK:
						resp.setStatus(HttpStatus.OK_200); 
						break;
					default:
						context.error(trans,resp,r);
				}
			}
		});

		/**
		 * Get History by Role
		 */
		authzAPI.route(GET,"/authz/hist/role/:role",API.HISTORY,new Code(facade,"Get History by Role", true) {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				int[] years;
				int descend;
				try {
					years = getYears(req);
					descend = decending(req);
				} catch(Exception e) {
					context.error(trans, resp, Result.err(Status.ERR_BadData, e.getMessage()));
					return;
				}

				Result<Void> r = context.getHistoryByRole(trans, resp, pathParam(req,":role"),years,descend);
				switch(r.status) {
					case OK:
						resp.setStatus(HttpStatus.OK_200); 
						break;
					default:
						context.error(trans,resp,r);
				}
			}
		});

		/**
		 * Get History by Perm Type
		 */
		authzAPI.route(GET,"/authz/hist/perm/:type",API.HISTORY,new Code(facade,"Get History by Perm Type", true) {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				int[] years;
				int descend;
				try {
					years = getYears(req);
					descend = decending(req);
				} catch(Exception e) {
					context.error(trans, resp, Result.err(Status.ERR_BadData, e.getMessage()));
					return;
				}
				
				Result<Void> r = context.getHistoryByPerm(trans, resp, pathParam(req,":type"),years,descend);
				switch(r.status) {
					case OK:
						resp.setStatus(HttpStatus.OK_200); 
						break;
					default:
						context.error(trans,resp,r);
				}
			}
		});
	}

	// Check if Ascending
	private static int decending(HttpServletRequest req) {
		if("true".equalsIgnoreCase(req.getParameter("desc")))return -1;
		if("true".equalsIgnoreCase(req.getParameter("asc")))return 1;
		return 0;
	}
	
	// Get Common "yyyymm" parameter, or none
	private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyyMM");
	
	private static int[] getYears(HttpServletRequest req) throws NumberFormatException {
		String yyyymm = req.getParameter("yyyymm");
		ArrayList<Integer> ai= new ArrayList<Integer>();
		if(yyyymm==null) {
			GregorianCalendar gc = new GregorianCalendar();
			// three months is the default
			for(int i=0;i<3;++i) {
				ai.add(Integer.parseInt(FMT.format(gc.getTime())));
				gc.add(GregorianCalendar.MONTH, -1);
			}
		} else {
			for(String ym : yyyymm.split(",")) {
				String range[] = ym.split("\\s*-\\s*");
				switch(range.length) {
					case 0:
						break;
					case 1:
						if(!ym.endsWith("-")) {
							ai.add(getNum(ym));
							break;
						} else {
							range=new String[] {ym.substring(0, 6),FMT.format(new Date())};
						}
					default:
						GregorianCalendar gc = new GregorianCalendar();
						gc.set(GregorianCalendar.MONTH, Integer.parseInt(range[1].substring(4,6))-1);
						gc.set(GregorianCalendar.YEAR, Integer.parseInt(range[1].substring(0,4)));
						int end = getNum(FMT.format(gc.getTime())); 
						
						gc.set(GregorianCalendar.MONTH, Integer.parseInt(range[0].substring(4,6))-1);
						gc.set(GregorianCalendar.YEAR, Integer.parseInt(range[0].substring(0,4)));
						for(int i=getNum(FMT.format(gc.getTime()));i<=end;gc.add(GregorianCalendar.MONTH, 1),i=getNum(FMT.format(gc.getTime()))) {
							ai.add(i);
						}

				}
			}
		}
		if(ai.size()==0) {
			throw new NumberFormatException(yyyymm + " is an invalid number or range");
		}
		Collections.sort(ai);
		int ym[] = new int[ai.size()];
		for(int i=0;i<ym.length;++i) {
			ym[i]=ai.get(i);
		}
		return ym;
	}
	
	private static int getNum(String n) {
		if(n==null || n.length()!=6) throw new NumberFormatException(n + " is not in YYYYMM format");
		return Integer.parseInt(n);
	}
}
