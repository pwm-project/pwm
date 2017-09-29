/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.svc;

import password.pwm.util.java.JavaHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum PwmServiceEnum {
    SecureService(          password.pwm.util.secure.SecureService.class,           Flag.StartDuringRuntimeInstance),
    LdapConnectionService(  password.pwm.ldap.LdapConnectionService.class,          Flag.StartDuringRuntimeInstance),
    DatabaseService(        password.pwm.util.db.DatabaseService.class,             Flag.StartDuringRuntimeInstance),
    SharedHistoryManager(   password.pwm.svc.wordlist.SharedHistoryManager.class),
    AuditService(           password.pwm.svc.event.AuditService.class),
    StatisticsManager(      password.pwm.svc.stats.StatisticsManager.class,         Flag.StartDuringRuntimeInstance),
    WordlistManager(        password.pwm.svc.wordlist.WordlistManager.class),
    SeedlistManager(        password.pwm.svc.wordlist.SeedlistManager.class),
    EmailQueueManager(      password.pwm.util.queue.EmailQueueManager.class),
    SmsQueueManager(        password.pwm.util.queue.SmsQueueManager.class),
    UrlShortenerService(    password.pwm.svc.shorturl.UrlShortenerService.class),
    TokenService(           password.pwm.svc.token.TokenService.class,              Flag.StartDuringRuntimeInstance),
    VersionChecker(         password.pwm.svc.telemetry.VersionChecker.class),
    IntruderManager(        password.pwm.svc.intruder.IntruderManager.class),
    CrService(              password.pwm.util.operations.CrService.class,           Flag.StartDuringRuntimeInstance),
    OtpService(             password.pwm.util.operations.OtpService.class),
    CacheService(           password.pwm.svc.cache.CacheService.class,              Flag.StartDuringRuntimeInstance),
    HealthMonitor(          password.pwm.health.HealthMonitor.class),
    ReportService(          password.pwm.svc.report.ReportService.class,            Flag.StartDuringRuntimeInstance),
    ResourceServletService( password.pwm.http.servlet.resource.ResourceServletService.class),
    SessionTrackService(    password.pwm.svc.sessiontrack.SessionTrackService.class),
    SessionStateSvc(        password.pwm.http.state.SessionStateService.class),
    UserSearchEngine(       password.pwm.ldap.search.UserSearchEngine.class,        Flag.StartDuringRuntimeInstance),
    TelemetryService(       password.pwm.svc.telemetry.TelemetryService.class),
    ClusterService(         password.pwm.svc.cluster.ClusterService.class),

    ;

    private final Class<? extends PwmService> clazz;
    private final Flag[] flags;

    private enum Flag {
        StartDuringRuntimeInstance,
    }

    PwmServiceEnum(final Class<? extends PwmService> clazz, final Flag... flags) {
        this.clazz = clazz;
        this.flags = flags;
    }

    public boolean isInternalRuntime() {
        return JavaHelper.enumArrayContainsValue(flags, Flag.StartDuringRuntimeInstance);
    }

    static List<Class<? extends PwmService>> allClasses() {
        final List<Class<? extends PwmService>> pwmServiceClasses = new ArrayList<>();
        for (final PwmServiceEnum enumClass : values()) {
            pwmServiceClasses.add(enumClass.getPwmServiceClass());
        }
        return Collections.unmodifiableList(pwmServiceClasses);
    }

    public Class<? extends PwmService> getPwmServiceClass() {
        return clazz;
    }
}
