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
public interface iState {
	/**
	 * Returns name of the group in which the element belongs
	 * @return name of the group
	 */
	public String getStateGroupName();

	/**
	 * Returns unique identifier of the element in the group
	 * @return id of the element
	 */
	public String getStateKey();

	public void storeState()
					throws IOException;

	public void storeStateRecursive()
					throws IOException;

	/*
	 * The class should also implement static method
	 * public static loadState(String name) throws IOException
	 */

	public byte[] stateToByteArray();
}
