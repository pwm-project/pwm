/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import password.pwm.http.servlet.activation.ActivateUserServlet;
import password.pwm.http.servlet.admin.AdminServlet;
import password.pwm.http.servlet.changepw.PrivateChangePasswordServlet;
import password.pwm.http.servlet.changepw.PublicChangePasswordServlet;
import password.pwm.http.servlet.command.PrivateCommandServlet;
import password.pwm.http.servlet.command.PublicCommandServlet;
import password.pwm.http.servlet.configeditor.ConfigEditorServlet;
import password.pwm.http.servlet.configguide.ConfigGuideServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerCertificatesServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerLocalDBServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerLoginServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerServlet;
import password.pwm.http.servlet.configmanager.ConfigManagerWordlistServlet;
import password.pwm.http.servlet.newuser.NewUserServlet;
import password.pwm.http.servlet.oauth.OAuthConsumerServlet;
import password.pwm.http.servlet.peoplesearch.PrivatePeopleSearchServlet;
import password.pwm.http.servlet.peoplesearch.PublicPeopleSearchServlet;
import password.pwm.http.servlet.updateprofile.UpdateProfileServlet;

import javax.servlet.annotation.WebServlet;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public enum PwmServletDefinition
{
    Login( password.pwm.http.servlet.LoginServlet.class, LoginServletBean.class ),
    Logout( password.pwm.http.servlet.LogoutServlet.class, null ),
    OAuthConsumer( OAuthConsumerServlet.class, null ),
    PublicCommand( PublicCommandServlet.class, null ),
    PublicPeopleSearch( PublicPeopleSearchServlet.class, null ),
    PublicChangePassword( PublicChangePasswordServlet.class, ChangePasswordBean.class ),
    Resource( password.pwm.http.servlet.resource.ResourceFileServlet.class, null ),

    AccountInformation( AccountInformationServlet.class, null ),
    PrivateChangePassword( PrivateChangePasswordServlet.class, ChangePasswordBean.class, Flag.RequiresUserPasswordAndBind ),
    SetupResponses( password.pwm.http.servlet.SetupResponsesServlet.class, SetupResponsesBean.class, Flag.RequiresUserPasswordAndBind ),
    UpdateProfile( UpdateProfileServlet.class, UpdateProfileBean.class, Flag.RequiresUserPasswordAndBind ),
    SetupOtp( password.pwm.http.servlet.SetupOtpServlet.class, SetupOtpBean.class, Flag.RequiresUserPasswordAndBind ),
    Helpdesk( password.pwm.http.servlet.helpdesk.HelpdeskServlet.class, null ),
    Shortcuts( password.pwm.http.servlet.ShortcutServlet.class, ShortcutsBean.class ),
    PrivateCommand( PrivateCommandServlet.class, null ),
    PrivatePeopleSearch( PrivatePeopleSearchServlet.class, null ),
    GuestRegistration( password.pwm.http.servlet.GuestRegistrationServlet.class, null, Flag.RequiresUserPasswordAndBind ),
    SelfDelete( DeleteAccountServlet.class, DeleteAccountBean.class ),

    ClientApi( ClientApiServlet.class, null ),
    Admin( AdminServlet.class, AdminBean.class ),
    ConfigGuide( ConfigGuideServlet.class, ConfigGuideBean.class ),
    ConfigEditor( ConfigEditorServlet.class, null ),
    ConfigManager( ConfigManagerServlet.class, ConfigManagerBean.class ),
    ConfigManager_Login( ConfigManagerLoginServlet.class, ConfigManagerBean.class ),
    ConfigManager_Wordlists( ConfigManagerWordlistServlet.class, ConfigManagerBean.class ),
    ConfigManager_LocalDB( ConfigManagerLocalDBServlet.class, ConfigManagerBean.class ),
    ConfigManager_Certificates( ConfigManagerCertificatesServlet.class, ConfigManagerBean.class ),
    FullPageHealth( FullPageHealthServlet.class, null ),

    NewUser( NewUserServlet.class, NewUserBean.class ),
    ActivateUser( ActivateUserServlet.class, ActivateUserBean.class ),
    ForgottenPassword( password.pwm.http.servlet.forgottenpw.ForgottenPasswordServlet.class, ForgottenPasswordBean.class ),
    ForgottenUsername( password.pwm.http.servlet.ForgottenUsernameServlet.class, null ),;

    private final String[] patterns;
    private final String servletUrl;
    private final Class<? extends PwmServlet> pwmServletClass;
    private final Class<? extends PwmSessionBean> pwmSessionBeanClass;
    private final Collection<Flag> flags;

    public enum Flag
    {
        RequiresUserPasswordAndBind,
    }

    PwmServletDefinition(
            final Class<? extends PwmServlet> pwmServletClass,
            final Class<? extends PwmSessionBean> pwmSessionBeanClass,
            final Flag... flags
    )
    {
        this.pwmServletClass = pwmServletClass;
        this.pwmSessionBeanClass = pwmSessionBeanClass;
        final EnumSet flagSet = EnumSet.noneOf( Flag.class );
        Collections.addAll( flagSet, flags );
        this.flags = Collections.unmodifiableSet( flagSet );

        try
        {
            this.patterns = getWebServletAnnotation( pwmServletClass ).urlPatterns();
        }
        catch ( final Exception e )
        {
            throw new IllegalStateException( "error initializing PwmServletInfo value " + this.toString() + ", error: " + e.getMessage() );
        }

        final String firstPattern = patterns[ 0 ];
        final int lastSlash = firstPattern.lastIndexOf( "/" );
        servletUrl = firstPattern.substring( lastSlash + 1 );
    }

    public String[] urlPatterns( )
    {
        return patterns == null ? null : Arrays.copyOf( patterns, patterns.length );
    }

    public String servletUrlName( )
    {
        return servletUrl;
    }

    public String servletUrl( )
    {
        return patterns[ 0 ];
    }

    public Class<? extends PwmServlet> getPwmServletClass( )
    {
        return pwmServletClass;
    }

    public Class<? extends PwmSessionBean> getPwmSessionBeanClass( )
    {
        return pwmSessionBeanClass;
    }

    private WebServlet getWebServletAnnotation( final Class<? extends PwmServlet> pwmServletClass ) throws PwmUnrecoverableException
    {
        for ( final Annotation annotation : pwmServletClass.getDeclaredAnnotations() )
        {
            if ( annotation instanceof WebServlet )
            {
                return ( WebServlet ) annotation;
            }
        }

        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "missing WebServlet annotation for class " + this.getClass().getName() ) );
    }

    public Collection<Flag> getFlags()
    {
        return flags;
    }
}
