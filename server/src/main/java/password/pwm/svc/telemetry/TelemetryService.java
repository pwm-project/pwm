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

package password.pwm.svc.telemetry;

import com.novell.ldapchai.provider.DirectoryVendor;
import lombok.Builder;
import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.PwmLdapVendor;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsBundle;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmRandom;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelemetryService implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(TelemetryService.class);

    private ScheduledExecutorService executorService;
    private PwmApplication pwmApplication;
    private Settings settings;

    private Instant lastPublishTime;
    private ErrorInformation lastError;
    private TelemetrySender sender;

    private STATUS status = STATUS.NEW;


    @Override
    public STATUS status()
    {
        return status;
    }

    @Override
    public void init(final PwmApplication pwmApplication) throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        if (pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING) {
            LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "will remain closed, app is not running");
            status = STATUS.CLOSED;
            return;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PUBLISH_STATS_ENABLE)) {
            LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "will remain closed, publish stats not enabled");
            status = STATUS.CLOSED;
            return;
        }

        if (pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN) {
            LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "will remain closed, localdb not enabled");
            status = STATUS.CLOSED;
            return;
        }

        if (pwmApplication.getStatisticsManager().status() != STATUS.OPEN) {
            LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "will remain closed, statistics manager is not enabled");
            status = STATUS.CLOSED;
            return;
        }

        settings = Settings.fromConfig(pwmApplication.getConfig());
        try {
            initSender();
        } catch (PwmUnrecoverableException e) {
            LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "will remain closed, unable to init sender: " + e.getMessage());
            status = STATUS.CLOSED;
            return;
        }

        {
            final Instant storedLastPublishTimestamp = pwmApplication.readAppAttribute(PwmApplication.AppAttribute.TELEMETRY_LAST_PUBLISH_TIMESTAMP, Instant.class);
            lastPublishTime = storedLastPublishTimestamp != null ?
                    storedLastPublishTimestamp :
                    pwmApplication.getInstallTime();
            LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "last publish time was " + JavaHelper.toIsoDate(lastPublishTime));
        }

        executorService = JavaHelper.makeSingleThreadExecutorService(pwmApplication, TelemetryService.class);

        scheduleNextJob();

        status = STATUS.OPEN;
    }

    private void initSender() throws PwmUnrecoverableException
    {
        if (StringUtil.isEmpty(settings.getSenderImplementation())) {
            final String msg = "telemetry sender implementation not specified";
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TELEMETRY_SEND_ERROR, msg));
        }

        final TelemetrySender telemetrySender;
        try {
            final String senderClass = settings.getSenderImplementation();
            final Class theClass = Class.forName(senderClass);
            telemetrySender = (TelemetrySender) theClass.newInstance();
        } catch (Exception e) {
            final String msg = "unable to load implementation class: " + e.getMessage();
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, msg));
        }

        try {
            final String macrodSettings = MacroMachine.forNonUserSpecific(pwmApplication, null).expandMacros(settings.getSenderSettings());
            telemetrySender.init(pwmApplication, macrodSettings);
        } catch (Exception e) {
            final String msg = "unable to init implementation class: " + e.getMessage();
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, msg));
        }
        sender = telemetrySender;
    }

    private void executePublishJob() throws PwmUnrecoverableException, IOException, URISyntaxException
    {
        final String authValue = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.AUTHENTICATIONS);
        if (StringUtil.isEmpty(authValue) || Integer.parseInt(authValue) < settings.getMinimumAuthentications()) {
            LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "skipping telemetry send, authentication count is too low");
        } else {
            try {
                final TelemetryPublishBean telemetryPublishBean = generatePublishableBean();
                sender.publish(telemetryPublishBean);
                LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "sent telemetry data: " + JsonUtil.serialize(telemetryPublishBean));
            } catch (PwmException e) {
                lastError = e.getErrorInformation();
                LOGGER.error(SessionLabel.TELEMETRY_SESSION_LABEL, "error sending telemetry data: " + e.getMessage());
            }
        }

        lastPublishTime = Instant.now();
        pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.TELEMETRY_LAST_PUBLISH_TIMESTAMP, lastPublishTime);
        scheduleNextJob();
    }

    private void scheduleNextJob() {
        final TimeDuration durationUntilNextPublish = durationUntilNextPublish();
        executorService.schedule(
                new PublishJob(),
                durationUntilNextPublish.getTotalMilliseconds(),
                TimeUnit.MILLISECONDS);
        LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "next publish time: " + durationUntilNextPublish().asCompactString());
    }

    private class PublishJob implements Runnable {
        @Override
        public void run()
        {
            try {
                executePublishJob();
            } catch (PwmException e) {
                LOGGER.error(e.getErrorInformation());
            } catch (Exception e) {
                LOGGER.error("unexpected error during telemetry publish job: " + e.getMessage());
            }
        }
    }

    @Override
    public void close()
    {

    }

    @Override
    public List<HealthRecord> healthCheck()
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo()
    {
        final Map<String,String> debugMap = new LinkedHashMap<>();
        debugMap.put("lastPublishTime", JavaHelper.toIsoDate(lastPublishTime));
        if (lastError != null) {
            debugMap.put("lastError", lastError.toDebugStr());
        }
        return new ServiceInfoBean(null,Collections.unmodifiableMap(debugMap));
    }


    public TelemetryPublishBean generatePublishableBean()
            throws URISyntaxException, IOException, PwmUnrecoverableException
    {
        final StatisticsBundle bundle = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE);
        final Configuration config = pwmApplication.getConfig();
        final Map<PwmAboutProperty,String> aboutPropertyStringMap = PwmAboutProperty.makeInfoBean(pwmApplication);

        final Map<String,String> statData = new TreeMap<>();
        for (final Statistic loopStat : Statistic.values()) {
            statData.put(loopStat.getKey(),bundle.getStatistic(loopStat));
        }

        final List<String> configuredSettings = new ArrayList<>();
        for (final PwmSetting pwmSetting : config.nonDefaultSettings()) {
            if (!pwmSetting.getCategory().hasProfiles() && !config.isDefaultValue(pwmSetting)) {
                configuredSettings.add(pwmSetting.getKey());
            }
        }

        final Set<PwmLdapVendor> ldapVendors = new LinkedHashSet<>();
        for (final LdapProfile ldapProfile : config.getLdapProfiles().values()) {
            try {
                final DirectoryVendor directoryVendor = ldapProfile.getProxyChaiProvider(pwmApplication).getDirectoryVendor();
                final PwmLdapVendor pwmLdapVendor = PwmLdapVendor.fromChaiVendor(directoryVendor);
                ldapVendors.add(pwmLdapVendor);
            } catch (Exception e) {
                LOGGER.trace(SessionLabel.TELEMETRY_SESSION_LABEL, "unable to read ldap vendor type for stats publication: " + e.getMessage());
            }
        }

        final Map<String, String> aboutStrings = new TreeMap<>();
        {
            for (final Map.Entry<PwmAboutProperty, String> entry : aboutPropertyStringMap.entrySet()) {
                final PwmAboutProperty pwmAboutProperty = entry.getKey();
                aboutStrings.put(pwmAboutProperty.name(), entry.getValue());
            }
            aboutStrings.remove(PwmAboutProperty.app_instanceID.name());
            aboutStrings.remove(PwmAboutProperty.app_siteUrl.name());
        }

        final TelemetryPublishBean.TelemetryPublishBeanBuilder builder = TelemetryPublishBean.builder();
        builder.timestamp(Instant.now());
        builder.id(makeId(pwmApplication));
        builder.instanceHash(pwmApplication.getSecureService().hash(pwmApplication.getInstanceID()));
        builder.installTime(pwmApplication.getInstallTime());
        builder.siteDescription(config.readSettingAsString(PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION));
        builder.versionBuild(PwmConstants.BUILD_NUMBER);
        builder.versionVersion(PwmConstants.BUILD_VERSION);
        builder.ldapVendor(Collections.unmodifiableList(new ArrayList<>(ldapVendors)));
        builder.statistics(Collections.unmodifiableMap(statData));
        builder.configuredSettings(Collections.unmodifiableList(configuredSettings));
        builder.about(aboutStrings);
        return builder.build();
    }

    private static String makeId(final PwmApplication pwmApplication) throws PwmUnrecoverableException
    {
        final String SEPARATOR = "-";
        final String DATETIME_PATTERN = "yyyyMMdd-HHmmss'Z'";
        final String timestamp = DateTimeFormatter.ofPattern(DATETIME_PATTERN).format(ZonedDateTime.now(ZoneId.of("Zulu")));
        return PwmConstants.PWM_APP_NAME.toLowerCase()
                + SEPARATOR + instanceHash(pwmApplication)
                + SEPARATOR + timestamp;

    }

    private static String instanceHash(final PwmApplication pwmApplication) throws PwmUnrecoverableException
    {
        final int MAX_HASH_LENGTH = 64;
        final String instanceID = pwmApplication.getInstanceID();
        final String hash = pwmApplication.getSecureService().hash(instanceID);
        return hash.length() > 64
                ? hash.substring(0, MAX_HASH_LENGTH)
                : hash;
    }

    @Getter
    @Builder
    private static class Settings {
        private TimeDuration publishFrequency;
        private int minimumAuthentications;
        private String senderImplementation;
        private String senderSettings;

        static Settings fromConfig(final Configuration config) {
            return Settings.builder()
                    .minimumAuthentications(Integer.parseInt(config.readAppProperty(AppProperty.TELEMETRY_MIN_AUTHENTICATIONS)))
                    .publishFrequency(new TimeDuration(Integer.parseInt(config.readAppProperty(AppProperty.TELEMETRY_SEND_FREQUENCY_SECONDS)),TimeUnit.SECONDS))
                    .senderImplementation(config.readAppProperty(AppProperty.TELEMETRY_SENDER_IMPLEMENTATION))
                    .senderSettings(config.readAppProperty(AppProperty.TELEMETRY_SENDER_SETTINGS))
                    .build();
        }
    }

    private TimeDuration durationUntilNextPublish() {

        final Instant nextPublishTime = settings.getPublishFrequency().incrementFromInstant(lastPublishTime);
        final Instant minuteFromNow = TimeDuration.MINUTE.incrementFromInstant(Instant.now());
        return nextPublishTime.isBefore(minuteFromNow)
                ? TimeDuration.fromCurrent(minuteFromNow)
                : TimeDuration.fromCurrent(nextPublishTime.toEpochMilli() + (PwmRandom.getInstance().nextInt(600) - 300));
    }

}
