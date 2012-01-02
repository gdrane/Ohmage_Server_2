package org.ohmage.pdv;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ohmage.pdv.storage.MYSQLConfigStorage;
import org.ohmage.pdv.storage.MYSQLDataStorage;

import edu.ucla.cens.pdc.libpdc.Application;
import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
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
		if(firstrun) {
			firstrun = false;
			GlobalConfig.setFeatures(GlobalConfig.FEAT_SHARING
					| GlobalConfig.FEAT_MANAGE);
			GlobalConfig.setConfigStorage(MYSQLConfigStorage.class);
			GlobalConfig.setDataStorage(MYSQLDataStorage.class);
			//Disable annoying ccnx debug messages
			org.ccnx.ccn.impl.support.Log.setDefaultLevel(
					org.ccnx.ccn.impl.support.Log.FAC_ALL, Level.WARNING);
			GlobalConfig config = GlobalConfig.getInstance();
			try {
				config.setRoot(ContentName.fromURI(NAMESPACE));
				_pdc_receiver = new PDCReceiver(NAMESPACE);
				// Set Authenticator
				_pdc_receiver.setAuthenticator(AUTHENTICATOR);
			} catch (MalformedContentNameStringException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void createStreamsForUser(String app_name, String username) {
		setupFirstRun();
		GlobalConfig config = GlobalConfig.getInstance();
		Application app = config.getApplication(app_name);
		CCNHandle ccn_handle = config.getCCNHandle();
		DataStream ds = null;
		String usernamehash = hashUsername(username);
		String user_namespace = "/ndn/ucla.edu/apps/" + username +
				"/androidclient";
		PDCPublisher pdc_publisher;
		
		if(app == null) {
			try {
				app = new Application(app_name);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			config.addApplication(app);
		}
		
		try {
			pdc_publisher = new PDCPublisher(user_namespace);
			pdc_publisher.setAuthenticator(AUTHENTICATOR);
			
			/*ds = new DataStream(ccn_handle, app,
					_campaigns_id + usernamehash,
					false, username);
			
			assert ds != null;
			assert _pdc_receiver != null : "PDC Receiver is NULL";
			
			ds.addReceiver(_pdc_receiver);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_campaigns_id);
			app.addDataStream(ds);
			ds = null;
			
			ds = new DataStream(ccn_handle, app, 
					_survey_id + usernamehash,
					false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_survey_id);
			ds = null;
			
			ds = new DataStream(ccn_handle, app, 
					_survey_prompts_id + usernamehash,
					false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_survey_prompts_id);
			ds = null;
			
			ds = new DataStream(ccn_handle, app,
					_responses_id + usernamehash,
					false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_responses_id);
			ds = null;
			
			ds = new DataStream(ccn_handle, app,
					_prompt_responses_id + usernamehash,
					false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds = null;
			
			ds = new DataStream(ccn_handle, app,
					_singlechoice_customchoice_id + usernamehash,
					false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_singlechoice_customchoice_id);
			ds = null;
			
			ds = new DataStream(ccn_handle, app,
					_multichoice_customchoice_id + usernamehash,
					false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);	
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_multichoice_customchoice_id);*/
			
			ds = null;
			ds = new DataStream(ccn_handle, app, 
					_mobility_data_stream + usernamehash, false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_mobility_data_stream);
			
			ds = null;
			ds = new DataStream(ccn_handle, app, 
					_survey_response_stream + usernamehash, false, username);
			assert ds != null;
			ds.addReceiver(_pdc_receiver);
			app.addDataStream(ds);
			ds.setPublisher(pdc_publisher);
			ds.setTablename(_mobility_data_stream);
					
		} catch (MalformedContentNameStringException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static String hashUsername(String username)
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
	
	private final static String NAMESPACE = "/ndn/ucla.edu/apps/ohmagepdv";
	
	private static PDCReceiver _pdc_receiver = null;
	
	private final static String _mobility_data_stream = 
			"MOBILITY_DATA_STREAM";
	
	private final static String _survey_response_stream = 
			"SURVEY_RESPONSE_STREAM";
	
	private final static String AUTHENTICATOR = "test_authenticator";

}
