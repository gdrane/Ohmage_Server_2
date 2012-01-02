/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.datastructures;

import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCParseException;
import edu.ucla.cens.pdc.libpdc.iBSONExportable;
import org.bson.BSONDecoder;
import org.bson.BSONEncoder;

/**
 *
 * @author Derek Kulinski
 */
public class StreamInfo implements iBSONExportable {
	private DBObject _data;

	public StreamInfo()
	{
	}

	public StreamInfo(DataStream stream)
	{
		_data = new BasicDBObject();

		_data.put("enc_key", stream.getTransport().getEncryptor().getEncryptKey());
	}

	public byte[] getStreamKey()
	{
		assert _data != null;
		assert _data.containsField("enc_key");

		return (byte[]) _data.get("enc_key");
	}

	public byte[] toBSON()
	{
		BSONEncoder encoder = new BSONEncoder();

		assert _data != null : "_data is null";
		assert _data.containsField("enc_key") : "no stream_key";
//		assert _data.containsField("block_size") : "no block_size";

		return encoder.encode(_data);
	}

	public StreamInfo fromBSON(byte[] data)
					throws PDCParseException
	{
		BSONDecoder decoder = new BSONDecoder();

		try {
			_data = new BasicDBObject(decoder.readObject(data).toMap());
		} catch (RuntimeException ex) {
			throw new PDCParseException("unable to parse data", ex);
		}

		return this;
	}

	@Override
	public String toString()
	{
		return JSON.serialize(_data);
	}
}
