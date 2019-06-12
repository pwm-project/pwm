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

package password.pwm.ws.server;

import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.servlet.PwmRequestID;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;

public class RestRequest extends PwmHttpRequestWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestRequest.class );

    private final PwmApplication pwmApplication;
    private final RestAuthentication restAuthentication;
    private final SessionLabel sessionLabel;
    private final PwmRequestID requestID;

    public static RestRequest forRequest(
            final PwmApplication pwmApplication,
            final RestAuthentication restAuthentication,
            final SessionLabel sessionLabel,
            final HttpServletRequest httpServletRequest
    )
            throws PwmUnrecoverableException
    {
        return new RestRequest( pwmApplication, restAuthentication, sessionLabel, httpServletRequest );
    }

    private RestRequest(
            final PwmApplication pwmApplication,
            final RestAuthentication restAuthentication,
            final SessionLabel sessionLabel,
            final HttpServletRequest httpServletRequest
    )
    {
        super( httpServletRequest, pwmApplication.getConfig() );
        this.pwmApplication = pwmApplication;
        this.restAuthentication = restAuthentication;
        this.sessionLabel = sessionLabel;
        this.requestID = PwmRequestID.next();
    }

    public RestAuthentication getRestAuthentication( )
    {
        return restAuthentication;
    }

    public PwmApplication getPwmApplication( )
    {
        return pwmApplication;
    }

    public HttpContentType readContentType( )
    {
        return HttpContentType.fromContentTypeHeader( readHeaderValueAsString( HttpHeader.ContentType ), null );
    }

    public HttpContentType readAcceptType( )
    {

        return readAcceptType( getHttpServletRequest() );
    }

    static HttpContentType readAcceptType( final HttpServletRequest request )
    {
        final String acceptHeaderValue = request.getHeader( HttpHeader.Accept.getHttpName() );
        return HttpContentType.fromContentTypeHeader( acceptHeaderValue, HttpContentType.json );
    }

    public Locale getLocale( )
    {
        final List<Locale> knownLocales = getConfig().getKnownLocales();
        return LocaleHelper.localeResolver( getHttpServletRequest().getLocale(), knownLocales );
    }

    public SessionLabel getSessionLabel( )
    {
        return sessionLabel;
    }

    public ChaiProvider getChaiProvider( final String ldapProfileID )
            throws PwmUnrecoverableException
    {
        if ( getRestAuthentication().getType() == RestAuthenticationType.LDAP )
        {
            if ( !getRestAuthentication().getLdapIdentity().getLdapProfileID().equals( ldapProfileID ) )
            {
                final String errorMsg = "target user ldap profileID does not match authenticated user ldap profileID";
                throw PwmUnrecoverableException.newException( PwmError.ERROR_REST_INVOCATION_ERROR, errorMsg );
            }
            return getRestAuthentication().getChaiProvider();
        }
        return getPwmApplication().getProxyChaiProvider( ldapProfileID );
    }

    public CommonValues commonValues()
    {
        return new CommonValues( pwmApplication, this.getSessionLabel(), this.getLocale(), requestID );
    }
}

