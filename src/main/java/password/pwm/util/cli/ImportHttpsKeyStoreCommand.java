/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.util.cli;

import password.pwm.PwmConstants;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.util.PasswordData;
import password.pwm.util.StringUtil;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

public class ImportHttpsKeyStoreCommand extends AbstractCliCommand {

    private static final String ALIAS_OPTIONNAME = "alias";
    private static final String FORMAT_OPTIONNAME = "format";

    @Override
    void doCommand()
            throws Exception
    {
        final File inputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName());
        if (inputFile == null || !inputFile.exists()) {
            out(CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() + " does not exist");
            return;
        }

        final String formatString = (String)cliEnvironment.getOptions().get(FORMAT_OPTIONNAME);
        final HttpsServerCertificateManager.KeyStoreFormat format;
        try {
            format = HttpsServerCertificateManager.KeyStoreFormat.valueOf(formatString);
        } catch (IllegalArgumentException e) {
            out("unknown format '" + formatString + "', must be one of " + StringUtil.join(HttpsServerCertificateManager.KeyStoreFormat.values(),","));
            return;
        }
        final String keyStorePassword = getOptionalPassword();
        final String inputAliasName = (String)cliEnvironment.getOptions().get(ALIAS_OPTIONNAME);

        final ConfigurationReader configurationReader = new ConfigurationReader(cliEnvironment.configurationFile);
        final StoredConfigurationImpl storedConfiguration = configurationReader.getStoredConfiguration();

        try {
            HttpsServerCertificateManager.importKey(
                    storedConfiguration,
                    format,
                    new FileInputStream(inputFile),
                    new PasswordData(keyStorePassword),
                    inputAliasName
            );
        } catch (Exception e) {
            out("unable to load configured https certificate: " + e.getMessage());
            return;
        }

        configurationReader.saveConfiguration(storedConfiguration, cliEnvironment.getPwmApplication(), PwmConstants.CLI_SESSION_LABEL);
        out("success");
    }

    @Override
    public CliParameters getCliParameters()
    {

        final CliParameters.Option aliasValueOption = new CliParameters.Option() {
            @Override
            public boolean isOptional() {
                return false;
            }

            @Override
            public type getType() {
                return type.STRING;
            }

            @Override
            public String getName() {
                return ALIAS_OPTIONNAME;
            }
        };

        final CliParameters.Option formatValueOption = new CliParameters.Option() {
            @Override
            public boolean isOptional() {
                return false;
            }

            @Override
            public type getType() {
                return type.STRING;
            }

            @Override
            public String getName() {
                return FORMAT_OPTIONNAME;
            }
        };

        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ImportHttpsKeyStore";
        cliParameters.description = "Import configured HTTPS certificate to Java KeyStore file.  [format] must be one of "
                + StringUtil.join(HttpsServerCertificateManager.KeyStoreFormat.values(),",");
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



