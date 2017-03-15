/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.error.PwmException;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;

public class DatabaseStatusChecker implements HealthChecker {
    private static final PwmLogger LOGGER = PwmLogger.forClass(DatabaseStatusChecker.class);

    @Override
    public List<HealthRecord> doHealthCheck(final PwmApplication pwmApplication)
    {
        return Collections.emptyList();
    }

    public static List<HealthRecord> checkNewDatabaseStatus(final PwmApplication pwmApplication, final Configuration config) {
        return checkDatabaseStatus(pwmApplication, config);
    }

    private static List<HealthRecord> checkDatabaseStatus(final PwmApplication pwmApplication, final Configuration config)
    {
        if (!config.hasDbConfigured()) {
            return Collections.singletonList(new HealthRecord(HealthStatus.INFO,HealthTopic.Database,"Database not configured"));
        }
        final DatabaseAccessorImpl impl = new DatabaseAccessorImpl();
        try {

            final PwmEnvironment runtimeEnvironment = pwmApplication.getPwmEnvironment().makeRuntimeInstance(config);
            final PwmApplication runtimeInstance = new PwmApplication(runtimeEnvironment);
            impl.init(runtimeInstance);
            impl.get(DatabaseTable.PWM_META, "test");
            return impl.healthCheck();
        } catch (PwmException e) {
            LOGGER.error("error during healthcheck: " + e.getMessage());
            return impl.healthCheck();
        } finally {
            impl.close();
        }
    }
}
