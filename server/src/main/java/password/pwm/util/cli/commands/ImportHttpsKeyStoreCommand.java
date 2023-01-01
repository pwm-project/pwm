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

package password.pwm.util.cli.commands;

import password.pwm.bean.SessionLabel;
import password.pwm.config.stored.ConfigurationFileManager;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ImportHttpsKeyStoreCommand extends AbstractCliCommand
{
    private static final String ALIAS_OPTIONNAME = "alias";
    private static final String FORMAT_OPTIONNAME = "format";

    @Override
    void doCommand( )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        final Path inputFile = ( Path ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() );
        if ( inputFile == null || !Files.exists( inputFile ) )
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

        final ConfigurationFileManager configurationFileManager = new ConfigurationFileManager(
                cliEnvironment.getConfigurationFile(),
                SessionLabel.CLI_SESSION_LABEL );
        final StoredConfiguration storedConfiguration = configurationFileManager.getStoredConfiguration();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        try ( InputStream fileInputStream = Files.newInputStream( inputFile ) )
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

        configurationFileManager.saveConfiguration( modifier.newStoredConfiguration(), cliEnvironment.getPwmApplication() );
        out( "success: keystore has been imported" );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters.Option aliasValueOption = CliParameters.newRequiredStringOption( ALIAS_OPTIONNAME );
        final CliParameters.Option formatValueOption = CliParameters.newRequiredStringOption( FORMAT_OPTIONNAME );

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



