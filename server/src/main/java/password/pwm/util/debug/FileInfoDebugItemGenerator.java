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
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream )
            throws IOException
    {
        final PwmApplication pwmApplication = debugItemInput.getPwmApplication();
        final Path applicationPath = pwmApplication.getPwmEnvironment().getApplicationPath();
        final List<Path> interestedFiles = new ArrayList<>();

        if ( pwmApplication.getPwmEnvironment().getContextManager() != null )
        {
            try
            {
                final Optional<Path> webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if ( webInfPath.isPresent() && Files.exists( webInfPath.get() ) )
                {
                    final Path servletRootPath = webInfPath.get().getParent();

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

        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );
        {
            final List<String> headerRow = new ArrayList<>();
            headerRow.add( "Filepath" );
            headerRow.add( "Filename" );
            headerRow.add( "Last Modified" );
            headerRow.add( "Size" );
            headerRow.add( "Sha512Hash" );
            csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
        }

        final List<FileSystemUtility.FileSummaryInformation> fileSummaries = FileSystemUtility.readFileInformation( interestedFiles );
        for ( final FileSystemUtility.FileSummaryInformation fileSummaryInformation : fileSummaries )
        {
            try
            {
                final List<String> dataRow = List.of(
                        fileSummaryInformation.getFilepath(),
                        fileSummaryInformation.getFilename(),
                        StringUtil.toIsoDate( fileSummaryInformation.getModified() ),
                        String.valueOf( fileSummaryInformation.getSize() ),
                        fileSummaryInformation.getSha512Hash() );

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
