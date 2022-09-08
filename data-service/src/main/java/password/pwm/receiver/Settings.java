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

package password.pwm.receiver;

import password.pwm.bean.VersionNumber;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class Settings
{
    private static final Logger LOGGER = Logger.createLogger( Setting.class );

    enum Setting
    {
        ftpMode( FtpMode.ftp.name() ),
        ftpSite( null ),
        ftpUser( null ),
        ftpPassword( null ),
        ftpReadPath( null ),
        storagePath( null ),
        maxInstanceSeconds( Long.toString( TimeDuration.of( 14, TimeDuration.Unit.DAYS ).as( TimeDuration.Unit.SECONDS ) ) ),
        currentVersion( null ),;

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

    private final VersionNumber versionNumber;

    private Settings( final Map<Setting, String> settings )
    {
        this.settings = settings;
        this.versionNumber = parseCurrentVersionInfo();
    }

    static Settings readFromFile( final String filename ) throws IOException
    {
        final Properties properties = new Properties();
        final Path path = new File( filename ).toPath();
        try ( Reader reader = new InputStreamReader( Files.newInputStream( path ), StandardCharsets.UTF_8 ) )
        {
            properties.load( reader );
            final Map<Setting, String> returnMap = CollectionUtil.enumStream( Setting.class )
                    .collect( Collectors.toUnmodifiableMap(
                            setting -> setting,
                            setting -> properties.getProperty( setting.name(), setting.getDefaultValue() )
                    ) );

            return new Settings( returnMap );
        }
    }

    public String getSetting( final Setting setting )
    {
        return settings.get( setting );
    }

    public boolean isFtpEnabled( )
    {
        final String value = settings.get( Setting.ftpSite );
        return StringUtil.notEmpty( value );
    }

    public VersionNumber getCurrentVersionInfo()
    {
        return versionNumber;
    }

    private VersionNumber parseCurrentVersionInfo()
    {
        final String stringVersion = getSetting( Setting.currentVersion );

        if ( stringVersion == null || stringVersion.isEmpty() )
        {
            return VersionNumber.ZERO;
        }

        try
        {
            return VersionNumber.parse( stringVersion );
        }
        catch ( final Exception e )
        {
            LOGGER.info( () -> "error parsing version string from setting properties: " + e.getMessage() );
            return VersionNumber.ZERO;
        }
    }
}
