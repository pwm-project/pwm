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

package password.pwm.ldap;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.PasswordSyncCheckMode;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.util.ProgressInfoCalculator;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.Percent;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class PasswordChangeProgressChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordChangeProgressChecker.class );

    public static final String PROGRESS_KEY_REPLICATION = "replication";

    private final ProgressRecord completedReplicationRecord;
    private final PwmDomain pwmDomain;
    private final ChangePasswordProfile changePasswordProfile;
    private final UserIdentity userIdentity;
    private final SessionLabel pwmSession;
    private final Locale locale;

    private final PasswordSyncCheckMode passwordSyncCheckMode;

    public PasswordChangeProgressChecker(
            final PwmDomain pwmDomain,
            final ChangePasswordProfile changePasswordProfile,
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel,
            final Locale locale
    )
    {
        this.pwmDomain = pwmDomain;
        this.changePasswordProfile = changePasswordProfile;
        this.pwmSession = sessionLabel;
        this.userIdentity = userIdentity;
        this.locale = locale == null ? PwmConstants.DEFAULT_LOCALE : locale;

        if ( pwmDomain == null )
        {
            throw new IllegalArgumentException( "pwmApplication cannot be null" );
        }
        passwordSyncCheckMode = pwmDomain.getConfig().readSettingAsEnum( PwmSetting.PASSWORD_SYNC_ENABLE_REPLICA_CHECK, PasswordSyncCheckMode.class );

        completedReplicationRecord = makeReplicaProgressRecord( Percent.ONE_HUNDRED );
    }

    @Value
    public static class PasswordChangeProgress implements Serializable
    {
        private boolean complete;
        private BigDecimal percentComplete;
        private Collection<ProgressRecord> messages;
        private String elapsedSeconds;
        private String estimatedRemainingSeconds;

        public static final PasswordChangeProgress COMPLETE = new PasswordChangeProgress(
                true,
                Percent.ONE_HUNDRED.asBigDecimal( 2 ),
                Collections.emptyList(),
                "",
                ""
        );
    }

    @Value
    @Builder
    public static class ProgressRecord implements Serializable
    {
        private String key;
        private String label;
        private float percentComplete;
        private boolean complete;
        private boolean show;
    }

    @Data
    public static class ProgressTracker implements Serializable
    {
        private Instant beginTime = Instant.now();
        private Instant lastReplicaCheckTime;
        private final Map<String, ProgressRecord> itemCompletions = new HashMap<>();
    }

    public PasswordChangeProgress figureProgress(
            final ProgressTracker tracker
    )
    {
        if ( tracker == null )
        {
            throw new IllegalArgumentException( "tracker cannot be null" );
        }

        final Map<String, ProgressRecord> newItemProgress = new LinkedHashMap<>( tracker.itemCompletions );

        if ( tracker.beginTime == null || Instant.now().isAfter( maxCompletionTime( tracker ) ) )
        {
            return PasswordChangeProgress.COMPLETE;
        }

        newItemProgress.putAll( figureItemProgresses( tracker ) );
        final Instant estimatedCompletion = figureEstimatedCompletion( tracker, newItemProgress.values() );
        final long elapsedMs = TimeDuration.fromCurrent( tracker.beginTime ).asMillis();
        final long remainingMs = TimeDuration.fromCurrent( estimatedCompletion ).asMillis();

        final Percent percentage;
        if ( Instant.now().isAfter( estimatedCompletion ) )
        {
            percentage = Percent.ONE_HUNDRED;
        }
        else
        {
            final long totalMs = TimeDuration.between( tracker.beginTime, estimatedCompletion ).asMillis();
            percentage = Percent.of( elapsedMs, totalMs + 1 );
        }
        tracker.itemCompletions.putAll( newItemProgress );
        return new PasswordChangeProgress(
                percentage.isComplete(),
                percentage.asBigDecimal( 2 ),
                newItemProgress.values(),
                PwmTimeUtil.asLongString( TimeDuration.of( elapsedMs, TimeDuration.Unit.MILLISECONDS ), locale ),
                PwmTimeUtil.asLongString( TimeDuration.of( remainingMs, TimeDuration.Unit.MILLISECONDS ), locale )
        );
    }

    private Map<String, ProgressRecord> figureItemProgresses(
            final ProgressTracker tracker
    )
    {
        final Map<String, ProgressRecord> returnValue = new LinkedHashMap<>();

            // figure replication progress
            figureReplicationStatusCompletion( tracker )
                    .ifPresent( progressRecord -> returnValue.put( progressRecord.key, progressRecord ) );

        {
            // random
            /*
            final long randMs = PwmRandom.getInstance().nextInt(90 * 1000) + 30 * 1000;
            //final long randMs = 75 * 1000;
            final long elapsedMs = TimeDuration.fromCurrent(tracker.beginTime).asMillis();
            final Percent percent = new Percent(elapsedMs,randMs);
            final ProgressRecord record = new ProgressRecord();
            record.key = "randomItem";
            record.label = "Random Replication Delay " + randMs + "ms";
            record.percentComplete = percent.asBigDecimal();
            record.complete = percent.isComplete();
            record.show = true;
            returnValue.put(record.key, record);
            */
        }

        // insert more checks here @todo

        return returnValue;
    }

    public Instant maxCompletionTime( final ProgressTracker tracker )
    {
        final long maxWaitSeconds = changePasswordProfile.readSettingAsLong( PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME );
        final TimeDuration maxWait = TimeDuration.of( maxWaitSeconds, TimeDuration.Unit.SECONDS );
        return Instant.ofEpochMilli( tracker.beginTime.toEpochMilli() + maxWait.asMillis() );
    }

    private Instant minCompletionTime( final ProgressTracker tracker )
    {
        final long maxWaitSeconds = changePasswordProfile.readSettingAsLong( PwmSetting.PASSWORD_SYNC_MIN_WAIT_TIME );
        final TimeDuration minWait = TimeDuration.of( maxWaitSeconds, TimeDuration.Unit.SECONDS );
        return Instant.ofEpochMilli( tracker.beginTime.toEpochMilli() + minWait.asMillis() );
    }

    private Instant figureEstimatedCompletion(
            final ProgressTracker tracker,
            final Collection<ProgressRecord> progressRecords
    )
    {
        final Instant minCompletionTime = minCompletionTime( tracker );
        final Instant maxCompletionTime = maxCompletionTime( tracker );

        final Instant estimatedCompletion;
        {
            final float pctComplete = figureAverageProgress( progressRecords );
            LOGGER.trace( pwmSession, () -> "percent complete: " + pctComplete );
            final ProgressInfoCalculator progressInfoCalculator = ProgressInfoCalculator.createProgressInfo( tracker.beginTime, 100, ( long ) pctComplete );
            final Instant actualEstimate = progressInfoCalculator.estimatedCompletion();

            if ( actualEstimate.isBefore( minCompletionTime ) )
            {
                estimatedCompletion = minCompletionTime;
            }
            else if ( actualEstimate.isAfter( maxCompletionTime ) )
            {
                estimatedCompletion = maxCompletionTime;
            }
            else
            {
                estimatedCompletion = actualEstimate;
            }
        }
        return estimatedCompletion;
    }

    private float figureAverageProgress( final Collection<ProgressRecord> progressRecords )
    {
        int items = 0;
        BigDecimal sum = BigDecimal.ZERO;
        if ( progressRecords != null )
        {
            for ( final ProgressRecord progress : progressRecords )
            {
                if ( progress.percentComplete != 0 )
                {
                    items++;
                    sum = sum.add( new BigDecimal( progress.percentComplete ) );
                }
            }
        }

        if ( items > 0 )
        {
            return sum.divide( new BigDecimal( items ), MathContext.DECIMAL32 ).setScale( 2, RoundingMode.UP ).floatValue();
        }

        return Percent.ONE_HUNDRED.asBigDecimal( 2 ).floatValue();
    }


    private Optional<ProgressRecord> figureReplicationStatusCompletion( final ProgressTracker tracker )
    {
        final long initDelayMs = Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_PASSWORD_REPLICA_CHECK_INIT_DELAY_MS ) );
        final long cycleDelayMs = Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_PASSWORD_REPLICA_CHECK_CYCLE_DELAY_MS ) );
        final TimeDuration initialReplicaDelay = TimeDuration.of( initDelayMs, TimeDuration.Unit.MILLISECONDS );
        final TimeDuration cycleReplicaDelay = TimeDuration.of( cycleDelayMs, TimeDuration.Unit.MILLISECONDS );

        if ( passwordSyncCheckMode == PasswordSyncCheckMode.DISABLED )
        {
            LOGGER.trace( pwmSession, () -> "skipping replica sync check, disabled" );
            return Optional.of( tracker.getItemCompletions().get( PROGRESS_KEY_REPLICATION ) );
        }

        if ( tracker.getItemCompletions().containsKey( PROGRESS_KEY_REPLICATION ) )
        {
            if ( tracker.getItemCompletions().get( PROGRESS_KEY_REPLICATION ).complete )
            {
                LOGGER.trace( pwmSession, () -> "skipping replica sync check, replica sync completed previously" );
                return Optional.of( tracker.getItemCompletions().get( PROGRESS_KEY_REPLICATION ) );
            }
        }

        if ( tracker.lastReplicaCheckTime == null )
        {
            if ( TimeDuration.fromCurrent( tracker.beginTime ).isShorterThan( initialReplicaDelay ) )
            {
                LOGGER.trace( pwmSession, () -> "skipping replica sync check, initDelay has not yet passed" );
                return Optional.empty();
            }
        }
        else if ( TimeDuration.fromCurrent( tracker.lastReplicaCheckTime ).isShorterThan( cycleReplicaDelay ) )
        {
            LOGGER.trace( pwmSession, () -> "skipping replica sync check, cycleDelay has not yet passed" );
            return Optional.empty();
        }

        tracker.setLastReplicaCheckTime( Instant.now() );
        LOGGER.trace( pwmSession, () -> "beginning password replication time check for " + userIdentity.toDelimitedKey() );

        try
        {
            final Map<String, Instant> checkResults = PasswordUtility.readIndividualReplicaLastPasswordTimes( pwmDomain,
                    pwmSession, userIdentity );
            if ( checkResults.size() <= 1 )
            {
                LOGGER.trace( pwmSession, () -> "only one replica returned data, marking as complete" );
                return Optional.of( completedReplicationRecord );
            }
            else
            {
                final HashSet<Instant> tempHashSet = new HashSet<>();
                int duplicateValues = 0;
                for ( final Instant date : checkResults.values() )
                {
                    if ( tempHashSet.contains( date ) )
                    {
                        duplicateValues++;
                    }
                    else
                    {
                        tempHashSet.add( date );
                    }
                }
                final Percent pctComplete = Percent.of( duplicateValues + 1, checkResults.size() );
                final ProgressRecord progressRecord = makeReplicaProgressRecord( pctComplete );
                LOGGER.trace( pwmSession, () -> "read password replication sync status as: " + JsonFactory.get().serialize( progressRecord ) );
                return Optional.of( progressRecord );
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( pwmSession, () -> "error during password replication status check: " + e.getMessage() );
        }
        return Optional.empty();
    }

    private ProgressRecord makeReplicaProgressRecord( final Percent pctComplete )
    {
        final String label = LocaleHelper.getLocalizedMessage(
                locale,
                "Display_PasswordReplicationStatus",
                pwmDomain.getConfig(),
                Display.class,
                new String[]
                        {
                                pctComplete.pretty(),
                                }
        );

        return ProgressRecord.builder()
                .key( PROGRESS_KEY_REPLICATION )
                .complete( pctComplete.isComplete() )
                .percentComplete( pctComplete.asBigDecimal( 2 ).floatValue() )
                .show( passwordSyncCheckMode == PasswordSyncCheckMode.ENABLED_SHOW )
                .label( label )
                .build();
    }
}
