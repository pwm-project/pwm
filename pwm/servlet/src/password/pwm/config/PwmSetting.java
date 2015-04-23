/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import org.jdom2.Element;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

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
    PWM_SITE_URL(
            "pwm.selfURL", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    VERSION_CHECK_ENABLE(
            "pwm.versionCheck.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.GENERAL),
    PUBLISH_STATS_ENABLE(
            "pwm.publishStats.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.GENERAL),
    PUBLISH_STATS_SITE_DESCRIPTION(
            "pwm.publishStats.siteDescription", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    URL_FORWARD(
            "pwm.forwardURL", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    URL_LOGOUT(
            "pwm.logoutURL", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    URL_HOME(
            "pwm.homeURL", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    PWM_INSTANCE_NAME(
            "pwmInstanceName", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    IDLE_TIMEOUT_SECONDS(
            "idleTimeoutSeconds", PwmSettingSyntax.DURATION, PwmSettingCategory.GENERAL),
    HIDE_CONFIGURATION_HEALTH_WARNINGS(
            "display.hideConfigHealthWarnings", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.GENERAL),
    KNOWN_LOCALES(
            "knownLocales", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.GENERAL),
    LOCALE_COOKIE_MAX_AGE(
            "locale.cookie.age", PwmSettingSyntax.DURATION, PwmSettingCategory.GENERAL),
    PWMDB_LOCATION(
            "pwmDb.location", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    HTTP_PROXY_URL(
            "http.proxy.url", PwmSettingSyntax.STRING, PwmSettingCategory.GENERAL),
    APP_PROPERTY_OVERRIDES(
            "pwm.appProperty.overrides", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.GENERAL),

    // user interface
    INTERFACE_THEME(
            "interface.theme", PwmSettingSyntax.SELECT, PwmSettingCategory.UI_WEB),
    DISPLAY_SHOW_HIDE_PASSWORD_FIELDS(
            "display.showHidePasswordFields", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_MASK_PASSWORD_FIELDS(
            "display.maskPasswordFields", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_MASK_RESPONSE_FIELDS(
            "display.maskResponseFields", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_CANCEL_BUTTON(
            "display.showCancelButton", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_RESET_BUTTON(
            "display.showResetButton", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_SUCCESS_PAGES(
            "display.showSuccessPage", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_LOGIN_PAGE_OPTIONS(
            "display.showLoginPageOptions", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_LOGOUT_BUTTON(
            "display.logoutButton", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_HOME_BUTTON(
            "display.homeButton", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_IDLE_TIMEOUT(
            "display.idleTimeout", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UI_FEATURES),
    DISPLAY_CSS_CUSTOM_STYLE(
            "display.css.customStyleLocation", PwmSettingSyntax.STRING, PwmSettingCategory.UI_WEB),
    DISPLAY_CSS_CUSTOM_MOBILE_STYLE(
            "display.css.customMobileStyleLocation", PwmSettingSyntax.STRING, PwmSettingCategory.UI_WEB),
    DISPLAY_CSS_EMBED(
            "display.css.customStyle", PwmSettingSyntax.TEXT_AREA, PwmSettingCategory.UI_WEB),
    DISPLAY_CSS_MOBILE_EMBED(
            "display.css.customMobileStyle", PwmSettingSyntax.TEXT_AREA, PwmSettingCategory.UI_WEB),
    DISPLAY_CUSTOM_JAVASCRIPT(
            "display.js.custom", PwmSettingSyntax.TEXT_AREA, PwmSettingCategory.UI_WEB),
    DISPLAY_CUSTOM_RESOURCE_BUNDLE(
            "display.custom.resourceBundle", PwmSettingSyntax.FILE, PwmSettingCategory.UI_WEB),

    // change password
    QUERY_MATCH_CHANGE_PASSWORD(
            "password.allowChange.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.CHANGE_PASSWORD),
    LOGOUT_AFTER_PASSWORD_CHANGE(
            "logoutAfterPasswordChange", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_REQUIRE_FORM(
            "password.require.form", PwmSettingSyntax.FORM, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_REQUIRE_CURRENT(
            "password.change.requireCurrent", PwmSettingSyntax.SELECT, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_CHANGE_AGREEMENT_MESSAGE(
            "display.password.changeAgreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_COMPLETE_MESSAGE(
            "display.password.completeMessage", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.CHANGE_PASSWORD),
    DISPLAY_PASSWORD_GUIDE_TEXT(
            "display.password.guideText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_SYNC_ENABLE_REPLICA_CHECK(
            "passwordSync.enableReplicaCheck", PwmSettingSyntax.SELECT, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_SYNC_MIN_WAIT_TIME(
            "passwordSyncMinWaitTime", PwmSettingSyntax.DURATION, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_SYNC_MAX_WAIT_TIME(
            "passwordSyncMaxWaitTime", PwmSettingSyntax.DURATION, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_EXPIRE_PRE_TIME(
            "expirePreTime", PwmSettingSyntax.DURATION, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_EXPIRE_WARN_TIME(
            "expireWarnTime", PwmSettingSyntax.DURATION, PwmSettingCategory.CHANGE_PASSWORD),
    EXPIRE_CHECK_DURING_AUTH(
            "expireCheckDuringAuth", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHANGE_PASSWORD),
    SEEDLIST_FILENAME(
            "pwm.seedlist.location", PwmSettingSyntax.STRING, PwmSettingCategory.CHANGE_PASSWORD),
    CHANGE_PASSWORD_WRITE_ATTRIBUTES(
            "changePassword.writeAttributes", PwmSettingSyntax.ACTION, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_SHOW_AUTOGEN(
            "password.showAutoGen", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHANGE_PASSWORD),
    PASSWORD_SHOW_STRENGTH_METER(
            "password.showStrengthMeter", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHANGE_PASSWORD),

    // account info
    ACCOUNT_INFORMATION_ENABLED(
            "display.accountInformation", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACCOUNT_INFO),
    ACCOUNT_INFORMATION_HISTORY(
            "display.passwordHistory", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACCOUNT_INFO),
    ACCOUNT_INFORMATION_VIEW_STATUS_VALUES(
            "accountInfo.viewStatusValues", PwmSettingSyntax.OPTIONLIST, PwmSettingCategory.ACCOUNT_INFO),

    //ldap directories
    LDAP_SERVER_URLS(
            "ldap.serverUrls", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.LDAP_PROFILE),
    LDAP_SERVER_CERTS(
            "ldap.serverCerts", PwmSettingSyntax.X509CERT, PwmSettingCategory.LDAP_PROFILE),
    LDAP_PROXY_USER_DN(
            "ldap.proxy.username", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    LDAP_PROXY_USER_PASSWORD(
            "ldap.proxy.password", PwmSettingSyntax.PASSWORD, PwmSettingCategory.LDAP_PROFILE),
    LDAP_CONTEXTLESS_ROOT(
            "ldap.rootContexts", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.LDAP_PROFILE),
    LDAP_TEST_USER_DN(
            "ldap.testuser.username", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    AUTO_ADD_OBJECT_CLASSES(
            "ldap.addObjectClasses", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.LDAP_PROFILE),
    LDAP_USERNAME_SEARCH_FILTER(
            "ldap.usernameSearchFilter", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    LDAP_USERNAME_ATTRIBUTE(
            "ldap.username.attr", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    LDAP_GUID_AUTO_ADD(
            "ldap.guid.autoAddValue", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.LDAP_PROFILE),
    LDAP_GUID_ATTRIBUTE(
            "ldap.guidAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    LDAP_LOGIN_CONTEXTS(
            "ldap.selectableContexts", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.LDAP_PROFILE),
    LDAP_PROFILE_DISPLAY_NAME(
            "ldap.profile.displayName", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.LDAP_PROFILE),
    LDAP_PROFILE_ENABLED(
            "ldap.profile.enabled", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.LDAP_PROFILE),
    LDAP_NAMING_ATTRIBUTE(
            "ldap.namingAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    PASSWORD_LAST_UPDATE_ATTRIBUTE(
            "passwordLastUpdateAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    LDAP_USER_GROUP_ATTRIBUTE(
            "ldap.user.group.attribute", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),
    LDAP_GROUP_LABEL_ATTRIBUTE(
            "ldap.group.label.attribute", PwmSettingSyntax.STRING, PwmSettingCategory.LDAP_PROFILE),


    // ldap global settings
    LDAP_PROFILE_LIST(
            "ldap.profile.list", PwmSettingSyntax.PROFILE, PwmSettingCategory.GENERAL),
    LDAP_IDLE_TIMEOUT(
            "ldap.idleTimeout", PwmSettingSyntax.NUMERIC, PwmSettingCategory.LDAP_GLOBAL),
    DEFAULT_OBJECT_CLASSES(
            "ldap.defaultObjectClasses", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.LDAP_GLOBAL),
    LDAP_FOLLOW_REFERRALS(
            "ldap.followReferrals", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.LDAP_GLOBAL),
    LDAP_DUPLICATE_MODE(
            "ldap.duplicateMode", PwmSettingSyntax.SELECT, PwmSettingCategory.LDAP_GLOBAL),
    LDAP_SELECTABLE_CONTEXT_MODE(
            "ldap.selectableContextMode", PwmSettingSyntax.SELECT, PwmSettingCategory.LDAP_GLOBAL),
    LDAP_IGNORE_UNREACHABLE_PROFILES(
            "ldap.ignoreUnreachableProfiles", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.LDAP_GLOBAL),
    LDAP_ENABLE_WIRE_TRACE(
            "ldap.wireTrace.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.LDAP_GLOBAL),


    // email settings
    EMAIL_SERVER_ADDRESS(
            "email.smtp.address", PwmSettingSyntax.STRING, PwmSettingCategory.EMAIL_SETTINGS),
    EMAIL_SERVER_PORT(
            "email.smtp.port", PwmSettingSyntax.NUMERIC, PwmSettingCategory.EMAIL_SETTINGS),
    EMAIL_DEFAULT_FROM_ADDRESS(
            "email.default.fromAddress", PwmSettingSyntax.STRING, PwmSettingCategory.EMAIL_SETTINGS),
    EMAIL_USERNAME(
            "email.smtp.username", PwmSettingSyntax.STRING, PwmSettingCategory.EMAIL_SETTINGS),
    EMAIL_PASSWORD(
            "email.smtp.userpassword", PwmSettingSyntax.PASSWORD, PwmSettingCategory.EMAIL_SETTINGS),
    EMAIL_USER_MAIL_ATTRIBUTE(
            "email.userMailAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.EMAIL_SETTINGS),
    EMAIL_MAX_QUEUE_AGE(
            "email.queueMaxAge", PwmSettingSyntax.DURATION, PwmSettingCategory.EMAIL_SETTINGS),
    EMAIL_CHANGEPASSWORD(
            "email.changePassword", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_CHANGEPASSWORD_HELPDESK(
            "email.changePassword.helpdesk", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_UPDATEPROFILE(
            "email.updateProfile", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_NEWUSER(
            "email.newUser", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_NEWUSER_VERIFICATION(
            "email.newUser.token", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_ACTIVATION(
            "email.activation", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_ACTIVATION_VERIFICATION(
            "email.activation.token", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_CHALLENGE_TOKEN(
            "email.challenge.token", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_HELPDESK_TOKEN(
            "email.helpdesk.token", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_GUEST(
            "email.guest", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_UPDATEGUEST(
            "email.updateguest", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_SENDPASSWORD(
            "email.sendpassword", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_SEND_USERNAME(
            "email.sendUsername", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_INTRUDERNOTICE(
            "email.intruderNotice", PwmSettingSyntax.EMAIL, PwmSettingCategory.EMAIL_TEMPLATES),
    EMAIL_ADVANCED_SETTINGS(
            "email.smtp.advancedSettings", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.EMAIL_SETTINGS),

    // sms settings
    SMS_USER_PHONE_ATTRIBUTE(
            "sms.userSmsAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.SMS_GATEWAY),
    SMS_MAX_QUEUE_AGE(
            "sms.queueMaxAge", PwmSettingSyntax.DURATION, PwmSettingCategory.SMS_GATEWAY),
    SMS_GATEWAY_URL(
            "sms.gatewayURL", PwmSettingSyntax.STRING, PwmSettingCategory.SMS_GATEWAY),
    SMS_GATEWAY_USER(
            "sms.gatewayUser", PwmSettingSyntax.STRING, PwmSettingCategory.SMS_GATEWAY),
    SMS_GATEWAY_PASSWORD(
            "sms.gatewayPassword", PwmSettingSyntax.PASSWORD, PwmSettingCategory.SMS_GATEWAY),
    SMS_GATEWAY_METHOD(
            "sms.gatewayMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.SMS_GATEWAY),
    SMS_GATEWAY_AUTHMETHOD(
            "sms.gatewayAuthMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.SMS_GATEWAY),
    SMS_REQUEST_DATA(
            "sms.requestData", PwmSettingSyntax.TEXT_AREA, PwmSettingCategory.SMS_GATEWAY),
    SMS_REQUEST_CONTENT_TYPE(
            "sms.requestContentType", PwmSettingSyntax.STRING, PwmSettingCategory.SMS_GATEWAY),
    SMS_REQUEST_CONTENT_ENCODING(
            "sms.requestContentEncoding", PwmSettingSyntax.SELECT, PwmSettingCategory.SMS_GATEWAY),
    SMS_GATEWAY_REQUEST_HEADERS(
            "sms.httpRequestHeaders", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.SMS_GATEWAY),
    SMS_MAX_TEXT_LENGTH(
            "sms.maxTextLength", PwmSettingSyntax.NUMERIC, PwmSettingCategory.SMS_GATEWAY),
    SMS_RESPONSE_OK_REGEX(
            "sms.responseOkRegex", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.SMS_GATEWAY),
    SMS_SENDER_ID(
            "sms.senderID", PwmSettingSyntax.STRING, PwmSettingCategory.SMS_GATEWAY),
    SMS_PHONE_NUMBER_FORMAT(
            "sms.phoneNumberFormat", PwmSettingSyntax.SELECT, PwmSettingCategory.SMS_GATEWAY),
    SMS_DEFAULT_COUNTRY_CODE(
            "sms.defaultCountryCode", PwmSettingSyntax.NUMERIC, PwmSettingCategory.SMS_GATEWAY),
    SMS_REQUESTID_CHARS(
            "sms.requestId.characters", PwmSettingSyntax.STRING, PwmSettingCategory.SMS_GATEWAY),
    SMS_REQUESTID_LENGTH(
            "sms.requestId.length", PwmSettingSyntax.NUMERIC, PwmSettingCategory.SMS_GATEWAY),
    SMS_CHALLENGE_TOKEN_TEXT(
            "sms.challenge.token.message", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.SMS_MESSAGES),
    SMS_CHALLENGE_NEW_PASSWORD_TEXT(
            "sms.challenge.newpassword.message", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.SMS_MESSAGES),
    SMS_NEWUSER_TOKEN_TEXT(
            "sms.newUser.token.message", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.SMS_MESSAGES),
    SMS_HELPDESK_TOKEN_TEXT(
            "sms.helpdesk.token.message", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.SMS_MESSAGES),
    SMS_ACTIVATION_VERIFICATION_TEXT(
            "sms.activation.token.message", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.SMS_MESSAGES),
    SMS_ACTIVATION_TEXT(
            "sms.activation.message", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.SMS_MESSAGES),
    SMS_FORGOTTEN_USERNAME_TEXT(
            "sms.forgottenUsername.message", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.SMS_MESSAGES),
    SMS_USE_URL_SHORTENER(
            "sms.useUrlShortener", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.SMS_GATEWAY),
    SMS_SUCCESS_RESULT_CODE(
            "sms.successResultCodes", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.SMS_GATEWAY),
    URL_SHORTENER_CLASS(
            "urlshortener.classname", PwmSettingSyntax.STRING, PwmSettingCategory.SMS_GATEWAY),
    URL_SHORTENER_PARAMETERS(
            "urlshortener.parameters", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.SMS_GATEWAY),


    //global password policy settings
    PASSWORD_POLICY_SOURCE(
            "password.policy.source", PwmSettingSyntax.SELECT, PwmSettingCategory.PASSWORD_GLOBAL),
    WORDLIST_FILENAME(
            "pwm.wordlist.location", PwmSettingSyntax.STRING, PwmSettingCategory.PASSWORD_GLOBAL),
    PASSWORD_SHAREDHISTORY_ENABLE(
            "password.sharedHistory.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_GLOBAL),
    PASSWORD_SHAREDHISTORY_MAX_AGE(
            "password.sharedHistory.age", PwmSettingSyntax.DURATION, PwmSettingCategory.PASSWORD_GLOBAL),
    WORDLIST_CASE_SENSITIVE(
            "wordlistCaseSensitive", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_GLOBAL),
    PASSWORD_WORDLIST_WORDSIZE(
            "password.wordlist.wordSize", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_GLOBAL),
    PASSWORD_POLICY_CASE_SENSITIVITY(
            "password.policy.caseSensitivity", PwmSettingSyntax.SELECT, PwmSettingCategory.PASSWORD_GLOBAL),
    PASSWORD_PROFILE_LIST(
            "password.profile.list", PwmSettingSyntax.PROFILE, PwmSettingCategory.PASSWORD_GLOBAL),

    // password policy profile settings
    PASSWORD_POLICY_QUERY_MATCH(
            "password.policy.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_LENGTH(
            "password.policy.minimumLength", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_LENGTH(
            "password.policy.maximumLength", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_REPEAT(
            "password.policy.maximumRepeat", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT(
            "password.policy.maximumSequentialRepeat", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_NUMERIC(
            "password.policy.allowNumeric", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC(
            "password.policy.allowFirstCharNumeric", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC(
            "password.policy.allowLastCharNumeric", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_NUMERIC(
            "password.policy.maximumNumeric", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_NUMERIC(
            "password.policy.minimumNumeric", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_SPECIAL(
            "password.policy.allowSpecial", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL(
            "password.policy.allowFirstCharSpecial", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL(
            "password.policy.allowLastCharSpecial", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_SPECIAL(
            "password.policy.maximumSpecial", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_SPECIAL(
            "password.policy.minimumSpecial", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_ALPHA(
            "password.policy.maximumAlpha", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_ALPHA(
            "password.policy.minimumAlpha", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_NON_ALPHA(
            "password.policy.maximumNonAlpha", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_NON_ALPHA(
            "password.policy.minimumNonAlpha", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_UPPERCASE(
            "password.policy.maximumUpperCase", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_UPPERCASE(
            "password.policy.minimumUpperCase", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_LOWERCASE(
            "password.policy.maximumLowerCase", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_LOWERCASE(
            "password.policy.minimumLowerCase", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_UNIQUE(
            "password.policy.minimumUnique", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS(
            "password.policy.maximumOldPasswordChars", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_LIFETIME(
            "password.policy.minimumLifetime", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_ENABLE_WORDLIST(
            "password.policy.checkWordlist", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_AD_COMPLEXITY_LEVEL(
            "password.policy.ADComplexityLevel", PwmSettingSyntax.SELECT, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_AD_COMPLEXITY_MAX_VIOLATIONS(
            "password.policy.ADComplexityMaxViolations", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH(
            "password.policy.regExMatch", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH(
            "password.policy.regExNoMatch", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_DISALLOWED_VALUES(
            "password.policy.disallowedValues", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_DISALLOWED_ATTRIBUTES(
            "password.policy.disallowedAttributes", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MINIMUM_STRENGTH(
            "password.policy.minimumStrength", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_MAXIMUM_CONSECUTIVE(
            "password.policy.maximumConsecutive", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_CHANGE_MESSAGE(
            "password.policy.changeMessage", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_RULE_TEXT(
            "password.policy.ruleText", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_DISALLOW_CURRENT(
            "password.policy.disallowCurrent", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_CHAR_GROUPS_MIN_MATCH(
            "password.policy.charGroup.minimumMatch", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PASSWORD_POLICY),
    PASSWORD_POLICY_CHAR_GROUPS(
            "password.policy.charGroup.regExValues", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PASSWORD_POLICY),


    // security settings
    PWM_SECURITY_KEY(
            "pwm.securityKey", PwmSettingSyntax.PASSWORD, PwmSettingCategory.APP_SECURITY),
    SECURITY_ENABLE_REQUEST_SEQUENCE(
            "security.page.enableRequestSequence", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.WEB_SECURITY),
    SECURITY_ENABLE_FORM_NONCE(
            "security.formNonce.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.WEB_SECURITY),
    ENABLE_SESSION_VERIFICATION(
            "enableSessionVerification", PwmSettingSyntax.SELECT, PwmSettingCategory.WEB_SECURITY),
    DISALLOWED_HTTP_INPUTS(
            "disallowedInputs", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.WEB_SECURITY),
    REQUIRE_HTTPS(
            "pwm.requireHTTPS", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.WEB_SECURITY),
    FORCE_BASIC_AUTH(
            "forceBasicAuth", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.WEB_SECURITY),
    USE_X_FORWARDED_FOR_HEADER(
            "useXForwardedForHeader", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.WEB_SECURITY),
    REVERSE_DNS_ENABLE(
            "network.reverseDNS.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.APP_SECURITY),
    MULTI_IP_SESSION_ALLOWED(
            "network.allowMultiIPSession", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.WEB_SECURITY),
    REQUIRED_HEADERS(
            "network.requiredHttpHeaders", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.WEB_SECURITY),
    IP_PERMITTED_RANGE(
            "network.ip.permittedRange", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.WEB_SECURITY),
    SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT(
            "security.page.leaveNoticeTimeout", PwmSettingSyntax.NUMERIC, PwmSettingCategory.WEB_SECURITY),
    DISPLAY_SHOW_DETAILED_ERRORS(
            "display.showDetailedErrors", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.APP_SECURITY),
    SESSION_MAX_SECONDS(
            "session.maxSeconds", PwmSettingSyntax.DURATION, PwmSettingCategory.APP_SECURITY),
    SECURITY_PREVENT_FRAMING(
            "security.preventFraming", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.WEB_SECURITY),
    SECURITY_REDIRECT_WHITELIST(
            "security.redirectUrl.whiteList", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.WEB_SECURITY),
    SECURITY_CSP_HEADER(
            "security.cspHeader", PwmSettingSyntax.STRING, PwmSettingCategory.WEB_SECURITY),

    // catpcha
    RECAPTCHA_KEY_PUBLIC(
            "captcha.recaptcha.publicKey", PwmSettingSyntax.STRING, PwmSettingCategory.CAPTCHA),
    RECAPTCHA_KEY_PRIVATE(
            "captcha.recaptcha.privateKey", PwmSettingSyntax.PASSWORD, PwmSettingCategory.CAPTCHA),
    CAPTCHA_PROTECTED_PAGES(
            "captcha.protectedPages", PwmSettingSyntax.OPTIONLIST, PwmSettingCategory.CAPTCHA),
    CAPTCHA_SKIP_PARAM(
            "captcha.skip.param", PwmSettingSyntax.STRING, PwmSettingCategory.CAPTCHA),
    CAPTCHA_SKIP_COOKIE(
            "captcha.skip.cookie", PwmSettingSyntax.STRING, PwmSettingCategory.CAPTCHA),

    // intruder detection
    INTRUDER_ENABLE(
            "intruder.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.INTRUDER_SETTINGS),
    INTRUDER_STORAGE_METHOD(
            "intruder.storageMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.INTRUDER_SETTINGS),
    SECURITY_SIMULATE_LDAP_BAD_PASSWORD(
            "security.ldap.simulateBadPassword", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.INTRUDER_SETTINGS),

    INTRUDER_USER_RESET_TIME(
            "intruder.user.resetTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_USER_MAX_ATTEMPTS(
            "intruder.user.maxAttempts", PwmSettingSyntax.NUMERIC, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_USER_CHECK_TIME(
            "intruder.user.checkTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_ATTRIBUTE_RESET_TIME(
            "intruder.attribute.resetTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_ATTRIBUTE_MAX_ATTEMPTS(
            "intruder.attribute.maxAttempts", PwmSettingSyntax.NUMERIC, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_ATTRIBUTE_CHECK_TIME(
            "intruder.attribute.checkTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_TOKEN_DEST_RESET_TIME(
            "intruder.tokenDest.resetTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_TOKEN_DEST_MAX_ATTEMPTS(
            "intruder.tokenDest.maxAttempts", PwmSettingSyntax.NUMERIC, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_TOKEN_DEST_CHECK_TIME(
            "intruder.tokenDest.checkTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_ADDRESS_RESET_TIME(
            "intruder.address.resetTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_ADDRESS_MAX_ATTEMPTS(
            "intruder.address.maxAttempts", PwmSettingSyntax.NUMERIC, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_ADDRESS_CHECK_TIME(
            "intruder.address.checkTime", PwmSettingSyntax.DURATION, PwmSettingCategory.INTRUDER_TIMEOUTS),
    INTRUDER_SESSION_MAX_ATTEMPTS(
            "intruder.session.maxAttempts", PwmSettingSyntax.NUMERIC, PwmSettingCategory.INTRUDER_TIMEOUTS),

    // token settings
    TOKEN_STORAGEMETHOD(
            "token.storageMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.TOKEN),
    TOKEN_CHARACTERS(
            "token.characters", PwmSettingSyntax.STRING, PwmSettingCategory.TOKEN),
    TOKEN_LENGTH(
            "token.length", PwmSettingSyntax.NUMERIC, PwmSettingCategory.TOKEN),
    TOKEN_LIFETIME(
            "token.lifetime", PwmSettingSyntax.DURATION, PwmSettingCategory.TOKEN),
    TOKEN_LDAP_ATTRIBUTE(
            "token.ldap.attribute", PwmSettingSyntax.STRING, PwmSettingCategory.TOKEN),

    // OTP
    OTP_ENABLED(
            "otp.enabled", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.OTP),
    OTP_FORCE_SETUP(
            "otp.forceSetup", PwmSettingSyntax.SELECT, PwmSettingCategory.OTP),
    OTP_SECRET_READ_PREFERENCE(
            "otp.secret.readPreference", PwmSettingSyntax.SELECT, PwmSettingCategory.OTP),
    OTP_SECRET_WRITE_PREFERENCE(
            "otp.secret.writePreference", PwmSettingSyntax.SELECT, PwmSettingCategory.OTP),
    OTP_SECRET_STORAGEFORMAT(
            "otp.secret.storageFormat", PwmSettingSyntax.SELECT, PwmSettingCategory.OTP),
    OTP_SECRET_ENCRYPT(
            "otp.secret.encrypt", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.OTP),
    OTP_SECRET_LDAP_ATTRIBUTE(
            "otp.secret.ldap.attribute", PwmSettingSyntax.STRING, PwmSettingCategory.OTP),
    OTP_SETUP_USER_PERMISSION(
            "otp.secret.allowSetup.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.OTP),
    OTP_SECRET_IDENTIFIER(
            "otp.secret.identifier", PwmSettingSyntax.STRING, PwmSettingCategory.OTP),
    OTP_RECOVERY_CODES(
            "otp.secret.recoveryCodes", PwmSettingSyntax.NUMERIC, PwmSettingCategory.OTP),

    // logger settings
    EVENTS_JAVA_STDOUT_LEVEL(
            "events.java.stdoutLevel", PwmSettingSyntax.SELECT, PwmSettingCategory.LOGGING),
    EVENTS_LOCALDB_LOG_LEVEL(
            "events.pwmDB.logLevel", PwmSettingSyntax.SELECT, PwmSettingCategory.LOGGING),
    EVENTS_FILE_LEVEL(
            "events.fileAppender.level", PwmSettingSyntax.SELECT, PwmSettingCategory.LOGGING),
    EVENTS_PWMDB_MAX_EVENTS(
            "events.pwmDB.maxEvents", PwmSettingSyntax.NUMERIC, PwmSettingCategory.LOGGING),
    EVENTS_PWMDB_MAX_AGE(
            "events.pwmDB.maxAge", PwmSettingSyntax.DURATION, PwmSettingCategory.LOGGING),
    EVENTS_ALERT_DAILY_SUMMARY(
            "events.alert.dailySummary.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.LOGGING),
    EVENTS_JAVA_LOG4JCONFIG_FILE(
            "events.java.log4jconfigFile", PwmSettingSyntax.STRING, PwmSettingCategory.LOGGING),


    // auditingsettings
    AUDIT_SYSTEM_EVENTS(
            "audit.system.eventList", PwmSettingSyntax.OPTIONLIST, PwmSettingCategory.AUDIT_CONFIG),
    AUDIT_USER_EVENTS(
            "audit.user.eventList", PwmSettingSyntax.OPTIONLIST, PwmSettingCategory.AUDIT_CONFIG),
    EVENTS_AUDIT_MAX_AGE(
            "events.audit.maxAge", PwmSettingSyntax.DURATION, PwmSettingCategory.AUDIT_CONFIG),

    EVENTS_USER_STORAGE_METHOD(
            "events.user.storageMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.USER_HISTORY),
    EVENTS_LDAP_ATTRIBUTE(
            "events.ldap.attribute", PwmSettingSyntax.STRING, PwmSettingCategory.USER_HISTORY),
    EVENTS_LDAP_MAX_EVENTS(
            "events.ldap.maxEvents", PwmSettingSyntax.NUMERIC, PwmSettingCategory.USER_HISTORY),

    AUDIT_EMAIL_SYSTEM_TO(
            "email.adminAlert.toAddress", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.AUDIT_FORWARD),
    AUDIT_EMAIL_USER_TO(
            "audit.userEvent.toAddress", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.AUDIT_FORWARD),
    AUDIT_SYSLOG_SERVERS(
            "audit.syslog.servers", PwmSettingSyntax.STRING, PwmSettingCategory.AUDIT_FORWARD),
    AUDIT_SYSLOG_CERTIFICATES(
            "audit.syslog.certificates", PwmSettingSyntax.X509CERT, PwmSettingCategory.AUDIT_FORWARD),


    // challenge settings
    CHALLENGE_ENABLE(
            "challenge.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHALLENGE),
    CHALLENGE_FORCE_SETUP(
            "challenge.forceSetup", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHALLENGE),
    CHALLENGE_SHOW_CONFIRMATION(
            "challenge.showConfirmation", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHALLENGE),
    CHALLENGE_CASE_INSENSITIVE(
            "challenge.caseInsensitive", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHALLENGE),
    CHALLENGE_ALLOW_DUPLICATE_RESPONSES(
            "challenge.allowDuplicateResponses", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHALLENGE),
    QUERY_MATCH_SETUP_RESPONSE(
            "challenge.allowSetup.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.CHALLENGE),
    QUERY_MATCH_CHECK_RESPONSES(
            "command.checkResponses.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.CHALLENGE),
    CHALLENGE_ENFORCE_MINIMUM_PASSWORD_LIFETIME(
            "challenge.enforceMinimumPasswordLifetime", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.CHALLENGE),

    // challenge policy profile
    CHALLENGE_PROFILE_LIST(
            "challenge.profile.list", PwmSettingSyntax.PROFILE, PwmSettingCategory.GENERAL),
    CHALLENGE_POLICY_QUERY_MATCH(
            "challenge.policy.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.CHALLENGE_POLICY),
    CHALLENGE_RANDOM_CHALLENGES(
            "challenge.randomChallenges", PwmSettingSyntax.CHALLENGE, PwmSettingCategory.CHALLENGE_POLICY),
    CHALLENGE_REQUIRED_CHALLENGES(
            "challenge.requiredChallenges", PwmSettingSyntax.CHALLENGE, PwmSettingCategory.CHALLENGE_POLICY),
    CHALLENGE_MIN_RANDOM_REQUIRED(
            "challenge.minRandomRequired", PwmSettingSyntax.NUMERIC, PwmSettingCategory.CHALLENGE_POLICY),
    CHALLENGE_MIN_RANDOM_SETUP(
            "challenge.minRandomsSetup", PwmSettingSyntax.NUMERIC, PwmSettingCategory.CHALLENGE_POLICY),
    CHALLENGE_HELPDESK_RANDOM_CHALLENGES(
            "challenge.helpdesk.randomChallenges", PwmSettingSyntax.CHALLENGE, PwmSettingCategory.CHALLENGE_POLICY),
    CHALLENGE_HELPDESK_REQUIRED_CHALLENGES(
            "challenge.helpdesk.requiredChallenges", PwmSettingSyntax.CHALLENGE, PwmSettingCategory.CHALLENGE_POLICY),
    CHALLENGE_HELPDESK_MIN_RANDOM_SETUP(
            "challenge.helpdesk.minRandomsSetup", PwmSettingSyntax.NUMERIC, PwmSettingCategory.CHALLENGE_POLICY),


    // recovery settings
    FORGOTTEN_PASSWORD_ENABLE(
            "recovery.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.RECOVERY_SETTINGS),
    FORGOTTEN_PASSWORD_SEARCH_FORM(
            "recovery.form", PwmSettingSyntax.FORM, PwmSettingCategory.RECOVERY_SETTINGS),
    FORGOTTEN_PASSWORD_SEARCH_FILTER(
            "recovery.searchFilter", PwmSettingSyntax.STRING, PwmSettingCategory.RECOVERY_SETTINGS),
    FORGOTTEN_PASSWORD_READ_PREFERENCE(
            "recovery.response.readPreference", PwmSettingSyntax.SELECT, PwmSettingCategory.RECOVERY_SETTINGS),
    FORGOTTEN_PASSWORD_WRITE_PREFERENCE(
            "recovery.response.writePreference", PwmSettingSyntax.SELECT, PwmSettingCategory.RECOVERY_SETTINGS),
    CHALLENGE_USER_ATTRIBUTE(
            "challenge.userAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.RECOVERY_SETTINGS),
    CHALLENGE_STORAGE_HASHED(
            "response.hashMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.RECOVERY_SETTINGS),
    FORGOTTEN_USER_POST_ACTIONS(
            "recovery.postActions", PwmSettingSyntax.ACTION, PwmSettingCategory.RECOVERY_SETTINGS),

    // recovery profile
    RECOVERY_PROFILE_LIST(
            "recovery.profile.list", PwmSettingSyntax.PROFILE, PwmSettingCategory.RECOVERY_SETTINGS),
    RECOVERY_PROFILE_QUERY_MATCH(
            "recovery.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.RECOVERY_PROFILE),
    RECOVERY_VERIFICATION_METHODS(
            "recovery.verificationMethods", PwmSettingSyntax.VERIFICATION_METHOD, PwmSettingCategory.RECOVERY_PROFILE),
    RECOVERY_TOKEN_SEND_METHOD(
            "challenge.token.sendMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.RECOVERY_PROFILE),
    RECOVERY_ALLOW_UNLOCK(
            "challenge.allowUnlock", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.RECOVERY_PROFILE),
    RECOVERY_ACTION(
            "recovery.action", PwmSettingSyntax.SELECT, PwmSettingCategory.RECOVERY_PROFILE),
    RECOVERY_SENDNEWPW_METHOD(
            "recovery.sendNewPassword.sendMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.RECOVERY_PROFILE),
    RECOVERY_ATTRIBUTE_FORM(
            "challenge.requiredAttributes", PwmSettingSyntax.FORM, PwmSettingCategory.RECOVERY_PROFILE),
    RECOVERY_ALLOW_WHEN_LOCKED(
            "recovery.allowWhenLocked", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.RECOVERY_PROFILE),


    //
    // forgotten username
    FORGOTTEN_USERNAME_ENABLE(
            "forgottenUsername.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_FORM(
            "forgottenUsername.form", PwmSettingSyntax.FORM, PwmSettingCategory.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_SEARCH_FILTER(
            "forgottenUsername.searchFilter", PwmSettingSyntax.STRING, PwmSettingCategory.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_USERNAME_ATTRIBUTE(
            "forgottenUsername.usernameAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.FORGOTTEN_USERNAME),
    FORGOTTEN_USERNAME_SEND_USERNAME_METHOD(
            "forgottenUsername.sendUsername.sendMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.FORGOTTEN_USERNAME),


    // new user settings
    NEWUSER_ENABLE(
            "newUser.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.NEWUSER_SETTINGS),
    NEWUSER_PROFILE_LIST(
            "newUser.profile.list", PwmSettingSyntax.PROFILE, PwmSettingCategory.NEWUSER_SETTINGS),
    NEWUSER_CONTEXT(
            "newUser.createContext", PwmSettingSyntax.STRING, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_AGREEMENT_MESSAGE(
            "display.newuser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_FORM(
            "newUser.form", PwmSettingSyntax.FORM, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_WRITE_ATTRIBUTES(
            "newUser.writeAttributes", PwmSettingSyntax.ACTION, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_DELETE_ON_FAIL(
            "newUser.deleteOnFail", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_USERNAME_DEFINITION(
            "newUser.username.definition", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_EMAIL_VERIFICATION(
            "newUser.email.verification", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_SMS_VERIFICATION(
            "newUser.sms.verification", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_PASSWORD_POLICY_USER(
            "newUser.passwordPolicy.user", PwmSettingSyntax.STRING, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_MINIMUM_WAIT_TIME(
            "newUser.minimumWaitTime", PwmSettingSyntax.DURATION, PwmSettingCategory.NEWUSER_PROFILE),
    NEWUSER_PROFILE_DISPLAY_NAME(
            "newUser.profile.displayName", PwmSettingSyntax.LOCALIZED_STRING, PwmSettingCategory.NEWUSER_PROFILE),


    // guest settings
    GUEST_ENABLE(
            "guest.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.GUEST),
    GUEST_CONTEXT(
            "guest.createContext", PwmSettingSyntax.STRING, PwmSettingCategory.GUEST),
    GUEST_ADMIN_GROUP(
            "guest.adminGroup", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.GUEST),
    GUEST_FORM(
            "guest.form", PwmSettingSyntax.FORM, PwmSettingCategory.GUEST),
    GUEST_UPDATE_FORM(
            "guest.update.form", PwmSettingSyntax.FORM, PwmSettingCategory.GUEST),
    GUEST_WRITE_ATTRIBUTES(
            "guest.writeAttributes", PwmSettingSyntax.ACTION, PwmSettingCategory.GUEST),
    GUEST_ADMIN_ATTRIBUTE(
            "guest.adminAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.GUEST),
    GUEST_EDIT_ORIG_ADMIN_ONLY(
            "guest.editOriginalAdminOnly", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.GUEST),
    GUEST_MAX_VALID_DAYS(
            "guest.maxValidDays", PwmSettingSyntax.NUMERIC, PwmSettingCategory.GUEST),
    GUEST_EXPIRATION_ATTRIBUTE(
            "guest.expirationAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.GUEST),

    // activation settings
    ACTIVATE_USER_ENABLE(
            "activateUser.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACTIVATION),
    ACTIVATE_USER_UNLOCK(
            "activateUser.allowUnlock", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACTIVATION),
    ACTIVATE_TOKEN_SEND_METHOD(
            "activateUser.token.sendMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.ACTIVATION),
    ACTIVATE_AGREEMENT_MESSAGE(
            "display.activateUser.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.ACTIVATION),
    ACTIVATE_USER_FORM(
            "activateUser.form", PwmSettingSyntax.FORM, PwmSettingCategory.ACTIVATION),
    ACTIVATE_USER_SEARCH_FILTER(
            "activateUser.searchFilter", PwmSettingSyntax.STRING, PwmSettingCategory.ACTIVATION),
    ACTIVATE_USER_QUERY_MATCH(
            "activateUser.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.ACTIVATION),
    ACTIVATE_USER_PRE_WRITE_ATTRIBUTES(
            "activateUser.writePreAttributes", PwmSettingSyntax.ACTION, PwmSettingCategory.ACTIVATION),
    ACTIVATE_USER_POST_WRITE_ATTRIBUTES(
            "activateUser.writePostAttributes", PwmSettingSyntax.ACTION, PwmSettingCategory.ACTIVATION),

    // update profile
    UPDATE_PROFILE_ENABLE(
            "updateAttributes.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UPDATE),
    UPDATE_PROFILE_FORCE_SETUP(
            "updateAttributes.forceSetup", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UPDATE),
    UPDATE_PROFILE_AGREEMENT_MESSAGE(
            "display.updateAttributes.agreement", PwmSettingSyntax.LOCALIZED_TEXT_AREA, PwmSettingCategory.UPDATE),
    UPDATE_PROFILE_QUERY_MATCH(
            "updateAttributes.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.UPDATE),
    UPDATE_PROFILE_WRITE_ATTRIBUTES(
            "updateAttributes.writeAttributes", PwmSettingSyntax.ACTION, PwmSettingCategory.UPDATE),
    UPDATE_PROFILE_FORM(
            "updateAttributes.form", PwmSettingSyntax.FORM, PwmSettingCategory.UPDATE),
    UPDATE_PROFILE_CHECK_QUERY_MATCH(
            "updateAttributes.check.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.UPDATE),
    UPDATE_PROFILE_SHOW_CONFIRMATION(
            "updateAttributes.showConfirmation", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.UPDATE),

    // shortcut settings
    SHORTCUT_ENABLE(
            "shortcut.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.SHORTCUT),
    SHORTCUT_ITEMS(
            "shortcut.items", PwmSettingSyntax.LOCALIZED_STRING_ARRAY, PwmSettingCategory.SHORTCUT),
    SHORTCUT_HEADER_NAMES(
            "shortcut.httpHeaders", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.SHORTCUT),
    SHORTCUT_NEW_WINDOW(
            "shortcut.newWindow", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.SHORTCUT),

    // peoplesearch settings
    PEOPLE_SEARCH_ENABLE(
            "peopleSearch.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_QUERY_MATCH(
            "peopleSearch.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_SEARCH_ATTRIBUTES(
            "peopleSearch.searchAttributes", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_RESULT_FORM(
            "peopleSearch.result.form", PwmSettingSyntax.FORM, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_DETAIL_FORM(
            "peopleSearch.detail.form", PwmSettingSyntax.FORM, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_RESULT_LIMIT(
            "peopleSearch.result.limit", PwmSettingSyntax.NUMERIC, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_USE_PROXY(
            "peopleSearch.useProxy", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_DISPLAY_NAME(
            "peopleSearch.displayName.user", PwmSettingSyntax.STRING, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_PHOTO_ATTRIBUTE(
            "peopleSearch.photo.ldapAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_PHOTO_URL_OVERRIDE(
            "peopleSearch.photo.urlOverride", PwmSettingSyntax.STRING, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_PHOTO_STYLE_ATTR(
            "peopleSearch.photo.style", PwmSettingSyntax.STRING, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_MAX_CACHE_SECONDS(
            "peopleSearch.maxCacheSeconds", PwmSettingSyntax.DURATION, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_PHOTO_QUERY_FILTER(
            "peopleSearch.photo.queryFilter", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_SEARCH_FILTER(
            "peopleSearch.searchFilter", PwmSettingSyntax.STRING, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_SEARCH_BASE(
            "peopleSearch.searchBase", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_ENABLE_PUBLIC(
            "peopleSearch.enablePublic", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS(
            "peopleSearch.idleTimeout", PwmSettingSyntax.DURATION, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE(
            "peopleSearch.orgChart.parentAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE(
            "peopleSearch.orgChart.childAttribute", PwmSettingSyntax.STRING, PwmSettingCategory.PEOPLE_SEARCH),
    PEOPLE_SEARCH_ORGCHART_DISPLAY_VALUES(
            "peopleSearch.orgChart.displayValues", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.PEOPLE_SEARCH),



    // edirectory settings
    EDIRECTORY_ENABLE_NMAS(
            "ldap.edirectory.enableNmas", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.EDIR_SETTINGS, Template.NOVL),
    EDIRECTORY_STORE_NMAS_RESPONSES(
            "ldap.edirectory.storeNmasResponses", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.EDIR_SETTINGS, Template.NOVL),
    EDIRECTORY_USE_NMAS_RESPONSES(
            "ldap.edirectory.useNmasResponses", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.EDIR_SETTINGS, Template.NOVL),
    EDIRECTORY_READ_USER_PWD(
            "ldap.edirectory.readUserPwd", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.EDIR_SETTINGS, Template.NOVL),
    EDIRECTORY_PWD_MGT_WEBSERVICE_URL(
            "ldap.edirectory.ws.pwdMgtURL", PwmSettingSyntax.STRING, PwmSettingCategory.EDIR_SETTINGS, Template.NOVL),

    EDIRECTORY_READ_CHALLENGE_SET(
            "ldap.edirectory.readChallengeSets", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.EDIR_CR_SETTINGS, Template.NOVL),
    EDIRECTORY_CR_MIN_RANDOM_DURING_SETUP(
            "ldap.edirectory.cr.minRandomDuringSetup", PwmSettingSyntax.NUMERIC, PwmSettingCategory.EDIR_CR_SETTINGS, Template.NOVL),
    EDIRECTORY_CR_APPLY_WORDLIST(
            "ldap.edirectory.cr.applyWordlist", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.EDIR_CR_SETTINGS, Template.NOVL),
    EDIRECTORY_CR_MAX_QUESTION_CHARS_IN__ANSWER(
            "ldap.edirectory.cr.maxQuestionCharsInAnswer", PwmSettingSyntax.NUMERIC, PwmSettingCategory.EDIR_CR_SETTINGS, Template.NOVL),


    // active directory
    AD_USE_PROXY_FOR_FORGOTTEN(
            "ldap.ad.proxyForgotten", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACTIVE_DIRECTORY, Template.AD),
    AD_ALLOW_AUTH_REQUIRE_NEW_PWD(
            "ldap.ad.allowAuth.requireNewPassword", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACTIVE_DIRECTORY, Template.AD),
    AD_ALLOW_AUTH_EXPIRED(
            "ldap.ad.allowAuth.expired", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACTIVE_DIRECTORY, Template.AD),
    AD_ENFORCE_PW_HISTORY_ON_SET(
            "ldap.ad.enforcePwHistoryOnSet", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ACTIVE_DIRECTORY, Template.AD),

    // active directory
    ORACLE_DS_ENABLE_MANIP_ALLOWCHANGETIME(
            "ldap.oracleDS.enable.manipAllowChangeTime", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ORACLE_DS, Template.ORACLE_DS),
    ORACLE_DS_ALLOW_AUTH_REQUIRE_NEW_PWD(
            "ldap.oracleDS.allowAuth.requireNewPassword", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.ORACLE_DS, Template.ORACLE_DS),


    // helpdesk profile
    HELPDESK_PROFILE_LIST(
            "helpdesk.profile.list", PwmSettingSyntax.PROFILE, PwmSettingCategory.GENERAL),
    HELPDESK_PROFILE_QUERY_MATCH(
            "helpdesk.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_SEARCH_FORM(
            "helpdesk.result.form", PwmSettingSyntax.FORM, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_SEARCH_FILTERS(
            "helpdesk.search.filters", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_SEARCH_FILTER(
            "helpdesk.filter", PwmSettingSyntax.STRING, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_SEARCH_BASE(
            "helpdesk.searchBase", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_DETAIL_FORM(
            "helpdesk.detail.form", PwmSettingSyntax.FORM, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_VIEW_STATUS_VALUES(
            "helpdesk.viewStatusValues", PwmSettingSyntax.OPTIONLIST, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_RESULT_LIMIT(
            "helpdesk.result.limit", PwmSettingSyntax.NUMERIC, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_SET_PASSWORD_MODE(
            "helpdesk.setPassword.mode", PwmSettingSyntax.SELECT, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_SEND_PASSWORD(
            "helpdesk.sendPassword", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_POST_SET_PASSWORD_WRITE_ATTRIBUTES(
            "helpdesk.setPassword.writeAttributes", PwmSettingSyntax.ACTION, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_ACTIONS(
            "helpdesk.actions", PwmSettingSyntax.ACTION, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_IDLE_TIMEOUT_SECONDS(
            "helpdesk.idleTimeout", PwmSettingSyntax.DURATION, PwmSettingCategory.HELPDESK_PROFILE),


    HELPDESK_ENABLE_UNLOCK(
            "helpdesk.enableUnlock", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_ENFORCE_PASSWORD_POLICY(
            "helpdesk.enforcePasswordPolicy", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_CLEAR_RESPONSES(
            "helpdesk.clearResponses", PwmSettingSyntax.SELECT, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_FORCE_PW_EXPIRATION(
            "helpdesk.forcePwExpiration", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_CLEAR_RESPONSES_BUTTON(
            "helpdesk.clearResponses.button", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_CLEAR_OTP_BUTTON(
            "helpdesk.clearOtp.button", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_DELETE_USER_BUTTON(
            "helpdesk.deleteUser.button", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_USE_PROXY(
            "helpdesk.useProxy", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_DETAIL_DISPLAY_NAME(
            "helpdesk.displayName", PwmSettingSyntax.STRING, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_TOKEN_SEND_METHOD(
            "helpdesk.token.sendMethod", PwmSettingSyntax.SELECT, PwmSettingCategory.HELPDESK_PROFILE),
    HELPDESK_ENABLE_OTP_VERIFY(
            "helpdesk.otp.verify", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.HELPDESK_PROFILE),



    // Database
    DATABASE_CLASS(
            "db.classname", PwmSettingSyntax.STRING, PwmSettingCategory.DATABASE),
    DATABASE_URL(
            "db.url", PwmSettingSyntax.STRING, PwmSettingCategory.DATABASE),
    DATABASE_USERNAME(
            "db.username", PwmSettingSyntax.STRING, PwmSettingCategory.DATABASE),
    DATABASE_PASSWORD(
            "db.password", PwmSettingSyntax.PASSWORD, PwmSettingCategory.DATABASE),
    DATABASE_JDBC_DRIVER(
            "db.jdbc.driver", PwmSettingSyntax.FILE, PwmSettingCategory.DATABASE),
    DATABASE_COLUMN_TYPE_KEY(
            "db.columnType.key", PwmSettingSyntax.STRING, PwmSettingCategory.DATABASE),
    DATABASE_COLUMN_TYPE_VALUE(
            "db.columnType.value", PwmSettingSyntax.STRING, PwmSettingCategory.DATABASE),
    DATABASE_DEBUG_TRACE(
            "db.debugTrace.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.DATABASE),

    // reporting
    REPORTING_ENABLE(
            "reporting.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.REPORTING),
    REPORTING_SEARCH_FILTER(
            "reporting.ldap.searchFilter", PwmSettingSyntax.STRING, PwmSettingCategory.REPORTING),
    REPORTING_MAX_CACHE_AGE(
            "reporting.maxCacheAge", PwmSettingSyntax.DURATION, PwmSettingCategory.REPORTING),
    REPORTING_MIN_CACHE_AGE(
            "reporting.minCacheAge", PwmSettingSyntax.DURATION, PwmSettingCategory.REPORTING),
    REPORTING_REST_TIME_MS(
            "reporting.reportRestTimeMS", PwmSettingSyntax.NUMERIC, PwmSettingCategory.REPORTING),
    REPORTING_MAX_QUERY_SIZE(
            "reporting.ldap.maxQuerySize", PwmSettingSyntax.NUMERIC, PwmSettingCategory.REPORTING),
    REPORTING_JOB_TIME_OFFSET(
            "reporting.job.timeOffset", PwmSettingSyntax.DURATION, PwmSettingCategory.REPORTING),
    REPORTING_SUMMARY_DAY_VALUES(
            "reporting.summary.dayValues", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.REPORTING),

    // OAuth
    OAUTH_ID_LOGIN_URL(
            "oauth.idserver.loginUrl", PwmSettingSyntax.STRING, PwmSettingCategory.OAUTH),
    OAUTH_ID_CODERESOLVE_URL(
            "oauth.idserver.codeResolveUrl", PwmSettingSyntax.STRING, PwmSettingCategory.OAUTH),
    OAUTH_ID_ATTRIBUTES_URL(
            "oauth.idserver.attributesUrl", PwmSettingSyntax.STRING, PwmSettingCategory.OAUTH),
    OAUTH_ID_CLIENTNAME(
            "oauth.idserver.clientName", PwmSettingSyntax.STRING, PwmSettingCategory.OAUTH),
    OAUTH_ID_SECRET(
            "oauth.idserver.secret", PwmSettingSyntax.PASSWORD, PwmSettingCategory.OAUTH),
    OAUTH_ID_DN_ATTRIBUTE_NAME(
            "oauth.idserver.dnAttributeName", PwmSettingSyntax.STRING, PwmSettingCategory.OAUTH),

    // CAS SSO
    CAS_CLEAR_PASS_URL(
            "cas.clearPassUrl", PwmSettingSyntax.STRING, PwmSettingCategory.CAS_SSO),

    // http sso
    SSO_AUTH_HEADER_NAME(
            "security.sso.authHeaderName", PwmSettingSyntax.STRING, PwmSettingCategory.HTTP_SSO),

    // administration
    QUERY_MATCH_PWM_ADMIN(
            "pwmAdmin.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.ADMINISTRATION),


    ENABLE_EXTERNAL_WEBSERVICES(
            "external.webservices.enable", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.REST_SERVER),
    ENABLE_WEBSERVICES_READANSWERS(
            "webservices.enableReadAnswers", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.REST_SERVER),
    PUBLIC_HEALTH_STATS_WEBSERVICES(
            "webservices.healthStats.makePublic", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.REST_SERVER),
    WEBSERVICES_QUERY_MATCH(
            "webservices.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.REST_SERVER),
    WEBSERVICES_THIRDPARTY_QUERY_MATCH(
            "webservices.thirdParty.queryMatch", PwmSettingSyntax.USER_PERMISSION, PwmSettingCategory.REST_SERVER),
    WEBSERVICES_EXTERNAL_SECRET(
            "webservices.external.secret", PwmSettingSyntax.PASSWORD, PwmSettingCategory.REST_SERVER),


    EXTERNAL_MACROS_DEST_TOKEN_URLS(
            "external.destToken.urls", PwmSettingSyntax.STRING, PwmSettingCategory.REST_CLIENT),
    EXTERNAL_PWCHECK_REST_URLS(
            "external.pwcheck.urls", PwmSettingSyntax.STRING, PwmSettingCategory.REST_CLIENT),
    EXTERNAL_MACROS_REST_URLS(
            "external.macros.urls", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.REST_CLIENT),
    CACHED_USER_ATTRIBUTES(
            "webservice.userAttributes", PwmSettingSyntax.STRING_ARRAY, PwmSettingCategory.REST_CLIENT),



    // deprecated.
    PASSWORD_POLICY_AD_COMPLEXITY(
            "password.policy.ADComplexity", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.PASSWORD_POLICY),
    CHALLENGE_REQUIRE_RESPONSES(
            "challenge.requireResponses", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.RECOVERY_SETTINGS),
    FORGOTTEN_PASSWORD_REQUIRE_OTP(
            "recovery.require.otp", PwmSettingSyntax.BOOLEAN, PwmSettingCategory.RECOVERY_SETTINGS),



    ;

// ------------------------------ STATICS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmSetting.class);


// ------------------------------ FIELDS ------------------------------

    private static class Static {
        private static final Pattern DEFAULT_REGEX = Pattern.compile(".*", Pattern.DOTALL);
    }

    private final String key;
    private final PwmSettingSyntax syntax;
    private final PwmSettingCategory category;
    private final Set<Template> templates;

    private final Map<Template, StoredValue>    CACHE_DEFAULT_VALUES = new HashMap<>();
    private final Map<Locale,String>            CACHE_LABELS = new HashMap<>();


// --------------------------- CONSTRUCTORS ---------------------------

    PwmSetting(
            final String key,
            final PwmSettingSyntax syntax,
            final PwmSettingCategory category,
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

    public PwmSettingCategory getCategory() {
        return category;
    }

    public PwmSettingSyntax getSyntax() {
        return syntax;
    }

    public Set<Template> getTemplates() {
        return templates;
    }


    // -------------------------- OTHER METHODS --------------------------

    public StoredValue getDefaultValue(final Template template)
            throws PwmOperationalException, PwmUnrecoverableException {
        if (!CACHE_DEFAULT_VALUES.containsKey(template)) {
            if (this.getSyntax() == PwmSettingSyntax.PASSWORD) {
                CACHE_DEFAULT_VALUES.put(template, new PasswordValue(null));
            } else {
                final Element settingElement = PwmSettingXml.readSettingXml(this);
                final XPathFactory xpfac = XPathFactory.instance();
                Element defaultElement = null;
                if (template != null) {
                    XPathExpression xp = xpfac.compile("default[@template=\"" + template.toString() + "\"]");
                    defaultElement = (Element) xp.evaluateFirst(settingElement);
                }
                if (defaultElement == null) {
                    XPathExpression xp = xpfac.compile("default[not(@template)]");
                    defaultElement = (Element) xp.evaluateFirst(settingElement);
                }
                if (defaultElement == null) {
                    throw new IllegalStateException("no default value for setting " + this.getKey());
                }
                CACHE_DEFAULT_VALUES.put(template, ValueFactory.fromXmlValues(this, defaultElement, this.getKey()));
            }
        }
        return CACHE_DEFAULT_VALUES.get(template);
    }

    public Map<Template, String> getDefaultValueDebugStrings(final boolean prettyPrint, final Locale locale)
            throws PwmOperationalException, PwmUnrecoverableException {
        final Map<Template, String> returnObj = new LinkedHashMap<>();
        final String defaultDebugStr = this.getDefaultValue(Template.DEFAULT).toDebugString(prettyPrint, locale);
        returnObj.put(Template.DEFAULT, defaultDebugStr);
        for (final Template template : Template.values()) {
            if (template != Template.DEFAULT) {
                final String debugStr = this.getDefaultValue(template).toDebugString(prettyPrint, locale);
                if (!defaultDebugStr.equals(debugStr)) {
                    returnObj.put(template, debugStr);
                }
            }
        }
        return returnObj;
    }

    public Map<String, String> getOptions() {
        final Element settingElement = PwmSettingXml.readSettingXml(this);
        final Element optionsElement = settingElement.getChild("options");
        final Map<String, String> returnList = new LinkedHashMap<>();
        if (optionsElement != null) {
            final List<Element> optionElements = optionsElement.getChildren("option");
            if (optionElements != null) {
                for (Element optionElement : optionElements) {
                    if (optionElement.getAttribute("value") == null) {
                        throw new IllegalStateException("option element is missing 'value' attribute for key " + this.getKey());
                    }
                    returnList.put(optionElement.getAttribute("value").getValue(), optionElement.getValue());
                }
            }
        }

        return Collections.unmodifiableMap(returnList);
    }

    public String getLabel(final Locale locale) {
        if (!CACHE_LABELS.containsKey(locale)) {
            final Element settingElement = PwmSettingXml.readSettingXml(this);
            if (settingElement == null) {
                throw new IllegalStateException("missing Setting value for setting " + this.getKey());
            }
            final Element labelElement = settingElement.getChild("label");
            String labelText = labelElement.getText();
            CACHE_LABELS.put(locale,labelText);
        }
        return CACHE_LABELS.get(locale);
    }

    public String getDescription(final Locale locale) {
        final Element settingElement = PwmSettingXml.readSettingXml(this);
        final Element descriptionElement = settingElement.getChild("description");
        return descriptionElement.getText();
    }

    public String getPlaceholder(final Locale locale) {
        Element placeHolderElement = PwmSettingXml.readSettingXml(this);
        Element placeholder = placeHolderElement.getChild("placeholder");
        return placeholder != null ? placeholder.getText() : "";
    }

    public boolean isRequired() {
        final Element settingElement = PwmSettingXml.readSettingXml(this);
        final Attribute requiredAttribute = settingElement.getAttribute("required");
        return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
    }

    public boolean isHidden() {
        final Element settingElement = PwmSettingXml.readSettingXml(this);
        final Attribute requiredAttribute = settingElement.getAttribute("hidden");
        return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
    }

    public int getLevel() {
        final Element settingElement = PwmSettingXml.readSettingXml(this);
        final Attribute levelAttribute = settingElement.getAttribute("level");
        return levelAttribute != null ? Integer.parseInt(levelAttribute.getValue()) : 0;
    }

    public Pattern getRegExPattern() {
        Element settingNode = PwmSettingXml.readSettingXml(this);
        Element regexNode = settingNode.getChild("regex");
        if (regexNode == null) {
            return Static.DEFAULT_REGEX;
        }
        try {
            return Pattern.compile(regexNode.getText());
        } catch (PatternSyntaxException e) {
            final String errorMsg = "error compiling regex constraints for setting " + this.toString() + ", error: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new IllegalStateException(errorMsg, e);
        }
    }

    public enum Template {
        NOVL,
        AD,
        ORACLE_DS,
        DEFAULT,;

        public String getLabel(final Locale locale) {
            Element categoryElement = PwmSettingXml.readTemplateXml(this);
            Element labelElement = categoryElement.getChild("label");
            return labelElement.getText();
        }

    }

    public static Map<PwmSettingCategory, List<PwmSetting>> valuesByFilter(
            final Template template,
            final PwmSettingCategory parent,
            final int level) {
        final List<PwmSetting> settingList = new ArrayList<>(Arrays.asList(PwmSetting.values()));

        for (Iterator<PwmSetting> iter = settingList.iterator(); iter.hasNext(); ) {
            final PwmSetting loopSetting = iter.next();
            if (parent != null && loopSetting.getCategory().getParent() != parent) {
                iter.remove();
            } else if (level >= 0 && loopSetting.getLevel() > level) {
                iter.remove();
            } else if (template != null && !loopSetting.getTemplates().contains(template)) {
                iter.remove();
            } else if (loopSetting.isHidden() || loopSetting.getCategory().isHidden()) {
                iter.remove();
            }
        }

        final Map<PwmSettingCategory, List<PwmSetting>> returnMap = new TreeMap<>();
        for (final PwmSetting loopSetting : settingList) {
            if (!returnMap.containsKey(loopSetting.getCategory())) {
                returnMap.put(loopSetting.getCategory(), new ArrayList<PwmSetting>());
            }
            returnMap.get(loopSetting.getCategory()).add(loopSetting);
        }
        for (final PwmSettingCategory category : returnMap.keySet()) {
            returnMap.put(category, Collections.unmodifiableList(returnMap.get(category)));
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

    public String toMenuLocationDebug(
            final String profileID,
            final Locale locale
    ) {
        final String SEPARATOR = LocaleHelper.getLocalizedMessage(locale, Config.Display_SettingNavigationSeparator, null);
        final StringBuilder sb = new StringBuilder();
        sb.append(this.getCategory().toMenuLocationDebug(profileID, locale));

        sb.append(SEPARATOR);
        sb.append(this.getLabel(locale));

        return sb.toString();
    }

    public enum SettingStat {
        Total,
        hasProfile,
        syntaxCounts,
    }

    public static Map<SettingStat, Object> getStats() {
        final Map<SettingStat,Object> returnObj = new LinkedHashMap<>();
        {
            returnObj.put(SettingStat.Total,PwmSetting.values().length);
        }
        {
            int hasProfile = 0;
            for (final PwmSetting pwmSetting : values()) {
                if (pwmSetting.getCategory().hasProfiles()) {
                    hasProfile++;
                }
            }
            returnObj.put(SettingStat.hasProfile,hasProfile);
        }
        {
            final Map<PwmSettingSyntax,Integer> syntaxCounts = new LinkedHashMap<>();
            for (final PwmSettingSyntax syntax : PwmSettingSyntax.values()) {
                syntaxCounts.put(syntax,0);
            }
            for (final PwmSetting pwmSetting : values()) {
                syntaxCounts.put(pwmSetting.getSyntax(), syntaxCounts.get(pwmSetting.getSyntax()) + 1);
            }
            returnObj.put(SettingStat.syntaxCounts, syntaxCounts);
        }
        return returnObj;
    }
}

