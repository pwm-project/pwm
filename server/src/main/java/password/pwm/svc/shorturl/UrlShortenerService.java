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

package password.pwm.svc.shorturl;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
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
public class UrlShortenerService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UrlShortenerService.class );

    private BasicUrlShortener theShortener = null;

    public UrlShortenerService( )
    {
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        final AppConfig config = pwmApplication.getConfig();
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
                theShortener = ( BasicUrlShortener ) theClass.getDeclaredConstructor().newInstance();
                theShortener.setConfiguration( sConfig );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error loading url shortener class " + classNameString + ": " + e.getMessage() );
            }
        }

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( PwmService.STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    public String shortenUrl( final String text, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        if ( theShortener != null )
        {
            return theShortener.shorten( text, getPwmApplication(), sessionLabel );
        }
        return text;
    }

    public String shortenUrlInText( final String text, final SessionLabel sessionLabel ) throws PwmUnrecoverableException
    {
        final String urlRegex = getPwmApplication().getConfig().readAppProperty( AppProperty.URL_SHORTNER_URL_REGEX );
        try
        {
            final Pattern p = Pattern.compile( urlRegex );
            final Matcher m = p.matcher( text );
            final StringBuilder result = new StringBuilder();
            boolean found = m.find();
            if ( found )
            {
                int start = 0;
                int end = m.start();
                result.append( text, start, end );
                start = end;
                end = m.end();
                while ( found )
                {
                    result.append( shortenUrl( text.substring( start, end ), sessionLabel ) );
                    start = end;
                    found = m.find();
                    if ( found )
                    {
                        end = m.start();
                        result.append( text, start, end );
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

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder().build();
    }
}
