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

import password.pwm.error.ErrorInformation;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPwmService
{
    private PwmService.STATUS status = PwmService.STATUS.CLOSED;
    private ErrorInformation startupError;

    public final PwmService.STATUS status()
    {
        return status;
    }

    protected void setStatus( final PwmService.STATUS status )
    {
        this.status = status;
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
            returnRecords.add( HealthRecord.forMessage( HealthMessage.ServiceClosed, startupError.toDebugStr() ) );
        }

        returnRecords.addAll( serviceHealthCheck() );

        return returnRecords;
    }

    protected abstract List<HealthRecord> serviceHealthCheck();
}
