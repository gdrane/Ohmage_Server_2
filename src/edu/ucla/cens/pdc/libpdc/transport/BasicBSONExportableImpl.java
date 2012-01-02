package edu.ucla.cens.pdc.libpdc.transport;

import edu.ucla.cens.pdc.libpdc.iBSONExportable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bson.BSONDecoder;
import org.bson.BSONEncoder;
import org.bson.BSONObject;

/**
 * This class provides a basic implementation of the iBSONExportable interface
 *
 * TODO: this class should probably reside in the PDC source
 * @author Alexander Bonomo
 */
public class BasicBSONExportableImpl implements iBSONExportable,BSONObject
{
    //map of the class variables
    protected Map<String, Object> classVars = new LinkedHashMap<String, Object>();

    public byte[] toBSON()
    {
        try
        {
            BSONEncoder encoder = new BSONEncoder();
            return encoder.encode(this);
        }
        catch(RuntimeException e)
        {
            return null;
        }
    }

    public BasicBSONExportableImpl fromBSON(byte[] data)
    {
        try
        {
            BSONDecoder decoder = new BSONDecoder();
            this.putAll(decoder.readObject(data));

            return this;
        }
        catch(RuntimeException e)
        {
            return null;
        }
    }

    public Object put(String string, Object o)
    {
        return this.classVars.put(string, o);
    }

    public void putAll(BSONObject bsono)
    {
        this.putAll(bsono.toMap());
    }

    public void putAll(Map map)
    {
        this.classVars.putAll(map);
    }

    public Object get(String string)
    {
        if(this.containsKey(string))
            return this.classVars.get(string);
        else
            return null;
    }

    public Map toMap()
    {
        return this.classVars;
    }

    public Object removeField(String string)
    {
        return this.classVars.remove(string);
    }

    public boolean containsKey(String string)
    {
        return this.classVars.containsKey(string);
    }

    public boolean containsField(String string)
    {
        return this.classVars.containsKey(string);
    }

    public Set<String> keySet()
    {
        return this.classVars.keySet();
    }
}
