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

package password.pwm.http.servlet.configguide;

import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.BooleanValue;
import password.pwm.config.value.ChallengeValue;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.util.PasswordData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.net.URI;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ConfigGuideForm
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigGuideForm.class );

    static final String LDAP_PROFILE_NAME = "default";
    public static final DomainID DOMAIN_ID = DomainID.DOMAIN_ID_DEFAULT;

    public static Map<ConfigGuideFormField, String> defaultForm( )
    {
        final Map<ConfigGuideFormField, String> defaultLdapForm = new EnumMap<>( ConfigGuideFormField.class );
        for ( final ConfigGuideFormField formParameter : ConfigGuideFormField.values() )
        {
            defaultLdapForm.put( formParameter, "" );
        }

        defaultLdapForm.put( ConfigGuideFormField.PARAM_LDAP_PORT, "636" );
        defaultLdapForm.put( ConfigGuideFormField.PARAM_LDAP_SECURE, "true" );
        defaultLdapForm.remove( ConfigGuideFormField.CHALLENGE_RESPONSE_DATA );

        return Collections.unmodifiableMap( defaultLdapForm );
    }


    private static void updateStoredConfigTemplateValue(
            final Map<ConfigGuideFormField, String> formData,
            final StoredConfigurationModifier modifier,
            final PwmSetting pwmSetting,
            final ConfigGuideFormField formField,
            final PwmSettingTemplate.Type type
    )
            throws PwmUnrecoverableException
    {
        final String formValue = formData.get( formField );
        if ( StringUtil.notEmpty( formValue ) )
        {
            final PwmSettingTemplate template = PwmSettingTemplate.templateForString( formValue, type );
            modifySetting( modifier, pwmSetting, null, new StringValue( template.toString() ) );
        }
    }

    public static StoredConfiguration generateStoredConfig(
            final ConfigGuideBean configGuideBean
    )
            throws PwmUnrecoverableException
    {

        final Map<ConfigGuideFormField, String> formData = configGuideBean.getFormData();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( StoredConfigurationFactory.newConfig() );

        // templates
        updateStoredConfigTemplateValue(
                formData,
                modifier,
                PwmSetting.TEMPLATE_LDAP,
                ConfigGuideFormField.PARAM_TEMPLATE_LDAP,
                PwmSettingTemplate.Type.LDAP_VENDOR );

        updateStoredConfigTemplateValue(
                formData,
                modifier,
                PwmSetting.TEMPLATE_STORAGE,
                ConfigGuideFormField.PARAM_TEMPLATE_STORAGE,
                PwmSettingTemplate.Type.STORAGE );

        updateStoredConfigTemplateValue(
                formData,
                modifier,
                PwmSetting.DB_VENDOR_TEMPLATE,
                ConfigGuideFormField.PARAM_DB_VENDOR,
                PwmSettingTemplate.Type.DB_VENDOR );

        // establish a default ldap profile

        modifySetting( modifier, PwmSetting.LDAP_PROFILE_LIST, null, StringArrayValue.create(
                Collections.singletonList( LDAP_PROFILE_NAME )
        ) );

        {
            final String newLdapURI = figureLdapUrlFromFormConfig( formData );
            final StringArrayValue newValue = StringArrayValue.create( Collections.singletonList( newLdapURI ) );
            modifySetting( modifier, PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE_NAME, newValue );
        }

        if ( configGuideBean.isUseConfiguredCerts() && !CollectionUtil.isEmpty( configGuideBean.getLdapCertificates() ) )
        {
            final StoredValue newStoredValue = X509CertificateValue.fromX509( configGuideBean.getLdapCertificates() );
            modifySetting( modifier, PwmSetting.LDAP_SERVER_CERTS, LDAP_PROFILE_NAME, newStoredValue );
        }

        {
            // proxy/admin account
            final String ldapAdminDN = formData.get( ConfigGuideFormField.PARAM_LDAP_PROXY_DN );
            final String ldapAdminPW = formData.get( ConfigGuideFormField.PARAM_LDAP_PROXY_PW );
            modifySetting( modifier, PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE_NAME, new StringValue( ldapAdminDN ) );
            final PasswordValue passwordValue = new PasswordValue( PasswordData.forStringValue( ldapAdminPW ) );
            modifySetting( modifier, PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE_NAME, passwordValue );
        }

        modifySetting( modifier, PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_NAME, StringArrayValue.create(
                Collections.singletonList( formData.get( ConfigGuideFormField.PARAM_LDAP_CONTEXT ) )
        ) );

        {
            final String ldapContext = formData.get( ConfigGuideFormField.PARAM_LDAP_CONTEXT );
            modifySetting( modifier, PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_NAME, StringArrayValue.create(
                    Collections.singletonList( ldapContext )
            ) );
        }

        {
            final boolean testuserEnabled = Boolean.parseBoolean( formData.get( ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED ) );
            if ( testuserEnabled )
            {
                final String ldapTestUserDN = formData.get( ConfigGuideFormField.PARAM_LDAP_TEST_USER );
                modifySetting( modifier, PwmSetting.LDAP_TEST_USER_DN, LDAP_PROFILE_NAME, new StringValue( ldapTestUserDN ) );
            }
            else
            {
                modifier.resetSetting( StoredConfigKey.forSetting( PwmSetting.LDAP_TEST_USER_DN, LDAP_PROFILE_NAME, DOMAIN_ID ), null );
            }
        }

        {
            // set admin query
            final String userDN = formData.get( ConfigGuideFormField.PARAM_LDAP_ADMIN_USER );
            final List<UserPermission> userPermissions = Collections.singletonList( UserPermission.builder()
                    .type( UserPermissionType.ldapUser )
                    .ldapBase( userDN )
                    .build() );
            modifySetting( modifier, PwmSetting.QUERY_MATCH_PWM_ADMIN, null, new UserPermissionValue( userPermissions ) );
        }

        {
            // database

            final String dbClass = formData.get( ConfigGuideFormField.PARAM_DB_CLASSNAME );
            modifySetting( modifier, PwmSetting.DATABASE_CLASS, null, new StringValue( dbClass ) );

            final String dbUrl = formData.get( ConfigGuideFormField.PARAM_DB_CONNECT_URL );
            modifySetting( modifier, PwmSetting.DATABASE_URL, null, new StringValue( dbUrl ) );

            final String dbUser = formData.get( ConfigGuideFormField.PARAM_DB_USERNAME );
            modifySetting( modifier, PwmSetting.DATABASE_USERNAME, null, new StringValue( dbUser ) );

            final String dbPassword = formData.get( ConfigGuideFormField.PARAM_DB_PASSWORD );
            final PasswordValue passwordValue = new PasswordValue( PasswordData.forStringValue( dbPassword ) );
            modifySetting( modifier, PwmSetting.DATABASE_PASSWORD, null, passwordValue );

            final FileValue jdbcDriver = configGuideBean.getDatabaseDriver();
            if ( jdbcDriver != null )
            {
                modifySetting( modifier, PwmSetting.DATABASE_JDBC_DRIVER, null, jdbcDriver );
            }
        }

        {
            //telemetry
            final boolean telemetryEnabled = Boolean.parseBoolean( formData.get( ConfigGuideFormField.PARAM_TELEMETRY_ENABLE ) );
            modifySetting( modifier, PwmSetting.PUBLISH_STATS_ENABLE, null, BooleanValue.of( telemetryEnabled ) );

            final String siteDescription = formData.get( ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION );
            modifySetting( modifier, PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION, null, new StringValue( siteDescription ) );
        }

        // cr policy
        if ( formData.containsKey( ConfigGuideFormField.CHALLENGE_RESPONSE_DATA ) )
        {
            final String stringValue = formData.get( ConfigGuideFormField.CHALLENGE_RESPONSE_DATA );
            final StoredValue challengeValue = ChallengeValue.factory().fromJson( PwmSetting.CHALLENGE_RANDOM_CHALLENGES, stringValue );
            modifySetting( modifier, PwmSetting.CHALLENGE_RANDOM_CHALLENGES, "default", challengeValue );
        }

        // set site url
        modifySetting( modifier, PwmSetting.PWM_SITE_URL, null, new StringValue( formData.get( ConfigGuideFormField.PARAM_APP_SITEURL ) ) );

        // enable debug mode
        modifySetting( modifier, PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS, null, BooleanValue.of( true ) );

        return modifier.newStoredConfiguration();
    }

    private static void modifySetting( final StoredConfigurationModifier modifier, final PwmSetting pwmSetting, final String profile, final StoredValue storedValue )
            throws PwmUnrecoverableException
    {
        final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, profile, DOMAIN_ID );
        modifier.writeSetting( key, storedValue, null );
    }


    static String figureLdapUrlFromFormConfig( final Map<ConfigGuideFormField, String> ldapForm )
    {
        final String ldapServerIP = ldapForm.get( ConfigGuideFormField.PARAM_LDAP_HOST );
        final String ldapServerPort = ldapForm.get( ConfigGuideFormField.PARAM_LDAP_PORT );
        final boolean ldapServerSecure = readCheckedFormField( ldapForm.get( ConfigGuideFormField.PARAM_LDAP_SECURE ) );

        return "ldap" + ( ldapServerSecure ? "s" : "" ) + "://" + ldapServerIP + ":" + ldapServerPort;
    }

    public static String figureLdapHostnameExample( final ConfigGuideBean configGuideBean )
    {
        try
        {
            final String uriString = getSettingExample( configGuideBean, PwmSetting.LDAP_SERVER_URLS );
            final URI uri = new URI( uriString );
            return uri.getHost();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error calculating ldap hostname example: " + e.getMessage() );
        }
        return "ldap.example.com";
    }

    public static boolean readCheckedFormField( final String value )
    {
        return "on".equalsIgnoreCase( value ) || "true".equalsIgnoreCase( value );
    }

    public static String getSettingExample( final ConfigGuideBean configGuideBean, final PwmSetting pwmSetting )
            throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = generateStoredConfig( configGuideBean );
        return pwmSetting.getExample( storedConfiguration.getTemplateSets().get( ConfigGuideForm.DOMAIN_ID ) );
    }
}
