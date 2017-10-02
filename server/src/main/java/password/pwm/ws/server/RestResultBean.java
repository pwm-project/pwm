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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Config;
import password.pwm.i18n.Message;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter(AccessLevel.PRIVATE)
public class RestResultBean implements Serializable {
    private boolean error;
    private int errorCode;
    private String errorMessage;
    private String errorDetail;
    private String successMessage;
    private Serializable data;

    public static RestResultBean withData(final Serializable data) {
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(data);
        return restResultBean;
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final PwmApplication pwmApplication,
            final Locale locale,
            final Configuration config,
            final boolean forceDetail
    ) {
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setError(true);
        restResultBean.setErrorMessage(errorInformation.toUserStr(locale, config));
        if (forceDetail || (pwmApplication != null && pwmApplication.determineIfDetailErrorMsgShown())) {
            restResultBean.setErrorDetail(errorInformation.toDebugStr());
        }
        restResultBean.setErrorCode(errorInformation.getError().getErrorCode());
        return restResultBean;
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final RestRequestBean restRequestBean
    ) {
        final PwmApplication pwmApplication = restRequestBean.getPwmApplication();
        final Configuration config = restRequestBean.getPwmApplication().getConfig();
        final Locale locale = restRequestBean.getPwmSession().getSessionStateBean().getLocale();
        return fromError(errorInformation, pwmApplication, locale, config, false);
    }

    public static RestResultBean fromError(
            final RestRequest restRequestBean,
            final ErrorInformation errorInformation
    ) {
        final PwmApplication pwmApplication = restRequestBean.getPwmApplication();
        final Configuration config = restRequestBean.getPwmApplication().getConfig();
        final Locale locale = restRequestBean.getLocale();
        return fromError(errorInformation, pwmApplication, locale, config, false);
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation
    ) {
        return fromError(errorInformation, null, null, null, false);
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final boolean showDetails
    )
    {
        return fromError(errorInformation, null, null, null, showDetails);
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final PwmRequest pwmRequest,
            final boolean forceDetail
    ) {
        return fromError(errorInformation, pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getConfig(), forceDetail);
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final PwmRequest pwmRequest
    ) {
        return fromError(errorInformation, pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getConfig(), false);
    }


    public static RestResultBean forSuccessMessage(
            final Serializable data,
            final Locale locale,
            final Configuration config,
            final Message message,
            final String... fieldValues

    ) {
        final RestResultBean restResultBean = new RestResultBean();
        final String msgText = Message.getLocalizedMessage(locale, message, config, fieldValues);
        restResultBean.setSuccessMessage(msgText);
        restResultBean.setData(data);
        return restResultBean;
    }

    public static RestResultBean forSuccessMessage(
            final Locale locale,
            final Configuration config,
            final Message message,
            final String... fieldValues

    ) {
        final RestResultBean restResultBean = new RestResultBean();
        final String msgText = Message.getLocalizedMessage(locale, message, config, fieldValues);
        restResultBean.setSuccessMessage(msgText);
        return restResultBean;
    }

    public static RestResultBean forSuccessMessage(
            final Serializable data,
            final PwmRequest pwmRequest,
            final Message message,
            final String... fieldValues
    ) {
        return forSuccessMessage(data, pwmRequest.getLocale(), pwmRequest.getConfig(), message, fieldValues);
    }

    public static RestResultBean forSuccessMessage(
            final Serializable data,
            final RestRequest restRequest,
            final Message message,
            final String... fieldValues
    ) {
        return forSuccessMessage(data, restRequest.getLocale(), restRequest.getConfig(), message, fieldValues);
    }

    public static RestResultBean forSuccessMessage(
            final RestRequest restRequest,
            final Message message,
            final String... fieldValues
    ) {
        return forSuccessMessage(restRequest.getLocale(), restRequest.getConfig(), message, fieldValues);
    }

    public static RestResultBean forSuccessMessage(
            final PwmRequest pwmRequest,
            final Message message,
            final String... fieldValues
    ) {
        return forSuccessMessage(pwmRequest.getLocale(), pwmRequest.getConfig(), message, fieldValues);
    }

    public static RestResultBean forConfirmMessage(
            final Locale locale,
            final Configuration config,
            final Config message
    ) {
        final RestResultBean restResultBean = new RestResultBean();
        final String msgText = Config.getLocalizedMessage(locale, message, config);
        restResultBean.setSuccessMessage(msgText);
        return restResultBean;
    }

    public static RestResultBean forConfirmMessage(
            final PwmRequest pwmRequest,
            final Config message
    ) {
        return forConfirmMessage(pwmRequest.getLocale(), pwmRequest.getConfig(), message);
    }


    public String toJson() {
        return JsonUtil.serialize(this, JsonUtil.Flag.PrettyPrint) + "\n";
    }
}
