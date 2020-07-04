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

package password.pwm.util.java;

import password.pwm.util.PwmScheduler;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BlockingThreadPool extends ThreadPoolExecutor
{

    private final Semaphore semaphore;

    public BlockingThreadPool( final int bound, final String name )
    {
        super( bound, bound, 0, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(), PwmScheduler.makePwmThreadFactory( name, true ) );
        semaphore = new Semaphore( bound );
    }

    public Future<?> blockingSubmit( final Runnable task )
    {
        semaphore.acquireUninterruptibly();
        return super.submit( task );
    }

    @Override
    protected void afterExecute( final Runnable r, final Throwable t )
    {
        super.afterExecute( r, t );
        semaphore.release();
    }
}
