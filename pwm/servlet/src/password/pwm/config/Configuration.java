/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiChallengeSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogLevel;
import password.pwm.util.PwmLogger;
import password.pwm.util.operations.PasswordUtility;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.util.*;

/**
 * Simple data object, contains configuration information defined in the servlet .properties
 * configuration file.
 *
 * @author Jason D. Rivard
 */
public class Configuration implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private final static PwmLogger LOGGER = PwmLogger.getLogger(Configuration.class);

    private final StoredConfiguration storedConfiguration;

    private Map<Locale,PwmPasswordPolicy> cachedPasswordPolicy = new HashMap<Locale,PwmPasswordPolicy>();
    private Map<Locale,PwmPasswordPolicy> newUserPasswordPolicy = new HashMap<Locale,PwmPasswordPolicy>();
    private List<Locale> knownLocales = null;
    private long newUserPasswordPolicyCacheTime = System.currentTimeMillis();

// --------------------------- CONSTRUCTORS ---------------------------

    public Configuration(final StoredConfiguration storedConfiguration) {
        this.storedConfiguration = storedConfiguration;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public String toString() {
        final StringBuilder outputText = new StringBuilder();
        outputText.append("  ");
        outputText.append(storedConfiguration.toString(true));
        return outputText.toString().replaceAll("\n","\n  ");
    }

// -------------------------- OTHER METHODS --------------------------

    public Set<String> getAllUsedLdapAttributes() {
        final Set<String> returnSet = new HashSet<String>();
        for (final PwmSetting formSetting : new PwmSetting[] { PwmSetting.ACTIVATE_USER_FORM, PwmSetting.NEWUSER_FORM, PwmSetting.UPDATE_PROFILE_FORM}) {
            for (final FormConfiguration formConfiguration : readSettingAsForm(formSetting, PwmConstants.DEFAULT_LOCALE)) {
                returnSet.add(formConfiguration.getAttributeName());
            }
        }
        returnSet.add(this.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.LDAP_GUID_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.EVENTS_LDAP_ATTRIBUTE));
        returnSet.addAll(this.getGlobalPasswordPolicy(PwmConstants.DEFAULT_LOCALE).getRuleHelper().getDisallowedAttributes());
        returnSet.add(this.readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.GUEST_ADMIN_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE));
        returnSet.remove("");
        return returnSet;
    }

    public List<FormConfiguration> readSettingAsForm(final PwmSetting setting, final Locale locale) {
        final List<String> input = readSettingAsLocalizedStringArray(setting, locale);

        if (input == null) {
            return Collections.emptyList();
        }

        final List<FormConfiguration> returnList = new LinkedList<FormConfiguration>();
        for (final String loopString : input) {
            if (loopString != null && loopString.length() > 0) {
                final FormConfiguration formConfig;
                try {
                    formConfig = FormConfiguration.parseConfigString(loopString);
                    returnList.add(formConfig);
                } catch (PwmOperationalException e) {
                    LOGGER.error("error parsing form configuration: " + e.getMessage());
                }
            }
        }
        return returnList;
    }

    public List<String> readSettingAsLocalizedStringArray(final PwmSetting setting, final Locale locale) {
        final Map<String, List<String>> storedValues = storedConfiguration.readLocalizedStringArraySetting(setting);
        final Map<Locale, List<String>> availableLocaleMap = new HashMap<Locale, List<String>>();
        for (final String localeStr : storedValues.keySet()) {
            availableLocaleMap.put(Helper.parseLocaleString(localeStr), storedValues.get(localeStr));
        }
        final Locale matchedLocale = Helper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }

    public String readSettingAsString(final PwmSetting setting) {
        return storedConfiguration.readSetting(setting);
    }

    public Map<Locale,String> readLocalizedBundle(final String className, final String keyName) {
        final Map<String,String> storedValue = storedConfiguration.readLocaleBundleMap(className,keyName);
        if (storedValue == null || storedValue.isEmpty()) {
            return null;
        }

        final Map<Locale,String> localizedMap = new LinkedHashMap<Locale, String>();
        for (final String localeKey : storedValue.keySet()) {
            localizedMap.put(new Locale(localeKey),storedValue.get(localeKey));
        }

        return localizedMap;
    }

    public PwmLogLevel getEventLogLocalLevel() {
        final String value = readSettingAsString(PwmSetting.EVENTS_PWMDB_LOG_LEVEL);
        for (final PwmLogLevel logLevel : PwmLogLevel.values()) {
            if (logLevel.toString().equalsIgnoreCase(value)) {
                return logLevel;
            }
        }

        return PwmLogLevel.TRACE;
    }

    public ChallengeSet getGlobalChallengeSet(final Locale locale) {
        final List<String> requiredQuestions = readSettingAsLocalizedStringArray(PwmSetting.CHALLENGE_REQUIRED_CHALLENGES, locale);
        final List<String> randomQuestions = readSettingAsLocalizedStringArray(PwmSetting.CHALLENGE_RANDOM_CHALLENGES, locale);

        final List<Challenge> challenges = new ArrayList<Challenge>();
        for (final String question : requiredQuestions) {
            final Challenge challenge = parseConfigStringToChallenge(question, true);
            if (challenge != null) {
                challenges.add(challenge);
            }
        }

        for (final String question : randomQuestions) {
            final Challenge challenge = parseConfigStringToChallenge(question, false);
            if (challenge != null) {
                challenges.add(challenge);
            }
        }

        int minimumRands = (int) readSettingAsLong(PwmSetting.CHALLENGE_MIN_RANDOM_REQUIRED);
        if (minimumRands > randomQuestions.size()) {
            minimumRands = randomQuestions.size();
        }

        try {
            return new ChaiChallengeSet(challenges, minimumRands, locale, "pwm-defined " + PwmConstants.SERVLET_VERSION);
        } catch (ChaiValidationException e) {
            LOGGER.warn("invalid challenge set configuration: " + e.getMessage());
        }
        return null;
    }

    private Challenge parseConfigStringToChallenge(String inputString, final boolean required) {
        if (inputString == null || inputString.length() < 1) {
            return null;
        }

        int minLength = 2;
        int maxLength = 255;

        final String[] s1 = inputString.split("::");
        if (s1.length > 0) {
            inputString = s1[0];
        }
        if (s1.length > 1) {
            try {
                minLength = Integer.parseInt(s1[1]);
            } catch (Exception e) {
                LOGGER.debug("unexpected error parsing config input '" + inputString + "' " + e.getMessage());
            }
        }
        if (s1.length > 2) {
            try {
                maxLength = Integer.parseInt(s1[2]);
            } catch (Exception e) {
                LOGGER.debug("unexpected error parsing config input '" + inputString + "' " + e.getMessage());
            }
        }

        boolean adminDefined = true;
        if (inputString != null && inputString.equalsIgnoreCase("%user%")) {
            inputString = null;
            adminDefined = false;
        }

        return new ChaiChallenge(required, inputString, minLength, maxLength, adminDefined);
    }

    public long readSettingAsLong(final PwmSetting setting) {
        return StringHelper.convertStrToLong(storedConfiguration.readSetting(setting), 0);
    }

    public PwmPasswordPolicy getGlobalPasswordPolicy(final Locale locale)
    {
        PwmPasswordPolicy policy = cachedPasswordPolicy.get(locale);

        if (policy == null) {
            final Map<String, String> passwordPolicySettings = new HashMap<String, String>();
            for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
                if (rule.getPwmSetting() != null) {
                    final String value;
                    final PwmSetting pwmSetting = rule.getPwmSetting();
                    switch (rule) {
                        case DisallowedAttributes:
                        case DisallowedValues:
                            value = StringHelper.stringCollectionToString(readSettingAsStringArray(pwmSetting), "\n");
                            break;
                        case RegExMatch:
                        case RegExNoMatch:
                            value = StringHelper.stringCollectionToString(readSettingAsStringArray(pwmSetting), ";;;");
                            break;
                        case ChangeMessage:
                            value = readSettingAsLocalizedString(pwmSetting, locale);
                            break;
                        default:
                            value = readSettingAsString(pwmSetting);
                    }
                    passwordPolicySettings.put(rule.getKey(), value);
                }
            }

            if (!"read".equals(readSettingAsString(PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY))) {
                passwordPolicySettings.put(PwmPasswordRule.CaseSensitive.getKey(),readSettingAsString(PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY));
            }

            policy = PwmPasswordPolicy.createPwmPasswordPolicy(passwordPolicySettings);
            cachedPasswordPolicy.put(locale,policy);
        }
        return policy;
    }


    public List<String> readSettingAsStringArray(final PwmSetting setting) {
        final List<String> results = new ArrayList<String>(storedConfiguration.readStringArraySetting(setting));
        for (final Iterator iter = results.iterator(); iter.hasNext();) {
            final Object loopString = iter.next();
            if (loopString == null || loopString.toString().length() < 1) {
                iter.remove();
            }
        }
        return results;
    }

    public Map<String,String> readSettingAsStringMap(final PwmSetting setting) {
        List<String> configs = readSettingAsStringArray(setting);
        //LinkedHashMap so that the order of the settings are maintained
        Map<String, String> results = new LinkedHashMap<String, String>();
        for (String config : configs) {
            String[] tokens = config.split(":");
            results.put(tokens[0], tokens.length > 1 ? tokens[1] : tokens[0]);
        }
        return results;
    }

    public String readSettingAsLocalizedString(final PwmSetting setting, final Locale locale) {
        final Map<String, String> availableValues = storedConfiguration.readLocalizedStringSetting(setting);
        final Map<Locale, String> availableLocaleMap = new HashMap<Locale, String>();
        for (final String localeStr : availableValues.keySet()) {
            availableLocaleMap.put(Helper.parseLocaleString(localeStr), availableValues.get(localeStr));
        }
        final Locale matchedLocale = Helper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }

    public Map<String, String> getLoginContexts() {
        final List<String> values = readSettingAsStringArray(PwmSetting.LDAP_LOGIN_CONTEXTS);
        return Configuration.convertStringListToNameValuePair(values, ":::");
    }

    public static Map<String, String> convertStringListToNameValuePair(final Collection<String> input, final String separator) {
        if (input == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> returnMap = new LinkedHashMap<String, String>();
        for (final String loopStr : input) {
            if (loopStr != null && separator != null && loopStr.contains(separator)) {
                final int seperatorLocation = loopStr.indexOf(separator);
                final String key = loopStr.substring(0, seperatorLocation);
                final String value = loopStr.substring(seperatorLocation + separator.length(), loopStr.length());
                returnMap.put(key, value);
            } else {
                returnMap.put(loopStr, "");
            }
        }

        return returnMap;
    }

    public Date getModifyTime() {
        return storedConfiguration.getModifyTime();
    }

    public boolean isDefaultValue(final PwmSetting pwmSetting) {
        return storedConfiguration.isDefaultValue(pwmSetting);
    }

    public Collection<Locale> localesForSetting(final PwmSetting setting) {
        final Collection<Locale> returnCollection = new ArrayList<Locale>();
        switch (setting.getSyntax()) {
            case LOCALIZED_TEXT_AREA:
            case LOCALIZED_STRING:
                for (final String localeStr : storedConfiguration.readLocalizedStringSetting(setting).keySet()) {
                    returnCollection.add(Helper.parseLocaleString(localeStr));
                }
                break;

            case LOCALIZED_STRING_ARRAY:
                for (final String localeStr : storedConfiguration.readLocalizedStringArraySetting(setting).keySet()) {
                    returnCollection.add(Helper.parseLocaleString(localeStr));
                }
                break;
        }

        return returnCollection;
    }

    public String readProperty(final String key) {
        return storedConfiguration.readProperty(key);
    }

    public boolean readSettingAsBoolean(final PwmSetting setting) {
        return StringHelper.convertStrToBoolean(storedConfiguration.readSetting(setting));
    }

    public String toDebugString() {
        return storedConfiguration.toString(true);
    }

    public String getNotes() {
        return storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_NOTES);
    }

    public SecretKey getSecurityKey() throws PwmOperationalException {
        final String configValue = readSettingAsString(PwmSetting.PWM_SECURITY_KEY);
        if (configValue == null || configValue.length() <= 0) {
            final String errorMsg = "PWM Security Key value is not configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (configValue.length() < 32) {
            final String errorMsg = "PWM Security Key must be greater than 32 characters in length";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        try {
            return Helper.SimpleTextCrypto.makeKey(configValue);
        } catch (Exception e) {
            final String errorMsg = "unexpected error generating PWM Security Key crypto: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            LOGGER.error(errorInfo,e);
            throw new PwmOperationalException(errorInfo);
        }
    }

    public List<STORAGE_METHOD> getResponseReadLocations() {
        final String readRawValue = readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE);
        final List<STORAGE_METHOD> readPreferences = new ArrayList<STORAGE_METHOD>();
        for (final String rawValue : readRawValue.split("-")) {
            readPreferences.add(STORAGE_METHOD.valueOf(rawValue));
        }
        return readPreferences;
    }

    public PwmPasswordPolicy getNewUserPasswordPolicy(final PwmApplication pwmApplication, final Locale userLocale)
            throws PwmUnrecoverableException, ChaiUnavailableException {

        {
            if ((System.currentTimeMillis() - newUserPasswordPolicyCacheTime) > PwmConstants.NEWUSER_PASSWORD_POLICY_CACHE_MS) {
                newUserPasswordPolicy.clear();
            }

            final PwmPasswordPolicy cachedPolicy = newUserPasswordPolicy.get(userLocale);
            if (cachedPolicy != null) {
                return cachedPolicy;
            }

            final String configuredNewUserPasswordDN = readSettingAsString(PwmSetting.NEWUSER_PASSWORD_POLICY_USER);
            if (configuredNewUserPasswordDN == null || configuredNewUserPasswordDN.length() < 1) {
                final PwmPasswordPolicy thePolicy = getGlobalPasswordPolicy(userLocale);
                newUserPasswordPolicy.put(userLocale,thePolicy);
                return thePolicy;
            } else {

                final String lookupDN;
                if (configuredNewUserPasswordDN.equalsIgnoreCase("TESTUSER") ) {
                    lookupDN = readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
                } else {
                    lookupDN = configuredNewUserPasswordDN;
                }

                final ChaiUser chaiUser = ChaiFactory.createChaiUser(lookupDN, pwmApplication.getProxyChaiProvider());
                final PwmPasswordPolicy thePolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, null, chaiUser, userLocale);
                newUserPasswordPolicy.put(userLocale,thePolicy);
                return thePolicy;
            }
        }
    }

    public List<Locale> getKnownLocales() {
        if (knownLocales == null) {
            final List<String> localeList = readSettingAsStringArray(PwmSetting.KNOWN_LOCALES);
            final Map<String,Locale> tempMap = new TreeMap<String,Locale>();
            final List<Locale> returnList = new ArrayList<Locale>();

            for (final String localeString : localeList) {
                final Locale theLocale = Helper.parseLocaleString(localeString);
                if (theLocale != null) {
                    tempMap.put(theLocale.getDisplayName(),theLocale);
                }
            }

            for (final Locale loopLocale : tempMap.values()) {
                returnList.add(loopLocale);
            }

            knownLocales = Collections.unmodifiableList(returnList);
        }
        return knownLocales;
    }


    public enum STORAGE_METHOD { DB, LDAP, PWMDB }
}
