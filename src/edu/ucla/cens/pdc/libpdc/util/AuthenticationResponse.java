package edu.ucla.cens.pdc.libpdc.util;

import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.iBSONExportable;
import edu.ucla.cens.pdc.libpdc.transport.BasicBSONExportableImpl;
import edu.ucla.cens.pdc.libpdc.util.EncryptionHelper;
import edu.ucla.cens.pdc.libpdc.util.Log;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.bson.BSONDecoder;
import org.bson.BSONEncoder;
import org.bson.BSONObject;

/**
 * This class is the authentication response from the PDV that lets the web client
 * know whether or not the user has been authenticated
 *
 * TODO this class should probably reside in the PDV somewhere
 *
 * @author Alexander Bonomo
 */
public class AuthenticationResponse extends BasicBSONExportableImpl
{
    //keys for the map
    private static final String isAuthenticatedKey  = "isAuthenticated";
    private static final String symkeyKey           = "symkey";


    public AuthenticationResponse()
    {
        //empty constructor...
    }

    /**
     * Constructor
     * @param isAuthenticated - whether or not the user was authenticated
     */
    public AuthenticationResponse(boolean isAuthenticated)
    {
        this.classVars.put(AuthenticationResponse.isAuthenticatedKey, new Boolean(isAuthenticated));

        //if the user was authenticated, create a symmetric key, otherwise put a null value in the map
        if(isAuthenticated)
        {
            try
            {
                SecretKey key = EncryptionHelper.generateSymKey();
                this.classVars.put(AuthenticationResponse.symkeyKey, key);
            }
            catch (PDCEncryptionException ex)
            {
                Logger.getLogger(AuthenticationResponse.class.getName()).log(Level.SEVERE, null, ex);
                Log.error("Error generating symmetric key: " + ex.getLocalizedMessage());
            }
        }
        else
            this.classVars.put(symkeyKey, null);
    }

    /**
     * check the authentication response
     * @return
     */
    public boolean isAuthenticated()
    {
        return (Boolean)this.classVars.get(isAuthenticatedKey);
    }

    /**
     * get the symmetric key for the session
     * @return - the key.  Returns null if the user was not authenticated
     */
    public SecretKey getSymmetricKey()
    {
        return (SecretKey) this.classVars.get(AuthenticationResponse.symkeyKey);
    }
    
}
