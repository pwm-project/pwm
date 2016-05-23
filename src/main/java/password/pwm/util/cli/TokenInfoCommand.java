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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.util.Helper;

import java.util.Collections;

public class TokenInfoCommand extends AbstractCliCommand {
    protected static final String TOKEN_KEY_OPTIONNAME = "token";

    public void doCommand()
            throws Exception
    {
        final String tokenKey = (String)cliEnvironment.getOptions().get(TOKEN_KEY_OPTIONNAME);
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final TokenService tokenService = pwmApplication.getTokenService();
        TokenPayload tokenPayload = null;
        Exception lookupError = null;
        try {
            tokenPayload = tokenService.retrieveTokenData(tokenKey);
        } catch (Exception e) {
            lookupError = e;
        }

        out(" token: " + tokenKey);
        if (lookupError != null) {
            out("result: error during token lookup: " + lookupError.toString());
        } else if (tokenPayload == null) {
            out("result: token not found");
        } else {
            out("  name: " + tokenPayload.getName());
            out("  user: " + tokenPayload.getUserIdentity());
            out("issued: " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(tokenPayload.getDate()));
            for (final String key : tokenPayload.getData().keySet()) {
                final String value = tokenPayload.getData().get(key);
                out("  payload key: " + key);
                out("        value: " + value);
            }
        }

        pwmApplication.shutdown();
        Helper.pause(1000);
    }

    @Override
    public CliParameters getCliParameters()
    {
        final CliParameters.Option tokenValue = new CliParameters.Option() {
            public boolean isOptional()
            {
                return false;
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
        cliParameters.options = Collections.singletonList(tokenValue);
        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;
        return cliParameters;
    }
}
