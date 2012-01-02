/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.unused;

import edu.ucla.cens.pdc.libpdc.stream.DataEncryptor;
import edu.ucla.cens.pdc.libpdc.datastructures.DataRecord;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.impl.support.DataUtils;
import org.ccnx.ccn.io.content.CCNNetworkObject;
import org.ccnx.ccn.io.content.ContentDecodingException;
import org.ccnx.ccn.io.content.ContentEncodingException;
import org.ccnx.ccn.io.content.ContentNotReadyException;
import org.ccnx.ccn.protocol.ContentName;

/**
 *
 * @author Derek Kulinski
 */
public class CCNDataRecordObject extends CCNNetworkObject<DataRecord> {
	private DataEncryptor _encryptor;

	public CCNDataRecordObject(DataEncryptor encryptor, ContentName name,
					CCNHandle handle)
					throws IOException
	{
		super(DataRecord.class, true, name, handle);

		assert encryptor != null;

		this._encryptor = encryptor;
	}

	@Override
	protected void writeObjectImpl(OutputStream output)
					throws ContentEncodingException, IOException
	{
		byte[] out;

		if (data() == null)
			throw new ContentNotReadyException("No content available to save for object "
							+ getBaseName());
		try {
			out = _encryptor.encryptRecord(data());
			output.write(out);
		}
		catch (PDCException ex) {
			throw new ContentEncodingException("Unable to encrypt the data", ex);
		}

	}

	@Override
	protected DataRecord readObjectImpl(InputStream input)
					throws ContentDecodingException, IOException
	{
		byte[] in;

		in = DataUtils.getBytesFromStream(input);
		try {
			return _encryptor.decryptRecord(in);
		}
		catch (PDCEncryptionException ex) {
			throw new ContentDecodingException("Unable to decrypt the data", ex);
		}
	}
}
