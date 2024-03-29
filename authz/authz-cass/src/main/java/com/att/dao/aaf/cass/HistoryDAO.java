/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.dao.aaf.cass;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.att.authz.env.AuthzTrans;
import com.att.authz.layer.Result;
import com.att.dao.AbsCassDAO;
import com.att.dao.CassDAOImpl;
import com.att.dao.Loader;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

/**
 * History
 * 
 * 
 * History is a special case, because we don't want Updates or Deletes...  Too likely to mess up history.
 * 
 * jg1555 9-9-2013 - Found a problem with using "Prepare".  You cannot prepare anything with a "now()" in it, as
 * it is evaluated once during the prepare, and kept.  That renders any use of "now()" pointless.  Therefore
 * the Create function needs to be run fresh everytime.
 * 
 * Fixed in Cassandra 1.2.6 https://issues.apache.org/jira/browse/CASSANDRA-5616
 *
 */
public class HistoryDAO extends CassDAOImpl<AuthzTrans, HistoryDAO.Data> {
	private static final String TABLE = "history";

	public static final SimpleDateFormat monthFormat = new SimpleDateFormat("yyyyMM");
//	private static final SimpleDateFormat dayTimeFormat = new SimpleDateFormat("ddHHmmss");

	private String[] helpers;

	private HistLoader defLoader;

	private AbsCassDAO<AuthzTrans, Data>.PSInfo readByUser, readBySubject, readByYRMN;

	public HistoryDAO(AuthzTrans trans, Cluster cluster, String keyspace) {
		super(trans, HistoryDAO.class.getSimpleName(),cluster,keyspace,Data.class,TABLE,ConsistencyLevel.LOCAL_ONE,ConsistencyLevel.ANY);
		init(trans);
	}

	public HistoryDAO(AuthzTrans trans, AbsCassDAO<AuthzTrans,?> aDao) {
		super(trans, HistoryDAO.class.getSimpleName(),aDao,Data.class,TABLE,ConsistencyLevel.LOCAL_ONE,ConsistencyLevel.ANY);
		init(trans);
	}


	private static final int KEYLIMIT = 1;
	public static class Data {
		public UUID id;
		public int	yr_mon;
		public String user;
		public String action;
		public String target;
		public String subject;
		public String  memo;
//		Map<String, String>  detail = null;
//		public Map<String, String>  detail() {
//			if(detail == null) {
//				detail = new HashMap<String, String>();
//			}
//			return detail;
//		}
		public ByteBuffer reconstruct;
	}
	
	private static class HistLoader extends Loader<Data> {
		public HistLoader(int keylimit) {
			super(keylimit);
		}

		@Override
		public Data load(Data data, Row row) {
			data.id = row.getUUID(0);
			data.yr_mon = row.getInt(1);
			data.user = row.getString(2);
			data.action = row.getString(3);
			data.target = row.getString(4);
			data.subject = row.getString(5);
			data.memo = row.getString(6);
//			data.detail = row.getMap(6, String.class, String.class);
			data.reconstruct = row.getBytes(7);
			return data;
		}

		@Override
		protected void key(Data data, int idx, Object[] obj) {
			obj[idx]=data.id;
		}

		@Override
		protected void body(Data data, int _idx, Object[] obj) {
		    	int idx = _idx;
			obj[idx]=data.yr_mon;
			obj[++idx]=data.user;
			obj[++idx]=data.action;
			obj[++idx]=data.target;
			obj[++idx]=data.subject;
			obj[++idx]=data.memo;
//			obj[++idx]=data.detail;
			obj[++idx]=data.reconstruct;		
		}
	};
	
	private void init(AuthzTrans trans) {
		// Loader must match fields order
		defLoader = new HistLoader(KEYLIMIT);
		helpers = setCRUD(trans, TABLE, Data.class, defLoader);

		// Need a specialty Creator to handle the "now()"
		// 9/9/2013 - jg - Just great... now() is evaluated once on Client side, invalidating usage (what point is a now() from a long time in the past?
		// Unless this is fixed, we're putting in non-prepared statement
		// Solved in Cassandra.  Make sure you are running 1.2.6 Cassandra or later. https://issues.apache.org/jira/browse/CASSANDRA-5616	
		replace(CRUD.create, new PSInfo(trans, "INSERT INTO history (" +  helpers[FIELD_COMMAS] +
					") VALUES(now(),?,?,?,?,?,?,?)", 
					new HistLoader(0) {
						@Override
						protected void key(Data data, int idx, Object[] obj) {
						}
					},writeConsistency)
				);
//		disable(CRUD.Create);
		
		replace(CRUD.read, new PSInfo(trans, SELECT_SP +  helpers[FIELD_COMMAS] +
				" FROM history WHERE id = ?", defLoader,readConsistency) 
//				new HistLoader(2) {
//					@Override
//					protected void key(Data data, int idx, Object[] obj) {
//						obj[idx]=data.yr_mon;
//						obj[++idx]=data.id;
//					}
//				})
			);
		disable(CRUD.update);
		disable(CRUD.delete);
		
		readByUser = new PSInfo(trans, SELECT_SP + helpers[FIELD_COMMAS] + 
				" FROM history WHERE user = ?", defLoader,readConsistency);
		readBySubject = new PSInfo(trans, SELECT_SP + helpers[FIELD_COMMAS] + 
				" FROM history WHERE subject = ? and target = ? ALLOW FILTERING", defLoader,readConsistency);
		readByYRMN = new PSInfo(trans, SELECT_SP + helpers[FIELD_COMMAS] + 
				" FROM history WHERE yr_mon = ?", defLoader,readConsistency);
		async(true); //TODO dropping messages with Async
	}

	public static Data newInitedData() {
		Data data = new Data();
		Date now = new Date();
		data.yr_mon = Integer.parseInt(monthFormat.format(now));
		// data.day_time = Integer.parseInt(dayTimeFormat.format(now));
		return data;		
	}

	public Result<List<Data>> readByYYYYMM(AuthzTrans trans, int yyyymm) {
		Result<ResultSet> rs = readByYRMN.exec(trans, "yr_mon", yyyymm);
		if(rs.notOK()) {
			return Result.err(rs);
		}
		return extract(defLoader,rs.value,null,dflt);
	}

	/**
	 * Gets the history for a user in the specified year and month
	 * year - the year in yyyy format
	 * month -  the month in a year ...values 1 - 12
	 **/
	public Result<List<Data>> readByUser(AuthzTrans trans, String user, int ... yyyymm) {
		if(yyyymm.length==0) {
			return Result.err(Status.ERR_BadData, "No or invalid yyyymm specified");
		}
		Result<ResultSet> rs = readByUser.exec(trans, "user", user);
		if(rs.notOK()) {
			return Result.err(rs);
		}
		return extract(defLoader,rs.value,null,yyyymm.length>0?new YYYYMM(yyyymm):dflt);
	}
	
	public Result<List<Data>> readBySubject(AuthzTrans trans, String subject, String target, int ... yyyymm) {
		if(yyyymm.length==0) {
			return Result.err(Status.ERR_BadData, "No or invalid yyyymm specified");
		}
		Result<ResultSet> rs = readBySubject.exec(trans, "subject", subject, target);
		if(rs.notOK()) {
			return Result.err(rs);
		}
		return extract(defLoader,rs.value,null,yyyymm.length>0?new YYYYMM(yyyymm):dflt);
	}
	
	private class YYYYMM implements Accept<Data> {
		private int[] yyyymm;
		public YYYYMM(int yyyymm[]) {
			this.yyyymm = yyyymm;
		}
		@Override
		public boolean ok(Data data) {
			int dym = data.yr_mon;
			for(int ym:yyyymm) {
				if(dym==ym) {
					return true;
				}
			}
			return false;
		}
		
	};
	
}
