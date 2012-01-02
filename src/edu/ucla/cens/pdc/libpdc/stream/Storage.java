/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.stream;

import edu.ucla.cens.pdc.libpdc.datastructures.DataRecord;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCDatabaseException;
import edu.ucla.cens.pdc.libpdc.iApplication;
import java.util.List;

/**
 *
 * @author Derek Kulinski
 */
abstract public class Storage {
	public Storage(iApplication app, String data_stream_id)
	{
		assert app != null;
		assert data_stream_id != null && data_stream_id.length() != 0;

		// Initialize your storage here as needed
	}

	abstract public String getLastEntry()
					throws PDCDatabaseException;

	abstract public List<String> getRangeIds()
					throws PDCDatabaseException;

	abstract public List<String> getRangeIds(String start)
					throws PDCDatabaseException;

	abstract public List<String> getRangeIds(String start, String end)
					throws PDCDatabaseException;

	abstract public DataRecord getRecord(String id)
					throws PDCDatabaseException;

	abstract public boolean insertRecord(DataRecord record)
					throws PDCDatabaseException;
}
