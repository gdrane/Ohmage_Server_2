/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.stream;

import com.google.protobuf.ByteString;
import edu.ucla.cens.pdc.libpdc.SystemState;
import edu.ucla.cens.pdc.libpdc.datastructures.DataRecord;
import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.core.PDCKeyManager;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCParseException;
import edu.ucla.cens.pdc.libpdc.iApplication;
import edu.ucla.cens.pdc.libpdc.iBSONExportable;
import edu.ucla.cens.pdc.libpdc.transport.PDCNode;
import edu.ucla.cens.pdc.libpdc.util.EncryptionHelper;
import edu.ucla.cens.pdc.libpdc.util.Log;
import edu.ucla.cens.pdc.libpdc.util.StringUtil;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.List;
import javax.crypto.SecretKey;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.engines.TwofishEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;

/**
 *
 * @author Derek Kulinski
 */
public class DataEncryptor {
	public DataEncryptor(DataStream stream)
	{
		_stream = stream;
		setupObject();
	}

	public DataEncryptor(DataStream stream, SystemState.DataEncryptor state)
			throws PDCException
	{
		_stream = stream;

		if (state.hasEncryptionKey())
			_encrypt_key = state.getEncryptionKey().toByteArray();

		if (state.hasDecryptionKey())
			_decrypt_key = state.getDecryptionKey().toByteArray();

		if (state.hasStreamKeyDigest())
			try {
				_stream_key_digest = new PublisherPublicKeyDigest(
						state.getStreamKeyDigest());
			}
			catch (IOException ex) {
				throw new PDCException("Unable to generate stream key digest", ex);
			}

		setupObject();
	}

	private void setupObject()
	{
		BlockCipher engine = new TwofishEngine();

		//TODO: Make use of CTR/SIC mode (needs a good iv)
//		BlockCipher mode = new SICBlockCipher(engine);
		BlockCipher mode = new CBCBlockCipher(engine);

//		_cipher = new BufferedBlockCipher(mode);
//		_cipher = new CTSBlockCipher(mode);
		_cipher = new PaddedBufferedBlockCipher(mode);

//		// This version doesn't do any encryption
//		BlockCipher engine = new NullEngine();
//		_cipher = new BufferedBlockCipher(engine);
	}

	public byte[] getEncryptKey()
	{
		return _encrypt_key;
	}

	public byte[] getDecryptKey()
	{
		return _decrypt_key;
	}

	public void setEncryptKey(byte[] key)
	{
		this._encrypt_key = key;
	}

	public void setDecryptKey(byte[] key)
	{
		this._decrypt_key = key;
	}

	/**
	 * Get hash of the stream's public key
	 * @return hash of stream's public key or null
	 */
	public PublisherPublicKeyDigest getStreamKeyDigest()
	{
		return _stream_key_digest;
	}

	/**
	 * Generate new asymmetric keys for the stream
	 * Don't forget to save stream's state or the
	 * keys will be lost
	 * @return hash of stream's public key
	 */
	PublisherPublicKeyDigest generateStreamKeys()
	{
		GlobalConfig config = GlobalConfig.getInstance();

		PDCKeyManager keymgr = config.getKeyManager();
		assert keymgr != null;

		_stream_key_digest = keymgr.generatePublisherKeys();
		assert _stream_key_digest != null;

		return _stream_key_digest;
	}

	public byte[] encryptRecord(iBSONExportable record)
			throws PDCEncryptionException
	{
		assert record != null;
		return encryptData(record.toBSON());
	}

	public byte[] encryptData(byte[] data)
			throws PDCEncryptionException
	{
		byte[] ciphertext;
		int output_len;

		if (_encrypt_key == null) {
			Log.info("No symmetric key set previously, generating a new one");
			generateNewKey();
		}

		_cipher.init(true, new KeyParameter(_encrypt_key));
//		_cipher.reset();

//		byte[] iv = generateIV(application, stream);
//		Log.debug(new String(iv));
//		_cipher.init(true, new ParametersWithIV(new KeyParameter(aclkey), iv));

		ciphertext = new byte[_cipher.getOutputSize(data.length)];
		output_len = _cipher.processBytes(data, 0, data.length, ciphertext, 0);

		try {
			_cipher.doFinal(ciphertext, output_len);
			return ciphertext;
		}
		catch (Exception ex) {
			throw new PDCEncryptionException("Unable to encrypt stream", ex);
		}
	}

	public DataRecord decryptRecord(byte[] cipherText)
			throws PDCEncryptionException
	{
		byte[] decrypted;

		decrypted = decryptData(cipherText);

		try {
			return new DataRecord().fromBSON(decrypted);
		}
		catch (PDCParseException ex) {
			throw new PDCEncryptionException("Got invalid BSON", ex);
		}
	}

	public byte[] decryptData(byte[] cipherText)
			throws PDCEncryptionException
	{
		byte[] buffer, decrypted;
		int outputLen;

		if (_decrypt_key == null)
			throw new PDCEncryptionException("No decryption key set");

		_cipher.init(false, new KeyParameter(_decrypt_key));

//		byte[] iv = generateIV(app, acl);
//		_cipher.init(false, new ParametersWithIV(new KeyParameter(aclkey), iv));

		buffer = new byte[_cipher.getOutputSize(cipherText.length)];
		outputLen = _cipher.processBytes(cipherText, 0, cipherText.length, buffer,
				0);

		try {
			outputLen += _cipher.doFinal(buffer, outputLen);

			decrypted = new byte[outputLen];
			System.arraycopy(buffer, 0, decrypted, 0, outputLen);

			return decrypted;
		}
		catch (Exception ex) {
			throw new PDCEncryptionException("Unable to decrypt the data", ex);
		}
	}

	/**
	 * Get node's public key
	 * @param node node of which public key we want to get
	 * @return public key or null
	 */
	PublicKey getPublicKey(PDCNode node)
	{
		assert node != null;

		final GlobalConfig config = GlobalConfig.getInstance();
		final KeyManager kmgr = config.getKeyManager();
		PublicKey key;
		PublisherPublicKeyDigest digest;
		KeyLocator locator;

		assert kmgr != null;

		digest = node.getPublickeyDigest();
		assert digest != null : "Digest of node " + node.uri.toURIString()
				+ " is not available";

		locator = _stream.getKeyLocator(node);
		assert locator != null;

		Log.debug("Obtaining key " + locator + " " + digest);

		if (digest == null || locator == null) {
			Log.warning("Don't have enough information to fetch public key of "
					+ node.uri.toURIString());
			return null;
		}

		try {
			key = kmgr.getPublicKey(digest, locator);
			if (key == null)
				Log.warning("No key available " + locator + " " + digest);
		}
		catch (IOException ex) {
			Log.error("Unable to obtain key for " + node.uri.toURIString() + ": "
					+ ex.getMessage());
			return null;
		}

		return key;
	}

	public byte[] encryptAsymData(PDCNode node, iBSONExportable data)
			throws PDCEncryptionException
	{
		return encryptAsymData(node, data.toBSON());
	}

	public byte[] encryptAsymData(PDCNode node, byte[] data)
			throws PDCEncryptionException
	{
		assert node != null;
		assert data != null;

		PublicKey public_key;

		public_key = getPublicKey(node);
		if (public_key == null)
			throw new PDCEncryptionException("Public key not available");

		return EncryptionHelper.encryptAsymData(public_key, data);
	}

	public byte[] decryptAsymData(byte[] cipherText)
			throws PDCEncryptionException
	{
		GlobalConfig config = GlobalConfig.getInstance();
		KeyManager keymgr;
		PrivateKey private_key;

		Log.debug("Decrypting data using my key: " + _stream_key_digest);

		keymgr = config.getKeyManager();
		assert keymgr != null;

		private_key = keymgr.getSigningKey(_stream_key_digest);
		assert private_key != null;

		return EncryptionHelper.decryptAsymData(private_key, cipherText);
	}

	private byte[] repeat(int size, String pattern)
	{
		byte[] result = new byte[size];
		byte[] pat = pattern.getBytes();
		int result_pos, pat_pos;

		for (result_pos = 0, pat_pos = 0; result_pos < size; result_pos++) {
			result[result_pos] = pat[pat_pos];

			pat_pos++;
			pat_pos %= pat.length;
		}

		return result;
	}

	public void generateNewKey()
	{
		try {
			SecretKey key = EncryptionHelper.generateSymKey("TwoFish", 256);
			_encrypt_key = key.getEncoded();
		}
		catch (PDCEncryptionException ex) {
			throw new Error("Unable to generate new key", ex);
		}
	}

	private byte[] generateIV(iApplication app, String data_stream)
	{
		return repeat(_cipher.getBlockSize(), data_stream.concat(app.getAppName()));
	}

	@Override
	public String toString()
	{
		List<String> data = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();

		sb.append(this.getClass().getSimpleName());
		sb.append('[');
		data.add(_cipher.toString());
		data.add(_decrypt_iv == null ? null : "set");
		data.add(_decrypt_key == null ? null : "set");
		data.add(_encrypt_iv == null ? null : "set");
		data.add(_encrypt_key == null ? null : "set");
		data.add(_stream_key_digest == null ? null : _stream_key_digest.toString());
		sb.append(StringUtil.join(data, "; "));
		sb.append(']');

		return sb.toString();
	}

// <editor-fold defaultstate="collapsed" desc="serialization code">
	SystemState.DataEncryptor getObjectState()
	{
		SystemState.DataEncryptor.Builder builder = SystemState.DataEncryptor.
				newBuilder();

		if (_encrypt_key != null)
			builder.setEncryptionKey(ByteString.copyFrom(_encrypt_key));

		if (_decrypt_key != null)
			builder.setDecryptionKey(ByteString.copyFrom(_decrypt_key));

		if (_stream_key_digest != null)
			builder.setStreamKeyDigest(_stream_key_digest.toString());

		return builder.build();
	}
// </editor-fold>

	private transient final DataStream _stream;

	private transient BufferedBlockCipher _cipher;

	private byte[] _encrypt_key;

	private transient byte[] _encrypt_iv;

	private byte[] _decrypt_key;

	private transient byte[] _decrypt_iv;

	/**
	 * keys associated with our stream
	 */
	protected PublisherPublicKeyDigest _stream_key_digest;
}
