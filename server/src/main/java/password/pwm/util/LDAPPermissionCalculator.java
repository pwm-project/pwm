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

package password.pwm.util;

import com.novell.ldapchai.ChaiConstant;
import lombok.Value;
import password.pwm.config.Configuration;
import password.pwm.config.LDAPPermissionInfo;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.queue.SmsQueueManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LDAPPermissionCalculator implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LDAPPermissionCalculator.class );

    private final transient StoredConfiguration storedConfiguration;
    private final transient Configuration configuration;
    private final Collection<PermissionRecord> permissionRecords;

    public LDAPPermissionCalculator( final StoredConfiguration storedConfiguration ) throws PwmUnrecoverableException
    {
        this.storedConfiguration = storedConfiguration;
        this.configuration = new Configuration( storedConfiguration );
        permissionRecords = figureRecords( storedConfiguration );
    }

    public Collection<PermissionRecord> getPermissionRecords( )
    {
        return permissionRecords;
    }

    public Map<String, Map<LDAPPermissionInfo.Access, List<PermissionRecord>>> getPermissionsByActor( final LDAPPermissionInfo.Actor actor )
    {
        final Map<String, Map<LDAPPermissionInfo.Access, List<PermissionRecord>>> returnObj = new TreeMap<>();
        for ( final PermissionRecord permissionRecord : getPermissionRecords() )
        {
            if ( permissionRecord.getActor() == actor )
            {
                if ( !returnObj.containsKey( permissionRecord.getAttribute() ) )
                {
                    returnObj.put( permissionRecord.getAttribute(), new TreeMap<LDAPPermissionInfo.Access, List<PermissionRecord>>() );
                }
                if ( !returnObj.get( permissionRecord.getAttribute() ).containsKey( permissionRecord.getAccess() ) )
                {
                    returnObj.get( permissionRecord.getAttribute() ).put( permissionRecord.getAccess(), new ArrayList<PermissionRecord>() );
                }
                returnObj.get( permissionRecord.getAttribute() ).get( permissionRecord.getAccess() ).add( permissionRecord );
            }
        }
        return Collections.unmodifiableMap( returnObj );
    }

    private Collection<PermissionRecord> figureRecords( final StoredConfiguration storedConfiguration ) throws PwmUnrecoverableException
    {
        final List<PermissionRecord> permissionRecords = new ArrayList<>();

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            if ( pwmSetting.getCategory().hasProfiles() )
            {
                final List<String> profiles = StoredConfigurationUtil.profilesForSetting( pwmSetting, storedConfiguration );
                for ( final String profile : profiles )
                {
                    permissionRecords.addAll( figureRecord( pwmSetting, profile ) );
                }
            }
            else
            {
                permissionRecords.addAll( figureRecord( pwmSetting, null ) );
            }
        }

        permissionRecords.addAll( permissionsForUserPassword() );
        permissionRecords.addAll( figureStaticRecords() );
        return permissionRecords;
    }

    private Collection<PermissionRecord> figureRecord( final PwmSetting pwmSetting, final String profile ) throws PwmUnrecoverableException
    {
        final List<PermissionRecord> permissionRecords = new ArrayList<>();
        final Collection<LDAPPermissionInfo> permissionInfos = figurePermissionInfos( pwmSetting, profile );
        if ( permissionInfos == null )
        {
            return Collections.emptyList();
        }
        for ( final LDAPPermissionInfo permissionInfo : permissionInfos )
        {
            switch ( pwmSetting.getSyntax() )
            {
                case STRING:
                {
                    final String attrName = ( String ) storedConfiguration.readSetting( pwmSetting, profile ).toNativeObject();
                    if ( attrName != null && !attrName.trim().isEmpty() )
                    {
                        permissionRecords.add( new PermissionRecord( attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor() ) );
                    }
                }
                break;

                case FORM:
                {
                    final List<FormConfiguration> formItems = ( List<FormConfiguration> ) storedConfiguration.readSetting( pwmSetting, profile ).toNativeObject();
                    if ( formItems != null )
                    {
                        for ( final FormConfiguration formConfiguration : formItems )
                        {
                            final String attrName = formConfiguration.getName();
                            if ( attrName != null && !attrName.trim().isEmpty() )
                            {
                                permissionRecords.add( new PermissionRecord( attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor() ) );
                            }
                        }
                    }
                }
                break;

                case ACTION:
                {
                    final List<ActionConfiguration> actionItems = ( List<ActionConfiguration> ) storedConfiguration.readSetting( pwmSetting, profile ).toNativeObject();
                    if ( actionItems != null )
                    {
                        for ( final ActionConfiguration actionConfiguration : actionItems )
                        {
                            for ( final ActionConfiguration.LdapAction ldapMethod : actionConfiguration.getLdapActions() )
                            {
                                final String attrName = ldapMethod.getAttributeName();
                                if ( attrName != null && !attrName.trim().isEmpty() )
                                {
                                    permissionRecords.add( new PermissionRecord( attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor() ) );
                                }
                            }
                        }
                    }
                }
                break;

                case STRING_ARRAY:
                {
                    final List<String> strings = ( List<String> ) storedConfiguration.readSetting( pwmSetting, profile ).toNativeObject();
                    for ( final String attrName : strings )
                    {
                        if ( attrName != null && !attrName.trim().isEmpty() )
                        {
                            permissionRecords.add( new PermissionRecord( attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor() ) );
                        }
                    }
                }
                break;

                case USER_PERMISSION:
                {
                    final List<UserPermission> userPermissions = ( List<UserPermission> ) storedConfiguration.readSetting( pwmSetting, profile ).toNativeObject();
                    if ( configuration.getLdapProfiles() != null && !configuration.getLdapProfiles().isEmpty() )
                    {
                        for ( final LdapProfile ldapProfile : configuration.getLdapProfiles().values() )
                        {
                            final String groupAttribute = ldapProfile.readSettingAsString( PwmSetting.LDAP_USER_GROUP_ATTRIBUTE );
                            if ( groupAttribute != null && !groupAttribute.trim().isEmpty() )
                            {
                                for ( final UserPermission userPermission : userPermissions )
                                {
                                    if ( userPermission.getType() == UserPermission.Type.ldapGroup )
                                    {
                                        permissionRecords.add( new PermissionRecord( groupAttribute, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor() ) );
                                    }
                                }
                            }
                        }
                    }
                }
                break;

                default:
                    throw new PwmUnrecoverableException( new ErrorInformation(
                            PwmError.ERROR_INTERNAL,
                            "no ldap permission record reader handler for setting " + pwmSetting.getKey() )
                    );

            }
        }
        return permissionRecords;
    }

    @SuppressWarnings( "checkstyle:MethodLength" )
    private Collection<LDAPPermissionInfo> figurePermissionInfos( final PwmSetting pwmSetting, final String profile )
    {

        PwmSettingCategory category = pwmSetting.getCategory();
        while ( category.hasProfiles() && !category.isTopLevelProfile() )
        {
            category = category.getParent();
        }

        switch ( category )
        {
            case PEOPLE_SEARCH:
            {
                if ( !( Boolean ) storedConfiguration.readSetting( PwmSetting.PEOPLE_SEARCH_ENABLE, null ).toNativeObject() )
                {
                    return Collections.emptyList();
                }
                final boolean proxyOverride = ( Boolean ) storedConfiguration.readSetting( PwmSetting.PEOPLE_SEARCH_USE_PROXY, profile ).toNativeObject();
                final boolean publicOverride = ( Boolean ) storedConfiguration.readSetting( PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC, profile ).toNativeObject();

                if ( proxyOverride || publicOverride )
                {
                    final Collection<LDAPPermissionInfo> configuredRecords = pwmSetting.getLDAPPermissionInfo();
                    final Collection<LDAPPermissionInfo> returnRecords = new ArrayList<>();
                    for ( final LDAPPermissionInfo ldapPermissionInfo : configuredRecords )
                    {
                        if ( !( proxyOverride && publicOverride ) )
                        {
                            // include regular self-other permission
                            returnRecords.add( ldapPermissionInfo );
                        }
                        returnRecords.add( new LDAPPermissionInfo( ldapPermissionInfo.getAccess(), LDAPPermissionInfo.Actor.proxy ) );
                    }
                    return returnRecords;
                }
            }
            break;

            case GUEST:
            {
                if ( !( Boolean ) storedConfiguration.readSetting( PwmSetting.GUEST_ENABLE, null ).toNativeObject() )
                {
                    return Collections.emptyList();
                }
            }
            break;

            case UPDATE:
            case UPDATE_PROFILE:
            case UPDATE_SETTINGS:
            {
                if ( !( Boolean ) storedConfiguration.readSetting( PwmSetting.UPDATE_PROFILE_ENABLE, null ).toNativeObject() )
                {
                    return Collections.emptyList();
                }
            }
            break;

            case FORGOTTEN_USERNAME:
            {
                if ( !( Boolean ) storedConfiguration.readSetting( PwmSetting.FORGOTTEN_USERNAME_ENABLE, null ).toNativeObject() )
                {
                    return Collections.emptyList();
                }
            }
            break;

            case NEWUSER:
            case NEWUSER_PROFILE:
            case NEWUSER_SETTINGS:
            {
                if ( !( Boolean ) storedConfiguration.readSetting( PwmSetting.NEWUSER_ENABLE, null ).toNativeObject() )
                {
                    return Collections.emptyList();
                }
            }
            break;

            case ACTIVATION:
            {
                if ( !( Boolean ) storedConfiguration.readSetting( PwmSetting.ACTIVATE_USER_ENABLE, null ).toNativeObject() )
                {
                    return Collections.emptyList();
                }
            }
            break;

            case HELPDESK_PROFILE:
            {
                if ( !( Boolean ) storedConfiguration.readSetting( PwmSetting.HELPDESK_ENABLE, null ).toNativeObject() )
                {
                    return Collections.emptyList();
                }
                if ( ( Boolean ) storedConfiguration.readSetting( PwmSetting.HELPDESK_USE_PROXY, profile ).toNativeObject() )
                {
                    final Collection<LDAPPermissionInfo> configuredRecords = pwmSetting.getLDAPPermissionInfo();
                    final Collection<LDAPPermissionInfo> returnRecords = new ArrayList<>();
                    for ( final LDAPPermissionInfo ldapPermissionInfo : configuredRecords )
                    {
                        returnRecords.add( new LDAPPermissionInfo( ldapPermissionInfo.getAccess(), LDAPPermissionInfo.Actor.proxy ) );
                    }
                    return returnRecords;
                }

            }
            break;

            default:
                //continue processing
                break;
        }

        switch ( pwmSetting )
        {
            case CHALLENGE_USER_ATTRIBUTE:
            {
                final Set<DataStorageMethod> storageMethods = new HashSet<>();
                storageMethods.addAll( configuration.getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE ) );
                storageMethods.addAll( configuration.getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE ) );
                if ( !storageMethods.contains( DataStorageMethod.LDAP ) )
                {
                    return Collections.emptyList();
                }

            }
            break;

            case SMS_USER_PHONE_ATTRIBUTE:
            {
                if ( !SmsQueueManager.smsIsConfigured( configuration ) )
                {
                    return Collections.emptyList();
                }
            }
            break;

            default:
                //continue processing
                break;
        }

        return pwmSetting.getLDAPPermissionInfo();
    }

    private Collection<PermissionRecord> permissionsForUserPassword( )
    {
        final String userPasswordAttributeName = LocaleHelper.getLocalizedMessage( Config.Label_UserPasswordAttribute, null );
        final Collection<PermissionRecord> records = new ArrayList<>();

        // user set password
        records.add( new PermissionRecord( userPasswordAttributeName, null, null, LDAPPermissionInfo.Access.write, LDAPPermissionInfo.Actor.self ) );

        // proxy user set password
        if ( configuration.readSettingAsBoolean( PwmSetting.FORGOTTEN_PASSWORD_ENABLE ) )
        {
            final Collection<PwmSettingTemplate> templates = configuration.getTemplate().getTemplates();
            if ( templates.contains( PwmSettingTemplate.NOVL ) || templates.contains( PwmSettingTemplate.NOVL_IDM ) )
            {
                records.add( new PermissionRecord(
                        userPasswordAttributeName,
                        PwmSetting.FORGOTTEN_PASSWORD_ENABLE,
                        null,
                        LDAPPermissionInfo.Access.read,
                        LDAPPermissionInfo.Actor.proxy ) );
            }
            else
            {
                records.add( new PermissionRecord(
                        userPasswordAttributeName,
                        PwmSetting.FORGOTTEN_PASSWORD_ENABLE,
                        null,
                        LDAPPermissionInfo.Access.write,
                        LDAPPermissionInfo.Actor.proxy ) );
            }
        }

        if ( configuration.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE ) )
        {
            records.add( new PermissionRecord(
                    userPasswordAttributeName,
                    PwmSetting.HELPDESK_ENABLE,
                    null,
                    LDAPPermissionInfo.Access.write,
                    LDAPPermissionInfo.Actor.helpdesk ) );
        }

        if ( configuration.readSettingAsBoolean( PwmSetting.GUEST_ENABLE ) )
        {
            records.add( new PermissionRecord(
                    userPasswordAttributeName,
                    PwmSetting.GUEST_ENABLE,
                    null,
                    LDAPPermissionInfo.Access.write,
                    LDAPPermissionInfo.Actor.guestManager ) );
        }

        if ( configuration.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            records.add( new PermissionRecord(
                    userPasswordAttributeName,
                    PwmSetting.NEWUSER_ENABLE,
                    null,
                    LDAPPermissionInfo.Access.write,
                    LDAPPermissionInfo.Actor.proxy ) );
        }


        return records;
    }


    private Collection<PermissionRecord> figureStaticRecords( )
    {

        final Set<PwmSettingTemplate> edirInterestedTemplates =
                Collections.unmodifiableSet( new HashSet<>( Arrays.asList(
                        PwmSettingTemplate.NOVL, PwmSettingTemplate.NOVL_IDM ) )
                );

        final List<PermissionRecord> permissionRecords = new ArrayList<>();

        final PwmSettingTemplateSet templateSet = storedConfiguration.getTemplateSet();

        {
            // edir specific attributes
            if ( !Collections.disjoint( templateSet.getTemplates(), edirInterestedTemplates ) )
            {
                final Map<String, LDAPPermissionInfo.Access> ldapAttributes = new LinkedHashMap<>();
                ldapAttributes.put( ChaiConstant.ATTR_LDAP_LOCKED_BY_INTRUDER, LDAPPermissionInfo.Access.write );
                ldapAttributes.put( ChaiConstant.ATTR_LDAP_LOGIN_INTRUDER_ATTEMPTS, LDAPPermissionInfo.Access.write );
                ldapAttributes.put( ChaiConstant.ATTR_LDAP_LOGIN_INTRUDER_RESET_TIME, LDAPPermissionInfo.Access.write );
                ldapAttributes.put( ChaiConstant.ATTR_LDAP_LOGIN_GRACE_LIMIT, LDAPPermissionInfo.Access.write );
                ldapAttributes.put( ChaiConstant.ATTR_LDAP_LOGIN_GRACE_REMAINING, LDAPPermissionInfo.Access.write );
                ldapAttributes.put( ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME, LDAPPermissionInfo.Access.read );

                for ( final Map.Entry<String, LDAPPermissionInfo.Access> entry : ldapAttributes.entrySet() )
                {
                    final String ldapAttribute = entry.getKey();
                    permissionRecords.add( new PermissionRecord(
                            ldapAttribute,
                            null,
                            null,
                            entry.getValue(),
                            LDAPPermissionInfo.Actor.proxy
                    ) );
                }
            }
        }

        if ( configuration.getLdapProfiles() != null && !configuration.getLdapProfiles().isEmpty() )
        {
            for ( final LdapProfile ldapProfile : configuration.getLdapProfiles().values() )
            {
                final List<String> autoAddObjectClasses = ldapProfile.readSettingAsStringArray( PwmSetting.AUTO_ADD_OBJECT_CLASSES );
                if ( autoAddObjectClasses != null && !autoAddObjectClasses.isEmpty() )
                {
                    permissionRecords.add( new PermissionRecord(
                            ChaiConstant.ATTR_LDAP_OBJECTCLASS,
                            PwmSetting.AUTO_ADD_OBJECT_CLASSES,
                            ldapProfile.getIdentifier(),
                            LDAPPermissionInfo.Access.write,
                            LDAPPermissionInfo.Actor.proxy
                    ) );
                }
            }
        }

        return permissionRecords;
    }

    @Value
    public static class PermissionRecord implements Serializable
    {
        private final String attribute;
        private final PwmSetting pwmSetting;
        private final String profile;
        private final LDAPPermissionInfo.Access access;
        private final LDAPPermissionInfo.Actor actor;
    }
}
