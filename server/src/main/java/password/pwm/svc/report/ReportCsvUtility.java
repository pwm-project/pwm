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

import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.config.AppConfig;
import password.pwm.config.SettingReader;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;

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


    public static void outputSummaryToCsv(
            final AppConfig config,
            final ReportSummaryData reportSummaryData,
            final OutputStream outputStream,
            final Locale locale
    )
            throws IOException
    {

        final List<ReportSummaryData.PresentationRow> outputList = reportSummaryData.asPresentableCollection( config, locale );
        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );

        for ( final ReportSummaryData.PresentationRow presentationRow : outputList )
        {
            final List<String> row = List.of(
                    presentationRow.getLabel(),
                    presentationRow.getCount(),
                    presentationRow.getPct() );

            csvPrinter.printRecord( row );
        }

        csvPrinter.flush();
    }

    static void outputHeaderRow( final Locale locale, final CSVPrinter csvPrinter, final SettingReader config )
            throws IOException
    {
        final List<String> headerRow = new ArrayList<>();
        final Class<? extends PwmDisplayBundle> localeClass = password.pwm.i18n.Admin.class;

        final LocaleHelper.Factory localeFactory = LocaleHelper.Factory.createFactory( config, locale, localeClass );
        headerRow.add( localeFactory.get( "Field_Report_DomainID" ) );
        headerRow.add( localeFactory.get( "Field_Report_Username" ) );
        headerRow.add( localeFactory.get( "Field_Report_UserDN" ) );
        headerRow.add( localeFactory.get( "Field_Report_LDAP_Profile" ) );
        headerRow.add( localeFactory.get( "Field_Report_Email" ) );
        headerRow.add( localeFactory.get( "Field_Report_UserGuid" ) );
        headerRow.add( localeFactory.get( "Field_Report_AccountExpireTime" ) );
        headerRow.add( localeFactory.get( "Field_Report_PwdExpireTime" ) );
        headerRow.add( localeFactory.get( "Field_Report_PwdChangeTime" ) );
        headerRow.add( localeFactory.get( "Field_Report_ResponseSaveTime" ) );
        headerRow.add( localeFactory.get( "Field_Report_LastLogin" ) );
        headerRow.add( localeFactory.get( "Field_Report_HasResponses" ) );
        headerRow.add( localeFactory.get( "Field_Report_HasHelpdeskResponses" ) );
        headerRow.add( localeFactory.get( "Field_Report_ResponseStorageMethod" ) );
        headerRow.add( localeFactory.get( "Field_Report_ResponseFormatType" ) );
        headerRow.add( localeFactory.get( "Field_Report_PwdExpired" ) );
        headerRow.add( localeFactory.get( "Field_Report_PwdPreExpired" ) );
        headerRow.add( localeFactory.get( "Field_Report_PwdViolatesPolicy" ) );
        headerRow.add( localeFactory.get( "Field_Report_PwdWarnPeriod" ) );
        headerRow.add( localeFactory.get( "Field_Report_RequiresPasswordUpdate" ) );
        headerRow.add( localeFactory.get( "Field_Report_RequiresResponseUpdate" ) );
        headerRow.add( localeFactory.get( "Field_Report_RequiresProfileUpdate" ) );
        headerRow.add( localeFactory.get( "Field_Report_RecordCacheTime" ) );

        csvPrinter.printRecord( headerRow );
    }

    static void outputRecordRow(
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
        csvRow.add( userReportRecord.getLdapProfile() );
        csvRow.add( userReportRecord.getEmail() );
        csvRow.add( userReportRecord.getUserGUID() );
        csvRow.add( userReportRecord.getAccountExpirationTime() == null
                ? naField
                : JavaHelper.toIsoDate( userReportRecord.getAccountExpirationTime() ) );
        csvRow.add( userReportRecord.getPasswordExpirationTime() == null
                ? naField
                : JavaHelper.toIsoDate( userReportRecord.getPasswordExpirationTime() ) );
        csvRow.add( userReportRecord.getPasswordChangeTime() == null
                ? naField
                : JavaHelper.toIsoDate( userReportRecord.getPasswordChangeTime() ) );
        csvRow.add( userReportRecord.getResponseSetTime() == null
                ? naField
                : JavaHelper.toIsoDate( userReportRecord.getResponseSetTime() ) );
        csvRow.add( userReportRecord.getLastLoginTime() == null
                ? naField
                : JavaHelper.toIsoDate( userReportRecord.getLastLoginTime() ) );
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
                : JavaHelper.toIsoDate( userReportRecord.getCacheTimestamp() ) );

        csvPrinter.printRecord( csvRow );
    }
}
