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

package password.pwm.svc.wordlist;

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.secure.PwmHashAlgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public enum WordType
{
    RAW( null ),
    MD5( PwmHashAlgorithm.MD5 ),
    SHA1( PwmHashAlgorithm.SHA1 ),
    SHA256( PwmHashAlgorithm.SHA256 ),
    SHA512( PwmHashAlgorithm.SHA512 ),;

    private static final String DELIMITER = ":";
    private static final Pattern HEX_CHAR_PATTERN = Pattern.compile( "^[0-9a-fA-F]*$" );

    private final String prefix;
    private final String suffix;
    private final PwmHashAlgorithm hashAlgorithm;

    WordType( final PwmHashAlgorithm pwmHashAlgorithm )
    {
        this.hashAlgorithm = pwmHashAlgorithm;
        prefix = ( this.name() + DELIMITER ).toLowerCase();
        suffix = ( DELIMITER + this.name() ).toLowerCase();
    }

    public String convertInputFromWordlist(
            final WordlistConfiguration wordlistConfiguration,
            final String input
    )
    {
        if ( this == RAW )
        {
            return !wordlistConfiguration.isCaseSensitive()
                    ? input.toLowerCase()
                    : input;
        }

        if ( input.startsWith( prefix ) )
        {
            final String strippedValue = input.substring( prefix.length() );
            return makeHashedStoredValue( strippedValue );
        }
        else
        {
            final String strippedValue = input.substring( 0, input.length() - suffix.length() );
            return makeHashedStoredValue( strippedValue );
        }
    }

    public String convertInputFromUser(
            final PwmApplication pwmApplication,
            final WordlistConfiguration wordlistConfiguration,
            final String input
    )
            throws PwmUnrecoverableException
    {
        if ( this == RAW )
        {
            return !wordlistConfiguration.isCaseSensitive()
                    ? input.toLowerCase()
                    : input;
        }

        final String hashedValue = pwmApplication.getSecureService().hash( this.hashAlgorithm, input );
        return makeHashedStoredValue( hashedValue );
    }

    private String makeHashedStoredValue( final String hash )
    {
        // stored hash first to improve sorting/storage efficiency
        return hash.toLowerCase() + DELIMITER + name();
    }

    public static WordType determineWordType( final String input )
    {
        Objects.requireNonNull( input );

        for ( final WordType wordType : NonRawTypeSingleton.NON_RAW_TYPES )
        {
            if ( wordType.matchesType( input ) )
            {
                return wordType;
            }
        }

        return RAW;
    }

    private boolean matchesType( final String input )
    {
        if ( this == RAW )
        {
            return true;
        }

        if ( input.length() <= prefix.length() )
        {
            return false;
        }

        final String lowerCaseInputPrefix = input.substring( 0, this.prefix.length() ).toLowerCase();
        if ( lowerCaseInputPrefix.equals( prefix ) )
        {
            final String hashValue = input.substring( prefix.length() );
            if ( hashValue.length() == this.hashAlgorithm.getHexValueLength() )
            {
                return HEX_CHAR_PATTERN.matcher( hashValue ).matches();
            }
        }

        final String lowerCaseInputSuffix = input.substring( input.length() - this.suffix.length() ).toLowerCase();
        if ( lowerCaseInputSuffix.equals( suffix ) )
        {
            final String hashValue = input.substring( 0, input.length() - suffix.length() );
            if ( hashValue.length() == this.hashAlgorithm.getHexValueLength() )
            {
                return HEX_CHAR_PATTERN.matcher( hashValue ).matches();
            }
        }

        return false;
    }

    private static class NonRawTypeSingleton
    {
        private static final WordType[] NON_RAW_TYPES;

        static
        {
            final List<WordType> wordTypes = new ArrayList<>( Arrays.asList( WordType.values() ) );
            wordTypes.remove( RAW );
            NON_RAW_TYPES = wordTypes.toArray( new WordType[0] );
        }
    }
}
