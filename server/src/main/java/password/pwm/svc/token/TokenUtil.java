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

package password.pwm.svc.token;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmDomain;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.user.UserInfo;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.macro.MacroReplacer;
import password.pwm.ws.client.rest.RestTokenDataClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class TokenUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( TokenUtil.class );

    private TokenUtil()
    {
    }


    public static List<TokenDestinationItem> figureAvailableTokenDestinations(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final Locale locale,
            final UserInfo userInfo,
            final MessageSendMethod tokenSendMethod
    )
            throws PwmUnrecoverableException
    {
        if ( tokenSendMethod == null || tokenSendMethod == MessageSendMethod.NONE )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_MISSING_CONTACT, "no token send methods configured in profile" );
        }

        List<TokenDestinationItem> tokenDestinations = new ArrayList<>( TokenDestinationItem.allFromConfig( pwmDomain, userInfo ) );

        if ( tokenSendMethod != MessageSendMethod.CHOICE_SMS_EMAIL )
        {
            tokenDestinations = tokenDestinations
                    .stream()
                    .filter( tokenDestinationItem -> tokenSendMethod == tokenDestinationItem.getType().getMessageSendMethod() )
                    .collect( Collectors.toList() );
        }

        final List<TokenDestinationItem> effectiveItems = new ArrayList<>( tokenDestinations.size() );
        for ( final TokenDestinationItem item : tokenDestinations )
        {
            final TokenDestinationItem effectiveItem = invokeExternalTokenDestRestClient( pwmDomain, sessionLabel, locale, userInfo.getUserIdentity(), item );
            effectiveItems.add( effectiveItem );
        }

        LOGGER.trace( sessionLabel, () -> "calculated available token send destinations: " + JsonFactory.get().serializeCollection( effectiveItems ) );

        if ( tokenDestinations.isEmpty() )
        {
            final String msg = "no available contact methods of type " + tokenSendMethod.name() + " available";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_MISSING_CONTACT, msg );
        }

        return Collections.unmodifiableList( effectiveItems );
    }

    private static TokenDestinationItem invokeExternalTokenDestRestClient(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final Locale locale,
            final UserIdentity userIdentity,
            final TokenDestinationItem tokenDestinationItem
    )
            throws PwmUnrecoverableException
    {
        final RestTokenDataClient.TokenDestinationData inputDestinationData = new RestTokenDataClient.TokenDestinationData(
                tokenDestinationItem.getType() == TokenDestinationItem.Type.email ? tokenDestinationItem.getValue() : null,
                tokenDestinationItem.getType() == TokenDestinationItem.Type.sms ? tokenDestinationItem.getValue() : null,
                tokenDestinationItem.getDisplay()
        );

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient( pwmDomain );
        final RestTokenDataClient.TokenDestinationData outputDestrestTokenDataClient = restTokenDataClient.figureDestTokenDisplayString(
                sessionLabel,
                inputDestinationData,
                userIdentity,
                locale );

        final String outputValue = tokenDestinationItem.getType() == TokenDestinationItem.Type.email
                ? outputDestrestTokenDataClient.getEmail()
                : outputDestrestTokenDataClient.getSms();

        return TokenDestinationItem.builder()
                .type( tokenDestinationItem.getType() )
                .display( outputDestrestTokenDataClient.getDisplayValue() )
                .value( outputValue )
                .id( tokenDestinationItem.getId() )
                .build();
    }

    public static TokenPayload checkEnteredCode(
            final PwmRequestContext pwmRequestContext,
            final String userEnteredCode,
            final TokenDestinationItem tokenDestinationItem,
            final UserIdentity userIdentity,
            final TokenType tokenType,
            final TokenService.TokenEntryType tokenEntryType
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequestContext.getPwmDomain();

        try
        {
            final TokenPayload tokenPayload = pwmDomain.getTokenService().processUserEnteredCode(
                    pwmRequestContext,
                    userIdentity,
                    tokenType,
                    userEnteredCode,
                    tokenEntryType
            );
            if ( tokenPayload != null )
            {
                if ( !tokenType.matchesName( tokenPayload.getName() ) )
                {
                    final String errorMsg = "expecting email token type but received : " + tokenPayload.getName();
                    throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                }

                if ( tokenEntryType == TokenService.TokenEntryType.authenticated )
                {
                    if ( tokenPayload.getUserIdentity() == null )
                    {
                        final String errorMsg = "missing userID for received token";
                        throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                    }

                    if ( !userIdentity.canonicalEquals( pwmRequestContext.getSessionLabel(), tokenPayload.getUserIdentity(), pwmDomain.getPwmApplication() ) )
                    {
                        final String errorMsg = "received token is not for currently authenticated user, received token is for: "
                                + tokenPayload.getUserIdentity().toDisplayString();
                        throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                    }
                }

                if ( tokenDestinationItem != null )
                {
                    final String currentTokenDest = tokenDestinationItem.getValue();
                    final TokenDestinationItem payloadTokenDest = tokenPayload.getDestination();
                    if ( payloadTokenDest != null && !StringUtil.nullSafeEquals( currentTokenDest, payloadTokenDest.getValue() ) )
                    {
                        final String errorMsg = "token is for destination '" + currentTokenDest
                                + "', but the current expected destination is '" + payloadTokenDest + "'";
                        throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                    }
                }
            }

            return tokenPayload;
        }
        catch ( final PwmOperationalException e )
        {
            final String errorMsg = "token incorrect: " + e.getMessage();
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
        }
    }

    public static void initializeAndSendToken(
            final PwmRequestContext pwmRequestContext,
            final TokenInitAndSendRequest tokenInitAndSendRequest
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmRequestContext.getDomainConfig();
        final UserInfo userInfo = tokenInitAndSendRequest.getUserInfo();
        final Map<String, String> tokenMapData = new LinkedHashMap<>();
        final MacroRequest macroRequest;
        {
            if ( tokenInitAndSendRequest.getMacroRequest() != null )
            {
                macroRequest = tokenInitAndSendRequest.getMacroRequest();
            }
            else if ( tokenInitAndSendRequest.getUserInfo() != null )
            {
                macroRequest = MacroRequest.forUser(
                        pwmRequestContext.getPwmApplication(),
                        pwmRequestContext.getLocale(),
                        pwmRequestContext.getSessionLabel(),
                        userInfo.getUserIdentity(),
                        makeTokenDestStringReplacer( tokenInitAndSendRequest.getTokenDestinationItem() ) );
            }
            else
            {
                macroRequest = null;
            }
        }

        if ( userInfo != null )
        {
            final Instant userLastPasswordChange = userInfo.getPasswordLastModifiedTime();
            if ( userLastPasswordChange != null )
            {
                final String userChangeString = StringUtil.toIsoDate( userLastPasswordChange );
                tokenMapData.put( PwmConstants.TOKEN_KEY_PWD_CHG_DATE, userChangeString );
            }
        }

        if ( tokenInitAndSendRequest.getInputTokenData() != null )
        {
            tokenMapData.putAll( tokenInitAndSendRequest.getInputTokenData() );
        }


        final String tokenKey;
        final TokenPayload tokenPayload;
        {

            final TimeDuration tokenLifetime = tokenInitAndSendRequest.getTokenLifetime() == null
                    ? TimeDuration.of( config.readSettingAsLong( PwmSetting.TOKEN_LIFETIME ), TimeDuration.Unit.SECONDS )
                    : tokenInitAndSendRequest.getTokenLifetime();

            try
            {
                tokenPayload = pwmRequestContext.getPwmDomain().getTokenService().createTokenPayload(
                        tokenInitAndSendRequest.getTokenType(),
                        tokenLifetime,
                        tokenMapData,
                        userInfo == null ? null : userInfo.getUserIdentity(),
                        tokenInitAndSendRequest.getTokenDestinationItem()
                );
                tokenKey = pwmRequestContext.getPwmDomain().getTokenService().generateNewToken( tokenPayload, pwmRequestContext.getSessionLabel() );
            }
            catch ( final PwmOperationalException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
        }

        final EmailItemBean emailItemBean = tokenInitAndSendRequest.getEmailToSend() == null
                ? null
                : config.readSettingAsEmail( tokenInitAndSendRequest.getEmailToSend(), pwmRequestContext.getLocale() );

        final String smsMessage = tokenInitAndSendRequest.getSmsToSend() == null
                ? null
                : config.readSettingAsLocalizedString( tokenInitAndSendRequest.getSmsToSend(), pwmRequestContext.getLocale() );

        TokenService.TokenSender.sendToken(
                TokenService.TokenSendInfo.builder()
                        .pwmDomain( pwmRequestContext.getPwmDomain() )
                        .userInfo( userInfo )
                        .macroRequest( macroRequest )
                        .configuredEmailSetting( emailItemBean )
                        .tokenDestinationItem( tokenInitAndSendRequest.getTokenDestinationItem() )
                        .smsMessage( smsMessage )
                        .tokenKey( tokenKey )
                        .sessionLabel( pwmRequestContext.getSessionLabel() )
                        .build()
        );
    }

    public static MacroReplacer makeTokenDestStringReplacer( final TokenDestinationItem tokenDestinationItem )
    {
        return ( matchedMacro, newValue ) ->
        {
            if ( "@User:Email@".equals( matchedMacro )  )
            {
                return tokenDestinationItem.getValue();
            }

            return newValue;
        };
    }

    @Value
    @Builder
    public static class TokenInitAndSendRequest
    {
        private final UserInfo userInfo;
        private TokenDestinationItem tokenDestinationItem;
        private PwmSetting emailToSend;
        private TokenType tokenType;
        private PwmSetting smsToSend;
        private Map<String, String> inputTokenData;
        private MacroRequest macroRequest;
        private TimeDuration tokenLifetime;
    }
}
