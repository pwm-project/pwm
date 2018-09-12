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

package password.pwm.http.servlet.configguide;

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.BooleanValue;
import password.pwm.config.value.ChallengeValue;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigGuideForm
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigGuideForm.class );

    public static Map<ConfigGuideFormField, String> defaultForm( )
    {
        final Map<ConfigGuideFormField, String> defaultLdapForm = new HashMap<>();
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
            final StoredConfigurationImpl storedConfiguration,
            final PwmSetting pwmSetting,
            final ConfigGuideFormField formField,
            final PwmSettingTemplate.Type type
    )
            throws PwmUnrecoverableException
    {
        final String formValue = formData.get( formField );
        if ( !StringUtil.isEmpty( formValue ) )
        {
            final PwmSettingTemplate template = PwmSettingTemplate.templateForString( formValue, type );
            storedConfiguration.writeSetting( pwmSetting, null, new StringValue( template.toString() ), null );
        }
    }

    private static final String LDAP_PROFILE_NAME = "default";

    public static StoredConfigurationImpl generateStoredConfig(
            final ConfigGuideBean configGuideBean
    )
            throws PwmUnrecoverableException
    {

        final Map<ConfigGuideFormField, String> formData = configGuideBean.getFormData();
        final StoredConfigurationImpl storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();

        // templates
        updateStoredConfigTemplateValue(
                formData,
                storedConfiguration,
                PwmSetting.TEMPLATE_LDAP,
                ConfigGuideFormField.PARAM_TEMPLATE_LDAP,
                PwmSettingTemplate.Type.LDAP_VENDOR );

        updateStoredConfigTemplateValue(
                formData,
                storedConfiguration,
                PwmSetting.TEMPLATE_STORAGE,
                ConfigGuideFormField.PARAM_TEMPLATE_STORAGE,
                PwmSettingTemplate.Type.STORAGE );

        updateStoredConfigTemplateValue(
                formData,
                storedConfiguration,
                PwmSetting.DB_VENDOR_TEMPLATE,
                ConfigGuideFormField.PARAM_DB_VENDOR,
                PwmSettingTemplate.Type.DB_VENDOR );

        // establish a default ldap profile
        storedConfiguration.writeSetting( PwmSetting.LDAP_PROFILE_LIST, null, new StringArrayValue(
                Collections.singletonList( LDAP_PROFILE_NAME )
        ), null );

        {
            final String newLdapURI = figureLdapUrlFromFormConfig( formData );
            final StringArrayValue newValue = new StringArrayValue( Collections.singletonList( newLdapURI ) );
            storedConfiguration.writeSetting( PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE_NAME, newValue, null );
        }

        if ( configGuideBean.isUseConfiguredCerts() )
        {
            final StoredValue newStoredValue = new X509CertificateValue( configGuideBean.getLdapCertificates() );
            storedConfiguration.writeSetting( PwmSetting.LDAP_SERVER_CERTS, LDAP_PROFILE_NAME, newStoredValue, null );
        }

        {
            // proxy/admin account
            final String ldapAdminDN = formData.get( ConfigGuideFormField.PARAM_LDAP_PROXY_DN );
            final String ldapAdminPW = formData.get( ConfigGuideFormField.PARAM_LDAP_PROXY_PW );
            storedConfiguration.writeSetting( PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE_NAME, new StringValue( ldapAdminDN ), null );
            final PasswordValue passwordValue = new PasswordValue( PasswordData.forStringValue( ldapAdminPW ) );
            storedConfiguration.writeSetting( PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE_NAME, passwordValue, null );
        }

        storedConfiguration.writeSetting( PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_NAME, new StringArrayValue(
                Collections.singletonList( formData.get( ConfigGuideFormField.PARAM_LDAP_CONTEXT ) )
        ), null );

        {
            final String ldapContext = formData.get( ConfigGuideFormField.PARAM_LDAP_CONTEXT );
            storedConfiguration.writeSetting( PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE_NAME, new StringArrayValue(
                    Collections.singletonList( ldapContext )
            ), null );
        }

        {
            final boolean testuserEnabled = Boolean.parseBoolean( formData.get( ConfigGuideFormField.PARAM_LDAP_TEST_USER_ENABLED ) );
            if ( testuserEnabled )
            {
                final String ldapTestUserDN = formData.get( ConfigGuideFormField.PARAM_LDAP_TEST_USER );
                storedConfiguration.writeSetting( PwmSetting.LDAP_TEST_USER_DN, LDAP_PROFILE_NAME, new StringValue( ldapTestUserDN ), null );
            }
            else
            {
                storedConfiguration.resetSetting( PwmSetting.LDAP_TEST_USER_DN, LDAP_PROFILE_NAME, null );
            }
        }

        {
            // set admin query
            final String groupDN = formData.get( ConfigGuideFormField.PARAM_LDAP_ADMIN_GROUP );
            final List<UserPermission> userPermissions = Collections.singletonList( new UserPermission( UserPermission.Type.ldapGroup, null, null, groupDN ) );
            storedConfiguration.writeSetting( PwmSetting.QUERY_MATCH_PWM_ADMIN, new UserPermissionValue( userPermissions ), null );
        }

        {
            // database

            final String dbClass = formData.get( ConfigGuideFormField.PARAM_DB_CLASSNAME );
            storedConfiguration.writeSetting( PwmSetting.DATABASE_CLASS, null, new StringValue( dbClass ), null );

            final String dbUrl = formData.get( ConfigGuideFormField.PARAM_DB_CONNECT_URL );
            storedConfiguration.writeSetting( PwmSetting.DATABASE_URL, null, new StringValue( dbUrl ), null );

            final String dbUser = formData.get( ConfigGuideFormField.PARAM_DB_USERNAME );
            storedConfiguration.writeSetting( PwmSetting.DATABASE_USERNAME, null, new StringValue( dbUser ), null );

            final String dbPassword = formData.get( ConfigGuideFormField.PARAM_DB_PASSWORD );
            final PasswordValue passwordValue = new PasswordValue( PasswordData.forStringValue( dbPassword ) );
            storedConfiguration.writeSetting( PwmSetting.DATABASE_PASSWORD, null, passwordValue, null );

            final FileValue jdbcDriver = configGuideBean.getDatabaseDriver();
            if ( jdbcDriver != null )
            {
                storedConfiguration.writeSetting( PwmSetting.DATABASE_JDBC_DRIVER, null, jdbcDriver, null );
            }
        }

        {
            //telemetry
            final boolean telemetryEnabled = Boolean.parseBoolean( formData.get( ConfigGuideFormField.PARAM_TELEMETRY_ENABLE ) );
            storedConfiguration.writeSetting( PwmSetting.PUBLISH_STATS_ENABLE, null, new BooleanValue( telemetryEnabled ), null );

            final String siteDescription = formData.get( ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION );
            storedConfiguration.writeSetting( PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION, null, new StringValue( siteDescription ), null );
        }

        // cr policy
        if ( formData.containsKey( ConfigGuideFormField.CHALLENGE_RESPONSE_DATA ) )
        {
            final String stringValue = formData.get( ConfigGuideFormField.CHALLENGE_RESPONSE_DATA );
            final StoredValue challengeValue = ChallengeValue.factory().fromJson( stringValue );
            storedConfiguration.writeSetting( PwmSetting.CHALLENGE_RANDOM_CHALLENGES, "default", challengeValue, null );
        }

        // set site url
        storedConfiguration.writeSetting( PwmSetting.PWM_SITE_URL, new StringValue( formData.get( ConfigGuideFormField.PARAM_APP_SITEURL ) ), null );

        // enable debug mode
        storedConfiguration.writeSetting( PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS, null, new BooleanValue( true ), null );

        return storedConfiguration;
    }

    static String figureLdapUrlFromFormConfig( final Map<ConfigGuideFormField, String> ldapForm )
    {
        final String ldapServerIP = ldapForm.get( ConfigGuideFormField.PARAM_LDAP_HOST );
        final String ldapServerPort = ldapForm.get( ConfigGuideFormField.PARAM_LDAP_PORT );
        final boolean ldapServerSecure = "true".equalsIgnoreCase( ldapForm.get( ConfigGuideFormField.PARAM_LDAP_SECURE ) );

        return "ldap" + ( ldapServerSecure ? "s" : "" ) + "://" + ldapServerIP + ":" + ldapServerPort;
    }

    public static String figureLdapHostnameExample( final ConfigGuideBean configGuideBean )
    {
        try
        {
            final StoredConfigurationImpl storedConfiguration = generateStoredConfig( configGuideBean );
            final String uriString = PwmSetting.LDAP_SERVER_URLS.getExample( storedConfiguration.getTemplateSet() );
            final URI uri = new URI( uriString );
            return uri.getHost();
        }
        catch ( Exception e )
        {
            LOGGER.error( "error calculating ldap hostname example: " + e.getMessage() );
        }
        return "ldap.example.com";
    }
}
