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
 *
 */

package password.pwm.receiver;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import password.pwm.PwmAboutProperty;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.config.PwmSetting;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.java.TimeDuration;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

@Getter
@Builder
public class SummaryBean {
    private int serverCount;
    private Map<String,SiteSummary> siteSummary;
    private Map<String,Integer> ldapVendorCount;
    private Map<String,Integer> appServerCount;
    private Map<String,Integer> settingCount;
    private Map<String,Integer> statCount;
    private Map<String,Integer> osCount;
    private Map<String,Integer> dbCount;
    private Map<String,Integer> javaCount;
    private Map<String,Integer> ssprVersionCount;

    static SummaryBean fromStorage(final Storage storage, final TimeDuration maxAge ) {

        final String naText = "n/a";

        int serverCount = 0;
        final Map<String,SiteSummary> siteSummaryMap = new TreeMap<>();
        final Map<String,Integer> ldapVendorCount = new TreeMap<>();
        final Map<String,Integer> appServerCount = new TreeMap<>();
        final Map<String,Integer> settingCount = new TreeMap<>();
        final Map<String,Integer> statCount = new TreeMap<>();
        final Map<String,Integer> osCount = new TreeMap<>();
        final Map<String,Integer> dbCount = new TreeMap<>();
        final Map<String,Integer> javaCount = new TreeMap<>();
        final Map<String,Integer> ssprVersionCount = new TreeMap<>();

        for (Iterator<TelemetryPublishBean> iterator = storage.iterator(); iterator.hasNext(); ) {
            final TelemetryPublishBean bean = iterator.next();
            final TimeDuration age = TimeDuration.fromCurrent( bean.getTimestamp() );

            if (bean.getAbout() != null && age.isShorterThan( maxAge ) ) {
                serverCount++;
                final String hashID = bean.getInstanceHash();
                final String ldapVendor = bean.getLdapVendorName() == null
                        ? naText
                        : bean.getLdapVendorName();

                final String dbVendor = dbVendorName(bean);

                final SiteSummary siteSummary = SiteSummary.builder()
                        .description(bean.getSiteDescription())
                        .version(bean.getVersionVersion())
                        .installAge(TimeDuration.fromCurrent(bean.getInstallTime()).asDuration())
                        .updateAge(TimeDuration.fromCurrent(bean.getTimestamp()).asDuration())
                        .ldapVendor(ldapVendor)
                        .osName(bean.getAbout().get(PwmAboutProperty.java_osName.name()))
                        .osVersion(bean.getAbout().get(PwmAboutProperty.java_osVersion.name()))
                        .servletName(bean.getAbout().get(PwmAboutProperty.java_appServerInfo.name()))
                        .dbVendor(dbVendor)
                        .appliance(Boolean.parseBoolean(bean.getAbout().get(PwmAboutProperty.app_mode_appliance.name())))
                        .javaVm(javaVmInfo( bean, "n/a" ))
                        .build();

                siteSummaryMap.put(hashID, siteSummary);

                incrementCounterMap(dbCount, dbVendor);

                incrementCounterMap(ldapVendorCount, ldapVendor);

                incrementCounterMap(appServerCount, siteSummary.getServletName());

                incrementCounterMap(osCount, bean.getAbout().get(PwmAboutProperty.java_osName.name()));

                incrementCounterMap(javaCount, siteSummary.getJavaVm());

                incrementCounterMap(ssprVersionCount, siteSummary.getVersion());

                for (final String settingKey : bean.getConfiguredSettings()) {
                    final PwmSetting setting = PwmSetting.forKey(settingKey);
                    if (setting != null) {
                        final String description = setting.toMenuLocationDebug(null, null);
                        incrementCounterMap(settingCount, description);
                    }
                }

                for (final String statKey : bean.getStatistics().keySet()) {
                    final Statistic statistic = Statistic.forKey(statKey);
                    if (statistic != null) {
                        if (statistic.getType() == Statistic.Type.INCREMENTOR) {
                            final int count = Integer.parseInt(bean.getStatistics().get(statKey));
                            incrementCounterMap(statCount, statistic.getLabel(null), count);
                        }
                    }
                }
            }
        }


        return SummaryBean.builder()
                .serverCount(serverCount)
                .siteSummary(siteSummaryMap)
                .ldapVendorCount(ldapVendorCount)
                .settingCount(settingCount)
                .statCount(statCount)
                .appServerCount(appServerCount)
                .osCount(osCount)
                .dbCount(dbCount)
                .javaCount(javaCount)
                .ssprVersionCount(ssprVersionCount)
                .build();

    }

    private static void incrementCounterMap(final Map<String,Integer> map, final String key) {
        incrementCounterMap(map, key, 1);
    }

    private static void incrementCounterMap(final Map<String,Integer> map, final String key, final int count) {
        if (map.containsKey(key)) {
            map.put(key, map.get(key) + count);
        } else {
            map.put(key, count);
        }
    }

    private static String dbVendorName(final TelemetryPublishBean bean) {
        String dbVendor = "n/a";
        final Map<String,String> aboutMap = bean.getAbout();
        if (aboutMap.get(PwmAboutProperty.database_databaseProductName.name()) != null) {
            dbVendor = aboutMap.get(PwmAboutProperty.database_databaseProductName.name());

            if (aboutMap.get(PwmAboutProperty.database_databaseProductVersion.name()) != null) {
                dbVendor += "/" + aboutMap.get(PwmAboutProperty.database_databaseProductVersion.name());
            }
        }
        return dbVendor;
    }

    private static String javaVmInfo(final TelemetryPublishBean bean, final String naText ) {
        return bean.getAbout().getOrDefault( PwmAboutProperty.java_vmName.name(), naText )
                + " ("
                + bean.getAbout().getOrDefault( PwmAboutProperty.java_vmVendor.name(), naText )
                + " ) "
                + bean.getAbout().getOrDefault( PwmAboutProperty.java_vmVersion.name(), naText );
    }

    @Value
    @Builder
    public static class SiteSummary {
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
        private boolean appliance;
    }
}
