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

package password.pwm.http;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.LocaleHelper;
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
            LOGGER.warn( "unable to load pwmRequest object during jsp execution" );
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
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.warn( "unable to load pwmRequest object during jsp execution: " + e.getMessage() );
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
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.warn( "unable to load pwmRequest object during jsp execution: " + e.getMessage() );
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
            catch ( Exception e )
            {
                LOGGER.warn( pwmRequest, "error reading number setting " + pwmSetting.getKey() + ", error: " + e.getMessage() );
            }
        }
        return defaultValue;
    }

    public static void logError( final PageContext pageContext, final String message )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        final PwmLogger logger = PwmLogger.getLogger( "jsp:" + pageContext.getPage().getClass() );
        logger.error( pwmRequest, message );
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
        return value
                ? LocaleHelper.getLocalizedMessage( Display.Value_True, pwmRequest )
                : LocaleHelper.getLocalizedMessage( Display.Value_False, pwmRequest );
    }

    public static String friendlyWrite( final PageContext pageContext, final long value )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        final NumberFormat numberFormat = NumberFormat.getInstance( pwmRequest.getLocale() );
        return numberFormat.format( value );
    }

    public static String friendlyWrite( final PageContext pageContext, final String input )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        if ( StringUtil.isEmpty( input ) )
        {
            return LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest );
        }
        return StringUtil.escapeHtml( input );
    }

    public static String friendlyWrite( final PageContext pageContext, final Instant instant )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        if ( instant == null )
        {
            return LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest );
        }
        return "<span class=\"timestamp\">" + instant.toString() + "</span>";
    }

    public static String localizedString( final PageContext pageContext, final String key, final Class<? extends PwmDisplayBundle> bundleClass, final String... values )
    {
        final PwmRequest pwmRequest = forRequest( pageContext.getRequest() );
        return LocaleHelper.getLocalizedMessage( pwmRequest.getLocale(), key, pwmRequest.getConfig(), bundleClass, values );
    }
}



