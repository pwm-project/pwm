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

package password.pwm.svc.wordlist;

import password.pwm.PwmApplication;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.AtomicLoopLongIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public abstract class AbstractWordlistBucket implements WordlistBucket
{
    protected final PwmApplication pwmApplication;
    protected final WordlistConfiguration wordlistConfiguration;
    protected final WordlistType type;

    public AbstractWordlistBucket( final PwmApplication pwmApplication, final WordlistConfiguration wordlistConfiguration, final WordlistType type )
    {
        this.pwmApplication = pwmApplication;
        this.wordlistConfiguration = wordlistConfiguration;
        this.type = type;
    }

    private static String seedlistLongToKey( final long longValue )
    {
        return Long.toString( longValue, 36 );
    }

    private Map<String, String> getWriteTxnForValue(
            final Collection<String> words,
            final AtomicLoopLongIncrementer valueIncrementer
    )
    {
        switch ( type )
        {
            case SEEDLIST:
            {
                final Map<String, String> returnSet = new TreeMap<>();
                for ( final String word : words )
                {
                    if ( !StringUtil.isEmpty( word ) )
                    {
                        final long nextLong = valueIncrementer.next();
                        final String nextKey = seedlistLongToKey( nextLong );
                        returnSet.put( nextKey, word );
                    }
                }
                return Collections.unmodifiableMap( returnSet );
            }

            case WORDLIST:
            {
                final Map<String, String> returnSet = new TreeMap<>();
                for ( final String word : words )
                {
                    if ( !StringUtil.isEmpty( word ) )
                    {
                        valueIncrementer.next();
                        returnSet.put( word, "" );
                    }
                }
                return returnSet;
            }

            default:
                JavaHelper.unhandledSwitchStatement( type );
        }

        throw new IllegalStateException( "unreachable switch statement" );
    }

    @Override
    public void addWords( final Collection<String> words, final AbstractWordlist abstractWordlist )
            throws PwmUnrecoverableException
    {
        final WordlistStatus initialStatus = abstractWordlist.readWordlistStatus();
        final AtomicLoopLongIncrementer valueIncrementer = AtomicLoopLongIncrementer.builder().initial( initialStatus.getValueCount() ).build();
        this.putValues( getWriteTxnForValue( words, valueIncrementer ) );

        if ( initialStatus.getValueCount() != valueIncrementer.get() )
        {
            final WordlistStatus incrementedStatus = initialStatus.toBuilder().valueCount( valueIncrementer.get() ).build();
            abstractWordlist.writeWordlistStatus( incrementedStatus );
        }
    }

    @Override
    public String randomSeed() throws PwmUnrecoverableException
    {
        if ( type == WordlistType.WORDLIST )
        {
            throw new IllegalStateException( "unable to read randomSeed from WORDLIST wordlist" );
        }

        try
        {
            final long seedCount = size();
            if ( seedCount > 1000 )
            {
                final long randomKey = pwmApplication.getSecureService().pwmRandom().nextLong( seedCount );
                return getValue( seedlistLongToKey( randomKey ) );
            }
        }
        catch ( final Exception e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "error while generating random word: " + e.getMessage() );
        }

        throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "seedlist word not available" );
    }

    @Override
    public boolean containsWord( final String word ) throws PwmUnrecoverableException
    {
        if ( type == WordlistType.SEEDLIST )
        {
            throw new IllegalStateException( "unable to containWord check SEEDLIST wordlist" );
        }

        return containsKey( word );
    }

    abstract void putValues( Map<String, String> values )
            throws PwmUnrecoverableException;

    abstract boolean containsKey( String key )
            throws PwmUnrecoverableException;

    abstract String getValue( String key )
            throws PwmUnrecoverableException;
}
