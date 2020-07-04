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

public enum AvgStatistic
{
    AVG_PASSWORD_SYNC_TIME( "AvgPasswordSyncTime", null, "ms" ),
    AVG_AUTHENTICATION_TIME( "AvgAuthenticationTime", null, "ms" ),
    AVG_PASSWORD_STRENGTH( "AvgPasswordStrength", null, "" ),
    AVG_LDAP_SEARCH_TIME( "AvgLdapSearchTime", null, "ms" ),
    AVG_REQUEST_PROCESS_TIME( "AvgRequestProcessTime", null, "ms" ),;

    private final String key;
    private final Statistic.StatDetail statDetail;
    private final String unit;

    AvgStatistic(
            final String key,
            final Statistic.StatDetail statDetail,
            final String unit
    )
    {
        this.key = key;
        this.statDetail = statDetail;
        this.unit = unit;
    }

    public String getKey( )
    {
        return key;
    }

    public String getUnit()
    {
        return unit;
    }

    public String getLabel( final Locale locale )
    {
        final String keyName = Admin.STATISTICS_LABEL_PREFIX + this.getKey();
        return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
    }

    public String getDescription( final Locale locale )
    {
        final String keyName = Admin.STATISTICS_DESCRIPTION_PREFIX + this.getKey();
        return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
    }
}
