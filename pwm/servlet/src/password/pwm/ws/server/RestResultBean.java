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

package password.pwm.ws.server;

import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;

import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.util.Locale;

public class RestResultBean implements Serializable {
    private boolean error;
    private int errorCode;
    private String errorMessage;
    private String errorDetail;
    private String successMessage;
    private Serializable data;

    public RestResultBean() {
    }

    public RestResultBean(final Serializable data) {
        this.data = data;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public void setSuccessMessage(String successMessage) {
        this.successMessage = successMessage;
    }

    public Serializable getData() {
        return data;
    }

    public void setData(Serializable data) {
        this.data = data;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
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
        if (forceDetail || Helper.determineIfDetailErrorMsgShown(pwmApplication)) {
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
            final ErrorInformation errorInformation
    ) {
        return fromError(errorInformation, null, null, null, false);
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
            final PwmRequest pwmRequest,
            final Message message
    ) {
        return forSuccessMessage(pwmRequest.getLocale(), pwmRequest.getConfig(), message);
    }

    public String toJson() {
        return JsonUtil.serialize(this);
    }

    public Response asJsonResponse() {
        final Response.ResponseBuilder responseBuilder = Response.ok();
        final String body = this.toJson();
        responseBuilder.entity(body);
        return responseBuilder.build();
    }
}
