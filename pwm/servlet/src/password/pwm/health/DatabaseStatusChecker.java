/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.health;

import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.PwmException;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.db.DatabaseTable;

import java.util.List;

public class DatabaseStatusChecker implements HealthChecker {
    @Override
    public List<HealthRecord> doHealthCheck(PwmApplication pwmApplication)
    {
        return null;
    }

    public static List<HealthRecord> checkNewDatabaseStatus(Configuration config) {
        try {
            final PwmApplication pwmApplication = new PwmApplication(config, PwmApplication.MODE.NEW, null, false, null);
            checkDatabaseStatus(pwmApplication,config);
        } catch (Exception e) {

        }

        return null;
    }

    private static List<HealthRecord> checkDatabaseStatus(final PwmApplication pwmApplication, Configuration config)
    {
        try {
            DatabaseAccessorImpl impl = new DatabaseAccessorImpl();
            impl.init(pwmApplication);
            impl.get(DatabaseTable.PWM_META, "test");
        } catch (PwmException e) {

        }
        return null;
    }
}
