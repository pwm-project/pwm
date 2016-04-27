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

import password.pwm.util.PasswordData;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.util.Arrays;

public class ExportHttpsKeyStoreCommand extends AbstractCliCommand {

    static final String ALIAS_OPTIONNAME = "alias";

    @Override
    void doCommand()
            throws Exception
    {
        final File outputFile = (File)cliEnvironment.getOptions().get(CliParameters.REQUIRED_NEW_OUTPUT_FILE.getName());
        if (outputFile.exists()) {
            out("outputFile for ExportHttpsKeyStore cannot already exist");
            return;
        }

        final String password = getOptionalPassword();
        final String alias = (String)cliEnvironment.getOptions().get(ALIAS_OPTIONNAME);

        final KeyStore keyStore = HttpsServerCertificateManager.keyStoreForApplication(cliEnvironment.getPwmApplication(), new PasswordData(password), alias);
        final FileOutputStream fos = new FileOutputStream(outputFile);
        keyStore.store(fos,password.toCharArray());
        fos.close();
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

        CliParameters cliParameters = new CliParameters();
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


