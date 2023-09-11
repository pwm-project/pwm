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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.IntruderStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.DataStore;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class IntruderSystemService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( IntruderSystemService.class );

    private IntruderRecordStore recordStore;
    private DataStorageMethod dataStorageMethod;

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID ) throws PwmException
    {
        try
        {
            final IntruderStorageMethod storageMethod = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.INTRUDER_STORAGE_METHOD, IntruderStorageMethod.class );
            final DataStore dataStore = IntruderDomainService.initDataStore( pwmApplication, getSessionLabel(), storageMethod );
            dataStorageMethod = dataStore.getDataStorageMethod();

            recordStore = new IntruderDataStore( this, dataStore, this::status );

            scheduleCleaner();
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unexpected error starting intruder manager: " + e.getMessage() );
            LOGGER.error( errorInformation::toDebugStr );
            setStartupError( errorInformation );
            shutdown();
            return STATUS.CLOSED;
        }

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl()
    {
        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck()
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo()
    {
        return ServiceInfoBean.builder()
                .debugProperties( recordStore.getStats().debugStats( PwmConstants.DEFAULT_LOCALE ) )
                .storageMethod( dataStorageMethod )
                .build();
    }

    static PublicIntruderRecord.LockStatus lockStatus( final PwmApplication pwmApplication, final IntruderRecord intruderRecord )
    {
        try
        {
            final DomainID domainID = intruderRecord.getDomainID();
            final PwmDomain pwmDomain = pwmApplication.domains().get( domainID );
            final IntruderDomainService domainIntruderService = pwmDomain.getIntruderService();
            domainIntruderService.check( intruderRecord.getType(), intruderRecord.getSubject() );
            return PublicIntruderRecord.LockStatus.watching;
        }
        catch ( final PwmException e )
        {
            return PublicIntruderRecord.LockStatus.locked;
        }
    }

    public ClosableIterator<PublicIntruderRecord> allRecordIterator()
            throws PwmUnrecoverableException
    {
        final ClosableIterator<IntruderRecord> innerIterator = recordStore.iterator();
        return new ClosableIterator<>()
        {
            @Override
            public void close()
            {
                innerIterator.close();
            }

            @Override
            public boolean hasNext()
            {
                return innerIterator.hasNext();
            }

            @Override
            public PublicIntruderRecord next()
            {
                return PublicIntruderRecord.fromIntruderRecord( getPwmApplication(), innerIterator.next() );
            }
        };
    }

    public List<PublicIntruderRecord> getRecords(
            final IntruderRecordType recordType,
            final int maximum )
            throws PwmException
    {
        return CollectionUtil.iteratorToStream( allRecordIterator() )
                .filter( record -> record.type() == recordType )
                .limit( maximum )
                .collect( Collectors.toList() );
    }

    private void scheduleCleaner()
    {
        final AppConfig config = getPwmApplication().getConfig();
        final TimeDuration maxRecordAge = TimeDuration.of( Long.parseLong(
                config.readAppProperty( AppProperty.INTRUDER_RETENTION_TIME_MS ) ), TimeDuration.Unit.MILLISECONDS );
        final TimeDuration cleanerRunFrequency = TimeDuration.of( Long.parseLong(
                config.readAppProperty( AppProperty.INTRUDER_CLEANUP_FREQUENCY_MS ) ), TimeDuration.Unit.MILLISECONDS );

        final Runnable cleanerJob = () ->
        {
            try
            {
                recordStore.cleanup( maxRecordAge );
            }
            catch ( final Exception e )
            {
                LOGGER.error( getSessionLabel(), () -> "error cleaning recordStore: " + e.getMessage(), e );
            }
        };

        scheduleFixedRateJob( cleanerJob, TimeDuration.SECONDS_10, cleanerRunFrequency );
    }

    IntruderRecordStore getRecordStore()
    {
        return recordStore;
    }
}
