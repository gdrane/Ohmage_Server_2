package org.ohmage.pdv.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.ucla.cens.pdc.libpdc.iApplication;
import edu.ucla.cens.pdc.libpdc.datastructures.DataRecord;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCDatabaseException;
import edu.ucla.cens.pdc.libpdc.stream.Storage;
import org.ohmage.dao.PDVDataDaos;
import org.ohmage.exception.DataAccessException;


public class MYSQLDataStorage extends Storage {

	public MYSQLDataStorage(iApplication app, String data_stream_id) {
		super(app, data_stream_id);
		_app = app;
		_data_stream_id = data_stream_id;
	}
	
	public String getLastEntry(String pub_id) {
		return _lastProcessedID.get(pub_id);
	}
	
	public void updateLastEntry(String userName, String origin_id, String new_val) {
		PDVDataDaos.updateLastEntry(userName, origin_id, new_val);
	}

	@Override
	public String getLastEntry() throws PDCDatabaseException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getRangeIds() throws PDCDatabaseException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getRangeIds(String start) throws PDCDatabaseException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getRangeIds(String start, String end)
			throws PDCDatabaseException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataRecord getRecord(String id) throws PDCDatabaseException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean insertRecord(DataRecord record) throws PDCDatabaseException {
		try {
			return PDVDataDaos.insertRecord(record);
		} catch (DataAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	public String getLastEntry(String userName, String origin_id) {
		return PDVDataDaos.getLastEntry(userName, origin_id);
	}
	
	public void initializeStream(String id)
	{
		_lastProcessedID.put(id, "0");
	}
	
	Map<String, String> _lastProcessedID = new HashMap<String, String>();
	private iApplication _app = null;
	private String _data_stream_id = null;

}
