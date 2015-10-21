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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.util.Helper;

import java.io.Console;
import java.io.File;
import java.util.Collections;

public class TokenInfoCommand extends AbstractCliCommand {
    protected static final String TOKEN_KEY_OPTIONNAME = "tokenKey";

    public void doCommand()
            throws Exception
    {
        final ConfigurationReader configurationReader = new ConfigurationReader(new File(PwmConstants.DEFAULT_CONFIG_FILE_FILENAME));
        final StoredConfigurationImpl storedConfiguration = configurationReader.getStoredConfiguration();


        final String tokenKey;
        if (cliEnvironment.getOptions().containsKey(TOKEN_KEY_OPTIONNAME)) {
            tokenKey = (String)cliEnvironment.getOptions().get(TOKEN_KEY_OPTIONNAME);
        } else {
            final Console console = System.console();
            console.writer().write("enter tokenKey:");
            console.writer().flush();
            tokenKey = console.readLine();
        }
        storedConfiguration.setPassword(tokenKey);
        configurationReader.saveConfiguration(storedConfiguration, cliEnvironment.getPwmApplication(), PwmConstants.CLI_SESSION_LABEL);
        out("success");


        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final TokenService tokenService = pwmApplication.getTokenService();
        TokenPayload tokenPayload = null;
        Exception lookupError = null;
        try {
            tokenPayload = tokenService.retrieveTokenData(tokenKey);
        } catch (Exception e) {
            lookupError = e;
        }

        pwmApplication.shutdown();
        Helper.pause(1000);

        final StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append("token: ").append(tokenKey);
        output.append("\n");

        if (lookupError != null) {
            output.append("result: error during token lookup: ").append(lookupError.toString());
        } else if (tokenPayload == null) {
            output.append("result: token not found");
            return;
        } else {
            output.append("  name: ").append(tokenPayload.getName());
            output.append("  user: ").append(tokenPayload.getUserIdentity());
            output.append("issued: ").append(PwmConstants.DEFAULT_DATETIME_FORMAT.format(tokenPayload.getDate()));
            for (final String key : tokenPayload.getData().keySet()) {
                final String value = tokenPayload.getData().get(key);
                output.append("  payload key: ").append(key).append(", value:").append(value);
            }
        }
        out(output.toString());
   }

    @Override
    public CliParameters getCliParameters()
    {
        final CliParameters.Option passwordValueOption = new CliParameters.Option() {
            public boolean isOptional()
            {
                return true;
            }

            public type getType()
            {
                return type.STRING;
            }

            public String getName()
            {
                return TOKEN_KEY_OPTIONNAME;
            }
        };

        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "TokenInfo";
        cliParameters.description = "Get information about an issued token";
        cliParameters.options = Collections.singletonList(passwordValueOption);
        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;
        return cliParameters;
    }
}
