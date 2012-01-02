/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.exceptions;

/**
 *
 * @author Derek Kulinski
 */
public class PDCDatabaseException extends PDCException {
	public PDCDatabaseException(Throwable cause)
	{
		super(cause);
	}

	public PDCDatabaseException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public PDCDatabaseException(String message)
	{
		super(message);
	}

	public PDCDatabaseException()
	{
		super();
	}
}
