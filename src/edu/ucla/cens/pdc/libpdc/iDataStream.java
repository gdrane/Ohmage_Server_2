/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc;

import edu.ucla.cens.pdc.libpdc.stream.Storage;
import edu.ucla.cens.pdc.libpdc.transport.PDCPublisher;
import edu.ucla.cens.pdc.libpdc.transport.PDCReceiver;
import edu.ucla.cens.pdc.libpdc.stream.StreamTransport;
import java.util.Collection;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 *
 * @author Derek Kulinski
 */
public interface iDataStream {
	/**
	 * Get instance of class responsible for storing the data in a database
	 * @return
	 */
	public Storage getStorage();

	/**
	 * Get instance of class responsible for transmission of data
	 * over NDN network
	 * @return
	 */
	public StreamTransport getTransport();

	public ContentName getPublisherStreamURI();

	public ContentName getReceiverStreamURI(PDCReceiver receiver)
			throws MalformedContentNameStringException;

	public void setPublisher(PDCPublisher node);

	public PDCPublisher getPublisher();

	public void addReceiver(PDCReceiver node);

	public void delReceiver(PDCReceiver node);

	public Collection<PDCReceiver> getReceivers();

	public PDCReceiver name2receiver(ContentName name_id);
}
