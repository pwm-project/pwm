/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.http.tag.value;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.IdleTimeoutCalculator;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Admin;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.ws.server.rest.RestAppDataServer;

import javax.servlet.jsp.JspPage;
import javax.servlet.jsp.PageContext;
import java.awt.*;
import java.util.Locale;

public enum PwmValue {

    cspNonce(new CspNonceOutput()),
    homeURL(new HomeUrlOutput()),
    passwordFieldType(new PasswordFieldTypeOutput()),
    responseFieldType(new ResponseFieldTypeOutput()),
    customJavascript(new CustomJavascriptOutput()),
    currentJspFilename(new CurrentJspFilenameOutput()),
    instanceID(new InstanceIDOutput()),
    headerMenuNotice(new HeaderMenuNoticeOutput()),
    clientETag(new ClientETag()),
    restClientKey(new RestClientKey()),
    localeCode(new LocaleCodeOutput()),
    localeDir(new LocaleDirOutput()),
    localeFlagFile(new LocaleFlagFileOutput()),
    localeName(new LocaleNameOutput()),
    inactiveTimeRemaining(new InactiveTimeRemainingOutput()),

    ;

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmValueTag.class);

    private ValueOutput valueOutput;

    PwmValue(ValueOutput valueOutput)
    {
        this.valueOutput = valueOutput;
    }

    public ValueOutput getValueOutput() {
        return valueOutput;
    }


    static class CspNonceOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            return pwmRequest.getCspNonce();
        }
    }

    static class HomeUrlOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            String outputURL = pwmRequest.getConfig().readSettingAsString(PwmSetting.URL_HOME);
            if (outputURL == null || outputURL.isEmpty()) {
                outputURL = pwmRequest.getHttpServletRequest().getContextPath();
            } else {
                try {
                    MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(
                            pwmRequest.getPwmApplication());
                    outputURL = macroMachine.expandMacros(outputURL);
                } catch ( PwmUnrecoverableException e) {
                    LOGGER.error(pwmRequest, "error expanding macros in homeURL: " + e.getMessage());
                }
            }
            return outputURL;
        }
    }

    static class PasswordFieldTypeOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            final boolean maskPasswordFields = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_MASK_PASSWORD_FIELDS);
            return maskPasswordFields ? "password" : "text";
        }
    }

    static class ResponseFieldTypeOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            final boolean maskPasswordFields = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_MASK_PASSWORD_FIELDS);
            return maskPasswordFields ? "password" : "text";
        }
    }

    static class CustomJavascriptOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            final String customScript = pwmRequest.getConfig().readSettingAsString(
                    PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT);
            if (customScript != null && !customScript.isEmpty()) {
                try {
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(
                            pwmRequest.getPwmApplication());
                    final String expandedScript = macroMachine.expandMacros(customScript);
                    return expandedScript;
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "error while expanding customJavascript macros: " + e.getMessage());
                    return customScript;
                }
            }
            return "";
        }
    }

    static class CurrentJspFilenameOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            final JspPage jspPage = (JspPage) pageContext.getPage();
            if (jspPage != null) {
                String name = jspPage.getClass().getSimpleName();
                name = name.replaceAll("_002d", "-");
                name = name.replaceAll("_", ".");
                return name;
            }
            return "";
        }
    }

    static class InstanceIDOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            return pwmRequest.getPwmApplication().getInstanceID();

        }
    }

    static class HeaderMenuNoticeOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            if (PwmConstants.TRIAL_MODE) {
                return LocaleHelper.getLocalizedMessage(pwmRequest.getLocale(), "Header_TrialMode", pwmRequest.getConfig(), Admin.class, new String[]{PwmConstants.PWM_APP_NAME});
            } else if (pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.CONFIGURATION) {
                String output = "";
                if (Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.CLIENT_JSP_SHOW_ICONS))) {
                    output += "<span id=\"icon-configModeHelp\" class=\"btn-icon pwm-icon pwm-icon-question-circle\"></span>";
                }
                output +=  LocaleHelper.getLocalizedMessage(pwmRequest.getLocale(), "Header_ConfigModeActive", pwmRequest.getConfig(), Admin.class, new String[]{PwmConstants.PWM_APP_NAME});
                return  output;
            } else if (pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN)) {
                return LocaleHelper.getLocalizedMessage(pwmRequest.getLocale(), "Header_AdminUser", pwmRequest.getConfig(), Admin.class, new String[]{PwmConstants.PWM_APP_NAME});

            }

            return "";
        }
    }

    static class ClientETag implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            return RestAppDataServer.makeClientEtag(pwmRequest);
        }
    }

    static class RestClientKey implements ValueOutput {
        @Override
        public String valueOutput(final PwmRequest pwmRequest, final PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            return pwmRequest.getPwmSession().getRestClientKey();
        }
    }

    static class LocaleCodeOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            return pwmRequest.getLocale().toLanguageTag();
        }
    }

    static class LocaleDirOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            final Locale locale = pwmRequest.getLocale();
            final ComponentOrientation orient = ComponentOrientation.getOrientation(locale);
            return orient != null && !orient.isLeftToRight() ? "rtl" : "ltr";
        }
    }

    static class LocaleNameOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            final Locale locale = pwmRequest.getLocale();
            return locale.getDisplayName(locale);
        }
    }

    static class LocaleFlagFileOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            final String flagFileName = pwmRequest.getConfig().getKnownLocaleFlagMap().get(pwmRequest.getLocale());
            return flagFileName == null ? "" : flagFileName;
        }
    }

    static class InactiveTimeRemainingOutput implements ValueOutput {
        @Override
        public String valueOutput(PwmRequest pwmRequest, PageContext pageContext) throws ChaiUnavailableException, PwmUnrecoverableException {
            return IdleTimeoutCalculator.idleTimeoutForRequest(pwmRequest).asLongString();
        }
    }
}
