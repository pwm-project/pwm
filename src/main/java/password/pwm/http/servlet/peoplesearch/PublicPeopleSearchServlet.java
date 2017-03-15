/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.peoplesearch;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet(
        name="PublicPeopleSearchServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/peoplesearch/",
                PwmConstants.URL_PREFIX_PUBLIC + "/peoplesearch",
                PwmConstants.URL_PREFIX_PUBLIC + "/peoplesearch/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/PeopleSearch",
                PwmConstants.URL_PREFIX_PUBLIC + "/PeopleSearch/*",
        }
)
public class PublicPeopleSearchServlet extends PeopleSearchServlet {

        @Override
        public ProcessStatus preProcessCheck(final PwmRequest pwmRequest) throws PwmUnrecoverableException, IOException, ServletException {
                if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC)) {
                        throw new PwmUnrecoverableException(new ErrorInformation(
                                PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                                "public peoplesearch service is not enabled")
                        );
                }

                return super.preProcessCheck(pwmRequest);
        }
}
