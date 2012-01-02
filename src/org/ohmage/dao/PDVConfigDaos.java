package org.ohmage.dao;

import javax.sql.DataSource;

public class PDVConfigDaos extends Dao {

	protected PDVConfigDaos(DataSource dataSource) {
		super(dataSource);
		instance = this;
	}
	
	public static void saveEntry(String obj_group, String obj_key, byte[] data)
	{

		instance.getJdbcTemplate().execute(
				SQL_USE_DATABASE);
		boolean entry_exists = instance.getJdbcTemplate().queryForObject(
				SQL_CHECK_ENTRY_EXISTS,
				new Object[] {obj_group, obj_key},
				Boolean.class
				);
		if(!entry_exists) {
			instance.getJdbcTemplate().update(
					SQL_INSERT_CONFIG_ENTRY,
					new Object[] {obj_group, obj_key, data}
					);
		} else {
			instance.getJdbcTemplate().update(
					SQL_UPDATE_EXISTING_ENTRY,
					new Object[]{data, obj_group, obj_key}
					);
		}
	}
	
	public static byte[] loadEntry(String obj_group, String obj_key)
	{
		instance.getJdbcTemplate().execute(
				SQL_USE_DATABASE);
		boolean entry_exists = instance.getJdbcTemplate().queryForObject(
				SQL_CHECK_ENTRY_EXISTS,
				new Object[] {obj_group, obj_key},
				Boolean.class
				);
		if(!entry_exists)
			return null;
		return instance.getJdbcTemplate().queryForObject(
				SQL_SELECT_ENTRY,
				new Object[] { obj_group, obj_key },
				byte[].class
				);
	}
	
	public static void removeEntry(String obj_group, String obj_key)
	{
		instance.getJdbcTemplate().execute(
				SQL_USE_DATABASE);
		boolean entry_exists = instance.getJdbcTemplate().queryForObject(
				SQL_CHECK_ENTRY_EXISTS,
				new Object[] {obj_group, obj_key},
				Boolean.class
				);
		if(entry_exists)
		{
			instance.getJdbcTemplate().update(
					SQL_DELETE_ENTRY,
					new Object[] { obj_group, obj_key}
			);
		}
	}
	
	// The single instance of this class as the constructor should only ever be
	// called once by Spring.
	private static PDVConfigDaos instance;
	
	/*
	private static final String SQL_EXISTS_TABLE = 
			"SELECT EXISTS(" +
			"SELECT count(*) " +
			"FROM information_schema.tables " +
			"WHERE table_schema = andwellness" +
			"AND table_name = pdvconfig" +
			")";
	
	private static final String SQL_CREATE_IF_NOT_EXISTS_TABLE = 
			"CREATE TABLE IF NOT EXISTS pdvconfig (" +
			"obj_group TINYTEXT, " +
			"obj_key TINYTEXT, " +
			"byte_out BLOB, " +
			"PRIMARY_KEY(obj_group, obj_key)" +
			")";
	*/
	private static final String SQL_INSERT_CONFIG_ENTRY =
			"INSERT INTO pdvconfig(obj_group, obj_key, byte_out) VALUES(" +
			"?, ?, ?" +
			")";
	
	private static final String SQL_CHECK_ENTRY_EXISTS = 
			"SELECT EXISTS( " +
			"SELECT * " +
			"FROM pdvconfig " +
			"WHERE obj_group = ? " +
			"AND obj_key = ? " +
			")";
	
	private static final String SQL_UPDATE_EXISTING_ENTRY = 
			"UPDATE pdvconfig " +
			"SET byte_out = ? " +
			"WHERE obj_group = ? " +
			"AND obj_key = ?"; 
	
	private static final String SQL_SELECT_ENTRY = 
			"SELECT byte_out " +
			"FROM pdv_config" +
			"WHERE obj_group = ? " +
			"AND obj_key = ? ";
	private static final String SQL_DELETE_ENTRY =
			"DELETE FROM pdv_config " +
			"WHERE obj_group = ? " +
			"AND obj_key = ? ";
	
	private static final String db_name = "andwellness";
	private static final String SQL_USE_DATABASE = 
			"USE " +
			db_name;

}
