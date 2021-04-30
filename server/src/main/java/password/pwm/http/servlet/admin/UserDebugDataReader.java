/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.servlet.admin;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.PwmService;
import password.pwm.svc.pwnotify.PwNotifyUserStatus;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordUtility;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class UserDebugDataReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserDebugDataReader.class );

    public static UserDebugDataBean readUserDebugData(
            final PwmDomain pwmDomain,
            final Locale locale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {


        final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxyForOfflineUser( pwmDomain.getPwmApplication(), sessionLabel, userIdentity );

        final Map<Permission, String> permissions = UserDebugDataReader.permissionMap( pwmDomain, sessionLabel, userIdentity );

        final Map<ProfileDefinition, String> profiles = UserDebugDataReader.profileMap( pwmDomain, sessionLabel, userIdentity );

        final PwmPasswordPolicy ldapPasswordPolicy = PasswordUtility.readLdapPasswordPolicy( pwmDomain, pwmDomain.getProxiedChaiUser( sessionLabel, userIdentity ) );

        final PwmPasswordPolicy configPasswordPolicy = PasswordUtility.determineConfiguredPolicyProfileForUser(
                pwmDomain,
                sessionLabel,
                userIdentity
        );

        boolean readablePassword = false;
        try
        {
            readablePassword = null != LdapOperationsHelper.readLdapPassword( pwmDomain, sessionLabel, userIdentity );
        }
        catch ( final ChaiUnavailableException e )
        {
            /* disregard */
        }

        final MacroRequest macroRequest = MacroRequest.forUser( pwmDomain.getPwmApplication(), locale, sessionLabel, userIdentity );

        final PwNotifyUserStatus pwNotifyUserStatus = readPwNotifyUserStatus( pwmDomain, userIdentity, sessionLabel );

        return UserDebugDataBean.builder()
                .userInfo( userInfo )
                .publicUserInfoBean( PublicUserInfoBean.fromUserInfoBean( userInfo, pwmDomain.getConfig(), locale, macroRequest ) )
                .permissions( permissions )
                .profiles( profiles )
                .ldapPasswordPolicy( ldapPasswordPolicy )
                .configuredPasswordPolicy( configPasswordPolicy )
                .passwordReadable( readablePassword )
                .passwordWithinMinimumLifetime( userInfo.isWithinPasswordMinimumLifetime() )
                .pwNotifyUserStatus( pwNotifyUserStatus )
                .build();
    }


    private static Map<Permission, String> permissionMap(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity

    )
            throws PwmUnrecoverableException
    {
        final Map<Permission, String> results = new TreeMap<>();
        for ( final Permission permission : Permission.values() )
        {
            final PwmSetting setting = permission.getPwmSetting();
            if ( !setting.isHidden() && !setting.getCategory().isHidden() && !setting.getCategory().hasProfiles() )
            {
                final List<UserPermission> userPermission = pwmDomain.getConfig().readSettingAsUserPermission( permission.getPwmSetting() );
                final boolean result = UserPermissionUtility.testUserPermission(
                        pwmDomain,
                        sessionLabel,
                        userIdentity,
                        userPermission
                );
                results.put( permission, result ? Permission.PermissionStatus.GRANTED.toString() : Permission.PermissionStatus.DENIED.toString() );
            }

        }
        return Collections.unmodifiableMap( results );
    }

    private static Map<ProfileDefinition, String> profileMap(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
        throws PwmUnrecoverableException
    {
        final Map<ProfileDefinition, String> results = new TreeMap<>( Comparator.comparing( Enum::name ) );
        for ( final ProfileDefinition profileDefinition : ProfileDefinition.values() )
        {
            if ( profileDefinition.getQueryMatch().isPresent() && profileDefinition.getProfileFactoryClass().isPresent() )
            {
                ProfileUtility.discoverProfileIDForUser(
                        pwmDomain,
                        sessionLabel,
                        userIdentity,
                        profileDefinition
                ).ifPresent( ( id ) -> results.put( profileDefinition, id ) );
            }
        }

        return Collections.unmodifiableMap( results );
    }

    private static PwNotifyUserStatus readPwNotifyUserStatus(
            final PwmDomain pwmDomain,
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel
    )
    {
        if ( pwmDomain.getPwNotifyService().status() == PwmService.STATUS.OPEN )
        {
            try
            {
                final Optional<PwNotifyUserStatus> value = pwmDomain.getPwNotifyService().readUserNotificationState( userIdentity, sessionLabel );
                if ( value.isPresent() )
                {
                    return value.get();
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.debug( () -> "error reading user pwNotify status: " + e.getMessage() );
            }
        }

        return null;
    }
}
