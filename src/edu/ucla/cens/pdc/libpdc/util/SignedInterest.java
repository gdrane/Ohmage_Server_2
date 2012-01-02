/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.util;

import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.Adler32;
import java.util.zip.Checksum;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.bouncycastle.util.Arrays;
import org.ccnx.ccn.protocol.ContentName;

/**
 *
 * @author Derek Kulinski
 */
public class SignedInterest {
	private SecureRandom _random;

	private SecretKey _symmetricKey = null;

	private Cipher _cipher;

	private Set<byte[]> _nonces = new LinkedHashSet<byte[]>();

	public SignedInterest()
			throws GeneralSecurityException
	{
		this(genKey());
	}

	public SignedInterest(SecretKey key)
			throws GeneralSecurityException
	{
		_random = SecureRandom.getInstance("SHA1PRNG");
		_cipher = Cipher.getInstance("AES/CTR/NoPadding");
		_symmetricKey = key;
	}

	public byte[] signName(ContentName name)
	{
		final EncryptedMessage.SignedInterest.Builder builder = EncryptedMessage.SignedInterest.
				newBuilder();
		byte[] nonce, crc, encrypted;

		assert name != null;

		try {
			builder.setCommand(name.toString());

			nonce = genNonce();
			builder.setNonce(ByteString.copyFrom(nonce));

			crc = genChecksum(nonce, name.toString());
			builder.setCrc(ByteString.copyFrom(crc));

			encrypted = encrypt(builder.build().toByteArray());

			return encrypted;
		}
		catch (GeneralSecurityException ex) {
			throw new RuntimeException("Problem while trying to encrypt the data", ex);
		}
	}

	public ContentName verifyName(byte[] name)
	{
		EncryptedMessage.SignedInterest interest;
		byte[] decrypted, nonce, crc, newcrc;
		ContentName decName;

		// Decrypt the command
		try {
			decrypted = decrypt(name);
			interest = EncryptedMessage.SignedInterest.parseFrom(decrypted);

			decName = ContentName.fromNative(interest.getCommand());
			nonce = interest.getNonce().toByteArray();
			crc = interest.getCrc().toByteArray();

			newcrc = genChecksum(nonce, decName.toString());
			if (!Arrays.areEqual(crc, newcrc))
				return null;

			// Check the nonce
			if (_nonces.contains(nonce))
				return null;

			_nonces.add(nonce);
		}
		catch (Exception ex) {
			return null;
		}

		return decName;
	}

	/**
	 * Get the key used for signing the interests
	 * @return Key
	 */
	public Key getKey()
	{
		assert _symmetricKey != null;
		return _symmetricKey;
	}

	private static SecretKey genKey()
			throws NoSuchAlgorithmException
	{
		KeyGenerator kg = KeyGenerator.getInstance("AES");
		return kg.generateKey();
	}

	private byte[] genChecksum(byte[] nonce, String text)
	{
		byte[] input = text.getBytes();
		Checksum cksum = new Adler32();

		cksum.reset();
		cksum.update(nonce, 0, nonce.length);
		cksum.update(input, 0, input.length);

		// Generate byte array
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			dos.writeLong(cksum.getValue());
			dos.flush();
			return bos.toByteArray();
		}
		catch (IOException ex) {
			throw new Error("Unable to generate base64 encoding", ex);
		}
	}

	private byte[] genNonce()
	{
		byte[] nonce = new byte[8];

		_random.nextBytes(nonce);

		return nonce;
	}

	byte[] encrypt(byte[] input)
			throws GeneralSecurityException
	{
		byte[] ciphertext, iv, result;

		assert _symmetricKey != null;
		_cipher.init(Cipher.ENCRYPT_MODE, _symmetricKey);

		ciphertext = _cipher.doFinal(input);
		iv = _cipher.getIV();

		assert iv != null;
		assert _cipher.getBlockSize() == iv.length;

		result = new byte[ciphertext.length + iv.length];

		System.arraycopy(iv, 0, result, 0, iv.length);
		System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

		return result;
	}

	byte[] decrypt(byte[] input)
			throws GeneralSecurityException
	{
		byte[] iv, ciphertext;

		assert _symmetricKey != null;

		iv = new byte[_cipher.getBlockSize()];
		ciphertext = new byte[input.length - iv.length];

		assert iv.length + ciphertext.length == input.length;

		System.arraycopy(input, 0, iv, 0, iv.length);
		System.arraycopy(input, iv.length, ciphertext, 0, ciphertext.length);

		_cipher.init(Cipher.DECRYPT_MODE, _symmetricKey, new IvParameterSpec(iv));

		return _cipher.doFinal(ciphertext);
	}
}
