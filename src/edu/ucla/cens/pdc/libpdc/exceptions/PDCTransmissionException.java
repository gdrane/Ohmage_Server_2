/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.exceptions;

/**
 *
 * @author Derek Kulinski
 */
public class PDCTransmissionException extends PDCException {
	public PDCTransmissionException(Throwable cause)
	{
		super(cause);
	}

	public PDCTransmissionException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public PDCTransmissionException(String message)
	{
		super(message);
	}

	public PDCTransmissionException()
	{
		super();
	}
}
