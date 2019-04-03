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
