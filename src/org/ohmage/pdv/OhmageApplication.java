package org.ohmage.pdv;

import java.io.IOException;
import java.util.HashMap;

import edu.ucla.cens.pdc.libpdc.Application;
import edu.ucla.cens.pdc.libpdc.core.PDVInstance;

public class OhmageApplication extends Application {
	
	public OhmageApplication(String name) throws IOException {
		super(name);
	}

	public OhmageApplication(String name, boolean restore) throws IOException {
		super(name, restore);
	}
	
	public void addPdvInstance(String username, PDVInstance pdvInstance) {
		registeredPdvs.put(username, pdvInstance);
	}
	
	public PDVInstance getPdvInstance(String username) {
		return registeredPdvs.get(username);
	}
	
	private HashMap<String, PDVInstance> registeredPdvs = 
			new HashMap<String, PDVInstance>();

}
