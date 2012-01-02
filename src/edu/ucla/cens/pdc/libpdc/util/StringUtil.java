/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

/**
 *
 * @author Derek Kulinski
 */
public class StringUtil {
	private static final char[] _alphanumeric = new char[36];

	private static final Random _random = new Random();

	static {
		for (int i = 0; i < 10; i++)
			_alphanumeric[i] = (char) ('0' + i);
		for (int i = 10; i < _alphanumeric.length; i++)
			_alphanumeric[i] = (char) ('a' + i - 10);
	}

	public static String join(Collection<String> list, String delimiter)
	{
		if (list == null || list.isEmpty())
			return "";

		Iterator<String> iter = list.iterator();
		StringBuilder sb = new StringBuilder(iter.next());
		while (iter.hasNext()) {
			sb.append(delimiter).append(iter.next());
		}

		return sb.toString();
	}

	public static String stripPrefixChar(String s, String prefix)
	{
		return s.startsWith(prefix) ? s.substring(1) : s;
	}

	public static String genrateRandomString(int len)
	{
		if (len < 1)
			throw new IllegalArgumentException("Invalid length given: " + len);

		final char[] buf = new char[len];
		for (int i = 0; i < len; i++)
			buf[i] = _alphanumeric[_random.nextInt(_alphanumeric.length)];

		return new String(buf);
	}
}
