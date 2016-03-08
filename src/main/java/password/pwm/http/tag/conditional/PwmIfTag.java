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

package password.pwm.http.tag.conditional;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.http.PwmSession;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;

public class PwmIfTag extends BodyTagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmIfTag.class);

    private PwmIfTest test;
    private Permission permission;
    private boolean negate;
    private PwmRequestFlag requestFlag;
    private PwmSetting setting;

    public void setTest(PwmIfTest test)
    {
        this.test = test;
    }

    public void setPermission(Permission permission)
    {
        this.permission = permission;
    }

    public void setNegate(boolean negate)
    {
        this.negate = negate;
    }

    public void setRequestFlag(PwmRequestFlag requestFlag) {
        this.requestFlag = requestFlag;
    }

    public void setSetting(final PwmSetting setting) {
        this.setting = setting;
    }

    @Override
    public int doStartTag()
            throws JspException
    {

        boolean showBody = false;
        if (PwmApplicationMode.determineMode((HttpServletRequest) pageContext.getRequest()) != PwmApplicationMode.ERROR) {
            if (test != null) {
                try {

                    final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest) pageContext.getRequest(),
                            (HttpServletResponse) pageContext.getResponse());
                    final PwmSession pwmSession = pwmRequest.getPwmSession();

                    PwmIfTest testEnum = test;
                    if (testEnum != null) {
                        try {
                            final PwmIfOptions options = new PwmIfOptions(negate, setting, permission, requestFlag);
                            showBody = testEnum.passed(pwmRequest, options);
                        } catch (ChaiUnavailableException e) {
                            LOGGER.error("error testing jsp if '" + testEnum.toString() + "', error: " + e.getMessage());
                        }
                    } else {
                        final String errorMsg = "unknown test name '" + test + "' in pwm:If jsp tag!";
                        LOGGER.warn(pwmSession, errorMsg);
                    }
                } catch (PwmUnrecoverableException e) {
                    LOGGER.error("error executing PwmIfTag for test '" + test + "', error: " + e.getMessage());
                }
            }
        }

        if (negate) {
            showBody = !showBody;
        }

        return showBody ? EVAL_BODY_INCLUDE : SKIP_BODY;
    }
}

