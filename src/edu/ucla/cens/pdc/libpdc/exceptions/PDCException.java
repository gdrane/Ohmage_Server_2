/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.exceptions;

/**
 *
 * @author Derek Kulinski
 */
public class PDCException extends Exception {
	public PDCException()
	{
		super();
	}

	public PDCException(String message)
	{
		super(message);
	}

	public PDCException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public PDCException(Throwable cause)
	{
		super(cause);
	}
}
