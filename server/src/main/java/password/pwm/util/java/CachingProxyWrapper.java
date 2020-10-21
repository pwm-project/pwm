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

import lombok.Value;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachingProxyWrapper
{

    @Value
    private static final class MethodSignature
    {
        private final Method method;
        private final Object[] arguments;
    }

    @Value
    private static final class ResultWrapper
    {
        private final Object result;
    }

    public static <T> T create( final Class<T> proxiedClass, final T innerInstance )
    {
        final Class<?>[] classList = new Class[]
                {
                        proxiedClass,
                };

        // proxy for the interface T
        return ( T ) Proxy.newProxyInstance( proxiedClass.getClassLoader(), classList, new ProxyInstance( innerInstance ) );
    }

    static class ProxyInstance implements InvocationHandler
    {
        private final Map<MethodSignature, ResultWrapper> cache = new ConcurrentHashMap<>();
        private final Object wrappedClass;

        ProxyInstance( final Object wrappedClass )
        {
            this.wrappedClass = wrappedClass;
        }

        @Override
        public Object invoke( final Object proxy, final Method method, final Object[] args ) throws Throwable
        {

            final MethodSignature methodSignature = new MethodSignature( method, args );
            final ResultWrapper cachedResult = cache.get( methodSignature );

            if ( cachedResult != null )
            {
                return cachedResult.getResult();
            }

            // make sure exceptions are handled transparently
            try
            {
                final Object result = method.invoke( wrappedClass, args );
                cache.put( methodSignature, new ResultWrapper( result ) );
                return result;
            }
            catch ( final InvocationTargetException e )
            {
                throw e.getTargetException();
            }
        }
    }
}


