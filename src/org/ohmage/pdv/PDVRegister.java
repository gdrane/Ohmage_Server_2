package org.ohmage.pdv;

import java.io.IOException;

import org.ccnx.ccn.CCNFilterListener;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.core.PDVInstance;
import edu.ucla.cens.pdc.libpdc.util.Log;

public class PDVRegister implements CCNFilterListener {
	
	private PDVRegister() {
	}
	
	public void registerPDV(String username) {
		if (_pdv_register == null)
			_pdv_register = new PDVRegister();
		OhmageApplication app = 
				(OhmageApplication) GlobalConfig.getInstance().
				getApplication("ohmage");
		String newpdvnamespace = "ccnx:/ucla.edu/ohmage/server/" + username;
		app.addPdvInstance(username, new PDVInstance());
		try {
			GlobalConfig.getInstance().getCCNHandle().registerFilter(
					ContentName.fromNative(newpdvnamespace),
					app.getPdvInstance(username));
		} catch (MalformedContentNameStringException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void startListening(String username) {
		
	}

	@Override
	public boolean handleInterest(Interest interest) {
		/*
		 * Interest Format:
		 * 	<root>/<app_name>/register/username/hashed_password
		 * 
		 * 	<root> = ccnx:/ucla.edu/server
		 * 	<app_name> = ohmage
		 * 	
		 */
		ContentName name = interest.name();
		ContentName postfix = name.postfix(_namespace);
		
		Log.info("Got interest: " + name + ", postfix: " + postfix);
		
		if (postfix == null || postfix.count() < 4)
			return false;
		if(postfix.stringComponent(1) != "register"){
			return false;
		}
		String app_name = postfix.stringComponent(0);
		OhmageApplication app = 
				(OhmageApplication) GlobalConfig.getInstance().
				getApplication(app_name);
		String username = postfix.stringComponent(2);
		String hashedPassword = postfix.stringComponent(3);
		if(OhmageAuthentication.getInstance().authenticate(username, hashedPassword)) {
			String newpdvnamespace = "ccnx:/ucla.edu/ohmage/server/" + username;
			app.addPdvInstance(username, new PDVInstance());
			try {
				GlobalConfig.getInstance().getCCNHandle().registerFilter(
						ContentName.fromNative(newpdvnamespace),
						app.getPdvInstance(username));
			} catch (MalformedContentNameStringException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}
	
	private static PDVRegister _pdv_register = null;
	private ContentName _namespace;
}
