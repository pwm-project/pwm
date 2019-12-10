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

import password.pwm.bean.SessionLabel;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.util.PasswordData;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

public class ImportHttpsKeyStoreCommand extends AbstractCliCommand
{

    private static final String ALIAS_OPTIONNAME = "alias";
    private static final String FORMAT_OPTIONNAME = "format";

    @Override
    void doCommand( )
            throws Exception
    {
        final File inputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() );
        if ( inputFile == null || !inputFile.exists() )
        {
            out( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() + " does not exist" );
            return;
        }

        final String formatString = ( String ) cliEnvironment.getOptions().get( FORMAT_OPTIONNAME );
        final HttpsServerCertificateManager.KeyStoreFormat format;
        try
        {
            format = HttpsServerCertificateManager.KeyStoreFormat.valueOf( formatString );
        }
        catch ( final IllegalArgumentException e )
        {
            out( "unknown format '" + formatString + "', must be one of " + StringUtil.join( HttpsServerCertificateManager.KeyStoreFormat.values(), "," ) );
            return;
        }
        final String keyStorePassword = getOptionalPassword();
        final String inputAliasName = ( String ) cliEnvironment.getOptions().get( ALIAS_OPTIONNAME );

        final ConfigurationReader configurationReader = new ConfigurationReader( cliEnvironment.getConfigurationFile() );
        final StoredConfiguration storedConfiguration = configurationReader.getStoredConfiguration();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        try ( FileInputStream fileInputStream = new FileInputStream( inputFile ) )
        {
            HttpsServerCertificateManager.importKey(
                    modifier,
                    format,
                    fileInputStream,
                    new PasswordData( keyStorePassword ),
                    inputAliasName
            );
        }
        catch ( final Exception e )
        {
            out( "unable to load configured https certificate: " + e.getMessage() );
            return;
        }

        configurationReader.saveConfiguration( modifier.newStoredConfiguration(), cliEnvironment.getPwmApplication(), SessionLabel.CLI_SESSION_LABEL );
        out( "success: keystore has been imported" );
    }

    @Override
    public CliParameters getCliParameters( )
    {

        final CliParameters.Option aliasValueOption = new CliParameters.Option()
        {
            @Override
            public boolean isOptional( )
            {
                return false;
            }

            @Override
            public Type getType( )
            {
                return Type.STRING;
            }

            @Override
            public String getName( )
            {
                return ALIAS_OPTIONNAME;
            }
        };

        final CliParameters.Option formatValueOption = new CliParameters.Option()
        {
            @Override
            public boolean isOptional( )
            {
                return false;
            }

            @Override
            public Type getType( )
            {
                return Type.STRING;
            }

            @Override
            public String getName( )
            {
                return FORMAT_OPTIONNAME;
            }
        };

        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ImportHttpsKeyStore";
        cliParameters.description = "Import configured HTTPS certificate to Java KeyStore file.  [format] must be one of "
                + StringUtil.join( HttpsServerCertificateManager.KeyStoreFormat.values(), "," );
        cliParameters.options = Arrays.asList(
                CliParameters.REQUIRED_EXISTING_INPUT_FILE,
                formatValueOption,
                aliasValueOption,
                CliParameters.OPTIONAL_PASSWORD
        );

        cliParameters.needsLocalDB = false;
        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}



