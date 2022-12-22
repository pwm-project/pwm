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

package password.pwm.http;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.PageContext;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class JspUtility
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( JspUtility.class );

    private static Optional<PwmRequest> forRequest(
            final ServletRequest request
    )
    {
        return Optional.of( ( PwmRequest ) request.getAttribute( PwmRequestAttribute.PwmRequest.toString() ) );
    }

    private static PwmUnrecoverableException makeMissingRequestException()
    {
        final String msg = "unable to load pwmRequest object during jsp execution";
        return PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, msg );
    }

    public static <E extends PwmSessionBean> E getSessionBean( final PageContext pageContext, final Class<E> theClass )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() )
                .orElseThrow( JspUtility::makeMissingRequestException );
        return pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, theClass );
    }

    public static Object getAttribute( final PageContext pageContext, final PwmRequestAttribute requestAttr )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() )
                .orElseThrow( JspUtility::makeMissingRequestException );
        return pwmRequest.getAttribute( requestAttr );
    }

    public static boolean getBooleanAttribute( final PageContext pageContext, final PwmRequestAttribute requestAttr )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() )
                .orElseThrow( JspUtility::makeMissingRequestException );
        final Object value = pwmRequest.getAttribute( requestAttr );
        return value != null && Boolean.parseBoolean( value.toString() );
    }

    public static void setFlag( final PageContext pageContext, final PwmRequestFlag flag )
    {
        setFlag( pageContext, flag, true );
    }

    public static void setFlag( final PageContext pageContext, final PwmRequestFlag flag, final boolean value )
    {
        forRequest( pageContext.getRequest() ).ifPresent( pwmRequest ->
        {
            pwmRequest.setFlag( flag, value );
        } );
    }

    public static boolean isFlag( final HttpServletRequest request, final PwmRequestFlag flag )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = forRequest( request )
                .orElseThrow( JspUtility::makeMissingRequestException );
        return pwmRequest.isFlag( flag );
    }

    public static Locale locale( final HttpServletRequest request )
    {
        return forRequest( request ).map( PwmRequest::getLocale ).orElse( PwmConstants.DEFAULT_LOCALE );
    }

    public static long numberSetting( final HttpServletRequest request, final PwmSetting pwmSetting, final long defaultValue )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = forRequest( request )
                .orElseThrow( JspUtility::makeMissingRequestException );
        if ( pwmRequest != null )
        {
            try
            {
                return pwmRequest.getDomainConfig().readSettingAsLong( pwmSetting );
            }
            catch ( final Exception e )
            {
                LOGGER.warn( pwmRequest, () -> "error reading number setting " + pwmSetting.getKey() + ", error: " + e.getMessage() );
            }
        }
        return defaultValue;
    }

    public static void logError( final PageContext pageContext, final String message )
    {
        forRequest( pageContext.getRequest() ).ifPresent( pwmRequest ->
        {
            final PwmLogger logger = PwmLogger.getLogger( "jsp:" + pageContext.getPage().getClass() );
            logger.error( pwmRequest, () -> message );
        } );
    }

    public static String getMessage( final PageContext pageContext, final PwmDisplayBundle key )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() )
                .orElseThrow( JspUtility::makeMissingRequestException );
        return LocaleHelper.getLocalizedMessage( key, pwmRequest );
    }

    public static PwmSession getPwmSession( final PageContext pageContext )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() )
                .orElseThrow( JspUtility::makeMissingRequestException );
        return pwmRequest.getPwmSession();
    }

    public static PwmRequest getPwmRequest( final PageContext pageContext )
            throws PwmUnrecoverableException
    {
        return forRequest( pageContext.getRequest() )
                .orElseThrow( JspUtility::makeMissingRequestException );
    }

    public static String friendlyWrite( final PageContext pageContext, final boolean value )
    {
        return forRequest( pageContext.getRequest() )
                .map( pwmRequest -> LocaleHelper.valueBoolean( pwmRequest.getLocale(), value ) )
                .orElse( "" );
    }

    public static String friendlyWrite( final PageContext pageContext, final long value )
    {
        return forRequest( pageContext.getRequest() )
                .map( pwmRequest -> NumberFormat.getInstance( pwmRequest.getLocale() ).format( value ) )
                .orElse( "" );
    }

    public static String friendlyWrite( final PageContext pageContext, final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return friendlyWriteNotApplicable( pageContext );
        }
        return StringUtil.escapeHtml( input );
    }

    public static String friendlyWrite( final PageContext pageContext, final Supplier<String> input )
    {
        try
        {
            final String str = input.get();
            if ( StringUtil.isEmpty( str ) )
            {
                return friendlyWriteNotApplicable( pageContext );
            }
            return StringUtil.escapeHtml( str );
        }
        catch ( final Exception e )
        {
            LOGGER.debug( () -> "error while performing JSP write: " + e.getMessage() );
        }

        return "";
    }


    public static String friendlyWriteNotApplicable( final PageContext pageContext )
    {
        return forRequest( pageContext.getRequest() )
                .map( pwmRequest -> LocaleHelper.valueNotApplicable( pwmRequest.getLocale() ) )
                .orElse( "" );
    }

    public static String friendlyWrite( final PageContext pageContext, final Instant instant )
    {
        return forRequest( pageContext.getRequest() )
                .map( pwmRequest -> "<span class=\"timestamp\">" + instant + "</span>" )
                .orElse( "" );
    }

    public static String localizedString( final PageContext pageContext, final String key, final Class<? extends PwmDisplayBundle> bundleClass, final String... values )
    {
        return forRequest( pageContext.getRequest() )
                .map( pwmRequest -> LocaleHelper.getLocalizedMessage( pwmRequest.getLocale(), key, pwmRequest.getDomainConfig(), bundleClass, values ) )
                .orElse( "" );
    }
}



