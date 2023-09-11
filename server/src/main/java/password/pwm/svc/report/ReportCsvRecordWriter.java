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
import password.pwm.PwmDomain;
import password.pwm.config.SettingReader;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ReportCsvRecordWriter implements ReportRecordWriter
{
    private final Locale locale;
    private final PwmDomain pwmDomain;
    private final CSVPrinter csvPrinter;

    ReportCsvRecordWriter( final OutputStream outputStream, final PwmDomain pwmDomain, final Locale locale )
            throws IOException
    {
        this.csvPrinter = PwmUtil.makeCsvPrinter( outputStream );
        this.locale = locale;
        this.pwmDomain = pwmDomain;
    }

    static void outputHeaderRow( final Locale locale, final CSVPrinter csvPrinter, final SettingReader config )
            throws IOException
    {
        final Class<? extends PwmDisplayBundle> localeClass = password.pwm.i18n.Admin.class;
        final LocaleHelper.Factory localeFactory = LocaleHelper.Factory.createFactory( config, locale, localeClass );
        final List<String> headerRow = List.of(
                localeFactory.get( "Field_Report_DomainID" ),
                localeFactory.get( "Field_Report_Username" ),
                localeFactory.get( "Field_Report_UserDN" ),
                localeFactory.get( "Field_Report_LDAP_Profile" ),
                localeFactory.get( "Field_Report_Email" ),
                localeFactory.get( "Field_Report_UserGuid" ),
                localeFactory.get( "Field_Report_AccountExpireTime" ),
                localeFactory.get( "Field_Report_PwdExpireTime" ),
                localeFactory.get( "Field_Report_PwdChangeTime" ),
                localeFactory.get( "Field_Report_ResponseSaveTime" ),
                localeFactory.get( "Field_Report_LastLogin" ),
                localeFactory.get( "Field_Report_HasResponses" ),
                localeFactory.get( "Field_Report_HasHelpdeskResponses" ),
                localeFactory.get( "Field_Report_ResponseStorageMethod" ),
                localeFactory.get( "Field_Report_ResponseFormatType" ),
                localeFactory.get( "Field_Report_PwdExpired" ),
                localeFactory.get( "Field_Report_PwdPreExpired" ),
                localeFactory.get( "Field_Report_PwdViolatesPolicy" ),
                localeFactory.get( "Field_Report_PwdWarnPeriod" ),
                localeFactory.get( "Field_Report_RequiresPasswordUpdate" ),
                localeFactory.get( "Field_Report_RequiresResponseUpdate" ),
                localeFactory.get( "Field_Report_RequiresProfileUpdate" ),
                localeFactory.get( "Field_Report_RecordCacheTime" ) );

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
        csvRow.add( userReportRecord.getLdapProfile().stringValue() );
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
        csvRow.add( userReportRecord.getPasswordStatus().expired() ? trueField : falseField );
        csvRow.add( userReportRecord.getPasswordStatus().preExpired() ? trueField : falseField );
        csvRow.add( userReportRecord.getPasswordStatus().violatesPolicy() ? trueField : falseField );
        csvRow.add( userReportRecord.getPasswordStatus().warnPeriod() ? trueField : falseField );
        csvRow.add( userReportRecord.isRequiresPasswordUpdate() ? trueField : falseField );
        csvRow.add( userReportRecord.isRequiresResponseUpdate() ? trueField : falseField );
        csvRow.add( userReportRecord.isRequiresProfileUpdate() ? trueField : falseField );
        csvRow.add( userReportRecord.getCacheTimestamp() == null
                ? naField
                : StringUtil.toIsoDate( userReportRecord.getCacheTimestamp() ) );

        csvPrinter.printRecord( csvRow );
    }

    @Override
    public String getZipName()
    {
        return "report.csv";
    }

    @Override
    public void outputHeader() throws IOException
    {
        outputHeaderRow( locale, csvPrinter, pwmDomain.getConfig() );
    }

    @Override
    public void outputRecord( final UserReportRecord userReportRecord ) throws IOException
    {
        final SettingReader settingReader = pwmDomain.getConfig();
        outputRecordRow( settingReader, locale, userReportRecord, csvPrinter );
    }

    @Override
    public void outputFooter() throws IOException
    {
    }

    @Override
    public void close() throws IOException
    {
        csvPrinter.flush();
    }
}
