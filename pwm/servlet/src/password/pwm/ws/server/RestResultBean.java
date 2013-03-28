/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.google.gson.GsonBuilder;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;

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

    public static RestResultBean fromErrorInformation(
            final ErrorInformation errorInformation,
            final Locale locale,
            final Configuration config
    ) {
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setError(true);
        restResultBean.setErrorMessage(errorInformation.toUserStr(locale, config));
        restResultBean.setErrorDetail(errorInformation.toDebugStr());
        restResultBean.setErrorCode(errorInformation.getError().getErrorCode());
        return restResultBean;
    }

    public static RestResultBean fromErrorInformation(
            final ErrorInformation errorInformation,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    ) {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        return fromErrorInformation(errorInformation, locale, config);
    }

    public String toJson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.disableHtmlEscaping();
        return gsonBuilder.create().toJson(this);
    }
}
