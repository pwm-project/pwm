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

package password.pwm;

import java.util.ResourceBundle;

public enum AppProperty {

    AUDIT_EVENTS_EMAILFROM                          ("audit.events.emailFrom"),
    AUDIT_VAULT_MAX_RECORDS                         ("audit.vault.maxRecords"),
    BACKUP_LOCATION                                 ("backup.path"),
    BACKUP_CONFIG_COUNT                             ("backup.config.count"),
    BACKUP_LOCALDB_COUNT                            ("backup.localdb.count"),
    CACHE_ENABLE                                    ("cache.enable"),
    CACHE_MEMORY_MAX_ITEMS                          ("cache.memory.maxItems"),
    CACHE_PWRULECHECK_LIFETIME_MS                   ("cache.pwRuleCheckLifetimeMS"),
    CLIENT_ACTIVITY_MAX_EPS_RATE                    ("client.ajax.activityMaxEpsRate"),
    CLIENT_AJAX_PW_WAIT_CHECK_SECONDS               ("client.ajax.changePasswordWaitCheckSeconds"),
    CLIENT_AJAX_TYPING_TIMEOUT                      ("client.ajax.typingTimeout"),
    CLIENT_AJAX_TYPING_WAIT                         ("client.ajax.typingWait"),
    CLIENT_FORM_NONCE_ENABLE                        ("client.formNonce.enable"),
    CLIENT_FORM_NONCE_LENGTH                        ("client.formNonce.length"),
    CLIENT_WARNING_HEADER_SHOW                      ("client.warningHeader.show"),
    CLIENT_PW_SHOW_REVERT_TIMEOUT                   ("client.pwShowRevertTimeout"),
    CLIENT_JS_ENABLE_HTML5DIALOG                    ("client.js.enableHtml5Dialog"),
    CLIENT_JSP_SHOW_ICONS                           ("client.jsp.showIcons"),
    CONFIG_MAX_JDBC_JAR_SIZE                        ("config.maxJdbcJarSize"),
    CONFIG_RELOAD_ON_CHANGE                         ("config.reloadOnChange"),
    CONFIG_MAX_PERSISTENT_LOGIN_SECONDS             ("config.maxPersistentLoginSeconds"),
    CONFIG_FILE_SCAN_FREQUENCY                      ("config.fileScanFrequencyMS"),
    CONFIG_NEWUSER_PASSWORD_POLICY_CACHE_MS         ("config.newuser.passwordPolicyCacheMS"),
    CONFIG_EDITOR_QUERY_FILTER_TEST_LIMIT           ("configEditor.queryFilter.testLimit"),
    CONFIG_EDITOR_IDLE_TIMEOUT                      ("configEditor.idleTimeoutSeconds"),
    CONFIG_GUIDE_IDLE_TIMEOUT                       ("configGuide.idleTimeoutSeconds"),
    FORM_EMAIL_REGEX                                ("form.email.regexTest"),
    HTTP_RESOURCES_MAX_CACHE_ITEMS                  ("http.resources.maxCacheItems"),
    HTTP_RESOURCES_MAX_CACHE_BYTES                  ("http.resources.maxCacheBytes"),
    HTTP_RESOURCES_EXPIRATION_SECONDS               ("http.resources.expirationSeconds"),
    HTTP_RESOURCES_ENABLE_GZIP                      ("http.resources.gzip.enable"),
    HTTP_RESOURCES_ENABLE_PATH_NONCE                ("http.resources.pathNonceEnable"),
    HTTP_RESOURCES_NONCE_PATH_PREFIX                ("http.resources.pathNoncePrefix"),
    HTTP_COOKIE_THEME_NAME                          ("http.cookie.theme.name"),
    HTTP_COOKIE_THEME_AGE                           ("http.cookie.theme.age"),
    HTTP_COOKIE_LOCALE_NAME                         ("http.cookie.locale.name"),
    HTTP_COOKIE_AUTHRECORD_NAME                     ("http.cookie.authRecord.name"),
    HTTP_COOKIE_AUTHRECORD_AGE                      ("http.cookie.authRecord.age"),
    HTTP_COOKIE_MAX_READ_LENGTH                     ("http.cookie.maxReadLength"),
    HTTP_BASIC_AUTH_CHARSET                         ("http.basicAuth.charset"),
    HTTP_BODY_MAXREAD_LENGTH                        ("http.body.maxReadLength"),
    HTTP_ENABLE_GZIP                                ("http.gzip.enable"),
    HTTP_ERRORS_ALLOW_HTML                          ("http.errors.allowHtml"),
    HTTP_HEADER_SEND_XAMB                           ("http.header.sendXAmb"),
    HTTP_HEADER_SEND_XINSTANCE                      ("http.header.sendXInstance"),
    HTTP_HEADER_SEND_XNOISE                         ("http.header.sendXNoise"),
    HTTP_HEADER_SEND_XSESSIONID                     ("http.header.sendXSessionID"),
    HTTP_HEADER_SEND_XVERSION                       ("http.header.sendXVersion"),
    HTTP_HEADER_SEND_XCONTENTTYPEOPTIONS            ("http.header.sendXContentTypeOptions"),
    HTTP_HEADER_SEND_XXSSPROTECTION                 ("http.header.sendXXSSProtection"),
    HTTP_PARAM_NAME_FORWARD_URL                     ("http.parameter.forward"),
    HTTP_PARAM_NAME_LOGOUT_URL                      ("http.parameter.logout"),
    HTTP_PARAM_NAME_THEME                           ("http.parameter.theme"),
    HTTP_PARAM_NAME_LOCALE                          ("http.parameter.locale"),
    HTTP_PARAM_NAME_PASSWORD_EXPIRED                ("http.parameter.passwordExpired"),
    HTTP_PARAM_MAX_READ_LENGTH                      ("http.parameter.maxReadLength"),
    HTTP_PARAM_OAUTH_ACCESS_TOKEN                   ("http.parameter.oauth.accessToken"),
    HTTP_PARAM_OAUTH_ATTRIBUTES                     ("http.parameter.oauth.attributes"),
    HTTP_PARAM_OAUTH_CLIENT_ID                      ("http.parameter.oauth.clientID"),
    HTTP_PARAM_OAUTH_CODE                           ("http.parameter.oauth.code"),
    HTTP_PARAM_OAUTH_EXPIRES                        ("http.parameter.oauth.expires"),
    HTTP_PARAM_OAUTH_RESPONSE_TYPE                  ("http.parameter.oauth.responseType"),
    HTTP_PARAM_OAUTH_REDIRECT_URI                   ("http.parameter.oauth.redirectUri"),
    HTTP_PARAM_OAUTH_REFRESH_TOKEN                  ("http.parameter.oauth.refreshToken"),
    HTTP_PARAM_OAUTH_STATE                          ("http.parameter.oauth.state"),
    HTTP_PARAM_OAUTH_GRANT_TYPE                     ("http.parameter.oauth.grantType"),
    HTTP_SESSION_RECYCLE_AT_AUTH                    ("http.session.recycleAtAuth"),
    HTTP_SESSION_VALIDATION_KEY_LENGTH              ("http.session.validationKeyLength"),
    LOCALDB_COMPRESSION_ENABLED                     ("localdb.compression.enabled"),
    LOCALDB_DECOMPRESSION_ENABLED                   ("localdb.decompression.enabled"),
    LOCALDB_COMPRESSION_MINSIZE                     ("localdb.compression.minSize"),
    LOCALDB_IMPLEMENTATION                          ("localdb.implementation"),
    LOCALDB_INIT_STRING                             ("localdb.initParameters"),
    MACRO_RANDOM_CHAR_MAX_LENGTH                    ("macro.randomChar.maxLength"),
    MACRO_LDAP_ATTR_CHAR_MAX_LENGTH                 ("macro.ldapAttr.maxLength"),
    
    /** Time intruder records exist in the intruder table before being deleted. */
    INTRUDER_RETENTION_TIME_MS                      ("intruder.retentionTimeMS"),
    
    /** How often to cleanup the intruder table. */
    INTRUDER_CLEANUP_FREQUENCY_MS                   ("intruder.cleanupFrequencyMS"),
    INTRUDER_MIN_DELAY_PENALTY_MS                   ("intruder.minimumDelayPenaltyMS"),
    INTRUDER_MAX_DELAY_PENALTY_MS                   ("intruder.maximumDelayPenaltyMS"),
    INTRUDER_DELAY_PER_COUNT_MS                     ("intruder.delayPerCountMS"),
    INTRUDER_DELAY_MAX_JITTER_MS                    ("intruder.delayMaxJitterMS"),
    HEALTH_MIN_CHECK_INTERVAL_SECONDS               ("health.minimumCheckIntervalSeconds"),
    HEALTH_CERTIFICATE_WARN_SECONDS                 ("health.certificate.warnSeconds"),
    HEALTH_LDAP_CAUTION_DURATION_MS                 ("health.ldap.cautionDurationMS"),
    HEALTH_JAVA_MAX_THREADS                         ("health.java.maxThreads"),
    HEALTH_JAVA_MIN_HEAP_BYTES                      ("health.java.minHeapBytes"),
    HELPDESK_TOKEN_VALUE                            ("helpdesk.token.value"),
    LDAP_CHAI_SETTINGS                              ("ldap.chaiSettings"),
    LDAP_CONNECTION_TIMEOUT                         ("ldap.connection.timeoutMS"),
    LDAP_PROFILE_RETRY_DELAY                        ("ldap.profile.retryDelayMS"),
    LDAP_PROMISCUOUS_ENABLE                         ("ldap.promiscuousEnable"),
    LDAP_SEARCH_TIMEOUT                             ("ldap.search.timeoutMS"),
    LDAP_PASSWORD_REPLICA_CHECK_INIT_DELAY_MS       ("ldap.password.replicaCheck.initialDelayMS"),
    LDAP_PASSWORD_REPLICA_CHECK_CYCLE_DELAY_MS      ("ldap.password.replicaCheck.cycleDelayMS"),
    LDAP_GUID_PATTERN                               ("ldap.guid.pattern"),
    LOGGING_PATTERN                                 ("logging.pattern"),
    LOGGING_FILE_MAX_SIZE                           ("logging.file.maxSize"),
    LOGGING_FILE_MAX_ROLLOVER                       ("logging.file.maxRollover"),
    LOGGING_FILE_PATH                               ("logging.file.path"),
    LOGGING_DEV_OUTPUT                              ("logging.devOutput.enable"),
    NEWUSER_LDAP_USE_TEMP_PW                        ("newUser.ldap.useTempPassword"),
    NMAS_THREADS_MAX_COUNT                          ("nmas.threads.maxCount"),
    NMAS_THREADS_MIN_SECONDS                        ("nmas.threads.minSeconds"),
    NMAS_THREADS_MAX_SECONDS                        ("nmas.threads.maxSeconds"),
    NMAS_THREADS_WATCHDOG_FREQUENCY                 ("nmas.threads.watchdogFrequencyMs"),
    OAUTH_ID_REQUEST_TYPE                           ("oauth.id.requestType"),
    OAUTH_ID_ACCESS_GRANT_TYPE                      ("oauth.id.accessGrantType"),
    OAUTH_ID_REFRESH_GRANT_TYPE                     ("oauth.id.refreshGrantType"),
    OAUTH_ENABLE_TOKEN_REFRESH                      ("oauth.enableTokenRefresh"),

    /* Allows one older TOTP token - compensate for clock out of sync */
    TOTP_PAST_INTERVALS                             ("otp.totp.pastIntervals"),
    
    /* Allows one newer TOTP token - compensate for clock out of sync */
    TOTP_FUTURE_INTERVALS                           ("otp.totp.futureIntervals"),
    
    TOTP_INTERVAL                                   ("otp.totp.intervalSeconds"),
    OTP_TOKEN_LENGTH                                ("otp.token.length"),
    OTP_RECOVERY_TOKEN_MACRO                        ("otp.recovery.macro"),
    OTP_RECOVERY_HASH_COUNT                         ("otp.recoveryHash.iterations"),
    OTP_RECOVERY_HASH_METHOD                        ("otp.recoveryHash.method"),
    PASSWORD_RANDOMGEN_MAX_ATTEMPTS                 ("password.randomGenerator.maxAttempts"),
    PASSWORD_RANDOMGEN_MAX_LENGTH                   ("password.randomGenerator.maxLength"),
    PASSWORD_RANDOMGEN_JITTER_COUNT                 ("password.randomGenerator.jitter.count"),
    PEOPLESEARCH_DISPLAYNAME_USEALLMACROS           ("peoplesearch.displayName.enableAllMacros"),
    PEOPLESEARCH_MAX_VALUE_COUNT                    ("peoplesearch.maxValueCount"),
    QUEUE_EMAIL_RETRY_TIMEOUT_MS                    ("queue.email.retryTimeoutMs"),
    QUEUE_EMAIL_MAX_AGE_MS                          ("queue.email.maxAgeMs"),
    QUEUE_EMAIL_MAX_COUNT                           ("queue.email.maxCount"),
    QUEUE_SMS_RETRY_TIMEOUT_MS                      ("queue.sms.retryTimeoutMs"),
    QUEUE_SMS_MAX_AGE_MS                            ("queue.sms.maxAgeMs"),
    QUEUE_SMS_MAX_COUNT                             ("queue.sms.maxCount"),
    QUEUE_SYSLOG_RETRY_TIMEOUT_MS                   ("queue.syslog.retryTimeoutMs"),
    QUEUE_SYSLOG_MAX_AGE_MS                         ("queue.syslog.maxAgeMs"),
    QUEUE_SYSLOG_MAX_COUNT                          ("queue.syslog.maxCount"),
    QUEUE_MAX_CLOSE_TIMEOUT_MS                      ("queue.maxCloseTimeoutMs"),
    RECAPTCHA_CLIENT_JS_URL("recaptcha.clientJsUrl"),
    RECAPTCHA_CLIENT_IFRAME_URL                     ("recaptcha.clientIframeUrl"),
    RECAPTCHA_VALIDATE_URL                          ("recaptcha.validateUrl"),
    REPORTING_LDAP_SEARCH_TIMEOUT                   ("reporting.ldap.searchTimeoutMs"),
    SECURITY_STRIP_INLINE_JAVASCRIPT                ("security.html.stripInlineJavascript"),
    SECURITY_HTTP_STRIP_HEADER_REGEX                ("security.http.stripHeaderRegex"),
    SECURITY_RESPONSES_HASH_ITERATIONS              ("security.responses.hashIterations"),
    SECURITY_INPUT_TRIM                             ("security.input.trim"),
    SECURITY_INPUT_PASSWORD_TRIM                    ("security.input.password.trim"),
    SECURITY_WS_REST_CLIENT_KEY_LENGTH              ("security.ws.rest.clientKeyLength"),
    SECURITY_SHAREDHISTORY_HASH_ITERATIONS          ("security.sharedHistory.hashIterations"),
    SECURITY_SHAREDHISTORY_HASH_NAME                ("security.sharedHistory.hashName"),
    SECURITY_SHAREDHISTORY_CASE_INSENSITIVE         ("security.sharedHistory.caseInsensitive"),
    TOKEN_REMOVAL_DELAY_MS                          ("token.removalDelayMS"),
    TOKEN_PURGE_BATCH_SIZE                          ("token.purgeBatchSize"),
    TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS                ("token.maxUniqueCreateAttempts"),
    
    /** Regular expression to be used for matching URLs to be shortened by the URL Shortening Service Class. */
    URL_SHORTNER_URL_REGEX                          ("urlshortener.url.regex"),
    WS_REST_CLIENT_PWRULE_HALTONERROR               ("ws.restClient.pwRule.haltOnError"),

    ;

    public static final String VALUE_SEPARATOR = ";;;";
    private static final String DESCRIPTION_SUFFIX = "_description";

    private final String key;
    private String defaultValue;

    private AppProperty(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static AppProperty forKey(final String key) {
        for (final AppProperty appProperty : AppProperty.values()) {
            if (appProperty.getKey().equals(key)) {
                return appProperty;
            }
        }
        return null;
    }

    public String getDefaultValue() {
        if (defaultValue == null) {
            defaultValue = readAppPropertiesBundle(this.getKey());
        }
        return defaultValue;
    }

    public String getDescription() {
        return readAppPropertiesBundle(this.getKey() + DESCRIPTION_SUFFIX);
    }

    private static String readAppPropertiesBundle(final String key) {
        return  ResourceBundle.getBundle(AppProperty.class.getName()).getString(key);
    }
}
