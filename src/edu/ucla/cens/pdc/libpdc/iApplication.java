/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc;

import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import org.ccnx.ccn.protocol.ContentName;

/**
 *
 * @author Derek Kulinski
 */
public interface iApplication {
	String getAppName();
	ContentName getBaseName();
	boolean isDataStream(String id);
	DataStream getDataStream(String id);
	DataStream addDataStream(DataStream ds);
}
