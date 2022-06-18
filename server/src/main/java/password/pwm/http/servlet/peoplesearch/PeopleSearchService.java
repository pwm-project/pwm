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

package password.pwm.http.servlet.peoplesearch;

import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

public class PeopleSearchService extends AbstractPwmService implements PwmService
{
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        final int maxThreadCount = 5;

        threadPoolExecutor = PwmScheduler.makeMultiThreadExecutor( maxThreadCount, pwmApplication, getSessionLabel(), PeopleSearchService.class );

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl()
    {
        if ( threadPoolExecutor != null )
        {
            threadPoolExecutor.shutdown();
            threadPoolExecutor = null;
        }
    }

    @Override
    public List<HealthRecord> serviceHealthCheck()
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo()
    {
        return ServiceInfoBean.builder().build();
    }

    public ThreadPoolExecutor getJobExecutor()
    {
        return threadPoolExecutor;
    }
}
