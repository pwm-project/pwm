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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PwmScriptTag extends BodyTagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmScriptTag.class);

    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<\\s*script.*?>|<\\s*\\/\\s*script\\s*.*?>"); // match start and end <script> tags

    public int doStartTag()
            throws JspException
    {
        return EVAL_BODY_BUFFERED;
    }

    public int doAfterBody() {
        try {
            final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest) pageContext.getRequest(), (HttpServletResponse) pageContext.getResponse());
            final BodyContent bc = getBodyContent();
            if (bc != null) {
                final String tagBody = bc.getString();
                final String strippedTagBody = stripHtmlScriptTags(tagBody);
                    final String output = "<script type=\"text/javascript\" nonce=\"" + pwmRequest.getCspNonce() + "\">"
                            + strippedTagBody
                            + "</script>";
                    getPreviousOut().write(output);
            }
        } catch (IOException e) {
            LOGGER.error("IO error while processing PwmScriptTag: " + e.getMessage());
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
