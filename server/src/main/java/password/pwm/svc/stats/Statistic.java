/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.svc.stats;

import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Admin;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.TimeDuration;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

public enum Statistic
{
    AUDIT_EVENTS( "AuditEvents", null ),
    AUTHENTICATIONS( "Authentications", null ),
    AUTHENTICATION_FAILURES( "AuthenticationFailures", null ),
    AUTHENTICATION_EXPIRED( "Authentications_Expired", null ),
    AUTHENTICATION_PRE_EXPIRED( "Authentications_PreExpired", null ),
    AUTHENTICATION_EXPIRED_WARNING( "Authentications_ExpiredWarning", null ),
    PWM_STARTUPS( "PWM_Startups", null ),
    PWM_UNKNOWN_ERRORS( "PWM_UnknownErrors", null ),
    PASSWORD_CHANGES( "PasswordChanges", null ),
    FORGOTTEN_USERNAME_FAILURES( "ForgottenUsernameFailures", null ),
    FORGOTTEN_USERNAME_SUCCESSES( "ForgottenUsernameSuccesses", null ),
    EMAIL_SEND_SUCCESSES( "EmailSendSuccesses", null ),
    EMAIL_SEND_FAILURES( "EmailSendFailures", null ),
    EMAIL_SEND_DISCARDS( "EmailSendDiscards", null ),
    SMS_SEND_SUCCESSES( "SmsSendSuccesses", null ),
    SMS_SEND_FAILURES( "SmsSendFailures", null ),
    SMS_SEND_DISCARDS( "SmsSendDiscards", null ),
    PASSWORD_RULE_CHECKS( "PasswordRuleChecks", null ),
    HTTP_REQUESTS( "HttpRequests", null ),
    HTTP_RESOURCE_REQUESTS( "HttpResourceRequests", null ),
    HTTP_SESSIONS( "HttpSessions", null ),
    ACTIVATED_USERS( "ActivatedUsers", null ),
    NEW_USERS( "NewUsers", new ConfigSettingDetail( PwmSetting.NEWUSER_ENABLE ) ),
    GUESTS( "Guests", new ConfigSettingDetail( PwmSetting.GUEST_ENABLE ) ),
    UPDATED_GUESTS( "UpdatedGuests", new ConfigSettingDetail( PwmSetting.GUEST_ENABLE ) ),
    LOCKED_USERS( "LockedUsers", null ),
    LOCKED_ADDRESSES( "LockedAddresses", null ),
    LOCKED_USERIDS( "LockedUserDNs", null ),
    LOCKED_ATTRIBUTES( "LockedAttributes", null ),
    LOCKED_TOKENDESTS( "LockedTokenDests", null ),
    CAPTCHA_SUCCESSES( "CaptchaSuccessess", null ),
    CAPTCHA_FAILURES( "CaptchaFailures", null ),
    CAPTCHA_PRESENTATIONS( "CaptchaPresentations", null ),
    LDAP_UNAVAILABLE_COUNT( "LdapUnavailableCount", null ),
    DB_UNAVAILABLE_COUNT( "DatabaseUnavailableCount", null ),
    SETUP_RESPONSES( "SetupResponses", null ),
    SETUP_OTP_SECRET( "SetupOtpSecret", null ),
    UPDATE_ATTRIBUTES( "UpdateAttributes", new ConfigSettingDetail( PwmSetting.UPDATE_PROFILE_ENABLE ) ),
    SHORTCUTS_SELECTED( "ShortcutsSelected", new ConfigSettingDetail( PwmSetting.SHORTCUT_ENABLE ) ),
    GENERATED_PASSWORDS( "GeneratedPasswords", null ),
    RECOVERY_SUCCESSES( "RecoverySuccesses", null ),
    RECOVERY_FAILURES( "RecoveryFailures", null ),
    TOKENS_SENT( "TokensSent", null ),
    TOKENS_PASSSED( "TokensPassed", null ),
    RECOVERY_TOKENS_SENT( "RecoveryTokensSent", null ),
    RECOVERY_TOKENS_PASSED( "RecoveryTokensPassed", null ),
    RECOVERY_TOKENS_FAILED( "RecoveryTokensFailed", null ),
    RECOVERY_OTP_PASSED( "RecoveryOTPPassed", null ),
    RECOVERY_OTP_FAILED( "RecoveryOTPFailed", null ),
    PEOPLESEARCH_CACHE_HITS( "PeopleSearchCacheHits", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_CACHE_MISSES( "PeopleSearchCacheMisses", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_SEARCHES( "PeopleSearchSearches", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_DETAILS( "PeopleSearchDetails", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_ORGCHART( "PeopleSearchOrgChart", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PWNOTIFY_JOBS ( "PwNotifyJobs", null ),
    PWNOTIFY_JOB_ERRORS ( "PwNotifyJobErrors", null ),
    PWNOTIFY_EMAILS_SENT ( "PwNotifyJobEmailsSent", null ),
    HELPDESK_PASSWORD_SET( "HelpdeskPasswordSet", null ),
    HELPDESK_USER_LOOKUP( "HelpdeskUserLookup", null ),
    HELPDESK_TOKENS_SENT( "HelpdeskTokenSent", null ),
    HELPDESK_UNLOCK( "HelpdeskUnlock", null ),
    HELPDESK_VERIFY_OTP( "HelpdeskVerifyOTP", null ),
    REST_STATUS( "RestStatus", null ),
    REST_CHECKPASSWORD( "RestCheckPassword", null ),
    REST_SETPASSWORD( "RestSetPassword", null ),
    REST_RANDOMPASSWORD( "RestRandomPassword", null ),
    REST_PROFILE( "RestProfile", null ),
    REST_SIGNING_FORM( "RestSigningForm", null ),
    REST_CHALLENGES( "RestChallenges", null ),
    REST_HEALTH( "RestHealth", null ),
    REST_STATISTICS( "RestStatistics", null ),
    REST_VERIFYCHALLENGES( "RestVerifyChallenges", null ),
    REST_VERIFYOTP( "RestVerifyOTP", null ),
    INTRUDER_ATTEMPTS( "IntruderAttempts", null ),
    FOREIGN_SESSIONS_ACCEPTED( "ForeignSessionsAccepted", null ),
    OBSOLETE_URL_REQUESTS( "ObsoleteUrlRequests", null ),
    SYSLOG_MESSAGES_SENT( "SyslogMessagesSent", null ),;

    private final String key;
    private final StatDetail statDetail;

    Statistic(
            final String key,
            final StatDetail statDetail
    )
    {
        this.key = key;
        this.statDetail = statDetail;
    }

    public String getKey( )
    {
        return key;
    }

    public boolean isActive( final PwmApplication pwmApplication )
    {
        if ( statDetail == null )
        {
            return true;
        }
        return statDetail.isActive( pwmApplication );
    }

    public static SortedSet<Statistic> sortedValues( final Locale locale )
    {
        final Comparator<Statistic> comparator = Comparator.comparing( o -> o.getLabel( locale ) );
        final TreeSet<Statistic> set = new TreeSet<>( comparator );
        set.addAll( Arrays.asList( values() ) );
        return set;
    }

    public String getLabel( final Locale locale )
    {
        final String keyName = Admin.STATISTICS_LABEL_PREFIX + this.getKey();
        return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
    }

    public String getDescription( final Locale locale )
    {
        final String keyName = Admin.STATISTICS_DESCRIPTION_PREFIX + this.getKey();
        return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
    }

    public enum EpsDuration
    {
        MINUTE( TimeDuration.MINUTE ),
        HOUR( TimeDuration.HOUR ),
        DAY( TimeDuration.DAY ),;

        private final TimeDuration timeDuration;

        EpsDuration( final TimeDuration timeDuration )
        {
            this.timeDuration = timeDuration;
        }

        public TimeDuration getTimeDuration( )
        {
            return timeDuration;
        }
    }

    interface StatDetail
    {
        boolean isActive( PwmApplication pwmApplication );
    }

    static class ConfigSettingDetail implements StatDetail
    {
        private final PwmSetting pwmSetting;

        ConfigSettingDetail( final PwmSetting pwmSetting )
        {
            this.pwmSetting = pwmSetting;
        }

        public boolean isActive( final PwmApplication pwmApplication )
        {
            return pwmApplication.getConfig().readSettingAsBoolean( pwmSetting );
        }
    }

    public static Statistic forKey( final String key )
    {
        if ( key == null )
        {
            return null;
        }

        for ( final Statistic stat : values() )
        {
            if ( stat.getKey().equals( key ) )
            {
                return stat;
            }
        }

        return null;
    }
}
