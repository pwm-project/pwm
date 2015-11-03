/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.TLSVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ExportHttpsTomcatConfigCommand extends AbstractCliCommand {

    private static final String TOKEN_TLS_PROTOCOLS = "%TLS_PROTOCOLS%";
    private static final String TOKEN_TLS_CIPHERS   = "%TLS_CIPHERS%";

    @Override
    void doCommand() throws Exception {
        final File sourceFile = (File)cliEnvironment.options.get("sourceFile");
        final File outputFile = (File)cliEnvironment.options.get("outputFile");
        try {
            writeOutputFile(sourceFile,outputFile);
        } catch (IOException e) {
            out("error during tomcat config file export: " + e.getMessage());
        }
        out("successfully exported tomcat https settings to " + outputFile.getAbsolutePath());
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ExportHttpsTomcatConfig";
        cliParameters.description = "Export the https settings to the tomcat configuration based on a tokenized source server.xml file";

        final CliParameters.Option sourceFileOpt= new CliParameters.Option() {
            public boolean isOptional()
            {
                return false;
            }

            public CliParameters.Option.type getType()
            {
                return type.EXISTING_FILE;
            }

            public String getName()
            {
                return "sourceFile";
            }

        };

        final List<CliParameters.Option> options = new ArrayList<>();
        options.add(sourceFileOpt);
        options.add(CliParameters.REQUIRED_NEW_OUTPUT_FILE);

        cliParameters.options = options;

        cliParameters.needsLocalDB = false;
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;

        return cliParameters;
    }

    void writeOutputFile(final File sourceFile, final File outputFile) throws IOException {
        String fileValue = fileToString(sourceFile);
        fileValue = fileValue.replace(TOKEN_TLS_PROTOCOLS, getTlsProtocolsValue());
        final String tlsCiphers = cliEnvironment.getConfig().readSettingAsString(PwmSetting.HTTPS_CIPHERS);
        fileValue = fileValue.replace(TOKEN_TLS_CIPHERS, tlsCiphers);
        stringToFile(fileValue, outputFile);
    }


    String getTlsProtocolsValue() {
        final Configuration config = cliEnvironment.getConfig();
        final Set<TLSVersion> tlsVersions = config.readSettingAsOptionList(PwmSetting.HTTPS_PROTOCOLS, TLSVersion.class);
        final StringBuilder output = new StringBuilder();
        for (final Iterator<TLSVersion> versionIterator = tlsVersions.iterator(); versionIterator.hasNext(); ) {
            final TLSVersion tlsVersion = versionIterator.next();
            output.append(tlsVersion.getTomcatValueName());
            if (versionIterator.hasNext()) {
                output.append(", ");
            }
        }
        return output.toString();
    }

    static String fileToString(final File file) throws IOException {
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, PwmConstants.DEFAULT_CHARSET);
    }

    static void stringToFile(final String string, final File file) throws IOException {
        final byte[] outputBytes= string.getBytes(PwmConstants.DEFAULT_CHARSET);
        Files.write( file.toPath(), outputBytes, StandardOpenOption.CREATE);
    }
}
