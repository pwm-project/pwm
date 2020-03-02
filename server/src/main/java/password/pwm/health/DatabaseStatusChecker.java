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

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.error.PwmException;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;

public class DatabaseStatusChecker implements HealthChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseStatusChecker.class );

    @Override
    public List<HealthRecord> doHealthCheck( final PwmApplication pwmApplication )
    {
        return Collections.emptyList();
    }

    public static List<HealthRecord> checkNewDatabaseStatus( final PwmApplication pwmApplication, final Configuration config )
    {
        return checkDatabaseStatus( pwmApplication, config );
    }

    private static List<HealthRecord> checkDatabaseStatus( final PwmApplication pwmApplication, final Configuration config )
    {
        if ( !config.hasDbConfigured() )
        {
            return Collections.singletonList( new HealthRecord( HealthStatus.INFO, HealthTopic.Database, "Database not configured" ) );
        }

        PwmApplication runtimeInstance = null;
        try
        {
            final PwmEnvironment runtimeEnvironment = pwmApplication.getPwmEnvironment().makeRuntimeInstance( config );
            runtimeInstance = PwmApplication.createPwmApplication( runtimeEnvironment );
            final DatabaseAccessor accessor = runtimeInstance.getDatabaseService().getAccessor();
            accessor.get( DatabaseTable.PWM_META, "test" );
            return runtimeInstance.getDatabaseService().healthCheck();
        }
        catch ( final PwmException e )
        {
            LOGGER.error( () -> "error during healthcheck: " + e.getMessage() );
            e.printStackTrace();
            return runtimeInstance.getDatabaseService().healthCheck();
        }
        finally
        {
            if ( runtimeInstance != null )
            {
                runtimeInstance.shutdown();
            }
        }
    }
}
