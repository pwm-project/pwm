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

package password.pwm.util.debug;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmApplication;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

class FileInfoDebugItemGenerator implements AppItemGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileInfoDebugItemGenerator.class );

    @Override
    public String getFilename()
    {
        return "fileinformation.csv";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
        final File applicationPath = pwmApplication.getPwmEnvironment().getApplicationPath();
        final List<File> interestedFiles = new ArrayList<>();

        if ( pwmApplication.getPwmEnvironment().getContextManager() != null )
        {
            try
            {
                final Optional<File> webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if ( webInfPath.isPresent() && webInfPath.get().exists() )
                {
                    final File servletRootPath = webInfPath.get().getParentFile();

                    if ( servletRootPath != null )
                    {
                        interestedFiles.add( webInfPath.orElseThrow() );
                    }
                }
            }
            catch ( final Exception e )
            {
                LOGGER.error( debugItemInput.getSessionLabel(), () -> "unable to generate webInfPath fileMd5sums during zip debug building: " + e.getMessage() );
            }
        }

        if ( applicationPath != null )
        {
            try
            {
                interestedFiles.add( applicationPath );
            }
            catch ( final Exception e )
            {
                LOGGER.error( debugItemInput.getSessionLabel(), () -> "unable to generate appPath fileMd5sums during zip debug building: " + e.getMessage() );
            }
        }

        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( outputStream );
        {
            final List<String> headerRow = new ArrayList<>();
            headerRow.add( "Filepath" );
            headerRow.add( "Filename" );
            headerRow.add( "Last Modified" );
            headerRow.add( "Size" );
            headerRow.add( "Checksum" );
            csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
        }

        final Iterator<FileSystemUtility.FileSummaryInformation> iter = FileSystemUtility.readFileInformation( interestedFiles );
        while ( iter.hasNext() )
        {
            final FileSystemUtility.FileSummaryInformation fileSummaryInformation = iter.next();
            try
            {
                final List<String> dataRow = new ArrayList<>();
                dataRow.add( fileSummaryInformation.getFilepath() );
                dataRow.add( fileSummaryInformation.getFilename() );
                dataRow.add( JavaHelper.toIsoDate( fileSummaryInformation.getModified() ) );
                dataRow.add( String.valueOf( fileSummaryInformation.getSize() ) );
                dataRow.add( Long.toString( fileSummaryInformation.getChecksum() ) );
                csvPrinter.printRecord( dataRow );
            }
            catch ( final Exception e )
            {
                LOGGER.trace( () -> "error generating file summary info: " + e.getMessage() );
            }
        }
        csvPrinter.flush();
    }
}
