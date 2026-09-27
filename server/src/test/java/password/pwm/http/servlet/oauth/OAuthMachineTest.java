/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

package password.pwm.http.servlet.oauth;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmRequest;
import password.pwm.svc.httpclient.HttpClientService;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.PasswordData;

import java.util.Map;

public class OAuthMachineTest
{
    @Test
    public void parserTest1()
    {
        final String input = "{\n"
                + "\t\"access_token\":\"Q6hgBgSZMMvVnOP2tOTufILVfao82kcHtVqE9pspzC55oqKdMjuaz9Jpj3KpTlv\",\n"
                + "\t\"token_type\":\"bearer\",\n"
                + "\t\"expires_in\":3599,\n"
                + "\t\"scope\":\"profile\"\n"
                + "}";
        final OAuthSettings oAuthSettings = OAuthSettings.builder().build();
        final OAuthMachine oAuthMachine = new OAuthMachine( null, oAuthSettings );
        Assert.assertEquals( "3599", oAuthMachine.readAttributeFromBodyMap( input, "expires_in" ) );
    }

    @Test
    public void parserTest2()
    {
        final String input = "{\"sub\":\"0c8463c904e6444fa5c2b4597f816bc2\",\"claims\":[],\"email\":\"testadmin@example.com\"}";
        final OAuthSettings oAuthSettings = OAuthSettings.builder().build();
        final OAuthMachine oAuthMachine = new OAuthMachine( null, oAuthSettings );
        Assert.assertEquals( "testadmin@example.com", oAuthMachine.readAttributeFromBodyMap( input, "email" ) );
        Assert.assertNull( oAuthMachine.readAttributeFromBodyMap( input, "claims" ) );
    }

    @Test
    public void parserTest3()
    {
        final String input = "{\"sub\":\"0c8463c904e6444fa5c2b4597f816bc2\",\"claims\":[\"value1\",\"value2\"],\"email\":\"testadmin@example.com\"}";
        final OAuthSettings oAuthSettings = OAuthSettings.builder().build();
        final OAuthMachine oAuthMachine = new OAuthMachine( null, oAuthSettings );
        Assert.assertEquals( "value1", oAuthMachine.readAttributeFromBodyMap( input, "claims" ) );
    }

    /**
     * Issue #723: verifies the new {@code loginHintValue} field round-trips through the
     * Lombok-generated builder/getter on {@link OAuthSettings}.
     */
    @Test
    public void loginHintValueRoundtripsThroughBuilder()
    {
        final OAuthSettings oAuthSettings = OAuthSettings.builder()
                .loginHintValue( "@LDAP:mail@" )
                .build();
        Assert.assertEquals( "@LDAP:mail@", oAuthSettings.getLoginHintValue() );
    }

    /**
     * Issue #723: verifies the AppProperty default for the OIDC {@code login_hint}
     * query-parameter name is wired to the spec-defined value.
     */
    @Test
    public void loginHintAppPropertyDefaultsToLoginHint()
            throws PwmUnrecoverableException
    {
        final Configuration conf = new Configuration( StoredConfigurationFactory.newConfig() );
        Assert.assertEquals( "login_hint", conf.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_LOGIN_HINT ) );
    }

    @Test
    public void usernameClaimRoundtripsThroughBuilder()
    {
        final OAuthSettings oAuthSettings = OAuthSettings.builder()
                .usernameClaim( "sub" )
                .build();

        Assert.assertEquals( "sub", oAuthSettings.getUsernameClaim() );
        Assert.assertTrue( oAuthSettings.usernameClaimIsConfigured() );
    }

    @Test
    public void usernameClaimIsNotConfiguredByDefault()
    {
        Assert.assertFalse( OAuthSettings.builder().build().usernameClaimIsConfigured() );
    }

    /**
     * A deployment that resolves the user name from an id_token claim does not need the
     * profile/userinfo service url or the login attribute, so neither may be required.
     */
    @Test
    public void oAuthIsConfiguredWithUsernameClaimAndNoUserInfoService()
            throws PwmUnrecoverableException
    {
        final OAuthSettings oAuthSettings = baseSettings()
                .usernameClaim( "sub" )
                .build();

        Assert.assertTrue( oAuthSettings.oAuthIsConfigured() );
    }

    /**
     * Regression guard: the pre-existing profile/userinfo configuration must keep working
     * unchanged when no user name claim is set.
     */
    @Test
    public void oAuthIsConfiguredWithUserInfoServiceAndNoUsernameClaim()
            throws PwmUnrecoverableException
    {
        final OAuthSettings oAuthSettings = baseSettings()
                .attributesUrl( "https://pingfederate.example.com/idp/userinfo.openid" )
                .dnAttributeName( "sub" )
                .build();

        Assert.assertTrue( oAuthSettings.oAuthIsConfigured() );
    }

    @Test
    public void oAuthIsNotConfiguredWithoutEitherUsernameSource()
            throws PwmUnrecoverableException
    {
        Assert.assertFalse( baseSettings().build().oAuthIsConfigured() );
    }

    @Test
    public void idTokenAppPropertyDefaults()
            throws PwmUnrecoverableException
    {
        final Configuration conf = new Configuration( StoredConfigurationFactory.newConfig() );
        Assert.assertEquals( "id_token", conf.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_ID_TOKEN ) );
        Assert.assertEquals( "openid", conf.readAppProperty( AppProperty.OAUTH_ID_TOKEN_REQUIRED_SCOPE ) );
        Assert.assertEquals( "60", conf.readAppProperty( AppProperty.OAUTH_ID_TOKEN_MAX_CLOCK_SKEW ) );
    }

    /**
     * The shipped default of both OAuth profile settings is to send client credentials in the
     * request body only ({@code client_secret_post}).
     */
    @Test
    public void clientAuthMethodSettingDefaultsToPost()
            throws PwmUnrecoverableException
    {
        final Configuration conf = new Configuration( StoredConfigurationFactory.newConfig() );

        Assert.assertEquals( OAuthClientAuthMethod.post, conf.readSettingAsEnum(
                PwmSetting.OAUTH_ID_CLIENT_AUTH_METHOD, OAuthClientAuthMethod.class ) );
        Assert.assertEquals( OAuthClientAuthMethod.post, OAuthSettings.forSSOAuthentication( conf ).getEffectiveClientAuthMethod() );

        for ( final ForgottenPasswordProfile profile : conf.getForgottenPasswordProfiles().values() )
        {
            Assert.assertEquals( OAuthClientAuthMethod.post,
                    OAuthSettings.forForgottenPassword( profile ).getEffectiveClientAuthMethod() );
        }
    }

    /**
     * An unset value must not disable client authentication; it falls back to the default.
     */
    @Test
    public void clientAuthMethodFallsBackWhenUnset()
    {
        Assert.assertEquals( OAuthClientAuthMethod.post, OAuthSettings.builder().build().getEffectiveClientAuthMethod() );
    }

    @Test
    public void clientAuthMethodBasicSendsHeaderOnly()
    {
        final OAuthClientAuthMethod method = OAuthClientAuthMethod.basic;

        Assert.assertTrue( method.sendCredentialsInHeader() );
        Assert.assertFalse( method.sendCredentialsInBody() );
        Assert.assertFalse( method.requiresCredentialsInBody() );
        Assert.assertTrue( method.sendAccessTokenInHeader() );
        Assert.assertFalse( method.sendAccessTokenInBody() );
    }

    @Test
    public void clientAuthMethodPostSendsBodyOnly()
    {
        final OAuthClientAuthMethod method = OAuthClientAuthMethod.post;

        Assert.assertFalse( method.sendCredentialsInHeader() );
        Assert.assertTrue( method.sendCredentialsInBody() );
        Assert.assertTrue( method.requiresCredentialsInBody() );
        Assert.assertTrue( method.sendAccessTokenInHeader() );
        Assert.assertFalse( method.sendAccessTokenInBody() );
    }

    @Test
    public void clientAuthMethodBothSendsEverythingOnCodeExchangeOnly()
    {
        final OAuthClientAuthMethod method = OAuthClientAuthMethod.both;

        Assert.assertTrue( method.sendCredentialsInHeader() );
        Assert.assertTrue( method.sendCredentialsInBody() );
        Assert.assertFalse( method.requiresCredentialsInBody() );
        Assert.assertTrue( method.sendAccessTokenInHeader() );
        Assert.assertTrue( method.sendAccessTokenInBody() );
    }

    /**
     * The profile/userinfo request sends the access token as {@code Authorization: Bearer} for
     * every client authentication method, and adds the {@code access_token} form parameter only
     * for {@link OAuthClientAuthMethod#both}.  It must not fall back to HTTP Basic client
     * credentials.
     */
    @Test
    public void userInfoRequestSendsAccessTokenPerClientAuthMethod()
            throws Exception
    {
        final Configuration conf = new Configuration( StoredConfigurationFactory.newConfig() );
        final String accessToken = "userinfo-access-token";
        final String attribute = "mail";

        for ( final OAuthClientAuthMethod method : OAuthClientAuthMethod.values() )
        {
            final OAuthSettings settings = baseSettings()
                    .attributesUrl( "https://pingfederate.example.com/idp/userinfo.openid" )
                    .dnAttributeName( attribute )
                    .clientAuthMethod( method )
                    .build();

            final PwmHttpClient httpClient = Mockito.mock( PwmHttpClient.class );
            final HttpClientService httpClientService = Mockito.mock( HttpClientService.class );
            final PwmApplication pwmApplication = Mockito.mock( PwmApplication.class );
            final PwmRequest pwmRequest = Mockito.mock( PwmRequest.class );

            Mockito.when( pwmRequest.getConfig() ).thenReturn( conf );
            Mockito.when( pwmRequest.getPwmApplication() ).thenReturn( pwmApplication );
            Mockito.when( pwmRequest.getLabel() ).thenReturn( SessionLabel.builder().sessionID( "test" ).build() );
            Mockito.when( pwmApplication.getHttpClientService() ).thenReturn( httpClientService );
            Mockito.when( httpClientService.getPwmHttpClient( Mockito.any() ) ).thenReturn( httpClient );
            Mockito.when( httpClient.makeRequest( Mockito.any( PwmHttpClientRequest.class ), Mockito.any( SessionLabel.class ) ) )
                    .thenReturn( PwmHttpClientResponse.builder()
                            .statusCode( HttpStatus.SC_OK )
                            .body( "{\"" + attribute + "\":\"ada\"}" )
                            .build() );

            final OAuthMachine machine = new OAuthMachine( null, settings );
            Assert.assertEquals( "ada", machine.makeOAuthGetUserInfoRequest( pwmRequest, accessToken ) );

            final ArgumentCaptor<PwmHttpClientRequest> requestCaptor = ArgumentCaptor.forClass( PwmHttpClientRequest.class );
            Mockito.verify( httpClient ).makeRequest( requestCaptor.capture(), Mockito.any( SessionLabel.class ) );

            final PwmHttpClientRequest sent = requestCaptor.getValue();
            final String authorization = sent.getHeaders().get( HttpHeader.Authorization.getHttpName() );
            Assert.assertEquals( method.name() + " bearer header", "Bearer " + accessToken, authorization );

            final String body = sent.getBody() == null ? "" : sent.getBody();
            Assert.assertEquals( method.name() + " access_token form parameter",
                    method == OAuthClientAuthMethod.both, body.contains( "access_token=" + accessToken ) );
            Assert.assertFalse( method.name() + " must not send client_id", body.contains( "client_id=" ) );
            Assert.assertFalse( method.name() + " must not send client_secret", body.contains( "client_secret=" ) );
            Assert.assertTrue( body.contains( "attributes=" + attribute ) );
        }
    }

    /**
     * The configured option values must match the enum constant names, otherwise
     * {@code readSettingAsEnum} silently returns null for a value the UI accepted.
     */
    @Test
    public void everyEnumConstantIsSelectableInBothProfiles()
            throws PwmUnrecoverableException
    {
        for ( final PwmSetting setting : new PwmSetting[]
                {
                        PwmSetting.OAUTH_ID_CLIENT_AUTH_METHOD,
                        PwmSetting.RECOVERY_OAUTH_ID_CLIENT_AUTH_METHOD,
                } )
        {
            final Map<String, String> options = setting.getOptions();
            for ( final OAuthClientAuthMethod method : OAuthClientAuthMethod.values() )
            {
                Assert.assertTrue( "setting " + setting.getKey() + " is missing option '" + method.name() + "'",
                        options.containsKey( method.name() ) );
            }
            Assert.assertEquals( OAuthClientAuthMethod.values().length, options.size() );
        }
    }

    private static OAuthSettings.OAuthSettingsBuilder baseSettings()
            throws PwmUnrecoverableException
    {
        return OAuthSettings.builder()
                .loginURL( "https://pingfederate.example.com/as/authorization.oauth2" )
                .codeResolveUrl( "https://pingfederate.example.com/as/token.oauth2" )
                .clientID( "pwm-client" )
                .secret( new PasswordData( "secret" ) );
    }
}
