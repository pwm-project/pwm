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

package password.pwm.http.tag;

import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * @author Jason D. Rivard
 */
public class DisplayTag extends PwmAbstractTag
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DisplayTag.class );

    private String key;
    private String value1;
    private String value2;
    private String value3;
    private boolean displayIfMissing;
    private String bundle;

    public String getKey( )
    {
        return key;
    }

    public void setKey( final String key )
    {
        this.key = key;
    }

    public String getValue1( )
    {
        return value1;
    }

    public void setValue1( final String value1 )
    {
        this.value1 = value1;
    }

    public String getValue2( )
    {
        return value2;
    }

    public void setValue2( final String value1 )
    {
        this.value2 = value1;
    }

    public String getValue3( )
    {
        return value3;
    }

    public void setValue3( final String value3 )
    {
        this.value3 = value3;
    }

    public boolean isDisplayIfMissing( )
    {
        return displayIfMissing;
    }

    public void setDisplayIfMissing( final boolean displayIfMissing )
    {
        this.displayIfMissing = displayIfMissing;
    }

    public String getBundle( )
    {
        return bundle;
    }

    public void setBundle( final String bundle )
    {
        this.bundle = bundle;
    }

    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        try
        {
            PwmRequest pwmRequest = null;
            try
            {
                pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
            }
            catch ( final PwmException e )
            {
                /* noop */
            }

            final Locale locale = pwmRequest == null ? PwmConstants.DEFAULT_LOCALE : pwmRequest.getLocale();

            final Class bundle = readBundle();
            String displayMessage = figureDisplayMessage( locale, pwmRequest == null ? null : pwmRequest.getConfig(), bundle );

            if ( pwmRequest != null )
            {
                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
                displayMessage = macroMachine.expandMacros( displayMessage );
            }

            pageContext.getOut().write( displayMessage );
        }
        catch ( final PwmUnrecoverableException e )
        {
            {
                LOGGER.debug( () -> "error while executing jsp display tag: " + e.getMessage() );
                return EVAL_PAGE;
            }
        }
        catch ( final Exception e )
        {
            LOGGER.debug( () -> "error while executing jsp display tag: " + e.getMessage(), e );
            throw new JspTagException( e.getMessage(), e );
        }
        return EVAL_PAGE;
    }

    private Class readBundle( )
    {
        if ( bundle == null || bundle.length() < 1 )
        {
            return Display.class;
        }

        try
        {
            return Class.forName( bundle );
        }
        catch ( final ClassNotFoundException e )
        {
            /* no op */
        }

        try
        {
            return Class.forName( Display.class.getPackage().getName() + "." + bundle );
        }
        catch ( final ClassNotFoundException e )
        {
            /* no op */
        }

        return Display.class;
    }

    private String figureDisplayMessage( final Locale locale, final Configuration config, final Class bundleClass )
    {
        try
        {
            return LocaleHelper.getLocalizedMessage(
                    locale == null ? PwmConstants.DEFAULT_LOCALE : locale,
                    key,
                    config,
                    bundleClass,
                    new String[]
                            {
                                    value1,
                                    value2,
                                    value3,
                            }
            );
        }
        catch ( final MissingResourceException e )
        {
            if ( !displayIfMissing )
            {
                LOGGER.info( () -> "error while executing jsp display tag: " + e.getMessage() );
            }
        }

        return displayIfMissing ? key : "";
    }
}

