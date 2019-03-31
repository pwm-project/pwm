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

package password.pwm.svc.report;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Value
@Builder
class ReportSettings implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportSettings.class );

    @Builder.Default
    private TimeDuration maxCacheAge = TimeDuration.of( TimeDuration.DAY.asMillis() * 90, TimeDuration.Unit.MILLISECONDS );

    @Builder.Default
    private List<UserPermission> searchFilter = Collections.emptyList();

    @Builder.Default
    private int jobOffsetSeconds = 0;

    @Builder.Default
    private int maxSearchSize = 100 * 1000;

    @Builder.Default
    private List<Integer> trackDays = Collections.emptyList();

    @Builder.Default
    private int reportJobThreads = 1;

    @Builder.Default
    private JobIntensity reportJobIntensity = JobIntensity.LOW;

    public enum JobIntensity
    {
        LOW,
        MEDIUM,
        HIGH,
    }

    public static ReportSettings readSettingsFromConfig( final Configuration config )
    {
        final ReportSettings.ReportSettingsBuilder builder = ReportSettings.builder();
        builder.maxCacheAge( TimeDuration.of( config.readSettingAsLong( PwmSetting.REPORTING_MAX_CACHE_AGE ), TimeDuration.Unit.SECONDS ) );
        builder.searchFilter( config.readSettingAsUserPermission( PwmSetting.REPORTING_USER_MATCH ) );
        builder.maxSearchSize ( ( int ) config.readSettingAsLong( PwmSetting.REPORTING_MAX_QUERY_SIZE ) );

        if ( builder.searchFilter == null || builder.searchFilter.isEmpty() )
        {
            builder.searchFilter = null;
        }

        builder.jobOffsetSeconds = ( int ) config.readSettingAsLong( PwmSetting.REPORTING_JOB_TIME_OFFSET );
        if ( builder.jobOffsetSeconds > 60 * 60 * 24 )
        {
            builder.jobOffsetSeconds = 0;
        }

        builder.trackDays( parseDayIntervalStr( config ) );

        builder.reportJobThreads( Integer.parseInt( config.readAppProperty( AppProperty.REPORTING_LDAP_SEARCH_THREADS ) ) );

        builder.reportJobIntensity( config.readSettingAsEnum( PwmSetting.REPORTING_JOB_INTENSITY, JobIntensity.class ) );

        return builder.build();
    }

    private static List<Integer> parseDayIntervalStr( final Configuration configuration )
    {
        final List<String> configuredValues = new ArrayList<>();
        if ( configuration != null )
        {
            configuredValues.addAll( configuration.readSettingAsStringArray( PwmSetting.REPORTING_SUMMARY_DAY_VALUES ) );
        }
        if ( configuredValues.isEmpty() )
        {
            configuredValues.add( "1" );
        }
        final List<Integer> returnValue = new ArrayList<>();
        for ( final String splitDay : configuredValues )
        {
            try
            {
                final int dayValue = Integer.parseInt( splitDay );
                returnValue.add( dayValue );
            }
            catch ( NumberFormatException e )
            {
                LOGGER.error( "error parsing reporting summary day value '" + splitDay + "', error: " + e.getMessage() );
            }
        }
        Collections.sort( returnValue );
        return Collections.unmodifiableList( returnValue );
    }

    String getSettingsHash( )
            throws PwmUnrecoverableException
    {
        return SecureEngine.hash( JsonUtil.serialize( this ), PwmConstants.SETTING_CHECKSUM_HASH_METHOD );
    }
}
