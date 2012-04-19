package org.ohmage.pdv;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

import org.apache.log4j.Logger;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ohmage.pdv.storage.MYSQLConfigStorage;
import org.ohmage.pdv.storage.MYSQLDataStorage;
import org.ohmage.request.auth.AuthTokenRequest;

import edu.ucla.cens.pdc.libpdc.Application;
import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.core.PDCKeyManager;
import edu.ucla.cens.pdc.libpdc.core.PDVInstance;
import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import edu.ucla.cens.pdc.libpdc.transport.PDCPublisher;
import edu.ucla.cens.pdc.libpdc.transport.PDCReceiver;

public class OhmagePDVGlobals {
	
	public static OhmagePDVGlobals getInstance() {
		if(_pdv_globals == null)
			_pdv_globals = new OhmagePDVGlobals();
		return _pdv_globals;
	}
	
	
	
	private OhmagePDVGlobals() {		
	}
	
	private static void setupFirstRun() {
		LOGGER.info("Setting up for the first run, value of first run" + firstrun);
		if(firstrun) {
			firstrun = false;
			GlobalConfig.setFeatures(GlobalConfig.FEAT_SHARING
					| GlobalConfig.FEAT_MANAGE | GlobalConfig.FEAT_KEYSTORE);
			GlobalConfig.setConfigStorage(MYSQLConfigStorage.class);
			GlobalConfig.setDataStorage(MYSQLDataStorage.class);
			//Disable annoying ccnx debug messages
			//org.ccnx.ccn.impl.support.Log.setDefaultLevel(
			//		org.ccnx.ccn.impl.support.Log.FAC_ALL, Level.CONFIG);
			GlobalConfig config = GlobalConfig.getInstance();
			try {
				config.setRoot(ContentName.fromURI(NAMESPACE));
			}catch (MalformedContentNameStringException e){
				LOGGER.error("Malformed string root namespace" + NAMESPACE);
			}
			try{
				_pdc_receiver = new PDCReceiver(NAMESPACE + "/" + _app_instance);
				LOGGER.info("PDC Receiver namespace :" + NAMESPACE + "/" 
				+ _app_instance);
				// Set Authenticator
				_pdc_receiver.setAuthenticator(AUTHENTICATOR);
			} catch (MalformedContentNameStringException e) {
				LOGGER.error("Malformed string receiver" + NAMESPACE + "/" + _app_instance);
				LOGGER.info(e.toString());
			}
			_pdv_instance = new PDVInstance();
			try {
				_pdv_instance.startListening();
			} catch (MalformedContentNameStringException e) {
				LOGGER.error("Malformed String when listening");
				LOGGER.error(e.toString());
			} catch (IOException e) {
				LOGGER.error("IOException when starting to listen");
				LOGGER.error(e.toString());
			}
			createConfigurationDigest();
		}
	}
	
	private static void createConfigurationDigest() {
		GlobalConfig config = GlobalConfig.getInstance();
		PDCKeyManager keymgr = config.getKeyManager();
		_configuration_digest = keymgr.generatePublisherKeys();
		ContentName config_keyname = null;
		try {
			config_keyname = ContentName.fromURI("ccnx:/ndn/ucla.edu/apps/borges/ohmagepdv").
					append("manage").
					append("configuration_key");
		} catch (MalformedContentNameStringException e) {
			LOGGER.info("Configuration keylocator name malformed");
			e.printStackTrace();
		}
		KeyLocator locator = new KeyLocator(config_keyname);
		keymgr.setKeyLocator(_configuration_digest, locator);
		LOGGER.info("Configuration Digest: " + _configuration_digest);
	}
	
	public static PublisherPublicKeyDigest getConfigurationDigest() {
		if(_configuration_digest != null)
			return _configuration_digest;
		return null;
	}
	
	public static void createStreamsForUser(String app_name, String username) {
		setupFirstRun();
		PublisherPublicKeyDigest digest = null;
		GlobalConfig config = GlobalConfig.getInstance();
		Application app = config.getApplication(app_name);
		CCNHandle ccn_handle = config.getCCNHandle();
		DataStream ds = null;
		String usernamehash = hashingFunction(username);
		String user_namespace = "ccnx:/ndn/ucla.edu/apps/" + username +
				"/androidclient";
		LOGGER.info("user_namespace is" + user_namespace);
		PDCPublisher pdc_publisher;

		if(app == null) {
			try {
				app = new Application(app_name);
			} catch (IOException e) {
				LOGGER.error(e);
			}
			config.addApplication(app);
		}
		
		try {
			// LOGGER.info("Reached here1");
			pdc_publisher = new PDCPublisher(user_namespace);
			pdc_publisher.setAuthenticator(AUTHENTICATOR);
			// LOGGER.info("Reached 2");
			ds = null;
			ds = new DataStream(ccn_handle, app, 
					_mobility_data_stream + usernamehash, false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_mobility_data_stream);
			LOGGER.info("Created mobility stream");
			LOGGER.info("DS Key for mobility stream " + 
			ds.getTransport().getEncryptor().getStreamKeyDigest());
			/*	
			ds = null;
			ds = new DataStream(ccn_handle, app, 
					_survey_response_stream + usernamehash, false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_survey_response_stream);
			LOGGER.info("Created survey response stream: " +
			ds.getTransport().getEncryptor().getStreamKeyDigest());
			*/
					
		} catch (MalformedContentNameStringException e1) {
			LOGGER.error(e1);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOGGER.error(e);
		}
	}
	
	public static String getAppInstance() {
		return _app_instance;
	}
	
	public static String getAppName() {
		return _app_name;
	}
	
	public static String hashingFunction(String username)
	{
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assert md != null;
		md.update(username.getBytes());
		byte hashData[] = md.digest();
		StringBuffer hexString = new StringBuffer();
		for(byte single: hashData) {
			String hex = Integer.toString(0xff & single);
			if(hex.length() == 1)
				hexString.append('0');
			hexString.append(hex);
		}
		return hexString.toString();
	}
	
	private static OhmagePDVGlobals _pdv_globals = null;
	
	private static boolean firstrun = true;
	
	private final static String NAMESPACE = "ccnx:/ndn/ucla.edu/apps/borges";
	
	private static PDCReceiver _pdc_receiver = null;
	
	private final static String _mobility_data_stream = 
			"MOBILITY_DATA_STREAM";
	
	private final static String _survey_response_stream = 
			"SURVEY_RESPONSE_STREAM";
	
	private final static String AUTHENTICATOR = "test_authenticator";
	
	private final static String _app_instance = "ohmagepdv";
	
	private final static String _app_name = "ohmage";
	
	private static PDVInstance _pdv_instance = null;
	
	private static final Logger LOGGER = 
			Logger.getLogger(OhmagePDVGlobals.class);
	
	private static PublisherPublicKeyDigest _configuration_digest = null;

}
