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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ApplicationPage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmCookiePath;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.svc.PwmService;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.svc.intruder.IntruderDomainService;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class CaptchaUtility
{

    private static final PwmLogger LOGGER = PwmLogger.getLogger( CaptchaUtility.class.getName() );

    private static final String COOKIE_SKIP_INSTANCE_VALUE = "INSTANCEID";

    public static final String PARAM_RECAPTCHA_FORM_NAME = "g-recaptcha-response";

    public enum CaptchaMode
    {
        V2( null ),
        V2_INVISIBLE( null ),

        // v3 versions deprecated since implementation is actually V2 only.
        V3( V2 ),
        V3_INVISIBLE( V2_INVISIBLE ),;

        private final CaptchaMode replacement;

        CaptchaMode( final CaptchaMode replacement )
        {
            this.replacement = replacement;
        }

        public CaptchaMode getReplacement()
        {
            return replacement;
        }

        boolean isDeprecated()
        {
            return replacement != null;
        }
    }

    public static CaptchaMode readCaptchaMode( final PwmRequest pwmRequest )
    {
        final CaptchaMode captchaMode = pwmRequest.getDomainConfig().readSettingAsEnum( PwmSetting.CAPTCHA_RECAPTCHA_MODE, CaptchaMode.class );
        if ( captchaMode == null )
        {
            return CaptchaMode.V2;
        }

        if ( captchaMode.isDeprecated() )
        {
            return captchaMode.getReplacement();
        }

        return captchaMode;
    }

    /**
     * Verify a reCaptcha request.  The reCaptcha request API is documented at
     * <a href="http://recaptcha.net/apidocs/captcha/">reCaptcha API</a>.
     *
     * @param pwmRequest request object
     * @return true if captcha passes
     * @throws PwmUnrecoverableException if the operation fails.
     */
    public static boolean verifyReCaptcha(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String recaptchaResponse = pwmRequest.readParameterAsString( PARAM_RECAPTCHA_FORM_NAME );
        return verifyReCaptcha( pwmRequest, recaptchaResponse );
    }

    public static boolean verifyReCaptcha(
            final PwmRequest pwmRequest,
            final String recaptchaResponse
    )
            throws PwmUnrecoverableException
    {
        if ( !captchaEnabledForRequest( pwmRequest ) )
        {
            return true;
        }

        if ( StringUtil.isEmpty( recaptchaResponse ) )
        {
            final String msg = "missing recaptcha validation response";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CAPTCHA_API_ERROR, msg );
            throw new PwmUnrecoverableException( errorInfo );
        }

        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PasswordData privateKey = pwmDomain.getConfig().readSettingAsPassword( PwmSetting.RECAPTCHA_KEY_PRIVATE );

        final String bodyText = "secret=" + StringUtil.urlEncode( privateKey.getStringValue() )
                + "&"
                + "remoteip=" + StringUtil.urlEncode( pwmRequest.getLabel().getSourceAddress() )
                + "&"
                + "response=" + StringUtil.urlEncode( recaptchaResponse );

        try
        {
            final PwmHttpClientRequest clientRequest = PwmHttpClientRequest.builder()
                    .method( HttpMethod.POST )
                    .url( pwmDomain.getConfig().readAppProperty( AppProperty.RECAPTCHA_VALIDATE_URL ) )
                    .body( bodyText )
                    .headers( Collections.singletonMap( HttpHeader.ContentType.getHttpName(), HttpContentType.form.getHeaderValueWithEncoding() ) )
                    .build();

            LOGGER.debug( pwmRequest, () -> "sending reCaptcha verification request" );
            final PwmHttpClient client = pwmRequest.getClientConnectionHolder().getPwmHttpClient( null );
            final PwmHttpClientResponse clientResponse = client.makeRequest( clientRequest );

            if ( clientResponse.getStatusCode() != HttpServletResponse.SC_OK )
            {
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_CAPTCHA_API_ERROR,
                        "unexpected HTTP status code (" + clientResponse.getStatusCode() + ")"
                ) );
            }

            final JsonElement responseJson = JsonParser.parseString( clientResponse.getBody() );
            final JsonObject topObject = responseJson.getAsJsonObject();
            if ( topObject != null )
            {
                if ( topObject.has( "score" ) )
                {
                    final String errorMsg = "captcha response contains 'score' attribute, indicating captcha setting key is not expected Version 2 key type";
                    LOGGER.error( () -> errorMsg );
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CAPTCHA_API_ERROR, errorMsg );
                    throw new PwmUnrecoverableException( errorInfo );
                }

                if ( topObject.has( "success" ) )
                {
                    final boolean success = topObject.get( "success" ).getAsBoolean();
                    if ( success )
                    {
                        writeCaptchaSkipCookie( pwmRequest );
                        LOGGER.trace( pwmRequest, () -> "captcha verification passed" );
                        StatisticsClient.incrementStat( pwmRequest, Statistic.CAPTCHA_SUCCESSES );
                        return true;
                    }
                }
                if ( topObject.has( "error-codes" ) )
                {
                    final List<String> errorCodes = new ArrayList<>();
                    for ( final JsonElement element : topObject.get( "error-codes" ).getAsJsonArray() )
                    {
                        final String errorCode = element.getAsString();
                        errorCodes.add( errorCode );
                    }
                    LOGGER.debug( pwmRequest, () -> "recaptcha error codes: " + JsonFactory.get().serializeCollection( errorCodes ) );
                }
            }
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error during reCaptcha API execution: " + e.getMessage();
            LOGGER.error( pwmRequest, () -> errorMsg, e );
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CAPTCHA_API_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInfo, e );
        }

        LOGGER.trace( pwmRequest, () -> "captcha verification failed" );
        StatisticsClient.incrementStat( pwmRequest, Statistic.CAPTCHA_FAILURES );
        return false;
    }


    private static void writeCaptchaSkipCookie(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String cookieValue = figureSkipCookieValue( pwmRequest );
        final int captchaSkipCookieLifetimeSeconds = Integer.parseInt( pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_AGE ) );
        final String captchaSkipCookieName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_NAME );
        if ( cookieValue != null )
        {
            pwmRequest.getPwmResponse().writeCookie(
                    captchaSkipCookieName,
                    cookieValue,
                    captchaSkipCookieLifetimeSeconds,
                    PwmCookiePath.Domain
            );
        }
    }

    private static String figureSkipCookieValue( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        String cookieValue = pwmRequest.getDomainConfig().readSettingAsString( PwmSetting.CAPTCHA_SKIP_COOKIE );
        if ( StringUtil.isTrimEmpty( cookieValue ) )
        {
            return null;
        }

        cookieValue = cookieValue.trim();

        if ( COOKIE_SKIP_INSTANCE_VALUE.equals( cookieValue ) )
        {
            cookieValue = pwmRequest.getPwmApplication().getInstanceID();
        }

        return cookieValue;
    }

    private static boolean checkRequestForCaptchaSkipCookie( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final String allowedSkipValue = figureSkipCookieValue( pwmRequest );
        final String captchaSkipCookieName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_NAME );
        if ( allowedSkipValue != null )
        {
            final Optional<String> cookieValue = pwmRequest.readCookie( captchaSkipCookieName );
            if ( cookieValue.isPresent() && allowedSkipValue.equals( cookieValue.get() ) )
            {
                LOGGER.debug( pwmRequest, () -> "browser has a valid " + captchaSkipCookieName + " cookie value of " + allowedSkipValue + ", skipping captcha check" );
                return true;
            }
        }
        return false;
    }

    public static boolean captchaEnabledForRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        if ( !checkIfCaptchaConfigEnabled( pwmRequest ) )
        {
            return false;
        }

        if ( checkIfCaptchaParamPresent( pwmRequest ) )
        {
            return false;
        }

        if ( checkRequestForCaptchaSkipCookie( pwmRequest ) )
        {
            return false;
        }

        if ( !checkIntruderCount( pwmRequest ) )
        {
            return false;
        }

        final Set<ApplicationPage> protectedModules = pwmRequest.getDomainConfig().readSettingAsOptionList(
                PwmSetting.CAPTCHA_PROTECTED_PAGES,
                ApplicationPage.class
        );

        final PwmURL pwmURL = pwmRequest.getURL();

        boolean enabled = false;

        if ( protectedModules != null )
        {
            if ( protectedModules.contains( ApplicationPage.LOGIN ) && pwmURL.matches( PwmServletDefinition.Login ) )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.FORGOTTEN_PASSWORD ) && pwmURL.matches( PwmServletDefinition.ForgottenPassword ) )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.FORGOTTEN_USERNAME ) && pwmURL.matches( PwmServletDefinition.ForgottenUsername ) )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.USER_ACTIVATION ) && pwmURL.matches( PwmServletDefinition.ActivateUser ) )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.NEW_USER_REGISTRATION ) && pwmURL.matches( PwmServletDefinition.NewUser ) )
            {
                enabled = true;
            }
        }

        return enabled;
    }

    public static boolean checkIfCaptchaConfigEnabled(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmRequest.getPwmDomain().getConfig();
        final PasswordData privateKey = config.readSettingAsPassword( PwmSetting.RECAPTCHA_KEY_PRIVATE );
        final String publicKey = config.readSettingAsString( PwmSetting.RECAPTCHA_KEY_PUBLIC );

        return ( privateKey != null && publicKey != null && !publicKey.isEmpty() );
    }

    public static void prepareCaptchaDisplay( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        StatisticsClient.incrementStat( pwmRequest, Statistic.CAPTCHA_PRESENTATIONS );

        final String reCaptchaPublicKey = pwmRequest.getDomainConfig().readSettingAsString( PwmSetting.RECAPTCHA_KEY_PUBLIC );
        pwmRequest.setAttribute( PwmRequestAttribute.CaptchaPublicKey, reCaptchaPublicKey );
        {
            final String urlValue = pwmRequest.getDomainConfig().readAppProperty( AppProperty.RECAPTCHA_CLIENT_JS_URL );
            pwmRequest.setAttribute( PwmRequestAttribute.CaptchaClientUrl, urlValue );
        }
        {
            final String configuredUrl = pwmRequest.getDomainConfig().readAppProperty( AppProperty.RECAPTCHA_CLIENT_IFRAME_URL );
            final String url = configuredUrl + "?k=" + reCaptchaPublicKey + "&hl=" + pwmRequest.getLocale().toString();
            pwmRequest.setAttribute( PwmRequestAttribute.CaptchaIframeUrl, url );
        }
    }

    private static boolean checkIfCaptchaParamPresent( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.getPwmSession().getSessionStateBean().isCaptchaBypassedViaParameter() )
        {
            LOGGER.trace( pwmRequest, () -> "valid skipCaptcha value previously received in session, skipping captcha check" );
            return true;
        }

        final String configValue = pwmRequest.getDomainConfig().readSettingAsString( PwmSetting.CAPTCHA_SKIP_PARAM );
        if ( StringUtil.notEmpty( configValue ) )
        {
            final String requestValue = pwmRequest.readParameterAsString( PwmConstants.PARAM_SKIP_CAPTCHA );
            if ( StringUtil.notEmpty( requestValue ) )
            {
                if ( StringUtil.nullSafeEquals( configValue, requestValue ) )
                {
                    LOGGER.trace( pwmRequest, () -> "valid skipCaptcha value in request, skipping captcha check for this session" );
                    pwmRequest.getPwmSession().getSessionStateBean().setCaptchaBypassedViaParameter( true );
                    return true;
                }
                else
                {
                    LOGGER.debug( pwmRequest, () -> "skipCaptcha value is in request, however value '" + requestValue + "' does not match configured value" );
                }
            }
        }

        return false;
    }

    private static boolean checkIntruderCount( final PwmRequest pwmRequest )
    {
        final long maxIntruderCount = pwmRequest.getDomainConfig().readSettingAsLong( PwmSetting.CAPTCHA_INTRUDER_COUNT_TRIGGER );

        if ( maxIntruderCount == 0 )
        {
            return true;
        }

        final int currentSessionAttempts = pwmRequest.getPwmSession().getSessionStateBean().getIntruderAttempts().get();
        if ( currentSessionAttempts >= maxIntruderCount )
        {
            LOGGER.debug( pwmRequest, () -> "session intruder attempt count '" + currentSessionAttempts + "', therefore captcha will be required" );
            return true;
        }

        final IntruderDomainService intruderManager = pwmRequest.getPwmDomain().getIntruderService();
        if ( intruderManager == null || intruderManager.status() != PwmService.STATUS.OPEN )
        {
            return false;
        }

        final int intruderAttemptCount = intruderManager.countForNetworkEndpointInRequest( pwmRequest );
        if ( intruderAttemptCount >= maxIntruderCount )
        {
            LOGGER.debug( pwmRequest, () -> "network intruder attempt count '" + intruderAttemptCount + "', therefore captcha will be required" );
            return true;
        }

        return false;
    }
}
