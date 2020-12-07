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


package password.pwm.util;

import lombok.Getter;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmDomain;
import password.pwm.PwmConstants;
import password.pwm.util.logging.PwmLogger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

public class MBeanUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( MBeanUtility.class );

    private MBeanUtility( )
    {
    }

    public static void registerMBean( final PwmDomain pwmDomain )
    {
        try
        {
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            final ObjectName name = figureMBeanName( pwmDomain );
            final Map<PwmAboutProperty, String> aboutMap = PwmAboutProperty.makeInfoBean( pwmDomain );
            final Map<String, String> outputMap = new HashMap<>(  );
            final AttributeList attributeList = new AttributeList(  );
            for ( final Map.Entry<PwmAboutProperty, String> entry : aboutMap.entrySet() )
            {
                outputMap.put( entry.getKey().name(), entry.getValue() );
                attributeList.add( new Attribute( entry.getKey().name(), entry.getValue() ) );
            }
            final PwmAbout mbean = new PwmAbout( outputMap );
            mbs.registerMBean( mbean, name );
            mbs.setAttributes( name, attributeList );
        }
        catch ( final Exception e )
        {
            LOGGER.debug( () -> "error registering mbean: " + e.getMessage() );
        }
    }

    public static void unregisterMBean( final PwmDomain pwmDomain )
    {
        try
        {
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbs.unregisterMBean( figureMBeanName( pwmDomain ) );
        }
        catch ( final Exception e )
        {
            LOGGER.debug( () -> "error unregistering mbean: " + e.getMessage() );
        }
    }

    private static ObjectName figureMBeanName( final PwmDomain pwmDomain )
            throws MalformedObjectNameException
    {
        final String context;
        if ( pwmDomain.getPwmEnvironment() != null && pwmDomain.getPwmEnvironment().getContextManager() != null )
        {
            context = "-" + pwmDomain.getPwmEnvironment().getContextManager().getContextPath();
        }
        else
        {
            context = "";
        }
        final String mbeanName = "password.pwm:type=About" + PwmConstants.PWM_APP_NAME.toUpperCase() + context;
        return new ObjectName( mbeanName );
    }


    public interface PwmAboutMXBean
    {
        Map<String, String> getAboutInfoMap();
    }

    @Getter
    public static class PwmAbout implements PwmAboutMXBean
    {
        final Map<String, String> aboutInfoMap;

        public PwmAbout( final Map<String, String> aboutInfoMap )
        {
            this.aboutInfoMap = aboutInfoMap;
        }
    }
}
