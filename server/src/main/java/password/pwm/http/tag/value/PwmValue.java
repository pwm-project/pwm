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

package password.pwm.http.tag.value;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.IdleTimeoutCalculator;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.ClientApiServlet;
import password.pwm.i18n.Admin;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.jsp.JspPage;
import javax.servlet.jsp.PageContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public enum PwmValue
{

    cspNonce( new CspNonceOutput() ),
    homeURL( new HomeUrlOutput() ),
    passwordFieldType( new PasswordFieldTypeOutput() ),
    responseFieldType( new ResponseFieldTypeOutput() ),
    customJavascript( new CustomJavascriptOutput(), Flag.DoNotEscape ),
    currentJspFilename( new CurrentJspFilenameOutput() ),
    instanceID( new InstanceIDOutput() ),
    headerMenuNotice( new HeaderMenuNoticeOutput() ),
    clientETag( new ClientETag() ),
    localeCode( new LocaleCodeOutput() ),
    localeDir( new LocaleDirOutput() ),
    localeFlagFile( new LocaleFlagFileOutput() ),
    localeName( new LocaleNameOutput() ),
    inactiveTimeRemaining( new InactiveTimeRemainingOutput() ),
    sessionID( new SessionIDValue() ),;

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmValueTag.class );

    private final ValueOutput valueOutput;
    private final Flag[] flags;

    enum Flag
    {
        DoNotEscape,
    }

    PwmValue( final ValueOutput valueOutput, final Flag... flags )
    {
        this.valueOutput = valueOutput;
        this.flags = flags;
    }

    public ValueOutput getValueOutput( )
    {
        return valueOutput;
    }

    public Set<Flag> getFlags( )
    {
        return flags == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet( new HashSet<>( Arrays.asList( flags ) ) );
    }

    static class CspNonceOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getCspNonce();
        }
    }

    static class HomeUrlOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            String outputURL = pwmRequest.getConfig().readSettingAsString( PwmSetting.URL_HOME );
            if ( outputURL == null || outputURL.isEmpty() )
            {
                outputURL = pwmRequest.getHttpServletRequest().getContextPath();
            }
            else
            {
                try
                {
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine();
                    outputURL = macroMachine.expandMacros( outputURL );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.error( pwmRequest, () -> "error expanding macros in homeURL: " + e.getMessage() );
                }
            }
            return outputURL;
        }
    }

    static class PasswordFieldTypeOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final boolean maskPasswordFields = pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_MASK_PASSWORD_FIELDS );
            return maskPasswordFields ? "password" : "text";
        }
    }

    static class ResponseFieldTypeOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final boolean maskPasswordFields = pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_MASK_RESPONSE_FIELDS );
            return maskPasswordFields ? "password" : "text";
        }
    }

    static class CustomJavascriptOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final String customScript = pwmRequest.getConfig().readSettingAsString(
                    PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT );
            if ( customScript != null && !customScript.isEmpty() )
            {
                try
                {
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine();
                    final String expandedScript = macroMachine.expandMacros( customScript );
                    return expandedScript;
                }
                catch ( final Exception e )
                {
                    LOGGER.error( pwmRequest, () -> "error while expanding customJavascript macros: " + e.getMessage() );
                    return customScript;
                }
            }
            return "";
        }
    }

    static class CurrentJspFilenameOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final JspPage jspPage = ( JspPage ) pageContext.getPage();
            if ( jspPage != null )
            {
                String name = jspPage.getClass().getSimpleName();
                name = name.replaceAll( "_002d", "-" );
                name = name.replaceAll( "_", "." );
                return name;
            }
            return "";
        }
    }

    static class InstanceIDOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().getInstanceID();

        }
    }

    static class HeaderMenuNoticeOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final String[] fieldNames = new String[]
                    {
                            PwmConstants.PWM_APP_NAME,
                    };

            if ( PwmConstants.TRIAL_MODE )
            {
                return LocaleHelper.getLocalizedMessage( pwmRequest.getLocale(), "Header_TrialMode", pwmRequest.getConfig(), Admin.class, fieldNames );
            }
            else if ( pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.CONFIGURATION )
            {
                String output = "";
                if ( Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.CLIENT_JSP_SHOW_ICONS ) ) )
                {
                    output += "<span id=\"icon-configModeHelp\" class=\"btn-icon pwm-icon pwm-icon-question-circle\"></span>";
                }
                output += LocaleHelper.getLocalizedMessage( pwmRequest.getLocale(), "Header_ConfigModeActive", pwmRequest.getConfig(), Admin.class, fieldNames );
                return output;
            }
            else if ( pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN ) )
            {
                return LocaleHelper.getLocalizedMessage( pwmRequest.getLocale(), "Header_AdminUser", pwmRequest.getConfig(), Admin.class, fieldNames );

            }

            return "";
        }
    }

    static class ClientETag implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext )
                throws PwmUnrecoverableException
        {
            return ClientApiServlet.makeClientEtag( pwmRequest );
        }
    }

    static class LocaleCodeOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext )
        {
            return pwmRequest.getLocale().toLanguageTag();
        }
    }

    static class LocaleDirOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext )
        {
            final Locale locale = pwmRequest.getLocale();
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            final LocaleHelper.TextDirection textDirection = LocaleHelper.textDirectionForLocale( pwmApplication, locale );
            return textDirection.name();
        }
    }

    static class LocaleNameOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext )
        {
            final Locale locale = pwmRequest.getLocale();
            return locale.getDisplayName( locale );
        }
    }

    static class LocaleFlagFileOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext )
        {
            final String flagFileName = pwmRequest.getConfig().getKnownLocaleFlagMap().get( pwmRequest.getLocale() );
            return flagFileName == null ? "" : flagFileName;
        }
    }

    static class InactiveTimeRemainingOutput implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext )
                throws PwmUnrecoverableException
        {
            return IdleTimeoutCalculator.idleTimeoutForRequest( pwmRequest ).asLongString();
        }
    }

    static class SessionIDValue implements ValueOutput
    {
        @Override
        public String valueOutput( final PwmRequest pwmRequest, final PageContext pageContext )
                throws PwmUnrecoverableException
        {
            return pwmRequest.getPwmSession().getSessionStateBean().getSessionID();
        }
    }
}
