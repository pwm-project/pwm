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

package password.pwm.ws.server;

import com.novell.ldapchai.util.StringHelper;
import password.pwm.PwmApplication;
import password.pwm.config.NamedSecretData;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StandaloneRestHelper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(StandaloneRestHelper.class);

    public static StandaloneRestRequestBean initialize(final HttpServletRequest httpServletRequest)
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(httpServletRequest.getServletContext());

        final Set<WebServiceUsage> usages = readWebServiceSecretAuthorizations(pwmApplication, httpServletRequest);

        return StandaloneRestRequestBean.builder()
                .pwmApplication(pwmApplication)
                .authorizedUsages(Collections.unmodifiableSet(usages))
                .build();
    }

    private static Set<WebServiceUsage> readWebServiceSecretAuthorizations(
            final PwmApplication pwmApplication,
            final HttpServletRequest httpServletRequest
    )
    {
        final Set<WebServiceUsage> usages = new HashSet<>();

        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader(pwmApplication, httpServletRequest);
        if (basicAuthInfo != null) {
            final Map<String, NamedSecretData> secrets = pwmApplication.getConfig().readSettingAsNamedPasswords(PwmSetting.WEBSERVICES_EXTERNAL_SECRET);
            final NamedSecretData namedSecretData = secrets.get(basicAuthInfo.getUsername());
            if (namedSecretData != null) {
                if (namedSecretData.getPassword().equals(basicAuthInfo.getPassword())) {
                    final List<WebServiceUsage> namedSecrets = JavaHelper.readEnumListFromStringCollection(WebServiceUsage.class, namedSecretData.getUsage());
                    usages.addAll(namedSecrets);
                    LOGGER.trace("REST request to " + httpServletRequest.getRequestURI() + " specified a basic auth username (\""
                            + basicAuthInfo.getUsername() + "\"), granting usage access to "
                            + StringHelper.stringCollectionToString(namedSecretData.getUsage(), ","));
                } else {
                    LOGGER.trace("REST request to " + httpServletRequest.getRequestURI() + " specified a basic auth username (\""
                            + basicAuthInfo.getUsername() + "\") with an incorrect password");
                }
            } else {
                LOGGER.trace("REST request to " + httpServletRequest.getRequestURI() + " specified a basic auth username (\""
                        + basicAuthInfo.getUsername() + "\") that does not correspond to a configured web secret");
            }
        }

        return Collections.unmodifiableSet(usages);
    }
}
