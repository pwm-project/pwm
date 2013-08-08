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

import com.google.gson.Gson;
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
import password.pwm.bean.EmailItemBean;
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
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class Configuration implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private final static PwmLogger LOGGER = PwmLogger.getLogger(Configuration.class);

    private final StoredConfiguration storedConfiguration;

    private Map<Locale,PwmPasswordPolicy> cachedPasswordPolicy = new HashMap<Locale,PwmPasswordPolicy>();
    private Map<Locale,PwmPasswordPolicy> newUserPasswordPolicy = new HashMap<Locale,PwmPasswordPolicy>();
    private Map<Locale,String> localeFlagMap = null;
    private long newUserPasswordPolicyCacheTime = System.currentTimeMillis();

    public enum STORAGE_METHOD { DB, LDAP, LOCALDB, NMAS, NMASUAWS }

    public enum RECOVERY_ACTION { RESETPW, SENDNEWPW }

    public enum TokenStorageMethod {STORE_LOCALDB, STORE_DB, STORE_CRYPTO, STORE_LDAP}

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

    public String toString(final PwmSetting pwmSetting) {
        return new Gson().toJson(this.storedConfiguration.readSetting(pwmSetting).toNativeObject());
    }

// -------------------------- OTHER METHODS --------------------------

    public List<FormConfiguration> readSettingAsForm(final PwmSetting setting) {
        if (PwmSettingSyntax.FORM != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read FORM value for setting: " + setting.toString());
        }

        final StoredValue value = storedConfiguration.readSetting(setting);
        return (List<FormConfiguration>)value.toNativeObject();
    }

    public EmailItemBean readSettingAsEmail(final PwmSetting setting, final Locale locale) {
        if (PwmSettingSyntax.EMAIL != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read EMAIL value for setting: " + setting.toString());
        }

        final Map<String, EmailItemBean> storedValues = (Map<String, EmailItemBean>)storedConfiguration.readSetting(setting).toNativeObject();
        final Map<Locale, EmailItemBean> availableLocaleMap = new LinkedHashMap<Locale, EmailItemBean>();
        for (final String localeStr : storedValues.keySet()) {
            availableLocaleMap.put(Helper.parseLocaleString(localeStr), storedValues.get(localeStr));
        }
        final Locale matchedLocale = Helper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }

    public PwmSetting.MessageSendMethod readSettingAsTokenSendMethod(final PwmSetting setting) {
        return PwmSetting.MessageSendMethod.valueOf(readSettingAsString(setting));
    }


    public List<ActionConfiguration> readSettingAsAction(final PwmSetting setting) {
        if (PwmSettingSyntax.ACTION != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read ACTION value for setting: " + setting.toString());
        }

        final StoredValue value = storedConfiguration.readSetting(setting);
        return (List<ActionConfiguration>)value.toNativeObject();
    }

    public List<String> readSettingAsLocalizedStringArray(final PwmSetting setting, final Locale locale) {
        if (PwmSettingSyntax.LOCALIZED_STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read LOCALIZED_STRING_ARRAY value for setting: " + setting.toString());
        }
        final Map<String, List<String>> storedValues = (Map<String, List<String>>)storedConfiguration.readSetting(setting).toNativeObject();
        final Map<Locale, List<String>> availableLocaleMap = new LinkedHashMap<Locale, List<String>>();
        for (final String localeStr : storedValues.keySet()) {
            availableLocaleMap.put(Helper.parseLocaleString(localeStr), storedValues.get(localeStr));
        }
        final Locale matchedLocale = Helper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }

    public String readSettingAsString(final PwmSetting setting) {
        switch (setting.getSyntax()) {
            case STRING:
            case TEXT_AREA:
            case SELECT:
            case NUMERIC:
            case BOOLEAN:
            case PASSWORD:
                StoredValue value = storedConfiguration.readSetting(setting);
                if (value == null) return null;
                Object nativeObject = value.toNativeObject();
                if (nativeObject == null) return null;
                return nativeObject.toString();

            default:
                throw new IllegalArgumentException("may not read setting as string: " + setting.toString());
        }
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
        final String value = readSettingAsString(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL);
        for (final PwmLogLevel logLevel : PwmLogLevel.values()) {
            if (logLevel.toString().equalsIgnoreCase(value)) {
                return logLevel;
            }
        }

        return PwmLogLevel.TRACE;
    }

    public ChallengeSet getGlobalChallengeSet(final Locale locale) {
        return readChallengeSet(
                locale,
                PwmSetting.CHALLENGE_REQUIRED_CHALLENGES,
                PwmSetting.CHALLENGE_RANDOM_CHALLENGES,
                (int) readSettingAsLong(PwmSetting.CHALLENGE_MIN_RANDOM_REQUIRED)
        );
    }

    public ChallengeSet getHelpdeskChallengeSet(final Locale locale) {
        return readChallengeSet(
                locale,
                PwmSetting.CHALLENGE_HELPDESK_REQUIRED_CHALLENGES,
                PwmSetting.CHALLENGE_HELPDESK_RANDOM_CHALLENGES,
                1
        );
    }

    private ChallengeSet readChallengeSet(
            final Locale locale,
            final PwmSetting requiredChallenges,
            final PwmSetting randomChallenges,
            int minimumRands
    )
    {
        final List<String> requiredQuestions = readSettingAsLocalizedStringArray(requiredChallenges, locale);
        final List<String> randomQuestions = readSettingAsLocalizedStringArray(randomChallenges, locale);

        final List<Challenge> challenges = new ArrayList<Challenge>();

        if (requiredQuestions != null) {
            for (final String question : requiredQuestions) {
                final Challenge challenge = parseConfigStringToChallenge(question, true);
                if (challenge != null) {
                    challenges.add(challenge);
                }
            }
        }

        if (randomQuestions != null) {
            for (final String question : randomQuestions) {
                final Challenge challenge = parseConfigStringToChallenge(question, false);
                if (challenge != null) {
                    challenges.add(challenge);
                }
            }

            if (minimumRands > randomQuestions.size()) {
                minimumRands = randomQuestions.size();
            }
        } else {
            minimumRands = 0;
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
            inputString = s1[0].trim();
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
        if (PwmSettingSyntax.NUMERIC != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read NUMERIC value for setting: " + setting.toString());
        }

        return (Long)storedConfiguration.readSetting(setting).toNativeObject();
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
                            value = String.valueOf(storedConfiguration.readSetting(pwmSetting).toNativeObject());
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
        if (PwmSettingSyntax.STRING_ARRAY != setting.getSyntax() && PwmSettingSyntax.FORM != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read STRING_ARRAY value for setting: " + setting.toString());
        }

        final StoredValue value = storedConfiguration.readSetting(setting);
        final List<String> results = new ArrayList<String>((List<String>)value.toNativeObject());
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
        if (PwmSettingSyntax.LOCALIZED_STRING != setting.getSyntax() && PwmSettingSyntax.LOCALIZED_TEXT_AREA != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read LOCALIZED_STRING or LOCALIZED_TEXT_AREA values for setting: " + setting.toString());
        }

        final Map<String, String> availableValues = (Map<String, String>)storedConfiguration.readSetting(setting).toNativeObject();
        final Map<Locale, String> availableLocaleMap = new LinkedHashMap<Locale, String>();
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
                for (final String localeStr : ((Map<String, String>)storedConfiguration.readSetting(setting).toNativeObject()).keySet()) {
                    returnCollection.add(Helper.parseLocaleString(localeStr));
                }
                break;

            case LOCALIZED_STRING_ARRAY:
                for (final String localeStr : ((Map<String, List<String>>)storedConfiguration.readSetting(setting).toNativeObject()).keySet()) {
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
        if (PwmSettingSyntax.BOOLEAN != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read BOOLEAN value for setting: " + setting.toString());
        }

        return (Boolean)storedConfiguration.readSetting(setting).toNativeObject();
    }

    public X509Certificate[] readSettingAsCertificate(final PwmSetting setting) {
        if (PwmSettingSyntax.X509CERT != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read X509CERT value for setting: " + setting.toString());
        }

        return (X509Certificate[])storedConfiguration.readSetting(setting).toNativeObject();
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
            final String errorMsg = "Security Key value is not configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (configValue.length() < 32) {
            final String errorMsg = "Security Key must be greater than 32 characters in length";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        try {
            return Helper.SimpleTextCrypto.makeKey(configValue);
        } catch (Exception e) {
            final String errorMsg = "unexpected error generating Security Key crypto: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            LOGGER.error(errorInfo,e);
            throw new PwmOperationalException(errorInfo);
        }
    }

    public List<STORAGE_METHOD> getResponseStorageLocations(final PwmSetting setting) {
        final String input = readSettingAsString(setting);
        final List<STORAGE_METHOD> storageMethods = new ArrayList<STORAGE_METHOD>();
        for (final String rawValue : input.split("-")) {
            try {
                storageMethods.add(STORAGE_METHOD.valueOf(rawValue));
            } catch (IllegalArgumentException e) {
                LOGGER.error("unknown STORAGE_METHOD found: " + rawValue);
            }
        }
        return storageMethods;
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
        if (localeFlagMap == null) {
            localeFlagMap = figureLocaleFlagMap();
        }
        return Collections.unmodifiableList(new ArrayList<Locale>(localeFlagMap.keySet()));
    }

    public Map<Locale,String> getKnownLocaleFlagMap() {
        if (localeFlagMap == null) {
            localeFlagMap = figureLocaleFlagMap();
        }
        return localeFlagMap;
    }

    private Map<Locale,String> figureLocaleFlagMap() {
        final String defaultLocaleAsString = PwmConstants.DEFAULT_LOCALE.toString();

        final List<String> inputList = readSettingAsStringArray(PwmSetting.KNOWN_LOCALES);
        final Map<String,String> inputMap = convertStringListToNameValuePair(inputList,"::");

        // Sort the map by display name
        Map<String,String> sortedMap = new TreeMap<String,String>();
        for (final String localeString : inputMap.keySet()) {
            final Locale theLocale = Helper.parseLocaleString(localeString);
            if (theLocale != null) {
                sortedMap.put(theLocale.getDisplayName(), localeString);
            }
        }

        final List<String> returnList = new ArrayList<String>();

        //ensure default is first.
        returnList.add(defaultLocaleAsString);
        for (final String localeDisplayString : sortedMap.keySet()) {
            final String localeString = sortedMap.get(localeDisplayString);
            if (!defaultLocaleAsString.equals(localeString)) {
                returnList.add(localeString);
            }
        }

        final Map<Locale,String> localeFlagMap = new LinkedHashMap<Locale, String>();
        for (final String localeString : returnList) {
            final Locale loopLocale = Helper.parseLocaleString(localeString);
            if (loopLocale != null) {
                final String flagCode = inputMap.containsKey(localeString) ? inputMap.get(localeString) : loopLocale.getCountry();
                localeFlagMap.put(loopLocale, flagCode);
            }
        }
        return Collections.unmodifiableMap(localeFlagMap);
    }

    public RECOVERY_ACTION getRecoveryAction() {
        final String stringValue = readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_ACTION);
        try {
            return RECOVERY_ACTION.valueOf(stringValue);
        } catch (IllegalArgumentException e) {
            LOGGER.error("unknown recovery action value: " + stringValue);
            return RECOVERY_ACTION.RESETPW;
        }
    }

    public TokenStorageMethod getTokenStorageMethod() {
        try {
            return TokenStorageMethod.valueOf(readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD));
        } catch (Exception e) {
            final String errorMsg = "unknown storage method specified: " + readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD);
            ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            LOGGER.warn(errorInformation.toDebugStr());
            return null;
        }
    }

    public PwmSetting.Template getTemplate() {
        return storedConfiguration.getTemplate();
    }

    public boolean shouldHaveDbConfigured() {
        for (final PwmSetting loopSetting : new PwmSetting[] {PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE, PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE}) {
            if (getResponseStorageLocations(loopSetting).contains(Configuration.STORAGE_METHOD.DB)) {
                return true;
            }
        }
        return false;
    }

    public String getUsernameAttribute() {
        final String configUsernameAttr = readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);
        final String ldapNamingAttribute = readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        return configUsernameAttr != null && configUsernameAttr.length() > 0 ? configUsernameAttr : ldapNamingAttribute;
    }
}
