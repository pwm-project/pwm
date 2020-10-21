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

package password.pwm.svc.stats;

import password.pwm.i18n.Admin;
import password.pwm.util.i18n.LocaleHelper;

import java.util.Locale;

public enum EpsStatistic
{
    REQUESTS(),
    SESSIONS(),
    PASSWORD_CHANGES(),
    AUTHENTICATION(),
    INTRUDER_ATTEMPTS(),
    PWMDB_WRITES(),
    PWMDB_READS(),
    DB_WRITES(),
    DB_READS(),
    LDAP_BINDS,;

    public String getLabel( final Locale locale )
    {
        final String keyName = Admin.EPS_STATISTICS_LABEL_PREFIX + this.name();
        return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
    }
}
