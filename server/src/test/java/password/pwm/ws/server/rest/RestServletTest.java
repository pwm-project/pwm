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

package password.pwm.ws.server.rest;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import password.pwm.http.HttpContentType;
import password.pwm.util.java.JavaHelper;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestWebServer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RestServletTest
{

    @Test
    public void testActionHandlerReturnTypes() throws IllegalAccessException, InstantiationException
    {
        final Set<Class<? extends RestServlet>> classMap = getClasses();

        for ( final Class<? extends RestServlet> restServlet : classMap )
        {
            if ( restServlet.getAnnotation( RestWebServer.class ) == null )
            {
                Assert.fail( restServlet.getName() + " is missing annotation type of " + RestWebServer.class.getName() );
            }

            final Collection<Method> methods = JavaHelper.getAllMethodsForClass( restServlet );

            final Set<RestMethodHandler> seenHandlers = new HashSet<>();
            for ( final Method method : methods )
            {
                final RestMethodHandler methodHandler = method.getAnnotation( RestMethodHandler.class );
                if ( methodHandler != null )
                {
                    final String returnTypeName = method.getReturnType().getName();
                    final RestMethodHandler restMethodHandler = method.getAnnotation( RestMethodHandler.class );

                    if ( !returnTypeName.equals( RestResultBean.class.getName() ) )
                    {
                        boolean requiresRestResultBeanReturnType = true;

                        if ( restMethodHandler != null )
                        {
                            if ( Arrays.asList( restMethodHandler.produces() ).contains( HttpContentType.plain ) )
                            {
                                requiresRestResultBeanReturnType = false;
                            }
                        }

                        if ( requiresRestResultBeanReturnType )
                        {
                            Assert.fail( "method " + restServlet.getName()
                                    + ":" + method.getName() + " should have return type of " + RestResultBean.class.getName() );
                        }
                    }

                    final Class[] paramTypes = method.getParameterTypes();
                    if ( paramTypes == null || paramTypes.length != 1 )
                    {
                        Assert.fail( "method " + restServlet.getName()
                                + ":" + method.getName() + " should have exactly one parameter" );
                    }

                    final String paramTypeName = paramTypes[0].getName();
                    if ( !paramTypeName.equals( RestRequest.class.getName() ) )
                    {
                        Assert.fail( "method " + restServlet.getName()
                                + ":" + method.getName() + " parameter type must be type " + RestRequest.class.getName() );
                    }

                    if ( seenHandlers.contains( methodHandler ) )
                    {
                        Assert.fail( "duplicate " + RestMethodHandler.class + " assertions on class " + restServlet.getName() );
                    }
                    seenHandlers.add( methodHandler );
                }

            }
        }
    }

    private Set<Class<? extends RestServlet>> getClasses()
    {
        final Reflections reflections = new Reflections( new ConfigurationBuilder()
                .setUrls( ClasspathHelper.forPackage( "password.pwm" ) )
                .setScanners( new SubTypesScanner(),
                        new TypeAnnotationsScanner(),
                        new FieldAnnotationsScanner()
                ) );


        final Set<Class<? extends RestServlet>> classes = reflections.getSubTypesOf( RestServlet.class );
        return Collections.unmodifiableSet( classes );
    }
}
