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

import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.OTPStorageFormat;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.util.Objects;
import java.util.Optional;

final class OtpServiceUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( OtpServiceUtil.class );

    private OtpServiceUtil()
    {
    }

    static String stringifyOtpRecord( final PwmDomain pwmDomain, final OTPUserRecord otpUserRecord )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( pwmDomain  );
        Objects.requireNonNull( otpUserRecord );

        final DomainConfig config = pwmDomain.getConfig();
        final OTPStorageFormat format = config.readSettingAsEnum( PwmSetting.OTP_SECRET_STORAGEFORMAT, OTPStorageFormat.class );

        final String value;
        value = format.getFormatter().stringifyRecord( otpUserRecord );

        if ( config.readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
        {
            return encryptAttributeValue( pwmDomain, value );
        }

        return value;
    }

    /**
     * Encrypt the given string using the PWM encryption key.
     *
     * @param unencrypted raw value to be encrypted
     *
     * @return the encrypted value
     * @throws PwmUnrecoverableException if the operation can't be completed
     */
    private static String encryptAttributeValue( final PwmDomain pwmDomain, final String unencrypted )
            throws PwmUnrecoverableException
    {
        final PwmBlockAlgorithm pwmBlockAlgorithm = figureBlockAlg( pwmDomain );
        final PwmSecurityKey pwmSecurityKey = pwmDomain.getConfig().getSecurityKey();
        return SecureEngine.encryptToString( unencrypted, pwmSecurityKey, pwmBlockAlgorithm );
    }

    private static PwmBlockAlgorithm figureBlockAlg( final PwmDomain pwmDomain )
    {
        final String otpEncryptionAlgString = pwmDomain.getConfig().readAppProperty( AppProperty.OTP_ENCRYPTION_ALG );
        return EnumUtil.readEnumFromString( PwmBlockAlgorithm.class, otpEncryptionAlgString )
                .orElse( PwmBlockAlgorithm.AES );
    }

    /**
     * Decrypt the given string using the PWM encryption key.
     *
     * @param encrypted value to be encrypted
     *
     * @return the decrypted value
     * @throws PwmUnrecoverableException if the operation can't be completed
     */
    private static String decryptAttributeValue( final PwmDomain pwmDomain, final String encrypted )
            throws PwmUnrecoverableException
    {
        final PwmBlockAlgorithm pwmBlockAlgorithm = figureBlockAlg( pwmDomain );
        final PwmSecurityKey pwmSecurityKey = pwmDomain.getConfig().getSecurityKey();
        return SecureEngine.decryptStringValue( encrypted, pwmSecurityKey, pwmBlockAlgorithm );
    }

    static Optional<OTPUserRecord> parseStoredOtpRecordValue(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final String value
    )
    {
        if ( StringUtil.isEmpty( value ) )
        {
            return Optional.empty();
        }

        final String effectiveValue;
        if ( pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.OTP_SECRET_ENCRYPT ) )
        {
            try
            {
                effectiveValue = decryptAttributeValue( pwmDomain, value );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.debug( sessionLabel, () -> "unable to decrypt user otp record: " + e.getMessage() );
                return Optional.empty();
            }
        }
        else
        {
            effectiveValue = value;
        }

        return decomposeOtpAttribute( sessionLabel, pwmDomain, effectiveValue );
    }

    private static Optional<OTPUserRecord> decomposeOtpAttribute(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final String value
    )
    {
        final OTPStorageFormat preferredFormat = pwmDomain.getConfig().readSettingAsEnum( PwmSetting.OTP_SECRET_STORAGEFORMAT, OTPStorageFormat.class );
        {
            final Optional<OTPUserRecord> preferredRecord = preferredFormat.getFormatter().parseStringRecord( sessionLabel, pwmDomain, value );
            if ( preferredRecord.isPresent() )
            {
                return preferredRecord;
            }
        }

        for ( final OTPStorageFormat format : OTPStorageFormat.values() )
        {
            if ( format != preferredFormat )
            {
                final Optional<OTPUserRecord> record = format.getFormatter()
                        .parseStringRecord( sessionLabel, pwmDomain, value );

                if ( record.isPresent() )
                {
                    LOGGER.debug( sessionLabel, () -> "otp decoded using configured preferred format " + format.name() );
                    return record;
                }
            }
        }

        LOGGER.trace( sessionLabel, () -> "could not parse stored otp attribute value using any format type " );

        return Optional.empty();
    }
}
