/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ConcurrentClosableIteratorWrapper<T> implements Iterator<T>, ClosableIterator<T>
{
    private final AtomicReference<T> nextReference = new AtomicReference<>();
    private final AtomicBoolean completed = new AtomicBoolean( false );

    private final Supplier<Optional<T>> nextSupplier;
    private final Runnable closeFunction;

    public ConcurrentClosableIteratorWrapper( final Supplier<Optional<T>> nextFunction, final Runnable closeFunction )
    {
        this.nextSupplier = nextFunction;
        this.closeFunction = closeFunction;
    }

    @Override
    public synchronized boolean hasNext()
    {
        if ( completed.get() )
        {
            return false;
        }
        work();
        return nextReference.get() != null;
    }

    @Override
    public synchronized T next()
    {
        if ( completed.get() )
        {
            throw new NoSuchElementException(  );
        }
        work();
        final T fileSummaryInformation = nextReference.get();
        if ( fileSummaryInformation == null )
        {
            throw new NoSuchElementException(  );
        }
        nextReference.set( null );
        return fileSummaryInformation;
    }

    @Override
    public synchronized void close()
    {
        closeImpl();
    }

    private void closeImpl()
    {
        if ( !completed.get() )
        {
            completed.set( true );
            nextReference.set( null );
            closeFunction.run();
        }
    }

    private void work()
    {
        if ( nextReference.get() == null && !completed.get() )
        {
            final Optional<T> next = nextSupplier.get();
            if ( next.isPresent() )
            {
                nextReference.set( next.get() );
            }
            else
            {
                closeImpl();
            }
        }
    }
}
