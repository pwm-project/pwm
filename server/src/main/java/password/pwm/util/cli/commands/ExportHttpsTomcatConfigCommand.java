/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.util.cli.commands;

import org.apache.commons.io.IOUtils;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.TLSVersion;
import password.pwm.util.cli.CliParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ExportHttpsTomcatConfigCommand extends AbstractCliCommand
{

    @Override
    void doCommand( ) throws Exception
    {
        final File sourceFile = ( File ) cliEnvironment.getOptions().get( "sourceFile" );
        final File outputFile = ( File ) cliEnvironment.getOptions().get( "outputFile" );
        try (
                FileInputStream fileInputStream = new FileInputStream( sourceFile );
                FileOutputStream fileOutputStream = new FileOutputStream( outputFile )
        )
        {
            TomcatConfigWriter.writeOutputFile(
                    cliEnvironment.getConfig(),
                    fileInputStream,
                    fileOutputStream
            );
        }
        catch ( final IOException e )
        {
            out( "error during tomcat config file export: " + e.getMessage() );
        }
        out( "successfully exported tomcat https settings to " + outputFile.getAbsolutePath() );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportHttpsTomcatConfig";
        cliParameters.description = "Export the https settings to the tomcat configuration based on a tokenized source server.xml file";

        final CliParameters.Option sourceFileOpt = new CliParameters.Option()
        {
            public boolean isOptional( )
            {
                return false;
            }

            public Type getType( )
            {
                return Type.EXISTING_FILE;
            }

            public String getName( )
            {
                return "sourceFile";
            }

        };

        final List<CliParameters.Option> options = new ArrayList<>();
        options.add( sourceFileOpt );
        options.add( CliParameters.REQUIRED_NEW_OUTPUT_FILE );

        cliParameters.options = options;

        cliParameters.needsLocalDB = false;
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;

        return cliParameters;
    }

    public static class TomcatConfigWriter
    {

        private static final String TOKEN_TLS_PROTOCOLS = "%TLS_PROTOCOLS%";
        private static final String TOKEN_TLS_CIPHERS = "%TLS_CIPHERS%";

        public static void writeOutputFile(
                final Configuration configuration,
                final InputStream sourceFile,
                final OutputStream outputFile
        )
                throws IOException
        {
            String fileContents = IOUtils.toString( sourceFile, PwmConstants.DEFAULT_CHARSET.toString() );
            fileContents = fileContents.replace( TOKEN_TLS_PROTOCOLS, getTlsProtocolsValue( configuration ) );
            final String tlsCiphers = configuration.readSettingAsString( PwmSetting.HTTPS_CIPHERS );
            fileContents = fileContents.replace( TOKEN_TLS_CIPHERS, tlsCiphers );
            outputFile.write( fileContents.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }

        public static String getTlsProtocolsValue( final Configuration configuration )
        {
            final Set<TLSVersion> tlsVersions = configuration.readSettingAsOptionList( PwmSetting.HTTPS_PROTOCOLS, TLSVersion.class );
            final StringBuilder output = new StringBuilder();
            for ( final Iterator<TLSVersion> versionIterator = tlsVersions.iterator(); versionIterator.hasNext(); )
            {
                final TLSVersion tlsVersion = versionIterator.next();
                output.append( "+" ).append( tlsVersion.getTomcatValueName() );
                if ( versionIterator.hasNext() )
                {
                    output.append( ", " );
                }
            }
            return output.toString();
        }
    }
}
