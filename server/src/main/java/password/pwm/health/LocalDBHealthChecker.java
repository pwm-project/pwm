/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.localdb.LocalDB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalDBHealthChecker implements HealthChecker
{
    @Override
    public List<HealthRecord> doHealthCheck( final PwmApplication pwmApplication )
    {
        if ( pwmApplication == null )
        {
            return null;
        }

        final List<HealthRecord> healthRecords = new ArrayList<>();

        final LocalDB localDB = pwmApplication.getLocalDB();

        if ( localDB == null )
        {
            final String detailedError = pwmApplication.getLastLocalDBFailure() == null ? "unknown, check logs" : pwmApplication.getLastLocalDBFailure().toDebugStr();
            healthRecords.add( HealthRecord.forMessage( HealthMessage.LocalDB_BAD, detailedError ) );
            return healthRecords;
        }

        if ( LocalDB.Status.NEW == localDB.status() )
        {
            healthRecords.add( HealthRecord.forMessage( HealthMessage.LocalDB_NEW ) );
            return healthRecords;
        }

        if ( LocalDB.Status.CLOSED == localDB.status() )
        {
            healthRecords.add( HealthRecord.forMessage( HealthMessage.LocalDB_CLOSED ) );
            return healthRecords;
        }

        healthRecords.addAll( checkSpaceRemaining( pwmApplication ) );

        if ( healthRecords.isEmpty() )
        {
            healthRecords.add( HealthRecord.forMessage( HealthMessage.LocalDB_OK ) );
        }

        return healthRecords;
    }

    private List<HealthRecord> checkSpaceRemaining( final PwmApplication pwmApplication )
    {
        final Configuration configuration = pwmApplication.getConfig();
        final long minFreeSpace = JavaHelper.silentParseLong( configuration.readAppProperty( AppProperty.HEALTH_DISK_MIN_FREE_WARNING ), 500_000_000 );
        final long freeSpace = FileSystemUtility.diskSpaceRemaining( pwmApplication.getLocalDB().getFileLocation() );

        if ( freeSpace < minFreeSpace )
        {
            final String spaceValue = StringUtil.formatDiskSizeforDebug( freeSpace );
            return Collections.singletonList( HealthRecord.forMessage( HealthMessage.LocalDB_LowDiskSpace, spaceValue ) );
        }

        return Collections.emptyList();
    }
}
