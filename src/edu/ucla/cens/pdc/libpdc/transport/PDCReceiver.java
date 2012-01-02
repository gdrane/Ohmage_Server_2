/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.transport;

import edu.ucla.cens.pdc.libpdc.SystemState;
import java.io.IOException;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 *
 * @author Derek Kulinski
 */
public class PDCReceiver extends PDCNode {
	public PDCReceiver(String uri)
			throws MalformedContentNameStringException
	{
		super(uri);
	}

	public PDCReceiver(SystemState.PDCReceiver state)
			throws MalformedContentNameStringException, IOException
	{
		super(state.getBase());
	}

	@Override
	public SystemState.PDCReceiver getObjectState()
	{
		SystemState.PDCReceiver.Builder builder = SystemState.PDCReceiver.newBuilder();

		builder.setBase((SystemState.PDCNode) super.getObjectState());

		return builder.build();
	}
}
