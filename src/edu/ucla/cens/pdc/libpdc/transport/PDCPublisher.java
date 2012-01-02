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
public class PDCPublisher extends PDCNode {
	public PDCPublisher(String uri)
			throws MalformedContentNameStringException
	{
		super(uri);
	}

	public PDCPublisher(SystemState.PDCPublisher state)
			throws MalformedContentNameStringException, IOException
	{
		super(state.getBase());
	}

	@Override
	public SystemState.PDCPublisher getObjectState()
	{
		SystemState.PDCPublisher.Builder builder = SystemState.PDCPublisher.
				newBuilder();

		builder.setBase((SystemState.PDCNode) super.getObjectState());

		return builder.build();
	}
}
