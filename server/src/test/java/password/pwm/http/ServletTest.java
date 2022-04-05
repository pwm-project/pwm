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

package password.pwm.http;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import password.pwm.PwmConstants;
import password.pwm.util.java.StringUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ServletTest
{
    @Test
    public void testDuplicateServletNames()
    {
        final var seenServletNames = new HashSet<String>();

        final var servletClasses = getServletClasses();

        for ( final Class<? extends HttpServlet> httpServletClass : servletClasses )
        {
            final var webServletAnnotation = httpServletClass.getAnnotation( WebServlet.class );
            if ( webServletAnnotation != null )
            {
                final var name = webServletAnnotation.name();
                if ( !StringUtil.isEmpty( name ) )
                {
                    if ( StringUtil.caseIgnoreContains( seenServletNames, name ) )
                    {
                        Assert.fail( httpServletClass.getName() + " servlet class name duplicate (case ignore) detected: " + name );
                    }
                    seenServletNames.add( name );
                }
            }
        }
    }

    @Test
    public void testDuplicatePatternsNames()
    {
        final var seenPatterns = new HashSet<String>();

        final var servletClasses = getServletClasses();

        for ( final Class<? extends HttpServlet> httpServletClass : servletClasses )
        {
            final WebServlet webServletAnnotation = httpServletClass.getAnnotation( WebServlet.class );
            if ( webServletAnnotation != null )
            {
                final var names = webServletAnnotation.urlPatterns();
                for ( final var name : names )
                {
                    if ( !StringUtil.isEmpty( name ) )
                    {
                        if ( seenPatterns.contains( name ) )
                        {
                            Assert.fail( httpServletClass.getName() + " servlet pattern duplicate detected: " + name );
                        }
                        seenPatterns.add( name );
                    }
                }
            }
        }
    }


    private Set<Class<? extends HttpServlet>> getServletClasses()
    {
        final var reflections = new Reflections( new ConfigurationBuilder()
                .setUrls( ClasspathHelper.forPackage( PwmConstants.PWM_BASE_PACKAGE.getName() ) )
                .setScanners( Scanners.SubTypes ) );

        return Collections.unmodifiableSet( reflections.getSubTypesOf( HttpServlet.class ) );
    }
}
