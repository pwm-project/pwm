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

package password.pwm.util.java;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.lang.ref.SoftReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class LazySupplierImpl
{
    static class StandardLazySupplier<T> implements LazySupplier<T>
    {
        private boolean supplied = false;
        private T value;
        private final Supplier<T> realSupplier;

        StandardLazySupplier( final Supplier<T> realSupplier )
        {
            this.realSupplier = realSupplier;
        }

        @Override
        public T get()
        {
            if ( !supplied )
            {
                value = realSupplier.get();
                supplied = true;
            }
            return value;
        }

        public boolean isSupplied()
        {
            return supplied;
        }

        @Override
        public void clear()
                throws UnsupportedOperationException
        {
            supplied = false;
            value = null;
        }
    }

    static class SoftLazySupplier<T> implements LazySupplier<T>
    {
        private static final Object TOMBSTONE = new Object();

        private SoftReference<?> reference;
        private final Supplier<T> realSupplier;

        SoftLazySupplier( final Supplier<T> realSupplier )
        {
            this.realSupplier = Objects.requireNonNull( realSupplier );
        }

        @Override
        public T get()
        {
            if ( reference != null )
            {
                final Object referencedValue = reference.get();
                if ( referencedValue != null )
                {
                    return referencedValue == TOMBSTONE ? null : ( T ) referencedValue;
                }
            }

            final T realValue = realSupplier.get();
            reference = new SoftReference<>( realValue == null ? TOMBSTONE : realValue );
            return realValue;
        }

        public boolean isSupplied()
        {
            return reference.get() != null;
        }

        public void clear()
        {
            reference = null;
        }
    }

    static class LockingSupplier<T> implements LazySupplier<T>
    {
        private final Supplier<T> realSupplier;
        private final AtomicReference<T> value = new AtomicReference<>();
        private final AtomicBoolean  supplied = new AtomicBoolean();

        private final FunctionalReentrantLock lock = new FunctionalReentrantLock();

        LockingSupplier( final Supplier<T> realSupplier )
        {
            this.realSupplier = Objects.requireNonNull( realSupplier );
        }

        @Override
        public T get()
        {
            return lock.exec( () ->
            {
                if ( !supplied.get() )
                {
                    value.set( realSupplier.get() );
                    supplied.set( true );
                }

                return value.get();
            } );
        }

        @Override
        public boolean isSupplied()
        {
            return lock.exec( supplied::get );
        }

        @Override
        public void clear()
        {
            lock.exec( () ->
            {
                supplied.set( false );
                value.set( null );
            } );
        }
    }

    @SuppressFBWarnings( "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION" )
    static class LazyCheckedSupplier<T, E extends Exception> implements LazySupplier.CheckedSupplier<T, E>
    {
        private boolean supplied = false;
        private T value;
        private final LazySupplier.CheckedSupplier<T, E> realSupplier;

        LazyCheckedSupplier( final LazySupplier.CheckedSupplier<T, E> realSupplier )
        {
            this.realSupplier = Objects.requireNonNull( realSupplier );
        }

        @Override
        @SuppressFBWarnings( "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION" )
        public T call() throws E
        {
            if ( !supplied )
            {
                value = realSupplier.call();
                supplied = true;
            }
            return value;
        }
    }
}
