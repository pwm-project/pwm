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

package password.pwm.http.servlet;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.JavaHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ControlledPwmServletTest
{
    @Test
    public void testProcess() throws IllegalAccessException, InstantiationException
    {
        final Map<Class<? extends ControlledPwmServlet>, Map<String, Method>> dataMap = getClassAndMethods();

        for ( final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet() )
        {
            final Class<? extends AbstractPwmServlet.ProcessAction> processActionsClass = controlledPwmServlet.newInstance().getProcessActionsClass();
            if ( !processActionsClass.isEnum() )
            {
                Assert.fail( controlledPwmServlet.getName() + " process action class must be an enum" );
            }
        }
    }

    @Test
    public void testActionHandlerReturnTypes() throws IllegalAccessException, InstantiationException
    {
        final Map<Class<? extends ControlledPwmServlet>, Map<String, Method>> dataMap = getClassAndMethods();

        for ( final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet() )
        {
            final String servletName = controlledPwmServlet.getName();
            for ( final String methodName : dataMap.get( controlledPwmServlet ).keySet() )
            {
                final Method method = dataMap.get( controlledPwmServlet ).get( methodName );
                if ( method.getReturnType() != ProcessStatus.class )
                {
                    Assert.fail( servletName + ":" + method.getName() + " must have return type of " + ProcessStatus.class.getName() );
                }
            }
        }
    }

    @Test
    public void testActionHandlerParameters() throws IllegalAccessException, InstantiationException
    {
        final Map<Class<? extends ControlledPwmServlet>, Map<String, Method>> dataMap = getClassAndMethods();

        for ( final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet() )
        {
            final String servletName = controlledPwmServlet.getName();
            for ( final String methodName : dataMap.get( controlledPwmServlet ).keySet() )
            {
                final Method method = dataMap.get( controlledPwmServlet ).get( methodName );
                final Class[] returnTypes = method.getParameterTypes();
                if ( returnTypes.length != 1 )
                {
                    Assert.fail( servletName + ":" + method.getName() + " must have exactly one parameter" );
                }
                if ( !returnTypes[0].equals( PwmRequest.class ) )
                {
                    Assert.fail( servletName + ":" + method.getName() + " must have exactly one parameter of type " + PwmRequest.class.getName() );
                }
            }
        }
    }

    @Test
    public void testActionHandlerMethodNaming() throws IllegalAccessException, InstantiationException
    {
        final Map<Class<? extends ControlledPwmServlet>, Map<String, Method>> dataMap = getClassAndMethods();

        for ( final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet() )
        {
            final String servletName = controlledPwmServlet.getName();
            for ( final Method method : JavaHelper.getAllMethodsForClass( controlledPwmServlet ) )
            {
                final String methodName = method.getName();
                final ControlledPwmServlet.ActionHandler actionHandler = method.getAnnotation( ControlledPwmServlet.ActionHandler.class );
                if ( actionHandler != null )
                {
                    final String actionName = actionHandler.action();
                    if ( !methodName.toLowerCase().contains( actionName.toLowerCase() ) )
                    {
                        Assert.fail( "method " + servletName + ":" + methodName + " must have the ActionHandler name '"
                                + actionName + "' as part of the method name." );
                    }
                }
            }
        }
    }


    @Test
    public void testActionHandlersExistence() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException
    {
        final Map<Class<? extends ControlledPwmServlet>, Map<String, Method>> dataMap = getClassAndMethods();

        for ( final Class<? extends ControlledPwmServlet> controlledPwmServlet : dataMap.keySet() )
        {
            final String servletName = controlledPwmServlet.getName();

            final Class<? extends AbstractPwmServlet.ProcessAction> processActionsClass = controlledPwmServlet.newInstance().getProcessActionsClass();
            final List<String> names = new ArrayList<>();
            for ( final Object enumObject : processActionsClass.getEnumConstants() )
            {
                names.add( ( ( Enum ) enumObject ).name() );
            }

            {
                final Collection<String> missingActionHandlers = new HashSet<>( names );
                missingActionHandlers.removeAll( dataMap.get( controlledPwmServlet ).keySet() );
                if ( !missingActionHandlers.isEmpty() )
                {
                    Assert.fail( servletName + " does not have an action handler for action " + missingActionHandlers.iterator().next() );
                }
            }

            {
                final Collection<String> superflousActionHandlers = new HashSet<>( dataMap.get( controlledPwmServlet ).keySet() );
                superflousActionHandlers.removeAll( names );
                if ( !superflousActionHandlers.isEmpty() )
                {
                    Assert.fail( servletName + " has an action handler for action " + superflousActionHandlers.iterator().next() + " but no such ProcessAction exists" );
                }
            }
        }
    }

    private Map<Class<? extends ControlledPwmServlet>, Map<String, Method>> getClassAndMethods()
    {
        final Reflections reflections = new Reflections( new ConfigurationBuilder()
                .setUrls( ClasspathHelper.forPackage( "password.pwm" ) )
                .setScanners( new SubTypesScanner(),
                        new TypeAnnotationsScanner(),
                        new FieldAnnotationsScanner()
                ) );


        final Set<Class<? extends ControlledPwmServlet>> classes = reflections.getSubTypesOf( ControlledPwmServlet.class );

        final Map<Class<? extends ControlledPwmServlet>, Map<String, Method>> returnMap = new HashMap<>();

        for ( final Class<? extends ControlledPwmServlet> controlledPwmServlet : classes )
        {
            if ( !Modifier.isAbstract( controlledPwmServlet.getModifiers() ) )
            {

                final Map<String, Method> annotatedMethods = new HashMap<>();

                for ( final Method method : JavaHelper.getAllMethodsForClass( controlledPwmServlet ) )
                {
                    if ( method.getAnnotation( ControlledPwmServlet.ActionHandler.class ) != null )
                    {
                        final String actionName = method.getAnnotation( ControlledPwmServlet.ActionHandler.class ).action();
                        annotatedMethods.put( actionName, method );
                    }
                }

                returnMap.put( controlledPwmServlet, Collections.unmodifiableMap( annotatedMethods ) );
            }
        }

        return Collections.unmodifiableMap( returnMap );
    }
}
