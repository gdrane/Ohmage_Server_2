/*******************************************************************************
 * Copyright 2011 The Regents of the University of California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohmage.cache;

import javax.sql.DataSource;

/**
 * Singleton cache for the indices and String values for survey response
 * privacy states.
 * 
 * @author John Jenkins
 */
public final class SurveyResponsePrivacyStateCache extends StringAndIdCache {
	// The column IDs for the query.
	private static final String ID_COLUMN = "id";
	private static final String STATE_COLUMN = "privacy_state";
	
	// The SQL that will retrieve the desired values.
	private static final String SQL_GET_SURVEY_RESPONSE_PRIVACY_STATES_AND_IDS = 
		"SELECT " + ID_COLUMN + ", " + STATE_COLUMN + " " +
		"FROM survey_response_privacy_state";
	
	// When we are requesting a cache in the Spring files, we use this
	// to reference which key we want.
	public static final String CACHE_KEY = "surveyResponsePrivacyStateCache";
	
	/**
	 * Known survey response privacy states.
	 * 
	 * @author John Jenkins
	 */
	public static enum PrivacyState {
		PRIVATE,
		SHARED,
		INVISIBLE;
		
		/**
		 * Converts a String value into a PrivacyState or throws an exception
		 * if there is no comparable privacy state.
		 * 
		 * @param privacyState The privacy state to be converted into a 
		 * 					   PrivacyState enum.
		 * 
		 * @return A comparable PrivacyState enum.
		 * 
		 * @throws IllegalArgumentException Thrown if there is no comparable
		 * 									PrivacyState enum.
		 */
		public static PrivacyState getValue(String privacyState) {
			return valueOf(privacyState.toUpperCase());
		}
		
		/**
		 * Converts the privacy state to a nice, human-readable format.
		 */
		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}
	
	// A reference to the only instance of this class for the Singleton
	// pattern.
	private static SurveyResponsePrivacyStateCache instance;
	
	/**
	 * Default constructor set private to make this a Singleton.
	 */
	private SurveyResponsePrivacyStateCache(DataSource dataSource, long updateFrequency) {
		super(dataSource, updateFrequency, SQL_GET_SURVEY_RESPONSE_PRIVACY_STATES_AND_IDS, ID_COLUMN, STATE_COLUMN);
		
		instance = this;
	}
	
	/**
	 * Returns the instance of this class. This should be used to get at all
	 * the cache's methods.
	 * 
	 * @return The only instance of this class.
	 */
	public static SurveyResponsePrivacyStateCache instance() {
		return instance;
	}
	
	/**
	 * Returns a human-readable name for this cache.
	 */
	@Override
	public String getName() {
		return CACHE_KEY;
	}
}
