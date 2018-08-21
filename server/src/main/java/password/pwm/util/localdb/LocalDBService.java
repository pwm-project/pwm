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

package password.pwm.util.localdb;

import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalDBService implements PwmService
{
    private PwmApplication pwmApplication;

    @Override
    public STATUS status( )
    {
        if ( pwmApplication != null
                && pwmApplication.getLocalDB() != null
                && pwmApplication.getLocalDB().status() == LocalDB.Status.OPEN )
        {
            return STATUS.OPEN;
        }

        return STATUS.CLOSED;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;
    }

    @Override
    public void close( )
    {
        //no-op
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> returnInfo = new LinkedHashMap<>();
        if ( status() == STATUS.OPEN )
        {
            final Map<String, Serializable> localDbInfo = pwmApplication.getLocalDB().debugInfo();
            for ( final Map.Entry<String, Serializable> entry : localDbInfo.entrySet() )
            {
                returnInfo.put( entry.getKey(), String.valueOf( entry.getValue() ) );
            }
        }
        return new ServiceInfoBean( Collections.singleton( DataStorageMethod.LOCALDB ), Collections.unmodifiableMap( returnInfo ) );
    }
}
