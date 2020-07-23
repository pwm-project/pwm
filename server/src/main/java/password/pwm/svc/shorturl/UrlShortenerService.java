/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.svc.shorturl;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Menno Pieters
 */
public class UrlShortenerService implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( UrlShortenerService.class );

    private PwmApplication pwmApplication;
    private BasicUrlShortener theShortener = null;
    private STATUS status = PwmService.STATUS.NEW;

    public UrlShortenerService( )
    {
    }

    public void init( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        final Configuration config = this.pwmApplication.getConfig();
        final String classNameString = config.readSettingAsString( PwmSetting.URL_SHORTENER_CLASS );
        if ( classNameString != null && classNameString.length() > 0 )
        {
            final Properties sConfig = new Properties();
            final List<String> sConfigList = config.readSettingAsStringArray( PwmSetting.URL_SHORTENER_PARAMETERS );
            // Parse configuration
            if ( sConfigList != null )
            {
                for ( final String p : sConfigList )
                {
                    final List<String> pl = Arrays.asList( p.split( "=", 2 ) );
                    if ( pl.size() == 2 )
                    {
                        sConfig.put( pl.get( 0 ), pl.get( 1 ) );
                    }
                }
            }
            try
            {
                final Class<?> theClass = Class.forName( classNameString );
                theShortener = ( BasicUrlShortener ) theClass.newInstance();
                theShortener.setConfiguration( sConfig );
            }
            catch ( final java.lang.IllegalAccessException e )
            {
                LOGGER.error( () ->  "illegal access to class " + classNameString + ": " + e.toString() );
            }
            catch ( final java.lang.InstantiationException e )
            {
                LOGGER.error( () -> "cannot instantiate class " + classNameString + ": " + e.toString() );
            }
            catch ( final java.lang.ClassNotFoundException e )
            {
                LOGGER.error( () -> "class " + classNameString + " not found: " + e.getMessage() );
            }
        }
        status = PwmService.STATUS.OPEN;
    }

    public STATUS status( )
    {
        return status;
    }

    public void close( )
    {
        status = PwmService.STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    public String shortenUrl( final String text ) throws PwmUnrecoverableException
    {
        if ( theShortener != null )
        {
            return theShortener.shorten( text, pwmApplication );
        }
        return text;
    }

    public String shortenUrlInText( final String text ) throws PwmUnrecoverableException
    {
        final String urlRegex = pwmApplication.getConfig().readAppProperty( AppProperty.URL_SHORTNER_URL_REGEX );
        try
        {
            final Pattern p = Pattern.compile( urlRegex );
            final Matcher m = p.matcher( text );
            final StringBuilder result = new StringBuilder();
            Boolean found = m.find();
            if ( found )
            {
                int start = 0;
                int end = m.start();
                result.append( text.substring( start, end ) );
                start = end;
                end = m.end();
                while ( found )
                {
                    result.append( shortenUrl( text.substring( start, end ) ) );
                    start = end;
                    found = m.find();
                    if ( found )
                    {
                        end = m.start();
                        result.append( text.substring( start, end ) );
                        start = end;
                        end = m.end();
                    }
                }
                result.append( text.substring( end ) );
                return result.toString();
            }
        }
        catch ( final PatternSyntaxException e )
        {
            LOGGER.error( () -> "error compiling pattern: " + e.getMessage() );
        }
        return text;
    }

    public ServiceInfoBean serviceInfo( )
    {
        return new ServiceInfoBean( Collections.<DataStorageMethod>emptyList() );
    }
}
