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

package password.pwm.util.macro;

import password.pwm.PwmConstants;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmDateFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public abstract class AbstractMacro implements Macro
{
    // match param values inside @ and include @ if preceded by /
    static final String PATTERN_OPTIONAL_PARAMETER_MATCH = "(:(?:/@|[^@])*)*";

    //split on : except if preceded by /
    static final String PATTERN_PARAMETER_SPLIT = "(?<![/]):";

    public AbstractMacro( )
    {
    }

    static String stripMacroDelimiters( final String input )
    {
        // strip leading / trailing @
        return input.replaceAll( "^@|@$", "" );
    }

    static String unescapeParamValue( final String input )
    {
        String result = input;
        result = result.replace( "/:", ":" );
        result = result.replace( "/@", "@" );
        return result;
    }

    static List<String> splitMacroParameters( final String input, final List<String> ignoreValueList )
    {
        final String strippedInput = stripMacroDelimiters( input );
        final String[] splitInput = strippedInput.split( PATTERN_PARAMETER_SPLIT );
        final List<String> returnObj = new ArrayList<>();
        for ( final String value : splitInput )
        {
            if ( !ignoreValueList.contains( value ) )
            {
                returnObj.add( unescapeParamValue( value ) );
                ignoreValueList.remove( value );
            }
        }
        return returnObj;
    }

    static PwmDateFormat readDateFormatAndTimeZoneParams( final List<String> parameters )
            throws MacroParseException
    {
        final String dateFormatStr;
        if ( parameters.size() > 0 && !parameters.get( 0 ).isEmpty() )
        {
            dateFormatStr = parameters.get( 0 );
        }
        else
        {
            dateFormatStr = PwmConstants.DEFAULT_DATETIME_FORMAT_STR;
        }

        final TimeZone tz;
        if ( parameters.size() > 1 && !parameters.get( 1 ).isEmpty() )
        {
            final String desiredTz = parameters.get( 1 );
            final List<String> availableIDs = Arrays.asList( TimeZone.getAvailableIDs() );
            if ( !availableIDs.contains( desiredTz ) )
            {
                throw new MacroParseException( "unknown timezone" );
            }
            tz = TimeZone.getTimeZone( desiredTz );
        }
        else
        {
            tz = PwmConstants.DEFAULT_TIMEZONE;
        }

        try
        {
            return PwmDateFormat.newPwmDateFormat( dateFormatStr, PwmConstants.DEFAULT_LOCALE, tz );
        }
        catch ( final IllegalArgumentException e )
        {
            throw new MacroParseException( e.getMessage() );
        }
    }

    static String processTimeOutputMacro( final Instant timestamp, final String matchValue, final List<String> ignoreValues )
            throws MacroParseException
    {
        if ( timestamp == null )
        {
            return "";
        }

        final List<String> parameters = splitMacroParameters( matchValue, ignoreValues );

        if ( !parameters.isEmpty() )
        {
            final PwmDateFormat pwmDateFormat = readDateFormatAndTimeZoneParams( parameters );

            try
            {
                return pwmDateFormat.format( timestamp );
            }
            catch ( final IllegalArgumentException e )
            {
                throw new MacroParseException( e.getMessage() );
            }
        }

        return JavaHelper.toIsoDate( timestamp );
    }

    @Override
    public Set<MacroDefinitionFlag> flags( )
    {
        return Collections.emptySet();
    }

    @Override
    public Sequence getSequence()
    {
        return Sequence.normal;
    }

}
