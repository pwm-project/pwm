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

package password.pwm.util.macro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractMacro implements MacroImplementation
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

    static List<String> splitMacroParameters( final String input, final String... ignoreValues )
    {
        final String strippedInput = stripMacroDelimiters( input );
        final String[] splitInput = strippedInput.split( PATTERN_PARAMETER_SPLIT );
        final List<String> returnObj = new ArrayList<>();
        final List<String> ignoreValueList = Arrays.asList( ignoreValues );
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

    @Override
    public MacroDefinitionFlag[] flags( )
    {
        return null;
    }
}
