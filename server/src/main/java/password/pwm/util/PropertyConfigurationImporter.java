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

package password.pwm.util;

import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.BooleanValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.X509Utils;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

public class PropertyConfigurationImporter
{
    private static final ProfileID LDAP_PROFILE = ProfileID.PROFILE_ID_DEFAULT;
    private static final DomainID DOMAIN_ID = DomainID.DOMAIN_ID_DEFAULT;


    private Map<String, String> inputMap;

    public enum PropertyKey
    {
        TEMPLATE_LDAP( "NOVL_IDM" ),
        DISPLAY_THEME( null ),

        ID_VAULT_HOST( null ),
        ID_VAULT_LDAPS_PORT( "636" ),
        ID_VAULT_ADMIN_LDAP( null ),
        ID_VAULT_PASSWORD( null ),
        UA_SERVER_HOST( null ),
        UA_SERVER_SSL_PORT( "443" ),
        UA_ADMIN( null ),
        RPT_ADMIN( null ),

        SSPR_SERVER_HOST( null ),
        SSPR_SERVER_SSL_PORT( "443" ),
        USER_CONTAINER( null ),
        SSO_SERVER_HOST( null ),
        SSO_SERVER_SSL_PORT( "443" ),
        SSO_SERVICE_PWD( null ),

        CONFIGURATION_PWD( null ),

        LDAP_SERVERCERTS( null ),
        OAUTH_IDSERVER_SERVERCERTS( null ),
        AUDIT_SERVERCERTS( null ),;

        private final String defaultValue;

        PropertyKey( final String defaultValue )
        {
            this.defaultValue = defaultValue;
        }

        public String getDefaultValue()
        {
            return defaultValue;
        }
    }

    public PropertyConfigurationImporter()
    {
    }

    private void readInputFile( final InputStream propertiesInput )
            throws IOException
    {
        final Properties inputProperties = new Properties( );
        inputProperties.load( propertiesInput );
        final Map<String, String> inputMap = JavaHelper.propertiesToStringMap( inputProperties );
        stripValueDelimiters( inputMap );
        this.inputMap = inputMap;
    }

    public StoredConfiguration readConfiguration( final InputStream propertiesInput )
            throws PwmException, IOException
    {
        readInputFile( propertiesInput );


        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( StoredConfigurationFactory.newConfig( ) );
        StoredConfigurationUtil.initNewRandomSecurityKey( modifier );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( false ) );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_EPOCH, String.valueOf( 0 ) );
        modifier.writeConfigProperty(
                ConfigurationProperty.IMPORT_LDAP_CERTIFICATES, Boolean.toString( true ) );

        // static values
        modifySetting( modifier, PwmSetting.TEMPLATE_LDAP, null, new StringValue(
                inputMap.getOrDefault( PropertyKey.TEMPLATE_LDAP.name( ), PropertyKey.TEMPLATE_LDAP.getDefaultValue() ) ) );
        if ( inputMap.containsKey( PropertyKey.DISPLAY_THEME.name( ) ) )
        {
            modifySetting( modifier, PwmSetting.PASSWORD_POLICY_SOURCE, null, new StringValue(
                    inputMap.get( PropertyKey.DISPLAY_THEME.name( ) ) ) );
        }

        modifySetting( modifier, PwmSetting.DISPLAY_HOME_BUTTON, null, BooleanValue.of( false ) );
        modifySetting( modifier, PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE, null, BooleanValue.of( false ) );
        modifySetting( modifier, PwmSetting.PASSWORD_REQUIRE_CURRENT, null, BooleanValue.of( false ) );
        modifySetting( modifier, PwmSetting.PASSWORD_POLICY_SOURCE, null, new StringValue( "LDAP" ) );
        modifySetting( modifier, PwmSetting.CERTIFICATE_VALIDATION_MODE, null, new StringValue( "CA_ONLY" ) );
        {
            final String notes = "Configuration generated via properties import at " + StringUtil.toIsoDate( Instant.now( ) );
            modifySetting( modifier, PwmSetting.NOTES, null, new StringValue( notes ) );
        }

        // ldap server
        modifySetting( modifier, PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE, makeLdapServerUrlValue( ) );
        modifySetting( modifier, PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE,
                new StringValue( inputMap.get( PropertyKey.ID_VAULT_ADMIN_LDAP.name( ) ) ) );
        modifySetting( modifier, PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE,
                new PasswordValue( PasswordData.forStringValue( inputMap.get( PropertyKey.ID_VAULT_PASSWORD.name( ) ) ) ) );
        modifySetting( modifier, PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE,
                StringArrayValue.create( Collections.singletonList( inputMap.get( PropertyKey.USER_CONTAINER.name( ) ) ) ) );

        // oauth
        modifySetting( modifier, PwmSetting.OAUTH_ID_LOGIN_URL, null, new StringValue( makeOAuthBaseUrl( ) + "/grant" ) );
        modifySetting( modifier, PwmSetting.OAUTH_ID_CODERESOLVE_URL, null, new StringValue( makeOAuthBaseUrl( ) + "/authcoderesolve" ) );
        modifySetting( modifier, PwmSetting.OAUTH_ID_ATTRIBUTES_URL, null, new StringValue( makeOAuthBaseUrl( ) + "/getattributes" ) );
        modifySetting( modifier, PwmSetting.OAUTH_ID_CLIENTNAME, null, new StringValue( "sspr" ) );
        modifySetting( modifier, PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME, null, new StringValue( "name" ) );
        modifySetting( modifier, PwmSetting.OAUTH_ID_SECRET, null,
                new PasswordValue( PasswordData.forStringValue( inputMap.get( PropertyKey.SSO_SERVICE_PWD.name( ) ) ) ) );

        //urls
        modifySetting( modifier, PwmSetting.URL_FORWARD, null, makeForwardUrl( ) );
        modifySetting( modifier, PwmSetting.URL_LOGOUT, null, makeLogoutUrl( ) );
        modifySetting( modifier, PwmSetting.PWM_SITE_URL, null, makeSelfUrl( ) );
        modifySetting( modifier, PwmSetting.SECURITY_REDIRECT_WHITELIST, null, makeWhitelistUrl( ) );

        // admin settings
        modifySetting( modifier, PwmSetting.QUERY_MATCH_PWM_ADMIN, null, makeAdminPermissions( ) );
        StoredConfigurationUtil.setPassword( modifier, inputMap.get( PropertyKey.CONFIGURATION_PWD.name( ) ) );

        // certificates
        {
            final Optional<Collection<X509Certificate>> optionalCert = readCertificate( PropertyKey.LDAP_SERVERCERTS );
            if ( optionalCert.isPresent( ) )
            {
                modifySetting( modifier, PwmSetting.LDAP_SERVER_CERTS, LDAP_PROFILE, X509CertificateValue.fromX509( optionalCert.get( ) ) );
            }
        }
        {
            final Optional<Collection<X509Certificate>> optionalCert = readCertificate( PropertyKey.AUDIT_SERVERCERTS );
            if ( optionalCert.isPresent( ) )
            {
                modifySetting( modifier, PwmSetting.AUDIT_SYSLOG_CERTIFICATES, null, X509CertificateValue.fromX509( optionalCert.get( ) ) );
            }
        }
        {
            final Optional<Collection<X509Certificate>> optionalCert = readCertificate( PropertyKey.OAUTH_IDSERVER_SERVERCERTS );
            if ( optionalCert.isPresent( ) )
            {
                modifySetting( modifier, PwmSetting.OAUTH_ID_CERTIFICATE, null, X509CertificateValue.fromX509( optionalCert.get( ) ) );
            }
        }


        return modifier.newStoredConfiguration();
    }

    private void modifySetting( final StoredConfigurationModifier modifier, final PwmSetting pwmSetting, final ProfileID profile, final StoredValue storedValue )
            throws PwmUnrecoverableException
    {
        final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, profile, DOMAIN_ID );
        modifier.writeSetting( key, storedValue, null );
    }

    private String makeOAuthBaseUrl( )
    {
        return "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.getOrDefault( PropertyKey.SSO_SERVER_SSL_PORT.name( ), PropertyKey.SSO_SERVER_SSL_PORT.getDefaultValue() )
                + "/osp/a/idm/auth/oauth2";
    }

    private StringArrayValue makeWhitelistUrl( )
    {
        return StringArrayValue.create( Collections.singletonList( "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.getOrDefault( PropertyKey.SSO_SERVER_SSL_PORT.name( ), PropertyKey.SSO_SERVER_SSL_PORT.getDefaultValue() ) ) );
    }

    private StoredValue makeSelfUrl( )
    {
        return new StringValue( "https://" + inputMap.get( PropertyKey.SSPR_SERVER_HOST.name( ) )
                + ":" + inputMap.getOrDefault( PropertyKey.SSPR_SERVER_SSL_PORT.name( ), PropertyKey.SSPR_SERVER_SSL_PORT.getDefaultValue() )
                + "/sspr" );
    }

    private StoredValue makeForwardUrl( )
    {
        return new StringValue( "https://" + inputMap.get( PropertyKey.UA_SERVER_HOST.name( ) )
                + ":" + inputMap.getOrDefault( PropertyKey.UA_SERVER_SSL_PORT.name( ), PropertyKey.UA_SERVER_SSL_PORT.getDefaultValue() )
                + "/idmdash/#/landing" );
    }

    private StoredValue makeLogoutUrl( )
    {
        final String targetValue = makeSelfUrl().toNativeObject().toString();

        return new StringValue( "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.getOrDefault( PropertyKey.SSO_SERVER_SSL_PORT.name( ), PropertyKey.SSO_SERVER_SSL_PORT.getDefaultValue() )
                + "/osp/a/idm/auth/app/logout?target="
                + StringUtil.urlEncode( targetValue ) );
    }

    private StoredValue makeLdapServerUrlValue( )
    {
        final String ldapUrl = "ldaps://" + inputMap.get( PropertyKey.ID_VAULT_HOST.name( ) )
                + ":" + inputMap.getOrDefault( PropertyKey.ID_VAULT_LDAPS_PORT.name( ), PropertyKey.ID_VAULT_LDAPS_PORT.getDefaultValue() );
        return StringArrayValue.create( Collections.singletonList( ldapUrl ) );
    }

    private StoredValue makeAdminPermissions( )
    {
        final List<PropertyKey> interestedProperties = new ArrayList<>();
        interestedProperties.add( PropertyKey.ID_VAULT_ADMIN_LDAP );
        interestedProperties.add( PropertyKey.UA_ADMIN );
        interestedProperties.add( PropertyKey.RPT_ADMIN );

        final String filter = "(objectclass=*)";
        final List<UserPermission> permissions = new ArrayList<>( );

        for ( final PropertyKey propertyKey : interestedProperties )
        {
            final String value = inputMap.get( propertyKey.name() );
            if ( StringUtil.notEmpty( value ) )
            {
                permissions.add( new UserPermission(
                        UserPermissionType.ldapQuery,
                        LDAP_PROFILE,
                        filter,
                        value ) );
            }
        }

        return new UserPermissionValue( permissions );
    }

    private void stripValueDelimiters( final Map<String, String> map )
    {
        final Pattern pattern = Pattern.compile( "^'|'$|^\"|\"$" );
        map.replaceAll( ( key, value ) -> pattern.matcher( value ).replaceAll( "" ) );
    }

    private Optional<Collection<X509Certificate>> readCertificate(
            final PropertyKey propertyKey
    )
            throws IOException
    {
        final String base64Cert = inputMap.get( propertyKey.name( ) );
        if ( StringUtil.notEmpty( base64Cert ) )
        {
            final List<X509Certificate> returnCerts = new ArrayList<>( );
            for ( final String splitB64Cert : StringUtil.splitAndTrim( base64Cert, "," ) )
            {
                try
                {
                    final X509Certificate cert = X509Utils.certificateFromBase64( splitB64Cert );
                    if ( cert != null )
                    {
                        returnCerts.add( cert );
                    }

                }
                catch ( final Exception e )
                {
                    throw new IOException( "error importing key " + propertyKey.name() + ", error: " + e.getMessage() );
                }
            }
            return Optional.of( returnCerts );
        }
        return Optional.empty( );
    }
}
