/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import com.novell.ldapchai.cr.CrMode;
import com.novell.ldapchai.util.StringHelper;
import org.jdom.CDATA;
import org.jdom.Element;

import java.io.Serializable;
import java.util.*;


/**
 * Each of the standard (non-localizable) settings configured via the pwmServlet.properties file
 * are identified here.
 *
 * @author Jason D. Rivard
 */
public enum PwmSetting {
    // general settings
    /**
     * Title of the application, displayed on most html pages seen by users.
     */
    APPLICATION_TILE(
            "title", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, true, Category.GENERAL),
    URL_LOGOUT(
            "logoutURL", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    URL_FORWARD(
            "forwardURL", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    LOGOUT_AFTER_PASSWORD_CHANGE(
            "logoutAfterPasswordChange", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.GENERAL),
    URL_SERVET_RELATIVE(
            "servletRelativeURL", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    EMAIL_SERVER_ADDRESS(
            "smtpServerAddress", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    USE_X_FORWARDED_FOR_HEADER(
            "useXForwardedForHeader", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.GENERAL),
    ADMIN_ALERT_EMAIL_ADDRESS(
            "adminAlertEmailAddress", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    ADMIN_ALERT_FROM_ADDRESS(
            "adminAlertFromAddress", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    PASSWORD_EXPIRE_PRE_TIME(
            "expirePreTime", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.GENERAL),
    PASSWORD_EXPIRE_WARN_TIME(
            "expireWarnTime", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.GENERAL),
    EXPIRE_CHECK_DURING_AUTH(
            "expireCheckDuringAuth", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.GENERAL),
    EXTERNAL_PASSWORD_METHODS(
            "externalPasswordMethods", false, Syntax.BOOLEAN, Static.STRING_ARRAY_VALUE_HELPER, false, Category.GENERAL),
    PASSWORD_SYNC_MAX_WAIT_TIME(
            "passwordSyncMaxWaitTime", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.GENERAL),
    WORDLIST_FILENAME(
            "password.WordlistFile", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    SEEDLIST_FILENAME(
            "password.SeedlistFile", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    QUERY_MATCH_CHECK_RESPONSES(
            "command.checkResponses.queryMatch", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    PASSWORD_LAST_UPDATE_ATTRIBUTE(
            "passwordLastUpdateAttribute", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.GENERAL),
    PASSWORD_SHAREDHISTORY_MAX_AGE(
            "password.sharedHistory.age", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.GENERAL),

    //ldap directory
    LDAP_SERVER_URLS(
            "ldapServerURLs", false, Syntax.TEXT_ARRAY, Static.STRING_ARRAY_VALUE_HELPER, false, Category.LDAP),
    LDAP_PROXY_USER_DN(
            "ldapProxyDN", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.LDAP),
    LDAP_PROXY_USER_PASSWORD(
            "ldapProxyPassword", true, Syntax.PASSWORD, Static.STRING_VALUE_HELPER, false, Category.LDAP),
    LDAP_PROMISCUOUS_SSL(
            "ldapPromiscuousSSL", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.LDAP),
    LDAP_TIMEOUT(
            "ldapTimeout", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.LDAP),
    LDAP_CONTEXTLESS_ROOT(
            "ldapContextlessLoginRoot", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.LDAP),
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.LDAP),
    LOGIN_CONTEXTS(
            "loginContexts", false, Syntax.TEXT_ARRAY, Static.STRING_ARRAY_VALUE_HELPER, false, Category.LDAP),
    USERNAME_SEARCH_FILTER(
            "usernameSearchFilter", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.LDAP),
    AUTO_ADD_OBJECT_CLASSES(
            "autoAddObjectClasses", false, Syntax.TEXT_ARRAY, Static.STRING_ARRAY_VALUE_HELPER, false, Category.LDAP),
    QUERY_MATCH_CHANGE_PASSWORD(
            "password.allowChange.queryMatch", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.LDAP),


    //global password policy settings
    PASSWORD_POLICY_MINIMUM_LENGTH(
            "password.MinimumLength", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_LENGTH(
            "password.MaximumLength", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_REPEAT(
            "password.MaximumRepeat", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT(
            "password.MaximumSequentialRepeat", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_NUMERIC(
            "password.AllowNumeric", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC(
            "password.AllowFirstCharNumeric", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC(
            "password.AllowLastCharNumeric", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_NUMERIC(
            "password.MaximumNumeric", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_NUMERIC(
            "password.MinimumNumeric", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_SPECIAL(
            "password.AllowSpecial", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL(
            "password.AllowFirstCharSpecial", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL(
            "password.AllowLastCharSpecial", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_SPECIAL(
            "password.MaximumSpecial", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_SPECIAL(
            "password.MinimumSpecial", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_ALPHA(
            "password.MaximumAlpha", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_ALPHA(
            "password.MinimumAlpha", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_UPPERCASE(
            "password.MaximumUpperCase", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_UPPERCASE(
            "password.MinimumUpperCase", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_LOWERCASE(
            "password.MaximumLowerCase", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_LOWERCASE(
            "password.MinimumLowerCase", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_UNIQUE(
            "password.MinimumUnique", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS(
            "password.MaximumOldPasswordChars", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_AD_COMPLEXITY(
            "password.ADComplexity", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH(
            "password.RegExMatch", false, Syntax.TEXT_ARRAY, Static.STRING_ARRAY_VALUE_HELPER, false, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH(
            "password.RegExNoMatch", false, Syntax.TEXT_ARRAY, Static.STRING_ARRAY_VALUE_HELPER, false, Category.PASSWORD_POLICY),





    // edirectory settings
    EDIRECTORY_ALWAYS_USE_PROXY(
            "ldap.edirectory.alwaysUseProxy", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.EDIRECTORY),
    EDIRECTORY_READ_PASSWORD_POLICY(
            "ldap.edirectory.readPasswordPolicies", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.EDIRECTORY),
    EDIRECTORY_ENABLE_NMAS(
            "ldap.edirectory.enableNmas", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.EDIRECTORY),

    // intruder settings
    INTRUDER_USER_RESET_TIME(
            "intruder.user.resetTime", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.INTRUDER),
    INTRUDER_USER_MAX_ATTEMPTS(
            "intruder.user.maxAttempts", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.INTRUDER),
    INTRUDER_ADDRESS_RESET_TIME(
            "intruder.address.resetTime", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.INTRUDER),
    INTRUDER_ADDRESS_MAX_ATTEMPTS(
            "intruder.address.maxAttempts", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.INTRUDER),
    INTRUDER_SESSION_MAX_ATTEMPTS(
            "intruder.session.maxAttempts", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.INTRUDER),

    // logger settings
    EVENT_LOG_MAX_LOCAL_EVENTS(
            "eventLog.localDbMaxEvents", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.LOGGING),
    EVENT_LOG_MAX_LOCAL_AGE(
            "eventLog.localDbMaxAge", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.LOGGING),
    EVENT_LOG_ATTRIBUTE(
            "eventLog.userAttribute", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.LOGGING),
    EVENT_LOG_MAX_EVENTS_USER(
            "eventLog.maxEvents", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.LOGGING),

    // recovery settings
    CHALLANGE_FORCE_SETUP(
            "challenge.forceSetup", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.RECOVERY),
    QUERY_MATCH_SETUP_RESPONSE(
            "challenge.allowSetup.queryMatch", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.RECOVERY),
    CHALLENGE_USER_ATTRIBUTE(
            "challenge.userAttribute", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.RECOVERY),
    CHALLENGE_RANDOM_STYLE(
            "challenge.randomStyle", false, Syntax.TEXT_ARRAY, new Static.ValueHelper() {
                Object parseImpl(final PwmSetting setting, final String value)
                {
                    Configuration.CR_RANDOM_STYLE s = Configuration.CR_RANDOM_STYLE.RECOVER;
                    if (value.equalsIgnoreCase(Configuration.CR_RANDOM_STYLE.RECOVER.toString())) {
                        s = Configuration.CR_RANDOM_STYLE.RECOVER;
                    } else if (value.equalsIgnoreCase(Configuration.CR_RANDOM_STYLE.SETUP.toString())) {
                        s = Configuration.CR_RANDOM_STYLE.SETUP;
                    }
                    return s;
                }
            },
            false, Category.RECOVERY),
    CHALLANGE_ALLOW_UNLOCK(
            "challenge.allowUnlock", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.RECOVERY),
    CHALLENGE_STORAGE_METHOD(
            "challenge.storageMethod", false, Syntax.TEXT_ARRAY, new Static.ValueHelper() {
                Object parseImpl(final PwmSetting setting, final String value)
                {
                    final List<CrMode> list = new ArrayList<CrMode>();
                    if (value.contains("PWMSHA1"))
                        list.add(CrMode.CHAI_SHA1_SALT);
                    if (value.contains("PWMTEXT"))
                        list.add(CrMode.CHAI_TEXT);
                    if (value.contains("NMAS"))
                        list.add(CrMode.NMAS);
                    return list.toArray(new CrMode[list.size()]);
                }

                String debugStringImpl(final Object value)
                {
                    final CrMode[] v = (CrMode[]) value;
                    final StringBuilder sb = new StringBuilder();
                    for (final CrMode mode : v) {
                        sb.append(mode.toString());
                    }

                    if (sb.length() > 2) {
                        sb.delete(sb.length() - 2, sb.length());
                    }

                    return sb.toString();
                }
            },
            false, Category.RECOVERY),
    CHALLENGE_POLICY_METHOD(
            "challenge.policyMethod", false, Syntax.TEXT_ARRAY, new Static.ValueHelper() {
                Object parseImpl(final PwmSetting setting, final String value)
                {
                    final List<Configuration.CR_POLICY_READ_METHOD> list = new ArrayList<Configuration.CR_POLICY_READ_METHOD>();
                    if (value.contains("NMAS"))
                        list.add(Configuration.CR_POLICY_READ_METHOD.NMAS);
                    if (value.contains("PWM"))
                        list.add(Configuration.CR_POLICY_READ_METHOD.PWM);
                    return list.toArray(new Configuration.CR_POLICY_READ_METHOD[list.size()]);
                }

                String debugStringImpl(final Object value)
                {
                    final Configuration.CR_POLICY_READ_METHOD[] v = (Configuration.CR_POLICY_READ_METHOD[]) value;
                    final StringBuilder sb = new StringBuilder();
                    for (final Configuration.CR_POLICY_READ_METHOD mode : v) {
                        sb.append(mode.toString());
                        sb.append(", ");
                    }

                    if (sb.length() > 2) {
                        sb.delete(sb.length() - 2, sb.length());
                    }

                    return sb.toString();
                }
            },
            false, Category.RECOVERY),
    CASE_INSENSITIVE_CHALLENGE(
            "challenge.caseInsensitive", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.RECOVERY),
    ALLOW_DUPLICATE_RESPONSES(
            "challenge.allowDuplicateResponses", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.RECOVERY),
    CHALLANGE_APPLY_WORDLIST(
            "challenge.applyWorldlist", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.RECOVERY),
    CHALLANGE_SHOW_CONFIRMATION(
            "challenge.showConfirmation", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.RECOVERY),
    /*
    CHALLANGE_TOKEN_ATTRIBUTE(
            "challenge.tokenAttribute", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.RECOVERY),
    CHALLANGE_TOKEN_MAX_AGE(
            "challenge.tokenMaxAge", false, Syntax.NUMERIC, Static.INT_VALUE_HELPER, false, Category.RECOVERY),
    */
    
    // new user settings
    ENABLE_NEW_USER(
            "newUser.enable", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.NEWUSER),
    NEWUSER_CONTEXT(
            "newUser.createContext", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.NEWUSER),
    NEWUSER_UNIQUE_ATTRIBUES(
            "newUser.creationUniqueAttributes", false, Syntax.TEXT_ARRAY, Static.STRING_ARRAY_VALUE_HELPER, false, Category.NEWUSER),
    NEWUSER_CREATION_ATTRIBUTES(
            "newUser.createAttributes", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, true, Category.NEWUSER),
    NEWUSER_WRITE_ATTRIBUTES(
            "newUser.writeAttributes", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.NEWUSER),

    // activation settings
    ENABLE_ACTIVATE_USER(
            "activateUser.enable", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.ACTIVATION),
    QUERY_MATCH_ACTIVATE_USER(
            "activateUser.queryMatch", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.ACTIVATION),
    ACTIVATE_USER_SEARCH_FILTER(
            "activateUser.searchFilter", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.ACTIVATION),
    ACTIVATE_USER_WRITE_ATTRIBUTES(
            "activateUser.writeAttributes", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.ACTIVATION),


    // update attributes
    ENABLE_UPDATE_ATTRIBUTES(
            "updateAttributes.enable", false, Syntax.BOOLEAN, Static.BOOLEAN_VALUE_HELPER, false, Category.UPDATE),
    QUERY_MATCH_UPDATE_USER(
            "updateAttributes.queryMatch", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.UPDATE),
    UPDATE_ATTRIBUTES_WRITE_ATTRIBUTES(
            "updateAttributes.writeAttributes", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.UPDATE),

    // captcha
    RECAPTCHA_KEY_PRIVATE(
            "captcha.recaptcha.privateKey", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.CAPTCHA),
    RECAPTCHA_KEY_PUBLIC(
            "captcha.recaptcha.publicKey", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.CAPTCHA),
    CAPTCHA_SKIP_PARAM(
            "captcha.skip.param", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.CAPTCHA),
    CAPTCHA_SKIP_COOKIE(
            "captcha.skip.cookie", false, Syntax.TEXT, Static.STRING_VALUE_HELPER, false, Category.CAPTCHA);



// ------------------------------ STATICS ------------------------------

    private static final Map<Category,List<PwmSetting>> VALUES_BY_CATEGORY;

    static {
        final Map<Category, List<PwmSetting>> returnMap = new LinkedHashMap<Category, List<PwmSetting>>();

        //setup nested lists
        for (final Category category : Category.values()) returnMap.put(category, new ArrayList<PwmSetting>());

        //populate map
        for (final PwmSetting setting : values()) returnMap.get(setting.getCategory()).add(setting);

        //make nested lists unmodifiable
        for (final Category category : Category.values()) returnMap.put(category, Collections.unmodifiableList(returnMap.get(category)));

        //assign unmodifable list
        VALUES_BY_CATEGORY = Collections.unmodifiableMap(returnMap);
    }

// ------------------------------ FIELDS ------------------------------

    private final String key;
    private final Category category;
    private final boolean confidential;
    private final Syntax syntax;
    private final Static.ValueHelper valueHelper;
    private final boolean localizable;

// --------------------------- CONSTRUCTORS ---------------------------

    PwmSetting(
            final String paramName,
            final boolean conceal,
            final Syntax syntax,
            final Static.ValueHelper valueHelper,
            final boolean localizable,
            final Category category)
    {
        this.key = paramName;
        this.confidential = conceal;
        this.syntax = syntax;
        this.valueHelper = valueHelper;
        this.localizable = localizable;
        this.category = category;
    }

// --------------------- GETTER / SETTER METHODS ---------------------


    public String getKey()
    {
        return key;
    }

    public boolean isConfidential()
    {
        return confidential;
    }

    public Category getCategory() {
        return category;
    }

    public Syntax getSyntax() {
        return syntax;
    }


    // -------------------------- OTHER METHODS --------------------------

    String debugValueString(final Object value)
    {
        return this.valueHelper.debugStringImpl(value);
    }

    Object parse(final String value)
    {
        return this.valueHelper.parseImpl(this,value);
    }

    public String getDefaultValue()
    {
        return readProps("DEFLT_" + this.getKey(), Locale.getDefault());
    }

    public String getLabel(final Locale locale) {
        return readProps("LABEL_" + this.getKey(), locale);
    }

    public String getDescription(final Locale locale) {
        return readProps("DESCR_" + this.getKey(), locale);
    }

    public Element toXmlElement(final String value) {
        return this.valueHelper.toXmlElement(this, value);
    }

    private static String readProps(final String key, final Locale locale) {
        try {
            final ResourceBundle bundle = ResourceBundle.getBundle(PwmSetting.class.getName(), locale);
            return bundle.getString(key);
        } catch (Exception e) {
            return "--RESOURCE MISSING--";
        }
    }

    public boolean isLocalizable() {
        return localizable;
    }

    // -------------------------- INNER CLASSES --------------------------

    private static class Static {
        private abstract static class ValueHelper implements Serializable {
            abstract Object parseImpl(PwmSetting setting, String value);

            Object parse(final PwmSetting setting, final String value)
            {
                final String strValue = value != null && value.length() > 0 ? value : setting.getDefaultValue();
                return parseImpl(setting, strValue);
            }

            String debugStringImpl(final Object value)
            {
                return value.toString();
            }

            Element toXmlElement(final PwmSetting pwmSetting, final String value) {
                final Element settingElement = new Element("setting");
                settingElement.setAttribute("key", pwmSetting.getKey());

                final Element valueElement = new Element("value");
                valueElement.addContent(new CDATA(value));
                settingElement.addContent(valueElement);

                return settingElement;
            }
        }

        static final ValueHelper STRING_VALUE_HELPER = new ValueHelper() {
            public Object parseImpl(final PwmSetting setting, final String value)
            {
                return value;
            }
        };

        static final ValueHelper STRING_ARRAY_VALUE_HELPER = new ValueHelper() {
            public Object parseImpl(final PwmSetting setting, final String value)
            {
                final List<String> values = StringHelper.tokenizeString(value, ",");
                return values.toArray(new String[values.size()]);
            }

            String debugStringImpl(final Object value)
            {
                final String[] v = (String[]) value;
                final StringBuilder sb = new StringBuilder();
                for (final String s : v) {
                    sb.append(s);
                    sb.append(", ");
                }

                if (sb.length() > 2) {
                    sb.delete(sb.length() - 2, sb.length());
                }

                return sb.toString();
            }
        };

        static final ValueHelper BOOLEAN_VALUE_HELPER = new ValueHelper() {
            public Object parseImpl(final PwmSetting setting, final String value)
            {
                return ConfigReader.convertStrToBoolean(value);
            }
        };

        static final ValueHelper INT_VALUE_HELPER = new ValueHelper() {
            public Object parseImpl(final PwmSetting setting, final String value)
            {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("setting " + setting.toString() + " requires a numeric value ");
                }
            }
        };
    }

    public enum Syntax {
        TEXT,
        PASSWORD,
        TEXT_ARRAY,
        NUMERIC,
        BOOLEAN,
    }

    public enum Category {
        GENERAL,
        LDAP,
        PASSWORD_POLICY,
        EDIRECTORY,
        INTRUDER,
        LOGGING,
        RECOVERY,
        NEWUSER,
        ACTIVATION,
        UPDATE,
        CAPTCHA,
        ;

        public String getLabel(final Locale locale)
        {
            return readProps("CATEGORY_LABEL_" + this.name(), locale);
        }

        public String getDescription(final Locale locale)
        {
            return readProps("CATEGORY_DESCR_" + this.name(), locale);
        }
    }

    public static Map<PwmSetting.Category, List<PwmSetting>> valuesByCategory() {
        return VALUES_BY_CATEGORY;
    }
}

