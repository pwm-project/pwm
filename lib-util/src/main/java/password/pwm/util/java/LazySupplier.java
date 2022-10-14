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

import java.util.function.Supplier;

/**
 * Supplier wrapper implementations.
 *
 * @param <T> the type of object being supplied.
 */
public interface LazySupplier<T> extends Supplier<T>
{
    boolean isSupplied();

    void clear() throws UnsupportedOperationException;

    @SuppressFBWarnings( "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION" )
    interface CheckedSupplier<T, E extends Exception>
    {
        T call() throws E;
    }

    /**
     * Synchronized wrapper for any other {@code Supplier} implementation that
     * guarantee thread safety.  In particular, the backing realSupplier will only ever be called
     * a single time unless {@code #clear} is invoked.
     * @param realSupplier another {@code LazySupplier} instance
     * @param <T> return type.
     * @return a {@code LazyWrapper} thread safe synchronization.
     */
    static <T> LazySupplier<T> createSynchronized( final Supplier<T> realSupplier )
    {
        return new LazySupplierImpl.LockingSupplier<>( realSupplier );
    }

    static <T> LazySupplier<T> create( final Supplier<T> realSupplier )
    {
        return new LazySupplierImpl.StandardLazySupplier<T>( realSupplier );
    }

    static <T> LazySupplier<T> createSoft( final Supplier<T> realSupplier )
    {
        return new LazySupplierImpl.SoftLazySupplier<T>( realSupplier );
    }

    static <T, E extends Exception> CheckedSupplier<T, E> checked( final CheckedSupplier<T, E> lazySupplier )
    {
        return new LazySupplierImpl.LazyCheckedSupplier<>( lazySupplier );
    }
}
