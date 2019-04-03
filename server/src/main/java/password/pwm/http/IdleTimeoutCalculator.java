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

package password.pwm.http;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.ProfileType;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.ldap.UserInfo;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class IdleTimeoutCalculator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( IdleTimeoutCalculator.class );

    public static MaxIdleTimeoutResult figureMaxSessionTimeout( final PwmApplication pwmApplication, final PwmSession pwmSession )
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();
        final SortedSet<MaxIdleTimeoutResult> results = new TreeSet<>();
        {
            final long idleSetting = configuration.readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
            results.add( new MaxIdleTimeoutResult(
                    MaxIdleTimeoutResult.reasonFor( PwmSetting.IDLE_TIMEOUT_SECONDS, null ),
                    TimeDuration.of( idleSetting, TimeDuration.Unit.SECONDS ) ) );
        }

        if ( !pwmSession.isAuthenticated() )
        {
            if ( pwmApplication.getApplicationMode() == PwmApplicationMode.NEW )
            {
                final long configGuideIdleTimeout = Long.parseLong( configuration.readAppProperty( AppProperty.CONFIG_GUIDE_IDLE_TIMEOUT ) );
                results.add( new MaxIdleTimeoutResult(
                        "Configuration Guide Idle Timeout",
                        TimeDuration.of( configGuideIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
            }

            if ( configuration.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC ) )
            {
                final long peopleSearchIdleTimeout = configuration.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS );
                if ( peopleSearchIdleTimeout > 0 )
                {
                    results.add( new MaxIdleTimeoutResult(
                            MaxIdleTimeoutResult.reasonFor( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS, null ),
                            TimeDuration.of( peopleSearchIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
                }
            }

        }
        else
        {
            final UserInfo userInfo = pwmSession.getUserInfo();
            final boolean userIsAdmin = pwmSession.isAuthenticated()
                    && pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.PWMADMIN );
            final Set<MaxIdleTimeoutResult> loggedInResults = figureMaxAuthUserTimeout( configuration, userInfo, userIsAdmin );
            results.addAll( loggedInResults );
        }

        return results.last();
    }

    private static Set<MaxIdleTimeoutResult> figureMaxAuthUserTimeout(
            final Configuration configuration,
            final UserInfo userInfo,
            final boolean userIsAdmin
    )
            throws PwmUnrecoverableException
    {
        final Set<MaxIdleTimeoutResult> results = new TreeSet<>();
        {
            final long idleSecondsSetting = configuration.readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
            results.add( new MaxIdleTimeoutResult(
                    MaxIdleTimeoutResult.reasonFor( PwmSetting.IDLE_TIMEOUT_SECONDS, null ),
                    TimeDuration.of( idleSecondsSetting, TimeDuration.Unit.SECONDS ) ) );
        }

        if ( configuration.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE ) )
        {
            final String helpdeskProfileID = userInfo.getProfileIDs().get( ProfileType.Helpdesk );
            if ( !StringUtil.isEmpty( helpdeskProfileID ) )
            {
                final HelpdeskProfile helpdeskProfile = configuration.getHelpdeskProfiles().get( helpdeskProfileID );
                final long helpdeskIdleTimeout = helpdeskProfile.readSettingAsLong( PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS );
                results.add( new MaxIdleTimeoutResult(
                        MaxIdleTimeoutResult.reasonFor( PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS, helpdeskProfileID ),
                        TimeDuration.of( helpdeskIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
            }
        }

        if ( configuration.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
        {
            final long peopleSearchIdleTimeout = configuration.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS );
            if ( peopleSearchIdleTimeout > 0 )
            {
                results.add( new MaxIdleTimeoutResult(
                        MaxIdleTimeoutResult.reasonFor( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS, null ),
                        TimeDuration.of( peopleSearchIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
            }
        }

        if ( userIsAdmin )
        {
            final long configEditorIdleTimeout = Long.parseLong( configuration.readAppProperty( AppProperty.CONFIG_EDITOR_IDLE_TIMEOUT ) );
            results.add( new MaxIdleTimeoutResult(
                    "Config Editor Idle Timeout",
                    TimeDuration.of( configEditorIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
        }

        return Collections.unmodifiableSet( results );
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    static class MaxIdleTimeoutResult implements Comparable<MaxIdleTimeoutResult>
    {
        private final String reason;
        private final TimeDuration idleTimeout;

        @Override
        public int compareTo( final MaxIdleTimeoutResult o )
        {
            return this.idleTimeout.compareTo( o.getIdleTimeout() );
        }

        static String reasonFor( final PwmSetting pwmSetting, final String profileID )
        {
            return "Setting " + pwmSetting.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE );
        }
    }

    public static TimeDuration idleTimeoutForRequest( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return idleTimeoutForRequest( pwmRequest.getURL(), pwmRequest.getPwmApplication(), pwmRequest.getPwmSession() );
    }

    public static TimeDuration idleTimeoutForRequest( final PwmURL pwmURL, final PwmApplication pwmApplication, final PwmSession pwmSession ) throws PwmUnrecoverableException
    {
        if ( pwmURL.isResourceURL() )
        {
            return figureMaxSessionTimeout( pwmApplication, pwmSession ).getIdleTimeout();
        }

        final Configuration config = pwmApplication.getConfig();
        if ( pwmURL.isPwmServletURL( PwmServletDefinition.Helpdesk ) )
        {
            if ( config.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE ) )
            {
                final HelpdeskProfile helpdeskProfile = pwmSession.getSessionManager().getHelpdeskProfile( pwmApplication );
                if ( helpdeskProfile != null )
                {
                    final long helpdeskIdleTimeout = helpdeskProfile.readSettingAsLong( PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS );
                    if ( helpdeskIdleTimeout > 0 )
                    {
                        return TimeDuration.of( helpdeskIdleTimeout, TimeDuration.Unit.SECONDS );
                    }
                }
            }
        }

        if (
                (
                        pwmURL.isPwmServletURL( PwmServletDefinition.PrivatePeopleSearch )
                                || pwmURL.isPwmServletURL( PwmServletDefinition.PublicPeopleSearch )
                )
                        && pwmURL.isPrivateUrl()
                )
        {
            if ( config.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
            {
                final long peopleSearchIdleTimeout = config.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS );
                if ( peopleSearchIdleTimeout > 0 )
                {
                    return TimeDuration.of( peopleSearchIdleTimeout, TimeDuration.Unit.SECONDS );
                }
            }
        }

        if ( pwmURL.isPwmServletURL( PwmServletDefinition.ConfigEditor ) )
        {
            try
            {
                if ( pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.PWMADMIN ) )
                {
                    final long configEditorIdleTimeout = Long.parseLong( config.readAppProperty( AppProperty.CONFIG_EDITOR_IDLE_TIMEOUT ) );
                    if ( configEditorIdleTimeout > 0 )
                    {
                        return TimeDuration.of( configEditorIdleTimeout, TimeDuration.Unit.SECONDS );
                    }
                }
            }
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( pwmSession, "error while figuring max idle timeout for session: " + e.getMessage() );
            }
        }

        if ( pwmURL.isPwmServletURL( PwmServletDefinition.ConfigGuide ) )
        {
            if ( pwmApplication.getApplicationMode() == PwmApplicationMode.NEW )
            {
                final long configGuideIdleTimeout = Long.parseLong( config.readAppProperty( AppProperty.CONFIG_GUIDE_IDLE_TIMEOUT ) );
                if ( configGuideIdleTimeout > 0 )
                {
                    return TimeDuration.of( configGuideIdleTimeout, TimeDuration.Unit.SECONDS );
                }
            }
        }

        final long idleTimeout = config.readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
        return TimeDuration.of( idleTimeout, TimeDuration.Unit.SECONDS );
    }
}
