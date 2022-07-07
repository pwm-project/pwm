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

import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.TimeDuration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

public abstract class AbstractPwmService implements PwmService
{
    private PwmApplication pwmApplication;
    private volatile PwmService.STATUS status = PwmService.STATUS.CLOSED;
    private ErrorInformation startupError;
    private DomainID domainID;
    private SessionLabel sessionLabel;

    private Supplier<ScheduledExecutorService> executorService;


    public final PwmService.STATUS status()
    {
        return status;
    }

    @Override
    public String name()
    {
        return "[" + this.getClass().getSimpleName()
                + ( domainID == null || domainID.isSystem() ? "" : "/" + domainID.stringValue() )
                + "]";
    }

    public final void init( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.domainID = Objects.requireNonNull( domainID );
        this.sessionLabel = domainID.isSystem()
                ? pwmApplication.getSessionLabel()
                : pwmApplication.domains().get( domainID ).getSessionLabel();

        executorService = new LazySupplier<>( () -> PwmScheduler.makeBackgroundServiceExecutor( pwmApplication, getSessionLabel(), getClass() ) );

        if ( pwmApplication.checkConditions( openConditions() ) )
        {
            setStatus( this.postAbstractInit( pwmApplication, domainID ) );
        }
    }

    protected abstract STATUS postAbstractInit( PwmApplication pwmApplication, DomainID domainID )
            throws PwmException;

    protected PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }

    protected void setStatus( final PwmService.STATUS status )
    {
        this.status = Objects.requireNonNull( status );
    }

    @Override
    public void shutdown()
    {
        this.status = STATUS.CLOSED;
        if ( executorService != null )
        {
            JavaHelper.closeAndWaitExecutor( executorService.get(), TimeDuration.SECONDS_10 );
        }
        shutdownImpl();
    }

    protected abstract void shutdownImpl();

    public DomainID getDomainID()
    {
        return domainID;
    }

    public SessionLabel getSessionLabel()
    {
        return sessionLabel;
    }

    protected void setStartupError( final ErrorInformation startupError )
    {
        this.startupError = startupError;
    }

    protected ErrorInformation getStartupError()
    {
        return startupError;
    }

    public final List<HealthRecord> healthCheck( )
    {
        final List<HealthRecord> returnRecords = new ArrayList<>(  );

        final ErrorInformation startupError = this.startupError;
        if ( startupError != null )
        {
            returnRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed,
                    name(),
                    startupError.toDebugStr() ) );
        }

        if ( status() == STATUS.OPEN )
        {
            final List<HealthRecord> records = serviceHealthCheck();
            if ( records != null )
            {
                returnRecords.addAll( records );
            }
        }

        return returnRecords;
    }

    protected abstract List<HealthRecord> serviceHealthCheck();

    protected Set<PwmApplication.Condition> openConditions()
    {
        return EnumSet.of( PwmApplication.Condition.RunningMode, PwmApplication.Condition.LocalDBOpen, PwmApplication.Condition.NotInternalInstance );
    }

    protected ScheduledExecutorService getExecutorService()
    {
        return executorService.get();
    }
}
