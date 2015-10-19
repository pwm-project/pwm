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

package password.pwm.http.servlet;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.configguide.ConfigGuideServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerLocalDBServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerWordlistServlet;

import javax.servlet.annotation.WebServlet;
import java.lang.annotation.Annotation;

public enum PwmServletDefinition {
    Login(password.pwm.http.servlet.LoginServlet.class),
    Logout(password.pwm.http.servlet.LogoutServlet.class),
    Captcha(password.pwm.http.servlet.CaptchaServlet.class),
    OAuthConsumer(password.pwm.http.servlet.OAuthConsumerServlet.class),
    Command(password.pwm.http.servlet.CommandServlet.class),
    //Resource(password.pwm.http.servlet.ResourceFileServlet.class),

    AccountInformation(AccountInformationServlet.class),
    ChangePassword(password.pwm.http.servlet.ChangePasswordServlet.class),
    SetupResponses(password.pwm.http.servlet.SetupResponsesServlet.class),
    UpdateProfile(password.pwm.http.servlet.UpdateProfileServlet.class),
    SetupOtp(password.pwm.http.servlet.SetupOtpServlet.class),
    Helpdesk(password.pwm.http.servlet.helpdesk.HelpdeskServlet.class),
    Shortcuts(password.pwm.http.servlet.ShortcutServlet.class),
    PeopleSearch(password.pwm.http.servlet.peoplesearch.PeopleSearchServlet.class),
    GuestRegistration(password.pwm.http.servlet.GuestRegistrationServlet.class),

    Admin(password.pwm.http.servlet.AdminServlet.class),
    ConfigGuide(ConfigGuideServlet.class),
    ConfigEditor(password.pwm.http.servlet.ConfigEditorServlet.class),
    ConfigManager(ConfigManagerServlet.class),
    ConfigManager_Wordlists(ConfigManagerWordlistServlet.class),
    ConfigManager_LocalDB(ConfigManagerLocalDBServlet.class),

    NewUser(password.pwm.http.servlet.NewUserServlet.class),
    ActivateUser(password.pwm.http.servlet.ActivateUserServlet.class),
    ForgottenPassword(password.pwm.http.servlet.forgottenpw.ForgottenPasswordServlet.class),
    ForgottenUsername(password.pwm.http.servlet.ForgottenUsernameServlet.class),

    ;

    private final String[] patterns;
    private final String servletUrl;
    private final Class<? extends PwmServlet> pwmServletClass;

    PwmServletDefinition(final Class<? extends PwmServlet> pwmServletClass) {
        this.pwmServletClass = pwmServletClass;

        try {
            this.patterns = getWebServletAnnotation(pwmServletClass).urlPatterns();
        } catch (Exception e) {
            throw new IllegalStateException("error initializing PwmServletInfo value " + this.toString() + ", error: " + e.getMessage());
        }

        final String firstPattern = patterns[0];
        final int lastSlash = firstPattern.lastIndexOf("/");
        servletUrl = firstPattern.substring(lastSlash + 1,firstPattern.length());
    }

    public String[] urlPatterns() {
        return patterns;
    }

    public String servletUrlName() {
        return servletUrl;
    }

    public String servletUrl() {
        return patterns[0];
    }

    public Class<? extends PwmServlet> getPwmServletClass() {
        return pwmServletClass;
    }

    private WebServlet getWebServletAnnotation(Class<? extends PwmServlet> pwmServletClass) throws PwmUnrecoverableException {
        for (Annotation annotation : pwmServletClass.getDeclaredAnnotations()) {
            if (annotation instanceof WebServlet) {
                return (WebServlet)annotation;
            }
        }

        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"missing WebServlet annotation for class " + this.getClass().getName()));
    }


}
