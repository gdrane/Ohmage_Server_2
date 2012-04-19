package org.ohmage.pdv;

import java.util.HashMap;

import org.apache.commons.lang3.RandomStringUtils;


public class KeyValueStore {
	
	private KeyValueStore() {
		
	}
	
	public boolean insertValue(String key, String value) {
		//if(!_store.containsKey(key)) {
			_store.put(key, value);
			//return true;
		// }
		return true;
	}
	
	public boolean containsKey(String key) {
		return _store.containsKey(key);
	}
	
	public boolean removeValue(String key) {
		if(!_store.containsKey(key)){
			return false;
		} else {
			_store.remove(key);
		}
		return true;
	}
	
	public String getValue(String key) {
		if(!_store.containsKey(key))
			return null;
		return _store.get(key);
	}
	
	public static KeyValueStore getInstance() {
		if(_singleTon == null)
			_singleTon = new KeyValueStore();
		return _singleTon;
	}
	
	public static String getRandomString() {
		return RandomStringUtils.randomAlphabetic(64);	
	}
	
	private HashMap<String,String> _store = new HashMap<String, String>();
	
	private static KeyValueStore _singleTon = null;
	
}
