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
import java.net.URISyntaxException;

import org.apache.log4j.Logger;
import org.bson.BSONDecoder;
import org.bson.BSONObject;
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
import org.ccnx.ccn.protocol.ContentName.DotDotComponent;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.PublisherID;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ohmage.dao.AuthenticationDao;
import org.ohmage.pdv.OhmagePDVGlobals;
import org.ohmage.util.NDNUtils;

import com.mongodb.BasicDBObject;

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
		// String publisherInformation = remainder.stringComponent(0);
		/*try {
			publisherInformation = new String(ds.getTransport().getEncryptor().
					decryptAsymData(remainder.stringComponent(0).getBytes()));
		} catch (PDCEncryptionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		// assert publisherInformation != null;
		String hashedIMEI = new String(remainder.lastComponent());
		// TODO(gdrane): USe the state information to stop replay attacks
		//String count = publisherInformation.split("/")[1];
		if (publisher == null) {
			LOGGER.warn("Got setup request, while a publisher wasn't set up");
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
		
		LOGGER.info("### REQUESTING AUTHENTICATOR ### (Step 6)");

		try {
			name = publisher.uri.append(hashedIMEI).
					append(ds.data_stream_id).append(Constants.STR_CONTROL).
					append("authenticator").append(config.getRoot()).
					append(OhmagePDVGlobals.getAppInstance());
			LOGGER.info("Authenticator interest: " + name);

			co = config.getCCNHandle().get(name,
					SystemConfiguration.LONG_TIMEOUT);
			if (co == null) {
				LOGGER.error("Got no response when requesting authenticator");
				return false;
			}
			LOGGER.info("Received Content Object is : " + co);
			//Verify if data is in check
			if (!co.verify(config.getKeyManager())) {
				LOGGER.error("Authenticator content fails data verification");
				return false;
			}

			final byte[] data = ds.getTransport().getEncryptor().
					decryptAsymData(co.content());
			final Authenticator auth = new Authenticator().fromBSON(data);

			if (!Arrays.equals(auth.getKeyDigest(), co.signedInfo().
					getPublisher())) {
				LOGGER.error(
						"Tempering detected! Data is signed with different" +
						" key than one stored in authenticator");
				return false;
			}

			if (!auth.getAuthenticator().equals(publisher.getAuthenticator())) {
				LOGGER.error("Authenticator value doesn't match");
				return false;
			}

			LOGGER.info("### AUTHENTICATOR RECEIVED IS CORRECT ### (Step 6)");

			LOGGER.info("Adding key " + DataUtils.base64Encode(auth.getKeyDigest(),
					PublisherID.PUBLISHER_ID_LEN * 2) + " to trusted keys.");
			config.getKeyManager().authenticateKey(auth.getKeyDigest());
			
			LOGGER.info("### ASKING FOR STREAM INFORMATION ### (Step 7)");
			
			name = publisher.uri.append(hashedIMEI).
					append(ds.data_stream_id).append(Constants.STR_CONTROL).
					append("stream_info").append(config.getRoot()).
					append(OhmagePDVGlobals.getAppInstance());

			co = config.getCCNHandle().get(name, 
					SystemConfiguration.LONG_TIMEOUT);
			if (co == null) {
				LOGGER.error("Got no response when requesting authenticator");
				return false;
			}
			LOGGER.info("Received Content Object is : " + co);
			//Verify if data is in check
			if (!co.verify(config.getKeyManager())) {
				LOGGER.error("Authenticator content fails data verification");
				return false;
			}

			final byte[] stream_data = ds.getTransport().getEncryptor().
					decryptAsymData(co.content());
			
			ds.getTransport().getEncryptor().setDecryptKey(stream_data);
			ds.getTransport().getEncryptor().setEncryptKey(stream_data);
			
			StreamInfo si = new StreamInfo(ds);
			ds.updateStreamSetupStatus(si);			
			
			return true;
		}
		catch (GeneralSecurityException ex) {
			LOGGER.error("Problem while trying to verify data signature: " + ex.
					getLocalizedMessage());
		}
		catch (IOException ex) {
			LOGGER.error("Communication problem: " + ex.getLocalizedMessage());
		}
		catch (PDCParseException ex) {
			LOGGER.error("Unable to parse data (malformaed string?): " + ex.
					getLocalizedMessage());
		}
		catch (PDCEncryptionException ex) {
			LOGGER.error("Unable to decrypt message: " + ex.getLocalizedMessage());
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
			LOGGER.info("Interest for authenticator for unknown " + receiver_uri.
					toURIString());
			return false;
		}

		LOGGER.info("### RESPONDING TO AUTHENTICATOR REQUEST ### (Step 6)");

		try {
			auth = new Authenticator(ds, receiver);
		}
		catch (PDCException ex) {
			LOGGER.info("No authenticator available: " + ex.getMessage());
			return false;
		}
		try {
			final DataEncryptor encryptor = ds.getTransport().getEncryptor();
			encrypted = encryptor.encryptAsymData(receiver, auth);
		}
		catch (PDCEncryptionException ex) {
			LOGGER.error("Error while encrypting data: " + ex.getMessage());
			return false;
		}

		try {
			return CommunicationHelper.publishEncryptedData(config.getCCNHandle(),
					interest, ds.getTransport().getEncryptor().getStreamKeyDigest(),
					encrypted, 1);
		}
		catch (PDCTransmissionException ex) {
			LOGGER.error("Error while transmitting data: " + ex.getMessage());
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
			LOGGER.warn(receiver_uri.toURIString() + " is an unknown URI");
			return false;
		}

		si = new StreamInfo(ds);
		ds.updateStreamSetupStatus(si);
		try {
			encrypted = ds.getTransport().getEncryptor().encryptAsymData(receiver, si);
		}
		catch (PDCEncryptionException ex) {
			LOGGER.error("Unable to encrypt stream info: " + ex.getMessage());
			return false;
		}

		LOGGER.info("### SENDING STREAMINFO ### (Step 7)");

		assert encrypted != null;

		try {
			return CommunicationHelper.publishEncryptedData(config.getCCNHandle(),
					interest, ds.getTransport().getEncryptor().getStreamKeyDigest(),
					encrypted, 1);
		}
		catch (PDCTransmissionException ex) {
			LOGGER.error("Unable to transmit data: " + ex.getMessage());
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

		LOGGER.info("### GOT KEY REQUEST; SENDING MY KEY ### (" + digest + ")" 
		+ "Key :" + key.toString() + "version:" + version.toString());

		final KeyLocator locator = new KeyLocator(interest.name(), digest);
		
		LOGGER.info("Key Interest to be sent : " + interest.name());

		try {
			PublicKeyObject pko = new PublicKeyObject(interest.name(), key,
					SaveType.RAW, digest, locator, config.getCCNHandle());
			LOGGER.debug("Could create the PKO object");
			pko.getFlowControl().disable();
			/*if(pko.save(version, interest))
				LOGGER.info("Could save the key");
			else
				LOGGER.info("Could not save the key");
			pko.close();
			*/
			pko.save();
			pko.close();
			return true;
		}
		catch (IOException ex) {
			LOGGER.error("Unable to send the key: " + ex.getLocalizedMessage());
		}

		return false;
	}

	boolean processLast(DataStream ds, Interest interest)
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		final PublisherPublicKeyDigest publisher = ds.getTransport().
				getEncryptor().getStreamKeyDigest();
		String last;

		try {

			last = ds.getStorage().getLastEntry();

			//in case of no data, send empty response
			if (last == null)
				last = "";

			return CommunicationHelper.publishUnencryptedData(
					config.getCCNHandle(),
					interest, publisher, last.getBytes(), 1);
		}
		catch (PDCTransmissionException ex) {
			LOGGER.error("Unable to send data: " + ex.getLocalizedMessage());
		}
		catch (PDCDatabaseException ex) {
			LOGGER.error("Unable to access the data: " + 
		ex.getLocalizedMessage());
		}
		return false;
	}
	
	boolean processRecvDigest(DataStream ds, ContentName postfix, 
			Interest interest) {
		try {
			GlobalConfig config = GlobalConfig.getInstance();
			String hashedIMEI = new String(postfix.lastComponent());
			String encryptedInfo = 
					new String(ContentName.componentParseURI(
							postfix.stringComponent(3)));
			int lastIndex;
			lastIndex = postfix.containsWhere(hashedIMEI);
			for(int i = 4 ; i < lastIndex ; ++i) {
					encryptedInfo += "/" + 
				new String(ContentName.componentParseURI(
						postfix.stringComponent(i)));
			}
			Log.info("Received encrypted Info : " +  encryptedInfo);
			byte[] unEncryptedInfo = NDNUtils.decryptConfigData(
					DataUtils.base64Decode(encryptedInfo.getBytes()));
			BSONDecoder decoder = new BSONDecoder();
			BSONObject obj = decoder.readObject(unEncryptedInfo);
			BasicDBObject map = new BasicDBObject(obj.toMap());
			String username = map.getString("username");
			String hashedPassword = map.getString("password");
			if(username == null || hashedPassword == null ||
					!AuthenticationDao.authenticate(username, hashedPassword)) {
				Log.info("Incorrect authentication");
				return false;
			}
			
			byte[] data = ds.getTransport().getEncryptor().getStreamKeyDigest().
					toString().getBytes();
			
			CommunicationHelper.publishUnencryptedData(config.getCCNHandle(), 
					interest, OhmagePDVGlobals.getConfigurationDigest(), 
					data, 1);
			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (DotDotComponent e) {
			e.printStackTrace();
		} catch (PDCTransmissionException e) {
			e.printStackTrace();
		}
		
		return true;
	}

	@Override
	boolean processCommand(DataStream ds, ContentName postfix, Interest interest)
	{
		LOGGER.debug("Got stream interest for: " + postfix.toString());

		if (postfix.count() == 0) {
			LOGGER.error("No subcommand!");

			return false;
		}

		String command = postfix.stringComponent(2);

		try {
			// ContentName remainder = postfix.subname(1, postfix.count() - 1);

			if (command.equals("setup"))
				return processSetup(ds, postfix, interest);
			else if (command.equals("authenticator"))
				return processAuthenticator(ds, postfix, interest);
			else if (command.equals("stream_info"))
				return processStreamInfo(ds, postfix, interest);
			else if (command.equals("key"))
				return processKey(ds, postfix, interest);
			else if (command.equals("pull"))
				return ds.getTransport().pullInterestHandler(postfix, interest);
			else if (command.equals("list"))
				return ds.getTransport().publishRecordList(interest, postfix);
			else if (command.equals("last"))
				return processLast(ds, interest);
			else if(command.equals("recv_digest"))
				return processRecvDigest(ds, postfix, interest);
			else {
				LOGGER.info("Invalid request: " + interest.name().toURIString());
				
				return false;
			}
		}
		catch (Exception ex) {
			throw new Error("Got problem", ex);
		}
	}
	
	private static final Logger LOGGER = Logger.getLogger(StreamControlCommands.class);
}
