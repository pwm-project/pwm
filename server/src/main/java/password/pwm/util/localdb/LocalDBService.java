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

package password.pwm.util.localdb;

import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalDBService implements PwmService
{
    private PwmApplication pwmApplication;
    private DomainID domainID;

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
    public String name()
    {
        return LocalDBService.class.getSimpleName();
    }

    @Override
    public void init( final PwmApplication pwmApplication, final DomainID domainID ) throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.domainID = domainID;
    }

    @Override
    public DomainID getDomainID()
    {
        return domainID;
    }

    @Override
    public SessionLabel getSessionLabel()
    {
        return SessionLabel.forPwmService( this, getDomainID() );
    }

    @Override
    public void shutdown( )
    {
        //no-op
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> returnInfo = new LinkedHashMap<>();
        if ( status() == STATUS.OPEN )
        {
            final Map<String, Object> localDbInfo = pwmApplication.getLocalDB().debugInfo();
            for ( final Map.Entry<String, Object> entry : localDbInfo.entrySet() )
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
