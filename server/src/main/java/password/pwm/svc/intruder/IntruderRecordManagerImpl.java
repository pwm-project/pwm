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

import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.secure.SecureService;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;

import java.util.Optional;

class IntruderRecordManagerImpl implements IntruderRecordManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( IntruderRecordManagerImpl.class );

    private final IntruderRecordType recordType;
    private final IntruderRecordStore recordStore;
    private final IntruderSettings.TypeSettings settings;
    private final SecureService secureService;
    private final DomainID domainID;
    private final PwmHashAlgorithm storageHashAlgorithm;

    IntruderRecordManagerImpl(
            final PwmDomain pwmDomain,
            final IntruderRecordType recordType,
            final IntruderRecordStore recordStore,
            final IntruderSettings settings
    )
    {
        this.domainID = pwmDomain.getDomainID();
        this.secureService = pwmDomain.getSecureService();
        this.recordType = recordType;
        this.recordStore = recordStore;
        this.settings = settings.targetSettings().get( recordType );
        this.storageHashAlgorithm = settings.storageHashAlgorithm();
    }

    @Override
    public boolean checkSubject( final String subject )
    {
        if ( StringUtil.isEmpty( subject ) )
        {
            throw new IllegalArgumentException( "subject is required value" );
        }

        final Optional<IntruderRecord> record = readIntruderRecord( subject );
        if ( record.isEmpty() )
        {
            return false;
        }

        if ( TimeDuration.fromCurrent( record.get().getTimeStamp() ).isLongerThan( settings.checkDuration() ) )
        {
            return false;
        }

        if ( record.get().getAttemptCount() >= settings.checkCount() )
        {
            return true;
        }
        return false;
    }

    @Override
    public void markSubject( final String subject )
    {
        if ( StringUtil.isEmpty( subject ) )
        {
            throw new IllegalArgumentException( "subject is required value" );
        }

        IntruderRecord record = readIntruderRecord( subject ).orElseGet( () -> new IntruderRecord( domainID, recordType, subject ) );

        final TimeDuration age = TimeDuration.fromCurrent( record.getTimeStamp() );
        if ( age.isLongerThan( settings.checkDuration() ) )
        {
            final IntruderRecord finalRecord = record;
            LOGGER.debug( () -> "re-setting existing outdated record=" + JsonFactory.get().serialize( finalRecord ) + " (" + age.asCompactString() + ")" );
            record = new IntruderRecord( domainID, recordType, subject );
        }

        record.incrementAttemptCount();

        writeIntruderRecord( record );
    }

    @Override
    public void clearSubject( final String subject )
    {
        final Optional<IntruderRecord> record = readIntruderRecord( subject );
        if ( record.isEmpty() )
        {
            return;
        }

        if ( record.get().getAttemptCount() == 0 )
        {
            return;
        }

        record.get().clearAttemptCount();
        writeIntruderRecord( record.get() );
    }

    @Override
    public boolean isAlerted( final String subject )
    {
        final Optional<IntruderRecord> record = readIntruderRecord( subject );
        return record.isPresent() && record.get().isAlerted();
    }

    @Override
    public void markAlerted( final String subject )
    {
        final Optional<IntruderRecord> record = readIntruderRecord( subject );
        if ( record.isEmpty() || record.get().isAlerted() )
        {
            return;
        }
        record.get().setAlerted( true );
        writeIntruderRecord( record.get() );
    }

    @Override
    public Optional<IntruderRecord> readIntruderRecord( final String subject )
    {
        try
        {
            return Optional.ofNullable( recordStore.read( makeKey( subject ) ).orElse( null ) );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( () -> "unable to read read intruder record from storage: " + e.getMessage() );
        }
        return Optional.empty();
    }

    private void writeIntruderRecord( final IntruderRecord intruderRecord )
    {
        try
        {
            recordStore.write( makeKey( intruderRecord.getSubject() ), intruderRecord );
        }
        catch ( final PwmException e )
        {
            LOGGER.warn( () -> "unexpected error attempting to write intruder record " + JsonFactory.get().serialize( intruderRecord ) + ", error: " + e.getMessage() );
        }
    }

    private String makeKey( final String subject ) throws PwmOperationalException
    {
        JavaHelper.requireNonEmpty( subject );

        try
        {
            return secureService.hash( storageHashAlgorithm, subject ) + "-" + domainID + "-" + recordType;
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new PwmOperationalException( PwmError.ERROR_INTERNAL, "error generating hash for intruder record: " + e.getMessage() );
        }
    }

    @Override
    public ClosableIterator<IntruderRecord> iterator( ) throws PwmException
    {
        return new RecordIterator<>( recordStore.iterator() );
    }

    private static class RecordIterator<IntruderRecord> implements ClosableIterator<IntruderRecord>
    {
        private final ClosableIterator<IntruderRecord> innerIter;

        RecordIterator( final ClosableIterator<IntruderRecord> recordIterator )
        {
            this.innerIter = recordIterator;
        }

        @Override
        public boolean hasNext( )
        {
            return innerIter.hasNext();
        }

        @Override
        public IntruderRecord next( )
        {
            IntruderRecord record = null;
            while ( innerIter.hasNext() && record == null )
            {
                record = innerIter.next();
            }
            return record;
        }

        @Override
        public void remove( )
        {
        }

        @Override
        public void close( )
        {
            innerIter.close();
        }
    }
}
