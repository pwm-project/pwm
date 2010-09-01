/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.CrFactory;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.ContextManager;
import password.pwm.Helper;
import password.pwm.PwmConstants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.util.PwmLogLevel;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.*;

/**
 * Simple data object, contains configuration information defined in the servlet .properties
 * configuration file.
 *
 * @author Jason D. Rivard
 */
public class Configuration implements Serializable {
    private final static PwmLogger LOGGER = PwmLogger.getLogger(Configuration.class);

    private final StoredConfiguration storedConfiguration;
    private final long loadTime = System.currentTimeMillis();

    private PwmPasswordPolicy cachedPasswordPolicy = null;

    public Configuration(final StoredConfiguration storedConfiguration) {
        this.storedConfiguration = storedConfiguration;
    }

    public String readSettingAsString(final PwmSetting setting)
    {
        return storedConfiguration.readSetting(setting);
    }

    public Date getModifyTime() {
        return storedConfiguration.getModifyTime();
    }

    public List<String> readStringArraySetting(final PwmSetting setting)
    {
        final List<String> results = storedConfiguration.readStringArraySetting(setting);
        for (final Iterator iter = results.iterator(); iter.hasNext(); ) {
            final Object loopString = iter.next();
            if (loopString == null || loopString.toString().length() < 1) {
                iter.remove();
            }
        }
        return results;
    }

    public List<String> readFormSetting(final PwmSetting setting, final Locale locale) {
        final Map<String,List<String>> storedValues = storedConfiguration.readLocalizedStringArraySetting(setting);
        final Map<Locale,List<String>> availableLocaleMap = new HashMap<Locale,List<String>>();
        for (final String localeStr : storedValues.keySet()) {
            availableLocaleMap.put(Helper.parseLocaleString(localeStr),storedValues.get(localeStr));
        }
        final Locale matchedLocale = Helper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
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

    public boolean readSettingAsBoolean(final PwmSetting setting)
    {
        return StringHelper.convertStrToBoolean(storedConfiguration.readSetting(setting));
    }

    public int readSettingAsInt(final PwmSetting setting)
    {
        return StringHelper.convertStrToInt(storedConfiguration.readSetting(setting),0);
    }

    public String readLocalizedStringSetting(final PwmSetting setting, final Locale locale) {
        final Map<String,String> availableValues = storedConfiguration.readLocalizedStringSetting(setting);
        final Map<Locale,String> availableLocaleMap = new HashMap<Locale,String>();
        for (final String localeStr : availableValues.keySet()) {
            availableLocaleMap.put(Helper.parseLocaleString(localeStr),availableValues.get(localeStr));
        }
        final Locale matchedLocale = Helper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }


    public Set<String> getAllUsedLdapAttributes()
    {
        final Set<String> returnSet = new HashSet<String>();

        returnSet.addAll(convertMapToFormConfiguration(readFormSetting(PwmSetting.ACTIVATE_USER_FORM,Locale.getDefault())).keySet());
        returnSet.addAll(convertMapToFormConfiguration(readFormSetting(PwmSetting.NEWUSER_FORM,Locale.getDefault())).keySet());
        returnSet.addAll(convertMapToFormConfiguration(readFormSetting(PwmSetting.UPDATE_ATTRIBUTES_FORM,Locale.getDefault())).keySet());
        returnSet.add(this.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.EVENTS_LDAP_ATTRIBUTE));
        returnSet.addAll(this.getGlobalPasswordPolicy(Locale.getDefault()).getRuleHelper().getDisallowedAttributes());
        returnSet.add(this.readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE));

        return returnSet;
    }

    public PwmLogLevel getEventLogLocalLevel()
    {
        final String value = readSettingAsString(PwmSetting.EVENTS_PWMDB_LOG_LEVEL);
        for (final PwmLogLevel logLevel : PwmLogLevel.values()) {
            if (logLevel.toString().equalsIgnoreCase(value)) {
                return logLevel;
            }
        }

        return PwmLogLevel.TRACE;
    }


    public PwmPasswordPolicy getGlobalPasswordPolicy(final Locale locale) {
        if (cachedPasswordPolicy != null) {
            return cachedPasswordPolicy;
        }

        final Map<String,String> passwordPolicySettings = new HashMap<String,String>();
        for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
            if (rule.getPwmSetting() != null) {
                final String value;
                final PwmSetting pwmSetting = rule.getPwmSetting();
                switch (rule) {
                    case DisallowedAttributes:
                    case DisallowedValues:
                        value = StringHelper.stringCollectionToString(readStringArraySetting(pwmSetting),"\n");
                        break;
                    case RegExMatch:
                    case RegExNoMatch:
                        value = StringHelper.stringCollectionToString(readStringArraySetting(pwmSetting),";;;");
                        break;
                    case ChangeMessage:
                        value = readLocalizedStringSetting(pwmSetting, locale);
                        break;
                    default:
                        value = readSettingAsString(pwmSetting);
                }
                passwordPolicySettings.put(rule.getKey(), value);
            }
        }

        cachedPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(passwordPolicySettings);
        return cachedPasswordPolicy;
    }

    public ChallengeSet getGlobalChallengeSet(final Locale locale) {
        final List<String> requiredQuestions = readFormSetting(PwmSetting.CHALLENGE_REQUIRED_CHALLENGES, locale);
        final List<String> randomQuestions = readFormSetting(PwmSetting.CHALLENGE_RANDOM_CHALLENGES, locale);

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

        int minimumRands = readSettingAsInt(PwmSetting.CHALLENGE_MIN_RANDOM_REQUIRED);
        if (minimumRands > randomQuestions.size()) {
            minimumRands = randomQuestions.size();
        }

        try {
            return CrFactory.newChallengeSet(challenges, locale, minimumRands, "pwm-defined " + PwmConstants.SERVLET_VERSION);
        } catch (ChaiValidationException e) {
            LOGGER.warn("invalid challenge set configuration: " + e.getMessage());
        }
        return null;
    }

    public Map<String,String> getLoginContexts() {
        final List<String> values = readStringArraySetting(PwmSetting.LDAP_LOGIN_CONTEXTS);
        return Configuration.convertStringListToNameValuePair(values,":::");
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

        return CrFactory.newChallenge(required, inputString, minLength, maxLength, adminDefined);
    }

    public static Map<String, String> convertStringListToNameValuePair(final Collection<String> input, final String separator) {
        if (input == null) {
            return Collections.emptyMap();
        }

        final Map<String,String> returnMap = new LinkedHashMap<String,String>();
        for (final String loopStr : input) {
            if (loopStr != null && separator != null && loopStr.contains(separator)) {
                final int seperatorLocation = loopStr.indexOf(separator);
                final String key = loopStr.substring(0, seperatorLocation);
                final String value = loopStr.substring(seperatorLocation + separator.length(), loopStr.length());
                returnMap.put(key, value);
            } else {
                returnMap.put(loopStr,"");
            }
        }

        return returnMap;
    }

    public static Map<String, FormConfiguration> convertMapToFormConfiguration(final Collection<String> input) {
        if (input == null) {
            return Collections.emptyMap();
        }

        final Map<String, FormConfiguration> returnMap = new LinkedHashMap<String, FormConfiguration>();
        for (final String loopString : input) {
            if (loopString != null && loopString.length() > 0) {
                final FormConfiguration formConfig = FormConfiguration.parseConfigString(loopString);
                final String attrName = formConfig.getAttributeName();
                returnMap.put(attrName, formConfig);
            }
        }
        return returnMap;
    }

    public String toString() {
        return storedConfiguration.toString();
    }

    public String toDebugString() {
        return storedConfiguration.toString(true);
    }

    public String readProperty(final String key) {
        return storedConfiguration.readProperty(key);
    }

    public static Configuration getConfig(final HttpSession session) {
        return ContextManager.getContextManager(session).getConfig();
    }

    public static Configuration getConfig(final HttpServletRequest request) {
        return ContextManager.getContextManager(request).getConfig();
    }
}