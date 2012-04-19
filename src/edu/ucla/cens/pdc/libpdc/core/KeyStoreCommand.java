package edu.ucla.cens.pdc.libpdc.core;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;
import org.bson.BSONDecoder;
import org.bson.BSONObject;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.impl.support.DataUtils;
import org.ccnx.ccn.profiles.ccnd.CCNDCacheManager;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.ContentName.DotDotComponent;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.dao.AuthenticationDao;
import org.ohmage.pdv.KeyValueStore;
import org.ohmage.util.NDNUtils;

import com.mongodb.BasicDBObject;

import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import edu.ucla.cens.pdc.libpdc.transport.CommunicationHelper;

public class KeyStoreCommand extends GenericCommand {

	public KeyStoreCommand() {
		super(KEY_STORE_CMD);
	}
	
	@Override
	boolean processCommand(ContentName postfix, Interest interest) {
		if(postfix.stringComponent(1).equals(GET))
			return handleGetCmd(postfix, interest);
		if(postfix.stringComponent(1).equals(PUT))
			return handlePutCmd(postfix, interest);
		return false;
	}
	
	private boolean handleGetCmd(ContentName postfix, Interest interest) {
		final GlobalConfig config = GlobalConfig.getInstance();
		String store_key = postfix.stringComponent(2);
		final String hashedIMEI = new String(postfix.lastComponent());
		String encryptedInfo = null;
		String responseText;
		JSONObject jsonObject = new JSONObject();
		ContentObject co = null;
		try {
			encryptedInfo = new String(ContentName.
					componentParseURI(postfix.stringComponent(3)));
			final int lastIndex = postfix.containsWhere(hashedIMEI);
			LOGGER.info("HASHED IMEI index was : " + lastIndex);
			for(int i = 4 ; i < lastIndex; ++i) {
				encryptedInfo += "/" + new String(ContentName.
						componentParseURI(postfix.stringComponent(i)));
			}
			LOGGER.info("Encrypted Info Sent : " + encryptedInfo);
			String unEncryptedInfo = new String(NDNUtils.decryptConfigData(
					DataUtils.base64Decode(encryptedInfo.getBytes())));
			BSONDecoder decoder = new BSONDecoder();
			BSONObject obj = decoder.readObject(unEncryptedInfo.getBytes());
			BasicDBObject received_data = new BasicDBObject(obj.toMap());
			LOGGER.info("USERNAME : " + received_data.getString("username"));
			LOGGER.info("PASSWORD : " + received_data.getString("password"));
			String username = received_data.getString("username");
	    	String hashedPassword = received_data.getString("password");
	    	if(username != null && hashedPassword != null &&
	    			AuthenticationDao.authenticate(username, hashedPassword)) {
	    		KeyValueStore storeInstance = KeyValueStore.getInstance();
	    		if(storeInstance.containsKey(store_key)) {
	    			String output = storeInstance.getValue(store_key);
	    			LOGGER.info("Output JSON " + output);
	    			jsonObject = new JSONObject(output);
	    			if(jsonObject.getString("result").equals("processing_request"))
	    				return false;
	    			jsonObject.remove("result");
	    			jsonObject.put("result", "success");
	    			PublisherPublicKeyDigest digest = 
	    					new PublisherPublicKeyDigest(
	    							jsonObject.getString("digest"));
	    			responseText = jsonObject.toString();
	    			LOGGER.info("ResponseText : " + responseText);
	    			CommunicationHelper.publishUnencryptedData(
							config.getCCNHandle(), 
							interest, digest, 
							responseText.getBytes(), 1);
	    			new CCNDCacheManager().clearCache(interest.name(), 
	    					config.getCCNHandle(), 
	    					SystemConfiguration.NO_TIMEOUT);
	    			storeInstance.removeValue(store_key);
	    			return true;
	    		} else {
	    			jsonObject.put("result", "failure");
	    			jsonObject.put("error_message", "Key does not exist in the key store");
	    			responseText = jsonObject.toString();
	    			CommunicationHelper.publishUnencryptedData(
							config.getCCNHandle(), 
							interest, config.getKeyManager().getDefaultKeyID(), 
							responseText.getBytes(), 1);
	    			new CCNDCacheManager().clearCache(interest.name(),
	    					config.getCCNHandle(),
	    					SystemConfiguration.NO_TIMEOUT);
	    			return true;
	    		}
	    	} else {
	    		jsonObject.put("result", "failure");
	    		jsonObject.put("error_message", "Problem with username or password");
	    		responseText = jsonObject.toString();
	    		co = ContentObject.buildContentObject(interest.name(), 
	    				responseText.getBytes());
	    		config.getCCNHandle().put(co);
	    		return true;
	    	}
		} catch (URISyntaxException e2) {
			e2.printStackTrace();
		} catch (DotDotComponent e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			LOGGER.info(jsonObject);
			e.printStackTrace();
		} catch (PDCTransmissionException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean handlePutCmd(ContentName postfix, Interest interest) {
		return false;
	}
	
	private static final Logger LOGGER = Logger.getLogger(KeyStoreCommand.class);
	
	private static final String GET = "get";
	private static final String PUT = "put";
	private static final String KEY_STORE_CMD = "key_store";
	
}
