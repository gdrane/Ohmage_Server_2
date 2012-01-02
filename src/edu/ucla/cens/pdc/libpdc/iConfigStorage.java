/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc;

import java.io.IOException;

/**
 *
 * @author Derek Kulinski
 */
public interface iConfigStorage {
	public byte[] loadEntry(String group, String name)
					throws IOException;

	public void saveEntry(String obj_group, String obj_key,
					byte[] byte_out)
					throws IOException;

	public void removeEntry(String group, String name)
					throws IOException;
}
