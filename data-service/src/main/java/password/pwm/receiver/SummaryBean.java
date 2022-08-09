/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.receiver;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmAboutProperty;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.config.PwmSetting;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.java.TimeDuration;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

@Value
@Builder
public class SummaryBean
{
    private int serverCount;
    private Map<String, SiteSummary> siteSummary;
    private Map<String, Integer> ldapVendorCount;
    private Map<String, Integer> appServerCount;
    private Map<String, Integer> settingCount;
    private Map<String, Integer> statCount;
    private Map<String, Integer> osCount;
    private Map<String, Integer> deploymentCount;
    private Map<String, Integer> dbCount;
    private Map<String, Integer> javaCount;
    private Map<String, Integer> appVersionCount;

    static SummaryBean fromStorage( final Storage storage, final Duration maxAge )
    {

        final String naText = "n/a";

        int serverCount = 0;
        final Map<String, SiteSummary> siteSummaryMap = new TreeMap<>();
        final Map<String, Integer> ldapVendorCount = new TreeMap<>();
        final Map<String, Integer> appServerCount = new TreeMap<>();
        final Map<String, Integer> settingCount = new TreeMap<>();
        final Map<String, Integer> statCount = new TreeMap<>();
        final Map<String, Integer> deploymentCount = new TreeMap<>();
        final Map<String, Integer> osCount = new TreeMap<>();
        final Map<String, Integer> dbCount = new TreeMap<>();
        final Map<String, Integer> javaCount = new TreeMap<>();
        final Map<String, Integer> appVersionCount = new TreeMap<>();

        for ( Iterator<TelemetryPublishBean> iterator = storage.iterator(); iterator.hasNext(); )
        {
            final TelemetryPublishBean bean = iterator.next();
            final Duration age = Duration.between( Instant.now(), bean.getTimestamp() );

            if ( bean.getAbout() != null && age.toMillis() < maxAge.toMillis() )
            {
                serverCount++;
                final String hashID = bean.getInstanceHash();
                final String ldapVendor = bean.getLdapVendorName() == null
                        ? naText
                        : bean.getLdapVendorName();

                final String dbVendor = dbVendorName( bean );

                final SiteSummary siteSummary = SiteSummary.builder()
                        .description( bean.getSiteDescription() )
                        .version( bean.getVersionVersion() )
                        .installAge( TimeDuration.fromCurrent( bean.getInstallTime() ).asDuration() )
                        .updateAge( TimeDuration.fromCurrent( bean.getTimestamp() ).asDuration() )
                        .ldapVendor( ldapVendor )
                        .osName( bean.getAbout().get( PwmAboutProperty.java_osName.name() ) )
                        .osVersion( bean.getAbout().get( PwmAboutProperty.java_osVersion.name() ) )
                        .servletName( bean.getAbout().get( PwmAboutProperty.java_appServerInfo.name() ) )
                        .dbVendor( dbVendor )
                        .platform( bean.getAbout().get( PwmAboutProperty.app_deployment_type.name() ) )
                        .javaVm( javaVmInfo( bean, "n/a" ) )
                        .build();

                siteSummaryMap.put( hashID, siteSummary );

                incrementCounterMap( dbCount, dbVendor );

                incrementCounterMap( ldapVendorCount, ldapVendor );

                incrementCounterMap( appServerCount, siteSummary.getServletName() );

                incrementCounterMap( osCount, bean.getAbout().get( PwmAboutProperty.java_osName.name() ) );

                incrementCounterMap( javaCount, siteSummary.getJavaVm() );

                incrementCounterMap( deploymentCount, bean.getAbout().get( PwmAboutProperty.app_deployment_type.name() ) );

                incrementCounterMap( appVersionCount, siteSummary.getVersion() );

                for ( final String settingKey : bean.getConfiguredSettings() )
                {
                    PwmSetting.forKey( settingKey ).ifPresent( ( setting ) ->
                    {
                        final String description = setting.toMenuLocationDebug( null, null );
                        incrementCounterMap( settingCount, description );
                    } );
                }

                for ( final String statKey : bean.getStatistics().keySet() )
                {
                    Statistic.forKey( statKey ).ifPresent( ( statistic ->
                    {
                        final int count = Integer.parseInt( bean.getStatistics().get( statKey ) );
                        incrementCounterMap( statCount, statistic.getLabel( null ), count );
                    } ) );
                }
            }
        }


        return SummaryBean.builder()
                .serverCount( serverCount )
                .siteSummary( siteSummaryMap )
                .ldapVendorCount( ldapVendorCount )
                .settingCount( settingCount )
                .statCount( statCount )
                .appServerCount( appServerCount )
                .osCount( osCount )
                .dbCount( dbCount )
                .javaCount( javaCount )
                .appVersionCount( appVersionCount )
                .build();

    }

    private static void incrementCounterMap( final Map<String, Integer> map, final String key )
    {
        incrementCounterMap( map, key, 1 );
    }

    private static void incrementCounterMap( final Map<String, Integer> map, final String key, final int count )
    {
        if ( map.containsKey( key ) )
        {
            map.put( key, map.get( key ) + count );
        }
        else
        {
            map.put( key, count );
        }
    }

    private static String dbVendorName( final TelemetryPublishBean bean )
    {
        String dbVendor = "n/a";
        final Map<String, String> aboutMap = bean.getAbout();
        if ( aboutMap.get( PwmAboutProperty.database_databaseProductName.name() ) != null )
        {
            dbVendor = aboutMap.get( PwmAboutProperty.database_databaseProductName.name() );

            if ( aboutMap.get( PwmAboutProperty.database_databaseProductVersion.name() ) != null )
            {
                dbVendor += "/" + aboutMap.get( PwmAboutProperty.database_databaseProductVersion.name() );
            }
        }
        return dbVendor;
    }

    private static String javaVmInfo( final TelemetryPublishBean bean, final String naText )
    {
        return bean.getAbout().getOrDefault( PwmAboutProperty.java_vmName.name(), naText )
                + " ("
                + bean.getAbout().getOrDefault( PwmAboutProperty.java_vmVendor.name(), naText )
                + " ) "
                + bean.getAbout().getOrDefault( PwmAboutProperty.java_vmVersion.name(), naText );
    }

    @Value
    @Builder
    public static class SiteSummary
    {
        private String description;
        private String version;
        private Duration installAge;
        private Duration updateAge;
        private String ldapVendor;
        private String osName;
        private String osVersion;
        private String servletName;
        private String dbVendor;
        private String javaVm;
        private String platform;
    }
}
