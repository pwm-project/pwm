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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.cli.CliException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;

public class ExportHttpsKeyStoreCommand extends AbstractCliCommand
{

    static final String ALIAS_OPTIONNAME = "alias";

    @Override
    void doCommand( )
            throws IOException, PwmUnrecoverableException, CliException
    {
        final Path outputFile = ( Path ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName() );
        if ( Files.exists( outputFile ) )
        {
            out( "outputFile for ExportHttpsKeyStore cannot already exist" );
            return;
        }

        final String password = getOptionalPassword();
        final String alias = ( String ) cliEnvironment.getOptions().get( ALIAS_OPTIONNAME );

        final KeyStore keyStore = HttpsServerCertificateManager.keyStoreForApplication(
                cliEnvironment.getPwmApplication(),
                new PasswordData( password ),
                alias );

        try ( OutputStream outputStream = Files.newOutputStream( outputFile ) )
        {
            try
            {
                keyStore.store( outputStream, password.toCharArray() );
            }
            catch ( final Exception e )
            {
                throw new CliException( "error writing keystore to file: " + e.getMessage() );
            }
        }

        out( "successfully exported java keystore to " + outputFile );
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters.Option aliasValueOption = CliParameters.newRequiredStringOption( ALIAS_OPTIONNAME );

        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportHttpsKeyStore";
        cliParameters.description = "Export configured or auto-generated HTTPS certificate to Java KeyStore file";
        cliParameters.options = Arrays.asList(
                CliParameters.REQUIRED_NEW_OUTPUT_FILE,
                aliasValueOption,
                CliParameters.OPTIONAL_PASSWORD
        );

        cliParameters.needsLocalDB = false;
        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;

        return cliParameters;
    }
}


