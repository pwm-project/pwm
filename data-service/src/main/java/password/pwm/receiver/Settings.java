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

package password.pwm.receiver;

import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class Settings
{
    enum Setting
    {
        ftpMode( FtpMode.ftp.name() ),
        ftpSite( null ),
        ftpUser( null ),
        ftpPassword( null ),
        ftpReadPath( null ),
        storagePath( null ),
        maxInstanceSeconds( Long.toString( new TimeDuration( 14, TimeUnit.DAYS ).getTotalSeconds() ) ),;

        private final String defaultValue;

        Setting( final String defaultValue )
        {
            this.defaultValue = defaultValue == null ? "" : defaultValue;
        }

        private String getDefaultValue( )
        {
            return defaultValue;
        }
    }

    enum FtpMode
    {
        ftp,
        ftps,
    }

    private final Map<Setting, String> settings;

    private Settings( final Map<Setting, String> settings )
    {
        this.settings = settings;
    }

    static Settings readFromFile( final String filename ) throws IOException
    {
        final Properties properties = new Properties();
        properties.load( new FileReader( filename ) );
        final Map<Setting, String> returnMap = new HashMap<>();
        for ( final Setting setting : Setting.values() )
        {
            final String value = properties.getProperty( setting.name(), setting.getDefaultValue() );
            returnMap.put( setting, value );
        }
        return new Settings( Collections.unmodifiableMap( returnMap ) );
    }

    public String getSetting( final Setting setting )
    {
        return settings.get( setting );
    }

    public boolean isFtpEnabled( )
    {
        final String value = settings.get( Setting.ftpSite );
        return !StringUtil.isEmpty( value );
    }
}
