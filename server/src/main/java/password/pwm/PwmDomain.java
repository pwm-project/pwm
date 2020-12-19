/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.http.servlet.peoplesearch.PeopleSearchService;
import password.pwm.http.servlet.resource.ResourceServletService;
import password.pwm.http.state.SessionStateService;
import password.pwm.ldap.LdapConnectionService;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.PwmService;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.email.EmailService;
import password.pwm.svc.event.AuditService;
import password.pwm.svc.httpclient.HttpClientService;
import password.pwm.svc.intruder.IntruderManager;
import password.pwm.svc.node.NodeService;
import password.pwm.svc.pwnotify.PwNotifyService;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.sessiontrack.SessionTrackService;
import password.pwm.svc.shorturl.UrlShortenerService;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.wordlist.SeedlistService;
import password.pwm.svc.wordlist.SharedHistoryManager;
import password.pwm.svc.wordlist.WordlistService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseService;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.OtpService;
import password.pwm.util.queue.SmsQueueManager;
import password.pwm.util.secure.SecureService;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public PwmDomain( final PwmApplication pwmApplication, final DomainID domainID )
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.domainID = Objects.requireNonNull( domainID );
    }

    public DomainConfig getConfig( )
    {
        return pwmApplication.getConfig().getDomainConfigs().get( domainID );
    }

    public PwmApplicationMode getApplicationMode( )
    {
        return pwmApplication.getApplicationMode();
    }

    public DatabaseAccessor getDatabaseAccessor( )

            throws PwmUnrecoverableException
    {
        return pwmApplication.getDatabaseAccessor();
    }

    public DatabaseService getDatabaseService( )
    {
        return pwmApplication.getDatabaseService();
    }

    public StatisticsManager getStatisticsManager( )
    {
        return pwmApplication.getStatisticsManager();
    }

    public OtpService getOtpService( )
    {
        return pwmApplication.getOtpService();
    }

    public CrService getCrService( )
    {
        return pwmApplication.getCrService();
    }

    public SessionStateService getSessionStateService( )
    {
        return pwmApplication.getSessionStateService();
    }

    public CacheService getCacheService( )
    {
        return pwmApplication.getCacheService();
    }

    public SecureService getSecureService( )
    {
        return pwmApplication.getSecureService();
    }




    public Instant getStartupTime( )
    {
        return pwmApplication.getStartupTime();
    }

    public Instant getInstallTime( )
    {
        return pwmApplication.getInstallTime();
    }

    public LocalDB getLocalDB( )
    {
        return pwmApplication.getLocalDB();
    }

    public PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }


    public PwmEnvironment getPwmEnvironment( )
    {
        return pwmApplication.getPwmEnvironment();
    }

    public String getRuntimeNonce( )
    {
        return pwmApplication.getRuntimeNonce();
    }

    public <T extends Serializable> Optional<T> readAppAttribute( final AppAttribute appAttribute, final Class<T> returnClass )
    {
       return getPwmApplication().readAppAttribute( appAttribute, returnClass );
    }

    public void writeAppAttribute( final AppAttribute appAttribute, final Serializable value )
    {
        getPwmApplication().writeAppAttribute( appAttribute, value );
    }

    public File getTempDirectory( ) throws PwmUnrecoverableException
    {
       return pwmApplication.getTempDirectory();
    }

    public boolean determineIfDetailErrorMsgShown( )
    {
        return pwmApplication.determineIfDetailErrorMsgShown();
    }

    public PwmScheduler getPwmScheduler()
    {
        return pwmApplication.getPwmScheduler();
    }

    public LdapConnectionService getLdapConnectionService()
    {
        return pwmApplication.getLdapConnectionService();
    }

    public AuditService getAuditManager()
    {
        return pwmApplication.getAuditManager();
    }

    public String getInstanceID()
    {
        return pwmApplication.getInstanceID();
    }

    public EmailService getEmailQueue()
    {
        return pwmApplication.getEmailQueue();
    }

    public SessionTrackService getSessionTrackService()
    {
        return pwmApplication.getSessionTrackService();
    }

    public ChaiProvider getProxyChaiProvider( final String ldapProfileID )
            throws PwmUnrecoverableException
    {
        return pwmApplication.getProxyChaiProvider( ldapProfileID );
    }

    public ChaiUser getProxiedChaiUser( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        return pwmApplication.getProxiedChaiUser( userIdentity );
    }

    public List<PwmService> getPwmServices( )
    {
        return pwmApplication.getPwmServices();
    }

    public UserSearchEngine getUserSearchEngine()
    {
        return pwmApplication.getUserSearchEngine();
    }

    public UrlShortenerService getUrlShortener()
    {
        return pwmApplication.getUrlShortener();
    }

    public HttpClientService getHttpClientService()
    {
        return pwmApplication.getHttpClientService();
    }

    public NodeService getClusterService()
    {
        return pwmApplication.getClusterService();
    }

    public IntruderManager getIntruderManager()
    {
        return pwmApplication.getIntruderManager();
    }

    public TokenService getTokenService()
    {
        return pwmApplication.getTokenService();
    }

    public void sendSmsUsingQueue( final String smsNumber, final String modifiedMessage, final SessionLabel sessionLabel, final MacroRequest macroRequest )
    {
        pwmApplication.sendSmsUsingQueue( smsNumber, modifiedMessage, sessionLabel, macroRequest );
    }

    public WordlistService getWordlistService()
    {
        return pwmApplication.getWordlistService();
    }

    public LocalDBLogger getLocalDBLogger()
    {
        return pwmApplication.getLocalDBLogger();
    }

    public SharedHistoryManager getSharedHistoryManager()
    {
        return pwmApplication.getSharedHistoryManager();
    }

    public SeedlistService getSeedlistManager()
    {
        return pwmApplication.getSeedlistManager();
    }

    public SmsQueueManager getSmsQueue()
    {
        return pwmApplication.getSmsQueue();
    }

    public ErrorInformation getLastLocalDBFailure()
    {
        return pwmApplication.getLastLocalDBFailure();
    }

    public HealthMonitor getHealthMonitor()
    {
        return pwmApplication.getHealthMonitor();
    }

    public ReportService getReportService()
    {
        return pwmApplication.getReportService();
    }

    public PeopleSearchService getPeopleSearchService()
    {
        return pwmApplication.getPeopleSearchService();
    }

    public PwNotifyService getPwNotifyService()
    {
        return pwmApplication.getPwNotifyService();
    }

    public ResourceServletService getResourceServletService()
    {
        return pwmApplication.getResourceServletService();
    }

    public void shutdown()
    {

    }
}



