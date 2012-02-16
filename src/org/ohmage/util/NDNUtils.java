package org.ohmage.util;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.impl.security.keys.PublicKeyCache;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ohmage.pdv.OhmagePDVGlobals;

import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.core.PDCKeyManager;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.util.EncryptionHelper;
import edu.ucla.cens.pdc.libpdc.util.Log;

public class NDNUtils {

	public static byte[] encryptConfigData(KeyLocator locator, PublisherPublicKeyDigest digest, String data) {
		PublicKey public_key = null;
		GlobalConfig config = GlobalConfig.getInstance();
		PDCKeyManager keymgr = config.getKeyManager();
		// PublisherPublicKeyDigest digest = OhmagePDVGlobals.getConfigurationDigest();
		try {
			public_key = keymgr.getPublicKey(digest, locator);
			return EncryptionHelper.encryptAsymData(public_key, data.getBytes());
		} catch (IOException e) {
			Log.error("Got IOException inside NDNUtils");
			e.printStackTrace();
		} catch (PDCEncryptionException e) {
			Log.error("Got PDCEncryption Exception inside NDNUtils");
		}
		return null;
	}
	
	public static byte[] decryptConfigData(byte[] data) {
		GlobalConfig config = GlobalConfig.getInstance();
		KeyManager keymgr;
		PrivateKey private_key;
		Log.debug("Decrypting data using my key: ");

		keymgr = config.getKeyManager();
		assert keymgr != null;
		private_key = keymgr.getSigningKey(OhmagePDVGlobals.getConfigurationDigest());
		
		assert private_key != null;

		try {
			return EncryptionHelper.decryptAsymData(private_key, data);
		} catch (PDCEncryptionException e) {
			Log.error("Got PDCEncryption Exception inside NDNUtils");
			e.printStackTrace();
		}
		return null;
	}

}
