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
import com.google.gson.reflect.TypeToken;
import password.pwm.PwmConstants;
import password.pwm.util.PwmLogger;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * PwmConfiguration settings.
 *
 * @author Jason D. Rivard
 */
public enum PwmSetting {
    // general settings
    PWM_URL(
            "pwm.selfURL", PwmSettingSyntax.STRING, Category.GENERAL, false, 0),
    VERSION_CHECK_ENABLE(
            "pwm.versionCheck.enable", PwmSettingSyntax.BOOLEAN, Category.GENERAL, true, 0),
    PUBLISH_STATS_ENABLE(
            "pwm.publishStats.enable", PwmSettingSyntax.BOOLEAN, Category.GENERAL, true, 0),
    PUBLISH_STATS_SITE_DESCRIPTION(
            "pwm.publishStats.siteDescription", PwmSettingSyntax.STRING, Category.GENERAL, false, 0),
    URL_FORWARD(
            "pwm.forwardURL", PwmSettingSyntax.STRING, Category.GENERAL, false, 0),
    URL_LOGOUT(
            "pwm.logoutURL", PwmSettingSyntax.STRING, Category.GENERAL, false, 0),
    GOOGLE_ANAYLTICS_TRACKER(
            "google.analytics.tracker", PwmSettingSyntax.STRING, Category.GENERAL, false, 0),
    PWM_INSTANCE_NAME(
            "pwmInstanceName", PwmSettingSyntax.STRING, Category.GENERAL, false, 1),
    IDLE_TIMEOUT_SECONDS(
            "idleTimeoutSeconds", PwmSettingSyntax.NUMERIC, Category.GENERAL, true, 0),
    HIDE_CONFIGURATION_HEALTH_WARNINGS(
            "display.hideConfigHealthWarnings", PwmSettingSyntax.BOOLEAN, Category.GENERAL, false, 1),
    KNOWN_LOCALES(
            "knownLocales", PwmSettingSyntax.STRING_ARRAY, Category.GENERAL, false, 1),

    // user interface
    INTERFACE_THEME(
            "interface.theme", PwmSettingSyntax.SELECT, Category.USER_INTERFACE, true, 0),
    PASSWORD_SHOW_AUTOGEN(
            "password.showAutoGen", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    PASSWORD_SHOW_STRENGTH_METER(
            "password.showStrengthMeter", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_PASSWORD_GUIDE_TEXT(
            "display.password.guideText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.USER_INTERFACE, false, 0),
    DISPLAY_SHOW_HIDE_PASSWORD_FIELDS(
            "display.showHidePasswordFields", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_CANCEL_BUTTON(
            "display.showCancelButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_RESET_BUTTON(
            "display.showResetButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_SUCCESS_PAGES(
            "display.showSuccessPage", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_PASSWORD_HISTORY(
            "display.passwordHistory", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_ACCOUNT_INFORMATION(
            "display.accountInformation", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_LOGIN_PAGE_OPTIONS(
            "display.showLoginPageOptions", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_LOGOUT_BUTTON(
            "display.logoutButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, 0),
    DISPLAY_CSS_CUSTOM_STYLE(
            "display.css.customStyleLocation", PwmSettingSyntax.STRING, Category.USER_INTERFACE, false, 1),
    DISPLAY_CSS_CUSTOM_MOBILE_STYLE(
            "display.css.customMobileStyleLocation", PwmSettingSyntax.STRING, Category.USER_INTERFACE, false, 1),
    DISPLAY_CSS_EMBED(
            "display.css.customStyle", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE, false, 1),
    DISPLAY_CSS_MOBILE_EMBED(
            "display.css.customMobileStyle", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE, false, 1),
    DISPLAY_CUSTOM_JAVASCRIPT(
            "display.js.custom", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE, false, 1),

    // change password
    LOGOUT_AFTER_PASSWORD_CHANGE(
            "logoutAfterPasswordChange", PwmSettingSyntax.BOOLEAN, Category.CHANGE_PASSWORD, true, 0),
    PASSWORD_REQUIRE_FORM(
            "password.require.form", PwmSettingSyntax.FORM, Category.CHANGE_PASSWORD, false, 0),
    PASSWORD_REQUIRE_CURRENT(
            "password.change.requireCurrent", PwmSettingSyntax.SELECT, Category.CHANGE_PASSWORD, true, 0),
    PASSWORD_CHANGE_AGREEMENT_MESSAGE(
            "display.password.changeAgreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.CHANGE_PASSWORD, false, 0),
    PASSWORD_SYNC_MIN_WAIT_TIME(
            "passwordSyncMinWaitTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, 1),
    PASSWORD_SYNC_MAX_WAIT_TIME(
            "passwordSyncMaxWaitTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, 1),
    PASSWORD_EXPIRE_PRE_TIME(
            "expirePreTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, 0),
    PASSWORD_EXPIRE_WARN_TIME(
            "expireWarnTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, 0),
    EXPIRE_CHECK_DURING_AUTH(
            "expireCheckDuringAuth", PwmSettingSyntax.BOOLEAN, Category.CHANGE_PASSWORD, true, 1),
    SEEDLIST_FILENAME(
            "pwm.seedlist.location", PwmSettingSyntax.STRING, Category.CHANGE_PASSWORD, false, 1),
    CHANGE_PASSWORD_WRITE_ATTRIBUTES(
            "changePassword.writeAttributes", PwmSettingSyntax.ACTION, Category.CHANGE_PASSWORD, false, 0),

    //ldap directory
    LDAP_SERVER_URLS(
            "ldap.serverUrls", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, true, 0),
    LDAP_PROMISCUOUS_SSL(
            "ldap.promiscuousSSL", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, 0),
    LDAP_PROXY_USER_DN(
            "ldap.proxy.username", PwmSettingSyntax.STRING, Category.LDAP, true, 0),
    LDAP_PROXY_USER_PASSWORD(
            "ldap.proxy.password", PwmSettingSyntax.PASSWORD, Category.LDAP, true, 0),
    LDAP_CONTEXTLESS_ROOT(
            "ldap.rootContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, true, 0),
    LDAP_LOGIN_CONTEXTS(
            "ldap.selectableContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, false, 1),
    LDAP_TEST_USER_DN(
            "ldap.testuser.username", PwmSettingSyntax.STRING, Category.LDAP, false, 0),
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", PwmSettingSyntax.STRING, Category.LDAP, true, 0),
    LDAP_USERNAME_SEARCH_FILTER(
            "ldap.usernameSearchFilter", PwmSettingSyntax.STRING, Category.LDAP, true, 1),
    LDAP_READ_PASSWORD_POLICY(
            "ldap.readPasswordPolicies", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, 1),
    AUTO_ADD_OBJECT_CLASSES(
            "ldap.addObjectClasses", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, false, 1),
    QUERY_MATCH_CHANGE_PASSWORD(
            "password.allowChange.queryMatch", PwmSettingSyntax.STRING, Category.LDAP, true, 1),
    PASSWORD_LAST_UPDATE_ATTRIBUTE(
            "passwordLastUpdateAttribute", PwmSettingSyntax.STRING, Category.LDAP, false, 1),
    LDAP_NAMING_ATTRIBUTE(
            "ldap.namingAttribute", PwmSettingSyntax.STRING, Category.LDAP, true, 1),
    LDAP_IDLE_TIMEOUT(
            "ldap.idleTimeout", PwmSettingSyntax.NUMERIC, Category.LDAP, true, 1),
    LDAP_GUID_ATTRIBUTE(
            "ldap.guidAttribute", PwmSettingSyntax.STRING, Category.LDAP, true, 1),
    LDAP_GUID_AUTO_ADD(
            "ldap.guid.autoAddValue", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, 1),
    LDAP_ENABLE_WIRE_TRACE(
            "ldap.wireTrace.enable", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, 1),
    LDAP_CHAI_SETTINGS(
            "ldapChaiSettings", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, false, 1),
    LDAP_USERNAME_ATTRIBUTE(
            "ldap.username.attr", PwmSettingSyntax.STRING, Category.LDAP, false, 1),
    LDAP_FOLLOW_REFERRALS(
            "ldap.followReferrals", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, 1),

    // email settings
    EMAIL_SERVER_ADDRESS(
            "email.smtp.address", PwmSettingSyntax.STRING, Category.EMAIL, false, 0),
    EMAIL_USERNAME(
            "email.smtp.username", PwmSettingSyntax.STRING, Category.EMAIL, false, 1),
    EMAIL_PASSWORD(
            "email.smtp.userpassword", PwmSettingSyntax.PASSWORD, Category.EMAIL, false, 1),
    EMAIL_USER_MAIL_ATTRIBUTE(
            "email.userMailAttribute", PwmSettingSyntax.STRING, Category.EMAIL, true, 1),
    EMAIL_MAX_QUEUE_AGE(
            "email.queueMaxAge", PwmSettingSyntax.NUMERIC, Category.EMAIL, true, 1),
    EMAIL_ADMIN_ALERT_TO(
            "email.adminAlert.toAddress", PwmSettingSyntax.STRING_ARRAY, Category.EMAIL, false, 0),
    EMAIL_ADMIN_ALERT_FROM(
            "email.adminAlert.fromAddress", PwmSettingSyntax.STRING, Category.EMAIL, false, 1),
    EMAIL_CHANGEPASSWORD(
            "email.changePassword", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_CHANGEPASSWORD_HELPDESK(
            "email.changePassword.helpdesk", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_UPDATEPROFILE(
            "email.updateProfile", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_NEWUSER(
            "email.newUser", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_NEWUSER_VERIFICATION(
            "email.newUser.token", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_ACTIVATION(
            "email.activation", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_ACTIVATION_VERIFICATION(
            "email.activation.token", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_CHALLENGE_TOKEN(
            "email.challenge.token", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 0),
    EMAIL_GUEST(
            "email.guest", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 1),
    EMAIL_UPDATEGUEST(
            "email.updateguest", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 1),
    EMAIL_SENDPASSWORD(
            "email.sendpassword", PwmSettingSyntax.EMAIL, Category.EMAIL, false, 1),
    EMAIL_ADVANCED_SETTINGS(
            "email.smtp.advancedSettings", PwmSettingSyntax.STRING_ARRAY, Category.EMAIL, false, 1),

    // sms settings
    SMS_USER_PHONE_ATTRIBUTE(
            "sms.userSmsAttribute", PwmSettingSyntax.STRING, Category.SMS, true, 0),
    SMS_MAX_QUEUE_AGE(
            "sms.queueMaxAge", PwmSettingSyntax.NUMERIC, Category.SMS, true, 1),
    SMS_GATEWAY_URL(
            "sms.gatewayURL", PwmSettingSyntax.STRING, Category.SMS, true, 0),
    SMS_GATEWAY_USER(
            "sms.gatewayUser", PwmSettingSyntax.STRING, Category.SMS, false, 0),
    SMS_GATEWAY_PASSWORD(
            "sms.gatewayPassword", PwmSettingSyntax.PASSWORD, Category.SMS, false, 0),
    SMS_GATEWAY_METHOD(
            "sms.gatewayMethod", PwmSettingSyntax.SELECT, Category.SMS, true, 0),
    SMS_GATEWAY_AUTHMETHOD(
            "sms.gatewayAuthMethod", PwmSettingSyntax.SELECT, Category.SMS, true, 1),
    SMS_REQUEST_DATA(
            "sms.requestData", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.SMS, false, 1),
    SMS_REQUEST_CONTENT_TYPE(
            "sms.requestContentType", PwmSettingSyntax.STRING, Category.SMS, false, 1),
    SMS_REQUEST_CONTENT_ENCODING(
            "sms.requestContentEncoding", PwmSettingSyntax.SELECT, Category.SMS, true, 1),
    SMS_GATEWAY_REQUEST_HEADERS(
            "sms.httpRequestHeaders", PwmSettingSyntax.STRING_ARRAY, Category.SMS, false, 1),
    SMS_MAX_TEXT_LENGTH(
            "sms.maxTextLength", PwmSettingSyntax.NUMERIC, Category.SMS, true, 1),
    SMS_RESPONSE_OK_REGEX(
            "sms.responseOkRegex", PwmSettingSyntax.STRING_ARRAY, Category.SMS, false, 1),
    SMS_SENDER_ID(
            "sms.senderID", PwmSettingSyntax.STRING, Category.SMS, false, 0),
    SMS_PHONE_NUMBER_FORMAT(
            "sms.phoneNumberFormat", PwmSettingSyntax.SELECT, Category.SMS, true, 1),
    SMS_DEFAULT_COUNTRY_CODE(
            "sms.defaultCountryCode", PwmSettingSyntax.NUMERIC, Category.SMS, false, 1),
    SMS_REQUESTID_CHARS(
            "sms.requestId.characters", PwmSettingSyntax.STRING, Category.SMS, true, 1),
    SMS_REQUESTID_LENGTH(
            "sms.requestId.length", PwmSettingSyntax.NUMERIC, Category.SMS, true, 1),
    SMS_CHALLENGE_TOKEN_TEXT(
            "sms.challenge.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, 0),
    SMS_NEWUSER_TOKEN_TEXT(
            "sms.newUser.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, 0),
    SMS_ACTIVATION_VERIFICATION_TEXT(
            "sms.activation.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, 0),
    SMS_ACTIVATION_TEXT(
            "sms.activation.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, 0),
    SMS_USE_URL_SHORTENER(
            "sms.useUrlShortener", PwmSettingSyntax.BOOLEAN, Category.SMS, false, 1),

    //global password policy settings
    PASSWORD_POLICY_SOURCE(
            "password.policy.source", PwmSettingSyntax.SELECT, Category.PASSWORD_POLICY, true, 1),
    PASSWORD_POLICY_MINIMUM_LENGTH(
            "password.policy.minimumLength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_LENGTH(
            "password.policy.maximumLength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_REPEAT(
            "password.policy.maximumRepeat", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT(
            "password.policy.maximumSequentialRepeat", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_ALLOW_NUMERIC(
            "password.policy.allowNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC(
            "password.policy.allowFirstCharNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC(
            "password.policy.allowLastCharNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_NUMERIC(
            "password.policy.maximumNumeric", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MINIMUM_NUMERIC(
            "password.policy.minimumNumeric", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_ALLOW_SPECIAL(
            "password.policy.allowSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL(
            "password.policy.allowFirstCharSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL(
            "password.policy.allowLastCharSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_SPECIAL(
            "password.policy.maximumSpecial", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MINIMUM_SPECIAL(
            "password.policy.minimumSpecial", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_ALPHA(
            "password.policy.maximumAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MINIMUM_ALPHA(
            "password.policy.minimumAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_NON_ALPHA(
            "password.policy.maximumNonAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MINIMUM_NON_ALPHA(
            "password.policy.minimumNonAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_UPPERCASE(
            "password.policy.maximumUpperCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MINIMUM_UPPERCASE(
            "password.policy.minimumUpperCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_LOWERCASE(
            "password.policy.maximumLowerCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MINIMUM_LOWERCASE(
            "password.policy.minimumLowerCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MINIMUM_UNIQUE(
            "password.policy.minimumUnique", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS(
            "password.policy.maximumOldPasswordChars", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_CASE_SENSITIVITY(
            "password.policy.caseSensitivity", PwmSettingSyntax.SELECT, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_ENABLE_WORDLIST(
            "password.policy.checkWordlist", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_AD_COMPLEXITY(
            "password.policy.ADComplexity", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH(
            "password.policy.regExMatch", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, 1),
    PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH(
            "password.policy.regExNoMatch", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, 1),
    PASSWORD_POLICY_DISALLOWED_VALUES(
            "password.policy.disallowedValues", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, 0),
    PASSWORD_POLICY_DISALLOWED_ATTRIBUTES(
            "password.policy.disallowedAttributes", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, 1),
    PASSWORD_SHAREDHISTORY_ENABLE(
            "password.sharedHistory.enable", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_SHAREDHISTORY_MAX_AGE(
            "password.sharedHistory.age", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 1),
    PASSWORD_POLICY_MINIMUM_STRENGTH(
            "password.policy.minimumStrength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_POLICY_CHANGE_MESSAGE(
            "password.policy.changeMessage", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY, false, 0),
    PASSWORD_POLICY_RULE_TEXT(
            "password.policy.ruleText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY, false, 1),
    WORDLIST_FILENAME(
            "pwm.wordlist.location", PwmSettingSyntax.STRING, Category.PASSWORD_POLICY, false, 0),
    WORDLIST_CASE_SENSITIVE(
            "wordlistCaseSensitive", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, 0),
    PASSWORD_WORDLIST_WORDSIZE(
            "password.wordlist.wordSize", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, 1),


    // security settings
    PWM_SECURITY_KEY(
            "pwm.securityKey", PwmSettingSyntax.PASSWORD, Category.SECURITY, false, 0),
    INTRUDER_USER_RESET_TIME(
            "intruder.user.resetTime", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, 0),
    INTRUDER_USER_MAX_ATTEMPTS(
            "intruder.user.maxAttempts", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, 0 ),
    INTRUDER_ADDRESS_RESET_TIME(
            "intruder.address.resetTime", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, 0),
    INTRUDER_ADDRESS_MAX_ATTEMPTS(
            "intruder.address.maxAttempts", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, 0),
    SECURITY_SIMULATE_LDAP_BAD_PASSWORD(
            "security.ldap.simulateBadPassword", PwmSettingSyntax.BOOLEAN, Category.SECURITY, false, 0),
    RECAPTCHA_KEY_PUBLIC(
            "captcha.recaptcha.publicKey", PwmSettingSyntax.STRING, Category.SECURITY, false, 0),
    RECAPTCHA_KEY_PRIVATE(
            "captcha.recaptcha.privateKey", PwmSettingSyntax.PASSWORD, Category.SECURITY, false, 0),
    CAPTCHA_SKIP_PARAM(
            "captcha.skip.param", PwmSettingSyntax.STRING, Category.SECURITY, false, 1),
    CAPTCHA_SKIP_COOKIE(
            "captcha.skip.cookie", PwmSettingSyntax.STRING, Category.SECURITY, false, 1),
    SECURITY_ENABLE_REQUEST_SEQUENCE(
            "security.page.enableRequestSequence", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 1),
    SECURITY_ENABLE_FORM_NONCE(
            "security.formNonce.enable", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 1),
    ALLOW_URL_SESSIONS(
            "allowUrlSessions", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 1),
    ENABLE_SESSION_VERIFICATION(
            "enableSessionVerification", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 1),
    DISALLOWED_HTTP_INPUTS(
            "disallowedInputs", PwmSettingSyntax.STRING_ARRAY, Category.SECURITY, false, 1),
    REQUIRE_HTTPS(
            "pwm.requireHTTPS", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 0),
    FORCE_BASIC_AUTH(
            "forceBasicAuth", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 1),
    USE_X_FORWARDED_FOR_HEADER(
            "useXForwardedForHeader", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 1),
    REVERSE_DNS_ENABLE(
            "network.reverseDNS.enable", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 1),
    SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT(
            "security.page.leaveNoticeTimeout", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, 1),
    DISPLAY_SHOW_DETAILED_ERRORS(
            "display.showDetailedErrors", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, 0),


    // token settings
    TOKEN_STORAGEMETHOD(
            "token.storageMethod", PwmSettingSyntax.SELECT, Category.TOKEN, true, 0),
    TOKEN_CHARACTERS(
            "token.characters", PwmSettingSyntax.STRING, Category.TOKEN, true, 0),
    TOKEN_LENGTH(
            "token.length", PwmSettingSyntax.NUMERIC, Category.TOKEN, true, 0),
    TOKEN_LIFETIME(
            "token.lifetime", PwmSettingSyntax.NUMERIC, Category.TOKEN, true, 0),
    TOKEN_LDAP_ATTRIBUTE(
            "token.ldap.attribute", PwmSettingSyntax.STRING, Category.TOKEN, true, 0),

    // logger settings
    EVENTS_HEALTH_CHECK_MIN_INTERVAL(
            "events.healthCheck.minInterval", PwmSettingSyntax.NUMERIC, Category.LOGGING, false, 1),
    EVENTS_JAVA_STDOUT_LEVEL(
            "events.java.stdoutLevel", PwmSettingSyntax.SELECT, Category.LOGGING, false, 0),
    EVENTS_JAVA_LOG4JCONFIG_FILE(
            "events.java.log4jconfigFile", PwmSettingSyntax.STRING, Category.LOGGING, false, 1),
    EVENTS_PWMDB_MAX_EVENTS(
            "events.pwmDB.maxEvents", PwmSettingSyntax.NUMERIC, Category.LOGGING, true, 1),
    EVENTS_PWMDB_MAX_AGE(
            "events.pwmDB.maxAge", PwmSettingSyntax.NUMERIC, Category.LOGGING, true, 1),
    EVENTS_PWMDB_LOG_LEVEL(
            "events.pwmDB.logLevel", PwmSettingSyntax.SELECT, Category.LOGGING, true, 0),
    EVENTS_LDAP_ATTRIBUTE(
            "events.ldap.attribute", PwmSettingSyntax.STRING, Category.LOGGING, false, 1),
    EVENTS_LDAP_MAX_EVENTS(
            "events.ldap.maxEvents", PwmSettingSyntax.NUMERIC, Category.LOGGING, true, 1),
    EVENTS_ALERT_STARTUP(
            "events.alert.startup.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, 0),
    EVENTS_ALERT_SHUTDOWN(
            "events.alert.shutdown.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, 0),
    EVENTS_ALERT_INTRUDER_LOCKOUT(
            "events.alert.intruder.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, 0),
    EVENTS_ALERT_FATAL_EVENT(
            "events.alert.fatalEvent.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, 0),
    EVENTS_ALERT_CONFIG_MODIFY(
            "events.alert.configModify.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, 0),
    EVENTS_ALERT_DAILY_SUMMARY(
            "events.alert.dailySummary.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, 0),


    // challenge policy
    CHALLENGE_ENABLE(
            "challenge.enable", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, false, 0),
    CHALLENGE_FORCE_SETUP(
            "challenge.forceSetup", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, 0),
    CHALLENGE_RANDOM_CHALLENGES(
            "challenge.randomChallenges", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, Category.CHALLENGE, false, 0),
    CHALLENGE_REQUIRED_CHALLENGES(
            "challenge.requiredChallenges", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, Category.CHALLENGE, false, 0),
    CHALLENGE_MIN_RANDOM_REQUIRED(
            "challenge.minRandomRequired", PwmSettingSyntax.NUMERIC, Category.CHALLENGE, true, 0),
    CHALLENGE_MIN_RANDOM_SETUP(
            "challenge.minRandomsSetup", PwmSettingSyntax.NUMERIC, Category.CHALLENGE, true, 0),
    CHALLENGE_SHOW_CONFIRMATION(
            "challenge.showConfirmation", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, 0),
    CHALLENGE_CASE_INSENSITIVE(
            "challenge.caseInsensitive", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, 1),
    CHALLENGE_MAX_LENGTH_CHALLENGE_IN_RESPONSE(
            "challenge.maxChallengeLengthInResponse", PwmSettingSyntax.NUMERIC, Category.CHALLENGE, true, 1),
    CHALLENGE_ALLOW_DUPLICATE_RESPONSES(
            "challenge.allowDuplicateResponses", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, 1),
    CHALLENGE_APPLY_WORDLIST(
            "challenge.applyWorldlist", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, 0),
    QUERY_MATCH_SETUP_RESPONSE(
            "challenge.allowSetup.queryMatch", PwmSettingSyntax.STRING, Category.CHALLENGE, true, 1),
    QUERY_MATCH_CHECK_RESPONSES(
            "command.checkResponses.queryMatch", PwmSettingSyntax.STRING, Category.CHALLENGE, true, 1),


    // recovery settings
    FORGOTTEN_PASSWORD_ENABLE(
            "recovery.enable", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, false, 0),
    FORGOTTEN_PASSWORD_SEARCH_FORM(
            "recovery.form", PwmSettingSyntax.FORM, Category.RECOVERY, true, 0),
    FORGOTTEN_PASSWORD_SEARCH_FILTER(
            "recovery.searchFilter", PwmSettingSyntax.STRING, Category.RECOVERY, true, 0),
    FORGOTTEN_PASSWORD_READ_PREFERENCE(
            "recovery.response.readPreference", PwmSettingSyntax.SELECT, Category.RECOVERY, true, 0),
    FORGOTTEN_PASSWORD_WRITE_PREFERENCE(
            "recovery.response.writePreference", PwmSettingSyntax.SELECT, Category.RECOVERY, true, 0),
    CHALLENGE_USER_ATTRIBUTE(
            "challenge.userAttribute", PwmSettingSyntax.STRING, Category.RECOVERY, false, 0),
    CHALLENGE_ALLOW_UNLOCK(
            "challenge.allowUnlock", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, true, 0),
    CHALLENGE_STORAGE_HASHED(
            "challenge.storageHashed", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, true, 1),
    CHALLENGE_REQUIRED_ATTRIBUTES(
            "challenge.requiredAttributes", PwmSettingSyntax.FORM, Category.RECOVERY, false, 0),
    CHALLENGE_REQUIRE_RESPONSES(
            "challenge.requireResponses", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, false, 0),
    CHALLENGE_TOKEN_ENABLE(
            "challenge.token.enable", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, true, 0),
    CHALLENGE_TOKEN_SEND_METHOD(
            "challenge.token.sendMethod", PwmSettingSyntax.SELECT, Category.RECOVERY, true, 0),
    FORGOTTEN_PASSWORD_ACTION(
            "recovery.action", PwmSettingSyntax.SELECT, Category.RECOVERY, true, 0),


    // forgotten username
    FORGOTTEN_USERNAME_ENABLE(
            "forgottenUsername.enable", PwmSettingSyntax.BOOLEAN, Category.FORGOTTEN_USERNAME, true, 0),
    FORGOTTEN_USERNAME_FORM(
            "forgottenUsername.form", PwmSettingSyntax.FORM, Category.FORGOTTEN_USERNAME, true, 0),
    FORGOTTEN_USERNAME_SEARCH_FILTER(
            "forgottenUsername.searchFilter", PwmSettingSyntax.STRING, Category.FORGOTTEN_USERNAME, true, 0),
    FORGOTTEN_USERNAME_USERNAME_ATTRIBUTE(
            "forgottenUsername.usernameAttribute", PwmSettingSyntax.STRING, Category.FORGOTTEN_USERNAME, true, 0),

    // new user settings
    NEWUSER_ENABLE(
            "newUser.enable", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, true, 0),
    NEWUSER_CONTEXT(
            "newUser.createContext", PwmSettingSyntax.STRING, Category.NEWUSER, true, 0),
    NEWUSER_AGREEMENT_MESSAGE(
            "display.newuser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.NEWUSER, false, 0),
    NEWUSER_FORM(
            "newUser.form", PwmSettingSyntax.FORM, Category.NEWUSER, true, 0),
    NEWUSER_UNIQUE_ATTRIBUES(
            "newUser.creationUniqueAttributes", PwmSettingSyntax.STRING_ARRAY, Category.NEWUSER, false, 0),
    NEWUSER_WRITE_ATTRIBUTES(
            "newUser.writeAttributes", PwmSettingSyntax.ACTION, Category.NEWUSER, false, 0),
    NEWUSER_DELETE_ON_FAIL(
            "newUser.deleteOnFail", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, false, 1),
    NEWUSER_USERNAME_CHARS(
            "newUser.username.characters", PwmSettingSyntax.STRING, Category.NEWUSER, false, 1),
    NEWUSER_USERNAME_LENGTH(
            "newUser.username.length", PwmSettingSyntax.NUMERIC, Category.NEWUSER, false, 1),
    NEWUSER_EMAIL_VERIFICATION(
            "newUser.email.verification", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, false, 0),
    NEWUSER_SMS_VERIFICATION(
            "newUser.sms.verification", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, false, 0),
    NEWUSER_PASSWORD_POLICY_USER(
            "newUser.passwordPolicy.user", PwmSettingSyntax.STRING, Category.NEWUSER, false, 0),
    NEWUSER_MINIMUM_WAIT_TIME(
            "newUser.minimumWaitTime", PwmSettingSyntax.NUMERIC, Category.NEWUSER, false, 0),
    DEFAULT_OBJECT_CLASSES(
            "ldap.defaultObjectClasses", PwmSettingSyntax.STRING_ARRAY, Category.NEWUSER, false, 1),


    // guest settings
    GUEST_ENABLE(
            "guest.enable", PwmSettingSyntax.BOOLEAN, Category.GUEST, true, 0),
    GUEST_CONTEXT(
            "guest.createContext", PwmSettingSyntax.STRING, Category.GUEST, true, 0),
    GUEST_ADMIN_GROUP(
            "guest.adminGroup", PwmSettingSyntax.STRING, Category.GUEST, true, 0),
    GUEST_FORM(
            "guest.form", PwmSettingSyntax.FORM, Category.GUEST, true, 0),
    GUEST_UPDATE_FORM(
            "guest.update.form", PwmSettingSyntax.FORM, Category.GUEST, true, 0),
    GUEST_UNIQUE_ATTRIBUTES(
            "guest.creationUniqueAttributes", PwmSettingSyntax.STRING_ARRAY, Category.GUEST, false, 0),
    GUEST_WRITE_ATTRIBUTES(
            "guest.writeAttributes", PwmSettingSyntax.ACTION, Category.GUEST, false, 0),
    GUEST_ADMIN_ATTRIBUTE(
            "guest.adminAttribute", PwmSettingSyntax.STRING, Category.GUEST, false, 0),
    GUEST_EDIT_ORIG_ADMIN_ONLY(
            "guest.editOriginalAdminOnly", PwmSettingSyntax.BOOLEAN, Category.GUEST, true, 0),
    GUEST_MAX_VALID_DAYS(
            "guest.maxValidDays", PwmSettingSyntax.NUMERIC, Category.GUEST, true, 0),
    GUEST_EXPIRATION_ATTRIBUTE (
            "guest.expirationAttribute", PwmSettingSyntax.STRING, Category.GUEST, false, 0),

    // activation settings
    ACTIVATE_USER_ENABLE(
            "activateUser.enable", PwmSettingSyntax.BOOLEAN, Category.ACTIVATION, true, 0),
    ACTIVATE_USER_TOKEN_VERIFICATION(
            "activateUser.token.verification", PwmSettingSyntax.BOOLEAN, Category.ACTIVATION, true, 0),
    ACTIVATE_AGREEMENT_MESSAGE(
            "display.activateUser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.ACTIVATION, false, 0),
    ACTIVATE_USER_FORM(
            "activateUser.form", PwmSettingSyntax.FORM, Category.ACTIVATION, true, 0),
    ACTIVATE_USER_SEARCH_FILTER(
            "activateUser.searchFilter", PwmSettingSyntax.STRING, Category.ACTIVATION, true, 0),
    ACTIVATE_USER_QUERY_MATCH(
            "activateUser.queryMatch", PwmSettingSyntax.STRING, Category.ACTIVATION, true, 0),
    ACTIVATE_USER_PRE_WRITE_ATTRIBUTES(
            "activateUser.writePreAttributes", PwmSettingSyntax.STRING_ARRAY, Category.ACTIVATION, false, 0),
    ACTIVATE_USER_POST_WRITE_ATTRIBUTES(
            "activateUser.writePostAttributes", PwmSettingSyntax.STRING_ARRAY, Category.ACTIVATION, false, 0),
    ACTIVATE_TOKEN_SEND_METHOD(
            "activateUser.token.sendMethod", PwmSettingSyntax.SELECT, Category.ACTIVATION, true, 0),

    // update profile
    UPDATE_PROFILE_ENABLE(
            "updateAttributes.enable", PwmSettingSyntax.BOOLEAN, Category.UPDATE, true, 0),
    UPDATE_PROFILE_FORCE_SETUP(
            "updateAttributes.forceSetup", PwmSettingSyntax.BOOLEAN, Category.UPDATE, true, 0),
    UPDATE_PROFILE_AGREEMENT_MESSAGE(
            "display.updateAttributes.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.UPDATE, false, 0),
    UPDATE_PROFILE_QUERY_MATCH(
            "updateAttributes.queryMatch", PwmSettingSyntax.STRING, Category.UPDATE, true, 0),
    UPDATE_PROFILE_WRITE_ATTRIBUTES(
            "updateAttributes.writeAttributes", PwmSettingSyntax.ACTION, Category.UPDATE, false, 0),
    UPDATE_PROFILE_FORM(
            "updateAttributes.form", PwmSettingSyntax.FORM, Category.UPDATE, true, 0),
    UPDATE_PROFILE_CHECK_QUERY_MATCH(
            "updateAttributes.check.queryMatch", PwmSettingSyntax.STRING, Category.UPDATE, false, 0),
    UPDATE_PROFILE_SHOW_CONFIRMATION(
            "updateAttributes.showConfirmation", PwmSettingSyntax.BOOLEAN, Category.UPDATE, true, 0),

    // shortcut settings
    SHORTCUT_ENABLE(
            "shortcut.enable", PwmSettingSyntax.BOOLEAN, Category.SHORTCUT, false, 0),
    SHORTCUT_ITEMS(
            "shortcut.items", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, Category.SHORTCUT, false, 0),
    SHORTCUT_HEADER_NAMES(
            "shortcut.httpHeaders", PwmSettingSyntax.STRING_ARRAY, Category.SHORTCUT, false, 0),

    // peoplesearch settings
    PEOPLE_SEARCH_ENABLE(
            "peopleSearch.enable", PwmSettingSyntax.BOOLEAN, Category.PEOPLE_SEARCH, false, 0),
    PEOPLE_SEARCH_QUERY_MATCH(
            "peopleSearch.queryMatch", PwmSettingSyntax.STRING, Category.PEOPLE_SEARCH, true, 0),
    PEOPLE_SEARCH_SEARCH_FILTER(
            "peopleSearch.searchFilter", PwmSettingSyntax.STRING, Category.PEOPLE_SEARCH, true, 0),
    PEOPLE_SEARCH_SEARCH_BASE(
            "peopleSearch.searchBase", PwmSettingSyntax.STRING_ARRAY, Category.PEOPLE_SEARCH, false, 0),
    PEOPLE_SEARCH_RESULT_FORM(
            "peopleSearch.result.form", PwmSettingSyntax.FORM, Category.PEOPLE_SEARCH, true, 0),
    PEOPLE_SEARCH_DETAIL_FORM(
            "peopleSearch.detail.form", PwmSettingSyntax.FORM, Category.PEOPLE_SEARCH, true, 0),
    PEOPLE_SEARCH_RESULT_LIMIT(
            "peopleSearch.result.limit", PwmSettingSyntax.NUMERIC, Category.PEOPLE_SEARCH, true, 0),
    PEOPLE_SEARCH_USE_PROXY(
            "peopleSearch.useProxy", PwmSettingSyntax.BOOLEAN, Category.PEOPLE_SEARCH, true, 1),

    // edirectory settings
    EDIRECTORY_ENABLE_NMAS(
            "ldap.edirectory.enableNmas", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, true, 0, new Template[]{Template.NOVL}),
    EDIRECTORY_STORE_NMAS_RESPONSES(
            "ldap.edirectory.storeNmasResponses", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, true, 0, new Template[]{Template.NOVL}),
    EDIRECTORY_READ_USER_PWD(
            "ldap.edirectory.readUserPwd", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, false, 1, new Template[]{Template.NOVL}),
    EDIRECTORY_READ_CHALLENGE_SET(
            "ldap.edirectory.readChallengeSets", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, true, 0, new Template[]{Template.NOVL}),
    EDIRECTORY_PWD_MGT_WEBSERVICE_URL(
            "ldap.edirectory.ws.pwdMgtURL", PwmSettingSyntax.STRING, Category.EDIRECTORY, false, 0, new Template[]{Template.NOVL}),

    AD_USE_PROXY_FOR_FORGOTTEN(
            "ldap.ad.proxyForgotten", PwmSettingSyntax.BOOLEAN, Category.ACTIVE_DIRECTORY, true, 0, new Template[]{Template.AD}),
    AD_ALLOW_AUTH_REQUIRE_NEW_PWD(
            "ldap.ad.allowAuthRequireNewPassword", PwmSettingSyntax.BOOLEAN, Category.ACTIVE_DIRECTORY, true, 0, new Template[]{Template.AD}),

    // helpdesk
    HELPDESK_ENABLE(
            "helpdesk.enable", PwmSettingSyntax.BOOLEAN, Category.HELPDESK, false, 0),
    HELPDESK_QUERY_MATCH(
            "helpdesk.queryMatch", PwmSettingSyntax.STRING, Category.HELPDESK, true, 0),
    HELPDESK_SEARCH_FILTER(
            "helpdesk.filter", PwmSettingSyntax.STRING, Category.HELPDESK, false, 1),
    HELPDESK_SEARCH_FORM(
            "helpdesk.result.form", PwmSettingSyntax.FORM, Category.HELPDESK, true, 0),
    HELPDESK_DETAIL_FORM(
            "helpdesk.detail.form", PwmSettingSyntax.FORM, Category.HELPDESK, true, 0),
    HELPDESK_SEARCH_BASE(
            "helpdesk.searchBase", PwmSettingSyntax.STRING_ARRAY, Category.HELPDESK, false, 0),
    HELPDESK_RESULT_LIMIT(
            "helpdesk.result.limit", PwmSettingSyntax.NUMERIC, Category.HELPDESK, true, 1),
    HELPDESK_SET_PASSWORD_MODE(
            "helpdesk.setPassword.mode", PwmSettingSyntax.SELECT, Category.HELPDESK, false, 0),
    HELPDESK_POST_SET_PASSWORD_WRITE_ATTRIBUTES(
            "helpdesk.setPassword.writeAttributes", PwmSettingSyntax.ACTION, Category.HELPDESK, false, 1),
    HELPDESK_ENABLE_UNLOCK(
            "helpdesk.enableUnlock", PwmSettingSyntax.BOOLEAN, Category.HELPDESK, true, 0),
    HELPDESK_ENFORCE_PASSWORD_POLICY(
            "helpdesk.enforcePasswordPolicy", PwmSettingSyntax.BOOLEAN, Category.HELPDESK, false, 0),
    HELPDESK_IDLE_TIMEOUT_SECONDS(
            "helpdesk.idleTimeout", PwmSettingSyntax.NUMERIC, Category.HELPDESK, false, 0),
    HELPDESK_CLEAR_RESPONSES(
            "helpdesk.clearResponses", PwmSettingSyntax.SELECT, Category.HELPDESK, false, 0),


    // Database
    PWMDB_LOCATION(
            "pwmDb.location", PwmSettingSyntax.STRING, Category.DATABASE, true, 0),
    PWMDB_IMPLEMENTATION(
            "pwmDb.implementation", PwmSettingSyntax.STRING, Category.DATABASE, true, 1),
    PWMDB_INIT_STRING(
            "pwmDb.initParameters", PwmSettingSyntax.STRING_ARRAY, Category.DATABASE, false, 1),
    DATABASE_CLASS(
            "db.classname", PwmSettingSyntax.STRING, Category.DATABASE, false, 0),
    DATABASE_URL(
            "db.url", PwmSettingSyntax.STRING, Category.DATABASE, false, 0),
    DATABASE_USERNAME(
            "db.username", PwmSettingSyntax.STRING, Category.DATABASE, false, 0),
    DATABASE_PASSWORD(
            "db.password", PwmSettingSyntax.PASSWORD, Category.DATABASE, false, 0),

    // misc
    EXTERNAL_CHANGE_METHODS(
            "externalChangeMethod", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, 0),
    EXTERNAL_JUDGE_METHODS(
            "externalJudgeMethod", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, 0),
    EXTERNAL_RULE_METHODS(
            "externalRuleMethod", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, 0),
    HTTP_PROXY_URL(
            "http.proxy.url", PwmSettingSyntax.STRING, Category.MISC, false, 0),
    CAS_CLEAR_PASS_URL(
            "cas.clearPassUrl", PwmSettingSyntax.STRING, Category.MISC, false, 0),
    URL_SHORTENER_CLASS(
            "urlshortener.classname", PwmSettingSyntax.STRING, Category.MISC, false, 1),
    URL_SHORTENER_REGEX(
            "urlshortener.regex", PwmSettingSyntax.STRING, Category.MISC, false, 1),
    URL_SHORTENER_PARAMETERS(
            "urlshortener.parameters", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, 1),
    ENABLE_EXTERNAL_WEBSERVICES(
            "external.webservices.enable", PwmSettingSyntax.BOOLEAN, Category.MISC, false, 0),

    ;

// ------------------------------ STATICS ------------------------------

    private static final Map<Category, List<PwmSetting>> VALUES_BY_CATEGORY;
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmSetting.class);

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
        private static final Pattern DEFAULT_REGEX = Pattern.compile(".*",Pattern.DOTALL);
    }

    private final String key;
    private final PwmSettingSyntax syntax;
    private final Category category;
    private final boolean required;
    private final int level;
    private final Set<Template> templates;

// --------------------------- CONSTRUCTORS ---------------------------

    PwmSetting(
            final String key,
            final PwmSettingSyntax syntax,
            final Category category,
            final boolean required,
            final int level,
            final Template... templates
    ) {
        this.key = key;
        this.syntax = syntax;
        this.category = category;
        this.required = required;
        this.level = level;
        final Template[] temps = (templates == null || templates.length == 0) ? Template.values() : templates;
        this.templates = Collections.unmodifiableSet(new HashSet(Arrays.asList(temps)));
    }

// --------------------- GETTER / SETTER METHODS ---------------------


    public String getKey() {
        return key;
    }

    public boolean isConfidential() {
        return PwmSettingSyntax.PASSWORD == this.getSyntax();
    }

    public Category getCategory() {
        return category;
    }

    public PwmSettingSyntax getSyntax() {
        return syntax;
    }

    public int getLevel() {
        return level;
    }

    public Set<Template> getTemplates() {
        return templates;
    }

    // -------------------------- OTHER METHODS --------------------------

    public String getDefaultValue(final Template template) {
        final String DEFAULT_KEY_NAME = "DEFLT_" + this.getKey();
        String keyName = DEFAULT_KEY_NAME;

        if (template != null) {
            switch (template){
                case NOVL:
                    keyName = "DEFLT-NOVL_" + this.getKey();
                    break;

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

        if (returnValue.equals(Static.RESOURCE_MISSING)) {
            final String errorMsg = "unable to find default resource value for setting key: " + this.getKey() + ", getTemplate: " + template + " in " + PwmSetting.class.getName() + " resource bundle";
            LOGGER.fatal(errorMsg);
            throw new IllegalStateException(errorMsg);
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


    public Pattern getRegExPattern() {
        final String value = readProps("REGEX_" + this.getKey(), PwmConstants.DEFAULT_LOCALE);

        if (value == null || value.length() < 1 || Static.RESOURCE_MISSING.equals(value)) {
            return Static.DEFAULT_REGEX;
        }

        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            final String errorMsg = "error compiling regex constraints for setting " + this.toString() + ", error: " + e.getMessage();
            LOGGER.error(errorMsg,e);
            throw new IllegalStateException(errorMsg,e);
        }
    }

    private static String readProps(final String key, final Locale locale) {
        try {
            final ResourceBundle bundle = ResourceBundle.getBundle(PwmSetting.class.getName(), locale);
            return bundle.getString(key);
        } catch (Exception e) {
            return Static.RESOURCE_MISSING;
        }
    }

    public enum Category {
        LDAP(Type.SETTING),
        GENERAL(Type.SETTING),
        USER_INTERFACE(Type.SETTING),
        PASSWORD_POLICY(Type.SETTING),
        CHALLENGE(Type.SETTING),
        EMAIL(Type.SETTING),
        SMS(Type.SETTING),
        SECURITY(Type.SETTING),
        TOKEN(Type.SETTING),
        LOGGING(Type.SETTING),
        EDIRECTORY(Type.SETTING),
        ACTIVE_DIRECTORY(Type.SETTING),
        DATABASE(Type.SETTING),
        MISC(Type.SETTING),
        CHANGE_PASSWORD(Type.MODULE),
        RECOVERY(Type.MODULE),
        FORGOTTEN_USERNAME(Type.MODULE),
        NEWUSER(Type.MODULE),
        GUEST(Type.MODULE),
        ACTIVATION(Type.MODULE),
        UPDATE(Type.MODULE),
        SHORTCUT(Type.MODULE),
        PEOPLE_SEARCH(Type.MODULE),
        HELPDESK(Type.MODULE),
        ;
        
        public enum Type {
            SETTING, MODULE
        }
        
        private Type type;

        private Category(Type type) {
            this.type = type;
        }

        public String getLabel(final Locale locale) {
            return readProps("CATEGORY_LABEL_" + this.name(), locale);
        }

        public String getDescription(final Locale locale) {
            return readProps("CATEGORY_DESCR_" + this.name(), locale);
        }

        public Type getType() {
            return type;
        }

        public static Category[] valuesByType(final Category.Type type) {
            final List<Category> returnCategories = new ArrayList<Category>();
            for (final Category category : values()) {
                if (category.getType() == type) {
                    returnCategories.add(category);
                }
            }
            return returnCategories.toArray(new Category[returnCategories.size()]);
        }
    }

    public enum SmsPriority {
        EMAILONLY,
        BOTH,
        EMAILFIRST,
        SMSFIRST,
        SMSONLY
    }

    public enum Template {
        NOVL("Novell eDirectory"),
        ADDB("Active Directory - Store responses in a database"),
        AD("Active Directory - Store responses in Active Directory"),
        DEFAULT("OpenLDAP, DirectoryServer389, Others"),
        ;

        private final String description;

        Template(final String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static Map<PwmSetting.Category, List<PwmSetting>> valuesByFilter(final Template template, final Category.Type categoryType, final int level) {
        final List<PwmSetting> settingList = new ArrayList<PwmSetting>(Arrays.asList(PwmSetting.values()));

        if (level >= 0) {
            for (Iterator<PwmSetting> iter = settingList.iterator(); iter.hasNext();) {
                final PwmSetting loopSetting = iter.next();
                if (loopSetting.getLevel() > level) {
                    iter.remove();
                }
            }
        }

        if (categoryType != null) {
            for (Iterator<PwmSetting> iter = settingList.iterator(); iter.hasNext();) {
                final PwmSetting loopSetting = iter.next();
                if (loopSetting.getCategory().getType() != categoryType) {
                    iter.remove();
                }
            }
        }

        if (template != null) {
            for (Iterator<PwmSetting> iter = settingList.iterator(); iter.hasNext();) {
                final PwmSetting loopSetting = iter.next();
                if (!loopSetting.getTemplates().contains(template)) {
                    iter.remove();
                }
            }
        }

        final Map<PwmSetting.Category, List<PwmSetting>> returnMap = new TreeMap<PwmSetting.Category, List<PwmSetting>>();
        for (final PwmSetting loopSetting : settingList) {
            if (!returnMap.containsKey(loopSetting.getCategory())) {
                returnMap.put(loopSetting.getCategory(),new ArrayList<PwmSetting>());
            }
            returnMap.get(loopSetting.getCategory()).add(loopSetting);
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

