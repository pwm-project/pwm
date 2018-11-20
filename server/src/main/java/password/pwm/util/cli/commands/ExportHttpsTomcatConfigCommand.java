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

package password.pwm.util.cli.commands;

import org.apache.commons.io.IOUtils;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.TLSVersion;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
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
        catch ( IOException e )
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

    /**
     * Invoked (via reflection) by tomcatOneJar class in Onejar module.
     * @param applicationPath application path containing configuration file.
     * @return Properties with tomcat connector parameters.
     * @throws PwmUnrecoverableException if problem loading config
     */
    public static Properties readAsProperties( final String applicationPath )
            throws PwmUnrecoverableException
    {
        final File configFile = new File( applicationPath + File.separator + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );
        final ConfigurationReader reader = new ConfigurationReader( configFile );
        final Configuration configuration = reader.getConfiguration();
        final String sslProtocolSettingValue = TomcatConfigWriter.getTlsProtocolsValue( configuration );
        final Properties newProps = new Properties();
        newProps.setProperty( "sslEnabledProtocols",  sslProtocolSettingValue );
        final String ciphers = configuration.readSettingAsString( PwmSetting.HTTPS_CIPHERS );
        if ( !StringUtil.isEmpty( ciphers ) )
        {
            newProps.setProperty( "ciphers", ciphers );
        }
        return newProps;
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

        private static String getTlsProtocolsValue( final Configuration configuration )
        {
            final Set<TLSVersion> tlsVersions = configuration.readSettingAsOptionList( PwmSetting.HTTPS_PROTOCOLS, TLSVersion.class );
            final StringBuilder output = new StringBuilder();
            for ( final Iterator<TLSVersion> versionIterator = tlsVersions.iterator(); versionIterator.hasNext(); )
            {
                final TLSVersion tlsVersion = versionIterator.next();
                output.append( tlsVersion.getTomcatValueName() );
                if ( versionIterator.hasNext() )
                {
                    output.append( ", " );
                }
            }
            return output.toString();
        }
    }
}
