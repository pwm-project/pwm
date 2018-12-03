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

package password.pwm.util.cli.commands;

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.BooleanValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Import IDM silent properties installer file and create new configuration.
 */
public class ImportIDMConfigCommand extends AbstractCliCommand
{
    @Override
    void doCommand( )
            throws Exception
    {
        final File configFile = cliEnvironment.getConfigurationFile();

        if ( configFile.exists() )
        {
            out( "this command can not be run with an existing configuration in place.  Exiting..." );
            return;
        }

        final File inputFile = ( File ) cliEnvironment.getOptions().get( CliParameters.REQUIRED_EXISTING_INPUT_FILE.getName() );

        try
        {
            final IDMImporter importer = new IDMImporter( new FileInputStream( inputFile ) );
            final StoredConfigurationImpl storedConfiguration = importer.readConfiguration();

            try ( OutputStream outputStream = new FileOutputStream( configFile ) )
            {
                storedConfiguration.toXml( outputStream );
                out( "output configuration file " + configFile.getAbsolutePath() );
            }

        }
        catch ( Exception e )
        {
            out( "error during import: " + e.getMessage() );
        }
    }

    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ImportIDMConfig";
        cliParameters.description = "Import an IDM installer script and create a new configuration";
        cliParameters.options = Collections.singletonList( CliParameters.REQUIRED_EXISTING_INPUT_FILE );

        cliParameters.needsPwmApplication = true;
        cliParameters.needsLocalDB = false;
        cliParameters.readOnly = false;

        return cliParameters;
    }

    private enum IdmProperty
    {
        ID_VAULT_HOST,
        ID_VAULT_LDAPS_PORT,
        ID_VAULT_ADMIN_LDAP,
        ID_VAULT_PASSWORD,
        USER_CONTAINER,
        UA_ADMIN,

        SSO_SERVER_HOST,
        SSO_SERVER_SSL_PORT,
        SSO_SERVICE_PWD,

        CONFIGURATION_PWD,
    }

    private static class IDMImporter
    {
        private static final String LDAP_PROFILE = "default";
        private final Map<String, String> inputMap;

        IDMImporter( final InputStream inputStream ) throws IOException
        {
            final Properties inputProperties = new Properties();
            inputProperties.load( inputStream );
            final Map<String, String> inputMap = JavaHelper.propertiesToStringMap( inputProperties );
            stripValueDelimiters( inputMap );
            this.inputMap = inputMap;
        }

        StoredConfigurationImpl readConfiguration() throws PwmUnrecoverableException, PwmOperationalException
        {
            final StoredConfigurationImpl storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();
            storedConfiguration.initNewRandomSecurityKey();
            storedConfiguration.writeConfigProperty(
                    ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( true ) );
            storedConfiguration.writeConfigProperty(
                    ConfigurationProperty.CONFIG_EPOCH, String.valueOf( 0 ) );

            // static values
            storedConfiguration.writeSetting( PwmSetting.TEMPLATE_LDAP, new StringValue( "NOVL_IDM" ), null );
            storedConfiguration.writeSetting( PwmSetting.INTERFACE_THEME, new StringValue( "mdefault" ), null );
            storedConfiguration.writeSetting( PwmSetting.DISPLAY_HOME_BUTTON, new BooleanValue( false ), null );
            storedConfiguration.writeSetting( PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE, new BooleanValue( false ), null );
            storedConfiguration.writeSetting( PwmSetting.PASSWORD_REQUIRE_CURRENT, new BooleanValue( false ), null );
            storedConfiguration.writeSetting( PwmSetting.PASSWORD_POLICY_SOURCE, new StringValue( "LDAP" ), null );

            // ldap server
            storedConfiguration.writeSetting( PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE, makeLdapServerUrlValue(), null );
            storedConfiguration.writeSetting( PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE,
                    new PasswordValue( PasswordData.forStringValue( inputMap.get( IdmProperty.ID_VAULT_ADMIN_LDAP.name() ) ) ), null );
            storedConfiguration.writeSetting( PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE,
                    new PasswordValue( PasswordData.forStringValue( inputMap.get( IdmProperty.ID_VAULT_PASSWORD.name() ) ) ), null );
            storedConfiguration.writeSetting( PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE,
                    new StringArrayValue( Collections.singletonList( inputMap.get( IdmProperty.USER_CONTAINER.name() ) ) ), null );

            // oauth
            storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_LOGIN_URL, new StringValue( makeOAuthBaseUrl() + "/grant" ), null );
            storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_CODERESOLVE_URL, new StringValue( makeOAuthBaseUrl() + "/authcoderesolve" ), null );
            storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_ATTRIBUTES_URL, new StringValue( makeOAuthBaseUrl() + "/getattributes" ), null );
            storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_CLIENTNAME, new StringValue( "sspr" ), null );
            storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME, new StringValue( "name" ), null );
            storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_SECRET,
                    new PasswordValue( PasswordData.forStringValue( inputMap.get( IdmProperty.SSO_SERVICE_PWD.name() ) ) ), null );

            //urls
            storedConfiguration.writeSetting( PwmSetting.URL_FORWARD, makeForwardUrl(), null );
            storedConfiguration.writeSetting( PwmSetting.URL_LOGOUT, makeLogoutUrl(), null );
            storedConfiguration.writeSetting( PwmSetting.PWM_SITE_URL, makeSelfUrl(), null );
            storedConfiguration.writeSetting( PwmSetting.SECURITY_REDIRECT_WHITELIST, makeWhitelistUrl(), null );

            // admin settings
            storedConfiguration.writeSetting( PwmSetting.QUERY_MATCH_PWM_ADMIN, makeAdminPermissions(), null );
            storedConfiguration.setPassword( inputMap.get( IdmProperty.CONFIGURATION_PWD.name() ) );

            return storedConfiguration;
        }

        String makeOAuthBaseUrl()
        {
            return "https://" + inputMap.get( IdmProperty.SSO_SERVER_HOST.name() )
                    + ":" + inputMap.get( IdmProperty.SSO_SERVER_SSL_PORT.name() )
                    + "/osp/a/idm/auth/oauth2";
        }

        StringArrayValue makeWhitelistUrl()
        {
            return new StringArrayValue( Collections.singletonList( "https://" + inputMap.get( IdmProperty.SSO_SERVER_HOST.name() )
                    + ":" + inputMap.get( IdmProperty.SSO_SERVER_SSL_PORT.name() ) ) );
        }

        StoredValue makeSelfUrl()
        {
            return new StringValue( "https://" + inputMap.get( IdmProperty.SSO_SERVER_HOST.name() )
                    + ":" + inputMap.get( IdmProperty.SSO_SERVER_SSL_PORT.name() )
                    + "/sspr" );
        }

        StoredValue makeForwardUrl()
        {
            return new StringValue( "https://" + inputMap.get( IdmProperty.SSO_SERVER_HOST.name() )
                    + ":" + inputMap.get( IdmProperty.SSO_SERVER_SSL_PORT.name() )
                    + "/idmdash/#/landing" );
        }

        StoredValue makeLogoutUrl()
        {
            final String targetValue = "https://" + inputMap.get( IdmProperty.SSO_SERVER_HOST.name() )
                    + ":" + inputMap.get( IdmProperty.SSO_SERVER_SSL_PORT.name() )
                    + "/sspr";

            return new StringValue( "https://" + inputMap.get( IdmProperty.SSO_SERVER_HOST.name() )
                    + ":" + inputMap.get( IdmProperty.SSO_SERVER_SSL_PORT.name() )
                    + "/osp/a/idm/auth/app/logout?target="
                    + StringUtil.urlEncode( targetValue ) );
        }

        StoredValue makeLdapServerUrlValue( )
        {
            final String ldapUrl = "ldaps://" + inputMap.get( IdmProperty.ID_VAULT_HOST.name() )
                    + ":" + inputMap.get( IdmProperty.ID_VAULT_LDAPS_PORT.name() );
            return new StringArrayValue( Collections.singletonList( ldapUrl ) );
        }

        StoredValue makeAdminPermissions( )
        {
            final String filter = "(objectclass=*)";
            final List<UserPermission> permissions = new ArrayList<>();
            permissions.add( new UserPermission( UserPermission.Type.ldapQuery, LDAP_PROFILE, filter,
                    inputMap.get( IdmProperty.ID_VAULT_ADMIN_LDAP.name() ) ) );
            permissions.add( new UserPermission( UserPermission.Type.ldapQuery, LDAP_PROFILE, filter,
                    inputMap.get( IdmProperty.UA_ADMIN.name() ) ) );
            return new UserPermissionValue( permissions );
        }

        static void stripValueDelimiters( final Map<String, String> map )
        {
            final Pattern pattern = Pattern.compile( "^'|'$" );
            map.replaceAll( ( key, value ) -> pattern.matcher( value ).replaceAll( "" ) );
        }
    }
}
