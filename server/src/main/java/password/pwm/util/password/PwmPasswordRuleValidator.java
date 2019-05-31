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

package password.pwm.util.password;

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.ws.client.rest.RestClientHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PwmPasswordRuleValidator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmPasswordRuleValidator.class );

    private final PwmApplication pwmApplication;
    private final PwmPasswordPolicy policy;
    private final Locale locale;
    private final Flag[] flags;


    public enum Flag
    {
        FailFast,
        BypassLdapRuleCheck,
    }

    public PwmPasswordRuleValidator(
            final PwmApplication pwmApplication,
            final PwmPasswordPolicy policy,
            final Flag... flags
    )
    {
        this.pwmApplication = pwmApplication;
        this.policy = policy;
        this.locale = PwmConstants.DEFAULT_LOCALE;
        this.flags = flags;
    }

    public PwmPasswordRuleValidator(
            final PwmApplication pwmApplication,
            final PwmPasswordPolicy policy,
            final Locale locale,
            final Flag... flags
    )
    {
        this.pwmApplication = pwmApplication;
        this.policy = policy;
        this.locale = locale;
        this.flags = flags;
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
            throw new PwmDataValidationException( errorResults.iterator().next() );
        }

        if ( user != null && !JavaHelper.enumArrayContainsValue( flags, Flag.BypassLdapRuleCheck ) )
        {
            try
            {
                LOGGER.trace( () -> "calling chai directory password validation checker" );
                user.testPasswordPolicy( password.getStringValue() );
            }
            catch ( UnsupportedOperationException e )
            {
                LOGGER.trace( () -> "Unsupported operation was thrown while validating password: " + e.toString() );
            }
            catch ( ChaiUnavailableException e )
            {
                pwmApplication.getStatisticsManager().incrementValue( Statistic.LDAP_UNAVAILABLE_COUNT );
                LOGGER.warn( "ChaiUnavailableException was thrown while validating password: " + e.toString() );
                throw e;
            }
            catch ( ChaiPasswordPolicyException e )
            {
                final ChaiError passwordError = e.getErrorCode();
                final PwmError pwmError = PwmError.forChaiError( passwordError );
                final ErrorInformation info = new ErrorInformation( pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError );
                LOGGER.trace( () -> "ChaiPasswordPolicyException was thrown while validating password: " + e.toString() );
                errorResults.add( info );
            }
        }

        if ( !errorResults.isEmpty() )
        {
            throw new PwmDataValidationException( errorResults.iterator().next() );
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
        if ( pwmApplication != null )
        {
            final List<ErrorInformation> externalResults = invokeExternalRuleMethods(
                    pwmApplication.getConfig(),
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
        return PasswordRuleChecks.extendedPolicyRuleChecker( pwmApplication, policy, passwordString, oldPasswordString, userInfo, flags );
    }

    public List<ErrorInformation> internalPwmPolicyValidator(
            final String password,
            final String oldPassword,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        return PasswordRuleChecks.extendedPolicyRuleChecker( pwmApplication, policy, password, oldPassword, userInfo, flags );
    }


    private static final String REST_RESPONSE_KEY_ERROR = "error";
    private static final String REST_RESPONSE_KEY_ERROR_MSG = "errorMessage";

    public List<ErrorInformation> invokeExternalRuleMethods(
            final Configuration config,
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
            final MacroMachine macroMachine = MacroMachine.forUser( pwmApplication, PwmConstants.DEFAULT_LOCALE, SessionLabel.SYSTEM_LABEL, userInfo.getUserIdentity() );
            final PublicUserInfoBean publicUserInfoBean = PublicUserInfoBean.fromUserInfoBean( userInfo, pwmApplication.getConfig(), locale, macroMachine );
            sendData.put( "userInfo", publicUserInfoBean );
        }

        final String jsonRequestBody = JsonUtil.serializeMap( sendData );
        try
        {
            final String responseBody = RestClientHelper.makeOutboundRestWSCall( pwmApplication, locale, restURL,
                    jsonRequestBody );
            final Map<String, Object> responseMap = JsonUtil.deserialize( responseBody,
                    new TypeToken<Map<String, Object>>()
                    {
                    }
            );
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
        catch ( PwmOperationalException e )
        {
            final String errorMsg = "error executing external rule REST call: " + e.getMessage();
            LOGGER.error( errorMsg );
            if ( haltOnError )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation(), e );
            }
            throw new IllegalStateException( "http response error code: " + e.getMessage() );
        }
        return returnedErrors;
    }
}

