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
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class DataStoreRecordStore implements RecordStore
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DataStoreRecordStore.class );
    private static final int MAX_REMOVALS_PER_CYCLE = 10 * 1000;

    private final IntruderManager intruderManager;
    private final DataStore dataStore;

    private Instant eldestRecord = Instant.now();

    DataStoreRecordStore( final DataStore dataStore, final IntruderManager intruderManager )
    {
        this.dataStore = dataStore;
        this.intruderManager = intruderManager;
    }

    public IntruderRecord read( final String key )
            throws PwmUnrecoverableException
    {
        if ( key == null || key.length() < 1 )
        {
            return null;
        }

        final String value;
        try
        {
            value = dataStore.get( key );
        }
        catch ( final PwmDataStoreException e )
        {
            LOGGER.error( () -> "error reading stored intruder record: " + e.getMessage() );
            if ( e.getError() == PwmError.ERROR_DB_UNAVAILABLE )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
            return null;
        }

        if ( value == null || value.length() < 1 )
        {
            return null;
        }

        try
        {
            return JsonUtil.deserialize( value, IntruderRecord.class );
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error decoding IntruderRecord:" + e.getMessage() );
        }

        //read failed, try to delete record
        try
        {
            dataStore.remove( key );
        }
        catch ( final PwmDataStoreException e )
        {
            /*noop*/
        }

        return null;
    }

    @Override
    public void write( final String key, final IntruderRecord record ) throws PwmOperationalException, PwmUnrecoverableException
    {
        final String jsonRecord = JsonUtil.serialize( record );
        try
        {
            dataStore.put( key, jsonRecord );
        }
        catch ( final PwmDataStoreException e )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "error writing to LocalDB: " + e.getMessage() ) );
        }
    }

    @Override
    public ClosableIterator<IntruderRecord> iterator( ) throws PwmOperationalException, PwmUnrecoverableException
    {
        try
        {
            return new RecordIterator( dataStore.iterator() );
        }
        catch ( final PwmDataStoreException e )
        {
            throw new PwmOperationalException( PwmError.ERROR_INTERNAL, "iterator unavailable:" + e.getMessage() );
        }
    }

    private class RecordIterator implements ClosableIterator<IntruderRecord>
    {
        private final ClosableIterator<Map.Entry<String, String>> dbIterator;

        private RecordIterator( final ClosableIterator<Map.Entry<String, String>> dbIterator )
        {
            this.dbIterator = dbIterator;
        }

        @Override
        public boolean hasNext( )
        {
            return dbIterator.hasNext();
        }

        @Override
        public IntruderRecord next( )
        {
            final String key = dbIterator.next().getKey();
            try
            {
                return read( key );
            }
            catch ( final PwmUnrecoverableException e )
            {
                throw new IllegalStateException( e );
            }
        }

        @Override
        public void remove( )
        {
            throw new UnsupportedOperationException();
        }

        public void close( )
        {
            dbIterator.close();
        }
    }


    @Override
    public void cleanup( final TimeDuration maxRecordAge )
    {
        if ( TimeDuration.fromCurrent( eldestRecord ).isShorterThan( maxRecordAge ) )
        {
            return;
        }
        eldestRecord = Instant.now();

        final Instant startTime = Instant.now();
        final int recordsExamined = 0;
        int recordsRemoved = 0;

        boolean complete = false;

        while ( !complete && intruderManager.status() == PwmService.STATUS.OPEN )
        {

            final List<String> recordsToRemove = discoverPurgableKeys( maxRecordAge );
            if ( recordsToRemove.isEmpty() )
            {
                complete = true;
            }
            try
            {
                for ( final String key : recordsToRemove )
                {
                    dataStore.remove( key );
                }
            }
            catch ( final PwmException e )
            {
                LOGGER.error( () -> "unable to perform removal of identified stale records: " + e.getMessage() );
            }
            recordsRemoved += recordsToRemove.size();
            recordsToRemove.clear();
        }
        {
            final int finalRemoved = recordsRemoved;
            LOGGER.trace( () -> "completed cleanup of intruder table in "
                    + TimeDuration.compactFromCurrent( startTime ) + ", recordsExamined="
                    + recordsExamined + ", recordsRemoved=" + finalRemoved );
        }
    }

    private List<String> discoverPurgableKeys( final TimeDuration maxRecordAge )
    {
        final List<String> recordsToRemove = new ArrayList<>();
        try ( ClosableIterator<Map.Entry<String, String>> dbIterator = dataStore.iterator() )
        {
            while ( intruderManager.status() == PwmService.STATUS.OPEN && dbIterator.hasNext() && recordsToRemove.size() < MAX_REMOVALS_PER_CYCLE )
            {
                final String key = dbIterator.next().getKey();
                final IntruderRecord record = read( key );
                if ( record != null )
                {
                    if ( TimeDuration.fromCurrent( record.getTimeStamp() ).isLongerThan( maxRecordAge ) )
                    {
                        recordsToRemove.add( key );
                    }
                    if ( eldestRecord.compareTo( record.getTimeStamp() ) == 1 )
                    {
                        eldestRecord = record.getTimeStamp();
                    }
                }
            }
        }
        catch ( final PwmDataStoreException | PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "unable to perform intruder table cleanup: " + e.getMessage() );
        }
        return recordsToRemove;
    }
}
