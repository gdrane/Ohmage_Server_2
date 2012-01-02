/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;

/**
 *
 * @author Derek Kulinski
 */
abstract class StreamCommand extends Command {
	public StreamCommand(String name)
	{
		super(name);
	}

	abstract boolean processCommand(DataStream ds, ContentName postfix,
					Interest interest);
}
