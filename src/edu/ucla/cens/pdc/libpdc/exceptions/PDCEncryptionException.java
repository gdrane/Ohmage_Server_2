/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.exceptions;

/**
 *
 * @author Derek Kulinski
 */
public class PDCEncryptionException extends PDCException {
	public PDCEncryptionException(Throwable cause)
	{
		super(cause);
	}

	public PDCEncryptionException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public PDCEncryptionException(String message)
	{
		super(message);
	}

	public PDCEncryptionException()
	{
		super();
	}
}
