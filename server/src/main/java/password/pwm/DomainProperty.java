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

package password.pwm;

import password.pwm.util.java.EnumUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

public enum DomainProperty
{
    CLIENT_AJAX_TYPING_TIMEOUT                      ( "client.ajax.typingTimeout" ),
    CLIENT_AJAX_TYPING_WAIT                         ( "client.ajax.typingWait" ),
    HTTP_COOKIE_DEFAULT_SECURE_FLAG                 ( "http.cookie.default.secureFlag" ),
    HTTP_COOKIE_HTTPONLY_ENABLE                     ( "http.cookie.httponly.enable" ),
    HTTP_COOKIE_THEME_NAME                          ( "http.cookie.theme.name" ),
    HTTP_COOKIE_THEME_AGE                           ( "http.cookie.theme.age" ),
    HTTP_COOKIE_LOCALE_NAME                         ( "http.cookie.locale.name" ),
    HTTP_COOKIE_AUTHRECORD_NAME                     ( "http.cookie.authRecord.name" ),
    HTTP_COOKIE_AUTHRECORD_AGE                      ( "http.cookie.authRecord.age" ),
    HTTP_COOKIE_MAX_READ_LENGTH                     ( "http.cookie.maxReadLength" ),
    HTTP_COOKIE_CAPTCHA_SKIP_NAME                   ( "http.cookie.captchaSkip.name" ),
    HTTP_COOKIE_CAPTCHA_SKIP_AGE                    ( "http.cookie.captchaSkip.age" ),
    HTTP_COOKIE_LOGIN_NAME                          ( "http.cookie.login.name" ),
    HTTP_COOKIE_NONCE_NAME                          ( "http.cookie.nonce.name" ),
    HTTP_COOKIE_NONCE_LENGTH                        ( "http.cookie.nonce.length" ),
    HTTP_COOKIE_SAMESITE_VALUE                      ( "http.cookie.sameSite.value" ),
    HTTP_PARAM_OAUTH_ACCESS_TOKEN                   ( "http.parameter.oauth.accessToken" ),
    HTTP_PARAM_OAUTH_ATTRIBUTES                     ( "http.parameter.oauth.attributes" ),
    HTTP_PARAM_OAUTH_CLIENT_ID                      ( "http.parameter.oauth.clientID" ),
    HTTP_PARAM_OAUTH_CLIENT_SECRET                  ( "http.parameter.oauth.clientSecret" ),
    HTTP_PARAM_OAUTH_CODE                           ( "http.parameter.oauth.code" ),
    HTTP_PARAM_OAUTH_EXPIRES                        ( "http.parameter.oauth.expires" ),
    HTTP_PARAM_OAUTH_RESPONSE_TYPE                  ( "http.parameter.oauth.responseType" ),
    HTTP_PARAM_OAUTH_REDIRECT_URI                   ( "http.parameter.oauth.redirectUri" ),
    HTTP_PARAM_OAUTH_REFRESH_TOKEN                  ( "http.parameter.oauth.refreshToken" ),
    HTTP_PARAM_OAUTH_SCOPE                          ( "http.parameter.oauth.scope" ),
    HTTP_PARAM_OAUTH_STATE                          ( "http.parameter.oauth.state" ),
    HTTP_PARAM_OAUTH_GRANT_TYPE                     ( "http.parameter.oauth.grantType" ),
    LDAP_RESOLVE_CANONICAL_DN                       ( "ldap.resolveCanonicalDN" ),
    LDAP_CACHE_CANONICAL_ENABLE                     ( "ldap.cache.canonical.enable" ),
    LDAP_CACHE_CANONICAL_SECONDS                    ( "ldap.cache.canonical.seconds" ),
    LDAP_CACHE_USER_GUID_ENABLE                     ( "ldap.cache.userGuid.enable" ),
    LDAP_CACHE_USER_GUID_SECONDS                    ( "ldap.cache.userGuid.seconds" ),
    LDAP_CHAI_SETTINGS                              ( "ldap.chaiSettings" ),
    LDAP_PROXY_CONNECTION_PER_PROFILE               ( "ldap.proxy.connectionsPerProfile" ),
    LDAP_PROXY_MAX_CONNECTIONS                      ( "ldap.proxy.maxConnections" ),
    LDAP_PROXY_IDLE_THREAD_LOCAL_TIMEOUT_MS         ( "ldap.proxy.idleThreadLocal.timeoutMS" ),
    LDAP_EXTENSIONS_NMAS_ENABLE                     ( "ldap.extensions.nmas.enable" ),
    LDAP_CONNECTION_TIMEOUT                         ( "ldap.connection.timeoutMS" ),
    LDAP_PROFILE_RETRY_DELAY                        ( "ldap.profile.retryDelayMS" ),
    LDAP_PROMISCUOUS_ENABLE                         ( "ldap.promiscuousEnable" ),
    LDAP_PASSWORD_REPLICA_CHECK_INIT_DELAY_MS       ( "ldap.password.replicaCheck.initialDelayMS" ),
    LDAP_PASSWORD_REPLICA_CHECK_CYCLE_DELAY_MS      ( "ldap.password.replicaCheck.cycleDelayMS" ),
    LDAP_PASSWORD_CHANGE_SELF_ENABLE                ( "ldap.password.change.self.enable" ),
    LDAP_PASSWORD_CHANGE_HELPDESK_ENABLE            ( "ldap.password.change.helpdesk.enable" ),
    LDAP_GUID_PATTERN                               ( "ldap.guid.pattern" ),
    LDAP_BROWSER_MAX_ENTRIES                        ( "ldap.browser.maxEntries" ),
    LDAP_BROWSER_MAX_THREADS                        ( "ldap.browser.maxThreads" ),
    LDAP_SEARCH_PAGING_ENABLE                       ( "ldap.search.paging.enable" ),
    LDAP_SEARCH_PAGING_SIZE                         ( "ldap.search.paging.size" ),
    LDAP_SEARCH_PARALLEL_ENABLE                     ( "ldap.search.parallel.enable" ),
    LDAP_SEARCH_PARALLEL_FACTOR                     ( "ldap.search.parallel.factor" ),
    LDAP_SEARCH_PARALLEL_THREAD_MAX                 ( "ldap.search.parallel.threadMax" ),
    LDAP_ORACLE_POST_TEMPPW_USE_CURRENT_TIME        ( "ldap.oracle.postTempPasswordUseCurrentTime" ),
    OAUTH_ID_REQUEST_TYPE                           ( "oauth.id.requestType" ),
    OAUTH_ID_ACCESS_GRANT_TYPE                      ( "oauth.id.accessGrantType" ),
    OAUTH_ID_REFRESH_GRANT_TYPE                     ( "oauth.id.refreshGrantType" ),
    OAUTH_ENABLE_TOKEN_REFRESH                      ( "oauth.enableTokenRefresh" ),
    OAUTH_RETURN_URL_OVERRIDE                       ( "oauth.returnUrlOverride" ),;

    private final String key;
    private final String defaultValue;

    DomainProperty( final String key )
    {
        this.key = key;
        this.defaultValue = readDomainPropertiesBundle( key );
    }

    public String getKey( )
    {
        return key;
    }

    public String getDefaultValue( )
    {
        return defaultValue;
    }

    public boolean isDefaultValue( final String value )
    {
        return Objects.equals( defaultValue, value );
    }

    public static Optional<DomainProperty> forKey( final String key )
    {
        return EnumUtil.readEnumFromPredicate( DomainProperty.class, domainProperty -> Objects.equals( domainProperty.getKey(), key ) );
    }

    private static String readDomainPropertiesBundle( final String key )
    {
        return ResourceBundle.getBundle( DomainProperty.class.getName() ).getString( key );
    }
}
