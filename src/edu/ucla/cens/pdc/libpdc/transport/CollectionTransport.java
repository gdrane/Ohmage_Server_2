package edu.ucla.cens.pdc.libpdc.transport;

import edu.ucla.cens.pdc.libpdc.transport.BasicBSONExportableImpl;
import java.util.Collection;

/**
 * This class is a wrapper to transport collections via NDN by converting them to
 * BSON.
 *
 * TODO: this class should probably reside in the PDC source
 * @author Alexander Bonomo
 */
public class CollectionTransport<T> extends BasicBSONExportableImpl
{
    //keys for the class variables
    private static final String collectionKey = "collection";

    public CollectionTransport()
    {
        //empty constructor
    }

    public CollectionTransport(Collection<T> collection)
    {
        super();
        this.classVars.put(collectionKey, collection);
    }

    public Collection<T> toCollection()
    {
        return (Collection<T>) this.classVars.get(collectionKey);
    }
}
