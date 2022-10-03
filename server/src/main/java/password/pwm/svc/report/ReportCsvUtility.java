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

package password.pwm.svc.report;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.config.SettingReader;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportCsvUtility
{

    private final PwmApplication pwmApplication;
    private final ReportService reportService;

    public ReportCsvUtility( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
        this.reportService = pwmApplication.getReportService();
    }

    public void outputSummaryToCsv( final OutputStream outputStream, final Locale locale )
            throws IOException
    {
        final List<ReportSummaryData.PresentationRow> outputList = reportService.getSummaryData().asPresentableCollection( pwmApplication.getConfig(), locale );
        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );

        for ( final ReportSummaryData.PresentationRow presentationRow : outputList )
        {
            final List<String> headerRow = new ArrayList<>();
            headerRow.add( presentationRow.getLabel() );
            headerRow.add( presentationRow.getCount() );
            headerRow.add( presentationRow.getPct() );
            csvPrinter.printRecord( headerRow );
        }

        csvPrinter.close();
    }

    public void outputToCsv( final OutputStream outputStream, final boolean includeHeader, final Locale locale )
            throws IOException
    {
        final SettingReader config = pwmApplication.getConfig();

        outputToCsv( outputStream, includeHeader, locale, config );
    }

    @SuppressFBWarnings( "PSC_PRESIZE_COLLECTIONS" )
    public void outputToCsv( final OutputStream outputStream, final boolean includeHeader, final Locale locale, final SettingReader config )
            throws IOException
    {
        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );
        final Class<? extends PwmDisplayBundle> localeClass = password.pwm.i18n.Admin.class;
        if ( includeHeader )
        {
            final List<String> headerRow = new ArrayList<>();

            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_DomainID", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_Username", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_UserDN", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_LDAP_Profile", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_Email", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_UserGuid", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_AccountExpireTime", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdExpireTime", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdChangeTime", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_ResponseSaveTime", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_LastLogin", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_HasResponses", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_HasHelpdeskResponses", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_ResponseStorageMethod", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_ResponseFormatType", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdExpired", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdPreExpired", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdViolatesPolicy", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdWarnPeriod", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RequiresPasswordUpdate", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RequiresResponseUpdate", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RequiresProfileUpdate", config, localeClass ) );
            headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RecordCacheTime", config, localeClass ) );


            csvPrinter.printRecord( headerRow );
        }

        try ( ClosableIterator<UserReportRecord> cacheBeanIterator = iterator() )
        {
            while ( cacheBeanIterator.hasNext() )
            {
                final UserReportRecord userReportRecord = cacheBeanIterator.next();
                outputRecordRow( config, locale, userReportRecord, csvPrinter );
            }
        }

        csvPrinter.flush();
    }

    @SuppressFBWarnings( {"CE_CLASS_ENVY", "PSC_PRESIZE_COLLECTIONS"} )
    private void outputRecordRow(
            final SettingReader config,
            final Locale locale,
            final UserReportRecord userReportRecord,
            final CSVPrinter csvPrinter
    )
            throws IOException
    {
        final String trueField = Display.getLocalizedMessage( locale, Display.Value_True, config );
        final String falseField = Display.getLocalizedMessage( locale, Display.Value_False, config );
        final String naField = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, config );
        final List<String> csvRow = new ArrayList<>();
        csvRow.add( userReportRecord.getDomainID().stringValue() );
        csvRow.add( userReportRecord.getUsername() );
        csvRow.add( userReportRecord.getUserDN() );
        csvRow.add( userReportRecord.getLdapProfile() == null ? "" : userReportRecord.getLdapProfile().stringValue() );
        csvRow.add( userReportRecord.getEmail() );
        csvRow.add( userReportRecord.getUserGUID() );
        csvRow.add( userReportRecord.getAccountExpirationTime() == null
                ? naField
                : StringUtil.toIsoDate( userReportRecord.getAccountExpirationTime() ) );
        csvRow.add( userReportRecord.getPasswordExpirationTime() == null
                ? naField
                : StringUtil.toIsoDate( userReportRecord.getPasswordExpirationTime() ) );
        csvRow.add( userReportRecord.getPasswordChangeTime() == null
                ? naField
                : StringUtil.toIsoDate( userReportRecord.getPasswordChangeTime() ) );
        csvRow.add( userReportRecord.getResponseSetTime() == null
                ? naField
                : StringUtil.toIsoDate( userReportRecord.getResponseSetTime() ) );
        csvRow.add( userReportRecord.getLastLoginTime() == null
                ? naField
                : StringUtil.toIsoDate( userReportRecord.getLastLoginTime() ) );
        csvRow.add( userReportRecord.isHasResponses() ? trueField : falseField );
        csvRow.add( userReportRecord.isHasHelpdeskResponses() ? trueField : falseField );
        csvRow.add( userReportRecord.getResponseStorageMethod() == null
                ? naField
                : userReportRecord.getResponseStorageMethod().toString() );
        csvRow.add( userReportRecord.getResponseFormatType() == null
                ? naField
                : userReportRecord.getResponseFormatType().toString() );
        csvRow.add( userReportRecord.getPasswordStatus().isExpired() ? trueField : falseField );
        csvRow.add( userReportRecord.getPasswordStatus().isPreExpired() ? trueField : falseField );
        csvRow.add( userReportRecord.getPasswordStatus().isViolatesPolicy() ? trueField : falseField );
        csvRow.add( userReportRecord.getPasswordStatus().isWarnPeriod() ? trueField : falseField );
        csvRow.add( userReportRecord.isRequiresPasswordUpdate() ? trueField : falseField );
        csvRow.add( userReportRecord.isRequiresResponseUpdate() ? trueField : falseField );
        csvRow.add( userReportRecord.isRequiresProfileUpdate() ? trueField : falseField );
        csvRow.add( userReportRecord.getCacheTimestamp() == null
                ? naField
                : StringUtil.toIsoDate( userReportRecord.getCacheTimestamp() ) );

        csvPrinter.printRecord( csvRow );
    }

    public ClosableIterator<UserReportRecord> iterator( )
    {
        return reportService.iterator();
    }
}
