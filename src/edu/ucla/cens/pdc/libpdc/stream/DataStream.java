/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.stream;

import edu.ucla.cens.pdc.libpdc.Constants;
import edu.ucla.cens.pdc.libpdc.SystemState;
import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.datastructures.StreamInfo;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import edu.ucla.cens.pdc.libpdc.iApplication;
import edu.ucla.cens.pdc.libpdc.iDataStream;
import edu.ucla.cens.pdc.libpdc.iState;
import edu.ucla.cens.pdc.libpdc.transport.PDCNode;
import edu.ucla.cens.pdc.libpdc.transport.PDCPublisher;
import edu.ucla.cens.pdc.libpdc.transport.PDCReceiver;
import edu.ucla.cens.pdc.libpdc.util.Log;
import edu.ucla.cens.pdc.libpdc.util.StringUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ohmage.pdv.OhmagePDVGlobals;

/**
 *
 * @author Derek Kulinski
 */
public class DataStream implements iDataStream, iState {
	public DataStream(CCNHandle handle, iApplication app, String name)
			throws IOException
	{
		this(handle, app, name, false);
	}

	public DataStream(CCNHandle handle, iApplication app, String name,
			boolean restore)
			throws IOException
	{
		GlobalConfig config = GlobalConfig.getInstance();
		byte[] byte_in;
		SystemState.DataStream state;

		if (restore) {
			byte_in = config.loadConfig("datastream_".concat(app.getAppName()), name);

			state = SystemState.DataStream.parseFrom(byte_in);

			this.app = app;
			this.data_stream_id = state.getDataStreamId();
			setupObject(handle, state);

			try {
				if (state.hasPublisher())
					_publisher = new PDCPublisher(state.getPublisher());

				for (SystemState.PDCReceiver rec_state : state.getReceiversList()) {
					final PDCReceiver receiver = new PDCReceiver(rec_state);
					_receivers.add(receiver);
				}
			}
			catch (MalformedContentNameStringException ex) {
				throw new Error("URI stored is malformed", ex);
			}

		} else {
			Log.info(String.format("Generating new data stream %s for %s",
					name, app.getAppName()));

			this.app = app;
			this.data_stream_id = name;
			setupObject(handle);
		}
	}
	
	public DataStream(CCNHandle handle, iApplication app, String name,
			boolean restore, String username)
	throws IOException
	{
		this(handle, app, name, restore);
		_username = username;
	}

	protected final void setupObject(CCNHandle handle)
	{
		setupObject(handle, null);
	}

	protected final void setupObject(CCNHandle handle,
			SystemState.DataStream state)
	{
		assert app != null;
		assert data_stream_id != null && !data_stream_id.equals("");
		assert !data_stream_id.startsWith("/") : "stream id: " + data_stream_id;

		_store = GlobalConfig.setupDataStorage(app, data_stream_id);

		try {
			if (state != null && state.hasTransport())
				this._transport = new StreamTransport(handle, 
						this, state.getTransport());
			else
				this._transport = new StreamTransport(handle, this);
		}
		catch (PDCException ex) {
			throw new Error("Unable to instantiate transmission class", ex);
		}

		try {
			final GlobalConfig config = GlobalConfig.getInstance();
			final ContentName key_name = _transport.base_name.append(
					Constants.STR_CONTROL).append("key");
			final KeyLocator locator = new KeyLocator(key_name);

			config.getKeyManager().setKeyLocator(
					_transport._encryptor._stream_key_digest, locator);
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error("Unable to generate a key name", ex);
		}
	}

	public Storage getStorage()
	{
		return _store;
	}

	public StreamTransport getTransport()
	{
		return _transport;
	}

	public void processStreamInfo(StreamInfo data)
	{
		_transport.getEncryptor().setDecryptKey(data.getStreamKey());
	}

	public ContentName getPublisherStreamURI()
	{
		ContentName uri;
		ContentName result;

		assert app != null : "Datastream should be always associated to application";
		assert _publisher != null : "The publisher needs to be defined to call this method";

		uri = _publisher.uri;
		assert uri != null : "Publisher should always have URI";

		result = uri;
		return result;
		
	}
	
	public ContentName getPublisherStreamURIOhmage()
	{
		ContentName uri;
		ContentName result;

		assert app != null : "Datastream should be always associated to application";
		assert _publisher != null : "The publisher needs to be defined to call this method";

		uri = _publisher.uri;
		assert uri != null : "Publisher should always have URI";
		return uri;
	}

	public ContentName getReceiverStreamURI(PDCReceiver receiver)
			throws MalformedContentNameStringException
	{
		ContentName uri, result;

		assert app != null;
		assert receiver != null;

		if (!_receivers.contains(receiver))
			throw new Error("Got not my receiver (bug?)");

		uri = receiver.uri;
		assert uri != null;

		result = uri.append(app.getAppName()).append(data_stream_id);

		return result;
	}

	public void setPublisher(PDCPublisher node)
	{
		_publisher = node;
	}

	public PDCPublisher getPublisher()
	{
		return _publisher;
	}

	public synchronized void addReceiver(PDCReceiver node)
	{
		_receivers.add(node);
	}

	public synchronized void delReceiver(PDCReceiver node)
	{
		_receivers.remove(node);
	}

	public synchronized Collection<PDCReceiver> getReceivers()
	{
		return _receivers;
	}
	
	public String getUsername() {
		return _username;
	}
	
	public synchronized void setUsername(String username) {
		_username = username;
	}

	public synchronized PDCReceiver name2receiver(ContentName name_id)
	{
		for (PDCReceiver receiver : _receivers) {
			if (!receiver.uri.equals(name_id))
				continue;

			return receiver;
		}

		return null;
	}

	/**
	 * Generate a key locator to fetch public key of another node
	 * @param node node of which KeyLocator we want
	 * @return key locator
	 */
	KeyLocator getKeyLocator(PDCNode node)
	{
		assert node != null;

		PublisherPublicKeyDigest digest;
		ContentName name;

		digest = node.getPublickeyDigest();
		assert digest != null;

		try {
			name = node.uri.append(OhmagePDVGlobals.getAppInstance()).
					append(data_stream_id).append(
					Constants.STR_CONTROL).append("key");

			return new KeyLocator(name, digest);
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error("Unable to generate a valid key locator", ex);
		}
	}

	public String setupPublisher(PDCPublisher publisher)
	{
		assert publisher != null;

		Log.info("### GENERATING AND GIVING AUTHENTICATOR TO THE USER ### (Step 2)");

		final String auth_string = StringUtil.genrateRandomString(10);
		// publisher.setAuthenticator(auth_string);
                publisher.setAuthenticator("test");
                Log.info("End of authenticator setup");
		return auth_string;
	}

	public boolean setupReceiver(PDCReceiver receiver, String auth_string)
			throws PDCTransmissionException
	{
		assert receiver != null;
		assert auth_string != null && !auth_string.equals("");

		receiver.setAuthenticator(auth_string);

		return _transport.initiateSetup(receiver);
	}

	public void requestPull()
	{
		for (PDCReceiver receiver : _receivers)
			try {
				requestPull(receiver);
			}
			catch (PDCTransmissionException ex) {
				Log.error("Error while trying to pull data from " + receiver.uri.
						toURIString() + ": " + ex.getLocalizedMessage());
			}
	}

	public void requestPull(PDCReceiver receiver)
			throws PDCTransmissionException
	{
		try {
			_transport.requestPull(receiver);
		}
		catch (MalformedContentNameStringException ex) {
			throw new PDCTransmissionException(ex.getLocalizedMessage(), ex);
		}
		catch (IOException ex) {
			throw new PDCTransmissionException(ex.getLocalizedMessage(), ex);
		}
	}

	public String getLowestRecordId()
	{
		return _transport.getLowestRecordId();
	}

	public String getLowestRecordId(PDCReceiver receiver)
	{
		return _transport.getLowestRecordId(receiver);
	}

	public boolean fetchNewData()
			throws PDCTransmissionException
	{
		return _transport.fetchNewData();
	}

	@Override
	public String toString()
	{
		List<String> data = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();

		sb.append(this.getClass().getSimpleName());
		sb.append('[');
		data.add(data_stream_id);
		data.add(app.getAppName());
		data.add(_store.toString());
		data.add(_transport.toString());
		data.add(_publisher == null ? null : _publisher.toString());
		data.add(_receivers.toString());
		sb.append(StringUtil.join(data, "; "));
		sb.append(']');

		return sb.toString();
	}

// <editor-fold defaultstate="collapsed" desc="serialization code">
	public String getStateGroupName()
	{
		return "datastream_" + app.getAppName();
	}

	public String getStateKey()
	{
		return data_stream_id;
	}

	public void storeState()
			throws IOException
	{
		GlobalConfig config = GlobalConfig.getInstance();

		config.saveConfig(this);
	}

	public void storeStateRecursive()
			throws IOException
	{
		storeState();
	}

	SystemState.DataStream getObjectState()
	{
		SystemState.DataStream.Builder builder = SystemState.DataStream.newBuilder();

		builder.setDataStreamId(data_stream_id);
		builder.setTransport(_transport.getObjectState());

		if (_publisher != null)
			builder.setPublisher(_publisher.getObjectState());

		for (PDCReceiver receiver : _receivers)
			builder.addReceivers(receiver.getObjectState());

		return builder.build();
	}

	public byte[] stateToByteArray()
	{
		return getObjectState().toByteArray();
	}
	
	public void setTablename(String tablename)
	{
		_tablename = tablename;
	}
	
	public String getTablename()
	{
		return _tablename;
	}
	
	public boolean isStreamSetup() {
		return _is_setup;
	}
	
	public void updateStreamSetupStatus(StreamInfo si) {
		byte[] enc_key = si.getStreamKey();
		byte[] stream_enc_key = _transport._encryptor.getEncryptKey();
		if(Arrays.equals(enc_key, stream_enc_key))
			_is_setup = true;
		else 
			_is_setup = false;
	}
// </editor-fold>

	/**
	 * application associated with the DS
	 */
	public transient final iApplication app;

	/**
	 * id of the data stream
	 */
	public String data_stream_id;

	/**
	 * instance of class responsible for the storage of data
	 */
	protected transient Storage _store;

	/**
	 * instance of class responsible for transferring data
	 * over the network
	 */
	protected StreamTransport _transport;

	/**
	 * publisher of the DS
	 */
	protected PDCPublisher _publisher = null;

	/**
	 * receivers of our DS
	 */
	protected List<PDCReceiver> _receivers = new ArrayList<PDCReceiver>();
	
	/**
	 * UserName to Store associate stream with a user
	 */
	protected String _username;
	
	/**
	 * Stream MYSQL tablename
	 */
	protected String _tablename;
	
	/**
	 * 
	 */
	private boolean _is_setup = false;
	 

}
