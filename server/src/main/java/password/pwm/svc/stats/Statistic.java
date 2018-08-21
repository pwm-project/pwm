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
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.SortedSet;
import java.util.TreeSet;

public enum Statistic
{
    AUDIT_EVENTS( Type.INCREMENTER, "AuditEvents", null ),
    AUTHENTICATIONS( Type.INCREMENTER, "Authentications", null ),
    AUTHENTICATION_FAILURES( Type.INCREMENTER, "AuthenticationFailures", null ),
    AUTHENTICATION_EXPIRED( Type.INCREMENTER, "Authentications_Expired", null ),
    AUTHENTICATION_PRE_EXPIRED( Type.INCREMENTER, "Authentications_PreExpired", null ),
    AUTHENTICATION_EXPIRED_WARNING( Type.INCREMENTER, "Authentications_ExpiredWarning", null ),
    PWM_STARTUPS( Type.INCREMENTER, "PWM_Startups", null ),
    PWM_UNKNOWN_ERRORS( Type.INCREMENTER, "PWM_UnknownErrors", null ),
    PASSWORD_CHANGES( Type.INCREMENTER, "PasswordChanges", null ),
    FORGOTTEN_USERNAME_FAILURES( Type.INCREMENTER, "ForgottenUsernameFailures", null ),
    FORGOTTEN_USERNAME_SUCCESSES( Type.INCREMENTER, "ForgottenUsernameSuccesses", null ),
    EMAIL_SEND_SUCCESSES( Type.INCREMENTER, "EmailSendSuccesses", null ),
    EMAIL_SEND_FAILURES( Type.INCREMENTER, "EmailSendFailures", null ),
    EMAIL_SEND_DISCARDS( Type.INCREMENTER, "EmailSendDiscards", null ),
    SMS_SEND_SUCCESSES( Type.INCREMENTER, "SmsSendSuccesses", null ),
    SMS_SEND_FAILURES( Type.INCREMENTER, "SmsSendFailures", null ),
    SMS_SEND_DISCARDS( Type.INCREMENTER, "SmsSendDiscards", null ),
    PASSWORD_RULE_CHECKS( Type.INCREMENTER, "PasswordRuleChecks", null ),
    HTTP_REQUESTS( Type.INCREMENTER, "HttpRequests", null ),
    HTTP_RESOURCE_REQUESTS( Type.INCREMENTER, "HttpResourceRequests", null ),
    HTTP_SESSIONS( Type.INCREMENTER, "HttpSessions", null ),
    ACTIVATED_USERS( Type.INCREMENTER, "ActivatedUsers", null ),
    NEW_USERS( Type.INCREMENTER, "NewUsers", new ConfigSettingDetail( PwmSetting.NEWUSER_ENABLE ) ),
    GUESTS( Type.INCREMENTER, "Guests", new ConfigSettingDetail( PwmSetting.GUEST_ENABLE ) ),
    UPDATED_GUESTS( Type.INCREMENTER, "UpdatedGuests", new ConfigSettingDetail( PwmSetting.GUEST_ENABLE ) ),
    LOCKED_USERS( Type.INCREMENTER, "LockedUsers", null ),
    LOCKED_ADDRESSES( Type.INCREMENTER, "LockedAddresses", null ),
    LOCKED_USERIDS( Type.INCREMENTER, "LockedUserDNs", null ),
    LOCKED_ATTRIBUTES( Type.INCREMENTER, "LockedAttributes", null ),
    LOCKED_TOKENDESTS( Type.INCREMENTER, "LockedTokenDests", null ),
    CAPTCHA_SUCCESSES( Type.INCREMENTER, "CaptchaSuccessess", null ),
    CAPTCHA_FAILURES( Type.INCREMENTER, "CaptchaFailures", null ),
    CAPTCHA_PRESENTATIONS( Type.INCREMENTER, "CaptchaPresentations", null ),
    LDAP_UNAVAILABLE_COUNT( Type.INCREMENTER, "LdapUnavailableCount", null ),
    DB_UNAVAILABLE_COUNT( Type.INCREMENTER, "DatabaseUnavailableCount", null ),
    SETUP_RESPONSES( Type.INCREMENTER, "SetupResponses", null ),
    SETUP_OTP_SECRET( Type.INCREMENTER, "SetupOtpSecret", null ),
    UPDATE_ATTRIBUTES( Type.INCREMENTER, "UpdateAttributes", new ConfigSettingDetail( PwmSetting.UPDATE_PROFILE_ENABLE ) ),
    SHORTCUTS_SELECTED( Type.INCREMENTER, "ShortcutsSelected", new ConfigSettingDetail( PwmSetting.SHORTCUT_ENABLE ) ),
    GENERATED_PASSWORDS( Type.INCREMENTER, "GeneratedPasswords", null ),
    RECOVERY_SUCCESSES( Type.INCREMENTER, "RecoverySuccesses", null ),
    RECOVERY_FAILURES( Type.INCREMENTER, "RecoveryFailures", null ),
    TOKENS_SENT( Type.INCREMENTER, "TokensSent", null ),
    TOKENS_PASSSED( Type.INCREMENTER, "TokensPassed", null ),
    RECOVERY_TOKENS_SENT( Type.INCREMENTER, "RecoveryTokensSent", null ),
    RECOVERY_TOKENS_PASSED( Type.INCREMENTER, "RecoveryTokensPassed", null ),
    RECOVERY_TOKENS_FAILED( Type.INCREMENTER, "RecoveryTokensFailed", null ),
    RECOVERY_OTP_PASSED( Type.INCREMENTER, "RecoveryOTPPassed", null ),
    RECOVERY_OTP_FAILED( Type.INCREMENTER, "RecoveryOTPFailed", null ),
    PEOPLESEARCH_CACHE_HITS( Type.INCREMENTER, "PeopleSearchCacheHits", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_CACHE_MISSES( Type.INCREMENTER, "PeopleSearchCacheMisses", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_SEARCHES( Type.INCREMENTER, "PeopleSearchSearches", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_DETAILS( Type.INCREMENTER, "PeopleSearchDetails", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PEOPLESEARCH_ORGCHART( Type.INCREMENTER, "PeopleSearchOrgChart", new ConfigSettingDetail( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    PWNOTIFY_JOBS ( Type.INCREMENTER, "PwNotifyJobs", null ),
    PWNOTIFY_JOB_ERRORS ( Type.INCREMENTER, "PwNotifyJobErrors", null ),
    PWNOTIFY_EMAILS_SENT ( Type.INCREMENTER, "PwNotifyJobEmailsSent", null ),
    HELPDESK_PASSWORD_SET( Type.INCREMENTER, "HelpdeskPasswordSet", null ),
    HELPDESK_USER_LOOKUP( Type.INCREMENTER, "HelpdeskUserLookup", null ),
    HELPDESK_TOKENS_SENT( Type.INCREMENTER, "HelpdeskTokenSent", null ),
    HELPDESK_UNLOCK( Type.INCREMENTER, "HelpdeskUnlock", null ),
    HELPDESK_VERIFY_OTP( Type.INCREMENTER, "HelpdeskVerifyOTP", null ),
    REST_STATUS( Type.INCREMENTER, "RestStatus", null ),
    REST_CHECKPASSWORD( Type.INCREMENTER, "RestCheckPassword", null ),
    REST_SETPASSWORD( Type.INCREMENTER, "RestSetPassword", null ),
    REST_RANDOMPASSWORD( Type.INCREMENTER, "RestRandomPassword", null ),
    REST_PROFILE( Type.INCREMENTER, "RestProfile", null ),
    REST_SIGNING_FORM( Type.INCREMENTER, "RestSigningForm", null ),
    REST_CHALLENGES( Type.INCREMENTER, "RestChallenges", null ),
    REST_HEALTH( Type.INCREMENTER, "RestHealth", null ),
    REST_STATISTICS( Type.INCREMENTER, "RestStatistics", null ),
    REST_VERIFYCHALLENGES( Type.INCREMENTER, "RestVerifyChallenges", null ),
    REST_VERIFYOTP( Type.INCREMENTER, "RestVerifyOTP", null ),
    INTRUDER_ATTEMPTS( Type.INCREMENTER, "IntruderAttempts", null ),
    FOREIGN_SESSIONS_ACCEPTED( Type.INCREMENTER, "ForeignSessionsAccepted", null ),
    OBSOLETE_URL_REQUESTS( Type.INCREMENTER, "ObsoleteUrlRequests", null ),
    SYSLOG_MESSAGES_SENT( Type.INCREMENTER, "SyslogMessagesSent", null ),

    AVG_PASSWORD_SYNC_TIME( Type.AVERAGE, "AvgPasswordSyncTime", null ),
    AVG_AUTHENTICATION_TIME( Type.AVERAGE, "AvgAuthenticationTime", null ),
    AVG_PASSWORD_STRENGTH( Type.AVERAGE, "AvgPasswordStrength", null ),
    AVG_LDAP_SEARCH_TIME( Type.AVERAGE, "AvgLdapSearchTime", null ),;

    private static final PwmLogger LOGGER = PwmLogger.forClass( Statistic.class );
    private final Type type;
    private final String key;
    private final StatDetail statDetail;

    Statistic(
            final Type type,
            final String key,
            final StatDetail statDetail
    )
    {
        this.type = type;
        this.key = key;
        this.statDetail = statDetail;
    }

    public String getKey( )
    {
        return key;
    }

    public Type getType( )
    {
        return type;
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

    public enum Type
    {
        INCREMENTER,
        AVERAGE,
    }

    public String getLabel( final Locale locale )
    {
        try
        {
            final String keyName = Admin.STATISTICS_LABEL_PREFIX + this.getKey();
            return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
        }
        catch ( MissingResourceException e )
        {
            return "MISSING STATISTIC LABEL for " + this.getKey();
        }
    }

    public String getDescription( final Locale locale )
    {
        final String keyName = Admin.STATISTICS_DESCRIPTION_PREFIX + this.getKey();
        try
        {
            return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
        }
        catch ( Exception e )
        {
            LOGGER.error( "unable to load localization for " + keyName + ", error: " + e.getMessage() );
            return "missing localization for " + keyName;
        }
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
