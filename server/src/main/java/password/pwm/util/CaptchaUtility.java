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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ApplicationPage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmURL;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.svc.PwmService;
import password.pwm.svc.intruder.IntruderManager;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class CaptchaUtility
{

    private static final PwmLogger LOGGER = PwmLogger.getLogger( CaptchaUtility.class.getName() );

    private static final String COOKIE_SKIP_INSTANCE_VALUE = "INSTANCEID";


    /**
     * Verify a reCaptcha request.  The reCaptcha request API is documented at
     * <a href="http://recaptcha.net/apidocs/captcha/"/>reCaptcha API.
     */
    public static boolean verifyReCaptcha(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String recaptchaResponse = pwmRequest.readParameterAsString( "g-recaptcha-response" );
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

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PasswordData privateKey = pwmApplication.getConfig().readSettingAsPassword( PwmSetting.RECAPTCHA_KEY_PRIVATE );

        final StringBuilder bodyText = new StringBuilder();
        bodyText.append( "secret=" ).append( privateKey.getStringValue() );
        bodyText.append( "&" );
        bodyText.append( "remoteip=" ).append( pwmRequest.getSessionLabel().getSrcAddress() );
        bodyText.append( "&" );
        bodyText.append( "response=" ).append( recaptchaResponse );

        try
        {
            final PwmHttpClientRequest clientRequest = new PwmHttpClientRequest(
                    HttpMethod.POST,
                    pwmApplication.getConfig().readAppProperty( AppProperty.RECAPTCHA_VALIDATE_URL ),
                    bodyText.toString(),
                    Collections.singletonMap( "Content-Type", HttpContentType.form.getHeaderValue() )
            );
            LOGGER.debug( pwmRequest, "sending reCaptcha verification request" );
            final PwmHttpClient client = new PwmHttpClient( pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel() );
            final PwmHttpClientResponse clientResponse = client.makeRequest( clientRequest );

            if ( clientResponse.getStatusCode() != HttpServletResponse.SC_OK )
            {
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_CAPTCHA_API_ERROR,
                        "unexpected HTTP status code (" + clientResponse.getStatusCode() + ")"
                ) );
            }

            final JsonElement responseJson = new JsonParser().parse( clientResponse.getBody() );
            final JsonObject topObject = responseJson.getAsJsonObject();
            if ( topObject != null && topObject.has( "success" ) )
            {
                final boolean success = topObject.get( "success" ).getAsBoolean();
                if ( success )
                {
                    writeCaptchaSkipCookie( pwmRequest );
                    return true;
                }

                if ( topObject.has( "error-codes" ) )
                {
                    final List<String> errorCodes = new ArrayList<>();
                    for ( final JsonElement element : topObject.get( "error-codes" ).getAsJsonArray() )
                    {
                        final String errorCode = element.getAsString();
                        errorCodes.add( errorCode );
                    }
                    LOGGER.debug( pwmRequest, "recaptcha error codes: " + JsonUtil.serializeCollection( errorCodes ) );
                }
            }
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error during reCaptcha API execution: " + e.getMessage();
            LOGGER.error( errorMsg, e );
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CAPTCHA_API_ERROR, errorMsg );
            final PwmUnrecoverableException pwmE = new PwmUnrecoverableException( errorInfo );
            pwmE.initCause( e );
            throw pwmE;
        }

        return false;
    }


    private static void writeCaptchaSkipCookie(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String cookieValue = figureSkipCookieValue( pwmRequest );
        final int captchaSkipCookieLifetimeSeconds = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_AGE ) );
        final String captchaSkipCookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_NAME );
        if ( cookieValue != null )
        {
            pwmRequest.getPwmResponse().writeCookie(
                    captchaSkipCookieName,
                    cookieValue,
                    captchaSkipCookieLifetimeSeconds
            );
        }
    }

    private static String figureSkipCookieValue( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        String cookieValue = pwmRequest.getConfig().readSettingAsString( PwmSetting.CAPTCHA_SKIP_COOKIE );
        if ( cookieValue == null || cookieValue.trim().length() < 1 )
        {
            return null;
        }

        if ( cookieValue.equals( COOKIE_SKIP_INSTANCE_VALUE ) )
        {
            cookieValue = pwmRequest.getPwmApplication().getInstanceID();
        }

        return cookieValue != null && cookieValue.trim().length() > 0 ? cookieValue : null;
    }

    private static boolean checkRequestForCaptchaSkipCookie( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final String allowedSkipValue = figureSkipCookieValue( pwmRequest );
        final String captchaSkipCookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_NAME );
        if ( allowedSkipValue != null )
        {
            final String cookieValue = pwmRequest.readCookie( captchaSkipCookieName );
            if ( allowedSkipValue.equals( cookieValue ) )
            {
                LOGGER.debug( pwmRequest, "browser has a valid " + captchaSkipCookieName + " cookie value of " + figureSkipCookieValue( pwmRequest ) + ", skipping captcha check" );
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

        final Set<ApplicationPage> protectedModules = pwmRequest.getConfig().readSettingAsOptionList(
                PwmSetting.CAPTCHA_PROTECTED_PAGES,
                ApplicationPage.class
        );

        final PwmURL pwmURL = pwmRequest.getURL();

        boolean enabled = false;

        if ( protectedModules != null )
        {
            if ( protectedModules.contains( ApplicationPage.LOGIN ) && pwmURL.isLoginServlet() )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.FORGOTTEN_PASSWORD ) && pwmURL.isForgottenPasswordServlet() )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.FORGOTTEN_USERNAME ) && pwmURL.isForgottenUsernameServlet() )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.USER_ACTIVATION ) && pwmURL.isUserActivationServlet() )
            {
                enabled = true;
            }
            else if ( protectedModules.contains( ApplicationPage.NEW_USER_REGISTRATION ) && pwmURL.isNewUserRegistrationServlet() )
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
        final Configuration config = pwmRequest.getPwmApplication().getConfig();
        final PasswordData privateKey = config.readSettingAsPassword( PwmSetting.RECAPTCHA_KEY_PRIVATE );
        final String publicKey = config.readSettingAsString( PwmSetting.RECAPTCHA_KEY_PUBLIC );

        return ( privateKey != null && publicKey != null && !publicKey.isEmpty() );
    }

    public static void prepareCaptchaDisplay( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        StatisticsManager.incrementStat( pwmRequest, Statistic.CAPTCHA_PRESENTATIONS );

        final String reCaptchaPublicKey = pwmRequest.getConfig().readSettingAsString( PwmSetting.RECAPTCHA_KEY_PUBLIC );
        pwmRequest.setAttribute( PwmRequestAttribute.CaptchaPublicKey, reCaptchaPublicKey );
        {
            final String urlValue = pwmRequest.getConfig().readAppProperty( AppProperty.RECAPTCHA_CLIENT_JS_URL );
            pwmRequest.setAttribute( PwmRequestAttribute.CaptchaClientUrl, urlValue );
        }
        {
            final String configuredUrl = pwmRequest.getConfig().readAppProperty( AppProperty.RECAPTCHA_CLIENT_IFRAME_URL );
            final String url = configuredUrl + "?k=" + reCaptchaPublicKey + "&hl=" + pwmRequest.getLocale().toString();
            pwmRequest.setAttribute( PwmRequestAttribute.CaptchaIframeUrl, url );
        }
    }

    private static boolean checkIfCaptchaParamPresent( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final String skipCaptcha = pwmRequest.readParameterAsString( PwmConstants.PARAM_SKIP_CAPTCHA );
        if ( skipCaptcha != null && skipCaptcha.length() > 0 )
        {
            final String configValue = pwmRequest.getConfig().readSettingAsString( PwmSetting.CAPTCHA_SKIP_PARAM );
            if ( configValue != null && configValue.equals( skipCaptcha ) )
            {
                LOGGER.trace( pwmRequest, "valid skipCaptcha value in request, skipping captcha check for this session" );
                return true;
            }
            else
            {
                LOGGER.error( pwmRequest, "skipCaptcha value is in request, however value '" + skipCaptcha + "' does not match configured value" );
            }
        }

        return false;
    }

    private static boolean checkIntruderCount( final PwmRequest pwmRequest )
    {
        final long maxIntruderCount = pwmRequest.getConfig().readSettingAsLong( PwmSetting.CAPTCHA_INTRUDER_COUNT_TRIGGER );

        if ( maxIntruderCount == 0 )
        {
            return true;
        }

        final int currentSessionAttempts = pwmRequest.getPwmSession().getSessionStateBean().getIntruderAttempts();
        if ( currentSessionAttempts >= maxIntruderCount )
        {
            LOGGER.debug( pwmRequest, "session intruder attempt count '" + currentSessionAttempts + "', therefore captcha will be required" );
            return true;
        }

        final IntruderManager intruderManager = pwmRequest.getPwmApplication().getIntruderManager();
        if ( intruderManager == null || intruderManager.status() != PwmService.STATUS.OPEN )
        {
            return false;
        }

        final int intruderAttemptCount = intruderManager.countForNetworkEndpointInRequest( pwmRequest );
        if ( intruderAttemptCount >= maxIntruderCount )
        {
            LOGGER.debug( pwmRequest, "network intruder attempt count '" + intruderAttemptCount + "', therefore captcha will be required" );
            return true;
        }

        return false;
    }
}
