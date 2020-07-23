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

package password.pwm.health;

import password.pwm.i18n.Health;
import password.pwm.util.i18n.LocaleHelper;

import java.util.Locale;

public enum HealthMessage
{

    NoData( HealthStatus.CAUTION, HealthTopic.Application ),
    LDAP_No_Connection( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_Ad_History_Asn_Missing( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_AD_Unsecure( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_AD_StaticIP( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_ProxyTestSameUser( HealthStatus.WARN, HealthTopic.Configuration ),
    LDAP_ProxyUserPwExpired( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_TestUserUnavailable( HealthStatus.CAUTION, HealthTopic.LDAP ),
    LDAP_TestUserUnexpected( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_TestUserError( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_TestUserWritePwError( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_TestUserReadPwError( HealthStatus.WARN, HealthTopic.LDAP ),
    LDAP_TestUserOK( HealthStatus.GOOD, HealthTopic.LDAP ),
    Email_SendFailure( HealthStatus.WARN, HealthTopic.Email ),
    PwNotify_Failure( HealthStatus.WARN, HealthTopic.Application ),
    MissingResource( HealthStatus.DEBUG, HealthTopic.Integrity ),
    BrokenMethod( HealthStatus.DEBUG, HealthTopic.Integrity ),
    Appliance_PendingUpdates( HealthStatus.CAUTION, HealthTopic.Appliance ),
    Appliance_UpdatesNotEnabled( HealthStatus.CAUTION, HealthTopic.Appliance ),
    Appliance_UpdateServiceNotConfigured( HealthStatus.WARN, HealthTopic.Appliance ),
    Cluster_Error( HealthStatus.CAUTION, HealthTopic.Application ),
    Config_MissingProxyDN( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_MissingProxyPassword( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_NoSiteURL( HealthStatus.WARN, HealthTopic.Configuration ),
    Config_LDAPWireTrace( HealthStatus.WARN, HealthTopic.Configuration ),
    Config_PromiscuousLDAP( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_ShowDetailedErrors( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_AddTestUser( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_ParseError( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_UsingLocalDBResponseStorage( HealthStatus.CAUTION, HealthTopic.Configuration ),
    Config_WeakPassword( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_LDAPUnsecure( HealthStatus.CAUTION, HealthTopic.Configuration ),
    Config_ConfigMode( HealthStatus.WARN, HealthTopic.Configuration ),
    Config_MissingDB( HealthStatus.CAUTION, HealthTopic.Configuration ),
    Config_MissingLDAPResponseAttr( HealthStatus.CAUTION, HealthTopic.Configuration ),
    Config_URLNotSecure( HealthStatus.CAUTION, HealthTopic.Configuration ),
    Config_PasswordPolicyProblem( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_UserPermissionValidity( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_DNValueValidity( HealthStatus.CONFIG, HealthTopic.Configuration ),
    Config_NoRecoveryEnabled( HealthStatus.CAUTION, HealthTopic.Configuration ),
    Config_Certificate( HealthStatus.WARN, HealthTopic.Configuration ),
    Config_InvalidSendMethod( HealthStatus.CAUTION, HealthTopic.Configuration ),
    Config_DeprecatedJSForm( HealthStatus.CONFIG, HealthTopic.Configuration ),
    LDAP_VendorsNotSame( HealthStatus.CONFIG, HealthTopic.LDAP ),
    LDAP_OK( HealthStatus.GOOD, HealthTopic.LDAP ),
    LDAP_RecentlyUnreachable( HealthStatus.CAUTION, HealthTopic.LDAP ),
    LDAP_SearchFailure( HealthStatus.WARN, HealthTopic.LDAP ),
    CryptoTokenWithNewUserVerification( HealthStatus.CAUTION, HealthTopic.Configuration ),
    TokenServiceError( HealthStatus.WARN, HealthTopic.TokenService ),
    Java_HighThreads( HealthStatus.CAUTION, HealthTopic.Platform ),
    Java_SmallHeap( HealthStatus.CAUTION, HealthTopic.Platform ),
    Java_OK( HealthStatus.GOOD, HealthTopic.Platform ),
    LocalDB_OK( HealthStatus.GOOD, HealthTopic.LocalDB ),
    LocalDB_BAD( HealthStatus.WARN, HealthTopic.LocalDB ),
    LocalDB_NEW( HealthStatus.WARN, HealthTopic.LocalDB ),
    LocalDB_CLOSED( HealthStatus.WARN, HealthTopic.LocalDB ),
    LocalDB_LowDiskSpace( HealthStatus.WARN, HealthTopic.LocalDB ),
    LocalDBLogger_NOTOPEN( HealthStatus.CAUTION, HealthTopic.LocalDB ),
    LocalDBLogger_HighRecordCount( HealthStatus.CAUTION, HealthTopic.LocalDB ),
    LocalDBLogger_OldRecordPresent( HealthStatus.CAUTION, HealthTopic.LocalDB ),
    NewUser_PwTemplateBad( HealthStatus.CAUTION, HealthTopic.Configuration ),
    ServiceClosed( HealthStatus.CAUTION, HealthTopic.Application ),
    ServiceClosed_LocalDBUnavail( HealthStatus.CAUTION, HealthTopic.Application ),
    ServiceClosed_AppReadOnly( HealthStatus.CAUTION, HealthTopic.Application ),
    SMS_SendFailure( HealthStatus.WARN, HealthTopic.SMS ),
    Wordlist_AutoImportFailure( HealthStatus.WARN, HealthTopic.Configuration ),
    Wordlist_ImportInProgress( HealthStatus.CAUTION, HealthTopic.Application ),;

    private final HealthStatus status;
    private final HealthTopic topic;

    HealthMessage(
            final HealthStatus status,
            final HealthTopic topic
    )
    {
        this.status = status;
        this.topic = topic;
    }

    public HealthStatus getStatus( )
    {
        return status;
    }

    public HealthTopic getTopic( )
    {
        return topic;
    }

    public String getKey( )
    {
        return HealthMessage.class.getSimpleName() + "_" + this.toString();
    }

    public String getDescription( final Locale locale, final password.pwm.config.Configuration config, final String[] fields )
    {
        return LocaleHelper.getLocalizedMessage( locale, this.getKey(), config, Health.class, fields );
    }
}
