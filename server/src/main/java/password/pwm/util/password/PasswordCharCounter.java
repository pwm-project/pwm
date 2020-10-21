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

package password.pwm.util.password;

public class PasswordCharCounter
{
    private final String password;
    private final int passwordLength;

    public PasswordCharCounter( final String password )
    {
        this.password = password;
        this.passwordLength = password.length();
    }

    public int getNumericCharCount( )
    {
        return getNumericChars().length();
    }

    public String getNumericChars( )
    {
        return returnCharsOfType( password, CharType.NUMBER );
    }

    public int getUpperCharCount( )
    {
        return getUpperChars().length();
    }

    public String getUpperChars( )
    {
        return returnCharsOfType( password, CharType.UPPERCASE );
    }

    public int getAlphaCharCount( )
    {
        return getAlphaChars().length();
    }

    public String getAlphaChars( )
    {
        return returnCharsOfType( password, CharType.LETTER );
    }

    public int getNonAlphaCharCount( )
    {
        return getNonAlphaChars().length();
    }

    public String getNonAlphaChars( )
    {
        return returnCharsOfType( password, CharType.NON_LETTER );
    }

    public int getLowerCharCount( )
    {
        return getLowerChars().length();
    }

    public String getLowerChars( )
    {
        return returnCharsOfType( password, CharType.LOWERCASE );
    }

    public int getSpecialCharsCount( )
    {
        return getSpecialChars().length();
    }

    public String getSpecialChars( )
    {
        return returnCharsOfType( password, CharType.SPECIAL );
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

    public int getSequentialNumericChars( )
    {
        int numberOfRepeats = 0;

        for ( int i = 0; i < passwordLength - 1; i++ )
        {
            int loopRepeats = 0;
            for ( int j = i; j < passwordLength; j++ )
            {
                if ( Character.isDigit( password.charAt( j ) ) )
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

    public int getSequentialAlphaChars( )
    {
        int numberOfRepeats = 0;

        for ( int i = 0; i < passwordLength - 1; i++ )
        {
            int loopRepeats = 0;
            for ( int j = i; j < passwordLength; j++ )
            {
                if ( Character.isLetter( password.charAt( j ) ) )
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

    public int getUniqueChars( )
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

    public int getOtherLetterCharCount( )
    {
        return getOtherLetterChars().length();
    }

    public String getOtherLetterChars( )
    {
        return returnCharsOfType( password, CharType.OTHER_LETTER );
    }

    public boolean isFirstNumeric( )
    {
        return password.length() > 0 && Character.isDigit( password.charAt( 0 ) );
    }

    public boolean isLastNumeric( )
    {
        return password.length() > 0 && Character.isDigit( password.charAt( password.length() - 1 ) );
    }

    public boolean isFirstSpecial( )
    {
        return password.length() > 0 && !Character.isLetterOrDigit( password.charAt( 0 ) );
    }

    public boolean isLastSpecial( )
    {
        return password.length() > 0 && !Character.isLetterOrDigit( password.charAt( password.length() - 1 ) );
    }

    private static String returnCharsOfType( final String input, final CharType charType )
    {
        final int passwordLength = input.length();
        final StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < passwordLength; i++ )
        {
            final char nextChar = input.charAt( i );
            if ( charType.getCharTester().isType( nextChar ) )
            {
                sb.append( nextChar );
            }
        }
        return sb.toString();
    }

    private enum CharType
    {
        UPPERCASE( Character::isUpperCase ),
        LOWERCASE( Character::isLowerCase ),
        SPECIAL( character -> !Character.isLetterOrDigit( character ) ),
        NUMBER( Character::isDigit ),
        LETTER( Character::isLetter ),
        NON_LETTER( character -> !Character.isLetter( character ) ),
        OTHER_LETTER( character -> Character.getType( character ) == Character.OTHER_LETTER ),;

        private final transient CharTester charTester;

        CharType( final CharTester charClassType )
        {
            this.charTester = charClassType;
        }

        public CharTester getCharTester( )
        {
            return charTester;
        }
    }

    private interface CharTester
    {
        boolean isType( char character );
    }
}
