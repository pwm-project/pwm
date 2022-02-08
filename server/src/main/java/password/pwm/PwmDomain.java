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

package password.pwm;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSettingScope;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.peoplesearch.PeopleSearchService;
import password.pwm.http.servlet.resource.ResourceServletService;
import password.pwm.http.state.SessionStateService;
import password.pwm.ldap.LdapConnectionService;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.PwmService;
import password.pwm.svc.PwmServiceEnum;
import password.pwm.svc.PwmServiceManager;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.event.AuditService;
import password.pwm.svc.httpclient.HttpClientService;
import password.pwm.svc.intruder.IntruderDomainService;
import password.pwm.svc.pwnotify.PwNotifyService;
import password.pwm.svc.secure.DomainSecureService;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.userhistory.UserHistoryService;
import password.pwm.svc.wordlist.SharedHistoryService;
import password.pwm.util.DailySummaryJob;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.svc.cr.CrService;
import password.pwm.svc.otp.OtpService;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * A repository for objects common to the servlet context.  A singleton
 * of this object is stored in the servlet context.
 *
 * @author Jason D. Rivard
 */
public class PwmDomain
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmDomain.class );

    private final PwmApplication pwmApplication;
    private final DomainID domainID;

    private final PwmServiceManager pwmServiceManager;

    public PwmDomain( final PwmApplication pwmApplication, final DomainID domainID )
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.domainID = Objects.requireNonNull( domainID );

        final SessionLabel sessionLabel = SessionLabel.builder().domain( domainID.stringValue() ).build();
        this.pwmServiceManager = new PwmServiceManager( sessionLabel, pwmApplication, domainID, PwmServiceEnum.forScope( PwmSettingScope.DOMAIN ) );
    }

    public void initialize()
            throws PwmUnrecoverableException

    {
        final Instant startTime = Instant.now();
        LOGGER.trace( () -> "initializing domain " + domainID.stringValue() );
        pwmServiceManager.initAllServices();

        {
            final ExecutorService executorService = PwmScheduler.makeSingleThreadExecutorService( getPwmApplication(), DailySummaryJob.class );
            pwmApplication.getPwmScheduler().scheduleDailyZuluZeroStartJob( new DailySummaryJob( this ), executorService, TimeDuration.ZERO );
        }

        LOGGER.trace( () -> "completed initializing domain " + domainID.stringValue(), () -> TimeDuration.fromCurrent( startTime ) );
    }

    public DomainConfig getConfig( )
    {
        return pwmApplication.getConfig().getDomainConfigs().get( domainID );
    }

    public PwmApplicationMode getApplicationMode( )
    {
        return pwmApplication.getApplicationMode();
    }

    public StatisticsService getStatisticsManager( )
    {
        return pwmApplication.getStatisticsManager();
    }

    public OtpService getOtpService( )
    {
        return ( OtpService ) pwmServiceManager.getService( PwmServiceEnum.OtpService );
    }

    public CrService getCrService( )
    {
        return ( CrService ) pwmServiceManager.getService( PwmServiceEnum.CrService );
    }

    public SessionStateService getSessionStateService( )
    {
        return pwmApplication.getSessionStateService();
    }

    public CacheService getCacheService( )
    {
        return pwmApplication.getCacheService();
    }

    public DomainSecureService getSecureService( )
    {
        return ( DomainSecureService ) pwmServiceManager.getService( PwmServiceEnum.DomainSecureService );
    }

    public PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }

    public boolean determineIfDetailErrorMsgShown( )
    {
        return pwmApplication.determineIfDetailErrorMsgShown();
    }

    public LdapConnectionService getLdapConnectionService( )
    {
        return ( LdapConnectionService ) pwmServiceManager.getService( PwmServiceEnum.LdapConnectionService );
    }

    public AuditService getAuditService()
    {
        return pwmApplication.getAuditService();
    }

    public SessionTrackService getSessionTrackService()
    {
        return pwmApplication.getSessionTrackService();
    }

    public ChaiUser getProxiedChaiUser( final SessionLabel sessionLabel, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        try
        {
            final ChaiProvider proxiedProvider = getProxyChaiProvider( sessionLabel, userIdentity.getLdapProfileID() );
            return proxiedProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    public ChaiProvider getProxyChaiProvider( final SessionLabel sessionLabel, final String identifier )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( identifier );
        return getLdapConnectionService().getProxyChaiProvider( sessionLabel, identifier );
    }

    public List<PwmService> getPwmServices( )
    {
        return pwmServiceManager.getRunningServices();
    }

    public UserSearchEngine getUserSearchEngine()
    {
        return ( UserSearchEngine ) pwmServiceManager.getService( PwmServiceEnum.UserSearchEngine );
    }

    public HttpClientService getHttpClientService()
    {
        return pwmApplication.getHttpClientService();
    }

    public IntruderDomainService getIntruderService()
    {
        return ( IntruderDomainService ) pwmServiceManager.getService( PwmServiceEnum.IntruderDomainService );
    }

    public TokenService getTokenService()
    {
        return ( TokenService ) pwmServiceManager.getService( PwmServiceEnum.TokenService );
    }

    public SharedHistoryService getSharedHistoryManager()
    {
        return pwmApplication.getSharedHistoryManager();
    }

    public PeopleSearchService getPeopleSearchService( )
    {
        return ( PeopleSearchService ) pwmServiceManager.getService( PwmServiceEnum.PeopleSearchService );
    }

    public PwNotifyService getPwNotifyService()
    {
        return ( PwNotifyService ) pwmServiceManager.getService( PwmServiceEnum.PwExpiryNotifyService );
    }

    public ResourceServletService getResourceServletService( )
    {
        return ( ResourceServletService ) pwmServiceManager.getService( PwmServiceEnum.ResourceServletService );
    }

    public UserHistoryService getUserHistoryService()
    {
        return ( UserHistoryService ) pwmServiceManager.getService( PwmServiceEnum.UserHistoryService );
    }

    public void shutdown()
    {
        LOGGER.trace( () -> "beginning shutdown domain " + domainID.stringValue() );
        pwmServiceManager.shutdownAllServices();
    }

    public DomainID getDomainID()
    {
        return domainID;
    }
}



