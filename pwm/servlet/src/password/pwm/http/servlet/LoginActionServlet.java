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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LoginActionServlet extends PwmServlet {



    public enum LoginActionActions implements ProcessAction {
        checkAll,
        checkResponses("checkIfResponseConfigNeeded"),
        checkProfile("checkAttributes"),
        checkPassword("checkExpire"),

        ;

        private Set<String> names;

        LoginActionActions(String... names)
        {
            final Set<String> nameSet = new HashSet<>();
            nameSet.add(this.toString().toLowerCase());
            if (names != null) {
                for (final String name : names) {
                    nameSet.add(name.toLowerCase());
                }
            }
            this.names = Collections.unmodifiableSet(nameSet);
        }

        public Set<String> getNames()
        {
            return names;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return PwmServlet.GET_AND_POST_METHODS;
        }

        public static LoginActionActions forValue(final String value) {
            if (value == null) {
                return null;
            }

            final String lowerValue = value.toLowerCase();
            for (final LoginActionActions loopAction : LoginActionActions.values()) {
                if (loopAction.getNames().contains(lowerValue)) {
                    return loopAction;
                }
            }

            return null;
        }
    }


    @Override
    protected void processAction(PwmRequest request)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        request.sendRedirectToContinue();
    }

    protected LoginActionActions readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        return LoginActionActions.forValue(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
    }
}
