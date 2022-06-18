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

package password.pwm.svc.event;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmException;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.Percent;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;

public class LocalDbAuditVault implements AuditVault
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDbAuditVault.class );

    private LocalDBStoredQueue auditDB;
    private AuditSettings settings;
    private Instant oldestRecord;

    private ScheduledExecutorService executorService;
    private volatile PwmService.STATUS status = PwmService.STATUS.CLOSED;


    public LocalDbAuditVault(
    )
            throws LocalDBException
    {
    }

    @Override
    public void init(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final LocalDB localDB,
            final AuditSettings settings
    )
            throws PwmException
    {
        this.settings = settings;
        this.auditDB = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, localDB, LocalDB.DB.AUDIT_EVENTS );

        readOldestRecord();

        executorService = PwmScheduler.makeBackgroundServiceExecutor( pwmApplication, sessionLabel, this.getClass() );

        status = PwmService.STATUS.OPEN;
        final TimeDuration jobFrequency = TimeDuration.of( 10, TimeDuration.Unit.MINUTES );
        pwmApplication.getPwmScheduler().scheduleFixedRateJob( new TrimmerThread(), executorService, TimeDuration.SECONDS_10, jobFrequency );
    }

    @Override
    public void close( )
    {
        executorService.shutdown();
        status = PwmService.STATUS.CLOSED;
    }

    public PwmService.STATUS getStatus( )
    {
        return status;
    }

    @Override
    public Instant oldestRecord( )
    {
        return oldestRecord;
    }

    @Override
    public int size( )
    {
        return auditDB.size();
    }

    @Override
    public Iterator<AuditRecord> readVault( )
    {
        return new IteratorWrapper( auditDB.descendingIterator() );
    }

    private static class IteratorWrapper implements Iterator<AuditRecord>
    {
        private final Iterator<String> innerIter;

        private IteratorWrapper( final Iterator<String> innerIter )
        {
            this.innerIter = innerIter;
        }

        @Override
        public boolean hasNext( )
        {
            return innerIter.hasNext();
        }

        @Override
        public AuditRecord next( )
        {
            final String value = innerIter.next();
            return deSerializeRecord( value );
        }

        @Override
        public void remove( )
        {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String sizeToDebugString( )
    {
        final long storedEvents = this.size();
        final long maxEvents = settings.getMaxRecords();
        final Percent percent = Percent.of( storedEvents, maxEvents );

        return storedEvents + " / " + maxEvents + " (" + percent.pretty( 2 ) + ")";
    }

    private static AuditRecord deSerializeRecord( final String input )
    {
        try
        {
            return JsonFactory.get().deserialize( input, AuditRecordData.class );
        }
        catch ( final Exception e )
        {
            final String finalErrorMsg = e.getMessage();
            LOGGER.debug( () -> "unable to deserialize stored record '" + input + "', error: " + finalErrorMsg );
        }

        return null;
    }

    @Override
    public void add( final AuditRecord record )
    {
        if ( record == null )
        {
            return;
        }

        final String jsonRecord = JsonFactory.get().serialize( record );
        auditDB.addLast( jsonRecord );

        if ( auditDB.size() > settings.getMaxRecords() )
        {
            removeRecords( 1 );
        }
    }

    private void readOldestRecord( )
    {
        if ( auditDB != null && !auditDB.isEmpty() )
        {
            final String stringFirstRecord = auditDB.getFirst();
            final AuditRecordData firstRecord = JsonFactory.get().deserialize( stringFirstRecord, AuditRecordData.class );
            oldestRecord = firstRecord.getTimestamp();
        }
    }

    private void removeRecords( final int count )
    {
        auditDB.removeFirst( count );
        readOldestRecord();
    }

    private class TrimmerThread implements Runnable
    {

        // keep transaction duration around 100ms if possible.
        final TransactionSizeCalculator transactionSizeCalculator = new TransactionSizeCalculator(
                TransactionSizeCalculator.Settings.builder()
                        .durationGoal( TimeDuration.of( 101, TimeDuration.Unit.MILLISECONDS ) )
                        .maxTransactions( 5003 )
                        .minTransactions( 3 )
                        .build()
        );

        @Override
        public void run( )
        {
            long startTime = System.currentTimeMillis();
            while ( status == PwmService.STATUS.OPEN
                    && trim( transactionSizeCalculator.getTransactionSize() ) )
            {
                final long executeTime = System.currentTimeMillis() - startTime;
                transactionSizeCalculator.recordLastTransactionDuration( executeTime );
                transactionSizeCalculator.pause();
                startTime = System.currentTimeMillis();
            }
        }

        private boolean trim( final int maxRemovals )
        {
            if ( auditDB.isEmpty() )
            {
                return false;
            }

            if ( auditDB.size() > settings.getMaxRecords() + maxRemovals )
            {
                removeRecords( maxRemovals );
                return true;
            }

            int workActions = 0;
            while ( oldestRecord != null
                    && workActions < maxRemovals
                    && !auditDB.isEmpty()
                    && status == PwmService.STATUS.OPEN
                    )
            {
                if ( TimeDuration.fromCurrent( oldestRecord ).isLongerThan( settings.getMaxRecordAge() ) )
                {
                    removeRecords( 1 );
                    workActions++;
                }
                else
                {
                    break;
                }
            }

            return workActions > 0;
        }
    }
}
