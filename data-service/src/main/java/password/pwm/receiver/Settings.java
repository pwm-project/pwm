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

package password.pwm.receiver;

import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
        maxInstanceSeconds( Long.toString( TimeDuration.of( 14, TimeDuration.Unit.DAYS ).as( TimeDuration.Unit.SECONDS ) ) ),;

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
        try ( Reader reader = new InputStreamReader( new FileInputStream( new File( filename ) ), StandardCharsets.UTF_8 ) )
        {
            properties.load( reader );
            final Map<Setting, String> returnMap = new HashMap<>();
            for ( final Setting setting : Setting.values() )
            {
                final String value = properties.getProperty( setting.name(), setting.getDefaultValue() );
                returnMap.put( setting, value );
            }
            return new Settings( Collections.unmodifiableMap( returnMap ) );
        }
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
