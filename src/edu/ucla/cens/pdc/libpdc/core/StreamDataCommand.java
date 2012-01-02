/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

import edu.ucla.cens.pdc.libpdc.Constants;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import edu.ucla.cens.pdc.libpdc.util.Log;

/**
 *
 * @author Derek Kulinski
 */
public class StreamDataCommand extends StreamCommand {
	StreamDataCommand()
			throws MalformedContentNameStringException
	{
		super(Constants.STR_DATA);
	}

	@Override
	boolean processCommand(DataStream datastream, ContentName postfix,
			Interest interest)
	{
		String record_id;

		if (postfix.count() != 1)
			return false;

		record_id = postfix.stringComponent(0);

		Log.debug(String.format(
				"Requested; app = %s, datastream = %s, record_id = %s",
				datastream.app.getAppName(), datastream.data_stream_id, record_id));
		try {
			return datastream.getTransport().publishDataRecord(interest, record_id);
		}
		catch (PDCTransmissionException ex) {
			throw new Error("Unable to fetch the data", ex);
		}
	}
}
