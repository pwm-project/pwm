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

package password.pwm.svc.report;

import org.jetbrains.annotations.NotNull;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;


public class ReportService extends AbstractPwmService implements PwmService
{
    private PwmApplication pwmApplication;
    private ReportSettings settings = ReportSettings.builder().build();

    private final Semaphore activeReportSemaphore = new Semaphore( 5 );
    private final Set<ReportProcess> outstandingReportProcesses = Collections.newSetFromMap( new WeakHashMap<>() );

    private ExecutorService threadPool;

    public ReportService( )
    {
    }

    public ReportSettings getSettings()
    {
        return settings;
    }

    public ExecutorService getExecutor()
    {
        return this.threadPool;
    }

    public enum ReportCommand
    {
        Start,
        Stop,
        Clear,
    }

    protected STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.settings = ReportSettings.readSettingsFromConfig( this.getPwmApplication().getConfig() );
        final int maxThreads = settings.getReportJobThreads();
        this.threadPool = PwmScheduler.makeMultiThreadExecutor( maxThreads, pwmApplication, getSessionLabel(), ReportService.class );
        return STATUS.OPEN;
    }

    public void shutdownImpl( )
    {
        if ( threadPool != null )
        {
            threadPool.shutdown();
            threadPool = null;
        }

        setStatus( STATUS.CLOSED );

        for ( final ReportProcess reportProcess : outstandingReportProcesses )
        {
            if ( reportProcess != null )
            {
                reportProcess.close();
            }
        }
    }

    public ReportProcess createReportProcess(
            final Locale locale,
            @NotNull final SessionLabel sessionLabel
    )
    {
        final ReportProcess reportProcess = ReportProcess.createReportProcess( pwmApplication, activeReportSemaphore, settings, locale, sessionLabel );
        outstandingReportProcesses.add( reportProcess );
        return reportProcess;
    }


    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder().storageMethod( DataStorageMethod.LDAP ).build();
    }
}
