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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Memorizer implements InvocationHandler
{
    private final StatisticCounterBundle<DebugStats> stats = new StatisticCounterBundle<>( DebugStats.class );

    enum DebugStats
    {
        hits,
        misses,
    }

    private final Object memorizedObject;

    private final Map<MethodAndArgsKey, ValueWrapper> valueCache = new HashMap<>();

    public static Object forObject( final Object memorizedObject )
    {
        if ( memorizedObject instanceof Memorizer )
        {
            return memorizedObject;
        }

        return Proxy.newProxyInstance(
                memorizedObject.getClass().getClassLoader(),
                memorizedObject.getClass().getInterfaces(),
                new Memorizer( memorizedObject ) );
    }

    private Memorizer( final Object memorizedObject )
    {
        this.memorizedObject = memorizedObject;
    }

    @Override
    public Object invoke( final Object object, final Method method, final Object[] args )
            throws Throwable
    {
        if ( method.getReturnType().equals( Void.TYPE ) )
        {
            // Don't cache void methods
            return realInvoke( method, args );
        }
        else
        {
            final MethodAndArgsKey key = new MethodAndArgsKey( method, args == null ? null : Arrays.asList( args ) );

            ValueWrapper valueWrapper = valueCache.get( key );

            // value is not in cache, so invoke method normally
            if ( valueWrapper == null )
            {
                stats.increment( DebugStats.misses );
                try
                {
                    final Object realValue = realInvoke( method, args );
                    valueWrapper = new ValueWrapper( realValue );
                    valueCache.put( key, valueWrapper );
                }
                catch ( final Exception e )
                {
                    throw e.getCause();
                }
            }
            else
            {
                stats.increment( DebugStats.hits );
            }

            return valueWrapper.getValue();
        }
    }

    public void outputStats()
    {
        System.out.println( stats.debugString() );
    }

    private Object realInvoke( final Method method, final Object[] args )
    throws Throwable
    {
        try
        {
            return method.invoke( memorizedObject, args );
        }
        catch ( final IllegalAccessException | InvocationTargetException e )
        {
            throw e.getCause();
        }
        catch ( final Throwable t )
        {
            throw t;
        }
    }


    @Value
    private static class ValueWrapper
    {
        private final Object value;
    }

    @Value
    private static class MethodAndArgsKey
    {
        private final Method method;
        private final List<Object> args;
    }

}
