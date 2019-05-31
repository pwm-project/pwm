/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.http;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.DeleteAccountProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.util.List;

/**
 * Wraps an <i>HttpSession</i> to provide additional PWM-related session
 * management activities.
 *
 * @author Jason D. Rivard
 */
public class SessionManager
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( SessionManager.class );

    private ChaiProvider chaiProvider;

    private final PwmSession pwmSession;


    public SessionManager( final PwmSession pwmSession )
    {
        this.pwmSession = pwmSession;
    }

    public ChaiProvider getChaiProvider( )
            throws PwmUnrecoverableException
    {
        if ( chaiProvider == null )
        {
            if ( isAuthenticatedWithoutPasswordAndBind() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_PASSWORD_REQUIRED, "password required for this operation" );
            }
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_AUTHENTICATION_REQUIRED, "ldap connection is not available for session" ) );
        }
        return chaiProvider;
    }

    public boolean isAuthenticatedWithoutPasswordAndBind()
    {
        return pwmSession.getLoginInfoBean().getUserCurrentPassword() == null
                && pwmSession.getLoginInfoBean().getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD
                && chaiProvider == null;

    }

    public void setChaiProvider( final ChaiProvider chaiProvider )
    {
        this.chaiProvider = chaiProvider;
    }

    public void updateUserPassword( final PwmApplication pwmApplication, final UserIdentity userIdentity, final PasswordData userPassword )
            throws PwmUnrecoverableException
    {
        this.closeConnections();

        try
        {
            this.chaiProvider = LdapOperationsHelper.createChaiProvider(
                    pwmApplication,
                    pwmSession.getLabel(),
                    userIdentity.getLdapProfile( pwmApplication.getConfig() ),
                    pwmApplication.getConfig(),
                    userIdentity.getUserDN(),
                    userPassword
            );
            final String userDN = userIdentity.getUserDN();
            chaiProvider.getEntryFactory().newChaiEntry( userDN ).exists();
        }
        catch ( ChaiUnavailableException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                    "error updating cached chaiProvider connection/password: " + e.getMessage() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }


    public void closeConnections( )
    {
        if ( chaiProvider != null )
        {
            try
            {
                LOGGER.debug( pwmSession, () -> "closing user ldap connection" );
                chaiProvider.close();
                chaiProvider = null;
            }
            catch ( Exception e )
            {
                LOGGER.error( pwmSession, "error while closing user connection: " + e.getMessage() );
            }
        }
    }

    public ChaiUser getActor( final PwmApplication pwmApplication )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {

        if ( !pwmSession.isAuthenticated() )
        {
            throw new IllegalStateException( "user not logged in" );
        }

        final UserIdentity userDN = pwmSession.getUserInfo().getUserIdentity();

        if ( userDN == null || userDN.getUserDN() == null || userDN.getUserDN().length() < 1 )
        {
            throw new IllegalStateException( "user not logged in" );
        }

        return this.getChaiProvider().getEntryFactory().newChaiUser( userDN.getUserDN() );
    }

    public boolean hasActiveLdapConnection( )
    {
        return this.chaiProvider != null && this.chaiProvider.isConnected();
    }

    public ChaiUser getActor( final PwmApplication pwmApplication, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( !pwmSession.isAuthenticated() )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_AUTHENTICATION_REQUIRED );
            }
            final UserIdentity thisIdentity = pwmSession.getUserInfo().getUserIdentity();
            if ( thisIdentity.getLdapProfileID() == null || userIdentity.getLdapProfileID() == null )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_NO_LDAP_CONNECTION );
            }
            final ChaiProvider provider = this.getChaiProvider();
            return provider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    public void incrementRequestCounterKey( )
    {
        if ( this.pwmSession != null )
        {
            this.pwmSession.getLoginInfoBean().setReqCounter(
                    this.pwmSession.getLoginInfoBean().getReqCounter() + 1 );

            LOGGER.trace( pwmSession, () -> "incremented request counter to " + this.pwmSession.getLoginInfoBean().getReqCounter() );
        }
    }

    public boolean checkPermission( final PwmApplication pwmApplication, final Permission permission )
            throws PwmUnrecoverableException
    {
        final boolean devDebugMode = pwmApplication.getConfig().isDevDebugMode();
        if ( devDebugMode )
        {
            LOGGER.trace( pwmSession, () -> String.format( "entering checkPermission(%s, %s, %s)", permission, pwmSession, pwmApplication ) );
        }

        if ( !pwmSession.isAuthenticated() )
        {
            if ( devDebugMode )
            {
                LOGGER.trace( pwmSession, () -> "user is not authenticated, returning false for permission check" );
            }
            return false;
        }

        Permission.PermissionStatus status = pwmSession.getUserSessionDataCacheBean().getPermission( permission );
        if ( status == Permission.PermissionStatus.UNCHECKED )
        {
            if ( devDebugMode )
            {
                LOGGER.debug( pwmSession,
                        () -> String.format( "checking permission %s for user %s", permission.toString(), pwmSession.getUserInfo().getUserIdentity().toDelimitedKey() ) );
            }

            final PwmSetting setting = permission.getPwmSetting();
            final List<UserPermission> userPermission = pwmApplication.getConfig().readSettingAsUserPermission( setting );
            final boolean result = LdapPermissionTester.testUserPermissions( pwmApplication, pwmSession.getLabel(), pwmSession.getUserInfo().getUserIdentity(), userPermission );
            status = result ? Permission.PermissionStatus.GRANTED : Permission.PermissionStatus.DENIED;
            pwmSession.getUserSessionDataCacheBean().setPermission( permission, status );

            {
                final Permission.PermissionStatus finalStatus = status;
                LOGGER.debug( pwmSession,
                        () -> String.format( "permission %s for user %s is %s",
                                permission.toString(),
                                pwmSession.isAuthenticated()
                                        ? pwmSession.getUserInfo().getUserIdentity().toDelimitedKey()
                                        : "[unauthenticated]",
                                finalStatus.toString() ) );
            }
        }
        return status == Permission.PermissionStatus.GRANTED;
    }

    public MacroMachine getMacroMachine( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = pwmSession.isAuthenticated()
                ? pwmSession.getUserInfo()
                : null;
        return MacroMachine.forUser( pwmApplication, pwmSession.getLabel(), userInfoBean, pwmSession.getLoginInfoBean() );
    }

    public Profile getProfile( final PwmApplication pwmApplication, final ProfileType profileType ) throws PwmUnrecoverableException
    {
        if ( profileType.isAuthenticated() && !pwmSession.isAuthenticated() )
        {
            return null;
        }
        final String profileID = pwmSession.getUserInfo().getProfileIDs().get( profileType );
        if ( profileID != null )
        {
            return pwmApplication.getConfig().profileMap( profileType ).get( profileID );
        }
        return null;
    }

    public HelpdeskProfile getHelpdeskProfile( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return ( HelpdeskProfile ) getProfile( pwmApplication, ProfileType.Helpdesk );
    }

    public SetupOtpProfile getSetupOTPProfile( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return ( SetupOtpProfile ) getProfile( pwmApplication, ProfileType.SetupOTPProfile );
    }

    public UpdateProfileProfile getUpdateAttributeProfile( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return ( UpdateProfileProfile ) getProfile( pwmApplication, ProfileType.UpdateAttributes );
    }

    public DeleteAccountProfile getSelfDeleteProfile( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return ( DeleteAccountProfile ) getProfile( pwmApplication, ProfileType.DeleteAccount );
    }
}
