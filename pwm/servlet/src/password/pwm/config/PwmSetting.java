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

import java.util.*;
import java.util.regex.Pattern;


/**
 * PwmConfiguration settings.
 * @author Jason D. Rivard
 */
public enum PwmSetting {
    // user interface
    APPLICATION_TILE(
            "display.applicationTitle", Syntax.LOCALIZED_STRING, Category.USER_INTERFACE, true),
    PASSWORD_SHOW_AUTOGEN(
            "password.showAutoGen", Syntax.BOOLEAN, Category.USER_INTERFACE, true),
    PASSWORD_SHOW_STRENGTH_METER(
            "password.showStrengthMeter", Syntax.BOOLEAN, Category.USER_INTERFACE, true),
    DISPLAY_PASSWORD_GUIDE_TEXT(
            "display.password.guideText", Syntax.LOCALIZED_TEXT_AREA, Category.USER_INTERFACE, false),
    PASSWORD_CHANGE_AGREEMENT_MESSAGE(
            "display.password.changeAgreement", Syntax.LOCALIZED_TEXT_AREA, Category.USER_INTERFACE, false),
    PASSWORD_CHANGE_SUCCESS_MESSAGE(
            "display.password.changeSuccess", Syntax.LOCALIZED_TEXT_AREA, Category.USER_INTERFACE, false),

    // general settings
    URL_FORWARD(
            "pwm.forwardURL", Syntax.STRING, Category.GENERAL, true),
    URL_LOGOUT(
            "pwm.logoutURL", Syntax.STRING, Category.GENERAL, false),
    LOGOUT_AFTER_PASSWORD_CHANGE(
            "logoutAfterPasswordChange", Syntax.BOOLEAN, Category.GENERAL, true),
    PASSWORD_EXPIRE_PRE_TIME(
            "expirePreTime", Syntax.NUMERIC, Category.GENERAL, true),
    PASSWORD_EXPIRE_WARN_TIME(
            "expireWarnTime", Syntax.NUMERIC, Category.GENERAL, true),
    EXPIRE_CHECK_DURING_AUTH(
            "expireCheckDuringAuth", Syntax.BOOLEAN, Category.GENERAL, true),
    PASSWORD_SYNC_MIN_WAIT_TIME(
            "passwordSyncMinWaitTime", Syntax.NUMERIC, Category.GENERAL, true),
    PASSWORD_SYNC_MAX_WAIT_TIME(
            "passwordSyncMaxWaitTime", Syntax.NUMERIC, Category.GENERAL, true),
    PASSWORD_REQUIRE_CURRENT(
            "password.change.requireCurrent", Syntax.BOOLEAN, Category.GENERAL, true),
    WORDLIST_FILENAME(
            "pwm.wordlist.location", Syntax.STRING, Category.GENERAL, false),
    SEEDLIST_FILENAME(
            "pwm.seedlist.location", Syntax.STRING, Category.GENERAL, false),
    GOOGLE_ANAYLTICS_TRACKER(
            "google.analytics.tracker", Syntax.STRING, Category.GENERAL, false),

    //ldap directory
    LDAP_SERVER_URLS(
            "ldap.serverUrls", Syntax.STRING_ARRAY, Category.LDAP, true),
    LDAP_PROMISCUOUS_SSL(
            "ldap.promiscuousSSL", Syntax.BOOLEAN, Category.LDAP, true),
    LDAP_PROXY_USER_DN(
            "ldap.proxy.username", Syntax.STRING, Category.LDAP, true),
    LDAP_PROXY_USER_PASSWORD(
            "ldap.proxy.password", Syntax.PASSWORD, Category.LDAP, true),
    LDAP_TEST_USER_DN(
            "ldap.testuser.username", Syntax.STRING, Category.LDAP, false),
    LDAP_TEST_USER_PASSWORD(
            "ldap.testuser.password", Syntax.PASSWORD, Category.LDAP, false),
    LDAP_CONTEXTLESS_ROOT(
            "ldap.rootContexts", Syntax.STRING, Category.LDAP, false),
    LDAP_LOGIN_CONTEXTS(
            "ldap.selectableContexts", Syntax.STRING_ARRAY, Category.LDAP, false),
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", Syntax.STRING, Category.LDAP, true),
    USERNAME_SEARCH_FILTER(
            "ldap.usernameSearchFilter", Syntax.STRING, Category.LDAP, true),
    AUTO_ADD_OBJECT_CLASSES(
            "ldap.addObjectClasses", Syntax.STRING_ARRAY, Category.LDAP, false),
    QUERY_MATCH_CHANGE_PASSWORD(
            "password.allowChange.queryMatch", Syntax.STRING, Category.LDAP, true),
    PASSWORD_LAST_UPDATE_ATTRIBUTE(
            "passwordLastUpdateAttribute", Syntax.STRING, Category.LDAP, true),
    LDAP_NAMING_ATTRIBUTE(
            "ldap.namingAttribute", Syntax.STRING, Category.LDAP, true),
    LDAP_PROXY_IDLE_TIMEOUT(
            "ldap.proxy.idleTimeout", Syntax.NUMERIC, Category.LDAP, true),

    // email settings
    EMAIL_SERVER_ADDRESS(
            "email.smtp.address", Syntax.STRING, Category.EMAIL, false),
    EMAIL_USER_MAIL_ATTRIBUTE(
            "email.userMailAttribute", Syntax.STRING, Category.EMAIL, true),
    EMAIL_ADMIN_ALERT_TO(
            "email.adminAlert.toAddress", Syntax.STRING_ARRAY, Category.EMAIL, false),
    EMAIL_ADMIN_ALERT_FROM(
            "email.adminAlert.fromAddress", Syntax.STRING, Category.EMAIL, false),
    EMAIL_CHANGEPASSWORD_FROM(
            "email.changePassword.form", Syntax.LOCALIZED_STRING, Category.EMAIL, false),
    EMAIL_CHANGEPASSWORD_SUBJECT(
            "email.changePassword.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false),
    EMAIL_CHANGEPASSWORD_BODY(
            "email.changePassword.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false),
    EMAIL_CHANGEPASSWORD_BODY_HMTL(
            "email.changePassword.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false),
    EMAIL_NEWUSER_SUBJECT(
            "email.newUser.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false),
    EMAIL_NEWUSER_FROM(
            "email.newUser.from", Syntax.LOCALIZED_STRING, Category.EMAIL, false),
    EMAIL_NEWUSER_BODY(
            "email.newUser.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false),
    EMAIL_NEWUSER_BODY_HTML(
            "email.newUser.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false),
    EMAIL_ACTIVATION_SUBJECT(
            "email.activation.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false),
    EMAIL_ACTIVATION_FROM(
            "email.activation.from", Syntax.LOCALIZED_STRING, Category.EMAIL, false),
    EMAIL_ACTIVATION_BODY(
            "email.activation.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false),
    EMAIL_ACTIVATION_BODY_HTML(
            "email.activation.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false),
    EMAIL_CHALLENGE_TOKEN_FROM(
            "email.challenge.token.from", Syntax.LOCALIZED_STRING, Category.EMAIL, true),
    EMAIL_CHALLENGE_TOKEN_SUBJECT(
            "email.challenge.token.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, true),
    EMAIL_CHALLENGE_TOKEN_BODY(
            "email.challenge.token.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, true),
    EMAIL_CHALLENGE_TOKEN_BODY_HTML(
            "email.challenge.token.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, true),
    EMAIL_ADVANCED_SETTINGS(
            "email.smtp.advancedSettings", Syntax.STRING_ARRAY, Category.EMAIL, false),

    //global password policy settings
    PASSWORD_POLICY_MINIMUM_LENGTH(
            "password.policy.minimumLength", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_LENGTH(
            "password.policy.maximumLength", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_REPEAT(
            "password.policy.maximumRepeat", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT(
            "password.policy.maximumSequentialRepeat", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_ALLOW_NUMERIC(
            "password.policy.allowNumeric", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC(
            "password.policy.allowFirstCharNumeric", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC(
            "password.policy.allowLastCharNumeric", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_NUMERIC(
            "password.policy.maximumNumeric", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_NUMERIC(
            "password.policy.minimumNumeric", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_ALLOW_SPECIAL(
            "password.policy.allowSpecial", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL(
            "password.policy.allowFirstCharSpecial", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL(
            "password.policy.allowLastCharSpecial", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_SPECIAL(
            "password.policy.maximumSpecial", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_SPECIAL(
            "password.policy.minimumSpecial", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_ALPHA(
            "password.policy.maximumAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_ALPHA(
            "password.policy.minimumAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_NON_ALPHA(
            "password.policy.maximumNonAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_NON_ALPHA(
            "password.policy.minimumNonAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_UPPERCASE(
            "password.policy.maximumUpperCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_UPPERCASE(
            "password.policy.minimumUpperCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_LOWERCASE(
            "password.policy.maximumLowerCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_LOWERCASE(
            "password.policy.minimumLowerCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_UNIQUE(
            "password.policy.minimumUnique", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS(
            "password.policy.maximumOldPasswordChars", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_ENABLE_WORDLIST(
            "password.policy.checkWordlist", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_AD_COMPLEXITY(
            "password.policy.ADComplexity", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH(
            "password.policy.regExMatch", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false),
    PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH(
            "password.policy.regExNoMatch", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false),
    PASSWORD_POLICY_DISALLOWED_VALUES(
            "password.policy.disallowedValues", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false),
    PASSWORD_POLICY_DISALLOWED_ATTRIBUTES(
            "password.policy.disallowedAttributes", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false),
    PASSWORD_SHAREDHISTORY_ENABLE(
            "password.sharedHistory.enable", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true),
    PASSWORD_SHAREDHISTORY_MAX_AGE(
            "password.sharedHistory.age", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_MINIMUM_STRENGTH(
            "password.policy.minimumStrength", Syntax.NUMERIC, Category.PASSWORD_POLICY, true),
    PASSWORD_POLICY_CHANGE_MESSAGE(
            "password.policy.changeMessage", Syntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY, false),

    // edirectory settings
    EDIRECTORY_READ_PASSWORD_POLICY(
            "ldap.edirectory.readPasswordPolicies", Syntax.BOOLEAN, Category.EDIRECTORY, true),
    EDIRECTORY_ENABLE_NMAS(
            "ldap.edirectory.enableNmas", Syntax.BOOLEAN, Category.EDIRECTORY, true),
    EDIRECTORY_STORE_NMAS_RESPONSES(
            "ldap.edirectory.storeNmasResponses", Syntax.BOOLEAN, Category.EDIRECTORY, true),
    EDIRECTORY_READ_CHALLENGE_SET(
            "ldap.edirectory.readChallengeSets", Syntax.BOOLEAN, Category.EDIRECTORY, true),
    EDIRECTORY_ALWAYS_USE_PROXY(
            "ldap.edirectory.alwaysUseProxy", Syntax.BOOLEAN, Category.EDIRECTORY, true),

    // intruder settings
    INTRUDER_USER_RESET_TIME(
            "intruder.user.resetTime", Syntax.NUMERIC, Category.INTRUDER, true),
    INTRUDER_USER_MAX_ATTEMPTS(
            "intruder.user.maxAttempts", Syntax.NUMERIC, Category.INTRUDER, true),
    INTRUDER_ADDRESS_RESET_TIME(
            "intruder.address.resetTime", Syntax.NUMERIC, Category.INTRUDER, true),
    INTRUDER_ADDRESS_MAX_ATTEMPTS(
            "intruder.address.maxAttempts", Syntax.NUMERIC, Category.INTRUDER, true),
    INTRUDER_SESSION_MAX_ATTEMPTS(
            "intruder.session.maxAttempts", Syntax.NUMERIC, Category.INTRUDER, true),

    // logger settings
    EVENTS_HEALTH_CHECK_INTERVAL(
            "events.healthCheck.interval", Syntax.NUMERIC, Category.LOGGING, false),
    EVENTS_JAVA_STDOUT_LEVEL(
            "events.java.stdoutLevel", Syntax.STRING, Category.LOGGING, false),
    EVENTS_JAVA_LOG4JCONFIG_FILE(
            "events.java.log4jconfigFile", Syntax.STRING, Category.LOGGING, false),
    EVENTS_PWMDB_MAX_EVENTS(
            "events.pwmDB.maxEvents", Syntax.NUMERIC, Category.LOGGING, true),
    EVENTS_PWMDB_MAX_AGE(
            "events.pwmDB.maxAge", Syntax.NUMERIC, Category.LOGGING, true),
    EVENTS_PWMDB_LOG_LEVEL(
            "events.pwmDB.logLevel", Syntax.STRING, Category.LOGGING, true),
    EVENTS_LDAP_ATTRIBUTE(
            "events.ldap.attribute", Syntax.STRING, Category.LOGGING, false),
    EVENTS_LDAP_MAX_EVENTS(
            "events.ldap.maxEvents", Syntax.NUMERIC, Category.LOGGING, true),
    EVENTS_ALERT_STARTUP(
            "events.alert.startup.enable", Syntax.BOOLEAN, Category.LOGGING, true),
    EVENTS_ALERT_SHUTDOWN(
            "events.alert.shutdown.enable", Syntax.BOOLEAN, Category.LOGGING, true),
    EVENTS_ALERT_INTRUDER_LOCKOUT(
            "events.alert.intruder.enable", Syntax.BOOLEAN, Category.LOGGING, true),
    EVENTS_ALERT_FATAL_EVENT(
            "events.alert.fatalEvent.enable", Syntax.BOOLEAN, Category.LOGGING, true),
    EVENTS_ALERT_CONFIG_MODIFY(
            "events.alert.configModify.enable", Syntax.BOOLEAN, Category.LOGGING, true),
    EVENTS_ALERT_DAILY_STATS(
            "events.alert.dailyStats.enable", Syntax.BOOLEAN, Category.LOGGING, true),

    // recovery settings
    CHALLENGE_FORCE_SETUP(
            "challenge.forceSetup", Syntax.BOOLEAN, Category.RECOVERY, true),
    QUERY_MATCH_SETUP_RESPONSE(
            "challenge.allowSetup.queryMatch", Syntax.STRING, Category.RECOVERY, true),
    CHALLENGE_USER_ATTRIBUTE(
            "challenge.userAttribute", Syntax.STRING, Category.RECOVERY, true),
    CHALLENGE_MIN_RANDOM_SETUP(
            "challenge.minRandomsSetup", Syntax.NUMERIC, Category.RECOVERY, true),
    CHALLENGE_ALLOW_UNLOCK(
            "challenge.allowUnlock", Syntax.BOOLEAN, Category.RECOVERY, true),
    CHALLENGE_STORAGE_HASHED(
            "challenge.storageHashed", Syntax.BOOLEAN, Category.RECOVERY, true),
    CHALLENGE_CASE_INSENSITIVE(
            "challenge.caseInsensitive", Syntax.BOOLEAN, Category.RECOVERY, true),
    CHALLENGE_ALLOW_DUPLICATE_RESPONSES(
            "challenge.allowDuplicateResponses", Syntax.BOOLEAN, Category.RECOVERY, true),
    CHALLENGE_APPLY_WORDLIST(
            "challenge.applyWorldlist", Syntax.BOOLEAN, Category.RECOVERY, true),
    CHALLENGE_SHOW_CONFIRMATION(
            "challenge.showConfirmation", Syntax.BOOLEAN, Category.RECOVERY, true),
    CHALLENGE_REQUIRED_CHALLENGES(
            "challenge.requiredChallenges", Syntax.LOCALIZED_STRING_ARRAY, Category.RECOVERY, false),
    CHALLENGE_RANDOM_CHALLENGES(
            "challenge.randomChallenges", Syntax.LOCALIZED_STRING_ARRAY, Category.RECOVERY, false),
    CHALLENGE_MIN_RANDOM_REQUIRED(
            "challenge.minRandomRequired", Syntax.NUMERIC, Category.RECOVERY, true),
    CHALLENGE_REQUIRED_ATTRIBUTES(
            "challenge.requiredAttributes", Syntax.LOCALIZED_STRING_ARRAY, Category.RECOVERY, false),
    QUERY_MATCH_CHECK_RESPONSES(
            "command.checkResponses.queryMatch", Syntax.STRING, Category.RECOVERY, true),
    CHALLENGE_TOKEN_ENABLE(
            "challenge.token.enable", Syntax.BOOLEAN, Category.RECOVERY, true),
    CHALLENGE_TOKEN_CHARACTERS(
            "challenge.token.characters", Syntax.STRING, Category.RECOVERY, true),
    CHALLENGE_TOKEN_LENGTH(
            "challenge.token.length", Syntax.NUMERIC, Category.RECOVERY, true),

    // new user settings
    NEWUSER_ENABLE(
            "newUser.enable", Syntax.BOOLEAN, Category.NEWUSER, true),
    NEWUSER_CONTEXT(
            "newUser.createContext", Syntax.STRING, Category.NEWUSER, true),
    NEWUSER_FORM(
            "newUser.form", Syntax.LOCALIZED_STRING_ARRAY, Category.NEWUSER, true),
    NEWUSER_UNIQUE_ATTRIBUES(
            "newUser.creationUniqueAttributes", Syntax.STRING_ARRAY, Category.NEWUSER, false),
    NEWUSER_WRITE_ATTRIBUTES(
            "newUser.writeAttributes", Syntax.STRING_ARRAY, Category.NEWUSER, false),

    // activation settings
    ACTIVATE_USER_ENABLE(
            "activateUser.enable", Syntax.BOOLEAN, Category.ACTIVATION, true),
    ACTIVATE_USER_FORM(
            "activateUser.form", Syntax.LOCALIZED_STRING_ARRAY, Category.ACTIVATION, true),
    ACTIVATE_USER_SEARCH_FILTER(
            "activateUser.searchFilter", Syntax.STRING, Category.ACTIVATION, true),
    ACTIVATE_USER_QUERY_MATCH(
            "activateUser.queryMatch", Syntax.STRING, Category.ACTIVATION, true),
    ACTIVATE_USER_WRITE_ATTRIBUTES(
            "activateUser.writeAttributes", Syntax.STRING_ARRAY, Category.ACTIVATION, false),

    // update attributes
    UPDATE_ATTRIBUTES_ENABLE(
            "updateAttributes.enable", Syntax.BOOLEAN, Category.UPDATE, true),
    UPDATE_ATTRIBUTES_QUERY_MATCH(
            "updateAttributes.queryMatch", Syntax.STRING, Category.UPDATE, true),
    UPDATE_ATTRIBUTES_WRITE_ATTRIBUTES(
            "updateAttributes.writeAttributes", Syntax.STRING_ARRAY, Category.UPDATE, false),
    UPDATE_ATTRIBUTES_FORM(
            "updateAttributes.form", Syntax.LOCALIZED_STRING_ARRAY, Category.UPDATE, true),

    // shortcut settings
    SHORTCUT_ENABLE(
            "shortcut.enable", Syntax.BOOLEAN, Category.SHORTCUT, false),
    SHORTCUT_ITEMS(
            "shortcut.items", Syntax.LOCALIZED_STRING_ARRAY, Category.SHORTCUT, false),
    SHORTCUT_HEADER_NAMES(
            "shortcut.httpHeaders", Syntax.STRING_ARRAY, Category.SHORTCUT, false),


    // captcha
    RECAPTCHA_KEY_PUBLIC(
            "captcha.recaptcha.publicKey", Syntax.STRING, Category.CAPTCHA, false),
    RECAPTCHA_KEY_PRIVATE(
            "captcha.recaptcha.privateKey", Syntax.PASSWORD, Category.CAPTCHA, false),
    CAPTCHA_SKIP_PARAM(
            "captcha.skip.param", Syntax.STRING, Category.CAPTCHA, false),
    CAPTCHA_SKIP_COOKIE(
            "captcha.skip.cookie", Syntax.STRING, Category.CAPTCHA, false),

    // advanced
    USE_X_FORWARDED_FOR_HEADER(
            "useXForwardedForHeader", Syntax.BOOLEAN, Category.ADVANCED, true),
    ALLOW_URL_SESSIONS(
            "allowUrlSessions", Syntax.BOOLEAN, Category.ADVANCED, true),
    ENABLE_SESSION_VERIFICATION(
            "enableSessionVerification", Syntax.BOOLEAN, Category.ADVANCED, true),
    FORCE_BASIC_AUTH(
            "forceBasicAuth", Syntax.BOOLEAN, Category.ADVANCED, true),
    EXTERNAL_CHANGE_METHODS(
            "externalChangeMethod", Syntax.STRING_ARRAY, Category.ADVANCED, false),
    EXTERNAL_JUDGE_METHODS(
            "externalJudgeMethod", Syntax.STRING_ARRAY, Category.ADVANCED, false),
    EXTERNAL_RULE_METHODS(
            "externalRuleMethod", Syntax.STRING_ARRAY, Category.ADVANCED, false),
    DISALLOWED_HTTP_INPUTS(
            "disallowedInputs", Syntax.STRING_ARRAY, Category.ADVANCED, false),
    LDAP_CHAI_SETTINGS(
            "ldapChaiSettings", Syntax.STRING_ARRAY, Category.ADVANCED, false),
    WORDLIST_CASE_SENSITIVE(
            "wordlistCaseSensitive", Syntax.BOOLEAN, Category.ADVANCED, true),
    PWMDB_LOCATION(
            "pwmDb.location", Syntax.STRING, Category.ADVANCED, true),
    PWMDB_IMPLEMENTATION(
            "pwmDb.implementation", Syntax.STRING, Category.ADVANCED, true),
    PWMDB_INIT_STRING(
            "pwmDb.initParameters", Syntax.STRING_ARRAY, Category.ADVANCED, false),
    PWM_INSTANCE_NAME(
            "pwmInstanceName", Syntax.STRING, Category.ADVANCED, false),

    ;
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

    private static class Static {
        private static final String RESOURCE_MISSING = "--RESOURCE MISSING--";
    }

    private final String key;
    private final Syntax syntax;
    private final Category category;
    private final boolean required;

// --------------------------- CONSTRUCTORS ---------------------------

    PwmSetting(
            final String key,
            final Syntax syntax,
            final Category category,
            final boolean required
    )
    {
        this.key = key;
        this.syntax = syntax;
        this.category = category;
        this.required = required;
    }

// --------------------- GETTER / SETTER METHODS ---------------------


    public String getKey()
    {
        return key;
    }

    public boolean isConfidential()
    {
        return Syntax.PASSWORD == this.getSyntax();
    }

    public Category getCategory() {
        return category;
    }

    public Syntax getSyntax() {
        return syntax;
    }



    // -------------------------- OTHER METHODS --------------------------

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

    public boolean isRequired() {
        return required;
    }

    public Pattern getRegExPattern() {
        final String value = readProps("REGEX_" + this.getKey(), Locale.getDefault());

        if (value == null || value.length() < 1 || Static.RESOURCE_MISSING.equals(value) ) {
            return Pattern.compile(".*");
        }

        return Pattern.compile(value);
    }

    private static String readProps(final String key, final Locale locale) {
        try {
            final ResourceBundle bundle = ResourceBundle.getBundle(PwmSetting.class.getName(), locale);
            return bundle.getString(key);
        } catch (Exception e) {
            return Static.RESOURCE_MISSING;
        }
    }

    public enum Syntax {
        STRING,
        STRING_ARRAY,
        LOCALIZED_STRING,
        LOCALIZED_TEXT_AREA,
        LOCALIZED_STRING_ARRAY,
        PASSWORD,
        NUMERIC,
        BOOLEAN,
    }

    public enum Category {
        USER_INTERFACE,
        GENERAL,
        LDAP,
        EMAIL,
        PASSWORD_POLICY,
        EDIRECTORY,
        INTRUDER,
        LOGGING,
        RECOVERY,
        NEWUSER,
        ACTIVATION,
        UPDATE,
        SHORTCUT,
        CAPTCHA,
        ADVANCED
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

    public static PwmSetting forKey(final String key) {
        for (final PwmSetting loopSetting : values()) {
            if (loopSetting.getKey().equals(key)) {
                return loopSetting;
            }
        }
        return null;
    }
}

