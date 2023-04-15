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

package password.pwm.http.tag.url;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.http.servlet.resource.ResourceFileServlet;
import password.pwm.util.java.StringUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.TagSupport;

public class PwmUrlTag extends TagSupport
{

    private String url;
    private boolean addContext;

    private static final String RESOURCE_URL = "/resources";

    public void setUrl( final String url )
    {
        this.url = url;
    }

    public void setAddContext( final boolean addContext )
    {
        this.addContext = addContext;
    }

    @Override
    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        final String url = convertUrl( this.url );

        String outputURL = url;
        PwmRequest pwmRequest = null;
        try
        {
            pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
        }
        catch ( final PwmException e )
        {
            /* noop */
        }

        String workingUrl = url;

        for ( final PwmThemeURL themeUrl : PwmThemeURL.values() )
        {
            if ( themeUrl.token().equals( url ) )
            {
                workingUrl = figureThemeURL( pwmRequest, themeUrl );
                workingUrl = insertContext( pageContext, workingUrl );
            }
        }

        if ( addContext )
        {
            workingUrl = insertContext( pageContext, workingUrl );
        }
        if ( pwmRequest != null )
        {
            workingUrl = insertResourceNonce( pwmRequest.getPwmDomain(), workingUrl );
        }

        outputURL = workingUrl;

        try
        {
            pageContext.getOut().write( outputURL );
        }
        catch ( final Exception e )
        {
            throw new JspTagException( e.getMessage() );
        }

        return EVAL_PAGE;
    }


    public static String insertContext( final PageContext pageContext, final String urlString )
    {
        if ( !urlString.startsWith( "/" ) )
        {
            return urlString;
        }

        if (
                urlString.toLowerCase().startsWith( "http://" )
                        || urlString.toLowerCase().startsWith( "https://" )
                        || urlString.startsWith( "//" )
                )
        {
            return urlString;
        }

        PwmRequest pwmRequest = null;
        try
        {
            pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
        }
        catch ( final PwmException e )
        {
            /* noop */
        }

        final String contextPath = pwmRequest == null
                ? pageContext.getServletContext().getContextPath()
                : pwmRequest.getBasePath();

        if ( urlString.startsWith( contextPath ) )
        {
            return urlString;
        }
        
        return contextPath + urlString;
    }

    public static String insertResourceNonce( final PwmDomain pwmDomain, final String urlString )
    {
        if ( pwmDomain != null && urlString.contains( RESOURCE_URL ) )
        {
            final String nonce = pwmDomain.getResourceServletService().getResourceNonce();
            if ( nonce != null && nonce.length() > 0 )
            {
                return urlString.replaceFirst( RESOURCE_URL, RESOURCE_URL + nonce );
            }

        }
        return urlString;
    }

    private static String figureThemeName(
            final PwmRequest pwmRequest
    )
    {
        if ( pwmRequest.isFlag( PwmRequestFlag.INCLUDE_CONFIG_CSS ) )
        {
            return pwmRequest.getDomainConfig().readAppProperty( AppProperty.CONFIG_THEME );
        }

        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        if ( ssBean.getTheme() != null )
        {
            return ssBean.getTheme();
        }

        if ( pwmRequest.getDomainConfig() != null )
        {
            return pwmRequest.getDomainConfig().readSettingAsString( PwmSetting.INTERFACE_THEME );
        }
        else
        {
            return "default";
        }
    }

    private static String figureThemeURL(
            final PwmRequest pwmRequest,
            final PwmThemeURL themeUrl
    )
    {
        String themeURL = null;
        String themeName = AppProperty.CONFIG_THEME.getDefaultValue();

        if ( pwmRequest != null )
        {
            final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

            themeName = figureThemeName( pwmRequest );

            if ( "custom".equals( themeName ) )
            {
                if ( themeUrl == PwmThemeURL.MOBILE_THEME_URL )
                {
                    themeURL = pwmDomain.getConfig().readSettingAsString( PwmSetting.DISPLAY_CSS_CUSTOM_MOBILE_STYLE );
                }
                else
                {
                    themeURL = pwmDomain.getConfig().readSettingAsString( PwmSetting.DISPLAY_CSS_CUSTOM_STYLE );
                }
            }
        }

        if ( themeURL == null || themeURL.length() < 1 )
        {
            themeURL = ResourceFileServlet.RESOURCE_PATH + themeUrl.getCssName();
            themeURL = themeURL.replace( ResourceFileServlet.TOKEN_THEME, StringUtil.escapeHtml( themeName ) );
        }

        return themeURL;
    }

    public static String convertUrl( final String input )
    {
        final String pwmClientUrl = "/resources/webjars/pwm-client/";
        if ( input.contains( pwmClientUrl ) )
        {
            final String correctedUrl = "/resources/webjars/" + PwmConstants.PWM_APP_NAME.toLowerCase() + "-client/";
            return input.replace( pwmClientUrl, correctedUrl );
        }
        return input;
    }
}
