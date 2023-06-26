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

import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import password.pwm.util.secure.PwmRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class MutablePassword
{
    private final RandomGeneratorRequest request;
    private final SeedMachine seedMachine;
    private final PwmRandom pwmRandom;

    private final StringBuilder password = new StringBuilder();

    private PasswordCharCounter passwordCharCounter;

    MutablePassword(
            final RandomGeneratorRequest request,
            final SeedMachine seedMachine,
            final PwmRandom pwmRandom,
            final CharSequence password
    )
    {
        this.request = request;
        this.seedMachine = seedMachine;
        this.pwmRandom = pwmRandom;
        this.reset( password );
    }

    String value()
    {
        return password.toString();
    }

    void reset( final CharSequence value )
    {
        password.delete( 0, password.length() );
        password.append( value == null ? "" : value );
    }

    public PwmRandom getPwmRandom()
    {
        return pwmRandom;
    }

    PasswordCharCounter getPasswordCharCounter()
    {
        final String passwordString = password.toString();
        if ( passwordCharCounter != null
                && Objects.equals( passwordCharCounter.getPassword(), passwordString ) )
        {
            return passwordCharCounter;
        }

        passwordCharCounter = new PasswordCharCounter( passwordString );
        return passwordCharCounter;
    }

    void randomizeCasing()
    {
        for ( int i = 0; i < password.length(); i++ )
        {
            final int randspot = pwmRandom.nextInt( password.length() );
            final char oldChar = password.charAt( randspot );
            if ( Character.isLetter( oldChar ) )
            {
                final char newChar = Character.isUpperCase( oldChar )
                        ? Character.toLowerCase( oldChar )
                        : Character.toUpperCase( oldChar );
                password.deleteCharAt( randspot );
                password.insert( randspot, newChar );
                return;
            }
        }
    }

    public void addRandChar()
            throws ImpossiblePasswordPolicyException
    {
        final List<PasswordCharType> possibleCharTypes =
                new ArrayList<>( request.maxCharsPerType().keySet() );
        final PasswordCharType charType = possibleCharTypes.get( pwmRandom.nextInt( possibleCharTypes.size() ) );
        addRandCharImpl( charType );
    }

    public void addRandCharExceptType(
            final PasswordCharType notType
    )
            throws ImpossiblePasswordPolicyException
    {
        final List<PasswordCharType> possibleCharTypes =
                new ArrayList<>( request.maxCharsPerType().keySet() );
        possibleCharTypes.remove( notType );
        final PasswordCharType charType = possibleCharTypes.get( pwmRandom.nextInt( possibleCharTypes.size() ) );
        addRandCharImpl( charType );
    }

    public void addRandChar( final PasswordCharType charType )
    {
        addRandCharImpl( charType );
    }

    private void addRandCharImpl( final PasswordCharType charType )
    {
        final int insertPosition = password.length() < 1 ? 0 : pwmRandom.nextInt( password.length() );
        final String possibleCharsToAdd = seedMachine.charsOfType( charType );
        final char charToAdd = possibleCharsToAdd.charAt( pwmRandom.nextInt( possibleCharsToAdd.length() ) );
        password.insert( insertPosition, charToAdd );
    }

    void deleteRandChar()
    {
        if ( password.length() == 0 )
        {
            return;
        }
        password.deleteCharAt( pwmRandom.nextInt( password.length() - 1 ) );
    }

    void deleteFirstChar()
    {
        password.deleteCharAt( 0 );
    }

    void deleteLastChar()
    {
        password.deleteCharAt( password.length() );
    }


    public void deleteRandCharExceptType(
            final PasswordCharType notType
    )
            throws ImpossiblePasswordPolicyException
    {
        final List<PasswordCharType> possibleCharTypes = new ArrayList<>();
        for ( final PasswordCharType charType : PasswordCharType.uniqueTypes() )
        {
            if ( charType != notType && getPasswordCharCounter().hasCharsOfType( charType ) )
            {
                possibleCharTypes.add( charType );
            }
        }

        if ( possibleCharTypes.isEmpty() )
        {
            deleteRandChar();
        }
        else
        {
            final PasswordCharType charType = possibleCharTypes.get( pwmRandom.nextInt( possibleCharTypes.size() ) );
            deleteRandChar( charType );
        }
    }

    void deleteRandChar(
            final PasswordCharType passwordCharType
    )
            throws ImpossiblePasswordPolicyException
    {
        // no need to iterate the entire pw for large values.
        final int maxDiscoverCount = 25;

        final String charsToRemove = getPasswordCharCounter().charsOfType( passwordCharType );
        final List<Integer> removePossibilities = new ArrayList<>();
        for ( int i = 0; i < password.length() && removePossibilities.size() < maxDiscoverCount; i++ )
        {
            final char loopChar = password.charAt( i );
            final int index = charsToRemove.indexOf( loopChar );
            if ( index != -1 )
            {
                removePossibilities.add( i );
            }
        }
        if ( removePossibilities.isEmpty() )
        {
            throw new ImpossiblePasswordPolicyException( ImpossiblePasswordPolicyException.ErrorEnum.UNEXPECTED_ERROR );
        }
        final Integer charToDelete = removePossibilities.get( pwmRandom.nextInt( removePossibilities.size() ) );
        password.deleteCharAt( charToDelete );
    }

    public void randomPasswordCharModifier(
    )
    {
        switch ( pwmRandom.nextInt( 10 ) )
        {
            case 0 -> addRandChar( PasswordCharType.SPECIAL );
            case 1 -> addRandChar( PasswordCharType.NUMBER );
            case 2 -> addRandChar( PasswordCharType.UPPERCASE );
            case 3 -> addRandChar( PasswordCharType.LOWERCASE );
            case 4, 5, 6, 7 -> addRandChar( PasswordCharType.LETTER );
            default -> randomizeCasing();
        }
    }

}
