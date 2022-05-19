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

package password.pwm.svc.intruder;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataStoreException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.PwmService;
import password.pwm.util.DataStore;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

class IntruderDataStore implements IntruderRecordStore
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( IntruderDataStore.class );
    private static final long CLEANER_MIN_WRITE_COUNT = 1_000;

    private final DataStore dataStore;
    private final Supplier<PwmService.STATUS> serviceStatus;
    private final StatisticCounterBundle<DebugKeys> stats = new StatisticCounterBundle<>( DebugKeys.class );
    private final PwmService intruderService;

    private final LongAdder writeCounter = new LongAdder();

    private Instant eldestRecord;

    IntruderDataStore( final PwmService intruderService, final DataStore dataStore, final Supplier<PwmService.STATUS> serviceStatus )
    {
        this.intruderService = intruderService;
        this.dataStore = dataStore;
        this.serviceStatus = serviceStatus;
        writeCounter.add( CLEANER_MIN_WRITE_COUNT );
    }

    @Override
    public StatisticCounterBundle<DebugKeys> getStats()
    {
        return stats;
    }

    @Override
    public Optional<IntruderRecord> read( final String key )
            throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( key ) )
        {
            return Optional.empty();
        }

        stats.increment( DebugKeys.reads );
        final Optional<String> value;
        try
        {
            value = dataStore.get( key );
        }
        catch ( final PwmDataStoreException e )
        {
            final String msg = "error reading stored intruder record: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( e.getError(), msg ) );
        }

        if ( value.isEmpty() )
        {
            return Optional.empty();
        }

        try
        {
            return Optional.ofNullable( JsonFactory.get().deserialize( value.get(), IntruderRecord.class ) );
        }
        catch ( final Exception e )
        {
            //read failed, try to delete record
            try
            {
                dataStore.remove( key );
            }
            catch ( final PwmDataStoreException e2 )
            {
                LOGGER.error( intruderService.getSessionLabel(), e2.getErrorInformation() );
            }

            final String msg = "error reading stored intruder record: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
        }
    }

    @Override
    public void write( final String key, final IntruderRecord record )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String jsonRecord = JsonFactory.get().serialize( record );
        try
        {
            dataStore.put( key, jsonRecord );
            writeCounter.increment();
        }
        catch ( final PwmDataStoreException e )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "error writing to LocalDB: " + e.getMessage() ) );
        }
        stats.increment( DebugKeys.writes );
    }

    @Override
    public ClosableIterator<IntruderRecord> iterator()
            throws PwmUnrecoverableException
    {
        try
        {
            return new RecordIterator( dataStore.iterator( ) );
        }
        catch ( final PwmDataStoreException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "iterator unavailable:" + e.getMessage() );
        }
    }

    private class RecordIterator implements ClosableIterator<IntruderRecord>
    {
        private final ClosableIterator<Map.Entry<String, String>> dbIterator;
        private String currentKey;
        private IntruderRecord currentRecord;

        private RecordIterator( final ClosableIterator<Map.Entry<String, String>> dbIterator )
        {
            this.dbIterator = dbIterator;
            doNext();
        }

        @Override
        public boolean hasNext( )
        {
            return currentRecord != null;
        }

        @Override
        public IntruderRecord next( )
        {
            if ( currentRecord != null )
            {
                final IntruderRecord returnRecord = currentRecord;
                doNext();
                return returnRecord;
            }

            throw new NoSuchElementException();
        }

        private void doNext()
        {
            currentRecord = null;

            try
            {
                while ( dbIterator.hasNext() )
                {
                    currentKey = dbIterator.next().getKey();
                    final Optional<IntruderRecord> record = read( currentKey );
                    if ( record.isPresent() )
                    {
                        currentRecord = record.get();
                        return;
                    }
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                throw new IllegalStateException( e );
            }
        }

        @Override
        public void remove( )
        {
            try
            {
                dataStore.remove( currentKey );
            }
            catch ( final PwmDataStoreException | PwmUnrecoverableException e )
            {
                throw new IllegalStateException( e );
            }
        }

        @Override
        public void close( )
        {
            dbIterator.close();
        }
    }


    @Override
    public void cleanup( final TimeDuration maxRecordAge )
    {
        if ( writeCounter.longValue() < CLEANER_MIN_WRITE_COUNT )
        {
            return;
        }

        if ( eldestRecord != null && TimeDuration.fromCurrent( eldestRecord ).isShorterThan( maxRecordAge ) )
        {
            LOGGER.trace( intruderService.getSessionLabel(), () -> "skipping table cleanup: eldest record is younger than max age" );
            return;
        }

        eldestRecord = Instant.now();

        stats.increment( DebugKeys.cleanupCycles );
        final Instant startTime = Instant.now();

        int recordsExamined = 0;
        int recordsRemoved = 0;

        try ( ClosableIterator<IntruderRecord> iterator = this.iterator( ) )
        {
            while ( this.serviceStatus.get() == PwmService.STATUS.OPEN && iterator.hasNext() )
            {
                final IntruderRecord record = iterator.next();
                stats.increment( DebugKeys.cleanupExamines );
                recordsExamined++;

                if ( TimeDuration.fromCurrent( record.getTimeStamp() ).isLongerThan( maxRecordAge ) )
                {
                    iterator.remove();
                    stats.increment( DebugKeys.cleanupRemoves );
                    recordsRemoved++;
                }
                if ( eldestRecord.compareTo( record.getTimeStamp() ) > 0 )
                {
                    eldestRecord = record.getTimeStamp();
                }
            }
        }
        catch ( final PwmException e )
        {
            LOGGER.error( intruderService.getSessionLabel(), () -> "unable to perform intruder table cleanup: " + e.getMessage() );
        }

        {
            final int finalRemoved = recordsRemoved;
            final int finalExamined = recordsExamined;
            LOGGER.trace( intruderService.getSessionLabel(), () -> "completed cleanup of intruder table in "
                    + TimeDuration.compactFromCurrent( startTime ) + ", recordsExamined="
                    + finalExamined + ", recordsRemoved=" + finalRemoved );
        }

        writeCounter.reset();
    }
}
