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

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JavaHelper;

import java.util.Collections;

public class TokenInfoCommand extends AbstractCliCommand
{
    protected static final String TOKEN_KEY_OPTIONNAME = "token";

    public void doCommand( )
            throws Exception
    {
        final String tokenKey = ( String ) cliEnvironment.getOptions().get( TOKEN_KEY_OPTIONNAME );
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();

        final TokenService tokenService = pwmApplication.getTokenService();
        TokenPayload tokenPayload = null;
        Exception lookupError = null;
        try
        {
            tokenPayload = tokenService.retrieveTokenData( SessionLabel.TOKEN_SESSION_LABEL, tokenKey );
        }
        catch ( final Exception e )
        {
            lookupError = e;
        }

        out( " token: " + tokenKey );
        if ( lookupError != null )
        {
            out( "result: error during token lookup: " + lookupError.toString() );
        }
        else if ( tokenPayload == null )
        {
            out( "result: token not found" );
        }
        else
        {
            out( "  name: " + tokenPayload.getName() );
            out( "  user: " + tokenPayload.getUserIdentity() );
            out( "issued: " + JavaHelper.toIsoDate( tokenPayload.getIssueTime() ) );
            out( "expire: " + JavaHelper.toIsoDate( tokenPayload.getExpiration() ) );
            for ( final String key : tokenPayload.getData().keySet() )
            {
                final String value = tokenPayload.getData().get( key );
                out( "  payload key: " + key );
                out( "        value: " + value );
            }
        }
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters.Option tokenValue = new CliParameters.Option()
        {
            public boolean isOptional( )
            {
                return false;
            }

            public Type getType( )
            {
                return Type.STRING;
            }

            public String getName( )
            {
                return TOKEN_KEY_OPTIONNAME;
            }
        };

        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "TokenInfo";
        cliParameters.description = "Get information about an issued token";
        cliParameters.options = Collections.singletonList( tokenValue );
        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;
        return cliParameters;
    }
}
