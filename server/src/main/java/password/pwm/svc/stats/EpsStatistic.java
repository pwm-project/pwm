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
import password.pwm.util.LocaleHelper;

import java.util.Locale;

public enum EpsStatistic
{
    REQUESTS( null ),
    SESSIONS( null ),
    PASSWORD_CHANGES( Statistic.PASSWORD_CHANGES ),
    AUTHENTICATION( Statistic.AUTHENTICATIONS ),
    INTRUDER_ATTEMPTS( Statistic.INTRUDER_ATTEMPTS ),
    PWMDB_WRITES( null ),
    PWMDB_READS( null ),
    DB_WRITES( null ),
    DB_READS( null ),;

    private Statistic relatedStatistic;

    EpsStatistic( final Statistic relatedStatistic )
    {
        this.relatedStatistic = relatedStatistic;
    }

    public Statistic getRelatedStatistic( )
    {
        return relatedStatistic;
    }

    public String getLabel( final Locale locale )
    {
        final String keyName = Admin.EPS_STATISTICS_LABEL_PREFIX + this.name();
        return LocaleHelper.getLocalizedMessage( locale, keyName, null, Admin.class );
    }
}
