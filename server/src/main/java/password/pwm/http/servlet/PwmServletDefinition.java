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

package password.pwm.http.servlet;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ActivateUserBean;
import password.pwm.http.bean.AdminBean;
import password.pwm.http.bean.ChangePasswordBean;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.bean.DeleteAccountBean;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.bean.LoginServletBean;
import password.pwm.http.bean.NewUserBean;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.http.bean.SetupOtpBean;
import password.pwm.http.bean.SetupResponsesBean;
import password.pwm.http.bean.ShortcutsBean;
import password.pwm.http.bean.UpdateProfileBean;
import password.pwm.http.servlet.accountinfo.AccountInformationServlet;
import password.pwm.http.servlet.admin.AdminServlet;
import password.pwm.http.servlet.changepw.PrivateChangePasswordServlet;
import password.pwm.http.servlet.changepw.PublicChangePasswordServlet;
import password.pwm.http.servlet.command.PrivateCommandServlet;
import password.pwm.http.servlet.command.PublicCommandServlet;
import password.pwm.http.servlet.configeditor.ConfigEditorServlet;
import password.pwm.http.servlet.configguide.ConfigGuideServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerCertificatesServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerLocalDBServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerWordlistServlet;
import password.pwm.http.servlet.newuser.NewUserServlet;
import password.pwm.http.servlet.oauth.OAuthConsumerServlet;
import password.pwm.http.servlet.peoplesearch.PrivatePeopleSearchServlet;
import password.pwm.http.servlet.peoplesearch.PublicPeopleSearchServlet;

import javax.servlet.annotation.WebServlet;
import java.lang.annotation.Annotation;
import java.util.Arrays;

public enum PwmServletDefinition {
    Login(password.pwm.http.servlet.LoginServlet.class, LoginServletBean.class),
    Logout(password.pwm.http.servlet.LogoutServlet.class, null),
    OAuthConsumer(OAuthConsumerServlet.class, null),
    PublicCommand(PublicCommandServlet.class, null),
    PublicPeopleSearch(PublicPeopleSearchServlet.class, null),
    PublicChangePassword(PublicChangePasswordServlet.class, ChangePasswordBean.class),
    //Resource(password.pwm.http.servlet.ResourceFileServlet.class),

    AccountInformation(AccountInformationServlet.class, null),
    PrivateChangePassword(PrivateChangePasswordServlet.class, ChangePasswordBean.class),
    SetupResponses(password.pwm.http.servlet.SetupResponsesServlet.class, SetupResponsesBean.class),
    UpdateProfile(password.pwm.http.servlet.UpdateProfileServlet.class, UpdateProfileBean.class),
    SetupOtp(password.pwm.http.servlet.SetupOtpServlet.class, SetupOtpBean.class),
    Helpdesk(password.pwm.http.servlet.helpdesk.HelpdeskServlet.class, null),
    Shortcuts(password.pwm.http.servlet.ShortcutServlet.class, ShortcutsBean.class),
    PrivateCommand(PrivateCommandServlet.class, null),
    PrivatePeopleSearch(PrivatePeopleSearchServlet.class, null),
    GuestRegistration(password.pwm.http.servlet.GuestRegistrationServlet.class, null),
    SelfDelete(DeleteAccountServlet.class, DeleteAccountBean.class),

    ClientApi(ClientApiServlet.class, null),
    Admin(AdminServlet.class, AdminBean.class),
    ConfigGuide(ConfigGuideServlet.class, ConfigGuideBean.class),
    ConfigEditor(ConfigEditorServlet.class, null),
    ConfigManager(ConfigManagerServlet.class, ConfigManagerBean.class),
    ConfigManager_Wordlists(ConfigManagerWordlistServlet.class, ConfigManagerBean.class),
    ConfigManager_LocalDB(ConfigManagerLocalDBServlet.class, ConfigManagerBean.class),
    ConfigManager_Certificates(ConfigManagerCertificatesServlet.class, ConfigManagerBean.class),

    NewUser(NewUserServlet.class, NewUserBean.class),
    ActivateUser(password.pwm.http.servlet.ActivateUserServlet.class, ActivateUserBean.class),
    ForgottenPassword(password.pwm.http.servlet.forgottenpw.ForgottenPasswordServlet.class, ForgottenPasswordBean.class),
    ForgottenUsername(password.pwm.http.servlet.ForgottenUsernameServlet.class, null),

    ;

    private final String[] patterns;
    private final String servletUrl;
    private final Class<? extends PwmServlet> pwmServletClass;
    private final Class<? extends PwmSessionBean> pwmSessionBeanClass;

    PwmServletDefinition(final Class<? extends PwmServlet> pwmServletClass, final Class<? extends PwmSessionBean> pwmSessionBeanClass) {
        this.pwmServletClass = pwmServletClass;
        this.pwmSessionBeanClass = pwmSessionBeanClass;

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
        return patterns == null ? null : Arrays.copyOf(patterns, patterns.length);
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

    public Class<? extends PwmSessionBean> getPwmSessionBeanClass() {
        return pwmSessionBeanClass;
    }

    private WebServlet getWebServletAnnotation(final Class<? extends PwmServlet> pwmServletClass) throws PwmUnrecoverableException {
        for (final Annotation annotation : pwmServletClass.getDeclaredAnnotations()) {
            if (annotation instanceof WebServlet) {
                return (WebServlet)annotation;
            }
        }

        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"missing WebServlet annotation for class " + this.getClass().getName()));
    }


}
