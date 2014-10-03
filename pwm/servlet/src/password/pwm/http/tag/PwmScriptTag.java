/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PwmScriptTag extends BodyTagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmScriptTag.class);

    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<\\s*script.*?>|<\\s*\\/\\s*script\\s*.*?>"); // match start and end <script> tags

    public int doStartTag()
            throws JspException
    {
        try {
            final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest)pageContext.getRequest(),(HttpServletResponse)pageContext.getResponse());
            final boolean stripJsInline = Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.SECURITY_STRIP_INLINE_JAVASCRIPT));
            return stripJsInline ? super.doStartTag() : EVAL_BODY_INCLUDE;
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error while processing PwmScriptTag: " + e.getMessage());
        }
        return super.doStartTag();
    }

    public int doAfterBody() {
        try {
            final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest)pageContext.getRequest(),(HttpServletResponse)pageContext.getResponse());
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final boolean stripJsInline = Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.SECURITY_STRIP_INLINE_JAVASCRIPT));
            if (stripJsInline) {
                final BodyContent bc = getBodyContent();
                final String tagBody = bc.getString();
                final String strippedTagBody = stripHtmlScriptTags(tagBody);
                pwmSession.getSessionStateBean().getScriptContents().append(strippedTagBody);
                bc.clearBody();
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error while processing PwmScriptTag: " + e.getMessage());
        }
        return SKIP_BODY;
    }

    private static String stripHtmlScriptTags(final String input) {
        if (input == null) {
            return null;
        }

        final Matcher matcher = SCRIPT_TAG_PATTERN.matcher(input);
        return matcher.replaceAll("");
    }
}
