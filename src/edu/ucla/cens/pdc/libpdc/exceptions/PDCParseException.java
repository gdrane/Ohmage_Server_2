/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.exceptions;

/**
 *
 * @author Derek Kulinski
 */
public class PDCParseException extends PDCException {
	public PDCParseException(Throwable cause)
	{
		super(cause);
	}

	public PDCParseException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public PDCParseException(String message)
	{
		super(message);
	}

	public PDCParseException()
	{
		super();
	}
}
