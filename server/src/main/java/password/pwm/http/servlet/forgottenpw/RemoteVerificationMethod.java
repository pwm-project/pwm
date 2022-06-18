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

package password.pwm.http.servlet.forgottenpw;

import password.pwm.PwmDomain;
import password.pwm.PwmConstants;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.RemoteVerificationRequestBean;
import password.pwm.bean.RemoteVerificationResponseBean;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.user.UserInfoBean;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.user.UserInfo;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RemoteVerificationMethod implements VerificationMethodSystem
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( RemoteVerificationMethod.class );


    private String remoteSessionID;

    private RemoteVerificationResponseBean lastResponse;
    private PwmHttpClient pwmHttpClient;
    private PwmDomain pwmDomain;

    private UserInfo userInfo;
    private SessionLabel sessionLabel;
    private Locale locale;
    private String url;

    @Override
    public List<UserPrompt> getCurrentPrompts( ) throws PwmUnrecoverableException
    {
        if ( lastResponse == null || lastResponse.getUserPrompts() == null )
        {
            return null;
        }

        return new ArrayList<>( lastResponse.getUserPrompts() );
    }

    @Override
    public String getCurrentDisplayInstructions( )
    {
        return lastResponse == null
                ? ""
                : lastResponse.getDisplayInstructions();
    }

    @Override
    public ErrorInformation respondToPrompts( final Map<String, String> answers ) throws PwmUnrecoverableException
    {
        sendRemoteRequest( answers );
        if ( lastResponse != null )
        {
            final String errorMsg = lastResponse.getErrorMessage();
            if ( errorMsg != null && !errorMsg.isEmpty() )
            {
                return new ErrorInformation( PwmError.ERROR_REMOTE_ERROR_VALUE, errorMsg );
            }
        }
        return null;
    }

    @Override
    public VerificationState getVerificationState( )
    {
        return lastResponse == null
                ? VerificationState.INPROGRESS
                : lastResponse.getVerificationState();
    }

    @Override
    public void init( final PwmDomain pwmDomain, final UserInfo userInfo, final SessionLabel sessionLabel, final Locale locale )
            throws PwmUnrecoverableException
    {
        pwmHttpClient = pwmDomain.getHttpClientService().getPwmHttpClient( sessionLabel );
        this.remoteSessionID = pwmDomain.getSecureService().pwmRandom().randomUUID().toString();
        this.userInfo = userInfo;
        this.sessionLabel = sessionLabel;
        this.locale = locale;
        this.pwmDomain = pwmDomain;
        this.url = pwmDomain.getConfig().readSettingAsString( PwmSetting.EXTERNAL_MACROS_REMOTE_RESPONSES_URL );

        if ( url == null || url.isEmpty() )
        {
            final String errorMsg = PwmSetting.EXTERNAL_MACROS_REMOTE_RESPONSES_URL.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE )
                    + " must be configured for remote responses";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            LOGGER.error( sessionLabel, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        sendRemoteRequest( null );
    }

    private void sendRemoteRequest( final Map<String, String> userResponses ) throws PwmUnrecoverableException
    {
        lastResponse = null;

        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() );
        headers.put( HttpHeader.AcceptLanguage.getHttpName(), locale.toLanguageTag() );

        final MacroRequest macroRequest = MacroRequest.forUser( pwmDomain.getPwmApplication(), sessionLabel, userInfo, null );

        final RemoteVerificationRequestBean remoteVerificationRequestBean = RemoteVerificationRequestBean.builder()
            .responseSessionID( this.remoteSessionID )
            .userInfo( UserInfoBean.toPublicUserInfoBean( userInfo, pwmDomain.getConfig(), locale, macroRequest ) )
            .userResponses( userResponses )
            .build();

        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder()
                .method( HttpMethod.POST )
                .url( url )
                .body( JsonFactory.get().serialize( remoteVerificationRequestBean ) )
                .headers( headers )
                .build();

        try
        {
            final PwmHttpClientResponse response = pwmHttpClient.makeRequest( pwmHttpClientRequest );
            final String responseBodyStr = response.getBody();
            this.lastResponse = JsonFactory.get().deserialize( responseBodyStr, RemoteVerificationResponseBean.class );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( sessionLabel, e.getErrorInformation() );
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "error reading remote responses web service response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            LOGGER.error( sessionLabel, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public static Map<String, String> readRemoteResponses( final PwmRequest pwmRequest, final String prefix )
        throws PwmUnrecoverableException
    {
        final Map<String, String> remoteResponses = new LinkedHashMap<>();
        {
            final Map<String, String> inputMap = pwmRequest.readParametersAsMap();
            for ( final Map.Entry<String, String> entry : inputMap.entrySet() )
            {
                final String name = entry.getKey();
                if ( name != null && name.startsWith( prefix ) )
                {
                    final String strippedName = name.substring( prefix.length() );
                    final String value = entry.getValue();
                    remoteResponses.put( strippedName, value );
                }
            }
        }
        return Collections.unmodifiableMap( remoteResponses );
    }


}
