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

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

/**
 * A lazy soft reference holder.  This reference will be built lazy and held softly
 * (according to the semantics of {@link SoftReference}).  This class is not thread
 * safe, and the GC may delete the reference at any time, so the {@link Supplier}
 * given to the constructor may be executed multiple times over the lifetime of
 * the reference.
 *
 * @param <E> type of object to hold
 */
public class LazySoftReference<E>
{
    private volatile SoftReference<E> reference = new SoftReference<>( null );
    private final Supplier<E> supplier;

    public LazySoftReference( final Supplier<E> supplier )
    {
        this.supplier = supplier;
    }

    public E get()
    {
        E localValue = reference.get();
        if ( localValue == null )
        {
            localValue = supplier.get();
            reference = new SoftReference<>( localValue );
        }
        return localValue;
    }
}
