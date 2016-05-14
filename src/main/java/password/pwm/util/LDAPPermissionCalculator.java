/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.util;

import com.novell.ldapchai.ChaiConstant;
import password.pwm.config.*;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.queue.SmsQueueManager;

import java.io.Serializable;
import java.util.*;

public class LDAPPermissionCalculator implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LDAPPermissionCalculator.class);

    private final StoredConfigurationImpl storedConfiguration;
    private final Configuration configuration;
    private final Collection<PermissionRecord> permissionRecords;

    public LDAPPermissionCalculator(final StoredConfigurationImpl storedConfiguration) throws PwmUnrecoverableException {
        this.storedConfiguration = storedConfiguration;
        this.configuration = new Configuration(storedConfiguration);
        permissionRecords = figureRecords(storedConfiguration);
    }

    public Collection<PermissionRecord> getPermissionRecords() {
        return permissionRecords;
    }

    public Map<String,Map<LDAPPermissionInfo.Access,List<PermissionRecord>>> getPermissionsByActor(final LDAPPermissionInfo.Actor actor) {
        final Map<String,Map<LDAPPermissionInfo.Access,List<PermissionRecord>>> returnObj = new TreeMap<>();
        for (final PermissionRecord permissionRecord : getPermissionRecords()) {
            if (permissionRecord.getActor() == actor) {
                if (!returnObj.containsKey(permissionRecord.getAttribute())) {
                    returnObj.put(permissionRecord.getAttribute(), new TreeMap<LDAPPermissionInfo.Access,List<PermissionRecord>>());
                }
                if (!returnObj.get(permissionRecord.getAttribute()).containsKey(permissionRecord.getAccess())) {
                    returnObj.get(permissionRecord.getAttribute()).put(permissionRecord.getAccess(), new ArrayList<PermissionRecord>());
                }
                returnObj.get(permissionRecord.getAttribute()).get(permissionRecord.getAccess()).add(permissionRecord);
            }
        }
        return Collections.unmodifiableMap(returnObj);
    }

    private Collection<PermissionRecord> figureRecords(final StoredConfigurationImpl storedConfiguration) throws PwmUnrecoverableException {
        final List<PermissionRecord> permissionRecords = new ArrayList<>();

        for (final PwmSetting pwmSetting : PwmSetting.values()) {
            if (pwmSetting.getCategory().hasProfiles()) {
                final List<String> profiles = StoredConfigurationUtil.profilesForSetting(pwmSetting, storedConfiguration);
                for (final String profile : profiles) {
                    permissionRecords.addAll(figureRecord(pwmSetting, profile));
                }
            } else {
                permissionRecords.addAll(figureRecord(pwmSetting, null));
            }
        }

        permissionRecords.addAll(permissionsForUserPassword());
        permissionRecords.addAll(figureStaticRecords());
        return permissionRecords;
    }

    private Collection<PermissionRecord> figureRecord(PwmSetting pwmSetting, final String profile) throws PwmUnrecoverableException {
        final List<PermissionRecord> permissionRecords = new ArrayList<>();
        final Collection<LDAPPermissionInfo> permissionInfos = figurePermissionInfos(pwmSetting, profile);
        if (permissionInfos == null) {
            return Collections.emptyList();
        }
        for (final LDAPPermissionInfo permissionInfo : permissionInfos) {
            switch (pwmSetting.getSyntax()) {
                case STRING:
                {
                    final String attrName = (String)storedConfiguration.readSetting(pwmSetting, profile).toNativeObject();
                    if (attrName != null && !attrName.trim().isEmpty()) {
                        permissionRecords.add(new PermissionRecord(attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor()));
                    }
                }
                break;

                case FORM:
                {
                    final List<FormConfiguration> formItems = (List<FormConfiguration>)storedConfiguration.readSetting(pwmSetting, profile).toNativeObject();
                    if (formItems != null) {
                        for (final FormConfiguration formConfiguration : formItems) {
                            final String attrName = formConfiguration.getName();
                            if (attrName != null && !attrName.trim().isEmpty()) {
                                permissionRecords.add(new PermissionRecord(attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor()));
                            }
                        }
                    }
                }
                break;

                case ACTION:
                {
                    final List<ActionConfiguration> actionItems = (List<ActionConfiguration>)storedConfiguration.readSetting(pwmSetting, profile).toNativeObject();
                    if (actionItems != null) {
                        for (final ActionConfiguration actionConfiguration : actionItems) {
                            if (actionConfiguration.getType() == ActionConfiguration.Type.ldap) {
                                final String attrName = actionConfiguration.getAttributeName();
                                if (attrName != null && !attrName.trim().isEmpty()) {
                                    permissionRecords.add(new PermissionRecord(attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor()));
                                }
                            }
                        }
                    }
                }
                break;

                case STRING_ARRAY:
                {
                    final List<String> strings = (List<String>) storedConfiguration.readSetting(pwmSetting, profile).toNativeObject();
                    for (final String attrName : strings) {
                        if (attrName != null && !attrName.trim().isEmpty()) {
                            permissionRecords.add(new PermissionRecord(attrName, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor()));
                        }
                    }
                }
                break;

                case USER_PERMISSION:
                {
                    final List<UserPermission> userPermissions = (List<UserPermission>) storedConfiguration.readSetting(pwmSetting, profile).toNativeObject();
                    if (configuration.getLdapProfiles() != null && !configuration.getLdapProfiles().isEmpty()) {
                        for (LdapProfile ldapProfile : configuration.getLdapProfiles().values()) {
                            final String groupAttribute = ldapProfile.readSettingAsString(PwmSetting.LDAP_USER_GROUP_ATTRIBUTE);
                            if (groupAttribute != null && !groupAttribute.trim().isEmpty()) {
                                for (final UserPermission userPermission : userPermissions) {
                                    if (userPermission.getType() == UserPermission.Type.ldapGroup) {
                                        permissionRecords.add(new PermissionRecord(groupAttribute, pwmSetting, profile, permissionInfo.getAccess(), permissionInfo.getActor()));
                                    }
                                }
                            }
                        }
                    }
                }
                break;

                default:
                {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"no ldap permission record reader handler for setting " + pwmSetting.getKey()));
                }

            }
        }
        return permissionRecords;
    }

    private Collection<LDAPPermissionInfo> figurePermissionInfos(final PwmSetting pwmSetting, final String profile) {
        switch (pwmSetting.getCategory()) {
            case PEOPLE_SEARCH:
            {
                if (!(Boolean)storedConfiguration.readSetting(PwmSetting.PEOPLE_SEARCH_ENABLE).toNativeObject()) {
                    return Collections.emptyList();
                }
                final boolean proxyOverride = (Boolean)storedConfiguration.readSetting(PwmSetting.PEOPLE_SEARCH_USE_PROXY, profile).toNativeObject()
                        || (Boolean)storedConfiguration.readSetting(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC, profile).toNativeObject();

                if (proxyOverride) {
                    final Collection<LDAPPermissionInfo> configuredRecords = pwmSetting.getLDAPPermissionInfo();
                    final Collection<LDAPPermissionInfo> returnRecords = new ArrayList<>();
                    for (final LDAPPermissionInfo ldapPermissionInfo : configuredRecords) {
                        returnRecords.add(new LDAPPermissionInfo(ldapPermissionInfo.getAccess(), LDAPPermissionInfo.Actor.proxy));
                    }
                    return returnRecords;
                }
            }
            break;

            case GUEST:
            {
                if (!(Boolean)storedConfiguration.readSetting(PwmSetting.GUEST_ENABLE).toNativeObject()) {
                    return Collections.emptyList();
                }
            }
            break;

            case UPDATE:
            case UPDATE_PROFILE:
            case UPDATE_SETTINGS:
            {
                if (!(Boolean)storedConfiguration.readSetting(PwmSetting.UPDATE_PROFILE_ENABLE).toNativeObject()) {
                    return Collections.emptyList();
                }
            }
            break;

            case FORGOTTEN_USERNAME:
            {
                if (!(Boolean)storedConfiguration.readSetting(PwmSetting.FORGOTTEN_USERNAME_ENABLE).toNativeObject()) {
                    return Collections.emptyList();
                }
            }
            break;

            case NEWUSER:
            case NEWUSER_PROFILE:
            case NEWUSER_SETTINGS:
            {
                if (!(Boolean)storedConfiguration.readSetting(PwmSetting.NEWUSER_ENABLE).toNativeObject()) {
                    return Collections.emptyList();
                }
            }
            break;

            case ACTIVATION:
            {
                if (!(Boolean)storedConfiguration.readSetting(PwmSetting.ACTIVATE_USER_ENABLE).toNativeObject()) {
                    return Collections.emptyList();
                }
            }
            break;

            case HELPDESK_PROFILE:
            {
                if (!(Boolean)storedConfiguration.readSetting(PwmSetting.HELPDESK_ENABLE).toNativeObject()) {
                    return Collections.emptyList();
                }
                if ((Boolean)storedConfiguration.readSetting(PwmSetting.HELPDESK_USE_PROXY, profile).toNativeObject()) {
                    final Collection<LDAPPermissionInfo> configuredRecords = pwmSetting.getLDAPPermissionInfo();
                    final Collection<LDAPPermissionInfo> returnRecords = new ArrayList<>();
                    for (final LDAPPermissionInfo ldapPermissionInfo : configuredRecords) {
                        returnRecords.add(new LDAPPermissionInfo(ldapPermissionInfo.getAccess(), LDAPPermissionInfo.Actor.proxy));
                    }
                    return returnRecords;
                }

            }
            break;
        }

        switch (pwmSetting) {
            case CHALLENGE_USER_ATTRIBUTE:
            {
                final Set<DataStorageMethod> storageMethods = new HashSet<>();
                storageMethods.addAll(configuration.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE));
                storageMethods.addAll(configuration.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE));
                if (!storageMethods.contains(DataStorageMethod.LDAP)) {
                    return Collections.emptyList();
                }

            }
            break;

            case OTP_SECRET_LDAP_ATTRIBUTE:
            {
                if (!configuration.readSettingAsBoolean(PwmSetting.OTP_ENABLED)) {
                    return Collections.emptyList();
                }
            }
            break;

            case SMS_USER_PHONE_ATTRIBUTE:
            {
                if (!SmsQueueManager.smsIsConfigured(configuration)) {
                    return Collections.emptyList();
                }
            }
            break;
        }

        return pwmSetting.getLDAPPermissionInfo();
    }

    private Collection<PermissionRecord> permissionsForUserPassword() {
        final String userPasswordAttributeName = LocaleHelper.getLocalizedMessage("Label_UserPasswordAttribute",null,Config.class);
        final Collection<PermissionRecord> records = new ArrayList<>();

        // user set password
        records.add(new PermissionRecord(userPasswordAttributeName, null, null, LDAPPermissionInfo.Access.write, LDAPPermissionInfo.Actor.self));

        // proxy user set password
        if (configuration.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
            final Collection<PwmSettingTemplate> templates = configuration.getTemplate().getTemplates();
            if (templates.contains(PwmSettingTemplate.NOVL) || templates.contains(PwmSettingTemplate.NOVL_IDM)) {
                records.add(new PermissionRecord(userPasswordAttributeName, PwmSetting.FORGOTTEN_PASSWORD_ENABLE, null, LDAPPermissionInfo.Access.read, LDAPPermissionInfo.Actor.proxy));
            } else {
                records.add(new PermissionRecord(userPasswordAttributeName, PwmSetting.FORGOTTEN_PASSWORD_ENABLE, null, LDAPPermissionInfo.Access.write, LDAPPermissionInfo.Actor.proxy));
            }
        }

        if (configuration.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE)) {
            records.add(new PermissionRecord(userPasswordAttributeName, PwmSetting.HELPDESK_ENABLE, null, LDAPPermissionInfo.Access.write, LDAPPermissionInfo.Actor.helpdesk));
        }

        if (configuration.readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) {
            records.add(new PermissionRecord(userPasswordAttributeName, PwmSetting.GUEST_ENABLE, null, LDAPPermissionInfo.Access.write, LDAPPermissionInfo.Actor.guestManager));
        }

        if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            records.add(new PermissionRecord(userPasswordAttributeName, PwmSetting.NEWUSER_ENABLE, null, LDAPPermissionInfo.Access.write, LDAPPermissionInfo.Actor.proxy));
        }


        return records;
    }

    final private static Set<PwmSettingTemplate> EDIR_INTERESTED_TEMPLATES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            new PwmSettingTemplate[]{ PwmSettingTemplate.NOVL, PwmSettingTemplate.NOVL_IDM}
    )));

    private Collection<PermissionRecord> figureStaticRecords() {

        final List<PermissionRecord> permissionRecords = new ArrayList<>();

        final PwmSettingTemplateSet templateSet =  storedConfiguration.getTemplateSet();

        { //

            if (!Collections.disjoint(templateSet.getTemplates(), EDIR_INTERESTED_TEMPLATES)) {
                if (configuration.readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) {
                    final String[] ldapAttributes = new String[] {
                            ChaiConstant.ATTR_LDAP_LOCKED_BY_INTRUDER,
                            ChaiConstant.ATTR_LDAP_LOGIN_INTRUDER_ATTEMPTS,
                            ChaiConstant.ATTR_LDAP_LOGIN_INTRUDER_RESET_TIME,
                            ChaiConstant.ATTR_LDAP_LOGIN_GRACE_LIMIT,
                            ChaiConstant.ATTR_LDAP_LOGIN_GRACE_REMAINING,
                    };

                    for (final String ldapAttribute : ldapAttributes) {
                        permissionRecords.add(new PermissionRecord(
                                ldapAttribute,
                                PwmSetting.FORGOTTEN_PASSWORD_ENABLE,
                                null,
                                LDAPPermissionInfo.Access.write,
                                LDAPPermissionInfo.Actor.proxy
                        ));
                    }
                }
            }
        }

        return permissionRecords;
    }

    public static class PermissionRecord implements Serializable {
        private final String attribute;
        private final PwmSetting pwmSetting;
        private final String profile;
        private final LDAPPermissionInfo.Access access;
        private final LDAPPermissionInfo.Actor actor;

        public PermissionRecord(String attribute, PwmSetting pwmSetting, String profile, LDAPPermissionInfo.Access access, LDAPPermissionInfo.Actor actor) {
            this.attribute = attribute;
            this.pwmSetting = pwmSetting;
            this.profile = profile;
            this.access = access;
            this.actor = actor;
        }

        public String getAttribute() {
            return attribute;
        }

        public PwmSetting getPwmSetting() {
            return pwmSetting;
        }

        public String getProfile() {
            return profile;
        }

        public LDAPPermissionInfo.Access getAccess() {
            return access;
        }

        public LDAPPermissionInfo.Actor getActor() {
            return actor;
        }
    }
}
