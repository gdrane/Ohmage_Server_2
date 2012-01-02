/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

import com.google.protobuf.ByteString;
import edu.ucla.cens.pdc.libpdc.SystemState;
import edu.ucla.cens.pdc.libpdc.iState;
import edu.ucla.cens.pdc.libpdc.util.Log;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.ContentVerifier;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.config.UserConfiguration;
import org.ccnx.ccn.impl.security.crypto.util.MinimalCertificateGenerator;
import org.ccnx.ccn.impl.security.keys.BasicKeyManager;
import org.ccnx.ccn.impl.security.keys.KeyStoreInfo;
import org.ccnx.ccn.impl.security.keys.PublicKeyCache;
import org.ccnx.ccn.impl.security.keys.SecureKeyCache;
import org.ccnx.ccn.protocol.CCNTime;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;

/**
 *
 * @author Derek Kulinski
 */
public class PDCKeyManager extends BasicKeyManager implements iState, ContentVerifier {
	public PDCKeyManager()
			throws ConfigurationException, IOException
	{
		this(true);
	}

	public PDCKeyManager(boolean restore)
			throws ConfigurationException, IOException
	{
		super();

		_restore = restore;

		if (!restore)
			return;

		final GlobalConfig config = GlobalConfig.getInstance();
		SystemState.KeyManager state;
		byte[] byte_in;

		byte_in = config.loadConfig(getStateGroupName(), getStateKey());
		if (byte_in == null) {
			Log.warning("No state for KeyManager stored");
			return;
		}

		state = SystemState.KeyManager.parseFrom(byte_in);

		_defaultKeyID = new PublisherPublicKeyDigest(state.getDefaultKeyId().
				toByteArray());

		_authenticated_public_key_digests = Collections.synchronizedSet(new HashSet<PublisherPublicKeyDigest>(state.
				getPublicKeyDigestsCount() * 2));
		_private_key_digests = new HashSet<byte[]>(state.getPrivateKeyDigestsCount()
				* 2);

		for (final ByteString key_digest : state.getPublicKeyDigestsList())
			_authenticated_public_key_digests.add(new PublisherPublicKeyDigest(key_digest.
					toByteArray()));
		for (final ByteString key_digest : state.getPrivateKeyDigestsList())
			_private_key_digests.add(key_digest.toByteArray());
	}

	@Override
	public synchronized void initialize()
			throws ConfigurationException, IOException
	{
		if (_initialized)
			return;

		_handle = CCNHandle.open(this);
		_publicKeyCache = new PublicKeyCache();
		_privateKeyCache = new SecureKeyCache();

		if (_defaultKeyID != null)
			loadKeys();
		else {
			Log.info("Generating my default keys");
			_defaultKeyID = generatePublisherKeys();
			if (_restore)
				storeStateRecursive();
		}

		_initialized = true;

		//		_keyStoreInfo = loadKeyStore();// uses _keyRepository and _privateKeyCache
//		if (!loadValuesFromKeystore(_keyStoreInfo))
//			Log.warning("Cannot process keystore!");
//
//		// This also loads our cached keys.
//		if (!loadValuesFromConfiguration(_keyStoreInfo))
//			Log.warning("Cannot process configuration data!");
//
//
//		// If we haven't been called off, initialize the key server
//		if (UserConfiguration.publishKeys())
//			initializeKeyServer(_handle);
	}

	@Override
	public ContentVerifier getDefaultVerifier()
	{
		return this;
	}

	public boolean verify(ContentObject content)
	{
		final PublisherPublicKeyDigest co_digest = content.signedInfo().
				getPublisherKeyID();

		if (!_authenticated_public_key_digests.contains(co_digest))
			return false;

		try {
			return content.verify(this);
		}
		catch (Exception ex) {
			Log.warning("Error while verifying content: " + ex.getLocalizedMessage());
			return false;
		}
	}

	public void authenticateKey(byte[] digest)
	{
		PublisherPublicKeyDigest pdigest = new PublisherPublicKeyDigest(digest);

		Log.info("### MARKING " + pdigest + " AS TRUSTED ### (Step 6)");

		_authenticated_public_key_digests.add(pdigest);
	}

	public static KeyPair generateKeys()
	{
		KeyPairGenerator kpg;
		KeyPair key_pair;

		try {
			kpg = KeyPairGenerator.getInstance(UserConfiguration.defaultKeyAlgorithm());
			key_pair = kpg.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new Error("Unable to obtain a key generator", ex);
		}

		return key_pair;
	}

	public static X509Certificate generateSSCert(KeyPair key_pair,
			ContentName name)
	{
		try {
			return MinimalCertificateGenerator.GenerateUserCertificate(key_pair,
					"CN=" + name.toString(),
					MinimalCertificateGenerator.MSEC_IN_YEAR);
		}
		catch (Exception ex) {
			throw new Error("Unable to generate certificate", ex);
		}
	}

	/**
	 * generates a new asymmetric keys
	 * @return digest of public key or null if problem saving the key
	 */
	public PublisherPublicKeyDigest generatePublisherKeys()
	{
		final CCNTime version = CCNTime.now();
		final KeyPair key_pair = generateKeys();
		PublisherPublicKeyDigest digest;

		digest = new PublisherPublicKeyDigest(key_pair.getPublic());

		_privateKeyCache.addMyPrivateKey(digest.digest(), key_pair.getPrivate());
		_publicKeyCache.remember(key_pair.getPublic(), version);
		_private_key_digests.add(digest.digest());

		return digest;
	}

	public SecretKey getWCSymmKey() //TODO will want to add userID parameter
	{
		return this._WCSymmKey;
	}

	public void addWCSymmKey(SecretKey key) //TODO will want to add userID parameter
	{
		this._WCSymmKey = key;
	}

	public KeyPair getKeysForWC()
	{
		return this._keysForWC;
	}

//<editor-fold defaultstate="collapsed" desc="serialization code">
	final public String getStateGroupName()
	{
		return "main";
	}

	final public String getStateKey()
	{
		return "keymanager";
	}

	public synchronized void storeState()
			throws IOException
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		final SystemState.AsyncKey.Builder ak_builder = SystemState.AsyncKey.
				newBuilder();
		CCNTime version;
		PublicKey pub_key;
		PrivateKey priv_key;

		Log.debug("Storing public keys");
		for (final PublisherPublicKeyDigest digest : _authenticated_public_key_digests) {
			Log.debug("Saving " + digest.toString());
			ak_builder.clear();

			version = _publicKeyCache.getPublicKeyVersionFromCache(digest);
			assert version != null;

			pub_key = _publicKeyCache.getPublicKeyFromCache(digest);
			assert pub_key != null;

			ak_builder.setPublicKey(ByteString.copyFrom(pub_key.getEncoded()));
			ak_builder.setAlgorithm(SystemState.AsyncKey.KeyAlg.valueOf(pub_key.
					getAlgorithm()));
			ak_builder.setTimestamp(version.getTime());

			config.saveConfig("keys", digest.toString(), ak_builder.build().
					toByteArray());
		}

		Log.debug("Storing private keys");
		for (final byte[] digest : _private_key_digests) {
			final PublisherPublicKeyDigest digest_obj = new PublisherPublicKeyDigest(
					digest);
			Log.debug("Saving " + digest_obj.toString());
			ak_builder.clear();

			version = _publicKeyCache.getPublicKeyVersionFromCache(digest_obj);
			assert version != null;

			pub_key = _publicKeyCache.getPublicKeyFromCache(digest_obj);
			assert pub_key != null;

			priv_key = _privateKeyCache.getPrivateKey(digest);
			assert priv_key != null;

			ak_builder.setPublicKey(ByteString.copyFrom(pub_key.getEncoded()));
			ak_builder.setPrivateKey(ByteString.copyFrom(priv_key.getEncoded()));
			ak_builder.setAlgorithm(SystemState.AsyncKey.KeyAlg.valueOf(pub_key.
					getAlgorithm()));
			ak_builder.setTimestamp(version.getTime());

			config.saveConfig("keys", digest_obj.toString(), ak_builder.build().
					toByteArray());
		}

		Log.debug("Storing keymanager state");
		config.saveConfig(this);
	}

	public void storeStateRecursive()
			throws IOException
	{
		storeState();
	}

	SystemState.KeyManager getObjectState()
	{
		SystemState.KeyManager.Builder builder = SystemState.KeyManager.newBuilder();

		assert _defaultKeyID != null;
		builder.setDefaultKeyId(ByteString.copyFrom(_defaultKeyID.digest()));

		for (final PublisherPublicKeyDigest key_digest : _authenticated_public_key_digests)
			builder.addPublicKeyDigests(ByteString.copyFrom(key_digest.digest()));

		for (byte[] key_digest : _private_key_digests)
			builder.addPrivateKeyDigests(ByteString.copyFrom(key_digest));

		return builder.build();
	}

	public byte[] stateToByteArray()
	{
		return getObjectState().toByteArray();
	}

	private static SystemState.AsyncKey restoreKey(PublisherPublicKeyDigest digest)
			throws IOException, ConfigurationException
	{
		final GlobalConfig config = GlobalConfig.getInstance();
		byte[] data_in;
		SystemState.AsyncKey ak_state;

		data_in = config.loadConfig("keys", digest.toString());
		if (data_in == null)
			throw new ConfigurationException("No public key stored with hash: "
					+ digest.toString());

		ak_state = SystemState.AsyncKey.parseFrom(data_in);

		if (!ak_state.hasTimestamp())
			throw new ConfigurationException("No timestamp available for key: "
					+ digest.toString());

		return ak_state;
	}

	private static PublicKey producePublicKey(SystemState.AsyncKey ak_state)
			throws ConfigurationException
	{
		try {
			final KeyFactory key_factory = KeyFactory.getInstance(ak_state.
					getAlgorithm().name());
			final X509EncodedKeySpec key_spec = new X509EncodedKeySpec(ak_state.
					getPublicKey().toByteArray());

			return key_factory.generatePublic(key_spec);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new ConfigurationException("Unsupported algorithm for public key",
					ex);
		}
		catch (InvalidKeySpecException ex) {
			throw new ConfigurationException("Unable to restore the public key", ex);
		}
	}

	private static PrivateKey producePrivateKey(SystemState.AsyncKey ak_state)
			throws ConfigurationException
	{
		try {
			final KeyFactory key_factory = KeyFactory.getInstance(ak_state.
					getAlgorithm().name());
			final PKCS8EncodedKeySpec key_spec = new PKCS8EncodedKeySpec(ak_state.
					getPrivateKey().toByteArray());
//			final X509EncodedKeySpec key_spec = new X509EncodedKeySpec(ak_state.
//							getPrivateKey().toByteArray());

			return key_factory.generatePrivate(key_spec);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new ConfigurationException("Unsupported algorithm for private key",
					ex);
		}
		catch (InvalidKeySpecException ex) {
			throw new ConfigurationException("Unable to restore the private key", ex);
		}
	}

	protected void loadKeys()
			throws IOException, ConfigurationException
	{
		SystemState.AsyncKey ak_state;
		PublicKey pub_key;
		PrivateKey priv_key;
		CCNTime version;

		Log.debug("Restoring keys");

		Log.debug("Restoring public keys");
		for (final PublisherPublicKeyDigest digest : _authenticated_public_key_digests) {
			ak_state = restoreKey(digest);
			if (ak_state.hasPrivateKey())
				Log.warning("Key with id " + digest.toString()
						+ "has also a private key, an error?");

			pub_key = producePublicKey(ak_state);
			version = new CCNTime(ak_state.getTimestamp());

			_publicKeyCache.remember(pub_key, version);
		}

		Log.debug("Restoring private keys");
		for (final byte[] digest : _private_key_digests) {
			final PublisherPublicKeyDigest digest_obj = new PublisherPublicKeyDigest(
					digest);
			ak_state = restoreKey(digest_obj);
			if (!ak_state.hasPrivateKey())
				throw new ConfigurationException("A private key for " + digest
						+ " is missing");

			pub_key = producePublicKey(ak_state);
			priv_key = producePrivateKey(ak_state);
			version = new CCNTime(ak_state.getTimestamp());

			_publicKeyCache.remember(pub_key, version);
			_privateKeyCache.addMyPrivateKey(digest, priv_key);
		}

		assert _defaultKeyID != null;
		pub_key = _publicKeyCache.getPublicKeyFromCache(_defaultKeyID);
		priv_key = _privateKeyCache.getPrivateKey(_defaultKeyID.digest());

		if (pub_key == null || priv_key == null)
			throw new ConfigurationException(
					"My default public and/or private key is missing!");
	}
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="disabled methods from the parent">
	@Override
	protected KeyStoreInfo loadKeyStore()
			throws ConfigurationException, IOException
	{
		throw new UnsupportedOperationException(
				"This method of loading data is unsupported in this class");
	}

	@Override
	protected boolean loadValuesFromKeystore(KeyStoreInfo keyStoreInfo)
			throws ConfigurationException
	{
		throw new UnsupportedOperationException(
				"This method of loading data is unsupported in this class");
	}

	@Override
	protected boolean loadValuesFromConfiguration(KeyStoreInfo keyStoreInfo)
			throws ConfigurationException
	{
		throw new UnsupportedOperationException(
				"This method of loading data is unsupported in this class");
	}
//</editor-fold>

	private Set<PublisherPublicKeyDigest> _authenticated_public_key_digests = Collections.
			synchronizedSet(new HashSet<PublisherPublicKeyDigest>());

	private Set<byte[]> _private_key_digests = new HashSet<byte[]>();

	private final boolean _restore;

	//TODO will eventually want a map of users to keys
	private SecretKey _WCSymmKey;

	private final KeyPair _keysForWC = generateKeys();
}
