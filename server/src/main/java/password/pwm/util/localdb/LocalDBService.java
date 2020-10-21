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

package password.pwm.util.localdb;

import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;

import java.io.Serializable;
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

        return ServiceInfoBean.builder()
                .storageMethod( DataStorageMethod.LOCALDB )
                .debugProperties( returnInfo )
                .build();
    }
}
