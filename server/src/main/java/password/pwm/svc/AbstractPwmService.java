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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractPwmService implements PwmService
{
    private PwmApplication pwmApplication;
    private final AtomicReference<PwmService.STATUS> status = new AtomicReference<>( PwmService.STATUS.CLOSED );
    private ErrorInformation startupError;
    private DomainID domainID;
    private SessionLabel sessionLabel;

    public final PwmService.STATUS status()
    {
        return status.get();
    }

    public final void init( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.domainID = Objects.requireNonNull( domainID );
        this.sessionLabel = SessionLabel.forPwmService( this, domainID );

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
        this.status.set( status );
    }

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
                    startupError.toDebugStr() ) );
        }

        if ( status() == STATUS.OPEN )
        {
            returnRecords.addAll( serviceHealthCheck() );
        }

        return returnRecords;
    }

    protected abstract List<HealthRecord> serviceHealthCheck();

    protected Set<PwmApplication.Condition> openConditions()
    {
        return EnumSet.of( PwmApplication.Condition.RunningMode, PwmApplication.Condition.LocalDBOpen, PwmApplication.Condition.NotInternalInstance );
    }
}
