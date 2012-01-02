/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.datastructures;

import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCParseException;
import edu.ucla.cens.pdc.libpdc.iBSONExportable;
import edu.ucla.cens.pdc.libpdc.transport.PDCReceiver;
import org.bson.BSONDecoder;
import org.bson.BSONEncoder;
import org.bson.BSONObject;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;

/**
 *
 * @author Derek Kulinski
 */
public class Authenticator implements iBSONExportable {
	private static final String STR_AUTHENTICATOR = "authenticator";

	private static final String STR_KEY_DIGEST = "key_digest";

	private DBObject _data;

	public Authenticator()
	{
	}

	public Authenticator(DataStream stream, PDCReceiver receiver)
			throws PDCException
	{
		assert stream != null;
		assert receiver != null;

		String authenticator;
		PublisherPublicKeyDigest digest_obj;
		byte[] digest;

		digest_obj = stream.getTransport().getEncryptor().getStreamKeyDigest();
		assert digest_obj != null;

		digest = digest_obj.digest();
		authenticator = receiver.getAuthenticator();
		if (authenticator == null)
			throw new PDCException("No authenticator set");

		_data = new BasicDBObject();
		_data.put(STR_AUTHENTICATOR, authenticator);
		_data.put(STR_KEY_DIGEST, digest);
	}

	public String getAuthenticator()
	{
		assert _data != null;
		assert _data.containsField(STR_AUTHENTICATOR);

		return (String) _data.get(STR_AUTHENTICATOR);
	}

	public byte[] getKeyDigest()
	{
		assert _data != null;
		assert _data.containsField(STR_KEY_DIGEST);

		return (byte[]) _data.get(STR_KEY_DIGEST);
	}

	public byte[] toBSON()
	{
		BSONEncoder encoder = new BSONEncoder();

		assert _data != null : "_data is null";
		assert _data.containsField(STR_AUTHENTICATOR) : "no authenticator";
		assert _data.containsField(STR_KEY_DIGEST) : "no key digest";

		return encoder.encode(_data);
	}

	public Authenticator fromBSON(byte[] data)
			throws PDCParseException
	{
		BSONDecoder decoder = new BSONDecoder();

		try {
			final BSONObject bo = decoder.readObject(data);
			_data = new BasicDBObject(bo.toMap());
		}
		catch (RuntimeException ex) {
			throw new PDCParseException("unable to parse data", ex);
		}

		return this;
	}
}
