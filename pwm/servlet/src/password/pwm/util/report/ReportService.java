/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util.report;

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.i18n.Display;
import password.pwm.i18n.LocaleHelper;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.ClosableIterator;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;

public class ReportService implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ReportService.class);

    private final AvgTracker avgTracker = new AvgTracker(100);

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private boolean cancelFlag = false;
    private ReportStatusInfo reportStatus = new ReportStatusInfo("");
    private ReportSummaryData summaryData = ReportSummaryData.newSummaryData(null);
    private Timer timer;

    private UserCacheService userCacheService;
    private ReportSettings settings = new ReportSettings();

    public ReportService() {
    }

    public STATUS status()
    {
        return status;
    }

    public void clear()
            throws LocalDBException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        LOGGER.info(PwmConstants.REPORTING_SESSION_LABEL,"clearing cached report data");
        if (userCacheService != null) {
            userCacheService.clear();
        }
        summaryData = ReportSummaryData.newSummaryData(settings.getTrackDays());
        reportStatus = new ReportStatusInfo(settings.getSettingsHash());
        saveTempData();
        LOGGER.info(PwmConstants.REPORTING_SESSION_LABEL,"finished clearing report " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    @Override
    public void init(PwmApplication pwmApplication)
            throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.READ_ONLY) {
            LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"application mode is read-only, will remain closed");
            status = STATUS.CLOSED;
            return;
        }

        if (pwmApplication.getLocalDB() == null || LocalDB.Status.OPEN != pwmApplication.getLocalDB().status()) {
            LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"LocalDB is not open, will remain closed");
            status = STATUS.CLOSED;
            return;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.REPORTING_ENABLE)) {
            LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"reporting module is not enabled, will remain closed");
            status = STATUS.CLOSED;
            clear();
            return;
        }

        try {
            userCacheService = new UserCacheService();
            userCacheService.init(pwmApplication);
        } catch (Exception e) {
            LOGGER.error(PwmConstants.REPORTING_SESSION_LABEL,"unable to init cache service");
            status = STATUS.CLOSED;
            return;
        }

        settings = ReportSettings.readSettingsFromConfig(pwmApplication.getConfig());
        summaryData = ReportSummaryData.newSummaryData(settings.getTrackDays());
        initTempData();

        reportStatus.setInProgress(false);

        timer = new Timer();

        final long nextZuluZeroTime = Helper.nextZuluZeroTime().getTime();
        if (settings.getJobOffsetSeconds() >= 0) {
            final long nextScheduleTime = nextZuluZeroTime + (settings.getJobOffsetSeconds() * 1000);
            timer.scheduleAtFixedRate(new DredgeTask(),new Date(nextScheduleTime), TimeDuration.DAY.getTotalMilliseconds());
        }

        String startupMsg = "report service started with " + this.userCacheService.size() + " cached records";
        if (summaryData != null && summaryData.getMeanCacheTime() != null) {
            startupMsg += ", mean record timestamp " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(this.summaryData.getMeanCacheTime());
        }
        LOGGER.debug(startupMsg);
        timer.schedule(new RolloverTask(), 1);
        timer.scheduleAtFixedRate(new RolloverTask(),new Date(nextZuluZeroTime), TimeDuration.DAY.getTotalMilliseconds());

        status = STATUS.OPEN;
    }

    @Override
    public void close()
    {
        saveTempData();
        pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.REPORT_CLEAN_FLAG, "true");
        if (userCacheService != null) {
            userCacheService.close();
        }
        status = STATUS.CLOSED;
    }

    private void saveTempData() {
        try {
            final String jsonInfo = JsonUtil.serialize(reportStatus);
            pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.REPORT_STATUS,jsonInfo);
        } catch (Exception e) {
            LOGGER.error(PwmConstants.REPORTING_SESSION_LABEL,"error loading cached report dredge info into memory: " + e.getMessage());
        }
    }

    private void initTempData()
            throws LocalDBException, PwmUnrecoverableException
    {
        final String cleanFlag = pwmApplication.readAppAttribute(PwmApplication.AppAttribute.REPORT_CLEAN_FLAG);
        if (!"true".equals(cleanFlag)) {
            LOGGER.error(PwmConstants.REPORTING_SESSION_LABEL,"did not shut down cleanly, will clear cached report data");
            clear();
            return;
        }

        try {
            final String jsonInfo = pwmApplication.readAppAttribute(PwmApplication.AppAttribute.REPORT_STATUS);
            if (jsonInfo != null && !jsonInfo.isEmpty()) {
                reportStatus = JsonUtil.deserialize(jsonInfo,ReportStatusInfo.class);
            }
        } catch (Exception e) {
            LOGGER.error(PwmConstants.REPORTING_SESSION_LABEL,"error loading cached report status info into memory: " + e.getMessage());
        }
        reportStatus = reportStatus == null ? new ReportStatusInfo(settings.getSettingsHash()) : reportStatus; //safety

        final String currentSettingCache = settings.getSettingsHash();
        if (reportStatus.getSettingsHash() != null && !reportStatus.getSettingsHash().equals(currentSettingCache)) {
            LOGGER.error(PwmConstants.REPORTING_SESSION_LABEL,"configuration has changed, will clear cached report data");
            clear();
        }

        pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.REPORT_CLEAN_FLAG, "false");
    }

    @Override
    public List<HealthRecord> healthCheck()
    {
        return null;
    }

    @Override
    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.singletonList(DataStorageMethod.LDAP));
    }

    public void scheduleImmediateUpdate() {
        if (!reportStatus.isInProgress()) {
            timer.schedule(new DredgeTask(),1);
        }
    }

    public void cancelUpdate() {
        cancelFlag = true;
    }

    private void updateCacheFromLdap()
            throws ChaiUnavailableException, ChaiOperationException, PwmOperationalException, PwmUnrecoverableException
    {
        LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"beginning process to updating user cache records from ldap");
        if (status != STATUS.OPEN) {
            return;
        }
        cancelFlag = false;
        reportStatus = new ReportStatusInfo(settings.getSettingsHash());
        reportStatus.setInProgress(true);
        reportStatus.setStartDate(new Date());
        try {
            final Queue<UserIdentity> allUsers = new LinkedList<>(getListOfUsers());
            reportStatus.setTotal(allUsers.size());
            while (status == STATUS.OPEN && !allUsers.isEmpty() && !cancelFlag) {
                final Date startUpdateTime = new Date();
                final UserIdentity userIdentity = allUsers.poll();
                try {
                    if (updateCache(userIdentity)) {
                        reportStatus.setUpdated(reportStatus.getUpdated() + 1);
                    }
                } catch (Exception e) {
                    String errorMsg = "error while updating report cache for " + userIdentity.toString() + ", cause: ";
                    errorMsg += e instanceof PwmException ? ((PwmException) e).getErrorInformation().toDebugStr() : e.getMessage();
                    final ErrorInformation errorInformation;
                    errorInformation = new ErrorInformation(PwmError.ERROR_REPORTING_ERROR,errorMsg);
                    LOGGER.error(PwmConstants.REPORTING_SESSION_LABEL,errorInformation.toDebugStr());
                    reportStatus.setLastError(errorInformation);
                    reportStatus.setErrors(reportStatus.getErrors() + 1);
                }
                reportStatus.setCount(reportStatus.getCount() + 1);
                reportStatus.getEventRateMeter().markEvents(1);
                final TimeDuration totalUpdateTime = TimeDuration.fromCurrent(startUpdateTime);
                if (settings.isAutoCalcRest()) {
                    avgTracker.addSample(totalUpdateTime.getTotalMilliseconds());
                    Helper.pause(avgTracker.avgAsLong());
                } else {
                    Helper.pause(settings.getRestTime().getTotalMilliseconds());
                }
            }
            if (cancelFlag) {
                reportStatus.setLastError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"report cancelled by operator"));
            }
        } finally {
            reportStatus.setFinishDate(new Date());
            reportStatus.setInProgress(false);
        }
        LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"update user cache process completed: " + JsonUtil.serialize(reportStatus));
    }

    private void updateRestingCacheData() {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"beginning cache review process");
        final ClosableIterator<UserCacheRecord> iterator = iterator();
        int examinedRecords = 0;
        while (iterator.hasNext() && status == STATUS.OPEN) {
            final UserCacheRecord record = iterator.next(); // (purge routine is embedded in next();

            if (summaryData != null && record != null) {
                summaryData.update(record);
            }

            examinedRecords++;
        }
        iterator.close();
        final TimeDuration totalTime = TimeDuration.fromCurrent(startTime);
        LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,
                "completed cache review process of " + examinedRecords + " cached report records in " + totalTime.asCompactString());
    }

    public boolean updateCache(final UserInfoBean uiBean)
            throws LocalDBException, PwmUnrecoverableException, ChaiUnavailableException
    {
        if (status != STATUS.OPEN) {
            return false;
        }

        final UserCacheService.StorageKey storageKey = UserCacheService.StorageKey.fromUserInfoBean(uiBean);
        return updateCache(uiBean.getUserIdentity(), uiBean, storageKey);
    }

    private boolean updateCache(final UserIdentity userIdentity)
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
        if (status != STATUS.OPEN) {
            return false;
        }

        final UserCacheService.StorageKey storageKey = UserCacheService.StorageKey.fromUserIdentity(pwmApplication,
                userIdentity);
        return updateCache(userIdentity, null, storageKey);
    }

    private boolean updateCache(
            final UserIdentity userIdentity,
            final UserInfoBean userInfoBean,
            final UserCacheService.StorageKey storageKey
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, LocalDBException
    {
        final UserCacheRecord userCacheRecord = userCacheService.readStorageKey(storageKey);
        TimeDuration cacheAge = null;
        if (userCacheRecord != null && userCacheRecord.getCacheTimestamp() != null) {
            cacheAge = TimeDuration.fromCurrent(userCacheRecord.getCacheTimestamp());
        }

        boolean updateCache = false;
        if (userInfoBean != null) {
            updateCache = true;
        } else {
            if (cacheAge == null) {
                LOGGER.trace(PwmConstants.REPORTING_SESSION_LABEL,"stored cache for " + userIdentity + " is missing cache storage timestamp, will update");
                updateCache = true;
            } else if (cacheAge.isLongerThan(settings.getMinCacheAge())) {
                LOGGER.trace(PwmConstants.REPORTING_SESSION_LABEL,"stored cache for " + userIdentity + " is " + cacheAge.asCompactString() + " old, will update");
                updateCache = true;
            }
        }

        if (updateCache) {
            if (userCacheRecord != null) {
                if (summaryData != null && summaryData.getEpoch() != null && summaryData.getEpoch().equals(userCacheRecord.getSummaryEpoch())) {
                    summaryData.remove(userCacheRecord);
                }
            }
            final UserInfoBean newUserBean;
            if (userInfoBean != null) {
                newUserBean = userInfoBean;
            } else {
                newUserBean = new UserInfoBean();
                final UserStatusReader.Settings readerSettings = new UserStatusReader.Settings();
                readerSettings.setSkipReportUpdate(true);
                final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
                final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication,PwmConstants.REPORTING_SESSION_LABEL,readerSettings);
                userStatusReader.populateUserInfoBean(
                        newUserBean,
                        PwmConstants.DEFAULT_LOCALE,
                        userIdentity,
                        chaiProvider
                );
            }
            final UserCacheRecord newUserCacheRecord = userCacheService.updateUserCache(newUserBean);

            if (summaryData != null && summaryData.getEpoch() != null && newUserCacheRecord != null) {
                if (!summaryData.getEpoch().equals(newUserCacheRecord.getSummaryEpoch())) {
                    newUserCacheRecord.setSummaryEpoch(summaryData.getEpoch());
                    userCacheService.store(newUserCacheRecord);
                }
                summaryData.update(newUserCacheRecord);
            }
        }

        return updateCache;
    }


    public ReportStatusInfo getReportStatusInfo()
    {
        return reportStatus;
    }

    private List<UserIdentity> getListOfUsers()
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        return readAllUsersFromLdap(pwmApplication, settings.getSearchFilter(), settings.getMaxSearchSize());
    }

    private static List<UserIdentity> readAllUsersFromLdap(
            final PwmApplication pwmApplication,
            final String searchFilter,
            final int maxResults
    )
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication,null);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setSearchTimeout(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.REPORTING_LDAP_SEARCH_TIMEOUT)));

        if (searchFilter == null) {
            searchConfiguration.setUsername("*");
        } else {
            searchConfiguration.setFilter(searchFilter);
        }

        LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"beginning UserReportService user search using parameters: " + (JsonUtil.serialize(searchConfiguration)));

        final Map<UserIdentity,Map<String,String>> searchResults = userSearchEngine.performMultiUserSearch(searchConfiguration, maxResults, Collections.<String>emptyList());
        LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"user search found " + searchResults.size() + " users for reporting");
        final List<UserIdentity> returnList = new ArrayList<>(searchResults.keySet());
        Collections.shuffle(returnList);
        return returnList;
    }

    public ClosableIterator<UserCacheRecord> iterator() {
        return new RecordIterator(userCacheService.iterator());
    }

    public class RecordIterator implements ClosableIterator<UserCacheRecord> {

        private ClosableIterator<UserCacheService.StorageKey> storageKeyIterator;

        public RecordIterator(ClosableIterator<UserCacheService.StorageKey> storageKeyIterator) {
            this.storageKeyIterator = storageKeyIterator;
        }

        public boolean hasNext() {
            return this.storageKeyIterator.hasNext();
        }

        public UserCacheRecord next()
        {
            try {
                UserCacheRecord returnBean = null;
                while (returnBean == null && this.storageKeyIterator.hasNext()) {
                    UserCacheService.StorageKey key = this.storageKeyIterator.next();
                    returnBean = userCacheService.readStorageKey(key);
                    if (returnBean != null) {
                        if (returnBean.getCacheTimestamp() == null) {
                            LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"purging record due to missing cache timestamp: " + JsonUtil.serialize(returnBean));
                            userCacheService.removeStorageKey(key);
                        } else if (TimeDuration.fromCurrent(returnBean.getCacheTimestamp()).isLongerThan(settings.getMaxCacheAge())) {
                            LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL,"purging record due to old age timestamp: " + JsonUtil.serialize(returnBean));
                            userCacheService.removeStorageKey(key);
                        } else {
                            return returnBean;
                        }
                    }

                }
            } catch (LocalDBException e) {
                throw new IllegalStateException("unexpected iterator traversal error while reading LocalDB: " + e.getMessage());
            }
            return null;
        }

        public void remove()
        {

        }

        public void close() {
            storageKeyIterator.close();
        }
    }

    public void outputSummaryToCsv(final OutputStream outputStream, final Locale locale)
            throws IOException
    {
        final List<ReportSummaryData.PresentationRow> outputList = summaryData.asPresentableCollection(pwmApplication.getConfig(),locale);
        final CSVPrinter csvPrinter = Helper.makeCsvPrinter(outputStream);

        for (final ReportSummaryData.PresentationRow presentationRow : outputList) {
            final List<String> headerRow = new ArrayList<>();
            headerRow.add(presentationRow.getLabel());
            headerRow.add(presentationRow.getCount());
            headerRow.add(presentationRow.getPct());
            csvPrinter.printRecord(headerRow);
        }

        csvPrinter.close();
    }

    public void outputToCsv(final OutputStream outputStream, final boolean includeHeader, final Locale locale)
            throws IOException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final CSVPrinter csvPrinter = Helper.makeCsvPrinter(outputStream);
        final Configuration config = pwmApplication.getConfig();
        final Class localeClass = password.pwm.i18n.Admin.class;
        if (includeHeader) {
            final List<String> headerRow = new ArrayList<>();
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_UserDN", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_LDAP_Profile", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_Username", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_Email", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_UserGuid", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_LastLogin", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_PwdExpireTime", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_PwdChangeTime", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_ResponseSaveTime", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_HasResponses", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_HasHelpdeskResponses", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_ResponseStorageMethod", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_PwdExpired", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_PwdPreExpired", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_PwdViolatesPolicy", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_PwdWarnPeriod", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_RequiresPasswordUpdate", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_RequiresResponseUpdate", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_RequiresProfileUpdate", config, localeClass));
            headerRow.add(LocaleHelper.getLocalizedMessage(locale, "Field_Report_RecordCacheTime", config, localeClass));
            csvPrinter.printRecord(headerRow);
        }

        ClosableIterator<UserCacheRecord> cacheBeanIterator = null;
        try {
            cacheBeanIterator = this.iterator();
            while (cacheBeanIterator.hasNext()) {
                final UserCacheRecord userCacheRecord = cacheBeanIterator.next();
                outputRecordRow(config, locale, userCacheRecord, csvPrinter);
            }
        } finally {
            if (cacheBeanIterator != null) {
                cacheBeanIterator.close();
            }
        }

        csvPrinter.flush();
    }

    private void outputRecordRow(
            final Configuration config,
            final Locale locale,
            final UserCacheRecord userCacheRecord,
            final CSVPrinter csvPrinter
    )
            throws IOException
    {
        final String trueField = Display.getLocalizedMessage(locale, Display.Value_True, config);
        final String falseField = Display.getLocalizedMessage(locale, Display.Value_False, config);
        final String naField = Display.getLocalizedMessage(locale, Display.Value_NotApplicable, config);
        final List<String> csvRow = new ArrayList<>();
        csvRow.add(userCacheRecord.getUserDN());
        csvRow.add(userCacheRecord.getLdapProfile());
        csvRow.add(userCacheRecord.getUsername());
        csvRow.add(userCacheRecord.getEmail());
        csvRow.add(userCacheRecord.getUserGUID());
        csvRow.add(userCacheRecord.getLastLoginTime() == null ? naField : PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                userCacheRecord.getLastLoginTime()));
        csvRow.add(userCacheRecord.getPasswordExpirationTime() == null ? naField : PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                userCacheRecord.getPasswordExpirationTime()));
        csvRow.add(userCacheRecord.getPasswordChangeTime() == null ? naField : PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                userCacheRecord.getPasswordChangeTime()));
        csvRow.add(userCacheRecord.getResponseSetTime() == null ? naField : PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                userCacheRecord.getResponseSetTime()));
        csvRow.add(userCacheRecord.isHasResponses() ? trueField : falseField);
        csvRow.add(userCacheRecord.isHasHelpdeskResponses() ? trueField : falseField);
        csvRow.add(userCacheRecord.getResponseStorageMethod() == null ? naField : userCacheRecord.getResponseStorageMethod().toString());
        csvRow.add(userCacheRecord.getPasswordStatus().isExpired() ? trueField : falseField);
        csvRow.add(userCacheRecord.getPasswordStatus().isPreExpired() ? trueField : falseField);
        csvRow.add(userCacheRecord.getPasswordStatus().isViolatesPolicy() ? trueField : falseField);
        csvRow.add(userCacheRecord.getPasswordStatus().isWarnPeriod() ? trueField : falseField);
        csvRow.add(userCacheRecord.isRequiresPasswordUpdate() ? trueField : falseField);
        csvRow.add(userCacheRecord.isRequiresResponseUpdate() ? trueField : falseField);
        csvRow.add(userCacheRecord.isRequiresProfileUpdate() ? trueField : falseField);
        csvRow.add(userCacheRecord.getCacheTimestamp() == null ? naField : PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                userCacheRecord.getCacheTimestamp()));

        csvPrinter.printRecord(csvRow);
    }

    public ReportSummaryData getSummaryData() {
        return summaryData;
    }

    private class DredgeTask extends TimerTask {
        @Override
        public void run()
        {
            try {
                updateCacheFromLdap();
            } catch (Exception e) {
                if (e instanceof PwmException) {
                    if (((PwmException) e).getErrorInformation().getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE) {
                        if (timer != null) {
                            timer.schedule(new DredgeTask(),10 * 60 * 1000);
                        }
                    }
                }
                LOGGER.warn(PwmConstants.REPORTING_SESSION_LABEL,"unable to dredge ldap due to error: " + e.getMessage());
            }
        }
    }

    public static class AvgTracker {
        private final int maxSamples;
        private final Queue<BigInteger> samples = new LinkedList<>();

        public AvgTracker(int maxSamples)
        {
            this.maxSamples = maxSamples;
        }

        public void addSample(final long input) {
            samples.add(new BigInteger(Long.toString(input)));
            while (samples.size() > maxSamples) {
                samples.remove();
            }
        }

        public BigDecimal avg() {
            if (samples.isEmpty()) {
                throw new IllegalStateException("unable to compute avg without samples");
            }

            BigInteger total = BigInteger.ZERO;
            for (final BigInteger sample : samples) {
                total = total.add(sample);
            }
            final BigDecimal maxAsBD = new BigDecimal(Integer.toString(maxSamples));
            return new BigDecimal(total).divide(maxAsBD, MathContext.DECIMAL32);
        }

        public long avgAsLong() {
            return avg().longValue();
        }
    }

    private class RolloverTask extends TimerTask {
        public void run()
        {
            summaryData = ReportSummaryData.newSummaryData(settings.getTrackDays());
            updateRestingCacheData();
        }
    }

    public int recordsInCache() {
        if (userCacheService != null && userCacheService.status() == STATUS.OPEN) {
            try {
                return userCacheService.size();
            } catch (LocalDBException e) {
                LOGGER.debug(PwmConstants.REPORTING_SESSION_LABEL, "error reading user cache service size: " + e.getMessage());
            }
        }
        return 0;
    }

}
