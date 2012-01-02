package org.ohmage.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.cache.PreferenceCache;
import org.ohmage.cache.SurveyResponsePrivacyStateCache;
import org.ohmage.domain.configuration.Configuration;
import org.ohmage.domain.upload.PromptResponse;
import org.ohmage.domain.upload.SurveyResponse;
import org.ohmage.exception.CacheMissException;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.ServiceException;
import org.ohmage.pdv.storage.MYSQLDataStorage;
import org.ohmage.request.JsonInputKeys;
import org.ohmage.service.CampaignServices;
import org.ohmage.util.JsonUtils;
import org.ohmage.util.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import edu.ucla.cens.pdc.libpdc.datastructures.DataRecord;

public class PDVDataDaos extends AbstractUploadDao {

	protected PDVDataDaos(DataSource dataSource) {
		super(dataSource);
		// TODO Auto-generated constructor stub
		instance = this;
	}
	public static boolean insertRecord(DataRecord record) throws DataAccessException
	{
		if(record != null)
		{
			Map requestParams =
					record.toMap();
			final String campaignUrn = (String)requestParams.get("campaign_urn");
			int campaignCreationTimestamp = Integer.parseInt((String)requestParams.get("campaign_creation_timestamp"));
			final String username = (String)requestParams.get("user");
			String hashedPassword = (String)requestParams.get("password");
			final String client = (String)requestParams.get("client");
			String surveyJSONObject = StringUtils.urlDecode((String)requestParams.get("surveys"));
			JSONObject temp = null;
			try {
				temp = new JSONObject(surveyJSONObject);
			} catch (JSONException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			final JSONObject survey = temp;
			List<Integer> duplicateIndexList = new ArrayList<Integer>();
			Configuration configuration = null;
			try {
				configuration = CampaignServices.findCampaignConfiguration(null, campaignUrn);
			} catch (ServiceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// The following variables are used in logging messages when errors occur
			SurveyResponse currentSurveyResponse = null;
			PromptResponse currentPromptResponse = null;
			String currentSql = null;
			// Wrap all of the inserts in a transaction 
			DefaultTransactionDefinition def = new DefaultTransactionDefinition();
			def.setName("survey upload");
			DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(instance.getDataSource());
			TransactionStatus status = transactionManager.getTransaction(def); // begin transaction
			
			// Use a savepoint to handle nested rollbacks if duplicates are found
			Object savepoint = status.createSavepoint();
			try { // handle TransactionExceptions
					
					 try { // handle DataAccessExceptions
						
						currentSql = SQL_INSERT_SURVEY_RESPONSE;
				
						KeyHolder idKeyHolder = new GeneratedKeyHolder();
						
						// First, insert the survey
						
						instance.getJdbcTemplate().update(
							new PreparedStatementCreator() {
								public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
									PreparedStatement ps 
										= connection.prepareStatement(SQL_INSERT_SURVEY_RESPONSE, Statement.RETURN_GENERATED_KEYS);
									ps.setString(1, username);
									ps.setString(2, campaignUrn);
									ps.setTimestamp(3, Timestamp.valueOf( JsonUtils.getStringFromJsonObject(survey, JsonInputKeys.METADATA_DATE)));
									ps.setLong(4, JsonUtils.getLongFromJsonObject(survey, JsonInputKeys.METADATA_TIME));
									ps.setString(5, JsonUtils.getStringFromJsonObject(survey, JsonInputKeys.METADATA_TIMEZONE));
									ps.setString(6, JsonUtils.getStringFromJsonObject(survey, JsonInputKeys.METADATA_LOCATION_STATUS));
									ps.setString(7, JsonUtils.getJsonObjectFromJsonObject(survey, JsonInputKeys.METADATA_LOCATION).toString());
									ps.setString(8, JsonUtils.getStringFromJsonObject(survey, JsonInputKeys.SURVEY_ID));
									ps.setString(9,survey.toString());
									ps.setString(10, client);
									ps.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
									ps.setString(12,  JsonUtils.getJsonObjectFromJsonObject(survey, JsonInputKeys.SURVEY_LAUNCH_CONTEXT).toString());
									try {
										ps.setInt(13, SurveyResponsePrivacyStateCache.instance().lookup(PreferenceCache.instance().lookup(PreferenceCache.KEY_DEFAULT_SURVEY_RESPONSE_SHARING_STATE)));
									} catch (CacheMissException e) {
										LOGGER.error("Error reading from the cache.", e);
										throw new SQLException(e);
									}
									return ps;
								}
							},
							idKeyHolder
						);
						
						savepoint = status.createSavepoint();
						
						final Number surveyResponseId = idKeyHolder.getKey(); // the primary key on the survey_response table for the 
						                                                      // just-inserted survey
						JSONArray promptResponses = JsonUtils.getJsonArrayFromJsonObject(survey, JsonInputKeys.SURVEY_RESPONSES);
						List<PromptResponse> convertedPromptResponses = new ArrayList<PromptResponse>();
						int arrayLength = promptResponses.length();	
						
						for(int i = 0; i < arrayLength; i++) {
							JSONObject responseObject = JsonUtils.getJsonObjectFromJsonArray(promptResponses, i);
							
							// Check to see if its a repeatable set
							String repeatableSetId = JsonUtils.getStringFromJsonObject(responseObject, JsonInputKeys.SURVEY_REPEATABLE_SET_ID);
							
							if(repeatableSetId != null) {
								
								// ok, grab the inner responses - repeatable sets are anonymous
								// objects in an array of arrays
								JSONArray outerArray = JsonUtils.getJsonArrayFromJsonObject(responseObject, JsonInputKeys.SURVEY_RESPONSES);
								
								// Now each element in the outer array is also an array
								for(int j = 0; j < outerArray.length(); j++) {
									JSONArray repeatableSetResponses =  JsonUtils.getJsonArrayFromJsonArray(outerArray, j);
									int numberOfRepeatableSetResponses = repeatableSetResponses.length();
									
									for(int k = 0; k < numberOfRepeatableSetResponses; k++) { 
										
										JSONObject rsPromptResponse = JsonUtils.getJsonObjectFromJsonArray(repeatableSetResponses, k);
										String promptId = JsonUtils.getStringFromJsonObject(rsPromptResponse, JsonInputKeys.SURVEY_PROMPT_ID);
										String repeatableSetIteration = String.valueOf(j);
										String promptType = configuration.getPromptType(JsonUtils.getStringFromJsonObject(survey, JsonInputKeys.SURVEY_ID), repeatableSetId, promptId);
										String value = handleDataPacketValue(rsPromptResponse, promptType);
										
										convertedPromptResponses.add(new PromptResponse(promptId, repeatableSetId, repeatableSetIteration, value, promptType));
									}
								}
								
							} else {
								
								String promptId = JsonUtils.getStringFromJsonObject(responseObject, "prompt_id");
								String promptType = configuration.getPromptType(JsonUtils.getStringFromJsonObject(survey, JsonInputKeys.SURVEY_ID), promptId); 
								String value = handleDataPacketValue(responseObject, promptType);
								
								convertedPromptResponses.add(new PromptResponse(promptId, null, null, value, promptType));
							}
						}
						
						currentSql = SQL_INSERT_PROMPT_RESPONSE;
						
					
						
						for(int i = 0; i < convertedPromptResponses.size(); i++) {
							final PromptResponse promptUpload = convertedPromptResponses.get(i);	
							currentPromptResponse = promptUpload;
							
							instance.getJdbcTemplate().update(
								new PreparedStatementCreator() {
									public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
										PreparedStatement ps 
											= connection.prepareStatement(SQL_INSERT_PROMPT_RESPONSE);
										ps.setInt(1, surveyResponseId.intValue());
										ps.setString(2, promptUpload.getRepeatableSetId());
										if(null != promptUpload.getRepeatableSetIteration()) {
											ps.setInt(3, Integer.parseInt(promptUpload.getRepeatableSetIteration()));
										} else {
											ps.setNull(3, java.sql.Types.NULL);
										}
										ps.setString(4, promptUpload.getType());
										ps.setString(5, promptUpload.getPromptId());
										ps.setString(6, promptUpload.getValue());
										
										return ps;
									}
								}
							);
						}
					 } catch (DataIntegrityViolationException dive) { // a unique index exists only on the survey_response table
							
							if(instance.isDuplicate(dive)) {
								 
								LOGGER.debug("Found a duplicate survey upload message for user " + username);
								
								//duplicateIndexList.add(Integer.parJsonUtils.getStringFromJsonObject(survey, JsonInputKeys.SURVEY_ID));
								status.rollbackToSavepoint(savepoint);
								
							} else {
							
								// Some other integrity violation occurred - bad!! All 
								// of the data to be inserted must be validated before 
								// this DAO runs so there is either missing validation 
								// or somehow an auto_incremented key has been duplicated.
								
								LOGGER.error("Caught DataAccessException", dive);
								logErrorDetails(currentSurveyResponse, currentPromptResponse, currentSql, username, campaignUrn);
								/*if(! regularImageList.isEmpty()) {
									for(File f : regularImageList) {
										f.delete();
									}
								}
								if(! scaledImageList.isEmpty()) {
									for(File f : scaledImageList) {
										f.delete();
									}
								}*/
								try {
									rollback(transactionManager, status);
								} catch (DataAccessException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								throw new DataAccessException(dive);
							}
								
						} catch (org.springframework.dao.DataAccessException dae) { 
							
							// Some other database problem happened that prevented
		                    // the SQL from completing normally.
							
							LOGGER.error("caught DataAccessException", dae);
							logErrorDetails(currentSurveyResponse, currentPromptResponse, currentSql, username, campaignUrn);
							/*if(! regularImageList.isEmpty()) {
								for(File f : regularImageList) {
									f.delete();
								}
							}
							if(! scaledImageList.isEmpty()) {
								for(File f : scaledImageList) {
									f.delete();
								}
							}*/
							try {
								rollback(transactionManager, status);
							} catch (DataAccessException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							throw new DataAccessException(dae);
						} 
						
						// Finally, commit the transaction
						transactionManager.commit(status);
						LOGGER.info("Completed survey message persistence");
					} 
					
					catch (TransactionException te) { 
						
						LOGGER.error("failed to commit survey upload transaction, attempting to rollback", te);
						rollback(transactionManager, status);
						/*if(! regularImageList.isEmpty()) {
							for(File f : regularImageList) {
								f.delete();
							}
						}
						if(! scaledImageList.isEmpty()) {
							for(File f : scaledImageList) {
								f.delete();
							}
						}*/
						logErrorDetails(currentSurveyResponse, currentPromptResponse, currentSql, username, campaignUrn);
						throw new DataAccessException(te);
					}
			
			return true;	
		}
		return false;
	}
	
	/**
	 * Handles special formatting cases for custom types and JSON arrays. For
	 * custom types, the prompt id is removed from the JSONObject 
	 * (custom_choices) because the prompt id is stored in its own db column.
	 * For multi_choice respsonses, the json.org JSON lib escapes JSON arrays
	 * using quotes when asked for arrays to be returned as Strings, so the 
	 * quotes have to be stripped in order for the value to be a 'clean'
	 * JSON array when it is eventually stored in the db.
	 * 
	 * @param response  The response containing a value to convert.
	 * @param promptType  The prompt type that indicates whether any special
	 * handling needs to occur.
	 * @return  The (possibly cleaned up) response value for any prompt type as
	 * a String.
	 */
	private static String handleDataPacketValue(JSONObject response, String promptType) {
		JSONArray customChoicesArray = JsonUtils.getJsonArrayFromJsonObject(response, JsonInputKeys.PROMPT_CUSTOM_CHOICES);
		
		if(null != customChoicesArray) {
			
			// Remove the prompt id because it is stored in its own column in 
			// the db.
			response.remove(JsonInputKeys.SURVEY_PROMPT_ID);
			return response.toString(); 
			
		} else {
			
			return stripQuotes(JsonUtils.getStringFromJsonObject(response, JsonInputKeys.PROMPT_VALUE), promptType);
		}
	}
	
	/**
	 * Attempts to rollback a transaction. 
	 */
	private static void rollback(PlatformTransactionManager transactionManager, TransactionStatus transactionStatus) 
		throws DataAccessException {
		
		try {
			
			LOGGER.error("rolling back a failed survey upload transaction");
			transactionManager.rollback(transactionStatus);
			
		} catch (TransactionException te) {
			
			LOGGER.error("failed to rollback survey upload transaction", te);
			throw new DataAccessException(te);
		}
	}
	
	private static void logErrorDetails(SurveyResponse surveyResponse, PromptResponse promptResponse, String sql, String username,
			String campaignUrn) {
	
		StringBuilder error = new StringBuilder();
		error.append("\nAn error occurred when attempting to insert survey responses for user ");
		error.append(username);
		error.append(" in campaign ");
		error.append(campaignUrn);
		error.append(".\n");
		error.append("The SQL statement at hand was ");
		error.append(sql);
		error.append("\n The survey response at hand was ");
		error.append(surveyResponse);
		error.append("\n The prompt response at hand was ");
		error.append(promptResponse);
		
		LOGGER.error(error.toString());
	}
	
	/**
	 * Strip quotes from String-ified JSONArrays. The JSON library will auto-quote arrays if you ask for them as strings.
	 * 
	 * TODO Move to StringUtils?
	 */
	private static String stripQuotes(String string, String promptType) {
		if("multi_choice".equals(promptType)) {
			return string.replace("\"", "");
		}
		return string;
	}
	
	private static PDVDataDaos instance;
	private static Logger LOGGER = Logger.getLogger(MYSQLDataStorage.class);
	private static final String SQL_INSERT_SURVEY_RESPONSE =
			"INSERT into survey_response " +
			"SET user_id = (SELECT id from user where username = ?), " +
			"campaign_id = (SELECT id from campaign where urn = ?), " +
			"msg_timestamp = ?, " +
			"epoch_millis = ?, " +
			"phone_timezone = ?, " +
			"location_status = ?, " +
			"location = ?, " +
			"survey_id = ?, " +
			"survey = ?, " +
			"client = ?, " +
			"upload_timestamp = ?, " +
			"launch_context = ?, " +
			"privacy_state_id = ?";
	
	private static final String SQL_INSERT_PROMPT_RESPONSE =
			"INSERT into prompt_response " +
	        "(survey_response_id, repeatable_set_id, repeatable_set_iteration," +
	        "prompt_type, prompt_id, response) " +
	        "VALUES (?,?,?,?,?,?)";
}
