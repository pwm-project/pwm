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

package password.pwm.svc;

import password.pwm.bean.DomainID;
import password.pwm.config.PwmSettingScope;
import password.pwm.health.HealthService;
import password.pwm.ldap.LdapDomainService;
import password.pwm.svc.email.EmailService;
import password.pwm.svc.intruder.IntruderDomainService;
import password.pwm.svc.intruder.IntruderSystemService;
import password.pwm.svc.node.NodeService;
import password.pwm.svc.pwnotify.PwNotifyService;
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.svc.version.VersionCheckService;
import password.pwm.svc.wordlist.SeedlistService;
import password.pwm.svc.wordlist.SharedHistoryService;
import password.pwm.svc.wordlist.WordlistService;
import password.pwm.util.java.CollectionUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum PwmServiceEnum
{
    LocalDBService( password.pwm.util.localdb.LocalDBService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    SystemSecureService( password.pwm.svc.secure.SystemSecureService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    HttpClientService( password.pwm.svc.httpclient.HttpClientService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    DatabaseService( password.pwm.svc.db.DatabaseService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    SharedHistoryManager( SharedHistoryService.class, PwmSettingScope.SYSTEM ),
    AuditService( password.pwm.svc.event.AuditService.class, PwmSettingScope.SYSTEM ),
    StatisticsService( StatisticsService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    WordlistService( WordlistService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    SeedlistService( SeedlistService.class, PwmSettingScope.SYSTEM ),
    IntruderSystemService( IntruderSystemService.class, PwmSettingScope.SYSTEM ),
    EmailService( EmailService.class, PwmSettingScope.SYSTEM ),
    SmsQueueManager( SmsQueueService.class, PwmSettingScope.SYSTEM ),
    UrlShortenerService( password.pwm.svc.shorturl.UrlShortenerService.class, PwmSettingScope.SYSTEM ),
    CacheService( password.pwm.svc.cache.CacheService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    LdapSystemService( password.pwm.ldap.LdapSystemService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    TokenSystemService( password.pwm.svc.token.TokenSystemService.class, PwmSettingScope.SYSTEM ),
    HealthMonitor( HealthService.class, PwmSettingScope.SYSTEM ),
    ReportService( password.pwm.svc.report.ReportService.class, PwmSettingScope.SYSTEM, Flag.StartDuringRuntimeInstance ),
    SessionTrackService( password.pwm.svc.sessiontrack.SessionTrackService.class, PwmSettingScope.SYSTEM ),
    SessionStateSvc( password.pwm.http.state.SessionStateService.class, PwmSettingScope.SYSTEM ),
    TelemetryService( password.pwm.svc.telemetry.TelemetryService.class, PwmSettingScope.SYSTEM ),
    VersionCheckService( VersionCheckService.class, PwmSettingScope.SYSTEM ),
    NodeService( NodeService.class, PwmSettingScope.SYSTEM ),

    DomainSecureService( password.pwm.svc.secure.DomainSecureService.class, PwmSettingScope.DOMAIN, Flag.StartDuringRuntimeInstance ),
    LdapConnectionService( LdapDomainService.class, PwmSettingScope.DOMAIN, Flag.StartDuringRuntimeInstance ),
    CrService( password.pwm.svc.cr.CrService.class, PwmSettingScope.DOMAIN, Flag.StartDuringRuntimeInstance ),
    OtpService( password.pwm.svc.otp.OtpService.class, PwmSettingScope.DOMAIN ),
    IntruderDomainService( IntruderDomainService.class, PwmSettingScope.DOMAIN ),
    UserSearchEngine( password.pwm.ldap.search.UserSearchEngine.class, PwmSettingScope.DOMAIN, Flag.StartDuringRuntimeInstance ),
    TokenService( password.pwm.svc.token.TokenService.class, PwmSettingScope.DOMAIN, Flag.StartDuringRuntimeInstance ),
    UserHistoryService( password.pwm.svc.userhistory.UserHistoryService.class, PwmSettingScope.DOMAIN, Flag.StartDuringRuntimeInstance ),
    PeopleSearchService( password.pwm.http.servlet.peoplesearch.PeopleSearchService.class, PwmSettingScope.DOMAIN ),
    PwExpiryNotifyService( PwNotifyService.class, PwmSettingScope.DOMAIN ),
    ResourceServletService( password.pwm.http.servlet.resource.ResourceServletService.class, PwmSettingScope.DOMAIN ),;


    private final Class<? extends PwmService> clazz;
    private final PwmSettingScope pwmSettingScope;
    private final Set<Flag> flags;

    private enum Flag
    {
        StartDuringRuntimeInstance,
    }

    PwmServiceEnum( final Class<? extends PwmService> clazz, final PwmSettingScope pwmSettingScope, final Flag... flags )
    {
        this.clazz = clazz;
        this.pwmSettingScope = pwmSettingScope;
        this.flags = CollectionUtil.enumSetFromArray( flags );
    }

    public boolean isInternalRuntime( )
    {
        return this.flags.contains( Flag.StartDuringRuntimeInstance );
    }

    public static List<PwmServiceEnum> forScope( final PwmSettingScope pwmSettingScope )
    {
        return Arrays.stream( values() )
                .filter( e -> e.pwmSettingScope == pwmSettingScope )
                .collect( Collectors.toUnmodifiableList() );
    }

    public Class<? extends PwmService> getPwmServiceClass( )
    {
        return clazz;
    }

    public String serviceName( final DomainID domainID )
    {
        return "[" + getPwmServiceClass().getSimpleName()
                + ( domainID.isSystem() ? "" : "/" + domainID.stringValue() )
                + "]";
    }
}
