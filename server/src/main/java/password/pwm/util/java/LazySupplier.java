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

import java.util.function.Supplier;

/**
 * Supplier implementation that will cache the value.   Note this implementation
 * is NOT thread safe, it is entirely possible that the underlying {@link Supplier}
 * will be invoked multiple times.
 *
 * @param <T> the type of object being supplied.
 */
public class LazySupplier<T> implements Supplier<T>
{
    private boolean supplied = false;
    private T value;
    private final Supplier<T> realSupplier;

    public LazySupplier( final Supplier<T> realSupplier )
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

    public interface CheckedSupplier<T, E extends Exception>
    {
        T call() throws E;
    }

    public static <T, E extends Exception> LazyCheckedSupplier<T, E> checked( final CheckedSupplier<T, E> lazySupplier )
    {
        return new LazyCheckedSupplier<>( lazySupplier );
    }

    private static class LazyCheckedSupplier<T, E extends Exception> implements CheckedSupplier<T, E>
    {
        private boolean supplied = false;
        private T value;
        private final CheckedSupplier<T, E> realCallable;

        private LazyCheckedSupplier( final CheckedSupplier<T, E> realSupplier )
        {
            this.realCallable = realSupplier;
        }

        @Override
        public T call() throws E
        {
            if ( !supplied )
            {
                value = realCallable.call();
                supplied = true;
            }
            return value;
        }
    }
}
