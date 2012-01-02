package edu.ucla.cens.pdc.libpdc.util;

import edu.ucla.cens.pdc.libpdc.transport.BasicBSONExportableImpl;
import java.security.PublicKey;

/**
 * This class holds the user's credentials as well as the WC's public key and is
 * translated to BSON before being sent via NDN
 * @author Alexander Bonomo
 */
public class WCCredentials extends BasicBSONExportableImpl
{
    //the keys for the map
    private static final String usernameKey = "username";
    private static final String passwordKey = "password";
    private static final String pubkeyKey   = "pubkey";

    //empty constructor
    public WCCredentials()
    {
        
    }

    public WCCredentials(String username, String password, PublicKey pubKey)
    {
        this.classVars.put(WCCredentials.usernameKey, username);
        this.classVars.put(WCCredentials.passwordKey, password);
        this.classVars.put(WCCredentials.pubkeyKey, pubKey);
    }

    public String getUsername()
    {
        return (String) this.classVars.get(WCCredentials.usernameKey);
    }

    /**
     * This method returns the password as a string.
     *
     * TODO: will want to eventually only allow a (MD5) hash of the credentials
     * (username + password)  to be accessed
     * @return
     */
    public String getPassword()
    {
        return (String) this.classVars.get(WCCredentials.passwordKey);
    }

    public PublicKey getPublicKey()
    {
        return (PublicKey) this.classVars.get(WCCredentials.pubkeyKey);
    }
}
