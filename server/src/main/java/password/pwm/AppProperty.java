/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

/**
 * Definition of available PWM application properties.  All {@link AppProperty} entries have a default value that is backed
 * by an associated {@code AppProperty.properties} file.  Properties can be overridden by the application administrator in
 * the configuration using the setting {@link password.pwm.config.PwmSetting#APP_PROPERTY_OVERRIDES}.
 */
public enum AppProperty {

    APPLICATION_FILELOCK_FILENAME                   ("application.fileLock.filename"),
    APPLICATION_FILELOCK_WAIT_SECONDS               ("application.fileLock.waitSeconds"),
    APPLICATION_WORDLIST_RETRY_SECONDS              ("application.wordlistRetryImportSeconds"),
    AUDIT_EVENTS_EMAILFROM                          ("audit.events.emailFrom"),
    AUDIT_EVENTS_EMAILSUBJECT                       ("audit.events.emailSubject"),
    AUDIT_EVENTS_LOCALDB_MAX_BULK_REMOVALS          ("audit.events.localdb.maxBulkRemovals"),
    AUDIT_SYSLOG_MAX_MESSAGE_LENGTH                 ("audit.syslog.message.length"),
    AUDIT_SYSLOG_TRUNCATE_MESSAGE                   ("audit.syslog.message.truncateMsg"),
    BACKUP_LOCATION                                 ("backup.path"),
    BACKUP_CONFIG_COUNT                             ("backup.config.count"),
    BACKUP_LOCALDB_COUNT                            ("backup.localdb.count"),
    CACHE_ENABLE                                    ("cache.enable"),
    CACHE_MEMORY_MAX_ITEMS                          ("cache.memory.maxItems"),
    CACHE_PWRULECHECK_LIFETIME_MS                   ("cache.pwRuleCheckLifetimeMS"),
    CACHE_FORM_UNIQUE_VALUE_LIFETIME_MS             ("cache.uniqueFormValueLifetimeMS"),
    CLIENT_ACTIVITY_MAX_EPS_RATE                    ("client.ajax.activityMaxEpsRate"),
    CLIENT_AJAX_PW_WAIT_CHECK_SECONDS               ("client.ajax.changePasswordWaitCheckSeconds"),
    CLIENT_AJAX_TYPING_TIMEOUT                      ("client.ajax.typingTimeout"),
    CLIENT_AJAX_TYPING_WAIT                         ("client.ajax.typingWait"),
    CLIENT_FORM_NONCE_ENABLE                        ("client.formNonce.enable"),
    CLIENT_FORM_NONCE_LENGTH                        ("client.formNonce.length"),
    CLIENT_FORM_CLIENT_REGEX_ENABLED                ("client.form.clientRegexEnable"),
    CLIENT_WARNING_HEADER_SHOW                      ("client.warningHeader.show"),
    CLIENT_PW_SHOW_REVERT_TIMEOUT                   ("client.pwShowRevertTimeout"),
    CLIENT_JS_ENABLE_HTML5DIALOG                    ("client.js.enableHtml5Dialog"),
    CLIENT_JSP_SHOW_ICONS                           ("client.jsp.showIcons"),
    CONFIG_MAX_JDBC_JAR_SIZE                        ("config.maxJdbcJarSize"),
    CONFIG_RELOAD_ON_CHANGE                         ("config.reloadOnChange"),
    CONFIG_MAX_PERSISTENT_LOGIN_SECONDS             ("config.maxPersistentLoginSeconds"),
    CONFIG_HISTORY_MAX_ITEMS                        ("config.login.history.maxEvents"),
    CONFIG_FILE_SCAN_FREQUENCY                      ("config.fileScanFrequencyMS"),
    CONFIG_NEWUSER_PASSWORD_POLICY_CACHE_MS         ("config.newuser.passwordPolicyCacheMS"),
    CONFIG_THEME                                    ("config.theme"),
    CONFIG_JBCRYPT_PWLIB_ENABLE                     ("config.enableJbCryptPwLibrary"),
    CONFIG_EDITOR_QUERY_FILTER_TEST_LIMIT           ("configEditor.queryFilter.testLimit"),
    CONFIG_EDITOR_IDLE_TIMEOUT                      ("configEditor.idleTimeoutSeconds"),
    CONFIG_GUIDE_IDLE_TIMEOUT                       ("configGuide.idleTimeoutSeconds"),
    CONFIG_MANAGER_ZIPDEBUG_MAXLOGLINES             ("configManager.zipDebug.maxLogLines"),
    CONFIG_MANAGER_ZIPDEBUG_MAXLOGSECONDS           ("configManager.zipDebug.maxLogSeconds"),
    CLUSTER_DB_ENABLE                               ("cluster.db.enable"),
    CLUSTER_DB_HEARTBEAT_SECONDS                    ("cluster.db.heartbeatSeconds"),
    CLUSTER_DB_NODE_TIMEOUT_SECONDS                 ("cluster.db.nodeTimeoutSeconds"),
    CLUSTER_DB_NODE_PURGE_SECONDS                   ("cluster.db.nodePurgeSeconds"),
    DB_JDBC_LOAD_STRATEGY                           ("db.jdbcLoadStrategy"),
    DB_CONNECTIONS_MAX                              ("db.connections.max"),
    DB_CONNECTIONS_TIMEOUT_MS                       ("db.connections.timeoutMs"),
    DB_CONNECTIONS_WATCHDOG_FREQUENCY_SECONDS       ("db.connections.watchdogFrequencySeconds"),
    DB_SCHEMA_KEY_LENGTH                            ("db.schema.keyLength"),
    DOWNLOAD_FILENAME_STATISTICS_CSV                ("download.filename.statistics.csv"),
    DOWNLOAD_FILENAME_USER_REPORT_SUMMARY_CSV       ("download.filename.reportSummary.csv"),
    DOWNLOAD_FILENAME_USER_REPORT_RECORDS_CSV       ("download.filename.reportRecords.csv"),
    DOWNLOAD_FILENAME_AUDIT_RECORDS_CSV             ("download.filename.auditRecords.csv"),
    DOWNLOAD_FILENAME_LDAP_PERMISSION_CSV           ("download.filename.ldapPermission.csv"),
    DOWNLOAD_FILENAME_USER_DEBUG_JSON               ("download.filename.userDebug.json"),
    FORM_EMAIL_REGEX                                ("form.email.regexTest"),
    HTTP_RESOURCES_MAX_CACHE_ITEMS                  ("http.resources.maxCacheItems"),
    HTTP_RESOURCES_MAX_CACHE_BYTES                  ("http.resources.maxCacheBytes"),
    HTTP_RESOURCES_EXPIRATION_SECONDS               ("http.resources.expirationSeconds"),
    HTTP_RESOURCES_ENABLE_GZIP                      ("http.resources.gzip.enable"),
    HTTP_RESOURCES_PATH_NONCE_LENGTH                ("http.resources.pathNonce.length"),
    HTTP_RESOURCES_ENABLE_PATH_NONCE                ("http.resources.pathNonceEnable"),
    HTTP_RESOURCES_NONCE_PATH_PREFIX                ("http.resources.pathNoncePrefix"),
    HTTP_RESOURCES_ZIP_FILES                        ("http.resources.zipFiles"),
    HTTP_COOKIE_DEFAULT_SECURE_FLAG                 ("http.cookie.default.secureFlag"),
    HTTP_COOKIE_THEME_NAME                          ("http.cookie.theme.name"),
    HTTP_COOKIE_THEME_AGE                           ("http.cookie.theme.age"),
    HTTP_COOKIE_LOCALE_NAME                         ("http.cookie.locale.name"),
    HTTP_COOKIE_AUTHRECORD_NAME                     ("http.cookie.authRecord.name"),
    HTTP_COOKIE_AUTHRECORD_AGE                      ("http.cookie.authRecord.age"),
    HTTP_COOKIE_MAX_READ_LENGTH                     ("http.cookie.maxReadLength"),
    HTTP_COOKIE_CAPTCHA_SKIP_NAME                   ("http.cookie.captchaSkip.name"),
    HTTP_COOKIE_CAPTCHA_SKIP_AGE                    ("http.cookie.captchaSkip.age"),
    HTTP_COOKIE_LOGIN_NAME                          ("http.cookie.login.name"),
    HTTP_BASIC_AUTH_CHARSET                         ("http.basicAuth.charset"),
    HTTP_BODY_MAXREAD_LENGTH                        ("http.body.maxReadLength"),
    HTTP_CLIENT_SOCKET_TIMEOUT_MS                   ("http.client.socketTimeoutMs"),
    HTTP_CLIENT_CONNECT_TIMEOUT_MS                  ("http.client.connectTimeoutMs"),
    HTTP_CLIENT_REQUEST_TIMEOUT_MS                  ("http.client.requestTimeoutMs"),
    HTTP_CLIENT_PROMISCUOUS_WORDLIST_ENABLE         ("http.client.promiscuous.wordlist.enable"),
    HTTP_ENABLE_GZIP                                ("http.gzip.enable"),
    HTTP_ERRORS_ALLOW_HTML                          ("http.errors.allowHtml"),
    HTTP_HEADER_SERVER                              ("http.header.server"),
    HTTP_HEADER_SEND_CONTENT_LANGUAGE               ("http.header.sendContentLanguage"),
    HTTP_HEADER_SEND_XAMB                           ("http.header.sendXAmb"),
    HTTP_HEADER_SEND_XINSTANCE                      ("http.header.sendXInstance"),
    HTTP_HEADER_SEND_XNOISE                         ("http.header.sendXNoise"),
    HTTP_HEADER_SEND_XSESSIONID                     ("http.header.sendXSessionID"),
    HTTP_HEADER_SEND_XVERSION                       ("http.header.sendXVersion"),
    HTTP_HEADER_SEND_XCONTENTTYPEOPTIONS            ("http.header.sendXContentTypeOptions"),
    HTTP_HEADER_SEND_XXSSPROTECTION                 ("http.header.sendXXSSProtection"),
    HTTP_HEADER_NOISE_LENGTH                        ("http.header.noise.length"),
    HTTP_HEADER_CSP_NONCE_BYTES                     ("http.header.csp.nonce.bytes"),
    HTTP_PARAM_NAME_FORWARD_URL                     ("http.parameter.forward"),
    HTTP_PARAM_NAME_LOGOUT_URL                      ("http.parameter.logout"),
    HTTP_PARAM_NAME_THEME                           ("http.parameter.theme"),
    HTTP_PARAM_NAME_LOCALE                          ("http.parameter.locale"),
    HTTP_PARAM_NAME_PASSWORD_EXPIRED                ("http.parameter.passwordExpired"),
    HTTP_PARAM_NAME_SSO_OVERRIDE                    ("http.parameter.ssoOverride"),
    HTTP_PARAM_MAX_READ_LENGTH                      ("http.parameter.maxReadLength"),
    HTTP_PARAM_SESSION_VERIFICATION                 ("http.parameter.sessionVerification"),
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
    HTTP_DOWNLOAD_BUFFER_SIZE                       ("http.download.buffer.size"),
    HTTP_SESSION_RECYCLE_AT_AUTH                    ("http.session.recycleAtAuth"),
    HTTP_SESSION_VALIDATION_KEY_LENGTH              ("http.session.validationKeyLength"),
    HTTP_SERVLET_ENABLE_POST_REDIRECT_GET           ("http.servlet.enablePostRedirectGet"),
    LOCALDB_AGGRESSIVE_COMPACT_ENABLED              ("localdb.aggressiveCompact.enabled"),
    LOCALDB_IMPLEMENTATION                          ("localdb.implementation"),
    LOCALDB_INIT_STRING                             ("localdb.initParameters"),
    LOCALDB_LOCATION                                ("localdb.location"),
    LOCALDB_LOGWRITER_BUFFER_SIZE                   ("localdb.logWriter.bufferSize"),
    LOCALDB_LOGWRITER_MAX_BUFFER_WAIT_MS            ("localdb.logWriter.maxBufferWaitMs"),
    LOCALDB_LOGWRITER_MAX_TRIM_SIZE                 ("localdb.logWriter.maxTrimSize"),
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
    HEALTHCHECK_ENABLED                             ("healthCheck.enabled"),
    HEALTHCHECK_NOMINAL_CHECK_INTERVAL              ("healthCheck.nominalCheckIntervalSeconds"),
    HEALTHCHECK_MIN_CHECK_INTERVAL                  ("healthCheck.minimumCheckIntervalSeconds"),
    HEALTHCHECK_MAX_RECORD_AGE                      ("healthCheck.maximumRecordAgeSeconds"),
    HEALTHCHECK_MAX_FORCE_WAIT                      ("healthCheck.maximumForceCheckWaitSeconds"),
    HEALTH_CERTIFICATE_WARN_SECONDS                 ("health.certificate.warnSeconds"),
    HEALTH_LDAP_CAUTION_DURATION_MS                 ("health.ldap.cautionDurationMS"),
    HEALTH_JAVA_MAX_THREADS                         ("health.java.maxThreads"),
    HEALTH_JAVA_MIN_HEAP_BYTES                      ("health.java.minHeapBytes"),
    HELPDESK_TOKEN_MAX_AGE                          ("helpdesk.token.maxAgeSeconds"),
    HELPDESK_TOKEN_VALUE                            ("helpdesk.token.value"),
    HELPDESK_VERIFICATION_INVALID_DELAY_MS          ("helpdesk.verification.invalid.delayMs"),
    HELPDESK_VERIFICATION_TIMEOUT_SECONDS           ("helpdesk.verification.timeoutSeconds"),
    LDAP_RESOLVE_CANONICAL_DN                       ("ldap.resolveCanonicalDN"),
    LDAP_CACHE_CANONICAL_ENABLE                     ("ldap.cache.canonical.enable"),
    LDAP_CACHE_CANONICAL_SECONDS                    ("ldap.cache.canonical.seconds"),
    LDAP_CACHE_USER_GUID_ENABLE                     ("ldap.cache.userGuid.enable"),
    LDAP_CACHE_USER_GUID_SECONDS                    ("ldap.cache.userGuid.seconds"),
    LDAP_CHAI_SETTINGS                              ("ldap.chaiSettings"),
    LDAP_PROXY_CONNECTION_PER_PROFILE               ("ldap.proxy.connectionsPerProfile"),
    LDAP_PROXY_MAX_CONNECTIONS                      ("ldap.proxy.maxConnections"),
    LDAP_EXTENSIONS_NMAS_ENABLE                     ("ldap.extensions.nmas.enable"),
    LDAP_CONNECTION_TIMEOUT                         ("ldap.connection.timeoutMS"),
    LDAP_PROFILE_RETRY_DELAY                        ("ldap.profile.retryDelayMS"),
    LDAP_PROMISCUOUS_ENABLE                         ("ldap.promiscuousEnable"),
    LDAP_PASSWORD_REPLICA_CHECK_INIT_DELAY_MS       ("ldap.password.replicaCheck.initialDelayMS"),
    LDAP_PASSWORD_REPLICA_CHECK_CYCLE_DELAY_MS      ("ldap.password.replicaCheck.cycleDelayMS"),
    LDAP_PASSWORD_CHANGE_SELF_ENABLE                ("ldap.password.change.self.enable"),
    LDAP_PASSWORD_CHANGE_HELPDESK_ENABLE            ("ldap.password.change.helpdesk.enable"),
    LDAP_GUID_PATTERN                               ("ldap.guid.pattern"),
    LDAP_BROWSER_MAX_ENTRIES                        ("ldap.browser.maxEntries"),
    LDAP_SEARCH_PAGING_ENABLE                       ("ldap.search.paging.enable"),
    LDAP_SEARCH_PAGING_SIZE                         ("ldap.search.paging.size"),
    LDAP_SEARCH_PARALLEL_ENABLE                     ("ldap.search.parallel.enable"),
    LDAP_SEARCH_PARALLEL_FACTOR                     ("ldap.search.parallel.factor"),
    LDAP_SEARCH_PARALLEL_THREAD_MAX                 ("ldap.search.parallel.threadMax"),
    LDAP_ORACLE_POST_TEMPPW_USE_CURRENT_TIME        ("ldap.oracle.postTempPasswordUseCurrentTime"),
    LOGGING_PATTERN                                 ("logging.pattern"),
    LOGGING_FILE_MAX_SIZE                           ("logging.file.maxSize"),
    LOGGING_FILE_MAX_ROLLOVER                       ("logging.file.maxRollover"),
    LOGGING_FILE_PATH                               ("logging.file.path"),
    LOGGING_DEV_OUTPUT                              ("logging.devOutput.enable"),
    NEWUSER_LDAP_USE_TEMP_PW                        ("newUser.ldap.useTempPassword"),
    NEWUSER_TOKEN_ALLOW_PLAIN_PW                    ("newUser.token.allowPlainPassword"),
    NMAS_THREADS_MAX_COUNT                          ("nmas.threads.maxCount"),
    NMAS_THREADS_MIN_SECONDS                        ("nmas.threads.minSeconds"),
    NMAS_THREADS_MAX_SECONDS                        ("nmas.threads.maxSeconds"),
    NMAS_THREADS_WATCHDOG_FREQUENCY                 ("nmas.threads.watchdogFrequencyMs"),
    NMAS_THREADS_WATCHDOG_DEBUG                     ("nmas.threads.watchdogDebug"),
    NMAS_IGNORE_NMASCR_DURING_FORCECHECK            ("nmas.ignoreNmasCrDuringForceSetupCheck"),
    NMAS_USE_LOCAL_SASL_FACTORY                     ("nmas.useLocalSaslFactory"),
    NMAS_FORCE_SASL_FACTORY_REGISTRATION            ("nmas.forceSaslFactoryRegistration"),
    OAUTH_ID_REQUEST_TYPE                           ("oauth.id.requestType"),
    OAUTH_ID_ACCESS_GRANT_TYPE                      ("oauth.id.accessGrantType"),
    OAUTH_ID_REFRESH_GRANT_TYPE                     ("oauth.id.refreshGrantType"),
    OAUTH_ENABLE_TOKEN_REFRESH                      ("oauth.enableTokenRefresh"),
    OAUTH_RETURN_URL_OVERRIDE                       ("oauth.returnUrlOverride"),

    /* Allows one older TOTP token - compensate for clock out of sync */
    TOTP_PAST_INTERVALS                             ("otp.totp.pastIntervals"),

    /* Allows one newer TOTP token - compensate for clock out of sync */
    TOTP_FUTURE_INTERVALS                           ("otp.totp.futureIntervals"),

    TOTP_INTERVAL                                   ("otp.totp.intervalSeconds"),
    OTP_TOKEN_LENGTH                                ("otp.token.length"),
    OTP_SALT_CHARLENGTH                             ("otp.salt.charLength"),
    OTP_RECOVERY_TOKEN_MACRO                        ("otp.recovery.macro"),
    OTP_RECOVERY_HASH_COUNT                         ("otp.recoveryHash.iterations"),
    OTP_RECOVERY_HASH_METHOD                        ("otp.recoveryHash.method"),
    OTP_QR_IMAGE_HEIGHT                             ("otp.qrImage.height"),
    OTP_QR_IMAGE_WIDTH                              ("otp.qrImage.width"),
    OTP_ENCRYPTION_ALG                              ("otp.encryptionAlg"),
    PASSWORD_RANDOMGEN_MAX_ATTEMPTS                 ("password.randomGenerator.maxAttempts"),
    PASSWORD_RANDOMGEN_MAX_LENGTH                   ("password.randomGenerator.maxLength"),
    PASSWORD_RANDOMGEN_JITTER_COUNT                 ("password.randomGenerator.jitter.count"),

    /* Strength thresholds, introduced by the addition of the zxcvbn strength meter library (since it has 5 levels) */
    PASSWORD_STRENGTH_THRESHOLD_VERY_STRONG         ("password.strength.threshold.veryStrong"),
    PASSWORD_STRENGTH_THRESHOLD_STRONG              ("password.strength.threshold.strong"),
    PASSWORD_STRENGTH_THRESHOLD_GOOD                ("password.strength.threshold.good"),
    PASSWORD_STRENGTH_THRESHOLD_WEAK                ("password.strength.threshold.weak"),
    PASSWORD_STRENGTH_THRESHOLD_VERY_WEAK           ("password.strength.threshold.veryWeak"),

    PEOPLESEARCH_MAX_VALUE_VERIFYUSERDN             ("peoplesearch.values.verifyUserDN"),
    PEOPLESEARCH_VALUE_MAXCOUNT                     ("peoplesearch.values.maxCount"),
    PEOPLESEARCH_VIEW_DETAIL_LINKS                  ("peoplesearch.view.detail.links"),
    PEOPLESEARCH_ORGCHART_ENABLE_CHILD_COUNT        ("peoplesearch.orgChart.enableChildCount"),
    PEOPLESEARCH_ORGCHART_MAX_PARENTS               ("peoplesearch.orgChart.maxParents"),
    QUEUE_EMAIL_RETRY_TIMEOUT_MS                    ("queue.email.retryTimeoutMs"),
    QUEUE_EMAIL_MAX_COUNT                           ("queue.email.maxCount"),
    QUEUE_EMAIL_MAX_THREADS                         ("queue.email.maxThreads"),
    QUEUE_SMS_RETRY_TIMEOUT_MS                      ("queue.sms.retryTimeoutMs"),
    QUEUE_SMS_MAX_COUNT                             ("queue.sms.maxCount"),
    QUEUE_SYSLOG_RETRY_TIMEOUT_MS                   ("queue.syslog.retryTimeoutMs"),
    QUEUE_SYSLOG_MAX_AGE_MS                         ("queue.syslog.maxAgeMs"),
    QUEUE_SYSLOG_MAX_COUNT                          ("queue.syslog.maxCount"),
    RECAPTCHA_CLIENT_JS_URL                         ("recaptcha.clientJsUrl"),
    RECAPTCHA_CLIENT_IFRAME_URL                     ("recaptcha.clientIframeUrl"),
    RECAPTCHA_VALIDATE_URL                          ("recaptcha.validateUrl"),
    REPORTING_LDAP_SEARCH_TIMEOUT                   ("reporting.ldap.searchTimeoutMs"),
    REPORTING_LDAP_SEARCH_THREADS                   ("reporting.ldap.searchThreads"),
    SECURITY_STRIP_INLINE_JAVASCRIPT                ("security.html.stripInlineJavascript"),
    SECURITY_HTTP_FORCE_REQUEST_SEQUENCING          ("security.http.forceRequestSequencing"),
    SECURITY_HTTP_STRIP_HEADER_REGEX                ("security.http.stripHeaderRegex"),
    SECURITY_HTTP_PERFORM_CSRF_HEADER_CHECKS        ("security.http.performCsrfHeaderChecks"),
    SECURITY_HTTP_PROMISCUOUS_ENABLE                ("security.http.promiscuousEnable"),
    SECURITY_HTTP_CONFIG_CSP_HEADER                 ("security.http.config.cspHeader"),
    SECURITY_HTTPSSERVER_SELF_FUTURESECONDS         ("security.httpsServer.selfCert.futureSeconds"),
    SECURITY_HTTPSSERVER_SELF_ALG                   ("security.httpsServer.selfCert.alg"),
    SECURITY_HTTPSSERVER_SELF_KEY_SIZE              ("security.httpsServer.selfCert.keySize"),
    SECURITY_LOGIN_HIDDEN_ERROR_TYPES               ("security.login.hiddenErrorTypes"),
    SECURITY_RESPONSES_HASH_ITERATIONS              ("security.responses.hashIterations"),
    SECURITY_INPUT_TRIM                             ("security.input.trim"),
    SECURITY_INPUT_PASSWORD_TRIM                    ("security.input.password.trim"),
    SECURITY_INPUT_THEME_MATCH_REGEX                ("security.input.themeMatchRegex"),
    SECURITY_WS_REST_SERVER_SECRET_HEADER           ("security.ws.rest.server.secretKeyHeader"),
    SECURITY_SHAREDHISTORY_HASH_ITERATIONS          ("security.sharedHistory.hashIterations"),
    SECURITY_SHAREDHISTORY_HASH_NAME                ("security.sharedHistory.hashName"),
    SECURITY_SHAREDHISTORY_CASE_INSENSITIVE         ("security.sharedHistory.caseInsensitive"),
    SECURITY_SHAREDHISTORY_SALT_LENGTH              ("security.sharedHistory.saltLength"),
    SECURITY_CERTIFICATES_VALIDATE_TIMESTAMPS       ("security.certs.validateTimestamps"),
    SECURITY_CONFIG_MIN_SECURITY_KEY_LENGTH         ("security.config.minSecurityKeyLength"),
    SECURITY_DEFAULT_EPHEMERAL_BLOCK_ALG            ("security.defaultEphemeralBlockAlg"),
    SECURITY_DEFAULT_EPHEMERAL_HASH_ALG             ("security.defaultEphemeralHashAlg"),
    SEEDLIST_BUILTIN_PATH                           ("seedlist.builtin.path"),
    SMTP_SUBJECT_ENCODING_CHARSET                   ("smtp.subjectEncodingCharset"),
    TOKEN_CLEANER_INTERVAL_SECONDS                  ("token.cleaner.intervalSeconds"),
    TOKEN_MASK_EMAIL_REGEX                          ("token.mask.email.regex"),
    TOKEN_MASK_EMAIL_REPLACE                        ("token.mask.email.replace"),
    TOKEN_MASK_SHOW_SELECTION                       ("token.mask.showSelection"),
    TOKEN_MASK_SMS_REGEX                            ("token.mask.sms.regex"),
    TOKEN_MASK_SMS_REPLACE                          ("token.mask.sms.replace"),
    TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS                ("token.maxUniqueCreateAttempts"),
    TOKEN_RESEND_DELAY_MS                           ("token.resend.delayMS"),
    TOKEN_REMOVE_ON_CLAIM                           ("token.removeOnClaim"),
    TOKEN_VERIFY_PW_MODIFY_TIME                     ("token.verifyPwModifyTime"),
    TOKEN_STORAGE_MAX_KEY_LENGTH                    ("token.storage.maxKeyLength"),
    TELEMETRY_SENDER_IMPLEMENTATION                 ("telemetry.senderImplementation"),
    TELEMETRY_SENDER_SETTINGS                       ("telemetry.senderSettings"),
    TELEMETRY_SEND_FREQUENCY_SECONDS                ("telemetry.sendFrequencySeconds"),
    TELEMETRY_MIN_AUTHENTICATIONS                   ("telemetry.minimumAuthentications"),



    /** Regular expression to be used for matching URLs to be shortened by the URL Shortening Service Class. */
    URL_SHORTNER_URL_REGEX                          ("urlshortener.url.regex"),
    WORDLIST_BUILTIN_PATH                           ("wordlist.builtin.path"),
    WORDLIST_CHAR_LENGTH_MAX                        ("wordlist.maxCharLength"),
    WORDLIST_CHAR_LENGTH_MIN                        ("wordlist.minCharLength"),
    WS_REST_CLIENT_PWRULE_HALTONERROR               ("ws.restClient.pwRule.haltOnError"),
    WS_REST_SERVER_SIGNING_FORM_TIMEOUT_SECONDS     ("ws.restServer.signing.form.timeoutSeconds"),
    ALLOW_MACRO_IN_REGEX_SETTING                    ("password.policy.allowMacroInRegexSetting"),

    ;

    public static final String VALUE_SEPARATOR = ";;;";
    private static final String DESCRIPTION_SUFFIX = "_description";

    private final String key;
    private String defaultValue;

    AppProperty(final String key) {
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
        return ResourceBundle.getBundle(AppProperty.class.getName()).getString(key);
    }
}
