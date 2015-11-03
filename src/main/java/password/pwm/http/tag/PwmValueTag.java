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

import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspPage;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public class PwmValueTag extends TagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmValueTag.class);

    private String name;

    public String getName()
    {
        return name;
    }

    public void setName(final String name)
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
                final VALUE value = VALUE.valueOf(getName());
                final String output = calcValue(pwmRequest,value);
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
            final VALUE value
    ) {
        if (value == null) {
            return "";
        }

        switch (value) {
            case cspNonce:
                return pwmRequest.getCspNonce();

            case homeURL: {
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

            case passwordFieldType: {
                final boolean maskPasswordFields = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_MASK_PASSWORD_FIELDS);
                return maskPasswordFields ? "password" : "text";
            }

            case responseFieldType: {
                final boolean maskResponseFields = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_MASK_RESPONSE_FIELDS);
                return maskResponseFields ? "password" : "text";
            }

            case customJavascript: {
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

            case currentJspFilename: {
                final JspPage jspPage = (JspPage)pageContext.getPage();
                if (jspPage != null) {
                    String name = jspPage.getClass().getSimpleName();
                    name = name.replaceAll("_002d", "-");
                    name = name.replaceAll("_", ".");
                    return name;
                }
                return "";
            }

            case instanceID: {
                return pwmRequest.getPwmApplication().getInstanceID();
            }
        }

        return "";
    }

    enum VALUE {
        cspNonce,
        homeURL,
        passwordFieldType,
        responseFieldType,
        customJavascript,
        currentJspFilename,
        instanceID,
    }
}

