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

package password.pwm.util.password;

import java.util.EnumMap;
import java.util.Map;

class PasswordCharCounter
{
    private final String password;
    private final int passwordLength;
    private final Map<PasswordCharType, String> cache = new EnumMap<>( PasswordCharType.class );

    PasswordCharCounter( final String password )
    {
        this.password = password;
        this.passwordLength = password.length();
    }

    public int charTypeCount( final PasswordCharType passwordCharType )
    {
        return charsOfType( passwordCharType ).length();
    }

    public boolean hasCharsOfType( final PasswordCharType passwordCharType )
    {
        return charTypeCount( passwordCharType ) > 0;
    }

    public String charsOfType( final PasswordCharType passwordCharType )
    {
        return cache.computeIfAbsent( passwordCharType, type -> PasswordCharType.charsOfType( password, type ) );
    }

    public int getRepeatedChars( )
    {
        int numberOfRepeats = 0;
        final CharSequence passwordL = password.toLowerCase();

        for ( int i = 0; i < passwordLength - 1; i++ )
        {
            int loopRepeats = 0;
            final char loopChar = passwordL.charAt( i );
            for ( int j = i; j < passwordLength; j++ )
            {
                if ( loopChar == passwordL.charAt( j ) )
                {
                    loopRepeats++;
                }
            }

            if ( loopRepeats > numberOfRepeats )
            {
                numberOfRepeats = loopRepeats;
            }
        }
        return numberOfRepeats;
    }

    public int getSequentialRepeatedChars( )
    {
        int numberOfRepeats = 0;
        final CharSequence passwordL = password.toLowerCase();

        for ( int i = 0; i < passwordLength - 1; i++ )
        {
            int loopRepeats = 0;
            final char loopChar = passwordL.charAt( i );
            for ( int j = i; j < passwordLength; j++ )
            {
                if ( loopChar == passwordL.charAt( j ) )
                {
                    loopRepeats++;
                }
                else
                {
                    break;
                }
            }

            if ( loopRepeats > numberOfRepeats )
            {
                numberOfRepeats = loopRepeats;
            }
        }
        return numberOfRepeats;
    }

    public int sequentialCharCountOfType( final PasswordCharType passwordCharType )
    {
        int numberOfRepeats = 0;

        for ( int i = 0; i < passwordLength - 1; i++ )
        {
            int loopRepeats = 0;
            for ( int j = i; j < passwordLength; j++ )
            {
                if ( passwordCharType.isCharType( password.charAt( j ) ) )
                {
                    loopRepeats++;
                }
                else
                {
                    break;
                }
            }
            if ( loopRepeats > numberOfRepeats )
            {
                numberOfRepeats = loopRepeats;
            }
        }

        return numberOfRepeats;
    }

    public int uniqueCharCount( )
    {
        final StringBuilder sb = new StringBuilder();
        final String passwordL = password.toLowerCase();
        for ( int i = 0; i < passwordLength; i++ )
        {
            final char loopChar = passwordL.charAt( i );
            if ( sb.indexOf( String.valueOf( loopChar ) ) == -1 )
            {
                sb.append( loopChar );
            }
        }
        return sb.length();
    }

    public boolean isFirstCharType( final PasswordCharType passwordCharType )
    {
        return password.length() > 0 && passwordCharType.isCharType( password.charAt( 0 ) );
    }

    public boolean isLastCharType( final PasswordCharType passwordCharType )
    {
        return password.length() > 0 && passwordCharType.isCharType( password.charAt( password.length() - 1 ) );
    }

    String getPassword()
    {
        return password;
    }
}
