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

package password.pwm.svc.token;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;

public class TokenSystemService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AbstractPwmService.class );

    @Override
    protected STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID ) throws PwmException
    {
        {
            final int cleanerFrequencySeconds = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.TOKEN_CLEANER_INTERVAL_SECONDS ) );
            final TimeDuration cleanerFrequency = TimeDuration.of( cleanerFrequencySeconds, TimeDuration.Unit.SECONDS );
            scheduleFixedRateJob( new CleanerTask(), TimeDuration.MINUTE, cleanerFrequency );
            LOGGER.trace( getSessionLabel(), () -> "token cleanup will occur every " + cleanerFrequency.asCompactString() );
        }

        return STATUS.OPEN;
    }

    @Override
    protected void shutdownImpl()
    {

    }

    @Override
    protected List<HealthRecord> serviceHealthCheck()
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo()
    {
        return null;
    }

    private class CleanerTask implements Runnable
    {
        @Override
        public void run( )
        {
            for ( final PwmDomain pwmDomain : getPwmApplication().domains().values() )
            {
                pwmDomain.getTokenService().cleanup();
            }
        }
    }

}
