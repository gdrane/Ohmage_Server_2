/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

import edu.ucla.cens.pdc.libpdc.datastructures.Authenticator;
import edu.ucla.cens.pdc.libpdc.Constants;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCDatabaseException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCParseException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import java.io.IOException;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import edu.ucla.cens.pdc.libpdc.datastructures.StreamInfo;
import edu.ucla.cens.pdc.libpdc.stream.DataEncryptor;
import edu.ucla.cens.pdc.libpdc.transport.CommunicationHelper;
import edu.ucla.cens.pdc.libpdc.transport.PDCPublisher;
import edu.ucla.cens.pdc.libpdc.transport.PDCReceiver;
import edu.ucla.cens.pdc.libpdc.util.Log;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Arrays;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.impl.CCNFlowControl.SaveType;
import org.ccnx.ccn.impl.support.DataUtils;
import org.ccnx.ccn.io.content.PublicKeyObject;
import org.ccnx.ccn.protocol.CCNTime;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.PublisherID;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;

/**
 *
 * @author Derek Kulinski
 */
public class StreamControlCommands extends StreamCommand {
	StreamControlCommands()
			throws MalformedContentNameStringException
	{
		super(Constants.STR_CONTROL);
	}

	private boolean processSetup(DataStream ds, ContentName remainder,
			Interest interest)
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		PDCPublisher publisher = ds.getPublisher();
		ContentName name;
		ContentObject co;
		String publisherInformation = remainder.stringComponent(0);
		/*try {
			publisherInformation = new String(ds.getTransport().getEncryptor().
					decryptAsymData(remainder.stringComponent(0).getBytes()));
		} catch (PDCEncryptionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		assert publisherInformation != null;
		String hashedIMEI = publisherInformation.split("/")[0];
		// TODO(gdrane): USe the state information to stop replay attacks
		//String count = publisherInformation.split("/")[1];
		if (publisher == null) {
			Log.warning("Got setup request, while a publisher wasn't set up");
			return false;
		}
		try {
			ds.getTransport().publishACK(interest);
		}
		catch (PDCTransmissionException ex) {
			throw new RuntimeException(
					"Error while trying to respond to the request with an ACK",
					ex);
		}
		
		Log.info("### REQUESTING AUTHENTICATOR ### (Step 6)");

		try {
			name = publisher.uri.append(hashedIMEI).append(ds.app.getAppName()).
					append(ds.data_stream_id).append(Constants.STR_CONTROL).
					append("authenticator").append(config.getRoot());

			co = config.getCCNHandle().get(name, 
					SystemConfiguration.LONG_TIMEOUT);
			if (co == null) {
				Log.error("Got no response when requesting authenticator");
				return false;
			}

			//Verify if data is in check
			if (!co.verify(config.getKeyManager())) {
				Log.error("Authenticator content fails data verification");
				return false;
			}

			final byte[] data = ds.getTransport().getEncryptor().
					decryptAsymData(co.content());
			final Authenticator auth = new Authenticator().fromBSON(data);

			if (!Arrays.equals(auth.getKeyDigest(), co.signedInfo().
					getPublisher())) {
				Log.error(
						"Tempering detected! Data is signed with different" +
						" key than one stored in authenticator");
				return false;
			}

			if (!auth.getAuthenticator().equals(publisher.getAuthenticator())) {
				Log.error("Authenticator value doesn't match");
				return false;
			}

			Log.info("### AUTHENTICATOR RECEIVED IS CORRECT ### (Step 6)");

			Log.info("Adding key " + DataUtils.base64Encode(auth.getKeyDigest(),
					PublisherID.PUBLISHER_ID_LEN * 2) + " to trusted keys.");
			config.getKeyManager().authenticateKey(auth.getKeyDigest());
			
			Log.info("### ASKING FOR STREAM INFORMATION ### (Step 7)");
			
			name = publisher.uri.append(hashedIMEI).append(ds.app.getAppName()).
					append(ds.data_stream_id).append(Constants.STR_CONTROL).
					append("stream_info").append(config.getRoot());

			co = config.getCCNHandle().get(name, 
					SystemConfiguration.LONG_TIMEOUT);
			if (co == null) {
				Log.error("Got no response when requesting authenticator");
				return false;
			}
			
			//Verify if data is in check
			if (!co.verify(config.getKeyManager())) {
				Log.error("Authenticator content fails data verification");
				return false;
			}

			final byte[] stream_data = ds.getTransport().getEncryptor().
					decryptAsymData(co.content());
			
			ds.getTransport().getEncryptor().setDecryptKey(stream_data);
			ds.getTransport().getEncryptor().setEncryptKey(stream_data);
			
			return true;
		}
		catch (GeneralSecurityException ex) {
			Log.error("Problem while trying to verify data signature: " + ex.
					getLocalizedMessage());
		}
		catch (IOException ex) {
			Log.error("Communication problem: " + ex.getLocalizedMessage());
		}
		catch (PDCParseException ex) {
			Log.error("Unable to parse data (malformaed string?): " + ex.
					getLocalizedMessage());
		}
		catch (PDCEncryptionException ex) {
			Log.error("Unable to decrypt message: " + ex.getLocalizedMessage());
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error("Unable to form CCN name", ex);
		}

		return false;
	}

	boolean processAuthenticator(DataStream ds, ContentName receiver_uri,
			Interest interest)
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		PDCReceiver receiver;
		Authenticator auth;
		byte[] encrypted;

		receiver = ds.name2receiver(receiver_uri);
		if (receiver == null) {
			Log.info("Interest for authenticator for unknown " + receiver_uri.
					toURIString());
			return false;
		}

		Log.info("### RESPONDING TO AUTHENTICATOR REQUEST ### (Step 6)");

		try {
			auth = new Authenticator(ds, receiver);
		}
		catch (PDCException ex) {
			Log.info("No authenticator available: " + ex.getMessage());
			return false;
		}
		try {
			final DataEncryptor encryptor = ds.getTransport().getEncryptor();
			encrypted = encryptor.encryptAsymData(receiver, auth);
		}
		catch (PDCEncryptionException ex) {
			Log.error("Error while encrypting data: " + ex.getMessage());
			return false;
		}

		try {
			return CommunicationHelper.publishEncryptedData(config.getCCNHandle(),
					interest, ds.getTransport().getEncryptor().getStreamKeyDigest(),
					encrypted, 1);
		}
		catch (PDCTransmissionException ex) {
			Log.error("Error while transmitting data: " + ex.getMessage());
			return false;
		}
	}

	boolean processStreamInfo(DataStream ds, ContentName receiver_uri,
			Interest interest)
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		PDCReceiver receiver;
		StreamInfo si;
		byte[] encrypted;

		receiver = ds.name2receiver(receiver_uri);
		if (receiver == null) {
			Log.warning(receiver_uri.toURIString() + " is an unknown URI");
			return false;
		}

		si = new StreamInfo(ds);
		try {
			encrypted = ds.getTransport().getEncryptor().encryptAsymData(receiver, si);
		}
		catch (PDCEncryptionException ex) {
			Log.error("Unable to encrypt stream info: " + ex.getMessage());
			return false;
		}

		Log.info("### SENDING STREAMINFO ### (Step 7)");

		assert encrypted != null;

		try {
			return CommunicationHelper.publishEncryptedData(config.getCCNHandle(),
					interest, ds.getTransport().getEncryptor().getStreamKeyDigest(),
					encrypted, 1);
		}
		catch (PDCTransmissionException ex) {
			Log.error("Unable to transmit data: " + ex.getMessage());
			return false;
		}
	}

	boolean processKey(DataStream ds, ContentName receiver_uri, Interest interest)
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		final PDCKeyManager keymgr = config.getKeyManager();
		final PublisherPublicKeyDigest digest = ds.getTransport().getEncryptor().
				getStreamKeyDigest();
		final PublicKey key = keymgr.getPublicKey(digest);
		final CCNTime version = keymgr.getKeyVersion(digest);

		assert key != null;
		assert version != null;

		Log.info("### GOT KEY REQUEST; SENDING MY KEY ### (" + digest + ")");

		final KeyLocator locator = new KeyLocator(interest.name(), digest);

		try {
			PublicKeyObject pko = new PublicKeyObject(interest.name(), key,
					SaveType.RAW, digest, locator, config.getCCNHandle());
			pko.getFlowControl().disable();
			pko.save(version, interest);
			pko.close();

			return true;
		}
		catch (IOException ex) {
			Log.error("Unable to send the key: " + ex.getLocalizedMessage());
		}

		return false;
	}

	boolean processLast(DataStream ds, Interest interest)
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		final PublisherPublicKeyDigest publisher = ds.getTransport().getEncryptor().
				getStreamKeyDigest();
		String last;

		try {

			last = ds.getStorage().getLastEntry();

			//in case of no data, send empty response
			if (last == null)
				last = "";

			return CommunicationHelper.publishUnencryptedData(config.getCCNHandle(),
					interest, publisher, last.getBytes(), 1);
		}
		catch (PDCTransmissionException ex) {
			Log.error("Unable to send data: " + ex.getLocalizedMessage());
		}
		catch (PDCDatabaseException ex) {
			Log.error("Unable to access the data: " + ex.getLocalizedMessage());
		}
		return false;
	}

	@Override
	boolean processCommand(DataStream ds, ContentName postfix, Interest interest)
	{
		Log.debug("Got stream interest for: " + postfix.toString());

		if (postfix.count() == 0) {
			Log.error("No subcommand!");

			return false;
		}

		String command = postfix.stringComponent(0);

		try {
			ContentName remainder = postfix.subname(1, postfix.count() - 1);

			if (command.equals("setup"))
				return processSetup(ds, remainder, interest);
			else if (command.equals("authenticator"))
				return processAuthenticator(ds, remainder, interest);
			else if (command.equals("stream_info"))
				return processStreamInfo(ds, remainder, interest);
			else if (command.equals("key"))
				return processKey(ds, remainder, interest);
			else if (command.equals("pull"))
				return ds.getTransport().pullInterestHandler(remainder, interest);
			else if (command.equals("list"))
				return ds.getTransport().publishRecordList(interest, remainder);
			else if (command.equals("last"))
				return processLast(ds, interest);
			else {
				Log.info("Invalid request: " + interest.name().toURIString());
				
				return false;
			}
		}
		catch (Exception ex) {
			throw new Error("Got problem", ex);
		}
	}
}
