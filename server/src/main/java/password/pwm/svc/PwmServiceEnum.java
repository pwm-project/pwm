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

package password.pwm.svc;

import password.pwm.svc.email.EmailService;
import password.pwm.svc.node.NodeService;
import password.pwm.svc.pwnotify.PwNotifyService;
import password.pwm.svc.wordlist.SeedlistService;
import password.pwm.svc.wordlist.WordlistService;
import password.pwm.util.java.JavaHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public enum PwmServiceEnum
{
    LocalDBService( password.pwm.util.localdb.LocalDBService.class, Flag.StartDuringRuntimeInstance ),
    SecureService( password.pwm.util.secure.SecureService.class, Flag.StartDuringRuntimeInstance ),
    HttpClientService( password.pwm.svc.httpclient.HttpClientService.class, Flag.StartDuringRuntimeInstance ),
    LdapConnectionService( password.pwm.ldap.LdapConnectionService.class, Flag.StartDuringRuntimeInstance ),
    DatabaseService( password.pwm.util.db.DatabaseService.class, Flag.StartDuringRuntimeInstance ),
    SharedHistoryManager( password.pwm.svc.wordlist.SharedHistoryManager.class ),
    AuditService( password.pwm.svc.event.AuditService.class ),
    StatisticsManager( password.pwm.svc.stats.StatisticsManager.class, Flag.StartDuringRuntimeInstance ),
    WordlistManager( WordlistService.class, Flag.StartDuringRuntimeInstance ),
    SeedlistManager( SeedlistService.class ),
    EmailQueueManager( EmailService.class, Flag.StartDuringRuntimeInstance ),
    SmsQueueManager( password.pwm.util.queue.SmsQueueManager.class ),
    UrlShortenerService( password.pwm.svc.shorturl.UrlShortenerService.class ),
    TokenService( password.pwm.svc.token.TokenService.class, Flag.StartDuringRuntimeInstance ),
    IntruderManager( password.pwm.svc.intruder.IntruderManager.class ),
    CrService( password.pwm.util.operations.CrService.class, Flag.StartDuringRuntimeInstance ),
    OtpService( password.pwm.util.operations.OtpService.class ),
    CacheService( password.pwm.svc.cache.CacheService.class, Flag.StartDuringRuntimeInstance ),
    HealthMonitor( password.pwm.health.HealthMonitor.class ),
    ReportService( password.pwm.svc.report.ReportService.class, Flag.StartDuringRuntimeInstance ),
    ResourceServletService( password.pwm.http.servlet.resource.ResourceServletService.class ),
    SessionTrackService( password.pwm.svc.sessiontrack.SessionTrackService.class ),
    SessionStateSvc( password.pwm.http.state.SessionStateService.class ),
    UserSearchEngine( password.pwm.ldap.search.UserSearchEngine.class, Flag.StartDuringRuntimeInstance ),
    PeopleSearchService( password.pwm.http.servlet.peoplesearch.PeopleSearchService.class ),
    TelemetryService( password.pwm.svc.telemetry.TelemetryService.class ),
    ClusterService( NodeService.class ),
    PwExpiryNotifyService( PwNotifyService.class ),;

    private final Class<? extends PwmService> clazz;
    private final Set<Flag> flags;

    private enum Flag
    {
        StartDuringRuntimeInstance,
    }

    PwmServiceEnum( final Class<? extends PwmService> clazz, final Flag... flags )
    {
        this.clazz = clazz;
        this.flags = JavaHelper.enumSetFromArray( flags );
    }

    public boolean isInternalRuntime( )
    {
        return this.flags.contains( Flag.StartDuringRuntimeInstance );
    }

    static List<Class<? extends PwmService>> allClasses( )
    {
        final List<Class<? extends PwmService>> pwmServiceClasses = new ArrayList<>();
        for ( final PwmServiceEnum enumClass : values() )
        {
            pwmServiceClasses.add( enumClass.getPwmServiceClass() );
        }
        return Collections.unmodifiableList( pwmServiceClasses );
    }

    public Class<? extends PwmService> getPwmServiceClass( )
    {
        return clazz;
    }

    public String serviceName()
    {
        return "[" + getPwmServiceClass().getSimpleName() + "]";
    }
}
