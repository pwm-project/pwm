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

import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JavaHelper;

import java.util.List;

public class TokenInfoCommand extends AbstractCliCommand
{
    protected static final String TOKEN_KEY_OPTION_TOKEN = "token";
    protected static final String TOKEN_KEY_OPTION_DOMAIN = "domain";


    @Override
    public void doCommand( )
            throws Exception
    {
        final String tokenKey = ( String ) cliEnvironment.getOptions().get( TOKEN_KEY_OPTION_TOKEN );
        final String tokenId = ( String ) cliEnvironment.getOptions().get( TOKEN_KEY_OPTION_DOMAIN );
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();
        final PwmDomain pwmDomain = pwmApplication.domains().get( tokenId );

        final TokenService tokenService = pwmDomain.getTokenService();
        TokenPayload tokenPayload = null;
        Exception lookupError = null;
        try
        {
            tokenPayload = tokenService.retrieveTokenData( SessionLabel.TEST_SESSION_LABEL, tokenKey );
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
                return TOKEN_KEY_OPTION_TOKEN;
            }
        };

        final CliParameters.Option domainId = new CliParameters.Option()
        {
            @Override
            public boolean isOptional()
            {
                return false;
            }

            @Override
            public Type getType()
            {
                return Type.STRING;
            }

            @Override
            public String getName()
            {
                return TOKEN_KEY_OPTION_DOMAIN;
            }
        };

        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "TokenInfo";
        cliParameters.description = "Get information about an issued token";
        cliParameters.options = List.of( tokenValue, domainId );
        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = false;
        return cliParameters;
    }
}
