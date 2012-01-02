		/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc;

import edu.ucla.cens.pdc.libpdc.core.GlobalConfig;
import edu.ucla.cens.pdc.libpdc.stream.DataStream;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import edu.ucla.cens.pdc.libpdc.util.Log;
import edu.ucla.cens.pdc.libpdc.util.StringUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;

/**
 * Stores information about applications that PDV handles
 * @author Derek Kulinski
 */
public class Application implements iApplication, iState {
	public Application(String name)
			throws IOException
	{
		this(name, false);
	}

	public Application(String name, boolean restore)
			throws IOException
	{
		if (restore) {
			GlobalConfig config = GlobalConfig.getInstance();
			byte[] byte_in;
			SystemState.Application state;
			CCNHandle handle = config.getCCNHandle();

			byte_in = config.loadConfig("applications", name);
			state = SystemState.Application.parseFrom(byte_in);

			this.name = state.getName();

			assert name.equals(this.name) : "Saved state name differs: " + name
					+ " vs " + this.name;

			this._pull_rate = state.getPullRate();
			setupObject();

			for (String stream_name : state.getDataStreamsList()) {
				DataStream stream = new DataStream(handle, this, stream_name, true);
				addDataStream(stream);
			}
		} else {
			this.name = name;
			setupObject();
		}
	}

	private void setupObject()
	{
		GlobalConfig config = GlobalConfig.getInstance();

		try {
			_base_uri = config.getRoot().append(name);
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error("Unable to set base name", ex);
		}

		_data_stream = new HashMap<String, DataStream>();
		_to_pull = new LinkedHashSet<String>();
	}

	/**
	 * Returns name of the application
	 * @return application name
	 */
	public String getAppName()
	{
		return name;
	}

	public ContentName getBaseName()
	{
		return _base_uri;
	}

	/**
	 * Check if given data stream exists
	 * @param id
	 * @return
	 */
	synchronized public boolean isDataStream(String id)
	{
		return _data_stream.containsKey(id);
	}

	synchronized public DataStream getDataStream(String id)
	{
		return _data_stream.get(id);
	}

	public final synchronized DataStream addDataStream(DataStream ds)
	{
		return _data_stream.put(ds.data_stream_id, ds);
	}

	synchronized public Iterator<DataStream> getDataStreamsIterator()
	{
		return _data_stream.values().iterator();
	}

	/**
	 * TODO maybe make this part of the interface?
	 * @return
	 */
	public synchronized Set<String> getDataStreamList()
	{
		return Collections.unmodifiableSet(_data_stream.keySet());
	}

	public synchronized Set<String> getDataStreamList(String username)
	{
		Iterator<DataStream> iter = getDataStreamsIterator();
		Set<String> user_streams = new LinkedHashSet<String>();
		while(iter.hasNext())
		{
			DataStream ds = iter.next();
			if(ds.getUsername() == username) {
				user_streams.add(ds.getUsername());
			}
		}
		return user_streams;
	}
	
	synchronized private boolean performPull()
	{
		Iterator<String> iter;
		String ds_id;
		DataStream ds;

		if (_to_pull.isEmpty())
			_to_pull.addAll(_data_stream.keySet());

		Log.debug("Calling pull; iterating through " + _to_pull.size()
				+ " entries ...");

		iter = _to_pull.iterator();
		while (iter.hasNext()) {
			ds_id = iter.next();

			ds = _data_stream.get(ds_id);
			if (ds == null)
				continue;

			if (ds.getPublisher() == null)
				continue;

			iter.remove();

			try {
				Log.debug("Making " + ds.data_stream_id + " fetch data from the uplink");
				return ds.fetchNewData();
			}
			catch (PDCTransmissionException ex) {
				Log.error("Unable to fetch data from uplink: " + ex.getMessage());
			}
		}

		return false;
	}

	synchronized public void periodicPullStart(long period)
	{
		if (_pull_rate > 0)
			periodicPullStop();

		Log.debug("Setting up pull for " + period + " seconds");

		_pull_rate = period;

		if (_scheduler == null || _scheduler.isTerminated())
			_scheduler = Executors.newSingleThreadScheduledExecutor();

		_scheduler.schedule(new Runnable() {
			public void run()
			{
				if (_pull_rate == 0)
					return;

				Log.debug("Performing pull...");
				performPull();
				Log.debug("Scheduling next task...");

				_scheduler.schedule(this, _pull_rate, TimeUnit.SECONDS);
			}
		}, _pull_rate, TimeUnit.SECONDS);
	}

	synchronized public void periodicPullStop()
	{
		if (_pull_rate == 0)
			return;

		_pull_rate = 0;
		if (_scheduler != null)
			_scheduler.shutdown();
	}

	@Override
	public String toString()
	{
		List<String> data = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();

		sb.append(this.getClass().getSimpleName());
		sb.append('[');
		data.add(name);
		data.add(_base_uri.toURIString());
		data.add(_data_stream.toString());
		sb.append(StringUtil.join(data, "; "));
		sb.append(']');

		return sb.toString();
	}

// <editor-fold defaultstate="collapsed" desc="serialization code">
	public String getStateGroupName()
	{
		return "applications";
	}

	public String getStateKey()
	{
		return name;
	}

	public void storeState()
			throws IOException
	{
		GlobalConfig config = GlobalConfig.getInstance();

		config.saveConfig(this);
	}

	public void storeStateRecursive()
			throws IOException
	{
		for (DataStream ds : _data_stream.values())
			ds.storeStateRecursive();

		storeState();
	}

	SystemState.Application getObjectState()
	{
		SystemState.Application.Builder builder = SystemState.Application.newBuilder();

		builder.setName(name);
		builder.addAllDataStreams(_data_stream.keySet());
		builder.setPullRate(_pull_rate);

		return builder.build();
	}

	public byte[] stateToByteArray()
	{
		return getObjectState().toByteArray();
	}
// </editor-fold>

	/**
	 * Identifier for the application
	 */
	public final String name;

	private transient ContentName _base_uri;

	private transient Map<String, DataStream> _data_stream;

	private transient Set<String> _to_pull;

	private transient ScheduledExecutorService _scheduler = null;

	private long _pull_rate = 0;
}
