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

package password.pwm;

import java.util.ResourceBundle;

public enum AppProperty {

    AUDIT_EVENTS_IGNORELIST                         ("audit.events.ignoreList"),
    AUDIT_EVENTS_EMAILFROM                          ("audit.events.emailFrom"),
    AUDIT_VAULT_MAX_RECORDS                         ("audit.vault.maxRecords"),
    BACKUP_LOCATION                                 ("backup.path"),
    BACKUP_CONFIG_COUNT                             ("backup.config.count"),
    BACKUP_LOCALDB_COUNT                            ("backup.localdb.count"),
    CLIENT_ACTIVITY_MAX_EPS_RATE                    ("client.ajax.activityMaxEpsRate"),
    CLIENT_AJAX_PW_WAIT_CHECK_SECONDS               ("client.ajax.changePasswordWaitCheckSeconds"),
    CLIENT_AJAX_TYPING_TIMEOUT                      ("client.ajax.typingTimeout"),
    CLIENT_AJAX_TYPING_WAIT                         ("client.ajax.typingWait"),
    HTTP_RESOURCES_MAX_CACHE_ITEMS                  ("http.resources.maxCacheItems"),
    HTTP_RESOURCES_MAX_CACHE_BYTES                  ("http.resources.maxCacheBytes"),
    HTTP_RESOURCES_EXPIRATION_SECONDS               ("http.resources.expirationSeconds"),
    HTTP_RESOURCES_ENABLE_GZIP                      ("http.resources.gzip.enable"),
    HTTP_RESOURCES_ENABLE_PATH_NONCE                ("http.resources.pathNonceEnable"),
    HTTP_RESOURCES_NONCE_PATH_PREFIX                ("http.resources.pathNoncePrefix"),
    HTTP_COOKIE_NAME_THEME                          ("http.cookie.theme"),
    HTTP_COOKIE_NAME_LOCALE                         ("http.cookie.locale"),
    HTTP_BASIC_AUTH_CHARSET                         ("http.basicAuth.charset"),
    HTTP_BODY_MAXREAD_LENGTH                        ("http.body.maxReadLength"),
    HTTP_ENABLE_GZIP                                ("http.gzip.enable"),
    HTTP_ERRORS_ALLOW_HTML                          ("http.errors.allowHtml"),
    HTTP_HEADER_SEND_XAMB                           ("http.header.sendXAmb"),
    HTTP_HEADER_SEND_XFRAMEDENY                     ("http.header.sendXFrameDeny"),
    HTTP_HEADER_SEND_XINSTANCE                      ("http.header.sendXInstance"),
    HTTP_HEADER_SEND_XNOISE                         ("http.header.sendXNoise"),
    HTTP_HEADER_SEND_XSESSIONID                     ("http.header.sendXSessionID"),
    HTTP_HEADER_SEND_XVERSION                       ("http.header.sendXVersion"),
    HTTP_PARAM_NAME_FORWARD_URL                     ("http.parameter.forward"),
    HTTP_PARAM_NAME_LOGOUT_URL                      ("http.parameter.logout"),
    HTTP_PARAM_NAME_THEME                           ("http.parameter.theme"),
    HTTP_PARAM_NAME_LOCALE                          ("http.parameter.locale"),
    HTTP_PARAM_NAME_PASSWORD_EXPIRED                ("http.parameter.passwordExpired"),
    HTTP_PARAM_MAX_READ_LENGTH                      ("http.parameter.maxReadLength"),
    HTTP_SESSION_RECYCLE_AT_AUTH                    ("http.session.recycleAtAuth"),
    HTTP_SESSION_VALIDATION_KEY_LENGTH              ("http.session.validationKeyLength"),
    LOCALDB_COMPRESSION_ENABLED                     ("localdb.compression.enabled"),
    LOCALDB_DECOMPRESSION_ENABLED                   ("localdb.decompression.enabled"),
    LOCALDB_COMPRESSION_MINSIZE                     ("localdb.compression.minSize"),
    LOCALDB_IMPLEMENTATION                          ("localdb.implementation"),
    LOCALDB_INIT_STRING                             ("localdb.initParameters"),
    INTRUDER_RETENTION_TIME_MS                      ("intruder.retentionTimeMS"),
    INTRUDER_CLEANUP_FREQUENCY_MS                   ("intruder.cleanupFrequencyMS"),
    INTRUDER_MIN_DELAY_PENALTY_MS                   ("intruder.minimumDelayPenaltyMS"),
    INTRUDER_MAX_DELAY_PENALTY_MS                   ("intruder.maximumDelayPenaltyMS"),
    INTRUDER_DELAY_PER_COUNT_MS                     ("intruder.delayPerCountMS"),
    INTRUDER_DELAY_MAX_JITTER_MS                    ("intruder.delayMaxJitterMS"),
    HEALTH_CERTIFICATE_WARN_SECONDS                 ("health.certificate.warnSeconds"),
    HEALTH_LDAP_CAUTION_DURATION_MS                 ("health.ldap.cautionDurationMS"),
    LDAP_CONNECTION_TIMEOUT                         ("ldap.connection.timeoutMS"),
    LDAP_PROFILE_RETRY_DELAY                        ("ldap.profile.retryDelayMS"),
    LDAP_PROMISCUOUS_ENABLE                         ("ldap.promiscuousEnable"),
    LDAP_SEARCH_TIMEOUT                             ("ldap.search.timeoutMS"),
    LOGGING_PATTERN                                 ("logging.pattern"),
    LOGGING_FILE_MAX_SIZE                           ("logging.file.maxSize"),
    LOGGING_FILE_MAX_ROLLOVER                       ("logging.file.maxRollover"),
    LOGGING_FILE_PATH                               ("logging.file.path"),
    LOGGING_DEV_OUTPUT                              ("logging.devOutput.enable"),
    NMAS_THREADS_MAX_COUNT                          ("nmas.threads.maxCount"),
    NMAS_THREADS_MIN_SECONDS                        ("nmas.threads.minSeconds"),
    NMAS_THREADS_MAX_SECONDS                        ("nmas.threads.maxSeconds"),
    NMAS_THREADS_WATCHDOG_FREQUENCY                 ("nmas.threads.watchdogFrequencyMs"),
    QUEUE_EMAIL_RETRY_TIMEOUT_MS                    ("queue.email.retryTimeoutMs"),
    QUEUE_EMAIL_MAX_AGE_MS                          ("queue.email.maxAgeMs"),
    QUEUE_EMAIL_MAX_COUNT                           ("queue.email.maxCount"),
    QUEUE_SMS_RETRY_TIMEOUT_MS                      ("queue.sms.retryTimeoutMs"),
    QUEUE_SMS_MAX_AGE_MS                            ("queue.sms.maxAgeMs"),
    QUEUE_SMS_MAX_COUNT                             ("queue.sms.maxCount"),
    QUEUE_SYSLOG_RETRY_TIMEOUT_MS                   ("queue.syslog.retryTimeoutMs"),
    QUEUE_SYSLOG_MAX_AGE_MS                         ("queue.syslog.maxAgeMs"),
    QUEUE_SYSLOG_MAX_COUNT                          ("queue.syslog.maxCount"),
    REPORTING_LDAP_SEARCH_TIMEOUT                   ("reporting.ldap.searchTimeoutMs"),
    SECURITY_RESPONSES_HASH_ITERATIONS              ("security.responses.hashIterations"),
    SECURITY_SHAREDHISTORY_HASH_ITERATIONS          ("security.sharedHistory.hashIterations"),
    SECURITY_SHAREDHISTORY_HASH_NAME                ("security.sharedHistory.hashName"),
    SECURITY_SHAREDHISTORY_CASE_INSENSITIVE         ("security.sharedHistory.caseInsensitive"),

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
