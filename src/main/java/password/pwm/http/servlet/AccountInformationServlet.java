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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet(
        name="UserInformationServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/account",
                PwmConstants.URL_PREFIX_PRIVATE + "/userinfo",
                PwmConstants.URL_PREFIX_PRIVATE + "/userinfo.jsp"
        }
)
public class AccountInformationServlet extends AbstractPwmServlet {
    @Override
    protected void processAction(PwmRequest pwmRequest) throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {

        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.ACCOUNT_INFORMATION_ENABLED)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
            return;
        }


        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.ACCOUNT_INFORMATION);
    }

    @Override
    protected ProcessAction readProcessAction(PwmRequest request) throws PwmUnrecoverableException {
        return null;
    }
}
