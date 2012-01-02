/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.transport;

import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import java.security.PrivateKey;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ccnx.ccn.protocol.SignedInfo;

/**
 *
 * @author Derek Kulinski
 */
public class CommunicationHelper {
	public static boolean publishData(CCNHandle handle,
			Interest interest, PublisherPublicKeyDigest sender_identity, byte[] data,
			int freshness, SignedInfo.ContentType type)
			throws PDCTransmissionException
	{
		GlobalConfig config;
		SignedInfo si;
		ContentObject co;
		KeyManager keymgr;
		KeyLocator key_locator;
		PrivateKey signing_key;

		assert interest != null;
		assert sender_identity != null;
		assert data != null;

		config = GlobalConfig.getInstance();
		keymgr = config.getKeyManager();
		assert keymgr != null;

		signing_key = keymgr.getSigningKey(sender_identity);
		assert signing_key != null : "no signing key for " + sender_identity;

		key_locator = keymgr.getKeyLocator(sender_identity);
		assert key_locator != null;

		try {
			si = new SignedInfo(sender_identity, type, key_locator, freshness, null);
			co = new ContentObject(interest.name(), si, data, signing_key);

			handle.put(co);

			return true;
		}
		catch (Exception ex) {
			throw new PDCTransmissionException("Error while publishing data", ex);
		}

	}

	public static boolean publishUnencryptedData(CCNHandle handle,
			Interest interest, PublisherPublicKeyDigest sender_identity, byte[] data,
			int freshness)
			throws PDCTransmissionException
	{
		return publishData(handle, interest, sender_identity, data, freshness,
				SignedInfo.ContentType.DATA);
	}

	public static boolean publishEncryptedData(CCNHandle handle, Interest interest,
			PublisherPublicKeyDigest sender_identity, byte[] data, int freshness)
			throws PDCTransmissionException
	{
		return publishData(handle, interest, sender_identity, data, freshness,
				SignedInfo.ContentType.ENCR);
	}
}
