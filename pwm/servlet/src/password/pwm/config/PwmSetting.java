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
            "pwm.selfURL", PwmSettingSyntax.STRING, Category.GENERAL, false, Level.BASIC),
    VERSION_CHECK_ENABLE(
            "pwm.versionCheck.enable", PwmSettingSyntax.BOOLEAN, Category.GENERAL, true, Level.BASIC),
    PUBLISH_STATS_ENABLE(
            "pwm.publishStats.enable", PwmSettingSyntax.BOOLEAN, Category.GENERAL, true, Level.BASIC),
    PUBLISH_STATS_SITE_DESCRIPTION(
            "pwm.publishStats.siteDescription", PwmSettingSyntax.STRING, Category.GENERAL, false, Level.BASIC),
    URL_FORWARD(
            "pwm.forwardURL", PwmSettingSyntax.STRING, Category.GENERAL, false, Level.BASIC),
    URL_LOGOUT(
            "pwm.logoutURL", PwmSettingSyntax.STRING, Category.GENERAL, false, Level.BASIC),
    GOOGLE_ANAYLTICS_TRACKER(
            "google.analytics.tracker", PwmSettingSyntax.STRING, Category.GENERAL, false, Level.BASIC),
    PWM_INSTANCE_NAME(
            "pwmInstanceName", PwmSettingSyntax.STRING, Category.GENERAL, false, Level.ADVANCED),
    IDLE_TIMEOUT_SECONDS(
            "idleTimeoutSeconds", PwmSettingSyntax.NUMERIC, Category.GENERAL, true, Level.BASIC),
    DISPLAY_SHOW_DETAILED_ERRORS(
            "display.showDetailedErrors", PwmSettingSyntax.BOOLEAN, Category.GENERAL, true, Level.BASIC),
    HIDE_CONFIGURATION_HEALTH_WARNINGS(
            "display.hideConfigHealthWarnings", PwmSettingSyntax.BOOLEAN, Category.GENERAL, false, Level.ADVANCED),
    KNOWN_LOCALES(
            "knownLocales", PwmSettingSyntax.STRING_ARRAY, Category.GENERAL, false, Level.ADVANCED),

    // user interface
    INTERFACE_THEME(
            "interface.theme", PwmSettingSyntax.SELECT, Category.USER_INTERFACE, true, Level.BASIC),
    PASSWORD_SHOW_AUTOGEN(
            "password.showAutoGen", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    PASSWORD_SHOW_STRENGTH_METER(
            "password.showStrengthMeter", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_PASSWORD_GUIDE_TEXT(
            "display.password.guideText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.USER_INTERFACE, false, Level.BASIC),
    DISPLAY_SHOW_HIDE_PASSWORD_FIELDS(
            "display.showHidePasswordFields", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_CANCEL_BUTTON(
            "display.showCancelButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_RESET_BUTTON(
            "display.showResetButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_SUCCESS_PAGES(
            "display.showSuccessPage", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_PASSWORD_HISTORY(
            "display.passwordHistory", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_ACCOUNT_INFORMATION(
            "display.accountInformation", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_LOGIN_PAGE_OPTIONS(
            "display.showLoginPageOptions", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_LOGOUT_BUTTON(
            "display.logoutButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE, true, Level.BASIC),
    DISPLAY_CSS_CUSTOM_STYLE(
            "display.css.customStyleLocation", PwmSettingSyntax.STRING, Category.USER_INTERFACE, false, Level.ADVANCED),
    DISPLAY_CSS_CUSTOM_MOBILE_STYLE(
            "display.css.customMobileStyleLocation", PwmSettingSyntax.STRING, Category.USER_INTERFACE, false, Level.ADVANCED),
    DISPLAY_CSS_EMBED(
            "display.css.customStyle", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE, false, Level.ADVANCED),
    DISPLAY_CSS_MOBILE_EMBED(
            "display.css.customMobileStyle", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE, false, Level.ADVANCED),
    DISPLAY_CUSTOM_JAVASCRIPT(
            "display.js.custom", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE, false, Level.ADVANCED),

    // change password
    LOGOUT_AFTER_PASSWORD_CHANGE(
            "logoutAfterPasswordChange", PwmSettingSyntax.BOOLEAN, Category.CHANGE_PASSWORD, true, Level.BASIC),
    PASSWORD_REQUIRE_FORM(
            "password.require.form", PwmSettingSyntax.FORM, Category.CHANGE_PASSWORD, false, Level.BASIC),
    PASSWORD_REQUIRE_CURRENT(
            "password.change.requireCurrent", PwmSettingSyntax.SELECT, Category.CHANGE_PASSWORD, true, Level.BASIC),
    PASSWORD_CHANGE_AGREEMENT_MESSAGE(
            "display.password.changeAgreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.CHANGE_PASSWORD, false, Level.BASIC),
    PASSWORD_SYNC_MIN_WAIT_TIME(
            "passwordSyncMinWaitTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, Level.ADVANCED),
    PASSWORD_SYNC_MAX_WAIT_TIME(
            "passwordSyncMaxWaitTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, Level.ADVANCED),
    PASSWORD_EXPIRE_PRE_TIME(
            "expirePreTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, Level.BASIC),
    PASSWORD_EXPIRE_WARN_TIME(
            "expireWarnTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD, true, Level.BASIC),
    EXPIRE_CHECK_DURING_AUTH(
            "expireCheckDuringAuth", PwmSettingSyntax.BOOLEAN, Category.CHANGE_PASSWORD, true, Level.ADVANCED),
    SEEDLIST_FILENAME(
            "pwm.seedlist.location", PwmSettingSyntax.STRING, Category.CHANGE_PASSWORD, false, Level.ADVANCED),
    CHANGE_PASSWORD_WRITE_ATTRIBUTES(
            "changePassword.writeAttributes", PwmSettingSyntax.ACTION, Category.CHANGE_PASSWORD, false, Level.BASIC),

    //ldap directory
    LDAP_SERVER_URLS(
            "ldap.serverUrls", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, true, Level.BASIC),
    LDAP_PROMISCUOUS_SSL(
            "ldap.promiscuousSSL", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, Level.BASIC),
    LDAP_PROXY_USER_DN(
            "ldap.proxy.username", PwmSettingSyntax.STRING, Category.LDAP, true, Level.BASIC),
    LDAP_PROXY_USER_PASSWORD(
            "ldap.proxy.password", PwmSettingSyntax.PASSWORD, Category.LDAP, true, Level.BASIC),
    LDAP_CONTEXTLESS_ROOT(
            "ldap.rootContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, false, Level.BASIC),
    LDAP_LOGIN_CONTEXTS(
            "ldap.selectableContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, false, Level.ADVANCED),
    LDAP_TEST_USER_DN(
            "ldap.testuser.username", PwmSettingSyntax.STRING, Category.LDAP, false, Level.BASIC),
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", PwmSettingSyntax.STRING, Category.LDAP, true, Level.BASIC),
    LDAP_USERNAME_SEARCH_FILTER(
            "ldap.usernameSearchFilter", PwmSettingSyntax.STRING, Category.LDAP, true, Level.ADVANCED),
    LDAP_READ_PASSWORD_POLICY(
            "ldap.readPasswordPolicies", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, Level.ADVANCED),
    AUTO_ADD_OBJECT_CLASSES(
            "ldap.addObjectClasses", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, false, Level.ADVANCED),
    QUERY_MATCH_CHANGE_PASSWORD(
            "password.allowChange.queryMatch", PwmSettingSyntax.STRING, Category.LDAP, true, Level.ADVANCED),
    PASSWORD_LAST_UPDATE_ATTRIBUTE(
            "passwordLastUpdateAttribute", PwmSettingSyntax.STRING, Category.LDAP, false, Level.ADVANCED),
    LDAP_NAMING_ATTRIBUTE(
            "ldap.namingAttribute", PwmSettingSyntax.STRING, Category.LDAP, true, Level.ADVANCED),
    LDAP_IDLE_TIMEOUT(
            "ldap.idleTimeout", PwmSettingSyntax.NUMERIC, Category.LDAP, true, Level.ADVANCED),
    LDAP_GUID_ATTRIBUTE(
            "ldap.guidAttribute", PwmSettingSyntax.STRING, Category.LDAP, true, Level.ADVANCED),
    LDAP_GUID_AUTO_ADD(
            "ldap.guid.autoAddValue", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, Level.ADVANCED),
    LDAP_ENABLE_WIRE_TRACE(
            "ldap.wireTrace.enable", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, Level.ADVANCED),
    LDAP_ALWAYS_USE_PROXY(
            "ldap.alwaysUseProxy", PwmSettingSyntax.BOOLEAN, Category.LDAP, true, Level.ADVANCED),
    LDAP_CHAI_SETTINGS(
            "ldapChaiSettings", PwmSettingSyntax.STRING_ARRAY, Category.LDAP, false, Level.ADVANCED),
    LDAP_USERNAME_ATTRIBUTE(
            "ldap.username.attr", PwmSettingSyntax.STRING, Category.LDAP, false, Level.ADVANCED),

    // email settings
    EMAIL_SERVER_ADDRESS(
            "email.smtp.address", PwmSettingSyntax.STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_USERNAME(
            "email.smtp.username", PwmSettingSyntax.STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_PASSWORD(
            "email.smtp.userpassword", PwmSettingSyntax.PASSWORD, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_USER_MAIL_ATTRIBUTE(
            "email.userMailAttribute", PwmSettingSyntax.STRING, Category.EMAIL, true, Level.ADVANCED),
    EMAIL_MAX_QUEUE_AGE(
            "email.queueMaxAge", PwmSettingSyntax.NUMERIC, Category.EMAIL, true, Level.ADVANCED),
    EMAIL_ADMIN_ALERT_TO(
            "email.adminAlert.toAddress", PwmSettingSyntax.STRING_ARRAY, Category.EMAIL, false, Level.BASIC),
    EMAIL_ADMIN_ALERT_FROM(
            "email.adminAlert.fromAddress", PwmSettingSyntax.STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_CHANGEPASSWORD_FROM(
            "email.changePassword.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHANGEPASSWORD_SUBJECT(
            "email.changePassword.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHANGEPASSWORD_BODY(
            "email.changePassword.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHANGEPASSWORD_BODY_HMTL(
            "email.changePassword.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_SUBJECT(
            "email.newUser.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_FROM(
            "email.newUser.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_BODY(
            "email.newUser.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_BODY_HTML(
            "email.newUser.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_VERIFICATION_SUBJECT(
            "email.newUser.token.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_VERIFICATION_FROM(
            "email.newUser.token.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_VERIFICATION_BODY(
            "email.newUser.token.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_NEWUSER_VERIFICATION_BODY_HTML(
            "email.newUser.token.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_SUBJECT(
            "email.activation.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_FROM(
            "email.activation.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_BODY(
            "email.activation.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_BODY_HTML(
            "email.activation.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_VERIFICATION_SUBJECT(
            "email.activation.token.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_VERIFICATION_FROM(
            "email.activation.token.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_VERIFICATION_BODY(
            "email.activation.token.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_ACTIVATION_VERIFICATION_BODY_HTML(
            "email.activation.token.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHALLENGE_TOKEN_FROM(
            "email.challenge.token.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHALLENGE_TOKEN_SUBJECT(
            "email.challenge.token.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHALLENGE_TOKEN_BODY(
            "email.challenge.token.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_CHALLENGE_TOKEN_BODY_HTML(
            "email.challenge.token.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.BASIC),
    EMAIL_GUEST_SUBJECT(
            "email.guest.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_GUEST_FROM(
            "email.guest.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_GUEST_BODY(
            "email.guest.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_GUEST_BODY_HTML(
            "email.guest.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_SUBJECT(
            "email.updateguest.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_FROM(
            "email.updateguest.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_BODY(
            "email.updateguest.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_UPDATEGUEST_BODY_HTML(
            "email.updateguest.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_SENDPASSWORD_SUBJECT(
            "email.sendpassword.subject", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_SENDPASSWORD_FROM(
            "email.sendpassword.from", PwmSettingSyntax.LOCALIZED_STRING, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_SENDPASSWORD_BODY(
            "email.sendpassword.plainBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_SENDPASSWORD_BODY_HTML(
            "email.sendpassword.htmlBody", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.EMAIL, false, Level.ADVANCED),
    EMAIL_ADVANCED_SETTINGS(
            "email.smtp.advancedSettings", PwmSettingSyntax.STRING_ARRAY, Category.EMAIL, false, Level.ADVANCED),

    // sms settings
    SMS_USER_PHONE_ATTRIBUTE(
            "sms.userSmsAttribute", PwmSettingSyntax.STRING, Category.SMS, true, Level.BASIC),
    SMS_MAX_QUEUE_AGE(
            "sms.queueMaxAge", PwmSettingSyntax.NUMERIC, Category.SMS, true, Level.ADVANCED),
    SMS_GATEWAY_URL(
            "sms.gatewayURL", PwmSettingSyntax.STRING, Category.SMS, true, Level.BASIC),
    SMS_GATEWAY_USER(
            "sms.gatewayUser", PwmSettingSyntax.STRING, Category.SMS, false, Level.BASIC),
    SMS_GATEWAY_PASSWORD(
            "sms.gatewayPassword", PwmSettingSyntax.PASSWORD, Category.SMS, false, Level.BASIC),
    SMS_GATEWAY_METHOD(
            "sms.gatewayMethod", PwmSettingSyntax.SELECT, Category.SMS, true, Level.BASIC),
    SMS_GATEWAY_AUTHMETHOD(
            "sms.gatewayAuthMethod", PwmSettingSyntax.SELECT, Category.SMS, true, Level.ADVANCED),
    SMS_REQUEST_DATA(
            "sms.requestData", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.SMS, false, Level.ADVANCED),
    SMS_REQUEST_CONTENT_TYPE(
            "sms.requestContentType", PwmSettingSyntax.STRING, Category.SMS, false, Level.ADVANCED),
    SMS_REQUEST_CONTENT_ENCODING(
            "sms.requestContentEncoding", PwmSettingSyntax.SELECT, Category.SMS, true, Level.ADVANCED),
    SMS_GATEWAY_REQUEST_HEADERS(
            "sms.httpRequestHeaders", PwmSettingSyntax.STRING_ARRAY, Category.SMS, false, Level.ADVANCED),
    SMS_MAX_TEXT_LENGTH(
            "sms.maxTextLength", PwmSettingSyntax.NUMERIC, Category.SMS, true, Level.ADVANCED),
    SMS_RESPONSE_OK_REGEX(
            "sms.responseOkRegex", PwmSettingSyntax.STRING_ARRAY, Category.SMS, false, Level.ADVANCED),
    SMS_SENDER_ID(
            "sms.senderID", PwmSettingSyntax.STRING, Category.SMS, false, Level.BASIC),
    SMS_PHONE_NUMBER_FORMAT(
            "sms.phoneNumberFormat", PwmSettingSyntax.SELECT, Category.SMS, true, Level.ADVANCED),
    SMS_DEFAULT_COUNTRY_CODE(
            "sms.defaultCountryCode", PwmSettingSyntax.NUMERIC, Category.SMS, false, Level.ADVANCED),
    SMS_REQUESTID_CHARS(
            "sms.requestId.characters", PwmSettingSyntax.STRING, Category.SMS, true, Level.ADVANCED),
    SMS_REQUESTID_LENGTH(
            "sms.requestId.length", PwmSettingSyntax.NUMERIC, Category.SMS, true, Level.ADVANCED),
    SMS_CHALLENGE_TOKEN_TEXT(
            "sms.challenge.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, Level.BASIC),
    SMS_NEWUSER_TOKEN_TEXT(
            "sms.newUser.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, Level.BASIC),
    SMS_ACTIVATION_VERIFICATION_TEXT(
            "sms.activation.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, Level.BASIC),
    SMS_ACTIVATION_TEXT(
            "sms.activation.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS, true, Level.BASIC),
    SMS_USE_URL_SHORTENER(
            "sms.useUrlShortener", PwmSettingSyntax.BOOLEAN, Category.SMS, false, Level.ADVANCED),

    //global password policy settings
    PASSWORD_POLICY_SOURCE(
            "password.policy.source", PwmSettingSyntax.SELECT, Category.PASSWORD_POLICY, true, Level.ADVANCED),
    PASSWORD_POLICY_MINIMUM_LENGTH(
            "password.policy.minimumLength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_LENGTH(
            "password.policy.maximumLength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_REPEAT(
            "password.policy.maximumRepeat", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT(
            "password.policy.maximumSequentialRepeat", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_NUMERIC(
            "password.policy.allowNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC(
            "password.policy.allowFirstCharNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC(
            "password.policy.allowLastCharNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_NUMERIC(
            "password.policy.maximumNumeric", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_NUMERIC(
            "password.policy.minimumNumeric", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_SPECIAL(
            "password.policy.allowSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL(
            "password.policy.allowFirstCharSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL(
            "password.policy.allowLastCharSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_SPECIAL(
            "password.policy.maximumSpecial", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_SPECIAL(
            "password.policy.minimumSpecial", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_ALPHA(
            "password.policy.maximumAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_ALPHA(
            "password.policy.minimumAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_NON_ALPHA(
            "password.policy.maximumNonAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_NON_ALPHA(
            "password.policy.minimumNonAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_UPPERCASE(
            "password.policy.maximumUpperCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_UPPERCASE(
            "password.policy.minimumUpperCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_LOWERCASE(
            "password.policy.maximumLowerCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_LOWERCASE(
            "password.policy.minimumLowerCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MINIMUM_UNIQUE(
            "password.policy.minimumUnique", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS(
            "password.policy.maximumOldPasswordChars", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_CASE_SENSITIVITY(
            "password.policy.caseSensitivity", PwmSettingSyntax.SELECT, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_ENABLE_WORDLIST(
            "password.policy.checkWordlist", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_AD_COMPLEXITY(
            "password.policy.ADComplexity", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH(
            "password.policy.regExMatch", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.ADVANCED),
    PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH(
            "password.policy.regExNoMatch", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.ADVANCED),
    PASSWORD_POLICY_DISALLOWED_VALUES(
            "password.policy.disallowedValues", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.BASIC),
    PASSWORD_POLICY_DISALLOWED_ATTRIBUTES(
            "password.policy.disallowedAttributes", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY, false, Level.ADVANCED),
    PASSWORD_SHAREDHISTORY_ENABLE(
            "password.sharedHistory.enable", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_SHAREDHISTORY_MAX_AGE(
            "password.sharedHistory.age", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.ADVANCED),
    PASSWORD_POLICY_MINIMUM_STRENGTH(
            "password.policy.minimumStrength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_POLICY_CHANGE_MESSAGE(
            "password.policy.changeMessage", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY, false, Level.BASIC),
    PASSWORD_POLICY_RULE_TEXT(
            "password.policy.ruleText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY, false, Level.ADVANCED),
    WORDLIST_FILENAME(
            "pwm.wordlist.location", PwmSettingSyntax.STRING, Category.PASSWORD_POLICY, false, Level.BASIC),
    WORDLIST_CASE_SENSITIVE(
            "wordlistCaseSensitive", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY, true, Level.BASIC),
    PASSWORD_WORDLIST_WORDSIZE(
            "password.wordlist.wordSize", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY, true, Level.ADVANCED),


    // security settings
    PWM_SECURITY_KEY(
            "pwm.securityKey", PwmSettingSyntax.PASSWORD, Category.SECURITY, false, Level.BASIC),
    INTRUDER_USER_RESET_TIME(
            "intruder.user.resetTime", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, Level.BASIC),
    INTRUDER_USER_MAX_ATTEMPTS(
            "intruder.user.maxAttempts", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, Level.BASIC ),
    INTRUDER_ADDRESS_RESET_TIME(
            "intruder.address.resetTime", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, Level.BASIC),
    INTRUDER_ADDRESS_MAX_ATTEMPTS(
            "intruder.address.maxAttempts", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, Level.BASIC),
    SECURITY_SIMULATE_LDAP_BAD_PASSWORD(
            "security.ldap.simulateBadPassword", PwmSettingSyntax.BOOLEAN, Category.SECURITY, false, Level.BASIC),
    RECAPTCHA_KEY_PUBLIC(
            "captcha.recaptcha.publicKey", PwmSettingSyntax.STRING, Category.SECURITY, false, Level.BASIC),
    RECAPTCHA_KEY_PRIVATE(
            "captcha.recaptcha.privateKey", PwmSettingSyntax.PASSWORD, Category.SECURITY, false, Level.BASIC),
    CAPTCHA_SKIP_PARAM(
            "captcha.skip.param", PwmSettingSyntax.STRING, Category.SECURITY, false, Level.ADVANCED),
    CAPTCHA_SKIP_COOKIE(
            "captcha.skip.cookie", PwmSettingSyntax.STRING, Category.SECURITY, false, Level.ADVANCED),
    SECURITY_ENABLE_REQUEST_SEQUENCE(
            "security.page.enableRequestSequence", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.ADVANCED),
    SECURITY_ENABLE_FORM_NONCE(
            "security.formNonce.enable", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.ADVANCED),
    ALLOW_URL_SESSIONS(
            "allowUrlSessions", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.ADVANCED),
    ENABLE_SESSION_VERIFICATION(
            "enableSessionVerification", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.ADVANCED),
    DISALLOWED_HTTP_INPUTS(
            "disallowedInputs", PwmSettingSyntax.STRING_ARRAY, Category.SECURITY, false, Level.ADVANCED),
    REQUIRE_HTTPS(
            "pwm.requireHTTPS", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.BASIC),
    FORCE_BASIC_AUTH(
            "forceBasicAuth", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.ADVANCED),
    USE_X_FORWARDED_FOR_HEADER(
            "useXForwardedForHeader", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.ADVANCED),
    REVERSE_DNS_ENABLE(
            "network.reverseDNS.enable", PwmSettingSyntax.BOOLEAN, Category.SECURITY, true, Level.ADVANCED),
    SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT(
            "security.page.leaveNoticeTimeout", PwmSettingSyntax.NUMERIC, Category.SECURITY, true, Level.ADVANCED),

    // token settings
    TOKEN_STORAGEMETHOD(
            "token.storageMethod", PwmSettingSyntax.SELECT, Category.TOKEN, true, Level.BASIC),
    TOKEN_CHARACTERS(
            "token.characters", PwmSettingSyntax.STRING, Category.TOKEN, true, Level.BASIC),
    TOKEN_LENGTH(
            "token.length", PwmSettingSyntax.NUMERIC, Category.TOKEN, true, Level.BASIC),
    TOKEN_LIFETIME(
            "token.lifetime", PwmSettingSyntax.NUMERIC, Category.TOKEN, true, Level.BASIC),
    TOKEN_LDAP_ATTRIBUTE(
            "token.ldap.attribute", PwmSettingSyntax.STRING, Category.TOKEN, true, Level.BASIC),

    // logger settings
    EVENTS_HEALTH_CHECK_MIN_INTERVAL(
            "events.healthCheck.minInterval", PwmSettingSyntax.NUMERIC, Category.LOGGING, false, Level.ADVANCED),
    EVENTS_JAVA_STDOUT_LEVEL(
            "events.java.stdoutLevel", PwmSettingSyntax.SELECT, Category.LOGGING, false, Level.BASIC),
    EVENTS_JAVA_LOG4JCONFIG_FILE(
            "events.java.log4jconfigFile", PwmSettingSyntax.STRING, Category.LOGGING, false, Level.ADVANCED),
    EVENTS_PWMDB_MAX_EVENTS(
            "events.pwmDB.maxEvents", PwmSettingSyntax.NUMERIC, Category.LOGGING, true, Level.ADVANCED),
    EVENTS_PWMDB_MAX_AGE(
            "events.pwmDB.maxAge", PwmSettingSyntax.NUMERIC, Category.LOGGING, true, Level.ADVANCED),
    EVENTS_PWMDB_LOG_LEVEL(
            "events.pwmDB.logLevel", PwmSettingSyntax.SELECT, Category.LOGGING, true, Level.BASIC),
    EVENTS_LDAP_ATTRIBUTE(
            "events.ldap.attribute", PwmSettingSyntax.STRING, Category.LOGGING, false, Level.ADVANCED),
    EVENTS_LDAP_MAX_EVENTS(
            "events.ldap.maxEvents", PwmSettingSyntax.NUMERIC, Category.LOGGING, true, Level.ADVANCED),
    EVENTS_ALERT_STARTUP(
            "events.alert.startup.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_SHUTDOWN(
            "events.alert.shutdown.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_INTRUDER_LOCKOUT(
            "events.alert.intruder.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_FATAL_EVENT(
            "events.alert.fatalEvent.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_CONFIG_MODIFY(
            "events.alert.configModify.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),
    EVENTS_ALERT_DAILY_SUMMARY(
            "events.alert.dailySummary.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING, true, Level.BASIC),


    // challenge policy
    CHALLENGE_ENABLE(
            "challenge.enable", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, false, Level.BASIC),
    CHALLENGE_FORCE_SETUP(
            "challenge.forceSetup", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, Level.BASIC),
    CHALLENGE_RANDOM_CHALLENGES(
            "challenge.randomChallenges", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, Category.CHALLENGE, false, Level.BASIC),
    CHALLENGE_REQUIRED_CHALLENGES(
            "challenge.requiredChallenges", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, Category.CHALLENGE, false, Level.BASIC),
    CHALLENGE_MIN_RANDOM_REQUIRED(
            "challenge.minRandomRequired", PwmSettingSyntax.NUMERIC, Category.CHALLENGE, true, Level.BASIC),
    CHALLENGE_MIN_RANDOM_SETUP(
            "challenge.minRandomsSetup", PwmSettingSyntax.NUMERIC, Category.CHALLENGE, true, Level.BASIC),
    CHALLENGE_SHOW_CONFIRMATION(
            "challenge.showConfirmation", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, Level.BASIC),
    CHALLENGE_CASE_INSENSITIVE(
            "challenge.caseInsensitive", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, Level.ADVANCED),
    CHALLENGE_MAX_LENGTH_CHALLENGE_IN_RESPONSE(
            "challenge.maxChallengeLengthInResponse", PwmSettingSyntax.NUMERIC, Category.CHALLENGE, true, Level.ADVANCED),
    CHALLENGE_ALLOW_DUPLICATE_RESPONSES(
            "challenge.allowDuplicateResponses", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, Level.ADVANCED),
    CHALLENGE_APPLY_WORDLIST(
            "challenge.applyWorldlist", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE, true, Level.BASIC),
    QUERY_MATCH_SETUP_RESPONSE(
            "challenge.allowSetup.queryMatch", PwmSettingSyntax.STRING, Category.CHALLENGE, true, Level.ADVANCED),
    QUERY_MATCH_CHECK_RESPONSES(
            "command.checkResponses.queryMatch", PwmSettingSyntax.STRING, Category.CHALLENGE, true, Level.ADVANCED),


    // recovery settings
    FORGOTTEN_PASSWORD_ENABLE(
            "recovery.enable", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, false, Level.BASIC),
    FORGOTTEN_PASSWORD_SEARCH_FORM(
            "recovery.form", PwmSettingSyntax.FORM, Category.RECOVERY, true, Level.BASIC),
    FORGOTTEN_PASSWORD_SEARCH_FILTER(
            "recovery.searchFilter", PwmSettingSyntax.STRING, Category.RECOVERY, true, Level.BASIC),
    FORGOTTEN_PASSWORD_READ_PREFERENCE(
            "recovery.response.readPreference", PwmSettingSyntax.SELECT, Category.RECOVERY, true, Level.BASIC),
    FORGOTTEN_PASSWORD_WRITE_PREFERENCE(
            "recovery.response.writePreference", PwmSettingSyntax.SELECT, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_USER_ATTRIBUTE(
            "challenge.userAttribute", PwmSettingSyntax.STRING, Category.RECOVERY, false, Level.BASIC),
    CHALLENGE_ALLOW_UNLOCK(
            "challenge.allowUnlock", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_STORAGE_HASHED(
            "challenge.storageHashed", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, true, Level.ADVANCED),
    CHALLENGE_REQUIRED_ATTRIBUTES(
            "challenge.requiredAttributes", PwmSettingSyntax.FORM, Category.RECOVERY, false, Level.BASIC),
    CHALLENGE_REQUIRE_RESPONSES(
            "challenge.requireResponses", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, false, Level.BASIC),
    CHALLENGE_TOKEN_ENABLE(
            "challenge.token.enable", PwmSettingSyntax.BOOLEAN, Category.RECOVERY, true, Level.BASIC),
    CHALLENGE_TOKEN_SEND_METHOD(
            "challenge.token.sendMethod", PwmSettingSyntax.SELECT, Category.RECOVERY, true, Level.BASIC),
    FORGOTTEN_PASSWORD_ACTION(
            "recovery.action", PwmSettingSyntax.SELECT, Category.RECOVERY, true, Level.BASIC),


    // forgotten username
    FORGOTTEN_USERNAME_ENABLE(
            "forgottenUsername.enable", PwmSettingSyntax.BOOLEAN, Category.FORGOTTEN_USERNAME, true, Level.BASIC),
    FORGOTTEN_USERNAME_FORM(
            "forgottenUsername.form", PwmSettingSyntax.FORM, Category.FORGOTTEN_USERNAME, true, Level.BASIC),
    FORGOTTEN_USERNAME_SEARCH_FILTER(
            "forgottenUsername.searchFilter", PwmSettingSyntax.STRING, Category.FORGOTTEN_USERNAME, true, Level.BASIC),
    FORGOTTEN_USERNAME_USERNAME_ATTRIBUTE(
            "forgottenUsername.usernameAttribute", PwmSettingSyntax.STRING, Category.FORGOTTEN_USERNAME, true, Level.BASIC),

    // new user settings
    NEWUSER_ENABLE(
            "newUser.enable", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, true, Level.BASIC),
    NEWUSER_CONTEXT(
            "newUser.createContext", PwmSettingSyntax.STRING, Category.NEWUSER, true, Level.BASIC),
    NEWUSER_AGREEMENT_MESSAGE(
            "display.newuser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.NEWUSER, false, Level.BASIC),
    NEWUSER_FORM(
            "newUser.form", PwmSettingSyntax.FORM, Category.NEWUSER, true, Level.BASIC),
    NEWUSER_UNIQUE_ATTRIBUES(
            "newUser.creationUniqueAttributes", PwmSettingSyntax.STRING_ARRAY, Category.NEWUSER, false, Level.BASIC),
    NEWUSER_WRITE_ATTRIBUTES(
            "newUser.writeAttributes", PwmSettingSyntax.ACTION, Category.NEWUSER, false, Level.BASIC),
    NEWUSER_DELETE_ON_FAIL(
            "newUser.deleteOnFail", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, false, Level.ADVANCED),
    NEWUSER_USERNAME_CHARS(
            "newUser.username.characters", PwmSettingSyntax.STRING, Category.NEWUSER, false, Level.ADVANCED),
    NEWUSER_USERNAME_LENGTH(
            "newUser.username.length", PwmSettingSyntax.NUMERIC, Category.NEWUSER, false, Level.ADVANCED),
    NEWUSER_EMAIL_VERIFICATION(
            "newUser.email.verification", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, false, Level.BASIC),
    NEWUSER_SMS_VERIFICATION(
            "newUser.sms.verification", PwmSettingSyntax.BOOLEAN, Category.NEWUSER, false, Level.BASIC),
    NEWUSER_PASSWORD_POLICY_USER(
            "newUser.passwordPolicy.user", PwmSettingSyntax.STRING, Category.NEWUSER, false, Level.BASIC),
    NEWUSER_MINIMUM_WAIT_TIME(
            "newUser.minimumWaitTime", PwmSettingSyntax.NUMERIC, Category.NEWUSER, false, Level.BASIC),
    DEFAULT_OBJECT_CLASSES(
            "ldap.defaultObjectClasses", PwmSettingSyntax.STRING_ARRAY, Category.NEWUSER, false, Level.ADVANCED),


    // guest settings
    GUEST_ENABLE(
            "guest.enable", PwmSettingSyntax.BOOLEAN, Category.GUEST, true, Level.BASIC),
    GUEST_CONTEXT(
            "guest.createContext", PwmSettingSyntax.STRING, Category.GUEST, true, Level.BASIC),
    GUEST_ADMIN_GROUP(
            "guest.adminGroup", PwmSettingSyntax.STRING, Category.GUEST, true, Level.BASIC),
    GUEST_FORM(
            "guest.form", PwmSettingSyntax.FORM, Category.GUEST, true, Level.BASIC),
    GUEST_UPDATE_FORM(
            "guest.update.form", PwmSettingSyntax.FORM, Category.GUEST, true, Level.BASIC),
    GUEST_UNIQUE_ATTRIBUTES(
            "guest.creationUniqueAttributes", PwmSettingSyntax.STRING_ARRAY, Category.GUEST, false, Level.BASIC),
    GUEST_WRITE_ATTRIBUTES(
            "guest.writeAttributes", PwmSettingSyntax.ACTION, Category.GUEST, false, Level.BASIC),
    GUEST_ADMIN_ATTRIBUTE(
            "guest.adminAttribute", PwmSettingSyntax.STRING, Category.GUEST, false, Level.BASIC),
    GUEST_EDIT_ORIG_ADMIN_ONLY(
            "guest.editOriginalAdminOnly", PwmSettingSyntax.BOOLEAN, Category.GUEST, true, Level.BASIC),
    GUEST_MAX_VALID_DAYS(
            "guest.maxValidDays", PwmSettingSyntax.NUMERIC, Category.GUEST, true, Level.BASIC),
    GUEST_EXPIRATION_ATTRIBUTE (
            "guest.expirationAttribute", PwmSettingSyntax.STRING, Category.GUEST, false, Level.BASIC),

    // activation settings
    ACTIVATE_USER_ENABLE(
            "activateUser.enable", PwmSettingSyntax.BOOLEAN, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_TOKEN_VERIFICATION(
            "activateUser.token.verification", PwmSettingSyntax.BOOLEAN, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_AGREEMENT_MESSAGE(
            "display.activateUser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.ACTIVATION, false, Level.BASIC),
    ACTIVATE_USER_FORM(
            "activateUser.form", PwmSettingSyntax.FORM, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_SEARCH_FILTER(
            "activateUser.searchFilter", PwmSettingSyntax.STRING, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_QUERY_MATCH(
            "activateUser.queryMatch", PwmSettingSyntax.STRING, Category.ACTIVATION, true, Level.BASIC),
    ACTIVATE_USER_PRE_WRITE_ATTRIBUTES(
            "activateUser.writePreAttributes", PwmSettingSyntax.STRING_ARRAY, Category.ACTIVATION, false, Level.BASIC),
    ACTIVATE_USER_POST_WRITE_ATTRIBUTES(
            "activateUser.writePostAttributes", PwmSettingSyntax.STRING_ARRAY, Category.ACTIVATION, false, Level.BASIC),
    ACTIVATE_TOKEN_SEND_METHOD(
            "activateUser.token.sendMethod", PwmSettingSyntax.SELECT, Category.ACTIVATION, true, Level.BASIC),

    // update profile
    UPDATE_PROFILE_ENABLE(
            "updateAttributes.enable", PwmSettingSyntax.BOOLEAN, Category.UPDATE, true, Level.BASIC),
    UPDATE_PROFILE_FORCE_SETUP(
            "updateAttributes.forceSetup", PwmSettingSyntax.BOOLEAN, Category.UPDATE, true, Level.BASIC),
    UPDATE_PROFILE_AGREEMENT_MESSAGE(
            "display.updateAttributes.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.UPDATE, false, Level.BASIC),
    UPDATE_PROFILE_QUERY_MATCH(
            "updateAttributes.queryMatch", PwmSettingSyntax.STRING, Category.UPDATE, true, Level.BASIC),
    UPDATE_PROFILE_WRITE_ATTRIBUTES(
            "updateAttributes.writeAttributes", PwmSettingSyntax.ACTION, Category.UPDATE, false, Level.BASIC),
    UPDATE_PROFILE_FORM(
            "updateAttributes.form", PwmSettingSyntax.FORM, Category.UPDATE, true, Level.BASIC),
    UPDATE_PROFILE_CHECK_QUERY_MATCH(
            "updateAttributes.check.queryMatch", PwmSettingSyntax.STRING, Category.UPDATE, false, Level.BASIC),
    UPDATE_PROFILE_SHOW_CONFIRMATION(
            "updateAttributes.showConfirmation", PwmSettingSyntax.BOOLEAN, Category.UPDATE, true, Level.BASIC),

    // shortcut settings
    SHORTCUT_ENABLE(
            "shortcut.enable", PwmSettingSyntax.BOOLEAN, Category.SHORTCUT, false, Level.BASIC),
    SHORTCUT_ITEMS(
            "shortcut.items", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, Category.SHORTCUT, false, Level.BASIC),
    SHORTCUT_HEADER_NAMES(
            "shortcut.httpHeaders", PwmSettingSyntax.STRING_ARRAY, Category.SHORTCUT, false, Level.BASIC),

    // peoplesearch settings
    PEOPLE_SEARCH_ENABLE(
            "peopleSearch.enable", PwmSettingSyntax.BOOLEAN, Category.PEOPLE_SEARCH, false, Level.BASIC),
    PEOPLE_SEARCH_QUERY_MATCH(
            "peopleSearch.queryMatch", PwmSettingSyntax.STRING, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_SEARCH_FILTER(
            "peopleSearch.searchFilter", PwmSettingSyntax.STRING, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_SEARCH_BASE(
            "peopleSearch.searchBase", PwmSettingSyntax.STRING_ARRAY, Category.PEOPLE_SEARCH, false, Level.BASIC),
    PEOPLE_SEARCH_RESULT_FORM(
            "peopleSearch.result.form", PwmSettingSyntax.FORM, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_DETAIL_FORM(
            "peopleSearch.detail.form", PwmSettingSyntax.FORM, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_RESULT_LIMIT(
            "peopleSearch.result.limit", PwmSettingSyntax.NUMERIC, Category.PEOPLE_SEARCH, true, Level.BASIC),
    PEOPLE_SEARCH_USE_PROXY(
            "peopleSearch.useProxy", PwmSettingSyntax.BOOLEAN, Category.PEOPLE_SEARCH, true, Level.ADVANCED),

    // edirectory settings
    EDIRECTORY_ENABLE_NMAS(
            "ldap.edirectory.enableNmas", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, true, Level.BASIC),
    EDIRECTORY_STORE_NMAS_RESPONSES(
            "ldap.edirectory.storeNmasResponses", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, true, Level.BASIC),
    EDIRECTORY_READ_USER_PWD(
            "ldap.edirectory.readUserPwd", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, false, Level.ADVANCED),
    EDIRECTORY_READ_CHALLENGE_SET(
            "ldap.edirectory.readChallengeSets", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, true, Level.BASIC),
    EDIRECTORY_PWD_MGT_WEBSERVICE_URL(
            "ldap.edirectory.ws.pwdMgtURL", PwmSettingSyntax.STRING, Category.EDIRECTORY, false, Level.BASIC),


    // helpdesk
    HELPDESK_ENABLE(
            "helpdesk.enable", PwmSettingSyntax.BOOLEAN, Category.HELPDESK, false, Level.BASIC),
    HELPDESK_QUERY_MATCH(
            "helpdesk.queryMatch", PwmSettingSyntax.STRING, Category.HELPDESK, true, Level.BASIC),
    HELPDESK_SEARCH_FILTER(
            "helpdesk.filter", PwmSettingSyntax.STRING, Category.HELPDESK, false, Level.ADVANCED),
    HELPDESK_SEARCH_FORM(
            "helpdesk.result.form", PwmSettingSyntax.FORM, Category.HELPDESK, true, Level.BASIC),
    HELPDESK_DETAIL_FORM(
            "helpdesk.detail.form", PwmSettingSyntax.FORM, Category.HELPDESK, true, Level.BASIC),
    HELPDESK_SEARCH_BASE(
            "helpdesk.searchBase", PwmSettingSyntax.STRING_ARRAY, Category.HELPDESK, false, Level.BASIC),
    HELPDESK_RESULT_LIMIT(
            "helpdesk.result.limit", PwmSettingSyntax.NUMERIC, Category.HELPDESK, true, Level.ADVANCED),
    HELPDESK_SET_PASSWORD_MODE(
            "helpdesk.setPassword.mode", PwmSettingSyntax.SELECT, Category.HELPDESK, false, Level.BASIC),
    HELPDESK_POST_SET_PASSWORD_WRITE_ATTRIBUTES(
            "helpdesk.setPassword.writeAttributes", PwmSettingSyntax.ACTION, Category.HELPDESK, false, Level.ADVANCED),
    HELPDESK_ENABLE_UNLOCK(
            "helpdesk.enableUnlock", PwmSettingSyntax.BOOLEAN, Category.HELPDESK, true, Level.BASIC),
    HELPDESK_ENFORCE_PASSWORD_POLICY(
            "helpdesk.enforcePasswordPolicy", PwmSettingSyntax.BOOLEAN, Category.HELPDESK, false, Level.BASIC),
    HELPDESK_IDLE_TIMEOUT_SECONDS(
            "helpdesk.idleTimeout", PwmSettingSyntax.NUMERIC, Category.HELPDESK, false, Level.BASIC),
    HELPDESK_CLEAR_RESPONSES(
            "helpdesk.clearResponses", PwmSettingSyntax.SELECT, Category.HELPDESK, false, Level.BASIC),


    // Database
    PWMDB_LOCATION(
            "pwmDb.location", PwmSettingSyntax.STRING, Category.DATABASE, true, Level.BASIC),
    PWMDB_IMPLEMENTATION(
            "pwmDb.implementation", PwmSettingSyntax.STRING, Category.DATABASE, true, Level.ADVANCED),
    PWMDB_INIT_STRING(
            "pwmDb.initParameters", PwmSettingSyntax.STRING_ARRAY, Category.DATABASE, false, Level.ADVANCED),
    DATABASE_CLASS(
            "db.classname", PwmSettingSyntax.STRING, Category.DATABASE, false, Level.BASIC),
    DATABASE_URL(
            "db.url", PwmSettingSyntax.STRING, Category.DATABASE, false, Level.BASIC),
    DATABASE_USERNAME(
            "db.username", PwmSettingSyntax.STRING, Category.DATABASE, false, Level.BASIC),
    DATABASE_PASSWORD(
            "db.password", PwmSettingSyntax.PASSWORD, Category.DATABASE, false, Level.BASIC),

    // misc
    EXTERNAL_CHANGE_METHODS(
            "externalChangeMethod", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, Level.BASIC),
    EXTERNAL_JUDGE_METHODS(
            "externalJudgeMethod", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, Level.BASIC),
    EXTERNAL_RULE_METHODS(
            "externalRuleMethod", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, Level.BASIC),
    HTTP_PROXY_URL(
            "http.proxy.url", PwmSettingSyntax.STRING, Category.MISC, false, Level.BASIC),
    CAS_CLEAR_PASS_URL(
            "cas.clearPassUrl", PwmSettingSyntax.STRING, Category.MISC, false, Level.BASIC),
    URL_SHORTENER_CLASS(
            "urlshortener.classname", PwmSettingSyntax.STRING, Category.MISC, false, Level.ADVANCED),
    URL_SHORTENER_REGEX(
            "urlshortener.regex", PwmSettingSyntax.STRING, Category.MISC, false, Level.ADVANCED),
    URL_SHORTENER_PARAMETERS(
            "urlshortener.parameters", PwmSettingSyntax.STRING_ARRAY, Category.MISC, false, Level.ADVANCED),
    ENABLE_EXTERNAL_WEBSERVICES(
            "external.webservices.enable", PwmSettingSyntax.BOOLEAN, Category.MISC, false, Level.BASIC),

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
    private final Level level;

// --------------------------- CONSTRUCTORS ---------------------------

    PwmSetting(
            final String key,
            final PwmSettingSyntax syntax,
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
        return PwmSettingSyntax.PASSWORD == this.getSyntax();
    }

    public Category getCategory() {
        return category;
    }

    public PwmSettingSyntax getSyntax() {
        return syntax;
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

    public Level getLevel() {
        return level;
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
        LDAP(0),
        GENERAL(0),
        USER_INTERFACE(0),
        PASSWORD_POLICY(0),
        CHALLENGE(0),
        EMAIL(0),
        SMS(0),
        SECURITY(0),
        TOKEN(0),
        LOGGING(0),
        EDIRECTORY(0),
        DATABASE(0),
        MISC(0),
        CHANGE_PASSWORD(1),
        RECOVERY(1),
        FORGOTTEN_USERNAME(1),
        NEWUSER(1),
        GUEST(1),
        ACTIVATION(1),
        UPDATE(1),
        SHORTCUT(1),
        PEOPLE_SEARCH(1),
        HELPDESK(1),
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

    public enum SmsPriority {
        EMAILONLY,
        BOTH,
        EMAILFIRST,
        SMSFIRST,
        SMSONLY
    }

    public enum Level {
        BASIC,
        ADVANCED
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

