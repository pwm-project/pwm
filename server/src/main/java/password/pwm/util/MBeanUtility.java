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


package password.pwm.util;

import lombok.Getter;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
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

    public static void registerMBean( final PwmApplication pwmApplication )
    {
        try
        {
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            final ObjectName name = figureMBeanName( pwmApplication );
            final Map<PwmAboutProperty, String> aboutMap = PwmAboutProperty.makeInfoBean( pwmApplication );
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
        catch ( Exception e )
        {
            LOGGER.error( "error registering mbean: " + e.getMessage() );
        }
    }

    public static void unregisterMBean( final PwmApplication pwmApplication )
    {
        try
        {
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbs.unregisterMBean( figureMBeanName( pwmApplication ) );
        }
        catch ( Exception e )
        {
            LOGGER.error( "error unregistering mbean: " + e.getMessage() );
        }
    }

    private static ObjectName figureMBeanName( final PwmApplication pwmApplication )
            throws MalformedObjectNameException
    {
        final String context;
        if ( pwmApplication.getPwmEnvironment() != null && pwmApplication.getPwmEnvironment().getContextManager() != null )
        {
            context = "-" + pwmApplication.getPwmEnvironment().getContextManager().getContextPath();
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
