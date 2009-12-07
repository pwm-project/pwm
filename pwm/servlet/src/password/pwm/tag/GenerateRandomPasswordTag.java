/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.tag;

import password.pwm.PwmSession;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;
import java.util.Map;

/**
 * Generates a random password.  The password will be compliant with the user's
 * password policy.
 *
 * @author Jason D. Rivard
 */
public class GenerateRandomPasswordTag extends TagSupport {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(GenerateRandomPasswordTag.class);

    private String instance;

// --------------------- GETTER / SETTER METHODS ---------------------

    public void setInstance(final String instance)
    {
        this.instance = instance;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            String randomPassword;
            if (instance == null) {
                randomPassword = RandomPasswordGenerator.createRandomPassword(pwmSession);
            } else {
                final Map<String, String> randomCache = pwmSession.getSessionStateBean().getRandomPasswordCache();
                randomPassword = randomCache.get(instance);

                if (randomPassword == null) {
                    randomPassword = RandomPasswordGenerator.createRandomPassword(pwmSession);
                    randomCache.put(instance, (randomPassword));
                }
            }

            if (randomPassword != null) {
                pageContext.getOut().write(randomPassword);
            }
        } catch (Exception e) {
            LOGGER.warn("error generating random password", e);
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}