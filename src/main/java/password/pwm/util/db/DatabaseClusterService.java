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

package password.pwm.util.db;

public class DatabaseClusterService {



    private static final String KEY_ENGINE_START_PREFIX = "engine-start-";

    private void heartbeat() {
        /*
        try {
            put(DatabaseTable.PWM_META, KEY_ENGINE_START_PREFIX + instanceID, JavaHelper.toIsoDate(new java.util.Date()));
        } catch (DatabaseException e) {
            final String errorMsg = "error writing engine start time value: " + e.getMessage();
            throw new DatabaseException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg));
        }
        */
    }
}
