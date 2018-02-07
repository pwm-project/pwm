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
import password.pwm.util.LocaleHelper;
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
        final ReportColumnFilter columnFilter = new ReportColumnFilter();

        outputToCsv( outputStream, includeHeader, locale, config, columnFilter );
    }

    public void outputToCsv( final OutputStream outputStream, final boolean includeHeader, final Locale locale, final ReportColumnFilter columnFilter )
            throws IOException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();
        outputToCsv( outputStream, includeHeader, locale, config, columnFilter );
    }

    public void outputToCsv( final OutputStream outputStream, final boolean includeHeader, final Locale locale, final Configuration config, final ReportColumnFilter columnFilter )
            throws IOException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );
        final Class localeClass = password.pwm.i18n.Admin.class;
        if ( includeHeader )
        {
            final List<String> headerRow = new ArrayList<>();

            if ( columnFilter.isUsernameVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_Username", config, localeClass ) );
            }
            if ( columnFilter.isUserDnVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_UserDN", config, localeClass ) );
            }
            if ( columnFilter.isLdapProfileVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_LDAP_Profile", config, localeClass ) );
            }
            if ( columnFilter.isEmailVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_Email", config, localeClass ) );
            }
            if ( columnFilter.isUserGuidVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_UserGuid", config, localeClass ) );
            }
            if ( columnFilter.isAccountExpirationTimeVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_AccountExpireTime", config, localeClass ) );
            }
            if ( columnFilter.isPasswordExpirationTimeVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdExpireTime", config, localeClass ) );
            }
            if ( columnFilter.isPasswordChangeTimeVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdChangeTime", config, localeClass ) );
            }
            if ( columnFilter.isResponseSetTimeVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_ResponseSaveTime", config, localeClass ) );
            }
            if ( columnFilter.isLastLoginTimeVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_LastLogin", config, localeClass ) );
            }
            if ( columnFilter.isHasResponsesVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_HasResponses", config, localeClass ) );
            }
            if ( columnFilter.isHasHelpdeskResponsesVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_HasHelpdeskResponses", config, localeClass ) );
            }
            if ( columnFilter.isResponseStorageMethodVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_ResponseStorageMethod", config, localeClass ) );
            }
            if ( columnFilter.isResponseFormatTypeVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_ResponseFormatType", config, localeClass ) );
            }
            if ( columnFilter.isPasswordStatusExpiredVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdExpired", config, localeClass ) );
            }
            if ( columnFilter.isPasswordStatusPreExpiredVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdPreExpired", config, localeClass ) );
            }
            if ( columnFilter.isPasswordStatusViolatesPolicyVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdViolatesPolicy", config, localeClass ) );
            }
            if ( columnFilter.isPasswordStatusWarnPeriodVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_PwdWarnPeriod", config, localeClass ) );
            }
            if ( columnFilter.isRequiresPasswordUpdateVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RequiresPasswordUpdate", config, localeClass ) );
            }
            if ( columnFilter.isRequiresResponseUpdateVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RequiresResponseUpdate", config, localeClass ) );
            }
            if ( columnFilter.isRequiresProfileUpdateVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RequiresProfileUpdate", config, localeClass ) );
            }
            if ( columnFilter.isCacheTimestampVisible() )
            {
                headerRow.add( LocaleHelper.getLocalizedMessage( locale, "Field_Report_RecordCacheTime", config, localeClass ) );
            }


            csvPrinter.printRecord( headerRow );
        }

        ClosableIterator<UserCacheRecord> cacheBeanIterator = null;
        try
        {
            cacheBeanIterator = iterator();
            while ( cacheBeanIterator.hasNext() )
            {
                final UserCacheRecord userCacheRecord = cacheBeanIterator.next();
                outputRecordRow( config, locale, userCacheRecord, csvPrinter, columnFilter );
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
            final CSVPrinter csvPrinter,
            final ReportColumnFilter columnFilter
    )
            throws IOException
    {
        final String trueField = Display.getLocalizedMessage( locale, Display.Value_True, config );
        final String falseField = Display.getLocalizedMessage( locale, Display.Value_False, config );
        final String naField = Display.getLocalizedMessage( locale, Display.Value_NotApplicable, config );
        final List<String> csvRow = new ArrayList<>();
        if ( columnFilter.isUsernameVisible() )
        {
            csvRow.add( userCacheRecord.getUsername() );
        }
        if ( columnFilter.isUserDnVisible() )
        {
            csvRow.add( userCacheRecord.getUserDN() );
        }
        if ( columnFilter.isLdapProfileVisible() )
        {
            csvRow.add( userCacheRecord.getLdapProfile() );
        }
        if ( columnFilter.isEmailVisible() )
        {
            csvRow.add( userCacheRecord.getEmail() );
        }
        if ( columnFilter.isUserGuidVisible() )
        {
            csvRow.add( userCacheRecord.getUserGUID() );
        }
        if ( columnFilter.isAccountExpirationTimeVisible() )
        {
            csvRow.add( userCacheRecord.getAccountExpirationTime() == null
                    ? naField
                    : JavaHelper.toIsoDate( userCacheRecord.getAccountExpirationTime() ) );
        }

        if ( columnFilter.isPasswordExpirationTimeVisible() )
        {
            csvRow.add( userCacheRecord.getPasswordExpirationTime() == null
                    ? naField
                    : JavaHelper.toIsoDate( userCacheRecord.getPasswordExpirationTime() ) );
        }

        if ( columnFilter.isPasswordChangeTimeVisible() )
        {
            csvRow.add( userCacheRecord.getPasswordChangeTime() == null
                    ? naField
                    : JavaHelper.toIsoDate( userCacheRecord.getPasswordChangeTime() ) );
        }

        if ( columnFilter.isResponseSetTimeVisible() )
        {
            csvRow.add( userCacheRecord.getResponseSetTime() == null
                    ? naField
                    : JavaHelper.toIsoDate( userCacheRecord.getResponseSetTime() ) );
        }

        if ( columnFilter.isLastLoginTimeVisible() )
        {
            csvRow.add( userCacheRecord.getLastLoginTime() == null
                    ? naField
                    : JavaHelper.toIsoDate( userCacheRecord.getLastLoginTime() ) );
        }

        if ( columnFilter.isHasResponsesVisible() )
        {
            csvRow.add( userCacheRecord.isHasResponses() ? trueField : falseField );
        }
        if ( columnFilter.isHasHelpdeskResponsesVisible() )
        {
            csvRow.add( userCacheRecord.isHasHelpdeskResponses() ? trueField : falseField );
        }

        if ( columnFilter.isResponseStorageMethodVisible() )
        {
            csvRow.add( userCacheRecord.getResponseStorageMethod() == null
                    ? naField
                    : userCacheRecord.getResponseStorageMethod().toString() );
        }

        if ( columnFilter.isResponseFormatTypeVisible() )
        {
            csvRow.add( userCacheRecord.getResponseFormatType() == null
                    ? naField
                    : userCacheRecord.getResponseFormatType().toString() );
        }

        if ( columnFilter.isPasswordStatusExpiredVisible() )
        {
            csvRow.add( userCacheRecord.getPasswordStatus().isExpired() ? trueField : falseField );
        }
        if ( columnFilter.isPasswordStatusPreExpiredVisible() )
        {
            csvRow.add( userCacheRecord.getPasswordStatus().isPreExpired() ? trueField : falseField );
        }
        if ( columnFilter.isPasswordStatusViolatesPolicyVisible() )
        {
            csvRow.add( userCacheRecord.getPasswordStatus().isViolatesPolicy() ? trueField : falseField );
        }
        if ( columnFilter.isPasswordStatusWarnPeriodVisible() )
        {
            csvRow.add( userCacheRecord.getPasswordStatus().isWarnPeriod() ? trueField : falseField );
        }
        if ( columnFilter.isRequiresPasswordUpdateVisible() )
        {
            csvRow.add( userCacheRecord.isRequiresPasswordUpdate() ? trueField : falseField );
        }
        if ( columnFilter.isRequiresResponseUpdateVisible() )
        {
            csvRow.add( userCacheRecord.isRequiresResponseUpdate() ? trueField : falseField );
        }
        if ( columnFilter.isRequiresProfileUpdateVisible() )
        {
            csvRow.add( userCacheRecord.isRequiresProfileUpdate() ? trueField : falseField );
        }

        if ( columnFilter.isCacheTimestampVisible() )
        {
            csvRow.add( userCacheRecord.getCacheTimestamp() == null
                    ? naField
                    : JavaHelper.toIsoDate( userCacheRecord.getCacheTimestamp() ) );
        }

        csvPrinter.printRecord( csvRow );
    }

    public ReportService.RecordIterator<UserCacheRecord> iterator( )
    {
        return reportService.iterator();
    }
}
