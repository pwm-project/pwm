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

package password.pwm.svc.otp;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Optional;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class LdapOtpOperator extends AbstractOtpOperator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapOtpOperator.class );

    public LdapOtpOperator( final PwmDomain pwmDomain )
    {
        setPwmApplication( pwmDomain );
    }

    /**
     * Read OTP secret and instantiate a OTP User Configuration object.
     */
    @Override
    public Optional<OTPUserRecord> readOtpUserConfiguration(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final String userGUID
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = getPwmApplication().getConfig();
        final LdapProfile ldapProfile = config.getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final String ldapStorageAttribute = ldapProfile.readSettingAsString( PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE );
        if ( ldapStorageAttribute == null || ldapStorageAttribute.length() < 1 )
        {
            final String errorMsg = "ldap storage attribute is not configured, unable to read OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        try
        {
            final ChaiUser theUser = pwmDomain.getProxiedChaiUser( sessionLabel, userIdentity );
            final String value = theUser.readStringAttribute( ldapStorageAttribute );
            if ( StringUtil.notEmpty( value ) && config.readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
            {
                final String decryptAttributeValue = decryptAttributeValue( value );

                if ( decryptAttributeValue != null )
                {
                    final OTPUserRecord otp = decomposeOtpAttribute( decryptAttributeValue );
                    return Optional.ofNullable( otp );
                }
            }
        }
        catch ( final ChaiOperationException | ChaiUnavailableException e )
        {
            final String errorMsg = "unexpected LDAP error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        return Optional.empty();
    }

    @Override
    public void writeOtpUserConfiguration(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final String userGuid,
            final OTPUserRecord otpConfig
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();
        final LdapProfile ldapProfile = config.getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final String ldapStorageAttribute = ldapProfile.readSettingAsString( PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE );
        if ( ldapStorageAttribute == null || ldapStorageAttribute.length() < 1 )
        {
            final String errorMsg = "ldap storage attribute is not configured, unable to write OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        String value = composeOtpAttribute( otpConfig );
        if ( value == null || value.length() == 0 )
        {
            final String errorMsg = "Invalid value for OTP secret, unable to store";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        try
        {
            if ( config.readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
            {
                value = encryptAttributeValue( value );
            }
            final ChaiUser theUser = pwmRequest == null
                    ? pwmDomain.getProxiedChaiUser( null, userIdentity )
                    : pwmRequest.getClientConnectionHolder().getActor( userIdentity );
            theUser.writeStringAttribute( ldapStorageAttribute, value );
            LOGGER.info( () -> "saved OTP secret for user to chai-ldap format" );
        }
        catch ( final ChaiException ex )
        {
            final String errorMsg;
            if ( ex.getErrorCode() == ChaiError.NO_ACCESS )
            {
                errorMsg = "permission error writing OTP secret to ldap attribute '"
                        + ldapStorageAttribute
                        + "', user does not appear to have correct permissions to save OTP secret: " + ex.getMessage();
            }
            else
            {
                errorMsg = "error writing OTP secret to ldap attribute '" + ldapStorageAttribute + "': " + ex.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, errorMsg );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo, ex );
            throw pwmOE;
        }
    }

    @Override
    public void clearOtpUserConfiguration(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final ChaiUser chaiUser,
            final String userGuid
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();

        final LdapProfile ldapProfile = config.getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final String ldapStorageAttribute = ldapProfile.readSettingAsString( PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE );
        if ( ldapStorageAttribute == null || ldapStorageAttribute.length() < 1 )
        {
            final String errorMsg = "ldap storage attribute is not configured, unable to clear OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        try
        {
            chaiUser.deleteAttribute( ldapStorageAttribute, null );
            LOGGER.info( () -> "cleared OTP secret for user to chai-ldap format" );
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg;
            if ( e.getErrorCode() == ChaiError.NO_ACCESS )
            {
                errorMsg = "permission error clearing responses to ldap attribute '"
                        + ldapStorageAttribute
                        + "', user does not appear to have correct permissions to clear OTP secret: " + e.getMessage();
            }
            else
            {
                errorMsg = "error clearing OTP secret to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, errorMsg );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo, e );
            throw pwmOE;
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
    }

    /**
     * Close the operator. Does nothing in this case.
     */
    @Override
    public void close( )
    {
    }
}
