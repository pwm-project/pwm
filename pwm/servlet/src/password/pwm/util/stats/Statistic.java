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

package password.pwm.util.stats;

import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Admin;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.*;

public enum Statistic {
    AUTHENTICATIONS                     (Type.INCREMENTOR, "Authentications", null),
    AUTHENTICATION_FAILURES             (Type.INCREMENTOR, "AuthenticationFailures", null),
    AUTHENTICATION_EXPIRED              (Type.INCREMENTOR, "Authentications_Expired", null),
    AUTHENTICATION_PRE_EXPIRED          (Type.INCREMENTOR, "Authentications_PreExpired", null),
    AUTHENTICATION_EXPIRED_WARNING      (Type.INCREMENTOR, "Authentications_ExpiredWarning", null),
    PWM_STARTUPS                        (Type.INCREMENTOR, "PWM_Startups", null),
    PWM_UNKNOWN_ERRORS                  (Type.INCREMENTOR, "PWM_UnknownErrors", null),
    PASSWORD_CHANGES                    (Type.INCREMENTOR, "PasswordChanges", null),
    FORGOTTEN_USERNAME_FAILURES         (Type.INCREMENTOR, "ForgottenUsernameFailures", null),
    FORGOTTEN_USERNAME_SUCCESSES        (Type.INCREMENTOR, "ForgottenUsernameSuccesses", null),
    EMAIL_SEND_SUCCESSES                (Type.INCREMENTOR, "EmailSendSuccesses", null),
    EMAIL_SEND_FAILURES                 (Type.INCREMENTOR, "EmailSendFailures", null),
    EMAIL_SEND_DISCARDS                 (Type.INCREMENTOR, "EmailSendDiscards", null),
    SMS_SEND_SUCCESSES                  (Type.INCREMENTOR, "SmsSendSuccesses", null),
    SMS_SEND_FAILURES                   (Type.INCREMENTOR, "SmsSendFailures", null),
    SMS_SEND_DISCARDS                   (Type.INCREMENTOR, "SmsSendDiscards", null),
    PASSWORD_RULE_CHECKS                (Type.INCREMENTOR, "PasswordRuleChecks", null),
    HTTP_REQUESTS                       (Type.INCREMENTOR, "HttpRequests", null),
    HTTP_RESOURCE_REQUESTS              (Type.INCREMENTOR, "HttpResourceRequests", null),
    HTTP_SESSIONS                       (Type.INCREMENTOR, "HttpSessions", null),
    ACTIVATED_USERS                     (Type.INCREMENTOR, "ActivatedUsers", null),
    NEW_USERS                           (Type.INCREMENTOR, "NewUsers", new ConfigSettingDetail(PwmSetting.NEWUSER_ENABLE)),
    GUESTS                              (Type.INCREMENTOR, "Guests", new ConfigSettingDetail(PwmSetting.GUEST_ENABLE)),
    UPDATED_GUESTS                      (Type.INCREMENTOR, "UpdatedGuests", new ConfigSettingDetail(PwmSetting.GUEST_ENABLE)),
    LOCKED_USERS                        (Type.INCREMENTOR, "LockedUsers", null),
    LOCKED_ADDRESSES                    (Type.INCREMENTOR, "LockedAddresses", null),
    LOCKED_USERIDS                      (Type.INCREMENTOR, "LockedUserDNs", null),
    LOCKED_ATTRIBUTES                   (Type.INCREMENTOR, "LockedAttributes", null),
    LOCKED_TOKENDESTS                   (Type.INCREMENTOR, "LockedTokenDests", null),
    CAPTCHA_SUCCESSES                   (Type.INCREMENTOR, "CaptchaSuccessess", null),
    CAPTCHA_FAILURES                    (Type.INCREMENTOR, "CaptchaFailures", null),
    CAPTCHA_PRESENTATIONS               (Type.INCREMENTOR, "CaptchaPresentations", null),
    LDAP_UNAVAILABLE_COUNT              (Type.INCREMENTOR, "LdapUnavailableCount", null),
    DB_UNAVAILABLE_COUNT                (Type.INCREMENTOR, "DatabaseUnavailableCount", null),
    SETUP_RESPONSES                     (Type.INCREMENTOR, "SetupResponses", null),
    SETUP_OTP_SECRET                    (Type.INCREMENTOR, "SetupOtpSecret", new ConfigSettingDetail(PwmSetting.OTP_ENABLED)),
    UPDATE_ATTRIBUTES                   (Type.INCREMENTOR, "UpdateAttributes", new ConfigSettingDetail(PwmSetting.UPDATE_PROFILE_ENABLE)),
    SHORTCUTS_SELECTED                  (Type.INCREMENTOR, "ShortcutsSelected", new ConfigSettingDetail(PwmSetting.SHORTCUT_ENABLE)),
    GENERATED_PASSWORDS                 (Type.INCREMENTOR, "GeneratedPasswords", null),
    RECOVERY_SUCCESSES                  (Type.INCREMENTOR, "RecoverySuccesses", null),
    RECOVERY_FAILURES                   (Type.INCREMENTOR, "RecoveryFailures", null),
    TOKENS_SENT                         (Type.INCREMENTOR, "TokensSent",null),
    TOKENS_PASSSED                      (Type.INCREMENTOR, "TokensPassed",null),
    RECOVERY_TOKENS_SENT                (Type.INCREMENTOR, "RecoveryTokensSent", null),
    RECOVERY_TOKENS_PASSED              (Type.INCREMENTOR, "RecoveryTokensPassed", null),
    RECOVERY_TOKENS_FAILED              (Type.INCREMENTOR, "RecoveryTokensFailed", null),
    RECOVERY_OTP_PASSED                 (Type.INCREMENTOR, "RecoveryOTPPassed", new ConfigSettingDetail(PwmSetting.OTP_ENABLED)),
    RECOVERY_OTP_FAILED                 (Type.INCREMENTOR, "RecoveryOTPFailed", new ConfigSettingDetail(PwmSetting.OTP_ENABLED)),
    PEOPLESEARCH_CACHE_HITS             (Type.INCREMENTOR, "PeopleSearchCacheHits", new ConfigSettingDetail(PwmSetting.PEOPLE_SEARCH_ENABLE)),
    PEOPLESEARCH_CACHE_MISSES           (Type.INCREMENTOR, "PeopleSearchCacheMisses", new ConfigSettingDetail(PwmSetting.PEOPLE_SEARCH_ENABLE)),
    PEOPLESEARCH_SEARCHES               (Type.INCREMENTOR, "PeopleSearchSearches", new ConfigSettingDetail(PwmSetting.PEOPLE_SEARCH_ENABLE)),
    PEOPLESEARCH_DETAILS                (Type.INCREMENTOR, "PeopleSearchDetails", new ConfigSettingDetail(PwmSetting.PEOPLE_SEARCH_ENABLE)),
    PEOPLESEARCH_ORGCHART               (Type.INCREMENTOR, "PeopleSearchOrgChart", new ConfigSettingDetail(PwmSetting.PEOPLE_SEARCH_ENABLE)),
    HELPDESK_PASSWORD_SET               (Type.INCREMENTOR, "HelpdeskPasswordSet", null),
    HELPDESK_USER_LOOKUP                (Type.INCREMENTOR, "HelpdeskUserLookup", null),
    HELPDESK_TOKENS_SENT                (Type.INCREMENTOR, "HelpdeskTokenSent", null),
    HELPDESK_UNLOCK                     (Type.INCREMENTOR, "HelpdeskUnlock", null),
    REST_STATUS                         (Type.INCREMENTOR, "RestStatus", null),
    REST_CHECKPASSWORD                  (Type.INCREMENTOR, "RestCheckPassword", null),
    REST_SETPASSWORD                    (Type.INCREMENTOR, "RestSetPassword", null),
    REST_RANDOMPASSWORD                 (Type.INCREMENTOR, "RestRandomPassword", null),
    REST_CHALLENGES                     (Type.INCREMENTOR, "RestChallenges", null),
    REST_HEALTH                         (Type.INCREMENTOR, "RestHealth", null),
    REST_STATISTICS                     (Type.INCREMENTOR, "RestStatistics", null),
    REST_VERIFYCHALLENGES               (Type.INCREMENTOR, "RestVerifyChallenges", null),
    INTRUDER_ATTEMPTS                   (Type.INCREMENTOR, "IntruderAttempts", null),

    AVG_PASSWORD_SYNC_TIME              (Type.AVERAGE, "AvgPasswordSyncTime", null),
    AVG_AUTHENTICATION_TIME             (Type.AVERAGE, "AvgAuthenticationTime", null),
    AVG_PASSWORD_STRENGTH               (Type.AVERAGE, "AvgPasswordStrength", null),
    AVG_LDAP_SEARCH_TIME                (Type.AVERAGE, "AvgLdapSearchTime", null),

    ;

    private final static PwmLogger LOGGER = PwmLogger.forClass(Statistic.class);
    private final Type type;
    private final String key;
    private final StatDetail statDetail;

    Statistic(
            final Type type,
            final String key,
            StatDetail statDetail
    ) {
        this.type = type;
        this.key = key;
        this.statDetail = statDetail;
    }

    public String getKey() {
        return key;
    }

    public Type getType() {
        return type;
    }

    public boolean isActive(final PwmApplication pwmApplication) {
        if (statDetail == null) {
            return true;
        }
        return statDetail.isActive(pwmApplication);
    }

    public static SortedSet<Statistic> sortedValues(final Locale locale) {
        final Comparator<Statistic> comparator = new Comparator<Statistic>() {
            public int compare(final Statistic o1, final Statistic o2) {
                return o1.getLabel(locale).compareTo(o2.getLabel(locale));
            }
        };
        final TreeSet<Statistic> set = new TreeSet<>(comparator);
        set.addAll(Arrays.asList(values()));
        return set;
    }

    public enum Type {
        INCREMENTOR,
        AVERAGE,
    }

    public String getLabel(final Locale locale) {
        final String keyName = "Statistic_Label." + this.getKey();
        return LocaleHelper.getLocalizedMessage(locale, keyName, null, Admin.class);
    }

    public String getDescription(final Locale locale) {
        final String keyName = "Statistic_Description." + this.getKey();
        try {
            return LocaleHelper.getLocalizedMessage(locale, keyName, null, Admin.class);
        } catch (Exception e) {
            LOGGER.error("unable to load localization for " + keyName + ", error: " + e.getMessage());
            return "missing localization for " + keyName;
        }
    }

    public enum EpsType {
        PASSWORD_CHANGES(Statistic.PASSWORD_CHANGES),
        AUTHENTICATION(Statistic.AUTHENTICATIONS),
        INTRUDER_ATTEMPTS(Statistic.INTRUDER_ATTEMPTS),
        PWMDB_WRITES(null),
        PWMDB_READS(null),
        DB_WRITES(null),
        DB_READS(null),
        ;

        private Statistic relatedStatistic;

        private EpsType(Statistic relatedStatistic) {
            this.relatedStatistic = relatedStatistic;
        }

        public Statistic getRelatedStatistic() {
            return relatedStatistic;
        }

        public String getLabel(final Locale locale) {
            final String keyName = "Statistic_Label." + EpsType.class.getSimpleName() + "_" + this.name();
            return LocaleHelper.getLocalizedMessage(locale, keyName, null, Admin.class);
        }
    }

    public enum EpsDuration {
        MINUTE(TimeDuration.MINUTE),
        HOUR(TimeDuration.HOUR),
        DAY(TimeDuration.DAY),
        ;

        private final TimeDuration timeDuration;

        private EpsDuration(TimeDuration timeDuration) {
            this.timeDuration = timeDuration;
        }

        public TimeDuration getTimeDuration() {
            return timeDuration;
        }
    }

    interface StatDetail {
        boolean isActive(PwmApplication pwmApplication);
    }

    static class ConfigSettingDetail implements StatDetail {
        final private PwmSetting pwmSetting;

        ConfigSettingDetail(PwmSetting pwmSetting)
        {
            this.pwmSetting = pwmSetting;
        }

        public boolean isActive(final PwmApplication pwmApplication) {
            return pwmApplication.getConfig().readSettingAsBoolean(pwmSetting);
        }
    }
}
