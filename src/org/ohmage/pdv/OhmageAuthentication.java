package org.ohmage.pdv;

public class OhmageAuthentication {
	
	public static OhmageAuthentication getInstance() {
		if(_ohmage_authentication == null)
			_ohmage_authentication = new OhmageAuthentication();
		return _ohmage_authentication;
	}
	
	public boolean authenticate(String username, String hashedPassword) {
		return false;
	}
	
	private OhmageAuthentication() {
		
	}
	private static OhmageAuthentication _ohmage_authentication = null;
}
