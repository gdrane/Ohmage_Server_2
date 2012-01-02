/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.util;

import java.security.Key;

/**
 *
 * @author Derek Kulinski
 */
public class MiscFuncs {
	public static void printKeyToLog(Key key, String message)
	{
		byte[] keyBytes = key.getEncoded();
		if (keyBytes == null) {
			Log.error("Couldn't get key bytes.");
			return;
		}

		StringBuilder keyBuf = new StringBuilder();
		for (int i = 0; i < keyBytes.length; i++) {
			String hex = Integer.toHexString(0xFF & keyBytes[i]);
			if (hex.length() == 1)
				keyBuf.append('0');
			keyBuf.append(hex);
		}

		Log.debug(message + keyBuf.toString());
	}
}
