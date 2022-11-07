/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.error.PwmInternalException;
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
import password.pwm.http.servlet.admin.domain.DomainAdminReportServlet;
import password.pwm.http.servlet.admin.AdminMenuServlet;
import password.pwm.http.servlet.admin.SystemAdminServlet;
import password.pwm.http.servlet.admin.domain.DomainAdminStatisticsServlet;
import password.pwm.http.servlet.admin.domain.DomainAdminUserDebugServlet;
import password.pwm.http.servlet.changepw.PrivateChangePasswordServlet;
import password.pwm.http.servlet.changepw.PublicChangePasswordServlet;
import password.pwm.http.servlet.command.PrivateCommandServlet;
import password.pwm.http.servlet.command.PublicCommandServlet;
import password.pwm.http.servlet.configeditor.ConfigEditorServlet;
import password.pwm.http.servlet.configguide.ConfigGuideServlet;
import password.pwm.http.servlet.admin.system.SystemAdminCertificatesServlet;
import password.pwm.http.servlet.admin.system.ConfigManagerLocalDBServlet;
import password.pwm.http.servlet.admin.system.ConfigManagerLoginServlet;
import password.pwm.http.servlet.admin.system.ConfigManagerServlet;
import password.pwm.http.servlet.admin.system.ConfigManagerWordlistServlet;
import password.pwm.http.servlet.newuser.NewUserServlet;
import password.pwm.http.servlet.oauth.OAuthConsumerServlet;
import password.pwm.http.servlet.peoplesearch.PrivatePeopleSearchServlet;
import password.pwm.http.servlet.peoplesearch.PublicPeopleSearchServlet;
import password.pwm.http.servlet.setupresponses.SetupResponsesServlet;
import password.pwm.http.servlet.updateprofile.UpdateProfileServlet;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.EnumUtil;

import javax.servlet.annotation.WebServlet;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    SetupResponses( SetupResponsesServlet.class, SetupResponsesBean.class, Flag.RequiresUserPasswordAndBind ),
    UpdateProfile( UpdateProfileServlet.class, UpdateProfileBean.class, Flag.RequiresUserPasswordAndBind ),
    SetupOtp( password.pwm.http.servlet.SetupOtpServlet.class, SetupOtpBean.class, Flag.RequiresUserPasswordAndBind ),
    Helpdesk( password.pwm.http.servlet.helpdesk.HelpdeskServlet.class, null ),
    Shortcuts( password.pwm.http.servlet.ShortcutServlet.class, ShortcutsBean.class ),
    PrivateCommand( PrivateCommandServlet.class, null ),
    PrivatePeopleSearch( PrivatePeopleSearchServlet.class, null ),
    GuestRegistration( password.pwm.http.servlet.GuestRegistrationServlet.class, null, Flag.RequiresUserPasswordAndBind ),
    SelfDelete( DeleteAccountServlet.class, DeleteAccountBean.class ),

    ClientApi( ClientApiServlet.class, null ),

    AdminMenu( AdminMenuServlet.class, null, Flag.RequiresUserPasswordAndBind ),
    SystemAdmin( SystemAdminServlet.class, AdminBean.class, Flag.RequiresUserPasswordAndBind ),

    DomainAdminReport( DomainAdminReportServlet.class, null, Flag.RequiresUserPasswordAndBind ),
    DomainAdminStatistics( DomainAdminStatisticsServlet.class, null, Flag.RequiresUserPasswordAndBind ),
    DomainAdminUserDebug( DomainAdminUserDebugServlet.class, null, Flag.RequiresUserPasswordAndBind ),

    ConfigGuide( ConfigGuideServlet.class, ConfigGuideBean.class ),
    ConfigEditor( ConfigEditorServlet.class, null, Flag.RequiresConfigAuth ),
    ConfigManager( ConfigManagerServlet.class, ConfigManagerBean.class, Flag.RequiresConfigAuth ),
    ConfigManager_Login( ConfigManagerLoginServlet.class, ConfigManagerBean.class ),
    ConfigManager_Wordlists( ConfigManagerWordlistServlet.class, ConfigManagerBean.class, Flag.RequiresConfigAuth ),
    ConfigManager_LocalDB( ConfigManagerLocalDBServlet.class, ConfigManagerBean.class, Flag.RequiresConfigAuth ),
    SystemAdmin_Certificates( SystemAdminCertificatesServlet.class, ConfigManagerBean.class, Flag.RequiresConfigAuth ),
    FullPageHealth( FullPageHealthServlet.class, null ),

    NewUser( NewUserServlet.class, NewUserBean.class ),
    ActivateUser( ActivateUserServlet.class, ActivateUserBean.class ),
    ForgottenPassword( password.pwm.http.servlet.forgottenpw.ForgottenPasswordServlet.class, ForgottenPasswordBean.class ),
    ForgottenUsername( password.pwm.http.servlet.ForgottenUsernameServlet.class, null ),;

    private final List<String> patterns;
    private final String servletUrl;
    private final Class<? extends PwmServlet> pwmServletClass;
    private final Class<? extends PwmSessionBean> pwmSessionBeanClass;
    private final Collection<Flag> flags;

    public enum Flag
    {
        RequiresUserPasswordAndBind,
        RequiresConfigAuth,
    }

    PwmServletDefinition(
            final Class<? extends PwmServlet> pwmServletClass,
            final Class<? extends PwmSessionBean> pwmSessionBeanClass,
            final Flag... flags
    )
    {
        this.pwmServletClass = pwmServletClass;
        this.pwmSessionBeanClass = pwmSessionBeanClass;
        this.flags = CollectionUtil.enumSetFromArray( flags );

        try
        {
            final String[] definedPatterns = getWebServletAnnotation( pwmServletClass ).urlPatterns();
            if ( definedPatterns == null || definedPatterns.length < 1 )
            {
                throw new PwmInternalException( "no url patterns are defined for servlet "  + this.getClass().getSimpleName()
                        + " value " + this.name() );
            }
            this.patterns = List.of( definedPatterns );
        }
        catch ( final Exception e )
        {
            throw new PwmInternalException( "error initializing " + this.getClass().getSimpleName()
                    + " value " + this.name() + ", error: " + e.getMessage(), e );
        }

        final String firstPattern = patterns.iterator().next();
        final int lastSlash = firstPattern.lastIndexOf( '/' );
        servletUrl = firstPattern.substring( lastSlash + 1 );
    }

    public List<String> urlPatterns( )
    {
        return patterns;
    }

    public String servletUrlName( )
    {
        return servletUrl;
    }

    public String servletUrl( )
    {
        return patterns.get( 0 );
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

        throw new PwmUnrecoverableException( new ErrorInformation(
                PwmError.ERROR_INTERNAL, "missing WebServlet annotation for class " + this.getClass().getName() ) );
    }

    public Collection<Flag> getFlags()
    {
        return flags;
    }

    public static Set<PwmServletDefinition> withFlag( final Flag flag )
    {
        return EnumUtil.readEnumsFromPredicate( PwmServletDefinition.class, e -> e.flags.contains( flag ) );
    }
}
