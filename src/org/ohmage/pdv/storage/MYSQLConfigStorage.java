package org.ohmage.pdv.storage;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.ohmage.dao.PDVConfigDaos;

import edu.ucla.cens.pdc.libpdc.iConfigStorage;
import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;

public class MYSQLConfigStorage implements iConfigStorage {
	
	public MYSQLConfigStorage() 
	{
		LOGGER.info("Config Storage constructor called");
		setupObject();
	}
	
	private void setupObject()
	{
		
	}

	@Override
	public byte[] loadEntry(String group, String name) throws IOException {
		return PDVConfigDaos.loadEntry(group, name);
	}

	@Override
	public void saveEntry(String obj_group, String obj_key, byte[] byte_out)
			throws IOException {
			PDVConfigDaos.saveEntry(obj_group, obj_key, byte_out);
	}

	@Override
	public void removeEntry(String group, String name) throws IOException {
			PDVConfigDaos.removeEntry(group, name);
	}
	
	private static final Logger LOGGER = 
			Logger.getLogger(MYSQLConfigStorage.class);

}
