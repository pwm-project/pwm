/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.ldap;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.PasswordSyncCheckMode;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;
import password.pwm.util.ProgressInfo;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.Percent;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;

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

public class PasswordChangeProgressChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordChangeProgressChecker.class );

    public static final String PROGRESS_KEY_REPLICATION = "replication";

    private final ProgressRecord completedReplicationRecord;
    private final PwmApplication pwmApplication;
    private final UserIdentity userIdentity;
    private final SessionLabel pwmSession;
    private final Locale locale;

    private final PasswordSyncCheckMode passwordSyncCheckMode;

    public PasswordChangeProgressChecker(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel,
            final Locale locale
    )
    {
        this.pwmApplication = pwmApplication;
        this.pwmSession = sessionLabel;
        this.userIdentity = userIdentity;
        this.locale = locale == null ? PwmConstants.DEFAULT_LOCALE : locale;

        if ( pwmApplication == null )
        {
            throw new IllegalArgumentException( "pwmApplication cannot be null" );
        }
        passwordSyncCheckMode = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.PASSWORD_SYNC_ENABLE_REPLICA_CHECK, PasswordSyncCheckMode.class );

        completedReplicationRecord = makeReplicaProgressRecord( Percent.ONE_HUNDRED );
    }

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

        public PasswordChangeProgress(
                final boolean complete,
                final BigDecimal percentComplete,
                final Collection<ProgressRecord> messages,
                final String elapsedSeconds,
                final String estimatedRemainingSeconds
        )
        {
            this.complete = complete;
            this.percentComplete = percentComplete;
            this.messages = messages;
            this.elapsedSeconds = elapsedSeconds;
            this.estimatedRemainingSeconds = estimatedRemainingSeconds;
        }

        public boolean isComplete( )
        {
            return complete;
        }

        public BigDecimal getPercentComplete( )
        {
            return percentComplete;
        }

        public Collection<ProgressRecord> getItemCompletion( )
        {
            return messages;
        }
    }

    public static class ProgressRecord implements Serializable
    {
        private String key;
        private String label;
        private BigDecimal percentComplete;
        private boolean complete;
        private boolean show;
    }

    public static class ProgressTracker implements Serializable
    {
        private Instant beginTime = Instant.now();
        private Instant lastReplicaCheckTime;
        private final Map<String, ProgressRecord> itemCompletions = new HashMap<>();

        public Instant getBeginTime( )
        {
            return beginTime;
        }

        public Instant getLastReplicaCheckTime( )
        {
            return lastReplicaCheckTime;
        }

        public Map<String, ProgressRecord> getItemCompletions( )
        {
            return itemCompletions;
        }
    }

    public PasswordChangeProgress figureProgress(
            final ProgressTracker tracker
    )
    {
        if ( tracker == null )
        {
            throw new IllegalArgumentException( "tracker cannot be null" );
        }

        final Map<String, ProgressRecord> newItemProgress = new LinkedHashMap<>();
        newItemProgress.putAll( tracker.itemCompletions );

        if ( tracker.beginTime == null || Instant.now().isAfter( maxCompletionTime( tracker ) ) )
        {
            return PasswordChangeProgress.COMPLETE;
        }

        newItemProgress.putAll( figureItemProgresses( tracker ) );
        final Instant estimatedCompletion = figureEstimatedCompletion( tracker, newItemProgress.values() );
        final long elapsedMs = TimeDuration.fromCurrent( tracker.beginTime ).getTotalMilliseconds();
        final long remainingMs = TimeDuration.fromCurrent( estimatedCompletion ).getTotalMilliseconds();

        final Percent percentage;
        if ( Instant.now().isAfter( estimatedCompletion ) )
        {
            percentage = Percent.ONE_HUNDRED;
        }
        else
        {
            final long totalMs = new TimeDuration( tracker.beginTime, estimatedCompletion ).getTotalMilliseconds();
            percentage = new Percent( elapsedMs, totalMs + 1 );
        }
        tracker.itemCompletions.putAll( newItemProgress );
        return new PasswordChangeProgress(
                percentage.isComplete(),
                percentage.asBigDecimal( 2 ),
                newItemProgress.values(),
                new TimeDuration( elapsedMs ).asLongString( locale ),
                new TimeDuration( remainingMs ).asLongString( locale )
        );
    }

    private Map<String, ProgressRecord> figureItemProgresses(
            final ProgressTracker tracker
    )
    {
        final Map<String, ProgressRecord> returnValue = new LinkedHashMap<>();

        {
            // figure replication progress
            final ProgressRecord replicationProgress = figureReplicationStatusCompletion( tracker );
            if ( replicationProgress != null )
            {
                returnValue.put( replicationProgress.key, replicationProgress );
            }
        }


        {
            // random
            /*
            final long randMs = PwmRandom.getInstance().nextInt(90 * 1000) + 30 * 1000;
            //final long randMs = 75 * 1000;
            final long elapsedMs = TimeDuration.fromCurrent(tracker.beginTime).getTotalMilliseconds();
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
        final TimeDuration maxWait = new TimeDuration( pwmApplication.getConfig().readSettingAsLong( PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME ) * 1000 );
        return Instant.ofEpochMilli( tracker.beginTime.toEpochMilli() + maxWait.getTotalMilliseconds() );
    }

    private Instant minCompletionTime( final ProgressTracker tracker )
    {
        final TimeDuration minWait = new TimeDuration( pwmApplication.getConfig().readSettingAsLong( PwmSetting.PASSWORD_SYNC_MIN_WAIT_TIME ) * 1000 );
        return Instant.ofEpochMilli( tracker.beginTime.toEpochMilli() + minWait.getTotalMilliseconds() );
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
            final BigDecimal pctComplete = figureAverageProgress( progressRecords );
            LOGGER.trace( pwmSession, "percent complete: " + pctComplete );
            final ProgressInfo progressInfo = new ProgressInfo( tracker.beginTime, 100, pctComplete.longValue() );
            final Instant actualEstimate = progressInfo.estimatedCompletion();

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

    private BigDecimal figureAverageProgress( final Collection<ProgressRecord> progressRecords )
    {
        int items = 0;
        BigDecimal sum = BigDecimal.ZERO;
        if ( progressRecords != null )
        {
            for ( final ProgressRecord progress : progressRecords )
            {
                if ( progress.percentComplete != null )
                {
                    items++;
                    sum = sum.add( progress.percentComplete );
                }
            }
        }

        if ( items > 0 )
        {
            return sum.divide( new BigDecimal( items ), MathContext.DECIMAL32 ).setScale( 2, RoundingMode.UP );
        }

        return Percent.ONE_HUNDRED.asBigDecimal( 2 );
    }


    private ProgressRecord figureReplicationStatusCompletion(
            final ProgressTracker tracker

    )
    {
        final long initDelayMs = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_PASSWORD_REPLICA_CHECK_INIT_DELAY_MS ) );
        final long cycleDelayMs = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_PASSWORD_REPLICA_CHECK_CYCLE_DELAY_MS ) );
        final TimeDuration initialReplicaDelay = new TimeDuration( initDelayMs );
        final TimeDuration cycleReplicaDelay = new TimeDuration( cycleDelayMs );

        if ( passwordSyncCheckMode == PasswordSyncCheckMode.DISABLED )
        {
            LOGGER.trace( pwmSession, "skipping replica sync check, disabled" );
            return tracker.itemCompletions.get( PROGRESS_KEY_REPLICATION );
        }

        if ( tracker.itemCompletions.containsKey( PROGRESS_KEY_REPLICATION ) )
        {
            if ( tracker.itemCompletions.get( PROGRESS_KEY_REPLICATION ).complete )
            {
                LOGGER.trace( pwmSession, "skipping replica sync check, replica sync completed previously" );
                return tracker.itemCompletions.get( PROGRESS_KEY_REPLICATION );
            }
        }

        if ( tracker.lastReplicaCheckTime == null )
        {
            if ( TimeDuration.fromCurrent( tracker.beginTime ).isShorterThan( initialReplicaDelay ) )
            {
                LOGGER.trace( pwmSession, "skipping replica sync check, initDelay has not yet passed" );
                return null;
            }
        }
        else if ( TimeDuration.fromCurrent( tracker.lastReplicaCheckTime ).isShorterThan( cycleReplicaDelay ) )
        {
            LOGGER.trace( pwmSession, "skipping replica sync check, cycleDelay has not yet passed" );
            return null;
        }

        tracker.lastReplicaCheckTime = Instant.now();
        LOGGER.trace( pwmSession, "beginning password replication time check for " + userIdentity.toDelimitedKey() );

        try
        {
            final Map<String, Instant> checkResults = PasswordUtility.readIndividualReplicaLastPasswordTimes( pwmApplication,
                    pwmSession, userIdentity );
            if ( checkResults.size() <= 1 )
            {
                LOGGER.trace( "only one replica returned data, marking as complete" );
                return completedReplicationRecord;
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
                final Percent pctComplete = new Percent( duplicateValues + 1, checkResults.size() );
                final ProgressRecord progressRecord = makeReplicaProgressRecord( pctComplete );
                LOGGER.trace( "read password replication sync status as: " + JsonUtil.serialize( progressRecord ) );
                return progressRecord;
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( pwmSession, "error during password replication status check: " + e.getMessage() );
        }
        return null;
    }

    private ProgressRecord makeReplicaProgressRecord( final Percent pctComplete )
    {
        final ProgressRecord progressRecord = new ProgressRecord();
        progressRecord.key = PROGRESS_KEY_REPLICATION;
        progressRecord.complete = pctComplete.isComplete();
        progressRecord.percentComplete = pctComplete.asBigDecimal( 2 );
        progressRecord.show = passwordSyncCheckMode == PasswordSyncCheckMode.ENABLED_SHOW;
        progressRecord.label = LocaleHelper.getLocalizedMessage(
                locale,
                "Display_PasswordReplicationStatus",
                pwmApplication.getConfig(),
                Display.class,
                new String[]
                        {
                                pctComplete.pretty(),
                        }
        );

        return progressRecord;
    }
}
