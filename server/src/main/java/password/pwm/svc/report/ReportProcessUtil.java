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
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.config.AppConfig;
import password.pwm.error.PwmInternalException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ReportProcessUtil
{
    static void outputResult(
            final ZipOutputStream zipOutputStream,
            final ReportProcessResult result
    )
            throws IOException
    {
        final String jsonData = JsonFactory.get().serialize( result, ReportProcessResult.class, JsonProvider.Flag.PrettyPrint );

        zipOutputStream.putNextEntry( new ZipEntry( "result.json" ) );
        zipOutputStream.write( jsonData.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        zipOutputStream.closeEntry();
    }

    static void outputSummary(
            final PwmDomain pwmDomain,
            final ReportSummaryCalculator summaryData,
            final Locale locale,
            final ZipOutputStream zipOutputStream
    )
            throws IOException
    {
        zipOutputStream.putNextEntry( new ZipEntry( "summary.json" ) );
        try
        {
            outputJsonSummaryToZip( summaryData, zipOutputStream );
        }
        finally
        {
            zipOutputStream.closeEntry();
        }

        zipOutputStream.putNextEntry( new ZipEntry( "summary.csv" ) );
        try
        {
            outputSummaryToCsv( pwmDomain.getPwmApplication().getConfig(), summaryData, zipOutputStream, locale );
        }
        finally
        {
            zipOutputStream.closeEntry();
        }
    }

    private static void outputJsonSummaryToZip( final ReportSummaryCalculator reportSummary, final OutputStream outputStream )
    {
        try
        {
            final ReportSummaryData data = ReportSummaryData.fromCalculator( reportSummary );
            final String json = JsonFactory.get().serialize( data, ReportSummaryData.class, JsonProvider.Flag.PrettyPrint );
            outputStream.write( json.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
        catch ( final IOException e )
        {
            throw new PwmInternalException( e.getMessage(), e );
        }
    }

    private static void outputSummaryToCsv(
            final AppConfig config,
            final ReportSummaryCalculator reportSummaryData,
            final OutputStream outputStream,
            final Locale locale
    )
            throws IOException
    {

        final List<ReportSummaryCalculator.PresentationRow> outputList = reportSummaryData.asPresentableCollection( config, locale );

        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );

        for ( final ReportSummaryCalculator.PresentationRow presentationRow : outputList )
        {
            csvPrinter.printRecord( presentationRow.toStringList() );
        }

        csvPrinter.flush();
    }

    public static void outputErrors( final ZipOutputStream zipOutputStream, final List<String> recordErrorMessages )
            throws IOException
    {
        if ( CollectionUtil.isEmpty( recordErrorMessages ) )
        {
            return;
        }

        zipOutputStream.putNextEntry( new ZipEntry( "errors.csv" ) );
        try
        {
            final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( zipOutputStream );

            for ( final String errorMsg : recordErrorMessages )
            {
                csvPrinter.printRecord( List.of( errorMsg ) );
            }

            csvPrinter.flush();
        }
        finally
        {
            zipOutputStream.closeEntry();
        }
    }
}
