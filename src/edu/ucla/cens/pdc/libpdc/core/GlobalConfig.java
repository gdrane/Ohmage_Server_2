/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

import edu.ucla.cens.pdc.libpdc.Application;
import edu.ucla.cens.pdc.libpdc.SystemState;
import edu.ucla.cens.pdc.libpdc.iApplication;
import edu.ucla.cens.pdc.libpdc.iConfigStorage;
import edu.ucla.cens.pdc.libpdc.iState;
import edu.ucla.cens.pdc.libpdc.stream.Storage;
import edu.ucla.cens.pdc.libpdc.util.StringUtil;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 *
 * @author Derek Kulinski
 */
public class GlobalConfig implements iState {
	@SuppressWarnings("LeakingThisInConstructor")
	private GlobalConfig()
	{
		_config_storage = setupConfigStorage();
		try {
			_instance = this;
			setupObject(false);
		}
		catch (IOException ex) {
			_instance = null;
			throw new Error("Cannot instantiate config", ex);
		}
		catch (RuntimeException ex) {
			_instance = null;
			throw ex;
		}
		catch (Error ex) {
			_instance = null;
			throw ex;
		}
	}

	@SuppressWarnings("LeakingThisInConstructor")
	private GlobalConfig(SystemState.GlobalConfig state)
			throws IOException
	{
		assert _instance == null;
		assert state != null;
		assert state.hasRoot();

		try {
			_root = ContentName.fromNative(state.getRoot());
		}
		catch (MalformedContentNameStringException ex) {
			throw new Error("Unable to parse previously stored root", ex);
		}

		try {
			_config_storage = setupConfigStorage();
			_instance = this;
			setupObject(true);

			for (String app_name : state.getApplicationsList()) {
				Application app = new Application(app_name, true);
				addApplication(app);
			}
		}
		catch (IOException ex) {
			_instance = null;
			throw ex;
		}
		catch (RuntimeException ex) {
			_instance = null;
			throw new Error("Failure when initializing the object", ex);
		}
		catch (Error ex) {
			_instance = null;
			throw new Error("Failure when initializing the object", ex);
		}
	}

	private void setupObject(boolean restore)
			throws IOException
	{
		PDCKeyManager keymanager;

		try {
			keymanager = new PDCKeyManager(restore);
			keymanager.initialize();

			setKeyManager(keymanager);
		}
		catch (ConfigurationException ex) {
			throw new Error("Unable to setup keymanager", ex);
		}
	}

	/**
	 * Provide class responsible for handling data storage
	 * @param name class name
	 */
	public static void setDataStorage(Class<? extends Storage> name)
	{
		if (_data_storage_class != null)
			throw new Error("You shouldn't change data storage type");

		_data_storage_class = name;
	}

	public static Storage setupDataStorage(iApplication app, String data_stream_id)
	{
		Constructor<? extends Storage> constructor;
		Storage store;

		assert app != null;
		assert _data_storage_class != null;

		try {
			constructor = _data_storage_class.getConstructor(app.getClass(),
					String.class);
			store = constructor.newInstance(app, data_stream_id);
		}
		catch (Exception ex) {
			throw new Error("Unable instantiate the storage class", ex);
		}

		return store;
	}

	/**
	 * Provide class responsible for handling configuration storage
	 */
	public static void setConfigStorage(Class<? extends iConfigStorage> name)
	{
		if (_config_storage_class != null)
			throw new Error("You shouldn't change config storage type");

		_config_storage_class = name;
	}

	public static iConfigStorage setupConfigStorage()
	{
		if (_config_storage_class == null)
			throw new Error("You need to provide the storage class first");

		try {
			return _config_storage_class.newInstance();
		}
		catch (Exception ex) {
			throw new Error("Error while instantiating storage object", ex);
		}
	}

	public static void setFeatures(int features)
	{
		_features = features;
	}

	public static int getFeatures()
	{
		return _features;
	}

	public static boolean hasFeature(int feature)
	{
		return (_features & feature) != 0;
	}

	/**
	 * Set the PDC's root
	 * @param root PDC's root
	 */
	public void setRoot(ContentName root)
	{
		if (_root != null)
			throw new UnsupportedOperationException(
					"You can't change the root once it is already set");

		_root = root;
	}

	/**
	 * Get the PDC's root
	 * @return PDC's root
	 */
	public ContentName getRoot()
	{
		assert _root != null : "Root wasn't set";

		return _root;
	}

	public CCNHandle getCCNHandle()
	{
		assert _key_manager != null;

		try {
			if (_ccn_handle == null)
				_ccn_handle = CCNHandle.open(_key_manager);
		}
		catch (IOException ex) {
			throw new RuntimeException("Unable to open handle", ex);
		}

		return _ccn_handle;
	}

	/**
	 * Add application to the config store
	 * @param app
	 */
	public final synchronized void addApplication(Application app)
	{
		assert _root != null : "Root wasn't set";

		_applications.put(app.name, app);
	}

	/**
	 * Retrieve application from the config store
	 * @param name name of the application
	 * @return application instance or null if doesn't exist
	 */
	public synchronized Application getApplication(String name)
	{
		assert _root != null : "Root wasn't set";

		return _applications.get(name);
	}

	/**
	 * Retrieve the list of applications
	 * @return the list of apps as a set of strings
	 */
	public synchronized Set<String> getAppList()
	{
		assert _root != null : "Root wasn't set";

		return Collections.unmodifiableSet(_applications.keySet());
	}

	/**
	 * Return Key Manager
	 * @return Key Manager
	 */
	public PDCKeyManager getKeyManager()
	{
		return _key_manager;
	}

	public void setKeyManager(PDCKeyManager key_manager)
	{
		this._key_manager = key_manager;
	}

	/**
	 * load configuration binary data
	 * @param group group under the configuration is held
	 * @param name unique identifier within the group
	 * @return binary encoded data or null if no entry exists
	 * @throws IOException when there's an issue accessing the data
	 */
	public byte[] loadConfig(String group, String name)
			throws IOException
	{
		return _config_storage.loadEntry(group, name);
	}

	public void saveConfig(iState object)
			throws IOException
	{
		_config_storage.saveEntry(object.getStateGroupName(), object.getStateKey(),
				object.stateToByteArray());
	}

	public void saveConfig(String group, String name, byte[] byte_out)
			throws IOException
	{
		_config_storage.saveEntry(group, name, byte_out);
	}

	/**
	 * get instance of our configuration class
	 * @return instance of the class
	 */
	public static GlobalConfig getInstance()
	{
		if (_instance == null)
			_instance = new GlobalConfig();

		return _instance;
	}

	@Override
	public String toString()
	{
		List<String> data = new LinkedList<String>();
		StringBuilder sb = new StringBuilder();

		sb.append(this.getClass().getSimpleName());
		sb.append('[');
		data.add(_root == null ? null : _root.toURIString());
		data.add(_applications.toString());
		sb.append(StringUtil.join(data, "; "));
		sb.append(']');

		return sb.toString();
	}

// <editor-fold defaultstate="collapsed" desc="class serialization">
	public String getStateGroupName()
	{
		return "main";
	}

	public String getStateKey()
	{
		return "global";
	}

	public void storeState()
			throws IOException
	{
		saveConfig(this);
	}

	public void storeStateRecursive()
			throws IOException
	{
		assert _key_manager != null : "Storing state needs to be done after key manager is instantienated";
		_key_manager.storeStateRecursive();

		for (Application app : _applications.values())
			app.storeStateRecursive();

		storeState();
	}

	public static GlobalConfig loadState()
			throws IOException
	{
		byte[] byte_in;
		SystemState.GlobalConfig state;

		// Bootstrapping the process
		final iConfigStorage storage = GlobalConfig.setupConfigStorage();

		byte_in = storage.loadEntry("main", "global");
		if (byte_in == null)
			return null;

		state = SystemState.GlobalConfig.parseFrom(byte_in);
		return new GlobalConfig(state);
	}

	SystemState.GlobalConfig getObjectState()
	{
		SystemState.GlobalConfig.Builder builder = SystemState.GlobalConfig.
				newBuilder();

		if (_root == null)
			throw new RuntimeException("Root is not set");

		builder.setRoot(_root.toString());
		builder.addAllApplications(_applications.keySet());

		return builder.build();
	}

	public byte[] stateToByteArray()
	{
		return getObjectState().toByteArray();
	}
// </editor-fold>

	private static GlobalConfig _instance;

	private static Class<? extends Storage> _data_storage_class = null;

	private static Class<? extends iConfigStorage> _config_storage_class = null;

	public static final int FEAT_SHARING = 0x1;

	public static final int FEAT_MANAGE = 0x2;

	public static final int FEAT_FILTERING = 0x4;

	private static int _features = FEAT_SHARING;

	private transient iConfigStorage _config_storage;

	private transient ContentName _root = null;

	private transient final Map<String, Application> _applications = new HashMap<String, Application>();

	private transient PDCKeyManager _key_manager = null;

	private transient CCNHandle _ccn_handle = null;
}
