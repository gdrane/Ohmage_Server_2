/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.bson.BSONDecoder;
import org.bson.BSONEncoder;
import org.bson.BSONObject;

/**
 *
 * @author Derek Kulinski
 */
public class EncryptionHelper {
	public static SecretKey generateSymKey()
			throws PDCEncryptionException
	{
		return generateSymKey("AES", 128);
	}

	public static SecretKey generateSymKey(String algorithm, int bits)
			throws PDCEncryptionException
	{
		KeyGenerator kg;
		SecretKey secret_key;

		initRandom();

		try {
			kg = KeyGenerator.getInstance(algorithm);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new PDCEncryptionException("No " + algorithm + " available", ex);
		}

		kg.init(bits, _random);
		secret_key = kg.generateKey();

		return secret_key;
	}

	public static byte[] encryptSymData(SecretKey key, byte[] data)
			throws PDCEncryptionException
	{
		final EncryptedMessage.Symmetric.Builder builder = EncryptedMessage.Symmetric.
				newBuilder();
		Cipher cipher;
		byte[] iv, ciphertext;

		initRandom();

		try {
			cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, _random);

			iv = cipher.getIV();
			assert iv != null;
			builder.setIv(ByteString.copyFrom(iv));

			ciphertext = cipher.doFinal(data);
			builder.setCipherText(ByteString.copyFrom(ciphertext));

			return builder.build().toByteArray();
		}
		catch (GeneralSecurityException ex) {
			throw new PDCEncryptionException("Unable to encrypt the data", ex);
		}
	}

	public static byte[] decryptSymData(SecretKey key, byte[] input)
			throws PDCEncryptionException
	{
		final EncryptedMessage.Symmetric message;
		Cipher cipher;
		byte[] iv, data;

		try {
			message = EncryptedMessage.Symmetric.parseFrom(input);
		}
		catch (InvalidProtocolBufferException ex) {
			throw new PDCEncryptionException("Invalid input format", ex);
		}

		if (!message.hasIv())
			throw new PDCEncryptionException("No IV provided in the message");
		iv = message.getIv().toByteArray();

		try {
			cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

			data = cipher.doFinal(message.getCipherText().toByteArray());

			return data;
		}
		catch (GeneralSecurityException ex) {
			throw new PDCEncryptionException("Unable to decrypt the data", ex);
		}
	}

	public static byte[] wrapKey(PublicKey public_key, SecretKey secret_key)
			throws PDCEncryptionException
	{
		String alg;
		Cipher cipher;

		//CBC/PKCS1Padding or ISO9796d1Encoding, not sure what's better?
		//XXX: why CBC or CFB are bad?
		alg = public_key.getAlgorithm() + "/ECB/PKCS1Padding";

		try {
			cipher = Cipher.getInstance(alg);
			cipher.init(Cipher.WRAP_MODE, public_key);
			return cipher.wrap(secret_key);
		}
		catch (GeneralSecurityException ex) {
			throw new PDCEncryptionException("Unable to wrap key in " + public_key.
					toString(), ex);
		}
	}

	public static SecretKey unwrapKey(PrivateKey private_key, byte[] wrapped_key)
			throws PDCEncryptionException
	{
		String alg;
		Cipher cipher;
		SecretKey secret_key;

		alg = private_key.getAlgorithm() + "/ECB/PKCS1Padding";
		try {
			cipher = Cipher.getInstance(alg);
			cipher.init(Cipher.UNWRAP_MODE, private_key);
			secret_key = (SecretKey) cipher.unwrap(wrapped_key, "AES",
					Cipher.SECRET_KEY);

			return secret_key;
		}
		catch (GeneralSecurityException ex) {
			throw new PDCEncryptionException("Unable to unwrap key", ex);
		}
	}

	public static byte[] encryptAsymData(PublicKey public_key, byte[] data)
			throws PDCEncryptionException
	{
		BSONEncoder encoder = new BSONEncoder();
		DBObject obj = new BasicDBList();
		SecureRandom random = new SecureRandom();
		KeyGenerator kg;
		Cipher cipher;
		SecretKey secret_key;
		byte[] ciphertext;
		byte[] iv;

		try {
			kg = KeyGenerator.getInstance("AES");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new PDCEncryptionException("No AES available", ex);
		}

		kg.init(128, random);
		secret_key = kg.generateKey();

		obj.put("0", wrapKey(public_key, secret_key));

		try {
			cipher = Cipher.getInstance("AES/CTR/PKCS7Padding");

			iv = new byte[cipher.getBlockSize()];
			random.nextBytes(iv);
			obj.put("1", iv);

			cipher.init(Cipher.ENCRYPT_MODE, secret_key, new IvParameterSpec(iv));

			ciphertext = cipher.doFinal(data);
			obj.put("2", ciphertext);

			return encoder.encode(obj);
		}
		catch (GeneralSecurityException ex) {
			throw new PDCEncryptionException("Unable to encrypt message to "
					+ public_key.toString(), ex);
		}
	}

	public static byte[] decryptAsymData(PrivateKey private_key, byte[] cipherText)
			throws PDCEncryptionException
	{
		BSONDecoder decoder = new BSONDecoder();
		Cipher cipher;
		BSONObject obj;
		SecretKey secret_key;
		byte[] aes_ciphertext, iv;

		assert private_key != null;

		try {
			obj = decoder.readObject(cipherText);
		}
		catch (RuntimeException ex) {
			throw new PDCEncryptionException("invalida data", ex);
		}

		secret_key = unwrapKey(private_key, (byte[]) obj.get("0"));
		iv = (byte[]) obj.get("1");
		try {
			cipher = Cipher.getInstance("AES/CTR/PKCS7Padding");
			cipher.init(Cipher.DECRYPT_MODE, secret_key, new IvParameterSpec(iv));

			aes_ciphertext = (byte[]) obj.get("2");

			return cipher.doFinal(aes_ciphertext);
		}
		catch (GeneralSecurityException ex) {
			throw new PDCEncryptionException("Unable to decrypt message", ex);
		}
	}

	private static void initRandom()
	{
		if (_random == null)
			_random = new SecureRandom();
	}

	private static SecureRandom _random = null;
}
