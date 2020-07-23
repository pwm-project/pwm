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

package password.pwm.http;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;
import java.io.Serializable;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;

public abstract class JspUtility
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( JspUtility.class );

    private static PwmRequest forRequest(
            final ServletRequest request
    )
    {
        final PwmRequest pwmRequest = ( PwmRequest ) request.getAttribute( PwmRequestAttribute.PwmRequest.toString() );
        if ( pwmRequest == null )
        {
            LOGGER.warn( () -> "unable to load pwmRequest object during jsp execution" );
        }
        return pwmRequest;
    }

    public static <E extends PwmSessionBean> E getSessionBean( final PageContext pageContext, final Class<E> theClass )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        try
        {
            return pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, theClass );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( () -> "unable to load pwmRequest object during jsp execution: " + e.getMessage() );
        }
        return null;
    }

    public static Serializable getAttribute( final PageContext pageContext, final PwmRequestAttribute requestAttr )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        return pwmRequest.getAttribute( requestAttr );
    }

    public static boolean getBooleanAttribute( final PageContext pageContext, final PwmRequestAttribute requestAttr )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        final Object value = pwmRequest.getAttribute( requestAttr );
        return value != null && Boolean.parseBoolean( value.toString() );
    }

    public static void setFlag( final PageContext pageContext, final PwmRequestFlag flag )
    {
        setFlag( pageContext, flag, true );
    }

    public static void setFlag( final PageContext pageContext, final PwmRequestFlag flag, final boolean value )
    {
        final PwmRequest pwmRequest;
        try
        {
            pwmRequest = PwmRequest.forRequest(
                    ( HttpServletRequest ) pageContext.getRequest(),
                    ( HttpServletResponse ) pageContext.getResponse()
            );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( () -> "unable to load pwmRequest object during jsp execution: " + e.getMessage() );
            return;
        }
        if ( pwmRequest != null )
        {
            pwmRequest.setFlag( flag, value );
        }
    }

    public static boolean isFlag( final HttpServletRequest request, final PwmRequestFlag flag )
    {
        final PwmRequest pwmRequest = forRequest( request );
        return pwmRequest != null && pwmRequest.isFlag( flag );
    }

    public static Locale locale( final HttpServletRequest request )
    {
        final PwmRequest pwmRequest = forRequest( request );
        if ( pwmRequest != null )
        {
            return pwmRequest.getLocale();
        }
        return PwmConstants.DEFAULT_LOCALE;
    }

    public static long numberSetting( final HttpServletRequest request, final PwmSetting pwmSetting, final long defaultValue )
    {
        final PwmRequest pwmRequest = forRequest( request );
        if ( pwmRequest != null )
        {
            try
            {
                return pwmRequest.getConfig().readSettingAsLong( pwmSetting );
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
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        final PwmLogger logger = PwmLogger.getLogger( "jsp:" + pageContext.getPage().getClass() );
        logger.error( pwmRequest, () -> message );
    }

    public static String getMessage( final PageContext pageContext, final PwmDisplayBundle key )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        return LocaleHelper.getLocalizedMessage( key, pwmRequest );
    }

    public static PwmSession getPwmSession( final PageContext pageContext )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        return pwmRequest.getPwmSession();
    }

    public static PwmRequest getPwmRequest( final PageContext pageContext )
    {
        return forRequest( pageContext.getRequest() );
    }

    public static String friendlyWrite( final PageContext pageContext, final boolean value )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        return LocaleHelper.valueBoolean( pwmRequest.getLocale(), value );
    }

    public static String friendlyWrite( final PageContext pageContext, final long value )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        final NumberFormat numberFormat = NumberFormat.getInstance( pwmRequest.getLocale() );
        return numberFormat.format( value );
    }

    public static String friendlyWrite( final PageContext pageContext, final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return friendlyWriteNotApplicable( pageContext );
        }
        return StringUtil.escapeHtml( input );
    }

    public static String friendlyWriteNotApplicable( final PageContext pageContext )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        return LocaleHelper.valueNotApplicable( pwmRequest.getLocale() );
    }

    public static String friendlyWrite( final PageContext pageContext, final Instant instant )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        if ( instant == null )
        {
            return LocaleHelper.valueNotApplicable( pwmRequest.getLocale() );
        }
        return "<span class=\"timestamp\">" + instant.toString() + "</span>";
    }

    public static String localizedString( final PageContext pageContext, final String key, final Class<? extends PwmDisplayBundle> bundleClass, final String... values )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        return LocaleHelper.getLocalizedMessage( pwmRequest.getLocale(), key, pwmRequest.getConfig(), bundleClass, values );
    }
}



