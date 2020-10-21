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

package password.pwm.util;

import lombok.Builder;
import lombok.Value;
import password.pwm.util.java.TimeDuration;

import java.util.Objects;

public class TransactionSizeCalculator
{
    private final Settings settings;
    private volatile int transactionSize;
    private volatile long lastDuration = 1;

    public TransactionSizeCalculator( final Settings settings )
    {
        settings.validateSettings();
        this.settings = settings;
        reset();
    }

    public void reset( )
    {
        transactionSize = settings.getMinTransactions();
        lastDuration = settings.getDurationGoal().asMillis();
    }

    public void recordLastTransactionDuration( final long duration )
    {
        recordLastTransactionDuration( TimeDuration.of( duration, TimeDuration.Unit.MILLISECONDS ) );
    }

    @SuppressWarnings( "ResultOfMethodCallIgnored" )
    public void pause( )
    {
        final long pauseTimeMs = Math.min( lastDuration, settings.getDurationGoal().asMillis() * 2 );
        TimeDuration.of( pauseTimeMs, TimeDuration.Unit.MILLISECONDS ).pause();
    }

    public void recordLastTransactionDuration( final TimeDuration duration )
    {
        Objects.requireNonNull( duration );

        lastDuration = duration.asMillis();
        final long durationGoalMs = settings.getDurationGoal().asMillis();
        final long difference = Math.abs( duration.asMillis() - durationGoalMs );
        final int closeThreshold = ( int ) ( durationGoalMs * .15f );

        int newTransactionSize;
        if ( duration.isShorterThan( settings.getDurationGoal() ) )
        {
            if ( difference > closeThreshold )
            {
                newTransactionSize = ( ( int ) ( transactionSize + ( transactionSize * 0.1 ) ) + 1 );
            }
            else
            {
                newTransactionSize = transactionSize + 1;
            }
        }
        else if ( duration.isLongerThan( settings.getDurationGoal() ) )
        {
            if ( difference > ( 10 * durationGoalMs ) )
            {
                newTransactionSize = settings.getMinTransactions();
            }
            else if ( difference > ( 2 * durationGoalMs ) )
            {
                newTransactionSize = transactionSize / 2;
            }
            else if ( difference > closeThreshold )
            {
                newTransactionSize = ( ( int ) ( transactionSize - ( transactionSize * 0.1 ) ) - 1 );
            }
            else
            {
                newTransactionSize = transactionSize - 1;
            }
        }
        else
        {
            newTransactionSize = transactionSize;
        }

        newTransactionSize = Math.min( newTransactionSize, settings.getMaxTransactions() );
        newTransactionSize = Math.max( newTransactionSize, settings.getMinTransactions() );
        this.transactionSize = newTransactionSize;
    }

    public int getTransactionSize( )
    {
        return transactionSize;
    }

    @Value
    @Builder
    public static class Settings
    {
        @Builder.Default
        private TimeDuration durationGoal = TimeDuration.of( 100, TimeDuration.Unit.MILLISECONDS );

        @Builder.Default
        private int maxTransactions = 5003;

        @Builder.Default
        private int minTransactions = 3;

        private void validateSettings( )
        {
            if ( minTransactions < 1 )
            {
                throw new IllegalArgumentException( "minTransactions must be a positive integer" );
            }

            if ( maxTransactions < 1 )
            {
                throw new IllegalArgumentException( "maxTransactions must be a positive integer" );
            }

            if ( minTransactions > maxTransactions )
            {
                throw new IllegalArgumentException( "minTransactions must be less than maxTransactions" );
            }

            if ( durationGoal == null )
            {
                throw new IllegalArgumentException( "durationGoal must not be null" );
            }

            if ( durationGoal.asMillis() < 1 )
            {
                throw new IllegalArgumentException( "durationGoal must be greater than 0ms" );
            }
        }
    }
}
