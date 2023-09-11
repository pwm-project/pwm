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

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.error.PwmException;
import password.pwm.svc.db.DatabaseAccessor;
import password.pwm.svc.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public final class DatabaseStatusChecker implements HealthSupplier
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseStatusChecker.class );

    public static List<HealthRecord> checkNewDatabaseStatus(
            final SessionLabel sessionLabel,
            final PwmEnvironment pwmEnvironment,
            final AppConfig appConfig )
    {
        if ( !appConfig.hasDbConfigured() )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.Database_Error,
                    "Database not configured" ) );
        }

        try
        {
            final PwmEnvironment runtimeEnvironment = pwmEnvironment.makeRuntimeInstance( appConfig );
            final PwmApplication runtimeInstance = PwmApplication.createPwmApplication( runtimeEnvironment );
            final List<HealthRecord> records = checkDatabaseStatus( sessionLabel, runtimeInstance );
            return List.copyOf( records );
        }
        catch ( final Exception e )
        {
            return List.of( exceptionToRecord( sessionLabel, e ) );
        }
    }

    @Override
    public List<Supplier<List<HealthRecord>>> jobs( final HealthSupplierRequest request )
    {
        if ( checkShouldBeSkipped( request.pwmApplication() ) )
        {
            return List.of();
        }

        return List.of( () -> checkDatabaseStatus(
                request.sessionLabel(),
                request.pwmApplication() ) );
    }

    private static boolean checkShouldBeSkipped(
            final PwmApplication pwmApplication
    )
    {
        return pwmApplication.getPwmEnvironment().isInternalRuntimeInstance()
                || pwmApplication.getConfig().hasDbConfigured();
    }

    private static List<HealthRecord> checkDatabaseStatus(
            final SessionLabel sessionLabel,
            final PwmApplication pwmApplication
    )
    {
        try
        {
            final DatabaseAccessor accessor = pwmApplication.getDatabaseService().getAccessor();
            accessor.get( DatabaseTable.PWM_META, "test" );
            final List<HealthRecord> records = pwmApplication.getDatabaseService().healthCheck();
            if ( records.isEmpty() )
            {
                return List.of( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.Database_OK ) );
            }
            return records;
        }
        catch ( final PwmException e )
        {
            return List.of( exceptionToRecord( sessionLabel, e ) );
        }
    }

    private static HealthRecord exceptionToRecord(
            final SessionLabel sessionLabel,
            final Exception e )
    {
        LOGGER.debug( sessionLabel, () -> "error during db health check: " + e.getMessage() );
        return  HealthRecord.forMessage(
                DomainID.systemId(),
                HealthMessage.Database_Error,
                "error: " + e.getMessage() );
    }
}
