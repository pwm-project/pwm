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

package password.pwm.util.password;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoBean;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.ws.client.rest.RestClientHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class PwmPasswordRuleValidator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmPasswordRuleValidator.class );

    private final SessionLabel sessionLabel;
    private final PwmDomain pwmDomain;
    private final PwmPasswordPolicy policy;
    private final Locale locale;
    private final Flag[] flags;

    public enum Flag
    {
        FailFast,
        BypassLdapRuleCheck,
    }

    private PwmPasswordRuleValidator(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy policy,
            final Locale locale,
            final Flag... flags
    )
    {
        this.sessionLabel = sessionLabel;
        this.pwmDomain = Objects.requireNonNull( pwmDomain );
        this.policy = policy;
        this.locale = locale;
        this.flags = flags;
    }

    public static PwmPasswordRuleValidator create(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy policy,
            final Flag... flags
    )
    {
        return new PwmPasswordRuleValidator( sessionLabel, pwmDomain, policy, PwmConstants.DEFAULT_LOCALE, flags );
    }

    public static PwmPasswordRuleValidator create(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy policy,
            final Locale locale,
            final Flag... flags
    )
    {
        return new PwmPasswordRuleValidator( sessionLabel, pwmDomain, policy, locale, flags );
    }

    public boolean testPassword(
            final PasswordData password,
            final PasswordData oldPassword,
            final UserInfo userInfo,
            final ChaiUser user
    )
            throws PwmDataValidationException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final List<ErrorInformation> errorResults = validate( password, oldPassword, userInfo );

        if ( !errorResults.isEmpty() )
        {
            throw new PwmDataValidationException( errorResults.get( 0 ) );
        }

        if ( user != null && !JavaHelper.enumArrayContainsValue( flags, Flag.BypassLdapRuleCheck ) )
        {
            try
            {
                LOGGER.trace( () -> "calling chai directory password validation checker" );
                user.testPasswordPolicy( password.getStringValue() );
            }
            catch ( final UnsupportedOperationException e )
            {
                LOGGER.trace( () -> "Unsupported operation was thrown while validating password: " + e );
            }
            catch ( final ChaiUnavailableException e )
            {
                StatisticsClient.incrementStat( pwmDomain.getPwmApplication(), Statistic.LDAP_UNAVAILABLE_COUNT );
                LOGGER.warn( () -> "ChaiUnavailableException was thrown while validating password: " + e );
                throw e;
            }
            catch ( final ChaiPasswordPolicyException e )
            {
                final ChaiError passwordError = e.getErrorCode();
                final PwmError pwmError = PwmError.forChaiError( passwordError ).orElse( PwmError.PASSWORD_UNKNOWN_VALIDATION );
                final ErrorInformation info = new ErrorInformation( pwmError );
                LOGGER.trace( () -> "ChaiPasswordPolicyException was thrown while validating password: " + e );
                errorResults.add( info );
            }
        }

        if ( !errorResults.isEmpty() )
        {
            throw new PwmDataValidationException( errorResults.get( 0 ) );
        }

        return true;
    }


    /**
     * Validates a password against the configured rules of PWM.  No directory operations
     * are performed here.
     *
     * @param password desired new password
     * @return true if the password is okay, never returns false.
     */
    private List<ErrorInformation> validate(
            final PasswordData password,
            final PasswordData oldPassword,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final List<ErrorInformation> internalResults = internalPwmPolicyValidator( password, oldPassword, userInfo );
        if ( pwmDomain != null )
        {
            final List<ErrorInformation> externalResults = invokeExternalRuleMethods(
                    pwmDomain.getConfig(),
                    policy,
                    password,
                    userInfo
            );
            internalResults.addAll( externalResults );
        }
        return internalResults;
    }

    public List<ErrorInformation> internalPwmPolicyValidator(
            final PasswordData password,
            final PasswordData oldPassword,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final String passwordString = password == null ? "" : password.getStringValue();
        final String oldPasswordString = oldPassword == null ? null : oldPassword.getStringValue();
        return PasswordRuleChecks.extendedPolicyRuleChecker( sessionLabel, pwmDomain, policy, passwordString, oldPasswordString, userInfo, flags );
    }

    public List<ErrorInformation> internalPwmPolicyValidator(
            final String password,
            final String oldPassword,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        return PasswordRuleChecks.extendedPolicyRuleChecker( sessionLabel, pwmDomain, policy, password, oldPassword, userInfo, flags );
    }


    private static final String REST_RESPONSE_KEY_ERROR = "error";
    private static final String REST_RESPONSE_KEY_ERROR_MSG = "errorMessage";

    public List<ErrorInformation> invokeExternalRuleMethods(
            final DomainConfig config,
            final PwmPasswordPolicy pwmPasswordPolicy,
            final PasswordData password,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final List<ErrorInformation> returnedErrors = new ArrayList<>();
        final String restURL = config.readSettingAsString( PwmSetting.EXTERNAL_PWCHECK_REST_URLS );
        final boolean haltOnError = Boolean.parseBoolean( config.readAppProperty( AppProperty.WS_REST_CLIENT_PWRULE_HALTONERROR ) );
        final Map<String, Object> sendData = new LinkedHashMap<>();


        if ( restURL == null || restURL.isEmpty() )
        {
            return Collections.emptyList();
        }

        {
            final String passwordStr = password == null ? "" : password.getStringValue();
            sendData.put( "password", passwordStr );
        }

        if ( pwmPasswordPolicy != null )
        {
            final LinkedHashMap<String, Object> policyData = new LinkedHashMap<>();
            for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
            {
                policyData.put( rule.name(), pwmPasswordPolicy.getValue( rule ) );
            }
            sendData.put( "policy", policyData );
        }
        if ( userInfo != null )
        {
            final MacroRequest macroRequest = MacroRequest.forUser(
                    pwmDomain.getPwmApplication(),
                    PwmConstants.DEFAULT_LOCALE,
                    SessionLabel.SYSTEM_LABEL,
                    userInfo.getUserIdentity() );
            final PublicUserInfoBean publicUserInfoBean = UserInfoBean.toPublicUserInfoBean( userInfo, pwmDomain.getConfig(), locale, macroRequest );
            sendData.put( "userInfo", publicUserInfoBean );
        }

        final String jsonRequestBody = JsonFactory.get().serializeMap( sendData );
        try
        {
            final String responseBody = RestClientHelper.makeOutboundRestWSCall( pwmDomain, locale, restURL,
                    jsonRequestBody );
            final Map<String, Object> responseMap = JsonFactory.get().deserializeMap( responseBody,
                    String.class,
                    Object.class );
            if ( responseMap.containsKey( REST_RESPONSE_KEY_ERROR ) && Boolean.parseBoolean( responseMap.get(
                    REST_RESPONSE_KEY_ERROR ).toString() ) )
            {
                if ( responseMap.containsKey( REST_RESPONSE_KEY_ERROR_MSG ) )
                {
                    final String errorMessage = responseMap.get( REST_RESPONSE_KEY_ERROR_MSG ).toString();
                    LOGGER.trace( () -> "external web service reported error: " + errorMessage );
                    returnedErrors.add( new ErrorInformation( PwmError.PASSWORD_CUSTOM_ERROR, errorMessage, errorMessage, null ) );
                }
                else
                {
                    LOGGER.trace( () -> "external web service reported error without specifying an errorMessage" );
                    returnedErrors.add( new ErrorInformation( PwmError.PASSWORD_CUSTOM_ERROR ) );
                }
            }
            else
            {
                LOGGER.trace( () -> "external web service did not report an error" );
            }

        }
        catch ( final PwmOperationalException e )
        {
            final String errorMsg = "error executing external rule REST call: " + e.getMessage();
            LOGGER.error( () -> errorMsg );
            if ( haltOnError )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation(), e );
            }
            throw new IllegalStateException( "http response error code: " + e.getMessage() );
        }
        return returnedErrors;
    }
}

