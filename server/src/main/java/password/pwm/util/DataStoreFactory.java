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

package password.pwm.util;

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.db.DatabaseDataStore;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBDataStore;

public abstract class DataStoreFactory
{
    public static DataStore autoDbOrLocalDBstore( final PwmApplication pwmApplication, final DatabaseTable table, final LocalDB.DB db ) throws PwmUnrecoverableException
    {
        if ( pwmApplication.getConfig().hasDbConfigured() )
        {
            return new DatabaseDataStore( pwmApplication.getDatabaseService(), table );
        }

        return new LocalDBDataStore( pwmApplication.getLocalDB(), db );
    }
}
