/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
