/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

/**
 *
 * @author Derek Kulinski
 */
abstract public class Command {
	protected final String COMMAND;

	public Command(String name)
	{
		this.COMMAND = name;
	}

	final String getCommandName()
	{
		return COMMAND;
	}
}
