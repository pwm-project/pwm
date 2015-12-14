/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.tag;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Admin;
import password.pwm.util.LocaleHelper;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspPage;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public class PwmValueTag extends TagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmValueTag.class);

    private VALUE name;

    public VALUE getName()
    {
        return name;
    }

    public void setName(final VALUE name)
    {
        this.name = name;
    }

    public int doEndTag()
            throws JspTagException
    {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmRequest pwmRequest = PwmRequest.forRequest(req, (HttpServletResponse) pageContext.getResponse());
            try {
               // final VALUE value = Helper.readEnumFromString(VALUE.class, null, getName());
                final VALUE value = getName();
                final String output = calcValue(pwmRequest,pageContext,value);
                pageContext.getOut().write(output);

            } catch (IllegalArgumentException e) {
                LOGGER.error("can't output requested value name '" + getName() + "'");
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error while processing PwmScriptTag: " + e.getMessage());
        } catch (Exception e) {
            throw new JspTagException(e.getMessage(),e);
        }
        return EVAL_PAGE;
    }

    public String calcValue(
            final PwmRequest pwmRequest,
            final PageContext pageContext,
            final VALUE value
    ) {

        if (value != null) {
            try {
                return value.getValueOutput().valueOutput(pwmRequest, pageContext);
            } catch (Exception e) {
                LOGGER.error("error executing value tag option '" + value.toString() + "', error: " + e.getMessage());
            }
        }

        return "";
    }


    interface ValueOutput {
        String valueOutput(
                final PwmRequest pwmRequest,
                final PageContext pageContext)
                throws ChaiUnavailableException, PwmUnrecoverableException;
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
            return StringUtil.escapeHtml(outputURL);
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
            } else if (pwmRequest.getPwmApplication().getApplicationMode() == PwmApplication.MODE.CONFIGURATION) {
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


    public static enum VALUE {
        cspNonce(new CspNonceOutput()),
        homeURL(new HomeUrlOutput()),
        passwordFieldType(new PasswordFieldTypeOutput()),
        responseFieldType(new ResponseFieldTypeOutput()),
        customJavascript(new CustomJavascriptOutput()),
        currentJspFilename(new CurrentJspFilenameOutput()),
        instanceID(new InstanceIDOutput()),
        headerMenuNotice(new HeaderMenuNoticeOutput()),

        ;

        private ValueOutput valueOutput;

        VALUE(ValueOutput valueOutput)
        {
            this.valueOutput = valueOutput;
        }

        public ValueOutput getValueOutput() {
            return valueOutput;
        }
    }
}

