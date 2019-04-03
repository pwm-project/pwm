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

package password.pwm.http.servlet.peoplesearch;

import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PeopleSearchService implements PwmService
{
    private PwmApplication pwmApplication;
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public STATUS status()
    {
        return null;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;

        final int maxThreadCount = 5;

        final ThreadFactory threadFactory = PwmScheduler.makePwmThreadFactory( PwmScheduler.makeThreadName( pwmApplication, PeopleSearchService.class ), true );
        threadPoolExecutor = new ThreadPoolExecutor(
                maxThreadCount,
                maxThreadCount,
                1,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>( 5000 ),
                threadFactory
        );

    }

    @Override
    public void close()
    {
        threadPoolExecutor.shutdown();
    }

    @Override
    public List<HealthRecord> healthCheck()
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo()
    {
        return null;
    }

    public ThreadPoolExecutor getJobExecutor()
    {
        return threadPoolExecutor;
    }
}
