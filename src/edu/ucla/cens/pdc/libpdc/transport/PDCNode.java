/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.transport;

import edu.ucla.cens.pdc.libpdc.SystemState;
import edu.ucla.cens.pdc.libpdc.stream.DataEncryptor;
import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.iBSONExportable;
import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import edu.ucla.cens.pdc.libpdc.util.Log;
import edu.ucla.cens.pdc.libpdc.util.StringUtil;
import java.io.IOException;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.List;
import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;

/**
 * Stores information about every node we communicate with
 * @author Derek Kulinski
 */
public class PDCNode {
	public PDCNode(String uri)
			throws MalformedContentNameStringException
	{
		this.uri = ContentName.fromURI(uri);
	}

	public PDCNode(SystemState.PDCNode state)
			throws MalformedContentNameStringException, IOException
	{
		this(state.getUri());

		if (state.hasKeyDigest())
			_publickey_digest = new PublisherPublicKeyDigest(state.getKeyDigest());

		if (state.hasKeyLocator()) {
			assert state.hasKeyDigest();

			final ContentName location = ContentName.fromNative(state.getKeyLocator());
			_key_locator = new KeyLocator(location, _publickey_digest);
		}
	}

	public KeyLocator getKeyLocator()
	{
		return _key_locator;
	}

	public void setKeyLocator(KeyLocator _key_locator)
	{
		this._key_locator = _key_locator;
	}

	public PublisherPublicKeyDigest getPublickeyDigest()
	{
		return _publickey_digest;
	}

	public void setPublickeyDigest(PublisherPublicKeyDigest digest)
	{
		Log.debug("Setting public key digest to: " + digest.toString());
		this._publickey_digest = digest;
	}

	public String getAuthenticator()
	{
		return _authenticator;
	}

	public void setAuthenticator(String authenticator)
	{
		this._authenticator = authenticator;
	}

	@Override
	public String toString()
	{
		List<String> data = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();

		sb.append(this.getClass().getSimpleName());
		sb.append('[');
		data.add(uri.toURIString());
		data.add(_publickey_digest == null ? null : _publickey_digest.toString());
		sb.append(StringUtil.join(data, "; "));
		sb.append(']');

		return sb.toString();
	}

	Object getObjectState()
	{
		SystemState.PDCNode.Builder builder = SystemState.PDCNode.newBuilder();

		builder.setUri(uri.toString());

		if (_publickey_digest != null)
			builder.setKeyDigest(_publickey_digest.toString());

		//XXX: maybe we should store more? but then _publickey_digest stores
		//the digest, so we'll duplicate
		if (_key_locator != null && _key_locator.name() != null)
			builder.setKeyLocator(_key_locator.name().name().toString());

		return builder.build();
	}

	/**
	 * URI of the node
	 */
	public final ContentName uri;

	/**
	 * Node's public key digest
	 */
	protected PublisherPublicKeyDigest _publickey_digest;

	private transient String _authenticator;

	/**
	 * Key locator for the  node
	 */
	protected KeyLocator _key_locator;

}
