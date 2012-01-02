/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;

/**
 *
 * @author Derek Kulinski
 */
abstract public class GenericCommand extends Command {
	public GenericCommand(String name)
	{
		super(name);
	}

	abstract boolean processCommand(ContentName postfix, Interest interest);
}
