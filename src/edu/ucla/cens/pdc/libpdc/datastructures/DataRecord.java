/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.datastructures;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCParseException;
import edu.ucla.cens.pdc.libpdc.iBSONExportable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bson.BSONDecoder;
import org.bson.BSONEncoder;
import org.bson.BSONObject;
import org.bson.types.ObjectId;

/**
 *
 * @author Derek Kulinski
 */
@SuppressWarnings("deprecation")
public class DataRecord implements DBObject, iBSONExportable {
	private boolean _isPartialObject = false;

	ObjectId id;

	DBObject data = new BasicDBObject();

	public DataRecord()
	{
	}

	public DataRecord(String id)
	{
		this.id = new ObjectId(id);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public DataRecord(Map data)
	{
		Map m = new LinkedHashMap(data);

		putAllValue(m, "_id");

		data.putAll(m);
	}

	public ObjectId getId()
	{
		return id;
	}

	public void markAsPartialObject()
	{
		_isPartialObject = true;
	}

	public boolean isPartialObject()
	{
		return _isPartialObject;
	}

	public Object put(String string, Object o)
	{
		if (string.equals("_id")) {
			id = (ObjectId) o;

			return id;
		}

		return data.put(string, o);
	}

	@SuppressWarnings("rawtypes")
	final protected void putAllValue(Map m, final String key)
	{
		if (m.containsKey(key)) {
			put(key, m.get(key));
			m.remove(key);
		}
	}

	public void putAll(BSONObject bsono)
	{
		putAll(bsono.toMap());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void putAll(Map map)
	{
		Map m = new LinkedHashMap(map);

		putAllValue(m, "_id");

		data.putAll(m);
	}

	public Object get(String string)
	{
		if (string.equals("_id"))
			return id;

		return data.get(string);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public Map toMap()
	{
		Map map = new LinkedHashMap(data.toMap());

		if (id != null)
			map.put("_id", id);

		return map;
	}

	public Object removeField(String string)
	{
		if (string.equals("_id")) {
			ObjectId t = id;
			this.id = null;

			return t;
		}

		return data.removeField(string);
	}

	public boolean containsKey(String string)
	{
//		throw new UnsupportedOperationException("Apparently deprecated");
		return containsField(string);
	}

	public boolean containsField(String string)
	{
		if (string.equals("_id"))
			return id != null;

		return data.containsField(string);
	}

	public Set<String> keySet()
	{
		Set<String> set = new LinkedHashSet<String>(data.keySet());

		if (id != null)
			set.add("_id");

		return set;
	}

	/**
	 * Generate a BSON representation of the object
	 * @return BSON encoded as an array of bytes
	 */
	public byte[] toBSON()
	{
		BSONEncoder encoder = new BSONEncoder();
		return encoder.encode(this);
//		return JSON.serialize(this).getBytes();
	}

	/**
	 * Generate new DataRecord instance from BSON input
	 * Note that this call doesn't erase existing data stored
	 * in the object
	 * @param data array of bytes representing a BSON object
	 * @return new instance of DataRecord
	 */
	public DataRecord fromBSON(byte[] data)
					throws PDCParseException
	{
		BSONDecoder decoder = new BSONDecoder();
		BSONObject content;

		try {
			content = decoder.readObject(data);
		}
		catch (RuntimeException ex) {
			throw new PDCParseException("Unable to parse BSON string", ex);
		}

		putAll(content);

		return this;
	}

	public DataRecord fromJSON(String json_data)
					throws PDCParseException
	{
		DBObject result;

		try {
			result = (DBObject) JSON.parse(json_data);
		}
		catch (RuntimeException ex) {
			throw new PDCParseException(
							"Unable to convert JSON to DataRecord object", ex);
		}

		return new DataRecord(result.toMap());
	}

	@Override
	public String toString()
	{
		return JSON.serialize(this);
	}
}
