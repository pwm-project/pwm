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
            catch ( InvocationTargetException e )
            {
                throw e.getTargetException();
            }
        } );
    }
}


