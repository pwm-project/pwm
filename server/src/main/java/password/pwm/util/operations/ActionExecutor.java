/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ActionExecutor
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ActionExecutor.class );

    private PwmApplication pwmApplication;
    private ActionExecutorSettings settings;

    private ActionExecutor( final PwmApplication pwmApplication, final ActionExecutorSettings settings )
    {
        this.pwmApplication = pwmApplication;
        this.settings = settings;
    }

    public void executeActions(
            final List<ActionConfiguration> configValues,
            final SessionLabel sessionLabel
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        for ( final ActionConfiguration loopAction : configValues )
        {
            this.executeAction( loopAction, sessionLabel );
        }
    }

    public void executeAction(
            final ActionConfiguration actionConfiguration,
            final SessionLabel sessionLabel
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        LOGGER.trace( sessionLabel, () -> "preparing to execute action(s) for " + actionConfiguration.getName() );

        for ( final ActionConfiguration.LdapAction ldapAction : actionConfiguration.getLdapActions() )
        {
            executeLdapAction( sessionLabel, actionConfiguration, ldapAction );
        }

        for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
        {
            executeWebserviceAction( sessionLabel, actionConfiguration, webAction );
        }

        LOGGER.info( sessionLabel, () -> "action " + actionConfiguration.getName() + " completed successfully" );
    }

    private void executeLdapAction(
            final SessionLabel sessionLabel,
            final ActionConfiguration actionConfiguration,
            final ActionConfiguration.LdapAction ldapAction
            )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        String attributeName = ldapAction.getAttributeName();
        String attributeValue = ldapAction.getAttributeValue();

        final ChaiUser theUser;
        if ( settings.getChaiUser() != null )
        {
            theUser = settings.getChaiUser();
        }
        else
        {
            if ( settings.getUserIdentity() == null )
            {
                final String errorMsg = "attempt to execute lap action but neither chaiUser or userIdentity is specified";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
            theUser = pwmApplication.getProxiedChaiUser( settings.getUserIdentity() );
        }

        if ( settings.isExpandPwmMacros() )
        {
            if ( settings.getMacroMachine() == null )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "executor specified macro expansion but did not supply macro machine" ) );
            }
            final MacroMachine macroMachine = settings.getMacroMachine();

            attributeName = macroMachine.expandMacros( attributeName );
            attributeValue = macroMachine.expandMacros( attributeValue );
        }

        writeLdapAttribute(
                sessionLabel,
                theUser,
                attributeName,
                attributeValue,
                ldapAction.getLdapMethod(),
                settings.getMacroMachine()
        );
    }

    private void executeWebserviceAction(
            final SessionLabel sessionLabel,
            final ActionConfiguration actionConfiguration,
            final ActionConfiguration.WebAction webAction
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        String url = webAction.getUrl();
        String body = webAction.getBody();
        final Map<String, String> headers = new LinkedHashMap<>();
        if ( webAction.getHeaders() != null )
        {
            headers.putAll( webAction.getHeaders() );
        }

        try
        {
            // expand using pwm macros
            if ( settings.isExpandPwmMacros() )
            {
                if ( settings.getMacroMachine() == null )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "executor specified macro expansion but did not supply macro machine" ) );
                }
                final MacroMachine macroMachine = settings.getMacroMachine();

                url = macroMachine.expandMacros( url );
                body = body == null ? "" : macroMachine.expandMacros( body );

                for ( final Map.Entry<String, String> entry : headers.entrySet() )
                {
                    final String headerName = entry.getKey();
                    final String headerValue = entry.getValue();
                    if ( headerValue != null )
                    {
                        headers.put( headerName, macroMachine.expandMacros( headerValue ) );
                    }
                }
            }

            // add basic auth header;
            if ( !StringUtil.isEmpty( webAction.getUsername() ) && !StringUtil.isEmpty( webAction.getPassword() ) )
            {
                final String authHeaderValue = new BasicAuthInfo( webAction.getUsername(), new PasswordData( webAction.getPassword() ) ).toAuthHeader();
                headers.put( HttpHeader.Authorization.getHttpName(), authHeaderValue );
            }

            final HttpMethod method = HttpMethod.fromString( webAction.getMethod().toString() );

            final PwmHttpClientRequest clientRequest = new PwmHttpClientRequest( method, url, body, headers );
            final PwmHttpClient client;
            {
                if ( webAction.getCertificates() != null )
                {
                    final PwmHttpClientConfiguration clientConfiguration = PwmHttpClientConfiguration.builder()
                            .certificates( webAction.getCertificates() )
                            .build();

                    client = new PwmHttpClient( pwmApplication, sessionLabel, clientConfiguration );
                }
                else
                {
                    client = new PwmHttpClient( pwmApplication, sessionLabel );
                }
            }
            final PwmHttpClientResponse clientResponse = client.makeRequest( clientRequest );

            final List<Integer> successStatus = webAction.getSuccessStatus() == null
                    ? Collections.emptyList()
                    : webAction.getSuccessStatus();

            if ( !successStatus.contains( clientResponse.getStatusCode() ) )
            {
                LOGGER.trace( () -> "response status code " + clientResponse.getStatusCode() + " is not one of the configured success status codes: "
                        + StringUtil.collectionToString( successStatus ) );

                throw new PwmOperationalException( new ErrorInformation(
                        PwmError.ERROR_SERVICE_UNREACHABLE,
                        "unexpected HTTP status code while calling external web service: "
                                + clientResponse.getStatusCode() + " " + clientResponse.getStatusPhrase()
                ) );
            }

        }
        catch ( PwmException e )
        {
            if ( e instanceof PwmOperationalException )
            {
                throw ( PwmOperationalException ) e;
            }

            final String errorMsg = "unexpected error during API execution: " + e.getMessage();
            LOGGER.error( errorMsg );
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
        }
    }

    private static void writeLdapAttribute(
            final SessionLabel sessionLabel,
            final ChaiUser theUser,
            final String attrName,
            final String attrValue,
            final ActionConfiguration.LdapMethod ldapMethod,
            final MacroMachine macroMachine
    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final ActionConfiguration.LdapMethod effectiveLdapMethod = ( ldapMethod == null )
                ? ActionConfiguration.LdapMethod.replace
                : ldapMethod;

        final String effectiveAttrValue = ( macroMachine != null )
                ? macroMachine.expandMacros( attrValue )
                : attrValue;


        LOGGER.trace( sessionLabel, () -> "beginning ldap " + effectiveLdapMethod.toString() + " operation on " + theUser.getEntryDN() + ", attribute " + attrName );
        switch ( effectiveLdapMethod )
        {
            case replace:
            {
                try
                {
                    theUser.writeStringAttribute( attrName, effectiveAttrValue );
                    LOGGER.info( sessionLabel, () -> "replaced attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + effectiveAttrValue + ")" );
                }
                catch ( ChaiOperationException e )
                {
                    final String errorMsg = "error setting '" + attrName + "' attribute on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                    final PwmOperationalException newException = new PwmOperationalException( errorInformation );
                    newException.initCause( e );
                    throw newException;
                }
            }
            break;

            case add:
            {
                try
                {
                    theUser.addAttribute( attrName, effectiveAttrValue );
                    LOGGER.info( sessionLabel, () -> "added attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + effectiveAttrValue + ")" );
                }
                catch ( ChaiOperationException e )
                {
                    final String errorMsg = "error adding '" + attrName + "' attribute value from user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                    final PwmOperationalException newException = new PwmOperationalException( errorInformation );
                    newException.initCause( e );
                    throw newException;
                }

            }
            break;

            case remove:
            {
                try
                {
                    theUser.deleteAttribute( attrName, effectiveAttrValue );
                    LOGGER.info( sessionLabel, () -> "deleted attribute value on user " + theUser.getEntryDN() + " (" + attrName + ")" );
                }
                catch ( ChaiOperationException e )
                {
                    final String errorMsg = "error deleting '" + attrName + "' attribute value on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                    final PwmOperationalException newException = new PwmOperationalException( errorInformation );
                    newException.initCause( e );
                    throw newException;
                }
            }
            break;

            default:
                throw new IllegalStateException( "unexpected ldap method type " + effectiveLdapMethod );
        }
    }

    public static class ActionExecutorSettings
    {
        private final UserIdentity userIdentity;
        private final PwmApplication pwmApplication;
        private final ChaiUser chaiUser;

        private boolean expandPwmMacros = true;
        private MacroMachine macroMachine;

        public ActionExecutorSettings( final PwmApplication pwmApplication, final ChaiUser chaiUser )
        {
            this.pwmApplication = pwmApplication;
            this.chaiUser = chaiUser;
            this.userIdentity = null;
        }

        public ActionExecutorSettings( final PwmApplication pwmApplication, final UserIdentity userIdentity )
        {
            this.pwmApplication = pwmApplication;
            this.userIdentity = userIdentity;
            this.chaiUser = null;
        }

        private boolean isExpandPwmMacros( )
        {
            return expandPwmMacros;
        }

        private ChaiUser getChaiUser( )
        {
            return chaiUser;
        }

        private MacroMachine getMacroMachine( )
        {
            return macroMachine;
        }

        private UserIdentity getUserIdentity( )
        {
            return userIdentity;
        }

        public ActionExecutorSettings setExpandPwmMacros( final boolean expandPwmMacros )
        {
            this.expandPwmMacros = expandPwmMacros;
            return this;
        }


        public ActionExecutorSettings setMacroMachine( final MacroMachine macroMachine )
        {
            this.macroMachine = macroMachine;
            return this;
        }

        public ActionExecutor createActionExecutor( )
        {
            return new ActionExecutor( this.pwmApplication, this );
        }
    }


}
