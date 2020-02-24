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

import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

class RecordManagerImpl implements RecordManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RecordManagerImpl.class );

    private final RecordType recordType;
    private final RecordStore recordStore;
    private final IntruderSettings settings;

    private static final PwmHashAlgorithm KEY_HASH_ALG = PwmHashAlgorithm.SHA256;

    RecordManagerImpl( final RecordType recordType, final RecordStore recordStore, final IntruderSettings settings )
    {
        this.recordType = recordType;
        this.recordStore = recordStore;
        this.settings = settings;
    }

    public boolean checkSubject( final String subject )
    {
        if ( subject == null || subject.length() < 1 )
        {
            throw new IllegalArgumentException( "subject is required value" );
        }

        final IntruderRecord record = readIntruderRecord( subject );
        if ( record == null )
        {
            return false;
        }
        if ( TimeDuration.fromCurrent( record.getTimeStamp() ).isLongerThan( settings.getCheckDuration() ) )
        {
            return false;
        }
        if ( record.getAttemptCount() >= settings.getCheckCount() )
        {
            return true;
        }
        return false;
    }

    public void markSubject( final String subject )
    {
        if ( subject == null || subject.length() < 1 )
        {
            throw new IllegalArgumentException( "subject is required value" );
        }

        IntruderRecord record = readIntruderRecord( subject );

        if ( record == null )
        {
            record = new IntruderRecord( recordType, subject );
        }

        final TimeDuration age = TimeDuration.fromCurrent( record.getTimeStamp() );
        if ( age.isLongerThan( settings.getCheckDuration() ) )
        {
            final IntruderRecord finalRecord = record;
            LOGGER.debug( () -> "re-setting existing outdated record=" + JsonUtil.serialize( finalRecord ) + " (" + age.asCompactString() + ")" );
            record = new IntruderRecord( recordType, subject );
        }

        record.incrementAttemptCount();

        writeIntruderRecord( record );
    }

    public void clearSubject( final String subject )
    {
        final IntruderRecord record = readIntruderRecord( subject );
        if ( record == null )
        {
            return;
        }

        if ( record.getAttemptCount() == 0 )
        {
            return;
        }

        record.clearAttemptCount();
        writeIntruderRecord( record );
    }

    public boolean isAlerted( final String subject )
    {
        final IntruderRecord record = readIntruderRecord( subject );
        return record != null && record.isAlerted();
    }

    public void markAlerted( final String subject )
    {
        final IntruderRecord record = readIntruderRecord( subject );
        if ( record == null || record.isAlerted() )
        {
            return;
        }
        record.setAlerted();
        writeIntruderRecord( record );
    }

    @Override
    public IntruderRecord readIntruderRecord( final String subject )
    {
        try
        {
            return recordStore.read( makeKey( subject ) );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( () -> "unable to read read intruder record from storage: " + e.getMessage() );
        }
        return null;
    }

    private void writeIntruderRecord( final IntruderRecord intruderRecord )
    {
        try
        {
            recordStore.write( makeKey( intruderRecord.getSubject() ), intruderRecord );
        }
        catch ( final PwmException e )
        {
            LOGGER.warn( () -> "unexpected error attempting to write intruder record " + JsonUtil.serialize( intruderRecord ) + ", error: " + e.getMessage() );
        }
    }

    private String makeKey( final String subject ) throws PwmOperationalException
    {
        final String hash;
        try
        {
            hash = SecureEngine.hash( subject, KEY_HASH_ALG );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new PwmOperationalException( PwmError.ERROR_INTERNAL, "error generating md5sum for intruder record: " + e.getMessage() );
        }
        return hash + recordType.toString();
    }


    @Override
    public ClosableIterator<IntruderRecord> iterator( ) throws PwmException
    {
        return new RecordIterator<>( recordStore.iterator() );
    }

    static class RecordIterator<IntruderRecord> implements ClosableIterator<IntruderRecord>
    {
        private ClosableIterator<IntruderRecord> innerIter;

        RecordIterator( final ClosableIterator<IntruderRecord> recordIterator ) throws PwmOperationalException
        {
            this.innerIter = recordIterator;
        }

        public boolean hasNext( )
        {
            return innerIter.hasNext();
        }

        public IntruderRecord next( )
        {
            IntruderRecord record = null;
            while ( innerIter.hasNext() && record == null )
            {
                record = innerIter.next();
            }
            return record;
        }

        public void remove( )
        {
        }

        public void close( )
        {
            innerIter.close();
        }
    }
}
