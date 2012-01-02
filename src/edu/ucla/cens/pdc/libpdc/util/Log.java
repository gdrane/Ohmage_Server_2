/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.util;

/**
 *
 * @author Derek Kulinski
 */
public class Log {
	static boolean isAssertionEnabled = false;

	static {
		assert isAssertionEnabled = true;
	}

	public static void debug(Object o)
	{
		//if (!isAssertionEnabled)
		//	return;

		System.err.print("DBG : ");
		System.err.println(o);
	}

	public static void info(Object o)
	{
		System.err.print("INFO: ");
		System.err.println(o);
	}

	public static void warning(Object o)
	{
		System.err.print("WARN: ");
		System.err.println(o);
	}

	public static void error(Object o)
	{
		System.err.print("ERR : ");
		System.err.println(o);
	}
}
