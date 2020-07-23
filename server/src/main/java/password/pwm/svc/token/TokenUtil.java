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

package password.pwm.svc.token;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
import password.pwm.ldap.UserInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
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
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Locale locale,
            final UserInfo userInfo,
            final MessageSendMethod tokenSendMethod
    )
            throws PwmUnrecoverableException
    {
        if ( tokenSendMethod == null || tokenSendMethod.equals( MessageSendMethod.NONE ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_MISSING_CONTACT, "no token send methods configured in profile" );
        }

        List<TokenDestinationItem> tokenDestinations = new ArrayList<>( TokenDestinationItem.allFromConfig( pwmApplication, userInfo ) );

        if ( tokenSendMethod != MessageSendMethod.CHOICE_SMS_EMAIL )
        {
            tokenDestinations = tokenDestinations
                    .stream()
                    .filter( tokenDestinationItem -> tokenSendMethod == tokenDestinationItem.getType().getMessageSendMethod() )
                    .collect( Collectors.toList() );
        }

        final List<TokenDestinationItem> effectiveItems = new ArrayList<>(  );
        for ( final TokenDestinationItem item : tokenDestinations )
        {
            final TokenDestinationItem effectiveItem = invokeExternalTokenDestRestClient( pwmApplication, sessionLabel, locale, userInfo.getUserIdentity(), item );
            effectiveItems.add( effectiveItem );
        }

        LOGGER.trace( sessionLabel, () -> "calculated available token send destinations: " + JsonUtil.serializeCollection( effectiveItems ) );

        if ( tokenDestinations.isEmpty() )
        {
            final String msg = "no available contact methods of type " + tokenSendMethod.name() + " available";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_MISSING_CONTACT, msg );
        }

        return Collections.unmodifiableList( effectiveItems );
    }

    private static TokenDestinationItem invokeExternalTokenDestRestClient(
            final PwmApplication pwmApplication,
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

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient( pwmApplication );
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
            final CommonValues commonValues,
            final String userEnteredCode,
            final TokenDestinationItem tokenDestinationItem,
            final UserIdentity userIdentity,
            final TokenType tokenType,
            final TokenService.TokenEntryType tokenEntryType
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = commonValues.getPwmApplication();

        try
        {
            final TokenPayload tokenPayload = pwmApplication.getTokenService().processUserEnteredCode(
                    commonValues,
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

                    if ( !userIdentity.canonicalEquals( tokenPayload.getUserIdentity(), pwmApplication ) )
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
            final CommonValues commonValues,
            final TokenInitAndSendRequest tokenInitAndSendRequest
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = commonValues.getConfig();
        final UserInfo userInfo = tokenInitAndSendRequest.getUserInfo();
        final Map<String, String> tokenMapData = new LinkedHashMap<>();
        final MacroMachine macroMachine;
        {
            if ( tokenInitAndSendRequest.getMacroMachine() != null )
            {
                macroMachine = tokenInitAndSendRequest.getMacroMachine();
            }
            else if ( tokenInitAndSendRequest.getUserInfo() != null )
            {
                macroMachine = MacroMachine.forUser(
                        commonValues.getPwmApplication(),
                        commonValues.getLocale(),
                        commonValues.getSessionLabel(),
                        userInfo.getUserIdentity(),
                        makeTokenDestStringReplacer( tokenInitAndSendRequest.getTokenDestinationItem() ) );
            }
            else
            {
                macroMachine = null;
            }
        }

        if ( userInfo != null )
        {
            final Instant userLastPasswordChange = userInfo.getPasswordLastModifiedTime();
            if ( userLastPasswordChange != null )
            {
                final String userChangeString = JavaHelper.toIsoDate( userLastPasswordChange );
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
                tokenPayload = commonValues.getPwmApplication().getTokenService().createTokenPayload(
                        tokenInitAndSendRequest.getTokenType(),
                        tokenLifetime,
                        tokenMapData,
                        userInfo == null ? null : userInfo.getUserIdentity(),
                        tokenInitAndSendRequest.getTokenDestinationItem()
                );
                tokenKey = commonValues.getPwmApplication().getTokenService().generateNewToken( tokenPayload, commonValues.getSessionLabel() );
            }
            catch ( final PwmOperationalException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
        }

        final EmailItemBean emailItemBean = tokenInitAndSendRequest.getEmailToSend() == null
                ? null
                : config.readSettingAsEmail( tokenInitAndSendRequest.getEmailToSend(), commonValues.getLocale() );

        final String smsMessage = tokenInitAndSendRequest.getSmsToSend() == null
                ? null
                : config.readSettingAsLocalizedString( tokenInitAndSendRequest.getSmsToSend(), commonValues.getLocale() );

        TokenService.TokenSender.sendToken(
                TokenService.TokenSendInfo.builder()
                        .pwmApplication( commonValues.getPwmApplication() )
                        .userInfo( userInfo )
                        .macroMachine( macroMachine )
                        .configuredEmailSetting( emailItemBean )
                        .tokenDestinationItem( tokenInitAndSendRequest.getTokenDestinationItem() )
                        .smsMessage( smsMessage )
                        .tokenKey( tokenKey )
                        .sessionLabel( commonValues.getSessionLabel() )
                        .build()
        );
    }

    public static MacroMachine.StringReplacer makeTokenDestStringReplacer( final TokenDestinationItem tokenDestinationItem )
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
        private MacroMachine macroMachine;
        private TimeDuration tokenLifetime;
    }
}
