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
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class FileInfoDebugItemGenerator implements AppItemGenerator
{
    enum CsvHeaders
    {
        Filepath,
        Filename,
        ModifiedTimestamp,
        Size,
        Sha512Hash,
    }

    @Override
    public String getFilename()
    {
        return "fileinformation.csv";
    }

    @Override
    public void outputItem( final AppDebugItemRequest debugItemInput, final OutputStream outputStream )
            throws IOException
    {
        final PwmApplication pwmApplication = debugItemInput.pwmApplication();
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
                debugItemInput.logger().error( this, () -> "unable to generate webInfPath fileMd5sums during zip debug building: " + e.getMessage()  );
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
                debugItemInput.logger().error( this, () -> "unable to generate appPath fileMd5sums during zip debug building: " + e.getMessage() );
            }
        }

        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );
        {
            final List<String> headerRow = CollectionUtil.enumSetToStringList( EnumSet.allOf( CsvHeaders.class ) );
            csvPrinter.printComment( StringUtil.join( headerRow, "," ) );
        }

        final Consumer<FileSystemUtility.FileSummaryInformation> consumer = new Consumer<FileSystemUtility.FileSummaryInformation>()
        {
            @Override
            public void accept( final FileSystemUtility.FileSummaryInformation fileSummaryInformation )
            {
                try
                {
                    final List<String> dataRow = List.of(
                            fileSummaryInformation.filepath(),
                            fileSummaryInformation.filename(),
                            StringUtil.toIsoDate( fileSummaryInformation.modified() ),
                            String.valueOf( fileSummaryInformation.size() ),
                            fileSummaryInformation.sha512Hash() );

                    csvPrinter.printRecord( dataRow );
                }
                catch ( final Exception e )
                {
                    debugItemInput.logger().error( FileInfoDebugItemGenerator.this,
                            () -> "error generating file summary info: " + e.getMessage() );
                }
            }
        };

        FileSystemUtility.readFileInformation( interestedFiles ).forEach( consumer );
        csvPrinter.flush();
    }
}
