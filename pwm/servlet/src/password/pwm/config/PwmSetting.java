/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.PwmLogger;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * PwmConfiguration settings.
 *
 * @author Jason D. Rivard
 */
public enum PwmSetting {
    // application settings
    PWM_URL(
            "pwm.selfURL", PwmSettingSyntax.STRING, Category.GENERAL),
    VERSION_CHECK_ENABLE(
            "pwm.versionCheck.enable", PwmSettingSyntax.BOOLEAN, Category.GENERAL),
    PUBLISH_STATS_ENABLE(
            "pwm.publishStats.enable", PwmSettingSyntax.BOOLEAN, Category.GENERAL),
    PUBLISH_STATS_SITE_DESCRIPTION(
            "pwm.publishStats.siteDescription", PwmSettingSyntax.STRING, Category.GENERAL),
    URL_FORWARD(
            "pwm.forwardURL", PwmSettingSyntax.STRING, Category.GENERAL),
    URL_LOGOUT(
            "pwm.logoutURL", PwmSettingSyntax.STRING, Category.GENERAL),
    PWM_INSTANCE_NAME(
            "pwmInstanceName", PwmSettingSyntax.STRING, Category.GENERAL),
    IDLE_TIMEOUT_SECONDS(
            "idleTimeoutSeconds", PwmSettingSyntax.NUMERIC, Category.GENERAL),
    HIDE_CONFIGURATION_HEALTH_WARNINGS(
            "display.hideConfigHealthWarnings", PwmSettingSyntax.BOOLEAN, Category.GENERAL),
    KNOWN_LOCALES(
            "knownLocales", PwmSettingSyntax.STRING_ARRAY, Category.GENERAL),
    PWMDB_LOCATION(
            "pwmDb.location", PwmSettingSyntax.STRING, Category.GENERAL),


    // user interface
    INTERFACE_THEME(
            "interface.theme", PwmSettingSyntax.SELECT, Category.USER_INTERFACE),
    PASSWORD_SHOW_AUTOGEN(
            "password.showAutoGen", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    PASSWORD_SHOW_STRENGTH_METER(
            "password.showStrengthMeter", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_SHOW_HIDE_PASSWORD_FIELDS(
            "display.showHidePasswordFields", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_CANCEL_BUTTON(
            "display.showCancelButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_RESET_BUTTON(
            "display.showResetButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_SUCCESS_PAGES(
            "display.showSuccessPage", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_PASSWORD_HISTORY(
            "display.passwordHistory", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_ACCOUNT_INFORMATION(
            "display.accountInformation", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_LOGIN_PAGE_OPTIONS(
            "display.showLoginPageOptions", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_LOGOUT_BUTTON(
            "display.logoutButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_HOME_BUTTON(
            "display.homeButton", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_IDLE_TIMEOUT(
            "display.idleTimeout", PwmSettingSyntax.BOOLEAN, Category.USER_INTERFACE),
    DISPLAY_CSS_CUSTOM_STYLE(
            "display.css.customStyleLocation", PwmSettingSyntax.STRING, Category.USER_INTERFACE),
    DISPLAY_CSS_CUSTOM_MOBILE_STYLE(
            "display.css.customMobileStyleLocation", PwmSettingSyntax.STRING, Category.USER_INTERFACE),
    DISPLAY_CSS_EMBED(
            "display.css.customStyle", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE),
    DISPLAY_CSS_MOBILE_EMBED(
            "display.css.customMobileStyle", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE),
    DISPLAY_CUSTOM_JAVASCRIPT(
            "display.js.custom", PwmSettingSyntax.TEXT_AREA, Category.USER_INTERFACE),

    // change password
    QUERY_MATCH_CHANGE_PASSWORD(
            "password.allowChange.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.CHANGE_PASSWORD),
    LOGOUT_AFTER_PASSWORD_CHANGE(
            "logoutAfterPasswordChange", PwmSettingSyntax.BOOLEAN, Category.CHANGE_PASSWORD),
    PASSWORD_REQUIRE_FORM(
            "password.require.form", PwmSettingSyntax.FORM, Category.CHANGE_PASSWORD),
    PASSWORD_REQUIRE_CURRENT(
            "password.change.requireCurrent", PwmSettingSyntax.SELECT, Category.CHANGE_PASSWORD),
    PASSWORD_CHANGE_AGREEMENT_MESSAGE(
            "display.password.changeAgreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.CHANGE_PASSWORD),
    PASSWORD_COMPLETE_MESSAGE(
            "display.password.completeMessage", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.CHANGE_PASSWORD),
    DISPLAY_PASSWORD_GUIDE_TEXT(
            "display.password.guideText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.CHANGE_PASSWORD),
    PASSWORD_SYNC_ENABLE_REPLICA_CHECK(
            "passwordSync.enableReplicaCheck", PwmSettingSyntax.SELECT, Category.CHANGE_PASSWORD),
    PASSWORD_SYNC_MIN_WAIT_TIME(
            "passwordSyncMinWaitTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD),
    PASSWORD_SYNC_MAX_WAIT_TIME(
            "passwordSyncMaxWaitTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD),
    PASSWORD_EXPIRE_PRE_TIME(
            "expirePreTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD),
    PASSWORD_EXPIRE_WARN_TIME(
            "expireWarnTime", PwmSettingSyntax.NUMERIC, Category.CHANGE_PASSWORD),
    EXPIRE_CHECK_DURING_AUTH(
            "expireCheckDuringAuth", PwmSettingSyntax.BOOLEAN, Category.CHANGE_PASSWORD),
    SEEDLIST_FILENAME(
            "pwm.seedlist.location", PwmSettingSyntax.STRING, Category.CHANGE_PASSWORD),
    CHANGE_PASSWORD_WRITE_ATTRIBUTES(
            "changePassword.writeAttributes", PwmSettingSyntax.ACTION, Category.CHANGE_PASSWORD),

    //ldap directories
    LDAP_SERVER_URLS(
            "ldap.serverUrls", PwmSettingSyntax.STRING_ARRAY, Category.LDAP_PROFILE),
    LDAP_SERVER_CERTS(
            "ldap.serverCerts", PwmSettingSyntax.X509CERT, Category.LDAP_PROFILE),
    LDAP_PROXY_USER_DN(
            "ldap.proxy.username", PwmSettingSyntax.STRING, Category.LDAP_PROFILE),
    LDAP_PROXY_USER_PASSWORD(
            "ldap.proxy.password", PwmSettingSyntax.PASSWORD, Category.LDAP_PROFILE),
    LDAP_CONTEXTLESS_ROOT(
            "ldap.rootContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP_PROFILE),
    LDAP_TEST_USER_DN(
            "ldap.testuser.username", PwmSettingSyntax.STRING, Category.LDAP_PROFILE),
    AUTO_ADD_OBJECT_CLASSES(
            "ldap.addObjectClasses", PwmSettingSyntax.STRING_ARRAY, Category.LDAP_PROFILE),
    LDAP_CHAI_SETTINGS(
            "ldapChaiSettings", PwmSettingSyntax.STRING_ARRAY, Category.LDAP_PROFILE),
    LDAP_USERNAME_SEARCH_FILTER(
            "ldap.usernameSearchFilter", PwmSettingSyntax.STRING, Category.LDAP_PROFILE),
    LDAP_USERNAME_ATTRIBUTE(
            "ldap.username.attr", PwmSettingSyntax.STRING, Category.LDAP_PROFILE),
    LDAP_GUID_AUTO_ADD(
            "ldap.guid.autoAddValue", PwmSettingSyntax.BOOLEAN, Category.LDAP_PROFILE),
    LDAP_GUID_ATTRIBUTE(
            "ldap.guidAttribute", PwmSettingSyntax.STRING, Category.LDAP_PROFILE),
    LDAP_LOGIN_CONTEXTS(
            "ldap.selectableContexts", PwmSettingSyntax.STRING_ARRAY, Category.LDAP_PROFILE),
    LDAP_PROFILE_DISPLAY_NAME(
            "ldap.profile.displayName", PwmSettingSyntax.LOCALIZED_STRING, Category.LDAP_PROFILE),
    LDAP_PROFILE_ENABLED(
            "ldap.profile.enabled", PwmSettingSyntax.BOOLEAN, Category.LDAP_PROFILE),



    // ldap global settings
    LDAP_PROFILE_LIST(
            "ldap.profile.list", PwmSettingSyntax.PROFILE, Category.GENERAL),
    LDAP_NAMING_ATTRIBUTE(
            "ldap.namingAttribute", PwmSettingSyntax.STRING, Category.LDAP_GLOBAL),
    PASSWORD_LAST_UPDATE_ATTRIBUTE(
            "passwordLastUpdateAttribute", PwmSettingSyntax.STRING, Category.LDAP_GLOBAL),
    LDAP_IDLE_TIMEOUT(
            "ldap.idleTimeout", PwmSettingSyntax.NUMERIC, Category.LDAP_GLOBAL),
    DEFAULT_OBJECT_CLASSES(
            "ldap.defaultObjectClasses", PwmSettingSyntax.STRING_ARRAY, Category.LDAP_GLOBAL),
    LDAP_FOLLOW_REFERRALS(
            "ldap.followReferrals", PwmSettingSyntax.BOOLEAN, Category.LDAP_GLOBAL),
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.LDAP_GLOBAL),
    LDAP_DUPLICATE_MODE(
            "ldap.duplicateMode", PwmSettingSyntax.SELECT, Category.LDAP_GLOBAL),
    LDAP_SELECTABLE_CONTEXT_MODE(
            "ldap.selectableContextMode", PwmSettingSyntax.SELECT, Category.LDAP_GLOBAL),
    LDAP_IGNORE_UNREACHABLE_PROFILES(
            "ldap.ignoreUnreachableProfiles", PwmSettingSyntax.BOOLEAN, Category.LDAP_GLOBAL),


    // email settings
    EMAIL_SERVER_ADDRESS(
            "email.smtp.address", PwmSettingSyntax.STRING, Category.EMAIL),
    EMAIL_SERVER_PORT(
            "email.smtp.port", PwmSettingSyntax.NUMERIC, Category.EMAIL),
    EMAIL_USERNAME(
            "email.smtp.username", PwmSettingSyntax.STRING, Category.EMAIL),
    EMAIL_PASSWORD(
            "email.smtp.userpassword", PwmSettingSyntax.PASSWORD, Category.EMAIL),
    EMAIL_USER_MAIL_ATTRIBUTE(
            "email.userMailAttribute", PwmSettingSyntax.STRING, Category.EMAIL),
    EMAIL_MAX_QUEUE_AGE(
            "email.queueMaxAge", PwmSettingSyntax.NUMERIC, Category.EMAIL),
    EMAIL_CHANGEPASSWORD(
            "email.changePassword", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_CHANGEPASSWORD_HELPDESK(
            "email.changePassword.helpdesk", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_UPDATEPROFILE(
            "email.updateProfile", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_NEWUSER(
            "email.newUser", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_NEWUSER_VERIFICATION(
            "email.newUser.token", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_ACTIVATION(
            "email.activation", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_ACTIVATION_VERIFICATION(
            "email.activation.token", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_CHALLENGE_TOKEN(
            "email.challenge.token", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_GUEST(
            "email.guest", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_UPDATEGUEST(
            "email.updateguest", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_SENDPASSWORD(
            "email.sendpassword", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_SEND_USERNAME(
            "email.sendUsername", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_INTRUDERNOTICE(
            "email.intruderNotice", PwmSettingSyntax.EMAIL, Category.EMAIL),
    EMAIL_ADVANCED_SETTINGS(
            "email.smtp.advancedSettings", PwmSettingSyntax.STRING_ARRAY, Category.EMAIL),

    // sms settings
    SMS_USER_PHONE_ATTRIBUTE(
            "sms.userSmsAttribute", PwmSettingSyntax.STRING, Category.SMS),
    SMS_MAX_QUEUE_AGE(
            "sms.queueMaxAge", PwmSettingSyntax.NUMERIC, Category.SMS),
    SMS_GATEWAY_URL(
            "sms.gatewayURL", PwmSettingSyntax.STRING, Category.SMS),
    SMS_GATEWAY_USER(
            "sms.gatewayUser", PwmSettingSyntax.STRING, Category.SMS),
    SMS_GATEWAY_PASSWORD(
            "sms.gatewayPassword", PwmSettingSyntax.PASSWORD, Category.SMS),
    SMS_GATEWAY_METHOD(
            "sms.gatewayMethod", PwmSettingSyntax.SELECT, Category.SMS),
    SMS_GATEWAY_AUTHMETHOD(
            "sms.gatewayAuthMethod", PwmSettingSyntax.SELECT, Category.SMS),
    SMS_REQUEST_DATA(
            "sms.requestData", PwmSettingSyntax.TEXT_AREA, Category.SMS),
    SMS_REQUEST_CONTENT_TYPE(
            "sms.requestContentType", PwmSettingSyntax.STRING, Category.SMS),
    SMS_REQUEST_CONTENT_ENCODING(
            "sms.requestContentEncoding", PwmSettingSyntax.SELECT, Category.SMS),
    SMS_GATEWAY_REQUEST_HEADERS(
            "sms.httpRequestHeaders", PwmSettingSyntax.STRING_ARRAY, Category.SMS),
    SMS_MAX_TEXT_LENGTH(
            "sms.maxTextLength", PwmSettingSyntax.NUMERIC, Category.SMS),
    SMS_RESPONSE_OK_REGEX(
            "sms.responseOkRegex", PwmSettingSyntax.STRING_ARRAY, Category.SMS),
    SMS_SENDER_ID(
            "sms.senderID", PwmSettingSyntax.STRING, Category.SMS),
    SMS_PHONE_NUMBER_FORMAT(
            "sms.phoneNumberFormat", PwmSettingSyntax.SELECT, Category.SMS),
    SMS_DEFAULT_COUNTRY_CODE(
            "sms.defaultCountryCode", PwmSettingSyntax.NUMERIC, Category.SMS),
    SMS_REQUESTID_CHARS(
            "sms.requestId.characters", PwmSettingSyntax.STRING, Category.SMS),
    SMS_REQUESTID_LENGTH(
            "sms.requestId.length", PwmSettingSyntax.NUMERIC, Category.SMS),
    SMS_CHALLENGE_TOKEN_TEXT(
            "sms.challenge.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS),
    SMS_CHALLENGE_NEW_PASSWORD_TEXT(
            "sms.challenge.newpassword.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS),
    SMS_NEWUSER_TOKEN_TEXT(
            "sms.newUser.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS),
    SMS_ACTIVATION_VERIFICATION_TEXT(
            "sms.activation.token.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS),
    SMS_ACTIVATION_TEXT(
            "sms.activation.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS),
    SMS_FORGOTTEN_USERNAME_TEXT(
            "sms.forgottenUsername.message", PwmSettingSyntax.LOCALIZED_STRING, Category.SMS),
    SMS_USE_URL_SHORTENER(
            "sms.useUrlShortener", PwmSettingSyntax.BOOLEAN, Category.SMS),

    //global password policy settings
    PASSWORD_PROFILE_LIST(
            "password.profile.list", PwmSettingSyntax.PROFILE, Category.PASSWORD_GLOBAL),
    WORDLIST_FILENAME(
            "pwm.wordlist.location", PwmSettingSyntax.STRING, Category.PASSWORD_GLOBAL),
    PASSWORD_SHAREDHISTORY_ENABLE(
            "password.sharedHistory.enable", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_GLOBAL),
    PASSWORD_SHAREDHISTORY_MAX_AGE(
            "password.sharedHistory.age", PwmSettingSyntax.NUMERIC, Category.PASSWORD_GLOBAL),
    WORDLIST_CASE_SENSITIVE(
            "wordlistCaseSensitive", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_GLOBAL),
    PASSWORD_WORDLIST_WORDSIZE(
            "password.wordlist.wordSize", PwmSettingSyntax.NUMERIC, Category.PASSWORD_GLOBAL),
    PASSWORD_POLICY_SOURCE(
            "password.policy.source", PwmSettingSyntax.SELECT, Category.PASSWORD_GLOBAL),
    PASSWORD_POLICY_CASE_SENSITIVITY(
            "password.policy.caseSensitivity", PwmSettingSyntax.SELECT, Category.PASSWORD_GLOBAL),


    // password policy profile settings
    PASSWORD_POLICY_QUERY_MATCH(
            "password.policy.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_LENGTH(
            "password.policy.minimumLength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_LENGTH(
            "password.policy.maximumLength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_REPEAT(
            "password.policy.maximumRepeat", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT(
            "password.policy.maximumSequentialRepeat", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_NUMERIC(
            "password.policy.allowNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC(
            "password.policy.allowFirstCharNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC(
            "password.policy.allowLastCharNumeric", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_NUMERIC(
            "password.policy.maximumNumeric", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_NUMERIC(
            "password.policy.minimumNumeric", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_SPECIAL(
            "password.policy.allowSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL(
            "password.policy.allowFirstCharSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL(
            "password.policy.allowLastCharSpecial", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_SPECIAL(
            "password.policy.maximumSpecial", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_SPECIAL(
            "password.policy.minimumSpecial", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_ALPHA(
            "password.policy.maximumAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_ALPHA(
            "password.policy.minimumAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_NON_ALPHA(
            "password.policy.maximumNonAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_NON_ALPHA(
            "password.policy.minimumNonAlpha", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_UPPERCASE(
            "password.policy.maximumUpperCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_UPPERCASE(
            "password.policy.minimumUpperCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_LOWERCASE(
            "password.policy.maximumLowerCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_LOWERCASE(
            "password.policy.minimumLowerCase", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_UNIQUE(
            "password.policy.minimumUnique", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS(
            "password.policy.maximumOldPasswordChars", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_LIFETIME(
            "password.policy.minimumLifetime", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_ENABLE_WORDLIST(
            "password.policy.checkWordlist", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_AD_COMPLEXITY(
            "password.policy.ADComplexity", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH(
            "password.policy.regExMatch", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH(
            "password.policy.regExNoMatch", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_DISALLOWED_VALUES(
            "password.policy.disallowedValues", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_DISALLOWED_ATTRIBUTES(
            "password.policy.disallowedAttributes", PwmSettingSyntax.STRING_ARRAY, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_STRENGTH(
            "password.policy.minimumStrength", PwmSettingSyntax.NUMERIC, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_CHANGE_MESSAGE(
            "password.policy.changeMessage", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_RULE_TEXT(
            "password.policy.ruleText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.PASSWORD_POLICY),
    PASSWORD_POLICY_DISALLOW_CURRENT(
            "password.policy.disallowCurrent", PwmSettingSyntax.BOOLEAN, Category.PASSWORD_POLICY),


    // security settings
    PWM_SECURITY_KEY(
            "pwm.securityKey", PwmSettingSyntax.PASSWORD, Category.SECURITY),
    RECAPTCHA_KEY_PUBLIC(
            "captcha.recaptcha.publicKey", PwmSettingSyntax.STRING, Category.SECURITY),
    RECAPTCHA_KEY_PRIVATE(
            "captcha.recaptcha.privateKey", PwmSettingSyntax.PASSWORD, Category.SECURITY),
    CAPTCHA_SKIP_PARAM(
            "captcha.skip.param", PwmSettingSyntax.STRING, Category.SECURITY),
    CAPTCHA_SKIP_COOKIE(
            "captcha.skip.cookie", PwmSettingSyntax.STRING, Category.SECURITY),
    SECURITY_ENABLE_REQUEST_SEQUENCE(
            "security.page.enableRequestSequence", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    SECURITY_ENABLE_FORM_NONCE(
            "security.formNonce.enable", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    ALLOW_URL_SESSIONS(
            "allowUrlSessions", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    ENABLE_SESSION_VERIFICATION(
            "enableSessionVerification", PwmSettingSyntax.SELECT, Category.SECURITY),
    DISALLOWED_HTTP_INPUTS(
            "disallowedInputs", PwmSettingSyntax.STRING_ARRAY, Category.SECURITY),
    REQUIRE_HTTPS(
            "pwm.requireHTTPS", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    FORCE_BASIC_AUTH(
            "forceBasicAuth", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    USE_X_FORWARDED_FOR_HEADER(
            "useXForwardedForHeader", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    REVERSE_DNS_ENABLE(
            "network.reverseDNS.enable", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    MULTI_IP_SESSION_ALLOWED(
            "network.allowMultiIPSession", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    REQUIRED_HEADERS(
            "network.requiredHttpHeaders", PwmSettingSyntax.STRING_ARRAY, Category.SECURITY),
    IP_PERMITTED_RANGE(
            "network.ip.permittedRange", PwmSettingSyntax.STRING_ARRAY, Category.SECURITY),
    SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT(
            "security.page.leaveNoticeTimeout", PwmSettingSyntax.NUMERIC, Category.SECURITY),
    DISPLAY_SHOW_DETAILED_ERRORS(
            "display.showDetailedErrors", PwmSettingSyntax.BOOLEAN, Category.SECURITY),
    SSO_AUTH_HEADER_NAME(
            "security.sso.authHeaderName", PwmSettingSyntax.STRING, Category.SECURITY),
    SESSION_MAX_SECONDS(
            "session.maxSeconds", PwmSettingSyntax.NUMERIC, Category.SECURITY),

    // intruder detection
    INTRUDER_STORAGE_METHOD(
            "intruder.storageMethod", PwmSettingSyntax.SELECT, Category.INTRUDER),
    INTRUDER_USER_RESET_TIME(
            "intruder.user.resetTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_USER_MAX_ATTEMPTS(
            "intruder.user.maxAttempts", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_USER_CHECK_TIME(
            "intruder.user.checkTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_ATTRIBUTE_RESET_TIME(
            "intruder.attribute.resetTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_ATTRIBUTE_MAX_ATTEMPTS(
            "intruder.attribute.maxAttempts", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_ATTRIBUTE_CHECK_TIME(
            "intruder.attribute.checkTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_TOKEN_DEST_RESET_TIME(
            "intruder.tokenDest.resetTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_TOKEN_DEST_MAX_ATTEMPTS(
            "intruder.tokenDest.maxAttempts", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_TOKEN_DEST_CHECK_TIME(
            "intruder.tokenDest.checkTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_ADDRESS_RESET_TIME(
            "intruder.address.resetTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_ADDRESS_MAX_ATTEMPTS(
            "intruder.address.maxAttempts", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_ADDRESS_CHECK_TIME(
            "intruder.address.checkTime", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    INTRUDER_SESSION_MAX_ATTEMPTS(
            "intruder.session.maxAttempts", PwmSettingSyntax.NUMERIC, Category.INTRUDER),
    SECURITY_SIMULATE_LDAP_BAD_PASSWORD(
            "security.ldap.simulateBadPassword", PwmSettingSyntax.BOOLEAN, Category.INTRUDER),

    // token settings
    TOKEN_STORAGEMETHOD(
            "token.storageMethod", PwmSettingSyntax.SELECT, Category.TOKEN),
    TOKEN_CHARACTERS(
            "token.characters", PwmSettingSyntax.STRING, Category.TOKEN),
    TOKEN_LENGTH(
            "token.length", PwmSettingSyntax.NUMERIC, Category.TOKEN),
    TOKEN_LIFETIME(
            "token.lifetime", PwmSettingSyntax.NUMERIC, Category.TOKEN),
    TOKEN_LDAP_ATTRIBUTE(
            "token.ldap.attribute", PwmSettingSyntax.STRING, Category.TOKEN),

    // OTP
    OTP_ENABLED(
            "otp.enabled", PwmSettingSyntax.BOOLEAN, Category.OTP),
    OTP_FORCE_SETUP(
            "otp.forceSetup", PwmSettingSyntax.BOOLEAN, Category.OTP),
    OTP_SECRET_READ_PREFERENCE(
            "otp.secret.readPreference", PwmSettingSyntax.SELECT, Category.OTP),
    OTP_SECRET_WRITE_PREFERENCE(
            "otp.secret.writePreference", PwmSettingSyntax.SELECT, Category.OTP),
    OTP_SECRET_STORAGEFORMAT(
            "otp.secret.storageFormat", PwmSettingSyntax.SELECT, Category.OTP),
    OTP_SECRET_ENCRYPT(
            "otp.secret.encrypt", PwmSettingSyntax.BOOLEAN, Category.OTP),
    OTP_SECRET_LDAP_ATTRIBUTE(
            "otp.secret.ldap.attribute", PwmSettingSyntax.STRING, Category.OTP),
    QUERY_MATCH_OTP_SETUP_RESPONSE(
            "otp.secret.allowSetup.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.OTP),

    // logger settings
    EVENTS_HEALTH_CHECK_MIN_INTERVAL(
            "events.healthCheck.minInterval", PwmSettingSyntax.NUMERIC, Category.LOGGING),
    EVENTS_JAVA_STDOUT_LEVEL(
            "events.java.stdoutLevel", PwmSettingSyntax.SELECT, Category.LOGGING),
    EVENTS_JAVA_LOG4JCONFIG_FILE(
            "events.java.log4jconfigFile", PwmSettingSyntax.STRING, Category.LOGGING),
    EVENTS_PWMDB_MAX_EVENTS(
            "events.pwmDB.maxEvents", PwmSettingSyntax.NUMERIC, Category.LOGGING),
    EVENTS_PWMDB_MAX_AGE(
            "events.pwmDB.maxAge", PwmSettingSyntax.NUMERIC, Category.LOGGING),
    EVENTS_LOCALDB_LOG_LEVEL(
            "events.pwmDB.logLevel", PwmSettingSyntax.SELECT, Category.LOGGING),
    EVENTS_FILE_LEVEL(
            "events.fileAppender.level", PwmSettingSyntax.SELECT, Category.LOGGING),
    EVENTS_USER_STORAGE_METHOD(
            "events.user.storageMethod", PwmSettingSyntax.SELECT, Category.LOGGING),
    EVENTS_LDAP_ATTRIBUTE(
            "events.ldap.attribute", PwmSettingSyntax.STRING, Category.LOGGING),
    EVENTS_LDAP_MAX_EVENTS(
            "events.ldap.maxEvents", PwmSettingSyntax.NUMERIC, Category.LOGGING),
    LDAP_ENABLE_WIRE_TRACE(
            "ldap.wireTrace.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING),
    EVENTS_ALERT_DAILY_SUMMARY(
            "events.alert.dailySummary.enable", PwmSettingSyntax.BOOLEAN, Category.LOGGING),
    EVENTS_AUDIT_MAX_AGE(
            "events.audit.maxAge", PwmSettingSyntax.NUMERIC, Category.LOGGING),
    AUDIT_EMAIL_SYSTEM_TO(
            "email.adminAlert.toAddress", PwmSettingSyntax.STRING_ARRAY, Category.LOGGING),
    AUDIT_EMAIL_USER_TO(
            "audit.userEvent.toAddress", PwmSettingSyntax.STRING_ARRAY, Category.LOGGING),
    AUDIT_SYSLOG_SERVERS(
            "audit.syslog.servers", PwmSettingSyntax.STRING, Category.LOGGING),


    // challenge settings
    CHALLENGE_ENABLE(
            "challenge.enable", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE),
    CHALLENGE_FORCE_SETUP(
            "challenge.forceSetup", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE),
    CHALLENGE_SHOW_CONFIRMATION(
            "challenge.showConfirmation", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE),
    CHALLENGE_CASE_INSENSITIVE(
            "challenge.caseInsensitive", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE),
    CHALLENGE_MAX_LENGTH_CHALLENGE_IN_RESPONSE(
            "challenge.maxChallengeLengthInResponse", PwmSettingSyntax.NUMERIC, Category.CHALLENGE),
    CHALLENGE_ALLOW_DUPLICATE_RESPONSES(
            "challenge.allowDuplicateResponses", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE),
    CHALLENGE_APPLY_WORDLIST(
            "challenge.applyWorldlist", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE),
    QUERY_MATCH_SETUP_RESPONSE(
            "challenge.allowSetup.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.CHALLENGE),
    QUERY_MATCH_CHECK_RESPONSES(
            "command.checkResponses.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.CHALLENGE),
    CHALLENGE_ENFORCE_MINIMUM_PASSWORD_LIFETIME(
            "challenge.enforceMinimumPasswordLifetime", PwmSettingSyntax.BOOLEAN, Category.CHALLENGE),

    // challenge policy profile
    CHALLENGE_PROFILE_LIST(
            "challenge.profile.list", PwmSettingSyntax.PROFILE, Category.GENERAL),
    CHALLENGE_POLICY_QUERY_MATCH(
            "challenge.policy.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.CHALLENGE_POLICY),
    CHALLENGE_RANDOM_CHALLENGES(
            "challenge.randomChallenges", PwmSettingSyntax.CHALLENGE, Category.CHALLENGE_POLICY),
    CHALLENGE_REQUIRED_CHALLENGES(
            "challenge.requiredChallenges", PwmSettingSyntax.CHALLENGE, Category.CHALLENGE_POLICY),
    CHALLENGE_MIN_RANDOM_REQUIRED(
            "challenge.minRandomRequired", PwmSettingSyntax.NUMERIC, Category.CHALLENGE_POLICY),
    CHALLENGE_MIN_RANDOM_SETUP(
            "challenge.minRandomsSetup", PwmSettingSyntax.NUMERIC, Category.CHALLENGE_POLICY),
    CHALLENGE_HELPDESK_RANDOM_CHALLENGES(
            "challenge.helpdesk.randomChallenges", PwmSettingSyntax.CHALLENGE, Category.CHALLENGE_POLICY),
    CHALLENGE_HELPDESK_REQUIRED_CHALLENGES(
            "challenge.helpdesk.requiredChallenges", PwmSettingSyntax.CHALLENGE, Category.CHALLENGE_POLICY),
    CHALLENGE_HELPDESK_MIN_RANDOM_SETUP(
            "challenge.helpdesk.minRandomsSetup", PwmSettingSyntax.NUMERIC, Category.CHALLENGE_POLICY),


    // recovery settings
    FORGOTTEN_PASSWORD_ENABLE(
            "recovery.enable", PwmSettingSyntax.BOOLEAN, Category.RECOVERY),
    FORGOTTEN_PASSWORD_SEARCH_FORM(
            "recovery.form", PwmSettingSyntax.FORM, Category.RECOVERY),
    FORGOTTEN_PASSWORD_SEARCH_FILTER(
            "recovery.searchFilter", PwmSettingSyntax.STRING, Category.RECOVERY),
    FORGOTTEN_PASSWORD_READ_PREFERENCE(
            "recovery.response.readPreference", PwmSettingSyntax.SELECT, Category.RECOVERY),
    FORGOTTEN_PASSWORD_WRITE_PREFERENCE(
            "recovery.response.writePreference", PwmSettingSyntax.SELECT, Category.RECOVERY),
    CHALLENGE_USER_ATTRIBUTE(
            "challenge.userAttribute", PwmSettingSyntax.STRING, Category.RECOVERY),
    CHALLENGE_ALLOW_UNLOCK(
            "challenge.allowUnlock", PwmSettingSyntax.BOOLEAN, Category.RECOVERY),
    CHALLENGE_STORAGE_HASHED(
            "response.hashMethod", PwmSettingSyntax.SELECT, Category.RECOVERY),
    CHALLENGE_REQUIRED_ATTRIBUTES(
            "challenge.requiredAttributes", PwmSettingSyntax.FORM, Category.RECOVERY),
    CHALLENGE_REQUIRE_RESPONSES(
            "challenge.requireResponses", PwmSettingSyntax.BOOLEAN, Category.RECOVERY),
    CHALLENGE_TOKEN_SEND_METHOD(
            "challenge.token.sendMethod", PwmSettingSyntax.SELECT, Category.RECOVERY),
    FORGOTTEN_PASSWORD_REQUIRE_OTP(
            "recovery.require.otp", PwmSettingSyntax.BOOLEAN, Category.RECOVERY),
    FORGOTTEN_PASSWORD_ACTION(
            "recovery.action", PwmSettingSyntax.SELECT, Category.RECOVERY),
    CHALLENGE_SENDNEWPW_METHOD(
            "recovery.sendNewPassword.sendMethod", PwmSettingSyntax.SELECT, Category.RECOVERY),
    FORGOTTEN_USER_POST_ACTIONS(
            "recovery.postActions", PwmSettingSyntax.ACTION, Category.RECOVERY),


    // forgotten username
    FORGOTTEN_USERNAME_ENABLE(
            "forgottenUsername.enable", PwmSettingSyntax.BOOLEAN, Category.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_FORM(
            "forgottenUsername.form", PwmSettingSyntax.FORM, Category.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_SEARCH_FILTER(
            "forgottenUsername.searchFilter", PwmSettingSyntax.STRING, Category.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_USERNAME_ATTRIBUTE(
            "forgottenUsername.usernameAttribute", PwmSettingSyntax.STRING, Category.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_SEND_USERNAME_METHOD(
            "forgottenUsername.sendUsername.sendMethod", PwmSettingSyntax.SELECT, Category.FORGOTTEN_USERNAME),


    // new user settings
    NEWUSER_ENABLE(
            "newUser.enable", PwmSettingSyntax.BOOLEAN, Category.NEWUSER),
    NEWUSER_CONTEXT(
            "newUser.createContext", PwmSettingSyntax.STRING, Category.NEWUSER),
    NEWUSER_AGREEMENT_MESSAGE(
            "display.newuser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.NEWUSER),
    NEWUSER_FORM(
            "newUser.form", PwmSettingSyntax.FORM, Category.NEWUSER),
    NEWUSER_WRITE_ATTRIBUTES(
            "newUser.writeAttributes", PwmSettingSyntax.ACTION, Category.NEWUSER),
    NEWUSER_DELETE_ON_FAIL(
            "newUser.deleteOnFail", PwmSettingSyntax.BOOLEAN, Category.NEWUSER),
    NEWUSER_USERNAME_CHARS(
            "newUser.username.characters", PwmSettingSyntax.STRING, Category.NEWUSER),
    NEWUSER_USERNAME_LENGTH(
            "newUser.username.length", PwmSettingSyntax.NUMERIC, Category.NEWUSER),
    NEWUSER_EMAIL_VERIFICATION(
            "newUser.email.verification", PwmSettingSyntax.BOOLEAN, Category.NEWUSER),
    NEWUSER_SMS_VERIFICATION(
            "newUser.sms.verification", PwmSettingSyntax.BOOLEAN, Category.NEWUSER),
    NEWUSER_PASSWORD_POLICY_USER(
            "newUser.passwordPolicy.user", PwmSettingSyntax.STRING, Category.NEWUSER),
    NEWUSER_MINIMUM_WAIT_TIME(
            "newUser.minimumWaitTime", PwmSettingSyntax.NUMERIC, Category.NEWUSER),


    // guest settings
    GUEST_ENABLE(
            "guest.enable", PwmSettingSyntax.BOOLEAN, Category.GUEST),
    GUEST_CONTEXT(
            "guest.createContext", PwmSettingSyntax.STRING, Category.GUEST),
    GUEST_ADMIN_GROUP(
            "guest.adminGroup", PwmSettingSyntax.STRING, Category.GUEST),
    GUEST_FORM(
            "guest.form", PwmSettingSyntax.FORM, Category.GUEST),
    GUEST_UPDATE_FORM(
            "guest.update.form", PwmSettingSyntax.FORM, Category.GUEST),
    GUEST_WRITE_ATTRIBUTES(
            "guest.writeAttributes", PwmSettingSyntax.ACTION, Category.GUEST),
    GUEST_ADMIN_ATTRIBUTE(
            "guest.adminAttribute", PwmSettingSyntax.STRING, Category.GUEST),
    GUEST_EDIT_ORIG_ADMIN_ONLY(
            "guest.editOriginalAdminOnly", PwmSettingSyntax.BOOLEAN, Category.GUEST),
    GUEST_MAX_VALID_DAYS(
            "guest.maxValidDays", PwmSettingSyntax.NUMERIC, Category.GUEST),
    GUEST_EXPIRATION_ATTRIBUTE (
            "guest.expirationAttribute", PwmSettingSyntax.STRING, Category.GUEST),

    // activation settings
    ACTIVATE_USER_ENABLE(
            "activateUser.enable", PwmSettingSyntax.BOOLEAN, Category.ACTIVATION),
    ACTIVATE_USER_UNLOCK(
            "activateUser.allowUnlock", PwmSettingSyntax.BOOLEAN, Category.ACTIVATION),
    ACTIVATE_TOKEN_SEND_METHOD(
            "activateUser.token.sendMethod", PwmSettingSyntax.SELECT, Category.ACTIVATION),
    ACTIVATE_AGREEMENT_MESSAGE(
            "display.activateUser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.ACTIVATION),
    ACTIVATE_USER_FORM(
            "activateUser.form", PwmSettingSyntax.FORM, Category.ACTIVATION),
    ACTIVATE_USER_SEARCH_FILTER(
            "activateUser.searchFilter", PwmSettingSyntax.STRING, Category.ACTIVATION),
    ACTIVATE_USER_QUERY_MATCH(
            "activateUser.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.ACTIVATION),
    ACTIVATE_USER_PRE_WRITE_ATTRIBUTES(
            "activateUser.writePreAttributes", PwmSettingSyntax.ACTION, Category.ACTIVATION),
    ACTIVATE_USER_POST_WRITE_ATTRIBUTES(
            "activateUser.writePostAttributes", PwmSettingSyntax.ACTION, Category.ACTIVATION),

    // update profile
    UPDATE_PROFILE_ENABLE(
            "updateAttributes.enable", PwmSettingSyntax.BOOLEAN, Category.UPDATE),
    UPDATE_PROFILE_FORCE_SETUP(
            "updateAttributes.forceSetup", PwmSettingSyntax.BOOLEAN, Category.UPDATE),
    UPDATE_PROFILE_AGREEMENT_MESSAGE(
            "display.updateAttributes.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, Category.UPDATE),
    UPDATE_PROFILE_QUERY_MATCH(
            "updateAttributes.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.UPDATE),
    UPDATE_PROFILE_WRITE_ATTRIBUTES(
            "updateAttributes.writeAttributes", PwmSettingSyntax.ACTION, Category.UPDATE),
    UPDATE_PROFILE_FORM(
            "updateAttributes.form", PwmSettingSyntax.FORM, Category.UPDATE),
    UPDATE_PROFILE_CHECK_QUERY_MATCH(
            "updateAttributes.check.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.UPDATE),
    UPDATE_PROFILE_SHOW_CONFIRMATION(
            "updateAttributes.showConfirmation", PwmSettingSyntax.BOOLEAN, Category.UPDATE),

    // shortcut settings
    SHORTCUT_ENABLE(
            "shortcut.enable", PwmSettingSyntax.BOOLEAN, Category.SHORTCUT),
    SHORTCUT_ITEMS(
            "shortcut.items", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, Category.SHORTCUT),
    SHORTCUT_HEADER_NAMES(
            "shortcut.httpHeaders", PwmSettingSyntax.STRING_ARRAY, Category.SHORTCUT),

    // peoplesearch settings
    PEOPLE_SEARCH_ENABLE(
            "peopleSearch.enable", PwmSettingSyntax.BOOLEAN, Category.PEOPLE_SEARCH),
    PEOPLE_SEARCH_QUERY_MATCH(
            "peopleSearch.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.PEOPLE_SEARCH),
    PEOPLE_SEARCH_SEARCH_FILTER(
            "peopleSearch.searchFilter", PwmSettingSyntax.STRING, Category.PEOPLE_SEARCH),
    PEOPLE_SEARCH_SEARCH_BASE(
            "peopleSearch.searchBase", PwmSettingSyntax.STRING_ARRAY, Category.PEOPLE_SEARCH),
    PEOPLE_SEARCH_RESULT_FORM(
            "peopleSearch.result.form", PwmSettingSyntax.FORM, Category.PEOPLE_SEARCH),
    PEOPLE_SEARCH_DETAIL_FORM(
            "peopleSearch.detail.form", PwmSettingSyntax.FORM, Category.PEOPLE_SEARCH),
    PEOPLE_SEARCH_RESULT_LIMIT(
            "peopleSearch.result.limit", PwmSettingSyntax.NUMERIC, Category.PEOPLE_SEARCH),
    PEOPLE_SEARCH_USE_PROXY(
            "peopleSearch.useProxy", PwmSettingSyntax.BOOLEAN, Category.PEOPLE_SEARCH),

    // edirectory settings
    EDIRECTORY_ENABLE_NMAS(
            "ldap.edirectory.enableNmas", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, Template.NOVL),
    EDIRECTORY_STORE_NMAS_RESPONSES(
            "ldap.edirectory.storeNmasResponses", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, Template.NOVL),
    EDIRECTORY_USE_NMAS_RESPONSES(
            "ldap.edirectory.useNmasResponses", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, Template.NOVL),
    EDIRECTORY_READ_USER_PWD(
            "ldap.edirectory.readUserPwd", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, Template.NOVL),
    EDIRECTORY_READ_CHALLENGE_SET(
            "ldap.edirectory.readChallengeSets", PwmSettingSyntax.BOOLEAN, Category.EDIRECTORY, Template.NOVL),
    EDIRECTORY_PWD_MGT_WEBSERVICE_URL(
            "ldap.edirectory.ws.pwdMgtURL", PwmSettingSyntax.STRING, Category.EDIRECTORY, Template.NOVL),

    // active directory
    AD_USE_PROXY_FOR_FORGOTTEN(
            "ldap.ad.proxyForgotten", PwmSettingSyntax.BOOLEAN, Category.ACTIVE_DIRECTORY, Template.AD),
    AD_ALLOW_AUTH_REQUIRE_NEW_PWD(
            "ldap.ad.allowAuth.requireNewPassword", PwmSettingSyntax.BOOLEAN, Category.ACTIVE_DIRECTORY, Template.AD),
    AD_ALLOW_AUTH_EXPIRED(
            "ldap.ad.allowAuth.expired", PwmSettingSyntax.BOOLEAN, Category.ACTIVE_DIRECTORY, Template.AD),
    AD_ENFORCE_PW_HISTORY_ON_SET(
            "ldap.ad.enforcePwHistoryOnSet", PwmSettingSyntax.BOOLEAN, Category.ACTIVE_DIRECTORY, Template.AD),

    // helpdesk
    HELPDESK_ENABLE(
            "helpdesk.enable", PwmSettingSyntax.BOOLEAN, Category.HELPDESK),
    HELPDESK_QUERY_MATCH(
            "helpdesk.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.HELPDESK),
    HELPDESK_SEARCH_FILTER(
            "helpdesk.filter", PwmSettingSyntax.STRING, Category.HELPDESK),
    HELPDESK_SEARCH_FORM(
            "helpdesk.result.form", PwmSettingSyntax.FORM, Category.HELPDESK),
    HELPDESK_DETAIL_FORM(
            "helpdesk.detail.form", PwmSettingSyntax.FORM, Category.HELPDESK),
    HELPDESK_SEARCH_BASE(
            "helpdesk.searchBase", PwmSettingSyntax.STRING_ARRAY, Category.HELPDESK),
    HELPDESK_RESULT_LIMIT(
            "helpdesk.result.limit", PwmSettingSyntax.NUMERIC, Category.HELPDESK),
    HELPDESK_SET_PASSWORD_MODE(
            "helpdesk.setPassword.mode", PwmSettingSyntax.SELECT, Category.HELPDESK),
    HELPDESK_SEND_PASSWORD(
            "helpdesk.sendPassword", PwmSettingSyntax.BOOLEAN, Category.HELPDESK),
    HELPDESK_POST_SET_PASSWORD_WRITE_ATTRIBUTES(
            "helpdesk.setPassword.writeAttributes", PwmSettingSyntax.ACTION, Category.HELPDESK),
    HELPDESK_ACTIONS(
            "helpdesk.actions", PwmSettingSyntax.ACTION, Category.HELPDESK),
    HELPDESK_ENABLE_UNLOCK(
            "helpdesk.enableUnlock", PwmSettingSyntax.BOOLEAN, Category.HELPDESK),
    HELPDESK_ENFORCE_PASSWORD_POLICY(
            "helpdesk.enforcePasswordPolicy", PwmSettingSyntax.BOOLEAN, Category.HELPDESK),
    HELPDESK_IDLE_TIMEOUT_SECONDS(
            "helpdesk.idleTimeout", PwmSettingSyntax.NUMERIC, Category.HELPDESK),
    HELPDESK_CLEAR_RESPONSES(
            "helpdesk.clearResponses", PwmSettingSyntax.SELECT, Category.HELPDESK),
    HELPDESK_CLEAR_RESPONSES_BUTTON(
            "helpdesk.clearResponses.button", PwmSettingSyntax.BOOLEAN, Category.HELPDESK),
    HELPDESK_CLEAR_OTP_BUTTON(
            "helpdesk.clearOtp.button", PwmSettingSyntax.BOOLEAN, Category.HELPDESK),
    HELPDESK_USE_PROXY(
            "helpdesk.useProxy",PwmSettingSyntax.BOOLEAN, Category.HELPDESK),

    // Database
    DATABASE_CLASS(
            "db.classname", PwmSettingSyntax.STRING, Category.DATABASE),
    DATABASE_URL(
            "db.url", PwmSettingSyntax.STRING, Category.DATABASE),
    DATABASE_USERNAME(
            "db.username", PwmSettingSyntax.STRING, Category.DATABASE),
    DATABASE_PASSWORD(
            "db.password", PwmSettingSyntax.PASSWORD, Category.DATABASE),
    DATABASE_COLUMN_TYPE_KEY(
            "db.columnType.key", PwmSettingSyntax.STRING, Category.DATABASE),
    DATABASE_COLUMN_TYPE_VALUE(
            "db.columnType.value", PwmSettingSyntax.STRING, Category.DATABASE),
    DATABASE_DEBUG_TRACE(
            "db.debugTrace.enable", PwmSettingSyntax.BOOLEAN, Category.DATABASE),

    // reporting
    REPORTING_ENABLE(
            "reporting.enable", PwmSettingSyntax.BOOLEAN, Category.REPORTING),
    REPORTING_SEARCH_FILTER(
            "reporting.ldap.searchFilter", PwmSettingSyntax.STRING, Category.REPORTING),
    REPORTING_MAX_CACHE_AGE(
            "reporting.maxCacheAge", PwmSettingSyntax.NUMERIC, Category.REPORTING),
    REPORTING_MIN_CACHE_AGE(
            "reporting.minCacheAge", PwmSettingSyntax.NUMERIC, Category.REPORTING),
    REPORTING_REST_TIME_MS(
            "reporting.reportRestTimeMS", PwmSettingSyntax.NUMERIC, Category.REPORTING),
    REPORTING_MAX_QUERY_SIZE(
            "reporting.ldap.maxQuerySize", PwmSettingSyntax.NUMERIC, Category.REPORTING),
    REPORTONG_JOB_TIME_OFFSET(
            "reporting.job.timeOffset", PwmSettingSyntax.NUMERIC, Category.REPORTING),

    // developer
    EXTERNAL_JUDGE_METHODS(
            "externalJudgeMethod", PwmSettingSyntax.STRING_ARRAY, Category.MISC),
    HTTP_PROXY_URL(
            "http.proxy.url", PwmSettingSyntax.STRING, Category.MISC),
    CAS_CLEAR_PASS_URL(
            "cas.clearPassUrl", PwmSettingSyntax.STRING, Category.MISC),
    URL_SHORTENER_CLASS(
            "urlshortener.classname", PwmSettingSyntax.STRING, Category.MISC),
    URL_SHORTENER_REGEX(
            "urlshortener.regex", PwmSettingSyntax.STRING, Category.MISC),
    URL_SHORTENER_PARAMETERS(
            "urlshortener.parameters", PwmSettingSyntax.STRING_ARRAY, Category.MISC),
    ENABLE_EXTERNAL_WEBSERVICES(
            "external.webservices.enable", PwmSettingSyntax.BOOLEAN, Category.MISC),
    ENABLE_WEBSERVICES_READANSWERS(
            "webservices.enableReadAnswers", PwmSettingSyntax.BOOLEAN, Category.MISC),
    PUBLIC_HEALTH_STATS_WEBSERVICES(
            "webservices.healthStats.makePublic", PwmSettingSyntax.BOOLEAN, Category.MISC),
    WEBSERVICES_THIRDPARTY_QUERY_MATCH(
            "webservices.thirdParty.queryMatch", PwmSettingSyntax.USER_PERMISSION, Category.MISC),
    EXTERNAL_WEB_AUTH_METHODS(
            "external.webAuth.methods", PwmSettingSyntax.STRING_ARRAY, Category.MISC),
    EXTERNAL_MACROS_DEST_TOKEN_URLS(
            "external.destToken.urls", PwmSettingSyntax.STRING, Category.MISC),
    EXTERNAL_MACROS_REST_URLS(
            "external.macros.urls", PwmSettingSyntax.STRING_ARRAY, Category.MISC),
    EXTERNAL_PWCHECK_REST_URLS(
            "external.pwcheck.urls", PwmSettingSyntax.STRING, Category.MISC),
    CACHED_USER_ATTRIBUTES(
            "webservice.userAttributes", PwmSettingSyntax.STRING_ARRAY, Category.MISC),


    // OAuth
    OAUTH_ID_LOGIN_URL(
            "oauth.idserver.loginUrl", PwmSettingSyntax.STRING, Category.OAUTH),
    OAUTH_ID_CODERESOLVE_URL(
            "oauth.idserver.codeResolveUrl", PwmSettingSyntax.STRING, Category.OAUTH),
    OAUTH_ID_ATTRIBUTES_URL(
            "oauth.idserver.attributesUrl", PwmSettingSyntax.STRING, Category.OAUTH),
    OAUTH_ID_CLIENTNAME(
            "oauth.idserver.clientName", PwmSettingSyntax.STRING, Category.OAUTH),
    OAUTH_ID_SECRET(
            "oauth.idserver.secret", PwmSettingSyntax.PASSWORD, Category.OAUTH),
    OAUTH_ID_DN_ATTRIBUTE_NAME(
            "oauth.idserver.dnAttributeName", PwmSettingSyntax.STRING, Category.OAUTH),

    ;

// ------------------------------ STATICS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmSetting.class);


// ------------------------------ FIELDS ------------------------------

    private static class Static {
        private static final Pattern DEFAULT_REGEX = Pattern.compile(".*",Pattern.DOTALL);
    }

    private final String key;
    private final PwmSettingSyntax syntax;
    private final Category category;
    private final Set<Template> templates;

// --------------------------- CONSTRUCTORS ---------------------------

    PwmSetting(
            final String key,
            final PwmSettingSyntax syntax,
            final Category category,
            final Template... templates
    ) {
        this.key = key;
        this.syntax = syntax;
        this.category = category;
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

    public Set<Template> getTemplates() {
        return templates;
    }

    public boolean showSetting(Category category, int level) {
        if (category != this.getCategory()) {
            return false;
        }

        if (getLevel() != level) {
            return false;
        }

        if (isHidden()) {
            return false;
        }

        return true;
    }

    public static List<PwmSetting> getSettings(Category category) {
        final List<PwmSetting> returnList = new ArrayList<PwmSetting>();
        for (final PwmSetting setting : PwmSetting.values()) {
            if (setting.getCategory() == category) {
                returnList.add(setting);
            }
        }
        return Collections.unmodifiableList(returnList);
    }

    public static List<PwmSetting> getSettings(Category category, int level) {
        final List<PwmSetting> returnList = new ArrayList<PwmSetting>();
        for (final PwmSetting setting : PwmSetting.values()) {
            if (setting.showSetting(category, level)) {
                returnList.add(setting);
            }
        }
        return Collections.unmodifiableList(returnList);
    }

    // -------------------------- OTHER METHODS --------------------------

    public StoredValue getDefaultValue(final Template template) throws PwmOperationalException {
        if (this.getSyntax() == PwmSettingSyntax.PASSWORD) {
            return new PasswordValue("");
        }
        final Element settingElement = readSettingXml(this);
        final XPathFactory xpfac = XPathFactory.instance();
        Element defaultElement = null;
        if (template != null) {
            XPathExpression xp = xpfac.compile("default[@template=\"" + template.toString() + "\"]");
            defaultElement = (Element)xp.evaluateFirst(settingElement);
        }
        if (defaultElement == null) {
            XPathExpression xp = xpfac.compile("default[not(@template)]");
            defaultElement = (Element)xp.evaluateFirst(settingElement);
        }
        if (defaultElement == null) {
            throw new IllegalStateException("no default value for setting " + this.getKey());
        }
        return ValueFactory.fromXmlValues(this,defaultElement,this.getKey());
    }

    public Map<String,String> getOptions() {
        final Element settingElement = readSettingXml(this);
        final Element optionsElement = settingElement.getChild("options");
        final Map<String,String> returnList = new LinkedHashMap<String, String>();
        if (optionsElement != null) {
            final List<Element> optionElements = optionsElement.getChildren("option");
            if (optionElements != null) {
                for (Element optionElement : optionElements) {
                    if (optionElement.getAttribute("value") == null) {
                        throw new IllegalStateException("option element is missing 'value' attribute for key " + this.getKey());
                    }
                    returnList.put(optionElement.getAttribute("value").getValue(),optionElement.getValue());
                }
            }
        }

        return Collections.unmodifiableMap(returnList);
    }

    public String getLabel(final Locale locale) {
        final Element settingElement = readSettingXml(this);
        if (settingElement == null) {
            throw new IllegalStateException("missing Setting value for setting " + this.getKey());
        }
        final Element labelElement = settingElement.getChild("label");
        return labelElement.getText();
    }

    public String getDescription(final Locale locale) {
        final Element settingElement = readSettingXml(this);
        final Element descriptionElement = settingElement.getChild("description");
        return descriptionElement.getText();
    }

    public String getPlaceholder(final Locale locale) {
        Element placeHolderElement = readSettingXml(this);
        Element placeholder = placeHolderElement.getChild("placeholder");
        return placeholder != null ? placeholder.getText() : "";
    }

    public boolean isRequired() {
        final Element settingElement = readSettingXml(this);
        final Attribute requiredAttribute = settingElement.getAttribute("required");
        return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
    }

    public boolean isHidden() {
        final Element settingElement = readSettingXml(this);
        final Attribute requiredAttribute = settingElement.getAttribute("hidden");
        return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
    }

    public int getLevel() {
        final Element settingElement = readSettingXml(this);
        final Attribute levelAttribute = settingElement.getAttribute("level");
        return levelAttribute != null ? Integer.parseInt(levelAttribute.getValue()) : 0;
    }

    public Pattern getRegExPattern() {
        Element settingNode = readSettingXml(this);
        Element regexNode = settingNode.getChild("regex");
        if (regexNode == null) {
            return Static.DEFAULT_REGEX;
        }
        try {
            return Pattern.compile(regexNode.getText());
        } catch (PatternSyntaxException e) {
            final String errorMsg = "error compiling regex constraints for setting " + this.toString() + ", error: " + e.getMessage();
            LOGGER.error(errorMsg,e);
            throw new IllegalStateException(errorMsg,e);
        }
    }

    public enum Category {
        GENERAL             (Type.SETTING),
        LDAP_PROFILE        (Type.PROFILE),
        LDAP_GLOBAL         (Type.SETTING),
        USER_INTERFACE      (Type.SETTING),
        PASSWORD_GLOBAL     (Type.SETTING),
        PASSWORD_POLICY     (Type.PROFILE),
        CHALLENGE           (Type.SETTING),
        CHALLENGE_POLICY    (Type.PROFILE),
        EMAIL               (Type.SETTING),
        SMS                 (Type.SETTING),
        SECURITY            (Type.SETTING),
        INTRUDER            (Type.SETTING),
        TOKEN               (Type.SETTING),
        OTP                 (Type.SETTING),
        LOGGING             (Type.SETTING),
        EDIRECTORY          (Type.SETTING),
        ACTIVE_DIRECTORY    (Type.SETTING),
        DATABASE            (Type.SETTING),
        REPORTING           (Type.SETTING),
        MISC                (Type.SETTING),
        OAUTH               (Type.SETTING),
        CHANGE_PASSWORD     (Type.MODULE),
        RECOVERY            (Type.MODULE),
        FORGOTTEN_USERNAME  (Type.MODULE),
        NEWUSER             (Type.MODULE),
        GUEST               (Type.MODULE),
        ACTIVATION          (Type.MODULE),
        UPDATE              (Type.MODULE),
        SHORTCUT            (Type.MODULE),
        PEOPLE_SEARCH       (Type.MODULE),
        HELPDESK            (Type.MODULE),

        ;

        public enum Type {
            SETTING, MODULE, PROFILE
        }

        private final Type type;

        private Category(final Type type) {
            this.type = type;
        }

        public String getKey() {
            return this.toString();
        }

        public PwmSetting getProfileSetting()
        {
            switch (this) {
                case LDAP_PROFILE:
                    return LDAP_PROFILE_LIST;
                case PASSWORD_POLICY:
                    return PASSWORD_PROFILE_LIST;
                case CHALLENGE_POLICY:
                    return CHALLENGE_PROFILE_LIST;
            }
            throw new IllegalArgumentException("category " + this.toString() + " does not have a profileSetting value");
        }

        public String getLabel(final Locale locale) {
            final Element categoryElement = readCategoryXml(this);
            if (categoryElement == null) {
                throw new IllegalStateException("missing descriptor element for category " + this.toString());
            }
            final Element labelElement = categoryElement.getChild("label");
            if (labelElement == null) {
                throw new IllegalStateException("missing descriptor label for category " + this.toString());
            }
            return labelElement.getText();
        }

        public String getDescription(final Locale locale) {
            Element categoryElement = readCategoryXml(this);
            Element description = categoryElement.getChild("description");
            return description.getText();
        }

        public Type getType() {
            return type;
        }

        public int getLevel() {
            final Element settingElement = readCategoryXml(this);
            final Attribute levelAttribute = settingElement.getAttribute("level");
            return levelAttribute != null ? Integer.parseInt(levelAttribute.getValue()) : 0;
        }

        public boolean isHidden() {
            final Element settingElement = readCategoryXml(this);
            final Attribute requiredAttribute = settingElement.getAttribute("hidden");
            return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
        }
    }

    public enum Template {
        NOVL,
        AD,
        DEFAULT,
        ;

        public String getLabel(final Locale locale) {
            Element categoryElement = readTemplateXml(this);
            Element labelElement = categoryElement.getChild("label");
            return labelElement.getText();
        }

    }

    public static Map<PwmSetting.Category, List<PwmSetting>> valuesByFilter(final Template template, final Category.Type categoryType, final int level) {
        final long startTime = System.currentTimeMillis();
        final List<PwmSetting> settingList = new ArrayList<PwmSetting>(Arrays.asList(PwmSetting.values()));

        for (Iterator<PwmSetting> iter = settingList.iterator(); iter.hasNext();) {
            final PwmSetting loopSetting = iter.next();
            if (categoryType != null && loopSetting.getCategory().getType() != categoryType) {
                iter.remove();
            } else if (level >= 0 && loopSetting.getLevel() > level) {
                iter.remove();
            } else if (template != null && !loopSetting.getTemplates().contains(template)) {
                iter.remove();
            } else if (loopSetting.isHidden() || loopSetting.getCategory().isHidden()) {
                iter.remove();
            }
        }

        final Map<PwmSetting.Category, List<PwmSetting>> returnMap = new TreeMap<PwmSetting.Category, List<PwmSetting>>();
        for (final PwmSetting loopSetting : settingList) {
            if (!returnMap.containsKey(loopSetting.getCategory())) {
                returnMap.put(loopSetting.getCategory(),new ArrayList<PwmSetting>());
            }
            returnMap.get(loopSetting.getCategory()).add(loopSetting);
        }
        for (final Category category : returnMap.keySet()) {
            returnMap.put(category,Collections.unmodifiableList(returnMap.get(category)));
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

    private static Element readSettingXml(final PwmSetting setting) {
        final XPathFactory xpfac = XPathFactory.instance();
        final XPathExpression xp = xpfac.compile("/settings/setting[@key=\"" + setting.getKey() + "\"]");
        return (Element)xp.evaluateFirst(readXml());
    }

    private static Element readCategoryXml(final Category category) {
        final XPathFactory xpfac = XPathFactory.instance();
        final XPathExpression xp = xpfac.compile("/settings/category[@key=\"" + category.toString() + "\"]");
        return (Element)xp.evaluateFirst(readXml());
    }

    private static Element readTemplateXml(final Template template) {
        final XPathFactory xpfac = XPathFactory.instance();
        final XPathExpression xp = xpfac.compile("/settings/template[@key=\"" + template.toString() + "\"]");
        return (Element)xp.evaluateFirst(readXml());
    }

    private static Document xmlDocCache = null;
    private static Document readXml() {
        if (xmlDocCache == null) {
            //validateXmlSchema();
            InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream("password/pwm/config/PwmSetting.xml");
            final SAXBuilder builder = new SAXBuilder();
            try {
                xmlDocCache = builder.build(inputStream);
            } catch (JDOMException e) {
                throw new IllegalStateException("error parsing PwmSetting.xml: " + e.getMessage());
            } catch (IOException e) {
                throw new IllegalStateException("unable to load PwmSetting.xml: " + e.getMessage());
            }
        }
        return xmlDocCache;
    }

    private static void validateXmlSchema() {
        try {
            final InputStream xsdInputStream = PwmSetting.class.getClassLoader().getResourceAsStream("password/pwm/config/PwmSetting.xsd");
            final InputStream xmlInputStream = PwmSetting.class.getClassLoader().getResourceAsStream("password/pwm/config/PwmSetting.xml");
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final Schema schema = factory.newSchema(new StreamSource(xsdInputStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xmlInputStream));
        } catch (Exception e) {
            throw new IllegalStateException("error validating PwmSetting.xml schema using PwmSetting.xsd definition: " + e.getMessage());
        }
    }
}

