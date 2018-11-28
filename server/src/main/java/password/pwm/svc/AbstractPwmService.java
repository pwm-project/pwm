/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import password.pwm.error.ErrorInformation;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;

import java.util.ArrayList;
import java.util.Collections;
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
        if ( status != PwmService.STATUS.OPEN )
        {
            return Collections.emptyList();
        }

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
