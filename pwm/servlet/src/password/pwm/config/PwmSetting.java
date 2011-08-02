/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import com.google.gson.reflect.TypeToken;
import password.pwm.PwmConstants;

import java.util.*;
import java.util.regex.Pattern;


/**
 * PwmConfiguration settings.
 *
 * @author Jason D. Rivard
 */
public enum PwmSetting {
    // general settings
    REQUIRE_HTTPS(
            "pwm.requireHTTPS", Syntax.BOOLEAN, Category.GENERAL, true, Level.BASIC),
    URL_FORWARD(
            "pwm.forwardURL", Syntax.STRING, Category.GENERAL, true, Level.BASIC),
    URL_LOGOUT(
            "pwm.logoutURL", Syntax.STRING, Category.GENERAL, false, Level.BASIC),
    LOGOUT_AFTER_PASSWORD_CHANGE(
            "logoutAfterPasswordChange", Syntax.BOOLEAN, Category.GENERAL, true, Level.ADVANCED),
    PASSWORD_EXPIRE_PRE_TIME(
            "expirePreTime", Syntax.NUMERIC, Category.GENERAL, true, Level.BASIC),
    PASSWORD_EXPIRE_WARN_TIME(
            "expireWarnTime", Syntax.NUMERIC, Category.GENERAL, true, Level.BASIC),
    EXPIRE_CHECK_DURING_AUTH(
            "expireCheckDuringAuth", Syntax.BOOLEAN, Category.GENERAL, true, Level.ADVANCED),
    PASSWORD_SYNC_MIN_WAIT_TIME(
            "passwordSyncMinWaitTime", Syntax.NUMERIC, Category.GENERAL, true, Level.ADVANCED),
    PASSWORD_SYNC_MAX_WAIT_TIME(
            "passwordSyncMaxWaitTime", Syntax.NUMERIC, Category.GENERAL, true, Level.BASIC),
    PASSWORD_REQUIRE_CURRENT(
            "password.change.requireCurrent", Syntax.BOOLEAN, Category.GENERAL, true, Level.BASIC),
    WORDLIST_FILENAME(
            "pwm.wordlist.location", Syntax.STRING, Category.GENERAL, false, Level.BASIC),
    SEEDLIST_FILENAME(
            "pwm.seedlist.location", Syntax.STRING, Category.GENERAL, false, Level.ADVANCED),
    GOOGLE_ANAYLTICS_TRACKER(
            "google.analytics.tracker", Syntax.STRING, Category.GENERAL, false, Level.BASIC),
    PWM_INSTANCE_NAME(
            "pwmInstanceName", Syntax.STRING, Category.GENERAL, false, Level.ADVANCED),

    // user interface
    DISPLAY_SHOW_DETAILED_ERRORS(
            "display.showDetailedErrors", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    PASSWORD_SHOW_AUTOGEN(
            "password.showAutoGen", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    PASSWORD_SHOW_STRENGTH_METER(
            "password.showStrengthMeter", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_PASSWORD_GUIDE_TEXT(
            "display.password.guideText", Syntax.LOCALIZED_TEXT_AREA, Category.USER_INTERFACE, false, Level.BASIC),
    PASSWORD_CHANGE_AGREEMENT_MESSAGE(
            "display.password.changeAgreement", Syntax.LOCALIZED_TEXT_AREA, Category.USER_INTERFACE, false, Level.BASIC),
    DISPLAY_SHOW_HIDE_PASSWORD_FIELDS(
            "display.showHidePasswordFields", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_CANCEL_BUTTON(
            "display.showCancelButton", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_RESET_BUTTON(
            "display.showResetButton", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_PASSWORD_HISTORY(
            "display.passwordHistory", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_ACCOUNT_INFORMATION(
            "display.accountInformation", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_ADMIN_LINK_ENABLE(
            "display.adminIndexLink", Syntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),


    //ldap directory
    LDAP_SERVER_URLS(
            "ldap.serverUrls", Syntax.STRING_ARRAY, Category.LDAP, true, Level.BASIC),
    LDAP_PROMISCUOUS_SSL(
            "ldap.promiscuousSSL", Syntax.BOOLEAN, Category.LDAP, true, Level.BASIC),
    LDAP_PROXY_USER_DN(
            "ldap.proxy.username", Syntax.STRING, Category.LDAP, true, Level.BASIC),
    LDAP_PROXY_USER_PASSWORD(
            "ldap.proxy.password", Syntax.PASSWORD, Category.LDAP, true, Level.BASIC),
    LDAP_CONTEXTLESS_ROOT(
            "ldap.rootContexts", Syntax.STRING, Category.LDAP, false, Level.BASIC),
    LDAP_LOGIN_CONTEXTS(
            "ldap.selectableContexts", Syntax.STRING_ARRAY, Category.LDAP, false, Level.ADVANCED),
    LDAP_TEST_USER_DN(
            "ldap.testuser.username", Syntax.STRING, Category.LDAP, false, Level.BASIC),
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", Syntax.STRING, Category.LDAP, true, Level.BASIC),
    USERNAME_SEARCH_FILTER(
            "ldap.usernameSearchFilter", Syntax.STRING, Category.LDAP, true, Level.ADVANCED),
    LDAP_READ_PASSWORD_POLICY(
            "ldap.readPasswordPolicies", Syntax.BOOLEAN, Category.LDAP, true, Level.ADVANCED),
    AUTO_ADD_OBJECT_CLASSES(
            "ldap.addObjectClasses", Syntax.STRING_ARRAY, Category.LDAP, false, Level.ADVANCED),
    DEFAULT_OBJECT_CLASSES(
            "ldap.defaultObjectClasses", Syntax.STRING_ARRAY, Category.LDAP, false, Level.ADVANCED),
    QUERY_MATCH_CHANGE_PASSWORD(
            "password.allowChange.queryMatch", Syntax.STRING, Category.LDAP, true, Level.ADVANCED),
    PASSWORD_LAST_UPDATE_ATTRIBUTE(
            "passwordLastUpdateAttribute", Syntax.STRING, Category.LDAP, false, Level.ADVANCED),
    LDAP_NAMING_ATTRIBUTE(
            "ldap.namingAttribute", Syntax.STRING, Category.LDAP, true, Level.ADVANCED),
    LDAP_PROXY_IDLE_TIMEOUT(
            "ldap.proxy.idleTimeout", Syntax.NUMERIC, Category.LDAP, true, Level.ADVANCED),
    LDAP_GUID_ATTRIBUTE(
            "ldap.guidAttribute", Syntax.STRING, Category.LDAP, true, Level.ADVANCED),
    LDAP_GUID_AUTO_ADD(
            "ldap.guid.autoAddValue", Syntax.BOOLEAN, Category.LDAP, true, Level.ADVANCED),

    // email settings
    EMAIL_SERVER_ADDRESS(
            "email.smtp.address", Syntax.STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_USER_MAIL_ATTRIBUTE(
            "email.userMailAttribute", Syntax.STRING, Category.EMAIL, true, Level.ADVANCED),
    EMAIL_MAX_QUEUE_AGE(
            "email.queueMaxAge", Syntax.NUMERIC, Category.EMAIL, true, Level.ADVANCED),
    EMAIL_ADMIN_ALERT_TO(
            "email.adminAlert.toAddress", Syntax.STRING_ARRAY, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_ADMIN_ALERT_FROM(
            "email.adminAlert.fromAddress", Syntax.STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHANGEPASSWORD_FROM(
            "email.changePassword.form", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHANGEPASSWORD_SUBJECT(
            "email.changePassword.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHANGEPASSWORD_BODY(
            "email.changePassword.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHANGEPASSWORD_BODY_HMTL(
            "email.changePassword.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_SUBJECT(
            "email.newUser.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_NEWUSER_FROM(
            "email.newUser.from", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_NEWUSER_BODY(
            "email.newUser.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_NEWUSER_BODY_HTML(
            "email.newUser.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_GUEST_SUBJECT(
            "email.guest.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_GUEST_FROM(
            "email.guest.from", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_GUEST_BODY(
            "email.guest.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_GUEST_BODY_HTML(
            "email.guest.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_SUBJECT(
            "email.updateguest.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_FROM(
            "email.updateguest.from", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_BODY(
            "email.updateguest.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_BODY_HTML(
            "email.updateguest.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_ACTIVATION_SUBJECT(
            "email.activation.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_ACTIVATION_FROM(
            "email.activation.from", Syntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_ACTIVATION_BODY(
            "email.activation.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_ACTIVATION_BODY_HTML(
            "email.activation.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_CHALLENGE_TOKEN_FROM(
            "email.challenge.token.from", Syntax.LOCALIZED_STRING, Category.EMAIL, true, Level.ADVANCED),
    EMAIL_CHALLENGE_TOKEN_SUBJECT(
            "email.challenge.token.subject", Syntax.LOCALIZED_STRING, Category.EMAIL, true, Level.ADVANCED),
    EMAIL_CHALLENGE_TOKEN_BODY(
            "email.challenge.token.plainBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, true, Level.ADVANCED),
    EMAIL_CHALLENGE_TOKEN_BODY_HTML(
            "email.challenge.token.htmlBody", Syntax.LOCALIZED_TEXT_AREA, Category.EMAIL, true, Level.ADVANCED),

    // sms settings
    SMS_USER_PHONE_ATTRIBUTE(
            "sms.userSmsAttribute", Syntax.STRING, Category.SMS, true, Level.BASIC),
    SMS_MAX_QUEUE_AGE(
            "sms.queueMaxAge", Syntax.NUMERIC, Category.SMS, true, Level.ADVANCED),
    SMS_GATEWAY_URL(
            "sms.gatewayURL", Syntax.STRING, Category.SMS, true, Level.BASIC),
    SMS_GATEWAY_USER(
            "sms.gatewayUser", Syntax.STRING, Category.SMS, false, Level.BASIC),
    SMS_GATEWAY_PASSWORD(
            "sms.gatewayPassword", Syntax.PASSWORD, Category.SMS, false, Level.BASIC),
    SMS_GATEWAY_METHOD(
            "sms.gatewayMethod", Syntax.SELECT, Category.SMS, true, Level.BASIC),
    SMS_GATEWAY_AUTHMETHOD(
            "sms.gatewayAuthMethod", Syntax.SELECT, Category.SMS, true, Level.ADVANCED),
    SMS_REQUEST_DATA(
            "sms.requestData", Syntax.LOCALIZED_TEXT_AREA, Category.SMS, false, Level.ADVANCED),
    SMS_REQUEST_CONTENT_TYPE(
            "sms.requestContentType", Syntax.STRING, Category.SMS, false, Level.ADVANCED),
    SMS_REQUEST_CONTENT_ENCODING(
            "sms.requestContentEncoding", Syntax.SELECT, Category.SMS, true, Level.ADVANCED),
    SMS_GATEWAY_REQUEST_HEADERS(
            "sms.httpRequestHeaders", Syntax.STRING_ARRAY, Category.SMS, false, Level.ADVANCED),
    SMS_MAX_TEXT_LENGTH(
            "sms.maxTextLength", Syntax.NUMERIC, Category.SMS, true, Level.ADVANCED),
    SMS_RESPONSE_OK_REGEX(
            "sms.responseOkRegex", Syntax.STRING_ARRAY, Category.SMS, false, Level.ADVANCED),
    SMS_SENDER_ID(
            "sms.senderID", Syntax.STRING, Category.SMS, false, Level.BASIC),
    SMS_PHONE_NUMBER_FORMAT(
            "sms.phoneNumberFormat", Syntax.SELECT, Category.SMS, true, Level.ADVANCED),
    SMS_DEFAULT_COUNTRY_CODE(
            "sms.defaultCountryCode", Syntax.NUMERIC, Category.SMS, true, Level.ADVANCED),
    SMS_REQUESTID_CHARS(
            "sms.requestId.characters", Syntax.STRING, Category.SMS, true, Level.ADVANCED),
    SMS_REQUESTID_LENGTH(
            "sms.requestId.length", Syntax.NUMERIC, Category.SMS, true, Level.ADVANCED),
    SMS_CHALLENGE_TOKEN_TEXT(
            "sms.challenge.token.message", Syntax.LOCALIZED_STRING, Category.SMS, true, Level.ADVANCED),

    //global password policy settings
    PASSWORD_POLICY_MINIMUM_LENGTH(
            "password.policy.minimumLength", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_LENGTH(
            "password.policy.maximumLength", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_REPEAT(
            "password.policy.maximumRepeat", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT(
            "password.policy.maximumSequentialRepeat", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_NUMERIC(
            "password.policy.allowNumeric", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC(
            "password.policy.allowFirstCharNumeric", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC(
            "password.policy.allowLastCharNumeric", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_NUMERIC(
            "password.policy.maximumNumeric", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_NUMERIC(
            "password.policy.minimumNumeric", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_SPECIAL(
            "password.policy.allowSpecial", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL(
            "password.policy.allowFirstCharSpecial", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL(
            "password.policy.allowLastCharSpecial", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_SPECIAL(
            "password.policy.maximumSpecial", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_SPECIAL(
            "password.policy.minimumSpecial", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_ALPHA(
            "password.policy.maximumAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_ALPHA(
            "password.policy.minimumAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_NON_ALPHA(
            "password.policy.maximumNonAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_NON_ALPHA(
            "password.policy.minimumNonAlpha", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_UPPERCASE(
            "password.policy.maximumUpperCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_UPPERCASE(
            "password.policy.minimumUpperCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_LOWERCASE(
            "password.policy.maximumLowerCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_LOWERCASE(
            "password.policy.minimumLowerCase", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_UNIQUE(
            "password.policy.minimumUnique", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS(
            "password.policy.maximumOldPasswordChars", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ENABLE_WORDLIST(
            "password.policy.checkWordlist", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_AD_COMPLEXITY(
            "password.policy.ADComplexity", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH(
            "password.policy.regExMatch", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.ADVANCED),
    PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH(
            "password.policy.regExNoMatch", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.ADVANCED),
    PASSWORD_POLICY_DISALLOWED_VALUES(
            "password.policy.disallowedValues", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.BASIC),
    PASSWORD_POLICY_DISALLOWED_ATTRIBUTES(
            "password.policy.disallowedAttributes", Syntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.ADVANCED),
    PASSWORD_SHAREDHISTORY_ENABLE(
            "password.sharedHistory.enable", Syntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.ADVANCED),
    PASSWORD_SHAREDHISTORY_MAX_AGE(
            "password.sharedHistory.age", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.ADVANCED),
    PASSWORD_POLICY_MINIMUM_STRENGTH(
            "password.policy.minimumStrength", Syntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_CHANGE_MESSAGE(
            "password.policy.changeMessage", Syntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY, false, Level.BASIC),


    // intruder settings
    INTRUDER_USER_RESET_TIME(
            "intruder.user.resetTime", Syntax.NUMERIC, Category.INTRUDER, true, Level.BASIC),
    INTRUDER_USER_MAX_ATTEMPTS(
            "intruder.user.maxAttempts", Syntax.NUMERIC, Category.INTRUDER, true, Level.BASIC ),
    INTRUDER_ADDRESS_RESET_TIME(
            "intruder.address.resetTime", Syntax.NUMERIC, Category.INTRUDER, true, Level.BASIC),
    INTRUDER_ADDRESS_MAX_ATTEMPTS(
            "intruder.address.maxAttempts", Syntax.NUMERIC, Category.INTRUDER, true, Level.BASIC),
    INTRUDER_SESSION_MAX_ATTEMPTS(
            "intruder.session.maxAttempts", Syntax.NUMERIC, Category.INTRUDER, true, Level.BASIC),
    // captcha
    RECAPTCHA_KEY_PUBLIC(
            "captcha.recaptcha.publicKey", Syntax.STRING, Category.INTRUDER, false, Level.BASIC),
    RECAPTCHA_KEY_PRIVATE(
            "captcha.recaptcha.privateKey", Syntax.PASSWORD, Category.INTRUDER, false, Level.BASIC),
    CAPTCHA_SKIP_PARAM(
            "captcha.skip.param", Syntax.STRING, Category.INTRUDER, false, Level.ADVANCED),
    CAPTCHA_SKIP_COOKIE(
            "captcha.skip.cookie", Syntax.STRING, Category.INTRUDER, false, Level.ADVANCED),


    // logger settings
    EVENTS_HEALTH_CHECK_MIN_INTERVAL(
            "events.healthCheck.minInterval", Syntax.NUMERIC, Category.LOGGING, false, Level.ADVANCED),
    EVENTS_JAVA_STDOUT_LEVEL(
            "events.java.stdoutLevel", Syntax.SELECT, Category.LOGGING, false, Level.BASIC),
    EVENTS_JAVA_LOG4JCONFIG_FILE(
            "events.java.log4jconfigFile", Syntax.STRING, Category.LOGGING, false, Level.ADVANCED),
    EVENTS_PWMDB_MAX_EVENTS(
            "events.pwmDB.maxEvents", Syntax.NUMERIC, Category.LOGGING, true, Level.ADVANCED),
    EVENTS_PWMDB_MAX_AGE(
            "events.pwmDB.maxAge", Syntax.NUMERIC, Category.LOGGING, true, Level.ADVANCED),
    EVENTS_PWMDB_LOG_LEVEL(
            "events.pwmDB.logLevel", Syntax.SELECT, Category.LOGGING, true, Level.BASIC),
    EVENTS_LDAP_ATTRIBUTE(
            "events.ldap.attribute", Syntax.STRING, Category.LOGGING, false, Level.ADVANCED),
    EVENTS_LDAP_MAX_EVENTS(
            "events.ldap.maxEvents", Syntax.NUMERIC, Category.LOGGING, true, Level.ADVANCED),
    EVENTS_ALERT_STARTUP(
            "events.alert.startup.enable", Syntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_SHUTDOWN(
            "events.alert.shutdown.enable", Syntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_INTRUDER_LOCKOUT(
            "events.alert.intruder.enable", Syntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_FATAL_EVENT(
            "events.alert.fatalEvent.enable", Syntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_CONFIG_MODIFY(
            "events.alert.configModify.enable", Syntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_DAILY_SUMMARY(
            "events.alert.dailySummary.enable", Syntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),


    // challenge policy
    CHALLENGE_ENABLE(
            "challenge.enable", Syntax.BOOLEAN, Category.CHALLENGE, false, Level.BASIC),
    CHALLENGE_FORCE_SETUP(
            "challenge.forceSetup", Syntax.BOOLEAN, Category.CHALLENGE, true, Level.ADVANCED),
    CHALLENGE_RANDOM_CHALLENGES(
            "challenge.randomChallenges", Syntax.LOCALIZED_STRING_ARRAY, Category.CHALLENGE, false, Level.BASIC),
    CHALLENGE_REQUIRED_CHALLENGES(
            "challenge.requiredChallenges", Syntax.LOCALIZED_STRING_ARRAY, Category.CHALLENGE, false, Level.BASIC),
    CHALLENGE_MIN_RANDOM_REQUIRED(
            "challenge.minRandomRequired", Syntax.NUMERIC, Category.CHALLENGE, true, Level.BASIC),
    CHALLENGE_MIN_RANDOM_SETUP(
            "challenge.minRandomsSetup", Syntax.NUMERIC, Category.CHALLENGE, true, Level.BASIC),
    CHALLENGE_SHOW_CONFIRMATION(
            "challenge.showConfirmation", Syntax.BOOLEAN, Category.CHALLENGE, true, Level.BASIC),
    CHALLENGE_CASE_INSENSITIVE(
            "challenge.caseInsensitive", Syntax.BOOLEAN, Category.CHALLENGE, true, Level.ADVANCED),
    CHALLENGE_ALLOW_DUPLICATE_RESPONSES(
            "challenge.allowDuplicateResponses", Syntax.BOOLEAN, Category.CHALLENGE, true, Level.ADVANCED),
    CHALLENGE_APPLY_WORDLIST(
            "challenge.applyWorldlist", Syntax.BOOLEAN, Category.CHALLENGE, true, Level.ADVANCED),
    QUERY_MATCH_SETUP_RESPONSE(
            "challenge.allowSetup.queryMatch", Syntax.STRING, Category.CHALLENGE, true, Level.ADVANCED),
    QUERY_MATCH_CHECK_RESPONSES(
            "command.checkResponses.queryMatch", Syntax.STRING, Category.CHALLENGE, true, Level.ADVANCED),


    // recovery settings
    FORGOTTEN_PASSWORD_ENABLE(
            "recovery.enable", Syntax.BOOLEAN, Category.RECOVERY, false, Level.BASIC),
    FORGOTTEN_PASSWORD_READ_PREFERENCE(
            "recovery.response.readPreference", Syntax.SELECT, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_USER_ATTRIBUTE(
            "challenge.userAttribute", Syntax.STRING, Category.RECOVERY, false, Level.BASIC),
    RESPONSE_STORAGE_DB(
            "responses.storage.db", Syntax.BOOLEAN, Category.RECOVERY, true, Level.BASIC),
    RESPONSE_STORAGE_PWMDB(
            "responses.storage.pwmDB", Syntax.BOOLEAN, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_ALLOW_UNLOCK(
            "challenge.allowUnlock", Syntax.BOOLEAN, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_STORAGE_HASHED(
            "challenge.storageHashed", Syntax.BOOLEAN, Category.RECOVERY, true, Level.ADVANCED),
    CHALLENGE_REQUIRED_ATTRIBUTES(
            "challenge.requiredAttributes", Syntax.LOCALIZED_STRING_ARRAY, Category.RECOVERY, false, Level.BASIC),
    CHALLENGE_REQUIRE_RESPONSES(
            "challenge.requireResponses", Syntax.BOOLEAN, Category.RECOVERY, false, Level.BASIC),
    CHALLENGE_TOKEN_ENABLE(
            "challenge.token.enable", Syntax.BOOLEAN, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_TOKEN_SEND_METHOD(
            "challenge.token.sendMethod", Syntax.SELECT, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_TOKEN_CHARACTERS(
            "challenge.token.characters", Syntax.STRING, Category.RECOVERY, true, Level.ADVANCED),
    CHALLENGE_TOKEN_LENGTH(
            "challenge.token.length", Syntax.NUMERIC, Category.RECOVERY, true, Level.ADVANCED),

    // forgotten username
    FORGOTTEN_USERNAME_ENABLE(
            "forgottenUsername.enable", Syntax.BOOLEAN, Category.FORGOTTEN_USERNAME, true, Level.BASIC),
    FORGOTTEN_USERNAME_FORM(
            "forgottenUsername.form", Syntax.LOCALIZED_STRING_ARRAY, Category.FORGOTTEN_USERNAME, true, Level.BASIC),
    FORGOTTEN_USERNAME_SEARCH_FILTER(
            "forgottenUsername.searchFilter", Syntax.STRING, Category.FORGOTTEN_USERNAME, true, Level.BASIC),
    FORGOTTEN_USERNAME_USERNAME_ATTRIBUTE(
            "forgottenUsername.usernameAttribute", Syntax.STRING, Category.FORGOTTEN_USERNAME, true, Level.BASIC),


    // new user settings
    NEWUSER_ENABLE(
            "newUser.enable", Syntax.BOOLEAN, Category.NEWUSER, true, Level.BASIC),
    NEWUSER_CONTEXT(
            "newUser.createContext", Syntax.STRING, Category.NEWUSER, true, Level.BASIC),
    NEWUSER_FORM(
            "newUser.form", Syntax.LOCALIZED_STRING_ARRAY, Category.NEWUSER, true, Level.BASIC),
    NEWUSER_UNIQUE_ATTRIBUES(
            "newUser.creationUniqueAttributes", Syntax.STRING_ARRAY, Category.NEWUSER, false, Level.BASIC),
    NEWUSER_WRITE_ATTRIBUTES(
            "newUser.writeAttributes", Syntax.STRING_ARRAY, Category.NEWUSER, false, Level.BASIC),

    // guest settings
    GUEST_ENABLE(
            "guest.enable", Syntax.BOOLEAN, Category.GUEST, true, Level.BASIC),
    GUEST_HIDDEN(
            "guest.hidden", Syntax.BOOLEAN, Category.GUEST, true, Level.BASIC),
    GUEST_CONTEXT(
            "guest.createContext", Syntax.STRING, Category.GUEST, true, Level.BASIC),
    GUEST_ADMIN_GROUP(
            "guest.adminGroup", Syntax.STRING, Category.GUEST, true, Level.ADVANCED),
    GUEST_FORM(
            "guest.form", Syntax.LOCALIZED_STRING_ARRAY, Category.GUEST, true, Level.ADVANCED),
    GUEST_UNIQUE_ATTRIBUTES(
            "guest.creationUniqueAttributes", Syntax.STRING_ARRAY, Category.GUEST, false, Level.ADVANCED),
    GUEST_WRITE_ATTRIBUTES(
            "guest.writeAttributes", Syntax.STRING_ARRAY, Category.GUEST, false, Level.ADVANCED),
    GUEST_ADMIN_ATTRIBUTE(
            "guest.adminAttribute", Syntax.STRING, Category.GUEST, false, Level.ADVANCED),
    GUEST_EDIT_ORIG_ADMIN_ONLY(
            "guest.editOriginalAdminOnly", Syntax.BOOLEAN, Category.GUEST, true, Level.ADVANCED),
    GUEST_MAX_VALID_DAYS(
            "guest.maxValidDays", Syntax.NUMERIC, Category.GUEST, true, Level.ADVANCED),
    GUEST_EXPIRATION_ATTRIBUTE (
            "guest.expirationAttribute", Syntax.STRING, Category.GUEST, false, Level.ADVANCED),

    // activation settings
    ACTIVATE_USER_ENABLE(
            "activateUser.enable", Syntax.BOOLEAN, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_FORM(
            "activateUser.form", Syntax.LOCALIZED_STRING_ARRAY, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_SEARCH_FILTER(
            "activateUser.searchFilter", Syntax.STRING, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_QUERY_MATCH(
            "activateUser.queryMatch", Syntax.STRING, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_PRE_WRITE_ATTRIBUTES(
            "activateUser.writePreAttributes", Syntax.STRING_ARRAY, Category.ACTIVATION, false, Level.BASIC),
    ACTIVATE_USER_POST_WRITE_ATTRIBUTES(
            "activateUser.writePostAttributes", Syntax.STRING_ARRAY, Category.ACTIVATION, false, Level.BASIC),

    // update profile
    UPDATE_PROFILE_ENABLE(
            "updateAttributes.enable", Syntax.BOOLEAN, Category.UPDATE, true, Level.BASIC),
    UPDATE_PROFILE_QUERY_MATCH(
            "updateAttributes.queryMatch", Syntax.STRING, Category.UPDATE, true, Level.BASIC),
    UPDATE_PROFILE_WRITE_ATTRIBUTES(
            "updateAttributes.writeAttributes", Syntax.STRING_ARRAY, Category.UPDATE, false, Level.BASIC),
    UPDATE_PROFILE_FORM(
            "updateAttributes.form", Syntax.LOCALIZED_STRING_ARRAY, Category.UPDATE, true, Level.BASIC),
    UPDATE_PROFILE_CHECK_QUERY_MATCH(
            "updateAttributes.check.queryMatch", Syntax.STRING, Category.UPDATE, false, Level.BASIC),

    // shortcut settings
    SHORTCUT_ENABLE(
            "shortcut.enable", Syntax.BOOLEAN, Category.SHORTCUT, false, Level.BASIC),
    SHORTCUT_ITEMS(
            "shortcut.items", Syntax.LOCALIZED_STRING_ARRAY, Category.SHORTCUT, false, Level.BASIC),
    SHORTCUT_HEADER_NAMES(
            "shortcut.httpHeaders", Syntax.STRING_ARRAY, Category.SHORTCUT, false, Level.ADVANCED),

    // peoplesearch settings
    PEOPLE_SEARCH_ENABLE(
            "peopleSearch.enable", Syntax.BOOLEAN, Category.PEOPLE_SEARCH, false, Level.BASIC),
    PEOPLE_SEARCH_QUERY_MATCH(
            "peopleSearch.queryMatch", Syntax.STRING, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_SEARCH_FILTER(
            "peopleSearch.searchFilter", Syntax.STRING, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_SEARCH_BASE(
            "peopleSearch.searchBase", Syntax.STRING, Category.PEOPLE_SEARCH, false, Level.BASIC),
    PEOPLE_SEARCH_RESULT_FORM(
            "peopleSearch.result.form", Syntax.LOCALIZED_STRING_ARRAY, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_RESULT_LIMIT(
            "peopleSearch.result.limit", Syntax.NUMERIC, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_USE_PROXY(
            "peopleSearch.useProxy", Syntax.BOOLEAN, Category.PEOPLE_SEARCH, true, Level.ADVANCED),

    // edirectory settings
    EDIRECTORY_ENABLE_NMAS(
            "ldap.edirectory.enableNmas", Syntax.BOOLEAN, Category.EDIRECTORY, true, Level.BASIC),
    EDIRECTORY_STORE_NMAS_RESPONSES(
            "ldap.edirectory.storeNmasResponses", Syntax.BOOLEAN, Category.EDIRECTORY, true, Level.ADVANCED),
    EDIRECTORY_READ_CHALLENGE_SET(
            "ldap.edirectory.readChallengeSets", Syntax.BOOLEAN, Category.EDIRECTORY, true, Level.ADVANCED),
    EDIRECTORY_PWD_MGT_WEBSERVICE_URL(
            "ldap.edirectory.ws.pwdMgtURL", Syntax.STRING, Category.EDIRECTORY, false, Level.ADVANCED),
    EDIRECTORY_ALWAYS_USE_PROXY(
            "ldap.edirectory.alwaysUseProxy", Syntax.BOOLEAN, Category.EDIRECTORY, true, Level.ADVANCED),


    // advanced
    USE_X_FORWARDED_FOR_HEADER(
            "useXForwardedForHeader", Syntax.BOOLEAN, Category.MISC, true, Level.ADVANCED),
    ALLOW_URL_SESSIONS(
            "allowUrlSessions", Syntax.BOOLEAN, Category.MISC, true, Level.ADVANCED),
    ENABLE_SESSION_VERIFICATION(
            "enableSessionVerification", Syntax.BOOLEAN, Category.MISC, true, Level.ADVANCED),
    FORCE_BASIC_AUTH(
            "forceBasicAuth", Syntax.BOOLEAN, Category.MISC, true, Level.ADVANCED),
    REVERSE_DNS_ENABLE(
            "network.reverseDNS.enable", Syntax.BOOLEAN, Category.MISC, true, Level.ADVANCED),
    EXTERNAL_CHANGE_METHODS(
            "externalChangeMethod", Syntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    EXTERNAL_JUDGE_METHODS(
            "externalJudgeMethod", Syntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    EXTERNAL_RULE_METHODS(
            "externalRuleMethod", Syntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    DISALLOWED_HTTP_INPUTS(
            "disallowedInputs", Syntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    LDAP_CHAI_SETTINGS(
            "ldapChaiSettings", Syntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    WORDLIST_CASE_SENSITIVE(
            "wordlistCaseSensitive", Syntax.BOOLEAN, Category.MISC, true, Level.BASIC),
    PWMDB_LOCATION(
            "pwmDb.location", Syntax.STRING, Category.MISC, true, Level.BASIC),
    PWMDB_IMPLEMENTATION(
            "pwmDb.implementation", Syntax.STRING, Category.MISC, true, Level.ADVANCED),
    PWMDB_INIT_STRING(
            "pwmDb.initParameters", Syntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    EMAIL_ADVANCED_SETTINGS(
            "email.smtp.advancedSettings", Syntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    HTTP_PROXY_URL(
            "http.proxy.url", Syntax.STRING, Category.MISC, false, Level.ADVANCED),
    HIDE_CONFIGURATION_HEALTH_WARNINGS(
            "display.hideConfigHealthWarnings", Syntax.BOOLEAN, Category.MISC, false, Level.ADVANCED),
    CAS_CLEAR_PASS_URL(
            "cas.clearPassUrl", Syntax.STRING, Category.MISC, false, Level.BASIC),

    DATABASE_CLASS(
            "db.classname", Syntax.STRING, Category.DATABASE, false, Level.BASIC),
    DATABASE_URL(
            "db.url", Syntax.STRING, Category.DATABASE, false, Level.BASIC),
    DATABASE_USERNAME(
            "db.username", Syntax.STRING, Category.DATABASE, false, Level.BASIC),
    DATABASE_PASSWORD(
            "db.password", Syntax.PASSWORD, Category.DATABASE, false, Level.BASIC),

    ;

// ------------------------------ STATICS ------------------------------

    private static final Map<Category, List<PwmSetting>> VALUES_BY_CATEGORY;

    static {
        final Map<Category, List<PwmSetting>> returnMap = new LinkedHashMap<Category, List<PwmSetting>>();

        //setup nested lists
        for (final Category category : Category.values()) returnMap.put(category, new ArrayList<PwmSetting>());

        //populate map
        for (final PwmSetting setting : values()) returnMap.get(setting.getCategory()).add(setting);

        //make nested lists unmodifiable
        for (final Category category : Category.values())
            returnMap.put(category, Collections.unmodifiableList(returnMap.get(category)));

        //assign unmodifiable list
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
    private final Level level;

// --------------------------- CONSTRUCTORS ---------------------------

    PwmSetting(
            final String key,
            final Syntax syntax,
            final Category category,
            final boolean required,
            final Level level
    ) {
        this.key = key;
        this.syntax = syntax;
        this.category = category;
        this.required = required;
        this.level = level;
    }

// --------------------- GETTER / SETTER METHODS ---------------------


    public String getKey() {
        return key;
    }

    public boolean isConfidential() {
        return Syntax.PASSWORD == this.getSyntax();
    }

    public Category getCategory() {
        return category;
    }

    public Syntax getSyntax() {
        return syntax;
    }


    // -------------------------- OTHER METHODS --------------------------

    public String getDefaultValue(final Template template) {
        final String DEFAULT_KEY_NAME = "DEFLT_" + this.getKey();
        String keyName = DEFAULT_KEY_NAME;

        if (template != null) {
            switch (template){
                case AD:
                    keyName = "DEFLT-AD_" + this.getKey();
                    break;

                case ADDB:
                    keyName = "DEFLT-ADDB_" + this.getKey();
                    break;

                default:
                    keyName = "DEFLT_" + this.getKey();
                    break;
            }
        }

        String returnValue = readProps(keyName, PwmConstants.DEFAULT_LOCALE);
        if (returnValue.equals(Static.RESOURCE_MISSING)) {
            returnValue = readProps(DEFAULT_KEY_NAME, PwmConstants.DEFAULT_LOCALE);
        }
        return returnValue;
    }

    public Map<String,String> getOptions() {
        final String keyName = "OPTS_" + this.getKey();
        final String inputValue = readProps(keyName, PwmConstants.DEFAULT_LOCALE);
        final Gson gson = new Gson();
        final Map<String,String> valueList = gson.fromJson(inputValue, new TypeToken<Map<String,String>>(){}.getType());
        return Collections.unmodifiableMap(valueList);

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

    public Level getLevel() {
        return level;
    }

    public Pattern getRegExPattern() {
        final String value = readProps("REGEX_" + this.getKey(), PwmConstants.DEFAULT_LOCALE);

        if (value == null || value.length() < 1 || Static.RESOURCE_MISSING.equals(value)) {
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

    public static enum Syntax {
        STRING,
        STRING_ARRAY,
        LOCALIZED_STRING,
        LOCALIZED_TEXT_AREA,
        LOCALIZED_STRING_ARRAY,
        PASSWORD,
        NUMERIC,
        BOOLEAN,
        SELECT
    }

    public enum Category {
        GENERAL(0),
        USER_INTERFACE(0),
        LDAP(0),
        PASSWORD_POLICY(0),
        CHALLENGE(0),
        EMAIL(0),
        SMS(0),
        INTRUDER(0),
        LOGGING(0),
        RECOVERY(1),
        FORGOTTEN_USERNAME(1),
        NEWUSER(1),
        GUEST(1),
        ACTIVATION(1),
        UPDATE(1),
        SHORTCUT(1),
        PEOPLE_SEARCH(1),
        EDIRECTORY(0),
        DATABASE(0),
        MISC(0),
        ;

        private final int group;

        private Category(final int group) {
            this.group = group;
        }

        public String getLabel(final Locale locale) {
            return readProps("CATEGORY_LABEL_" + this.name(), locale);
        }

        public String getDescription(final Locale locale) {
            return readProps("CATEGORY_DESCR_" + this.name(), locale);
        }

        public int getGroup() {
            return group;
        }

        public Set<PwmSetting> settingsForCategory(final Level level) {
            final HashSet<PwmSetting> returnSettings = new HashSet<PwmSetting>();
            for (final PwmSetting loopSetting : PwmSetting.values()) {
                if (this.equals(loopSetting.getCategory())) {
                    if (level == null || level.equals(loopSetting.getLevel())) {
                        returnSettings.add(loopSetting);
                    }
                }
            }
            return returnSettings;
        }

        public static Category[] valuesByGroup(final int group) {
            final List<Category> returnCategories = new ArrayList<Category>();
            for (final Category category : values()) {
                if (category.getGroup() == group) {
                    returnCategories.add(category);
                }
            }
            return returnCategories.toArray(new Category[returnCategories.size()]);
        }
    }

    public enum Level {
        BASIC,
        ADVANCED
    }

    public enum Template {
        DEFAULT("Novell, OpenLDAP, DirectoryServer389, Others"),
        ADDB("Active Directory - Store responses in a database"),
        AD("Active Directory - Store responses in Active Directory"),
        ;

        private final String description;

        Template(final String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static Map<PwmSetting.Category, List<PwmSetting>> valuesByCategory(final Level byLevel) {
        if (byLevel == null || byLevel == Level.ADVANCED) {
            return VALUES_BY_CATEGORY;
        }

        final Map<PwmSetting.Category, List<PwmSetting>> returnMap = new TreeMap<PwmSetting.Category, List<PwmSetting>>();

        for (final Category category : VALUES_BY_CATEGORY.keySet()) {
            final List<PwmSetting> loopList = new ArrayList<PwmSetting>();
            for (final PwmSetting setting : VALUES_BY_CATEGORY.get(category)) {
                if (setting.getLevel() == byLevel) {
                    loopList.add(setting);
                }
            }
            if (!loopList.isEmpty()) {
                returnMap.put(category, Collections.unmodifiableList(loopList));
            }
        }

        return Collections.unmodifiableMap(returnMap);
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

