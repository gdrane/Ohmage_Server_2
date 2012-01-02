/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.exceptions;

/**
 *
 * @author Derek Kulinski
 */
public class PDCDataNotFoundException extends PDCException {
	public PDCDataNotFoundException(Throwable cause)
	{
		super(cause);
	}

	public PDCDataNotFoundException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public PDCDataNotFoundException(String message)
	{
		super(message);
	}

	public PDCDataNotFoundException()
	{
		super();
	}
}
