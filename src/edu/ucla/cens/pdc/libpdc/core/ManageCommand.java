/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cens.pdc.libpdc.core;

import edu.ucla.cens.pdc.libpdc.Application;
import edu.ucla.cens.pdc.libpdc.Constants;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCDatabaseException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCEncryptionException;
import edu.ucla.cens.pdc.libpdc.exceptions.PDCTransmissionException;
import edu.ucla.cens.pdc.libpdc.iDataStream;
import edu.ucla.cens.pdc.libpdc.transport.CollectionTransport;
import edu.ucla.cens.pdc.libpdc.transport.CommunicationHelper;
import edu.ucla.cens.pdc.libpdc.util.EncryptionHelper;
import edu.ucla.cens.pdc.libpdc.util.Log;
import edu.ucla.cens.pdc.libpdc.util.MiscFuncs;
import edu.ucla.cens.pdc.libpdc.util.WCCredentials;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;

import org.bson.BSONDecoder;
import org.bson.BSONObject;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.config.UserConfiguration;
import org.ccnx.ccn.impl.CCNFlowControl.SaveType;
import org.ccnx.ccn.impl.support.DataUtils;
import org.ccnx.ccn.io.content.PublicKeyObject;
import org.ccnx.ccn.profiles.ccnd.CCNDCacheManager;
import org.ccnx.ccn.protocol.CCNTime;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentName.DotDotComponent;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.cache.CampaignRoleCache;
import org.ohmage.dao.AuthenticationDao;
import org.ohmage.domain.CampaignInformation;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.ServiceException;
import org.apache.log4j.Logger;
import org.ohmage.pdv.KeyValueStore;
import org.ohmage.pdv.OhmagePDVGlobals;
import org.ohmage.service.CampaignServices;
import org.ohmage.service.UserCampaignServices;
import org.ohmage.util.NDNUtils;
import org.ohmage.validator.CampaignValidators.OutputFormat;

import com.mongodb.BasicDBObject;

/**
 * Handles the manage command postfix
 *
 * Root: <PDV>/manage/...
 *
 * @author Derek Kulinski, Alexander Bonomo
 */
public class ManageCommand extends GenericCommand
{    
    //ManageCommand constants
    public static final String LIST_CMD    = "list";
    public static final String LOGIN_CMD   = "login";
    public static final String STR_APPS    = "apps";
    public static final String STR_DS      = "datastreams";
    public static final String STR_DR      = "datarecords";
    public static final String STR_isAuth  = "isAuthenticated";
    private static final String JSON_KEY_USER_ROLES = "user_roles";
	private static final long MILLIS_IN_A_SECOND = 1000;
	public static final String JSON_KEY_RESULT = "result";
	public static final String RESULT_SUCCESS = "success";
	public static final String RESULT_FAILURE = "failure";
	public static final String REQUEST_PROCESSING = "processing_request";
	public static final String STORE_KEY = "store_key";

    //private member variables
    private GlobalConfig    _globalConfig;
    private WCCredentials   _credentials;
    private PublicKey       _wcPubKey;
    private static final Logger LOGGER = Logger.getLogger(ManageCommand.class);
    // For short and long reads.
  	private Map<CampaignInformation, List<CampaignRoleCache.Role>> shortOrLongResult;


    public ManageCommand()
    {
        super(Constants.STR_MANAGE);
        _globalConfig   = GlobalConfig.getInstance();
        _credentials    = new WCCredentials();
    }
    

    /* Commands:
     * 
     * /list/apps
     * /list/datastreams/<app_name>
     * /list/datarecords/<app_name>/<datastream_name>
     *
     * /login/<dottedWC>/<user_hash>
     * /login/isAuthenticated/<user_hash>
     * 
     * /hashedIMEI/manage/campaign/read/outputformat/<encrypted hashedpasswd and username>
     */

    @Override
    boolean processCommand(ContentName postfix, Interest interest)
    {
        //make sure there is a command
        if (postfix.count() == 0)
        {
            Log.error("No subcommand!");
            return false;
        }

        //check if it's the list command
        if(postfix.stringComponent(0).equals(LIST_CMD))
            return handleListCmd(postfix, interest);

        //check for login command
        if(postfix.stringComponent(0).equals(LOGIN_CMD))
            return handleLoginCmd(postfix, interest);
        
        if(postfix.stringComponent(1).equals("default_publisherpublickeydigest")) {
        	return handleDefaultPublisherPublicKeyDigest(postfix, interest);
        }
        
        if(postfix.stringComponent(1).equals("default_public_key")) {
        	return handleDefaultPublicKey(postfix, interest);
        }
        if(postfix.stringComponent(1).equals("configuration_key")) {
        	LOGGER.info("Got the Configuration Key command");
        	return handleConfigurationKeyCmd(postfix, interest);
        }
        if(postfix.stringComponent(1).equals("campaign")) {
        	if (postfix.stringComponent(2).equals("read")) {
        		return handleReadCampaign(postfix, interest);
        	}
        }

        //more commands here...

        //if we get here, the command isn't supported
        return false;
    }
    
    private boolean handleConfigurationKeyCmd (ContentName postfix, 
    		Interest interest) {
    	final GlobalConfig config = GlobalConfig.getInstance();
    	final PDCKeyManager keymgr = config.getKeyManager();
    	final PublisherPublicKeyDigest digest = OhmagePDVGlobals.
    			getConfigurationDigest();
    	final PublicKey key  = keymgr.getPublicKey(digest);
    	final CCNTime version = keymgr.getKeyVersion(digest);
    	if(key == null) {
    		LOGGER.error("Public for given digest " + digest + " Is null");
    		return false;
    	} else {
    		LOGGER.info("Key : " + key);
    	}
    	
    	if(version == null) {
    		LOGGER.error("version key for given digest");
    		return false;
    	} else {
    		LOGGER.info("Version : " + version);
    	}
    	
    	final KeyLocator locator = new KeyLocator(interest.name(), digest);

    	try {
			PublicKeyObject pko = new PublicKeyObject(interest.name(),
					key, SaveType.RAW, 
					digest, locator, 
					config.getCCNHandle());
			pko.getFlowControl().disable();
			pko.save(version, interest);
			// LOGGER.info("Saved the key");
			pko.close();
			return true;
		} catch (IOException e) {
			LOGGER.error("Manage Command get Configuration public key failed");
			e.printStackTrace();
		}
    	return false;
    }
    
    private boolean handleDefaultPublicKey(ContentName postfix, 
    		Interest interest) {
    	GlobalConfig config = GlobalConfig.getInstance();
    	PDCKeyManager keymgr = config.getKeyManager();
    	PublisherPublicKeyDigest digest = keymgr.getDefaultKeyID();
    	final KeyLocator locator = new KeyLocator(interest.name(), digest);
    	try {
			PublicKeyObject pko = new PublicKeyObject(interest.name(),
					keymgr.getDefaultPublicKey(), SaveType.RAW, digest, locator, 
					config.getCCNHandle());
			pko.getFlowControl().disable();
			pko.save();
			pko.close();
			return true;
		} catch (IOException e) {
			LOGGER.error("Manage default public key command failed : " + e);
			e.printStackTrace();
		}
    	return false;
    }
    
    
    private boolean handleDefaultPublisherPublicKeyDigest(ContentName postfix, 
    		Interest interest) {
    	GlobalConfig config = GlobalConfig.getInstance();
    	PDCKeyManager keymgr = config.getKeyManager();
    	byte[] data = keymgr.getDefaultKeyID().toString().getBytes();
    	try {
			return CommunicationHelper.publishUnencryptedData(config.getCCNHandle(), 
					interest, keymgr.getDefaultKeyID(), data, 1);
		} catch (PDCTransmissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return false;
    }
    
    private boolean handleReadCampaign(ContentName postfix, Interest interest) {
    	String responseText = null;
    	String outputFormat = postfix.stringComponent(3);
    	String encryptedInfo = null;
    	BasicDBObject received_data;
    	final String hashedIMEI = new String(postfix.lastComponent());
    	GlobalConfig config = GlobalConfig.getInstance();
		try {
			final int lastIndex = postfix.containsWhere(hashedIMEI);
			LOGGER.info("HASHED IMEI index was : " + lastIndex);
			if(!outputFormat.equals("xml")) {
				encryptedInfo = new String(ContentName.
						componentParseURI(postfix.stringComponent(4)));
				for(int i = 5; i < lastIndex; ++i)
					encryptedInfo += "/" + new String(ContentName.
							componentParseURI(postfix.stringComponent(i)));
			} else {
				encryptedInfo = new String(ContentName.
						componentParseURI(postfix.stringComponent(5)));
				for(int i = 6; i < lastIndex; ++i)
					encryptedInfo += "/" + new String(ContentName.
							componentParseURI(postfix.stringComponent(i)));
			}
		} catch (URISyntaxException e2) {
			e2.printStackTrace();
		} catch (DotDotComponent e) {
			e.printStackTrace();
		}
    	if(encryptedInfo == null) {
    		LOGGER.error("Encrypted username and password " +
    				"not sent along with the interest");
    		return false;
    	}
    	
    	String unEncryptedInfo = null;
    
    	// encryptedInfo = new String(ContentName.componentParseURI(encryptedInfo));
		try {
			LOGGER.info("Encrypted Info received : " + 
		new String(encryptedInfo.getBytes(), "UTF-8"));
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}
    	try {
    		
    		LOGGER.info("Base64 decoded info");
    		// for(byte b : DataUtils.base64Decode(encryptedInfo.getBytes())) 
    		// 	LOGGER.info("Byte info : " + b);
		
			unEncryptedInfo = new String(NDNUtils.decryptConfigData(
					DataUtils.base64Decode(encryptedInfo.getBytes())));
			BSONDecoder decoder = new BSONDecoder();
			BSONObject obj = decoder.readObject(unEncryptedInfo.getBytes());
			received_data = new BasicDBObject(obj.toMap());
			LOGGER.info("USERNAME : " + received_data.getString("username"));
			LOGGER.info("PASSWORD : " + received_data.getString("password"));
			
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
    	String username = received_data.getString("username");
    	String hashedPassword = received_data.getString("password");
    	LOGGER.info("Received Username: " + username + "Received Password : " + hashedPassword);
    	String store_key = 
    			KeyValueStore.getRandomString();
    	LOGGER.info("Store Key " + store_key);
    	// Send a ACK as soon as you get the request so that you don't get multiple requests
    	// and as soon as the user is authenticated in order to avoid creating multiple
    	// keys
    	// bsonData.put("key", KeyValueStore.getRandomString() + hashedIMEI);
    	JSONObject jsonObject = new JSONObject();
		try {
			if(hashedPassword == null || username == null || !AuthenticationDao.authenticate(username, hashedPassword))
				jsonObject.put(JSON_KEY_RESULT, RESULT_FAILURE);
			else {
				jsonObject.put(JSON_KEY_RESULT, REQUEST_PROCESSING);
			}
			jsonObject.put(STORE_KEY, store_key);
			responseText = jsonObject.toString();
			//CommunicationHelper.publishUnencryptedData(config.getCCNHandle(), 
			//		interest, OhmagePDVGlobals.getConfigurationDigest(), 
			//		responseText.getBytes(), 1);
			if(jsonObject.get(JSON_KEY_RESULT).equals(RESULT_FAILURE))
				return true;
			//new CCNDCacheManager().clearCache(interest.name(), 
			//		config.getCCNHandle(),
			//		SystemConfiguration.NO_TIMEOUT);
			// Add result to the key store for later retrieval if response gets dropped
			KeyValueStore.getInstance().insertValue(store_key, 
					responseText);
		} catch (JSONException e) {
			e.printStackTrace();
		}/* catch (IOException e) {
			e.printStackTrace();
		} catch (PDCTransmissionException e) {
			e.printStackTrace();
		}*/
    	if (outputFormat.equals("short") || outputFormat.equals("long")) {
    		LOGGER.info("Generating the list of campaign IDs based on the parameters.");
			try {
					List<String> resultCampaignIds = 
							UserCampaignServices.getCampaignsForUser(null, username, 
							null, null, null, null, null, null, null);
					LOGGER.info("Gathering the information about the campaigns . : " + resultCampaignIds);
					shortOrLongResult = UserCampaignServices.
							getCampaignAndUserRolesForCampaigns(null, 
									username, resultCampaignIds, 
									OutputFormat.LONG.equals(outputFormat));
					
					// Create the JSONObject with which to respond.
					JSONObject result = new JSONObject();
							
					// Mark it as successful.
					result.put(JSON_KEY_RESULT, RESULT_SUCCESS);
						
					// Create and add the metadata.
					JSONObject metadata = new JSONObject();
					// Add the information for each of the campaigns into their own
					// JSONObject and add that to the result.
					JSONObject campaignInfo = new JSONObject();
					
					// Get all of the campaign IDs for the metadata.
					Set<String> resultCampaignIdsSet = new HashSet<String>();
					
					// This is done, so we don't have to repeatedly check the
					// same value.
					boolean longOutput = OutputFormat.LONG.equals(outputFormat);
					
					// For each of the campaigns, process its information and
					// place it in its respective object.
					for(CampaignInformation campaign : shortOrLongResult.keySet()) {
						// Get the campaign's ID for the metadata.
						resultCampaignIdsSet.add(campaign.getId());
					
						List<CampaignRoleCache.Role> roles = shortOrLongResult.get(campaign);
						boolean supervisorOrAuthor = 
							roles.contains(CampaignRoleCache.Role.SUPERVISOR) || 
							roles.contains(CampaignRoleCache.Role.AUTHOR);
						
						// Create the JSONObject response. This may return null
						// if there is an error building it.
						JSONObject resultJson = campaign.toJson(
								false,	// ID 
								longOutput,	// Classes
								longOutput,	// Any roles
								supervisorOrAuthor,	// Participants
								supervisorOrAuthor, // Analysts
								true,				// Authors
								supervisorOrAuthor,	// Supervisors
								longOutput);// XML
						
						if(resultJson != null) {
							resultJson.put(JSON_KEY_USER_ROLES, roles);
						}
						
						campaignInfo.accumulate(campaign.getId(), resultJson);
					}
					
					metadata.put("number_of_results", resultCampaignIdsSet.size());
					metadata.put("items", resultCampaignIdsSet);
					
					result.put("metadata", metadata);
					result.put("data", campaignInfo);
					responseText = result.toString();
					LOGGER.info("Response Text is : " + responseText);
				
				} catch (ServiceException e) {
					LOGGER.error(e);
				} catch (DataAccessException e) {
					LOGGER.error(e);
				}catch(JSONException e) {
					// If anything fails, return a failure message.
					LOGGER.error(e);
				}
    		} else if(outputFormat.equals("xml")) {
    			List<String> campaignIds = new ArrayList<String>();
    			String xmlResult = "";
    			String campaignNameResult = "";
    			try {
	    			campaignIds.add(new String(ContentName.componentParseURI(
	    					postfix.stringComponent(4))));
    			
					UserCampaignServices.campaignsExistAndUserBelongs(
							null, campaignIds, username);
					xmlResult = 
							CampaignServices.getCampaignXml(
									null, campaignIds.get(0));
					campaignNameResult = 
							CampaignServices.getCampaignName(
									null, campaignIds.get(0));
					JSONObject obj = new JSONObject();
	    			obj.put("xml_result", xmlResult);
	    			obj.put("campaign_name", campaignNameResult);
	    			responseText = obj.toString();
	    			LOGGER.info("Response Text for xml data is  : " +
	    					responseText);
				} catch (ServiceException e) {
					LOGGER.info("Service Exception" + e);
					e.printStackTrace();
				} catch (JSONException e) {
					LOGGER.info("JSON Exception" + e);
					e.printStackTrace();
				} catch (DotDotComponent e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	
    		}
    		
			ContentName defaultKeyName = null;
			ContentName defaultPublicKeyDigest = null;
			ContentObject co = null;
			try {
				defaultKeyName = 
						ContentName.fromURI("/ndn/ucla.edu/apps").
						append(username).
						append("androidclient").
						append(hashedIMEI).
						append("manage").
						append("default_public_key");
				defaultPublicKeyDigest =
						ContentName.fromURI("/ndn/ucla.edu/apps").
						append(username).append("androidclient").
						append(hashedIMEI).
						append("manage").
						append("default_publisherpublickeydigest");

				co = config.getCCNHandle().
						get(defaultPublicKeyDigest, 
								SystemConfiguration.LONG_TIMEOUT);
		
				PublisherPublicKeyDigest digest = 
						new PublisherPublicKeyDigest(
								new String(co.content()));
				
				KeyLocator locator = new KeyLocator(defaultKeyName,
						digest);
				
				byte[] data = NDNUtils.encryptConfigData(locator, 
						digest, responseText);
				
				jsonObject = new JSONObject();
				jsonObject.put("data", new String(data));
				jsonObject.put("digest", digest.toString());
				jsonObject.put("locator", defaultKeyName);
				jsonObject.put(JSON_KEY_RESULT, RESULT_SUCCESS);
				
				LOGGER.info("Value Stored in the key value store");
				KeyValueStore.getInstance().insertValue(store_key,
						jsonObject.toString());
				
				CommunicationHelper.publishUnencryptedData(config.getCCNHandle(), 
						interest, OhmagePDVGlobals.getConfigurationDigest(), data, 1);
				
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			} catch (MalformedContentNameStringException e) {
				// LOGGER.error(e);
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			} catch (PDCTransmissionException e) {
				e.printStackTrace();
			}
			// TODO(gdrane): Make a interest here asking the phone to pull
			// data giving error status
    	return false;
    }

    /**
     * Handle list command
     * @param postfix
     * @param interest
     * @return
     */
    private boolean handleListCmd(ContentName postfix, Interest interest)
    {
        //make sure there are enough components in the postfix
        if(postfix.count() < 2)
        {
            Log.error("No subcommand after /list/");
            return false;
        }

        //apps
        if(postfix.stringComponent(1).equals(STR_APPS))
        {
            Log.info("Received list apps command");
            
            //get the app list
            Set<String> apps = _globalConfig.getAppList();
            
            //send it across the network
            CollectionTransport<String> ctApps = new CollectionTransport<String>(apps);
            ContentObject co = ContentObject.buildContentObject(interest.name(), ctApps.toBSON());
            try {
                _globalConfig.getCCNHandle().put(co);
            } catch (IOException ex) {
                LOGGER.error("List apps command IOException");
            }
            //TODO use communication helper for encryption

            return true;
        }

        //make sure there are enough components in the postfix for datastreams
        if(postfix.count() < 3)
        {
            Log.error("Cannot perform list command. Not enough arguments.");
            return false;
        }

        //datastreams
        if(postfix.stringComponent(1).equals(STR_DS))
        {
            Log.info("Received list datastreams command for " + postfix.stringComponent(2));
            
            //get the list of data streams
            String appName          = postfix.stringComponent(2);
            Application app         = _globalConfig.getApplication(appName);
            Set<String> dataStreams = app.getDataStreamList(postfix.stringComponent(2));

            Log.info("Sending back list: " + dataStreams.toString());
            
            //send it across the network
            CollectionTransport<String> ctDS = new CollectionTransport<String>(dataStreams);
            ContentObject co = ContentObject.buildContentObject(interest.name(), ctDS.toBSON());
            try {
                _globalConfig.getCCNHandle().put(co);
            } catch (IOException ex) {
                LOGGER.error("IOException when sending back list of datastreams");
            }

            //TODO use communication helper for encryption

            return true;
        }

        //datarecords
        if(postfix.stringComponent(1).equals(STR_DR))
        {
            //make sure there are enough components in the postfix
            if(postfix.count() < 4)
            {
                Log.error("Cannot perform list command. Not enough arguments.");
                return false;
            }

            Log.info("Received list data record command.");
            
            //grab arguments needed to list the data records
            String appName  = postfix.stringComponent(2);
            String dsName   = postfix.stringComponent(3);
            Application app = this._globalConfig.getApplication(appName);
            iDataStream ds  = app.getDataStream(dsName);

            //get the data record list and publish it
            try
            {
                //the data records
                Collection<String> drList = ds.getStorage().getRangeIds();
                CollectionTransport<String> ctDRList = new CollectionTransport<String>(drList);
                ContentObject co = ContentObject.buildContentObject(interest.name(), ctDRList.toBSON());
                this._globalConfig.getCCNHandle().put(co);
            } 
            catch (IOException ex)
            {
                LOGGER.error("IOException when making a list of datarecords");
                Log.error(ex);
            }
            catch (PDCDatabaseException ex)
            {
                Log.error(ex);
            }

            return true;
        }

        //if we get here, the command isn't supported
        return false;
    }

    /**
     * Handle login commands
     * @param postfix
     * @param interest
     * @return
     *
     * /login/<dottedWC>/<user_hash>
     * /login/isAuthenticated/<user_hash>
     */
    private boolean handleLoginCmd(ContentName postfix, Interest interest)
    {
        //make sure there are enough components in the postfix
        if(postfix.count() < 2)
        {
            Log.error("No subcommand after /login/");
            return false;
        }

        if(postfix.stringComponent(1).equals(STR_isAuth))
        {
            try
            {
                /*if(this._wcPubKey == null)
                {
                    try {
                        Thread.sleep(SystemConfiguration.MEDIUM_TIMEOUT);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ManageCommand.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if(this._wcPubKey == null)
                {
                    Log.error("WC public key not yet known.");
                    return false;
                }*/
                //TODO use CommunicationHelper
                //AuthenticationResponse ar = new AuthenticationResponse(true);
                SecretKey symmKey = this._globalConfig.getKeyManager().getWCSymmKey();
                if(symmKey == null)
                {
                    symmKey = EncryptionHelper.generateSymKey();
                    this._globalConfig.getKeyManager().addWCSymmKey(symmKey);
                }

                MiscFuncs.printKeyToLog(symmKey, "Symmetric key: ");
                //byte[] content = EncryptionHelper.wrapKey(_wcPubKey, symmKey);
                ContentObject co = ContentObject.buildContentObject(interest.name(), symmKey.getEncoded());
                this._globalConfig.getCCNHandle().put(co);
                

                return true;
            } catch (IOException ex) {
              LOGGER.error(ex.toString());
            } catch (PDCEncryptionException ex) {
               LOGGER.error(ex);
            }

            return false;
        }

        //otherwise the next string component will have the dotted WC namespace
        try 
        {
            //send ack
            String ack = "ACK";
            ContentObject co = ContentObject.buildContentObject(interest.name(), ack.getBytes());
            this._globalConfig.getCCNHandle().put(co);

            //send interest
            String wcNamespace = "ccnx:" + postfix.stringComponent(1).replace('.', '/');
            Log.info("Sending interest for credentials: " + wcNamespace + "/login");
            ContentObject coWCPubKey = this._globalConfig.getCCNHandle().get(
                    ContentName.fromURI(wcNamespace + "/login"), SystemConfiguration.MEDIUM_TIMEOUT);

            if(coWCPubKey == null)
            {
                Log.warning("No credentials received from Web Client.");
                return false;
            }

            byte[] wcPubKeyBytes = EncryptionHelper.decryptAsymData(this._globalConfig.getKeyManager().getKeysForWC().getPrivate(), coWCPubKey.content());
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(wcPubKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(UserConfiguration.defaultKeyAlgorithm());
            this._wcPubKey = keyFactory.generatePublic(pubKeySpec);
            
            //_credentials.fromBSON(coWCPubKey.content());
            Log.info("WC PublicKey Received: " + _wcPubKey);
        } 
        catch (InvalidKeySpecException ex)
        {
            // Logger.getLogger(ManageCommand.class.getName()).log(Level.SEVERE, null, ex);
            // Log.error(ex.getLocalizedMessage());
        	LOGGER.error(ex);
        }
        catch (NoSuchAlgorithmException ex)
        {
           // Logger.getLogger(ManageCommand.class.getName()).log(Level.SEVERE, null, ex);
           // Log.error(ex.getLocalizedMessage());
           LOGGER.error(ex);
        }        
        catch (PDCEncryptionException ex)
        {
            // Logger.getLogger(ManageCommand.class.getName()).log(Level.SEVERE, null, ex);
            // Log.error(ex.getLocalizedMessage());
        	LOGGER.error(ex);
        }
        catch (MalformedContentNameStringException ex)
        {
            // Logger.getLogger(ManageCommand.class.getName()).log(Level.SEVERE, null, ex);
            // Log.error(ex.getLocalizedMessage());
        	LOGGER.error(ex);
        }
        catch (IOException ex)
        {
            // Logger.getLogger(ManageCommand.class.getName()).log(Level.SEVERE, null, ex);
            // Log.error(ex.getLocalizedMessage());
        	LOGGER.error(ex);
        }

        return true;
    }
    
    
}
