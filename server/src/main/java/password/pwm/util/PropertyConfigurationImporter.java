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

package password.pwm.util;

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.BooleanValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmException;
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
    private static final String LDAP_PROFILE = "default";

    private Map<String, String> inputMap;

    public enum PropertyKey
    {
        TEMPLATE_LDAP,
        DISPLAY_THEME,

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

        LDAP_SERVERCERTS,
        OAUTH_IDSERVER_SERVERCERTS,
        AUDIT_SERVERCERTS,;
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

    public StoredConfigurationImpl readConfiguration( final InputStream propertiesInput )
            throws PwmException, IOException
    {
        readInputFile( propertiesInput );

        final StoredConfigurationImpl storedConfiguration = StoredConfigurationImpl.newStoredConfiguration( );
        storedConfiguration.initNewRandomSecurityKey( );
        storedConfiguration.writeConfigProperty( 
                ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( true ) );
        storedConfiguration.writeConfigProperty( 
                ConfigurationProperty.CONFIG_EPOCH, String.valueOf( 0 ) );

        // static values
        storedConfiguration.writeSetting( PwmSetting.TEMPLATE_LDAP, new StringValue( 
                        inputMap.getOrDefault( PropertyKey.TEMPLATE_LDAP.name( ), "NOVL_IDM" ) ),
                null );

        if ( inputMap.containsKey( PropertyKey.DISPLAY_THEME.name( ) ) )
        {
            storedConfiguration.writeSetting( PwmSetting.PASSWORD_POLICY_SOURCE, new StringValue( 
                            inputMap.get( PropertyKey.DISPLAY_THEME.name( ) ) ),
                    null );
        }

        storedConfiguration.writeSetting( PwmSetting.DISPLAY_HOME_BUTTON, new BooleanValue( false ), null );
        storedConfiguration.writeSetting( PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE, new BooleanValue( false ), null );
        storedConfiguration.writeSetting( PwmSetting.PASSWORD_REQUIRE_CURRENT, new BooleanValue( false ), null );
        storedConfiguration.writeSetting( PwmSetting.PASSWORD_POLICY_SOURCE, new StringValue( "LDAP" ), null );
        storedConfiguration.writeSetting( PwmSetting.CERTIFICATE_VALIDATION_MODE, new StringValue( "CA_ONLY" ), null );
        {
            final String notes = "Configuration generated via properties import at " + JavaHelper.toIsoDate( Instant.now( ) );
            storedConfiguration.writeSetting( PwmSetting.NOTES, new StringValue( notes ), null );
        }

        // ldap server
        storedConfiguration.writeSetting( PwmSetting.LDAP_SERVER_URLS, LDAP_PROFILE, makeLdapServerUrlValue( ), null );
        storedConfiguration.writeSetting( PwmSetting.LDAP_PROXY_USER_DN, LDAP_PROFILE,
                new PasswordValue( PasswordData.forStringValue( inputMap.get( PropertyKey.ID_VAULT_ADMIN_LDAP.name( ) ) ) ), null );
        storedConfiguration.writeSetting( PwmSetting.LDAP_PROXY_USER_PASSWORD, LDAP_PROFILE,
                new PasswordValue( PasswordData.forStringValue( inputMap.get( PropertyKey.ID_VAULT_PASSWORD.name( ) ) ) ), null );
        storedConfiguration.writeSetting( PwmSetting.LDAP_CONTEXTLESS_ROOT, LDAP_PROFILE,
                new StringArrayValue( Collections.singletonList( inputMap.get( PropertyKey.USER_CONTAINER.name( ) ) ) ), null );

        // oauth
        storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_LOGIN_URL, new StringValue( makeOAuthBaseUrl( ) + "/grant" ), null );
        storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_CODERESOLVE_URL, new StringValue( makeOAuthBaseUrl( ) + "/authcoderesolve" ), null );
        storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_ATTRIBUTES_URL, new StringValue( makeOAuthBaseUrl( ) + "/getattributes" ), null );
        storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_CLIENTNAME, new StringValue( "sspr" ), null );
        storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME, new StringValue( "name" ), null );
        storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_SECRET,
                new PasswordValue( PasswordData.forStringValue( inputMap.get( PropertyKey.SSO_SERVICE_PWD.name( ) ) ) ), null );

        //urls
        storedConfiguration.writeSetting( PwmSetting.URL_FORWARD, makeForwardUrl( ), null );
        storedConfiguration.writeSetting( PwmSetting.URL_LOGOUT, makeLogoutUrl( ), null );
        storedConfiguration.writeSetting( PwmSetting.PWM_SITE_URL, makeSelfUrl( ), null );
        storedConfiguration.writeSetting( PwmSetting.SECURITY_REDIRECT_WHITELIST, makeWhitelistUrl( ), null );

        // admin settings
        storedConfiguration.writeSetting( PwmSetting.QUERY_MATCH_PWM_ADMIN, makeAdminPermissions( ), null );
        storedConfiguration.setPassword( inputMap.get( PropertyKey.CONFIGURATION_PWD.name( ) ) );

        // certificates
        {
            final Optional<Collection<X509Certificate>> optionalCert = readCertificate( PropertyKey.LDAP_SERVERCERTS );
            if ( optionalCert.isPresent( ) )
            {
                storedConfiguration.writeSetting( PwmSetting.LDAP_SERVER_CERTS, LDAP_PROFILE, new X509CertificateValue( optionalCert.get( ) ), null );
            }
        }
        {
            final Optional<Collection<X509Certificate>> optionalCert = readCertificate( PropertyKey.AUDIT_SERVERCERTS );
            if ( optionalCert.isPresent( ) )
            {
                storedConfiguration.writeSetting( PwmSetting.AUDIT_SYSLOG_CERTIFICATES, new X509CertificateValue( optionalCert.get( ) ), null );
            }
        }
        {
            final Optional<Collection<X509Certificate>> optionalCert = readCertificate( PropertyKey.OAUTH_IDSERVER_SERVERCERTS );
            if ( optionalCert.isPresent( ) )
            {
                storedConfiguration.writeSetting( PwmSetting.OAUTH_ID_CERTIFICATE, new X509CertificateValue( optionalCert.get( ) ), null );
            }
        }


        return storedConfiguration;
    }

    private String makeOAuthBaseUrl( )
    {
        return "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.get( PropertyKey.SSO_SERVER_SSL_PORT.name( ) )
                + "/osp/a/idm/auth/oauth2";
    }

    private StringArrayValue makeWhitelistUrl( )
    {
        return new StringArrayValue( Collections.singletonList( "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.get( PropertyKey.SSO_SERVER_SSL_PORT.name( ) ) ) );
    }

    private StoredValue makeSelfUrl( )
    {
        return new StringValue( "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.get( PropertyKey.SSO_SERVER_SSL_PORT.name( ) )
                + "/sspr" );
    }

    private StoredValue makeForwardUrl( )
    {
        return new StringValue( "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.get( PropertyKey.SSO_SERVER_SSL_PORT.name( ) )
                + "/idmdash/#/landing" );
    }

    private StoredValue makeLogoutUrl( )
    {
        final String targetValue = "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.get( PropertyKey.SSO_SERVER_SSL_PORT.name( ) )
                + "/sspr";

        return new StringValue( "https://" + inputMap.get( PropertyKey.SSO_SERVER_HOST.name( ) )
                + ":" + inputMap.get( PropertyKey.SSO_SERVER_SSL_PORT.name( ) )
                + "/osp/a/idm/auth/app/logout?target="
                + StringUtil.urlEncode( targetValue ) );
    }

    private StoredValue makeLdapServerUrlValue( )
    {
        final String ldapUrl = "ldaps://" + inputMap.get( PropertyKey.ID_VAULT_HOST.name( ) )
                + ":" + inputMap.get( PropertyKey.ID_VAULT_LDAPS_PORT.name( ) );
        return new StringArrayValue( Collections.singletonList( ldapUrl ) );
    }

    private StoredValue makeAdminPermissions( )
    {
        final String filter = "( objectclass=* )";
        final List<UserPermission> permissions = new ArrayList<>( );
        permissions.add( new UserPermission( UserPermission.Type.ldapQuery, LDAP_PROFILE, filter,
                inputMap.get( PropertyKey.ID_VAULT_ADMIN_LDAP.name( ) ) ) );
        permissions.add( new UserPermission( UserPermission.Type.ldapQuery, LDAP_PROFILE, filter,
                inputMap.get( PropertyKey.UA_ADMIN.name( ) ) ) );
        return new UserPermissionValue( permissions );
    }

    private void stripValueDelimiters( final Map<String, String> map )
    {
        final Pattern pattern = Pattern.compile( "^'|'$" );
        map.replaceAll( ( key, value ) -> pattern.matcher( value ).replaceAll( "" ) );
    }

    private Optional<Collection<X509Certificate>> readCertificate( 
            final PropertyKey propertyKey
    )
            throws IOException
    {
        final String base64Cert = inputMap.get( propertyKey.name( ) );
        if ( !StringUtil.isEmpty( base64Cert ) )
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
                catch ( Exception e )
                {
                    throw new IOException( "error importing key " + propertyKey.name() + ", error: " + e.getMessage() );
                }
            }
            return Optional.of( returnCerts );
        }
        return Optional.empty( );
    }
}
