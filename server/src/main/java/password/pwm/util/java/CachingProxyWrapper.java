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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CachingProxyWrapper
{

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    private static final class MethodSignature
    {
        private Method method;
        private Object[] arguments;
    }

    public static <T> T create( final Class<T> proxiedClass, final T innerInstance )
    {
        // create the cache
        final Map<MethodSignature, Optional<T>> cache = new ConcurrentHashMap<>();

        final Class<?>[] classList = new Class[]
                {
                        proxiedClass,
                };

        // proxy for the interface T
        return ( T ) Proxy.newProxyInstance( proxiedClass.getClassLoader(), classList, ( proxy, method, args ) ->
        {
            final MethodSignature methodSignature = new MethodSignature( method, args );

            final Optional<T> cachedResult = cache.get( methodSignature );

            if ( cachedResult != null )
            {
                if ( cachedResult.isPresent() )
                {
                    return cachedResult.get();
                }
                return null;
            }

            // make sure exceptions are handled transparently
            try
            {
                final T result = ( T ) method.invoke( innerInstance, args );
                cache.put( methodSignature, Optional.ofNullable( result ) );
                return result;
            }
            catch ( final InvocationTargetException e )
            {
                throw e.getTargetException();
            }
        } );
    }
}


