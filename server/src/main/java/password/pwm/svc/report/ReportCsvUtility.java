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

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.ClosableIterator;
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

    public void outputSummaryToCsv( final OutputStream outputStream, final Locale locale )
            throws IOException
    {
        final List<ReportSummaryData.PresentationRow> outputList = reportService.getSummaryData().asPresentableCollection( pwmApplication.getConfig(), locale );
        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );

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
            throws IOException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();

        outputToCsv( outputStream, includeHeader, locale, config );
    }

    public void outputToCsv( final OutputStream outputStream, final boolean includeHeader, final Locale locale, final Configuration config )
            throws IOException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );
        final Class localeClass = password.pwm.i18n.Admin.class;
        if ( includeHeader )
        {
            final List<String> headerRow = new ArrayList<>();

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

        ClosableIterator<UserCacheRecord> cacheBeanIterator = null;
        try
        {
            cacheBeanIterator = iterator();
            while ( cacheBeanIterator.hasNext() )
            {
                final UserCacheRecord userCacheRecord = cacheBeanIterator.next();
                outputRecordRow( config, locale, userCacheRecord, csvPrinter );
            }
        }
        finally
        {
            if ( cacheBeanIterator != null )
            {
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
        final String trueField = Display.getLocalizedMessage( locale, Display.Value_True, config );
        final String falseField = Display.getLocalizedMessage( locale, Display.Value_False, config );
        final String naField = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, config );
        final List<String> csvRow = new ArrayList<>();
        csvRow.add( userCacheRecord.getUsername() );
        csvRow.add( userCacheRecord.getUserDN() );
        csvRow.add( userCacheRecord.getLdapProfile() );
        csvRow.add( userCacheRecord.getEmail() );
        csvRow.add( userCacheRecord.getUserGUID() );
        csvRow.add( userCacheRecord.getAccountExpirationTime() == null
                ? naField
                : JavaHelper.toIsoDate( userCacheRecord.getAccountExpirationTime() ) );
        csvRow.add( userCacheRecord.getPasswordExpirationTime() == null
                ? naField
                : JavaHelper.toIsoDate( userCacheRecord.getPasswordExpirationTime() ) );
        csvRow.add( userCacheRecord.getPasswordChangeTime() == null
                ? naField
                : JavaHelper.toIsoDate( userCacheRecord.getPasswordChangeTime() ) );
        csvRow.add( userCacheRecord.getResponseSetTime() == null
                ? naField
                : JavaHelper.toIsoDate( userCacheRecord.getResponseSetTime() ) );
        csvRow.add( userCacheRecord.getLastLoginTime() == null
                ? naField
                : JavaHelper.toIsoDate( userCacheRecord.getLastLoginTime() ) );
        csvRow.add( userCacheRecord.isHasResponses() ? trueField : falseField );
        csvRow.add( userCacheRecord.isHasHelpdeskResponses() ? trueField : falseField );
        csvRow.add( userCacheRecord.getResponseStorageMethod() == null
                ? naField
                : userCacheRecord.getResponseStorageMethod().toString() );
        csvRow.add( userCacheRecord.getResponseFormatType() == null
                ? naField
                : userCacheRecord.getResponseFormatType().toString() );
        csvRow.add( userCacheRecord.getPasswordStatus().isExpired() ? trueField : falseField );
        csvRow.add( userCacheRecord.getPasswordStatus().isPreExpired() ? trueField : falseField );
        csvRow.add( userCacheRecord.getPasswordStatus().isViolatesPolicy() ? trueField : falseField );
        csvRow.add( userCacheRecord.getPasswordStatus().isWarnPeriod() ? trueField : falseField );
        csvRow.add( userCacheRecord.isRequiresPasswordUpdate() ? trueField : falseField );
        csvRow.add( userCacheRecord.isRequiresResponseUpdate() ? trueField : falseField );
        csvRow.add( userCacheRecord.isRequiresProfileUpdate() ? trueField : falseField );
        csvRow.add( userCacheRecord.getCacheTimestamp() == null
                ? naField
                : JavaHelper.toIsoDate( userCacheRecord.getCacheTimestamp() ) );

        csvPrinter.printRecord( csvRow );
    }

    public ReportService.RecordIterator<UserCacheRecord> iterator( )
    {
        return reportService.iterator();
    }
}
