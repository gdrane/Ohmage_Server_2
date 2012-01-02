/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.stream;

import com.mongodb.util.JSON;
import edu.ucla.cens.pdc.libpdc.Constants;
import edu.ucla.cens.pdc.libpdc.SystemState;
import edu.ucla.cens.pdc.libpdc.datastructures.DataRecord;
import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.core.PDCKeyManager;
import edu.ucla.cens.pdc.libpdc.datastructures.StreamInfo;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCDatabaseException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCParseException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import edu.ucla.cens.pdc.libpdc.transport.PDCPublisher;
import edu.ucla.cens.pdc.libpdc.transport.PDCReceiver;
import edu.ucla.cens.pdc.libpdc.util.Log;
import edu.ucla.cens.pdc.libpdc.util.StringUtil;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.bson.types.ObjectId;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.ContentVerifier;
import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ccnx.ccn.protocol.SignedInfo;

/**
 *
 * @author Derek Kulinski
 */
public class StreamTransport {
	public StreamTransport(CCNHandle handle, DataStream stream)
			throws PDCException
	{
		assert handle != null;
		assert stream != null;

		this._stream = stream;
		this._encryptor = new DataEncryptor(stream);
		this._ccn_handle = handle;

		try {
			this.base_name = _stream.app.getBaseName().append(_stream.data_stream_id);
		}
		catch (MalformedContentNameStringException ex) {
			throw new PDCException("Unable to create base name for the data stream",
					ex);
		}

		setupObject();
	}

	public StreamTransport(CCNHandle handle, DataStream stream,
			SystemState.StreamTransport state)
			throws PDCException
	{
		assert handle != null;
		assert stream != null;

		this._stream = stream;
		this._encryptor = new DataEncryptor(stream, state.getEncryptor());
		this._ccn_handle = handle;

		try {
			this.base_name = _stream.app.getBaseName().append(_stream.data_stream_id);
		}
		catch (MalformedContentNameStringException ex) {
			throw new PDCException("Unable to create base name for the data stream",
					ex);
		}

		setupObject();
	}

	private void setupObject()
	{
		GlobalConfig config = GlobalConfig.getInstance();
		PDCKeyManager keymgr;
		PublisherPublicKeyDigest digest;

		keymgr = config.getKeyManager();
		assert keymgr != null;

		assert _encryptor != null;
		digest = _encryptor.getStreamKeyDigest();

		//Sanity check
		if (digest != null) {
			final PublicKey public_key = keymgr.getPublicKey(digest);
			final PrivateKey private_key = keymgr.getSigningKey(digest);

			if (public_key == null || private_key == null) {
				Log.error("!!! have keys in config but not in key manager !!!");
				Log.debug(public_key);
				Log.debug(private_key);
				digest = null;
			}
		}

		if (digest != null)
			Log.info("Keys for " + _stream.data_stream_id + " loaded, hash: " + digest);
		else {
			Log.info("Generating keys for " + _stream.data_stream_id + "...");

			digest = _encryptor.generateStreamKeys();
			if (digest == null)
				throw new Error("Unable to obtain stream keys");
			else
				Log.info("Generated keys for " + _stream.data_stream_id + ": " + digest);
		}
	}

	/**
	 * @return the _encryptor
	 */
	public DataEncryptor getEncryptor()
	{
		return _encryptor;
	}

	/**
	 * Responds with data record
	 * @param interest interest asking for the record
	 * @param data_id record id
	 * @return true if record was published
	 * @throws PDCTransmissionException
	 */
	public boolean publishDataRecord(Interest interest, String data_id)
			throws PDCTransmissionException
	{
		DataRecord record;
		byte[] data;

		try {
			record = _stream.getStorage().getRecord(data_id);

			if (record == null) {
				Log.warning(data_id + " does not exist");
				return false;
			}

			data = _encryptor.encryptRecord(record);
			return publishData(interest, data, 1);
		}
		catch (PDCEncryptionException ex) {
			throw new PDCTransmissionException("Error encrypting the data", ex);
		}
		catch (PDCDatabaseException ex) {
			throw new PDCTransmissionException("Error accessing the database", ex);
		}
	}

	public boolean publishRecordList(Interest interest, ContentName arguments)
			throws PDCDatabaseException, PDCEncryptionException,
			PDCTransmissionException
	{
		Collection<String> ids;
		String data;
		byte[] cipher;

		assert interest != null;
		assert arguments != null;

		// Content of the data packet
		if (arguments.count() == 0)
			ids = _stream.getStorage().getRangeIds();
		else if (arguments.count() == 1)
			ids = _stream.getStorage().getRangeIds(arguments.stringComponent(0));
		else
			ids = _stream.getStorage().getRangeIds(arguments.stringComponent(0),
					arguments.stringComponent(1));

		data = StringUtil.join(ids, "\n");

		cipher = _encryptor.encryptData(data.getBytes());

		return publishData(interest, cipher, 1);
	}

	public boolean publishACK(Interest interest)
			throws PDCTransmissionException
	{
		String answer = "ACK";

		//TODO: ACK shouldn't be marked as ENCR

		return publishData(interest, answer.getBytes(), 1);
	}

	private boolean publishData(Interest interest, byte[] data, int freshness)
			throws PDCTransmissionException
	{
		GlobalConfig config = GlobalConfig.getInstance();
		SignedInfo si;
		ContentObject co;
		KeyManager keymgr;
		PublisherPublicKeyDigest digest;
		KeyLocator key_locator;
		PrivateKey signing_key;

		assert interest != null;
		assert data != null;

		keymgr = config.getKeyManager();
		assert keymgr != null;

		digest = _encryptor.getStreamKeyDigest();
		assert digest != null;

		key_locator = keymgr.getKeyLocator(digest);
		assert key_locator != null;

		signing_key = keymgr.getSigningKey(digest);
		assert signing_key != null;

		try {
			si = new SignedInfo(digest, SignedInfo.ContentType.ENCR, key_locator,
					freshness, null);
			co = new ContentObject(interest.name(), si, data, signing_key);

			_ccn_handle.put(co);

			return true;
		}
		catch (Exception ex) {
			throw new PDCTransmissionException("Error while publishing data", ex);
		}
	}

	boolean initiateSetup(PDCReceiver receiver)
			throws PDCTransmissionException
	{
		assert receiver != null;

		ContentName name;
		ContentObject co;

		if (receiver.getAuthenticator() == null)
			throw new PDCTransmissionException("Authenticator is not set");

		Log.info("### SENDING SETUP INTEREST TO THE RECEIVER ### (Step 4)");

		try {
			name = _stream.getReceiverStreamURI(receiver).append(Constants.STR_CONTROL).
					append("setup");
			co = _ccn_handle.get(name, SystemConfiguration.MEDIUM_TIMEOUT);

			// No verification since trust isn't established yet

			if (co != null && new String(co.content()).equals("ACK"))
				return true;
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error("Unable to form a proper name for the request", ex);
		}
		catch (IOException ex) {
			throw new PDCTransmissionException(
					"Error when trying to express an interest", ex);
		}

		return false;
	}

	public boolean pullInterestHandler(Interest interest)
	{
		try {
			publishACK(interest);
		}
		catch (PDCTransmissionException ex) {
			Log.error("Error while sending ack: " + ex.getLocalizedMessage());
		}

		Log.info(
				"### GOT PULL REQUEST; ATTEMPING TO FETCH DATA FROM THE PRODUCER ### (Step T2)");

		try {
			fetchNewData();
		}
		catch (PDCTransmissionException ex) {
			Log.error("Error while pulling new records: " + ex.getLocalizedMessage());
			return false;
		}

		return true;
	}
	
	public boolean pullInterestHandlerOhmage(ContentName postfix, Interest interest)
	{
		try {
			publishACK(interest);
		}
		catch (PDCTransmissionException ex) {
			Log.error("Error while sending ack: " + ex.getLocalizedMessage());
		}

		Log.info(
				"### GOT PULL REQUEST; ATTEMPING TO FETCH DATA FROM THE PRODUCER ### (Step T2)");

		try {
			fetchNewDataOhmage(postfix);
		}
		catch (PDCTransmissionException ex) {
			Log.error("Error while pulling new records: " + ex.getLocalizedMessage());
			return false;
		}

		return true;
	}

	public synchronized void fetchStreamInfo()
			throws PDCTransmissionException
	{
		GlobalConfig config = GlobalConfig.getInstance();
		ContentName uri;
		ContentObject object;
		byte[] decrypted;

		try {
			uri = _stream.getPublisherStreamURI().append(Constants.STR_CONTROL).append(
					"stream_info").append(config.getRoot());
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error(
					String.format(
					"Unable to create proper URI (%s, %s, %s)",
					_stream.app.getAppName(), _stream.data_stream_id, config.getRoot()),
					ex);
		}

		Log.debug("requesting stream_info data for " + uri.toURIString());

		Log.info("### REQUESTING STREAM INFO ### (Step 7)");

		try {
			object = _ccn_handle.get(uri, SystemConfiguration.MEDIUM_TIMEOUT);
		}
		catch (IOException ex) {
			throw new PDCTransmissionException("Unable to get the data", ex);
		}

		if (object == null)
			return;

		// Checking authenticity
		final ContentVerifier verifier = _ccn_handle.keyManager().getDefaultVerifier();
		if (!verifier.verify(object))
			throw new PDCTransmissionException(
					"Got a packet which I can't verify credentials.");

		try {
			decrypted = _encryptor.decryptAsymData(object.content());
		}
		catch (PDCEncryptionException ex) {
			throw new PDCTransmissionException("Unable to decrypt the data", ex);
		}

		try {
			StreamInfo si = new StreamInfo().fromBSON(decrypted);
			Log.debug("Got StreamInfo: " + si);
			_stream.processStreamInfo(si);
		}
		catch (PDCParseException ex) {
			throw new PDCTransmissionException("Unable to process the data", ex);
		}
	}

	public List<String> fetchList()
			throws PDCTransmissionException
	{
		return fetchList(null, null);
	}

	public List<String> fetchList(String start)
			throws PDCTransmissionException
	{
		return fetchList(start, null);
	}

	public List<String> fetchList(String start, String end)
			throws PDCTransmissionException
	{
		ContentName uri = null;
		ContentObject response;

		byte[] decoded;
		List<String> result;

		try {
			uri = _stream.getPublisherStreamURI().append(Constants.STR_CONTROL).append(
					"list");

			if (start != null) {
				uri = uri.append(start);
				if (end != null)
					uri = uri.append(end);
			} else if (end != null)
				throw new Error("Start is null, but end is not null???");
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error(String.format(
					"Unable to create proper URI (%s, %s, %s, %s)",
					_stream.app.getAppName(), _stream.data_stream_id, start, end));
		}

		Log.debug("Requesting: " + uri.toURIString());

		try {
			response = _ccn_handle.get(uri, SystemConfiguration.LONG_TIMEOUT);
		}
		catch (IOException ex) {
			throw new PDCTransmissionException("Unable to get the data", ex);
		}

		if (response == null)
			return null;

		// Checking authenticity
		final ContentVerifier verifier = _ccn_handle.keyManager().getDefaultVerifier();
		if (!verifier.verify(response)) {
			Log.warning("Got a packet which I can't verify credentials.");
			return null;
		}

		try {
			decoded = _encryptor.decryptData(response.content());
		}
		catch (PDCEncryptionException ex) {
			if (isFinalFailure(false))
				throw new PDCTransmissionException("Unable to decrypt the message", ex);

			Log.info(
					"### UNABLE TO DECRYPT THE DATA; (RE)FETCHING STREAMINFO ### (Step 7)");

			fetchStreamInfo();
			return null;
		}
		isFinalFailure(true);

		//Special case (empty list returned)
		if (decoded.length == 0)
			return new LinkedList<String>();

		result = Arrays.asList(new String(decoded).split("\\\n"));

		return result;
	}

	public DataRecord fetchRecord(String content_id)
			throws PDCTransmissionException
	{
		DataRecord record;
		ContentName publisher_ds, data_uri;
		ContentObject response;

		assert content_id != null;

		Log.info("### FETCHING RECORD " + content_id + " ### (Step T4)");

		publisher_ds = _stream.getPublisherStreamURI();

		try {
			data_uri = _stream.getPublisherStreamURI().append(Constants.STR_DATA).
					append(content_id);

			try {
				response = _ccn_handle.get(data_uri, SystemConfiguration.LONG_TIMEOUT);
			}
			catch (IOException ex) {
				throw new PDCTransmissionException("Unable to fetch the data", ex);
			}

			if (response == null)
				return null;

			// Checking authenticity
			final ContentVerifier verifier = _ccn_handle.keyManager().
					getDefaultVerifier();
			if (!verifier.verify(response)) {
				Log.warning("Got a packet which I can't verify credentials.");
				return null;
			}

			try {
				record = _encryptor.decryptRecord(response.content());
			}
			catch (PDCEncryptionException ex) {
				throw new PDCTransmissionException("Unable to decrypt message", ex);
			}

			return record;
		}
		catch (MalformedContentNameStringException ex) {
			throw new PDCTransmissionException(String.format(
					"Unable to create proper URI (%s, %s, %s)",
					publisher_ds, Constants.STR_CONTROL, content_id), ex);
		}
	}
	
	public DataRecord fetchRecordOhmage(ContentName postfix, String content_id)
			throws PDCTransmissionException
	{
		DataRecord record;
		ContentName publisher_ds, data_uri;
		ContentObject response;
		String phoneIMEI = null;
		String Hpasswd = null;
		try {
			phoneIMEI = new String(_encryptor.decryptAsymData(postfix.stringComponent(0).getBytes()));
			Hpasswd = new String(_encryptor.decryptAsymData(postfix.stringComponent(1).getBytes()));
		} catch (PDCEncryptionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assert Hpasswd != null : "Private key for server not setup";
		assert content_id != null;

		Log.info("### FETCHING RECORD " + content_id + " ### (Step T4)");

		publisher_ds = _stream.getPublisherStreamURIOhmage();

		try {
			data_uri = _stream.getPublisherStreamURIOhmage().append(phoneIMEI).
					append(new String(_encryptor.encryptAsymData(_stream.getPublisher(), 
							Hpasswd.getBytes()))).append(_stream.data_stream_id).
							append(Constants.STR_DATA).append(content_id);
			try {
				response = _ccn_handle.get(data_uri, SystemConfiguration.LONG_TIMEOUT);
			}
			catch (IOException ex) {
				throw new PDCTransmissionException("Unable to fetch the data", ex);
			}

			if (response == null)
				return null;

			// Checking authenticity
			final ContentVerifier verifier = _ccn_handle.keyManager().
					getDefaultVerifier();
			if (!verifier.verify(response)) {
				Log.warning("Got a packet which I can't verify credentials.");
				return null;
			}

			
			record = _encryptor.decryptRecord(response.content());
			
		
			return record;
		}
		catch (MalformedContentNameStringException ex) {
			throw new PDCTransmissionException(String.format(
					"Unable to create proper URI (%s, %s, %s)",
					publisher_ds, Constants.STR_CONTROL, content_id), ex);
		}
		catch (PDCEncryptionException ex) {
			throw new PDCTransmissionException("Unable to decrypt message", ex);
		}

	}

	void requestPull(PDCReceiver receiver)
			throws PDCTransmissionException, MalformedContentNameStringException,
			IOException
	{
		ContentName pull_uri;

		if (!_stream._receivers.contains(receiver))
			return;

		pull_uri = _stream.getReceiverStreamURI(receiver);
		pull_uri = pull_uri.append(Constants.STR_CONTROL).append("pull");

		Log.debug("Requesting: " + pull_uri.toURIString());

		_ccn_handle.get(pull_uri, SystemConfiguration.LONG_TIMEOUT);
	}

	synchronized boolean fetchNewData()
			throws PDCTransmissionException
	{
		PDCPublisher publisher;
		String last_entry;
		DataRecord record;
		Collection<String> ids;
		Storage storage;

		storage = _stream.getStorage();

		publisher = _stream.getPublisher();
		if (publisher == null) {
			Log.warning("No uplink defined; fetching aborted");
			return true;
		}

		try {
			last_entry = storage.getLastEntry();
		}
		catch (PDCDatabaseException ex) {
			throw new PDCTransmissionException(
					"Unable to fetch id of last record from the database", ex);
		}

		Log.info("### REQUESTING LIST OF NEW DATA IDS ### (Step T3)");

		if (last_entry == null)
			ids = fetchList();
		else
			ids = fetchList(last_entry);

		if (ids == null) {
			Log.warning("No response when fetching ids");
			return false;
		}

		if (ids.isEmpty()) {
			Log.info("No more records to pull");
			return true;
		}

		try {
			for (final String data_id : ids) {
				Log.debug("pulling: " + data_id);
				record = fetchRecord(data_id);
				if (record == null) {
					Log.warning("No response when fetching record " + data_id);
					return false;
				}

				Log.debug("got: " + JSON.serialize(record));
				storage.insertRecord(record);
			}
		}
		catch (PDCDatabaseException ex) {
			throw new Error("Unable to insert entry to the database", ex);
		}

		return true;
	}
	
	synchronized boolean fetchNewDataOhmage(ContentName postfix)
		throws PDCTransmissionException 
	{
		PDCPublisher publisher;
		String last_entry;
		DataRecord record;
		Collection<String> ids;
		Storage storage;

		storage = _stream.getStorage();

		publisher = _stream.getPublisher();
		if (publisher == null) {
			Log.warning("No uplink defined; fetching aborted");
			return true;
		}

		try {
			last_entry = storage.getLastEntry();
		}
		catch (PDCDatabaseException ex) {
			throw new PDCTransmissionException(
					"Unable to fetch id of last record from the database", ex);
		}

		Log.info("### REQUESTING LIST OF NEW DATA IDS ### (Step T3)");

		if (last_entry == null)
			ids = fetchList();
		else
			ids = fetchList(last_entry);

		if (ids == null) {
			Log.warning("No response when fetching ids");
			return false;
		}

		if (ids.isEmpty()) {
			Log.info("No more records to pull");
			return true;
		}

		try {
			for (final String data_id : ids) {
				Log.debug("pulling: " + data_id);
				record = fetchRecordOhmage(postfix, data_id);
				if (record == null) {
					Log.warning("No response when fetching record " + data_id);
					return false;
				}

				Log.debug("got: " + JSON.serialize(record));
				// Create HTTPRequest
				
				storage.insertRecord(record);
			}
		}
		catch (PDCDatabaseException ex) {
			throw new Error("Unable to insert entry to the database", ex);
		}

		return true;
	}

	String getLowestRecordId()
	{
		String smallest = null;

		synchronized (_stream._receivers) {
			for (PDCReceiver receiver : _stream._receivers) {
				final String result = getLowestRecordId(receiver);

				// no response or no data -> return no data
				// since that's smallest value
				if (result == null || result.equals(""))
					return "";

				// no value assigned, or current value is smaller
				if (smallest == null || result.compareTo(smallest) < 0)
					smallest = result;
			}
		}

		return smallest == null ? "" : smallest;
	}

	String getLowestRecordId(PDCReceiver receiver)
	{
		ContentName uri;

		try {
			uri = _stream.getReceiverStreamURI(receiver);
			uri = uri.append(Constants.STR_CONTROL).append("last");

			Log.debug("Requesting: " + uri.toURIString());

			final ContentObject co = _ccn_handle.get(uri,
					SystemConfiguration.MEDIUM_TIMEOUT);

			if (co == null)
				return null;

			final byte[] content = co.content();

			// special case: the receiver has no data
			if (content.length == 0)
				return "";

			final String id = new String(content);
			if (!ObjectId.isValid(id)) {
				Log.error("Got invalid id: " + id + " from: "
						+ receiver.uri.toURIString());
				return "";
			}

			return id;
		}
		catch (IOException ex) {
			Log.warning("No response from " + receiver.uri.toURIString());
			return null;
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error("Unable to construct uri", ex);
		}
	}

	/**
	 * This method counts failures so we know when to give up
	 * when trying to request a key
	 * @param success was the attempt successful?
	 * @return returns true if we should give up
	 */
	private boolean isFinalFailure(boolean success)
	{
		if (!success && _invalid_key_retry-- > 0)
			return false;

		_invalid_key_retry = 1;
		return !success;
	}

	@Override
	public String toString()
	{
		List<String> data = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();

		sb.append(this.getClass().getSimpleName());
		sb.append('[');
		data.add(base_name.toString());
		data.add(_stream.data_stream_id);
		data.add(_encryptor.toString());
		data.add(_ccn_handle.toString());
		sb.append(StringUtil.join(data, "; "));
		sb.append(']');

		return sb.toString();
	}

// <editor-fold defaultstate="collapsed" desc="serialization code">
	SystemState.StreamTransport getObjectState()
	{
		SystemState.StreamTransport.Builder builder = SystemState.StreamTransport.
				newBuilder();

		builder.setEncryptor(_encryptor.getObjectState());

		return builder.build();
	}
// </editor-fold>

	final public ContentName base_name;

	final protected DataStream _stream;

	final DataEncryptor _encryptor;

	protected transient CCNHandle _ccn_handle;

	protected transient int _invalid_key_retry = 1;
}
