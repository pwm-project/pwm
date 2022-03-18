/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.ldap.UserInfo;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

public class IdleTimeoutCalculator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( IdleTimeoutCalculator.class );

    public static MaxIdleTimeoutResult figureMaxSessionTimeout( final PwmDomain pwmDomain, final PwmSession pwmSession )
            throws PwmUnrecoverableException
    {
        final DomainConfig domainConfig = pwmDomain.getConfig();
        final SortedSet<MaxIdleTimeoutResult> results = new TreeSet<>();
        {
            final long idleSetting = domainConfig.readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
            results.add( new MaxIdleTimeoutResult(
                    MaxIdleTimeoutResult.reasonFor( PwmSetting.IDLE_TIMEOUT_SECONDS, null ),
                    TimeDuration.of( idleSetting, TimeDuration.Unit.SECONDS ) ) );
        }

        if ( !pwmSession.isAuthenticated() )
        {
            if ( pwmDomain.getApplicationMode() == PwmApplicationMode.NEW )
            {
                final long configGuideIdleTimeout = Long.parseLong( domainConfig.readAppProperty( AppProperty.CONFIG_GUIDE_IDLE_TIMEOUT ) );
                results.add( new MaxIdleTimeoutResult(
                        () -> "Configuration Guide Idle Timeout",
                        TimeDuration.of( configGuideIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
            }

            if ( domainConfig.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC ) )
            {
                final Optional<PeopleSearchProfile> optionalPeopleSearchProfile = domainConfig.getPublicPeopleSearchProfile();
                if ( optionalPeopleSearchProfile.isPresent() )
                {
                    final PeopleSearchProfile publicProfile = optionalPeopleSearchProfile.get();
                    final long peopleSearchIdleTimeout = publicProfile.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS );
                    if ( peopleSearchIdleTimeout > 0 )
                    {
                        results.add( new MaxIdleTimeoutResult(
                                MaxIdleTimeoutResult.reasonFor( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS, publicProfile.getIdentifier() ),
                                TimeDuration.of( peopleSearchIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
                    }
                }
            }
        }
        else
        {
            final UserInfo userInfo = pwmSession.getUserInfo();
            final boolean userIsAdmin = pwmSession.isAuthenticated()
                    && pwmSession.getSessionManager().checkPermission( pwmDomain, Permission.PWMADMIN );
            final Set<MaxIdleTimeoutResult> loggedInResults = figureMaxAuthUserTimeout( domainConfig, userInfo, userIsAdmin );
            results.addAll( loggedInResults );
        }

        return results.last();
    }

    private static Set<MaxIdleTimeoutResult> figureMaxAuthUserTimeout(
            final DomainConfig domainConfig,
            final UserInfo userInfo,
            final boolean userIsAdmin
    )
            throws PwmUnrecoverableException
    {
        final Set<MaxIdleTimeoutResult> results = new TreeSet<>();
        {
            final long idleSecondsSetting = domainConfig.readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
            results.add( new MaxIdleTimeoutResult(
                    MaxIdleTimeoutResult.reasonFor( PwmSetting.IDLE_TIMEOUT_SECONDS, null ),
                    TimeDuration.of( idleSecondsSetting, TimeDuration.Unit.SECONDS ) ) );
        }

        if ( domainConfig.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE ) )
        {
            final String helpdeskProfileID = userInfo.getProfileIDs().get( ProfileDefinition.Helpdesk );
            if ( StringUtil.notEmpty( helpdeskProfileID ) )
            {
                final HelpdeskProfile helpdeskProfile = domainConfig.getHelpdeskProfiles().get( helpdeskProfileID );
                final long helpdeskIdleTimeout = helpdeskProfile.readSettingAsLong( PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS );
                results.add( new MaxIdleTimeoutResult(
                        MaxIdleTimeoutResult.reasonFor( PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS, helpdeskProfileID ),
                        TimeDuration.of( helpdeskIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
            }
        }

        if ( domainConfig.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
        {
            final String peopleSearchID = userInfo.getProfileIDs().get( ProfileDefinition.PeopleSearch );
            if ( StringUtil.notEmpty( peopleSearchID ) )
            {
                final PeopleSearchProfile peopleSearchProfile = domainConfig.getPeopleSearchProfiles().get( peopleSearchID );
                final long peopleSearchIdleTimeout = peopleSearchProfile.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS );
                if ( peopleSearchIdleTimeout > 0 )
                {
                    results.add( new MaxIdleTimeoutResult(
                            MaxIdleTimeoutResult.reasonFor( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS, peopleSearchID ),
                            TimeDuration.of( peopleSearchIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
                }
            }
        }

        if ( userIsAdmin )
        {
            final long configEditorIdleTimeout = Long.parseLong( domainConfig.readAppProperty( AppProperty.CONFIG_EDITOR_IDLE_TIMEOUT ) );
            results.add( new MaxIdleTimeoutResult(
                    () -> "Config Editor Idle Timeout",
                    TimeDuration.of( configEditorIdleTimeout, TimeDuration.Unit.SECONDS ) ) );
        }

        return Collections.unmodifiableSet( results );
    }

    @Value
    static class MaxIdleTimeoutResult implements Comparable<MaxIdleTimeoutResult>
    {
        private final Supplier<String> reason;
        private final TimeDuration idleTimeout;

        @Override
        public int compareTo( final MaxIdleTimeoutResult o )
        {
            return this.idleTimeout.compareTo( o.getIdleTimeout() );
        }

        static Supplier<String> reasonFor( final PwmSetting pwmSetting, final String profileID )
        {
            return () -> "Setting " + pwmSetting.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE );
        }
    }

    public static TimeDuration idleTimeoutForRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmURL pwmURL = pwmRequest.getURL();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if ( pwmURL.isResourceURL() )
        {
            return figureMaxSessionTimeout( pwmDomain, pwmSession ).getIdleTimeout();
        }

        for ( final IdleTimeoutCalculatorModule module : SERVLET_IDLE_CALCULATORS )
        {
            for ( final PwmServletDefinition pwmServletDefinition : module.forServlets() )
            {
                if ( pwmURL.matches( pwmServletDefinition ) )
                {
                    final Optional<TimeDuration> calculatedDuration = module.calculate( pwmRequest );
                    if ( calculatedDuration.isPresent() )
                    {
                        return calculatedDuration.get();
                    }
                }
            }
        }

        final long idleTimeout = pwmRequest.getDomainConfig().readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
        return TimeDuration.of( idleTimeout, TimeDuration.Unit.SECONDS );
    }

    private static final List<IdleTimeoutCalculatorModule> SERVLET_IDLE_CALCULATORS = List.of(
            new ConfigGuideIdleCalculator(),
            new ConfigEditorIdleCalculator(),
            new HelpdeskIdleCalculator(),
            new PublicPeopleSearchIdleCalculator(),
            new PrivatePeopleSearchIdleCalculator() );


    interface IdleTimeoutCalculatorModule
    {
        List<PwmServletDefinition> forServlets();

        Optional<TimeDuration> calculate( PwmRequest pwmRequest ) throws PwmUnrecoverableException;
    }

    static class ConfigGuideIdleCalculator implements IdleTimeoutCalculatorModule
    {
        @Override
        public List<PwmServletDefinition> forServlets()
        {
            return Collections.singletonList( PwmServletDefinition.ConfigGuide );
        }

        @Override
        public Optional<TimeDuration> calculate( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            if ( pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.NEW )
            {
                final long configGuideIdleTimeout = Long.parseLong( pwmRequest.getDomainConfig().readAppProperty( AppProperty.CONFIG_GUIDE_IDLE_TIMEOUT ) );
                if ( configGuideIdleTimeout > 0 )
                {
                    return Optional.of( TimeDuration.of( configGuideIdleTimeout, TimeDuration.Unit.SECONDS ) );
                }
            }
            return Optional.empty();
        }
    }

    static class ConfigEditorIdleCalculator implements IdleTimeoutCalculatorModule
    {
        @Override
        public List<PwmServletDefinition> forServlets()
        {
            return Collections.singletonList( PwmServletDefinition.ConfigEditor );
        }

        @Override
        public Optional<TimeDuration> calculate( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            final long configEditorIdleTimeout = Long.parseLong( pwmRequest.getDomainConfig().readAppProperty( AppProperty.CONFIG_EDITOR_IDLE_TIMEOUT ) );
            if ( configEditorIdleTimeout > 0 )
            {
                return Optional.of( TimeDuration.of( configEditorIdleTimeout, TimeDuration.Unit.SECONDS ) );
            }
            return Optional.empty();
        }
    }

    static class HelpdeskIdleCalculator implements IdleTimeoutCalculatorModule
    {
        @Override
        public List<PwmServletDefinition> forServlets()
        {
            return Collections.singletonList( PwmServletDefinition.Helpdesk );
        }

        @Override
        public Optional<TimeDuration> calculate( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                return Optional.empty();
            }

            if ( !pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE ) )
            {
                return Optional.empty();
            }

            final HelpdeskProfile helpdeskProfile = pwmRequest.getHelpdeskProfile( );
            if ( helpdeskProfile != null )
            {
                final long helpdeskIdleTimeout = helpdeskProfile.readSettingAsLong( PwmSetting.HELPDESK_IDLE_TIMEOUT_SECONDS );
                if ( helpdeskIdleTimeout > 0 )
                {
                    return Optional.of( TimeDuration.of( helpdeskIdleTimeout, TimeDuration.Unit.SECONDS ) );
                }
            }

            return Optional.empty();
        }
    }

    static class PublicPeopleSearchIdleCalculator implements IdleTimeoutCalculatorModule
    {
        @Override
        public List<PwmServletDefinition> forServlets()
        {
            return Collections.singletonList( PwmServletDefinition.PublicPeopleSearch );
        }

        @Override
        public Optional<TimeDuration> calculate( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            if ( pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC ) )
            {
                final PeopleSearchProfile peopleSearchProfile = pwmRequest.getDomainConfig().getPublicPeopleSearchProfile().orElseThrow(
                        () -> PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, "public peoplesearch profile not assigned" ) );

                final long peopleSearchIdleTimeout = peopleSearchProfile.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS );
                if ( peopleSearchIdleTimeout > 0 )
                {
                    return Optional.of( TimeDuration.of( peopleSearchIdleTimeout, TimeDuration.Unit.SECONDS ) );
                }
            }

            return Optional.empty();
        }
    }


    static class PrivatePeopleSearchIdleCalculator implements IdleTimeoutCalculatorModule
    {
        @Override
        public List<PwmServletDefinition> forServlets()
        {
            return Collections.singletonList( PwmServletDefinition.PrivatePeopleSearch );
        }

        @Override
        public Optional<TimeDuration> calculate( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                return Optional.empty();
            }

            if ( !pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
            {
                return Optional.empty();
            }

            final PeopleSearchProfile peopleSearchProfile = pwmRequest.getPeopleSearchProfile();
            if ( peopleSearchProfile != null )
            {
                final long peopleSearchIdleTimeout = peopleSearchProfile.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_IDLE_TIMEOUT_SECONDS );
                if ( peopleSearchIdleTimeout > 0 )
                {
                    return Optional.of( TimeDuration.of( peopleSearchIdleTimeout, TimeDuration.Unit.SECONDS ) );
                }
            }

            return Optional.empty();
        }
    }
}
