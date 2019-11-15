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

package password.pwm.config;

import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class StoredValueEncoder
{
    public enum SecureOutputMode
    {
        Plain( new PlaintextModeEngine(), "PLAIN" + DELIMITER, "PLAINTEXT" + DELIMITER, "RAW" + DELIMITER ),
        Stripped( new StrippedModeEngine(), "REMOVED" + DELIMITER ),
        Encoded( new EncodedModeEngine(), "ENC-PW" + DELIMITER, "ENCODED" + DELIMITER ),;

        private final List<String> prefixes;
        private final SecureOutputEngine secureOutputEngine;

        SecureOutputMode( final SecureOutputEngine secureOutputEngine, final String... prefixes )
        {
            this.secureOutputEngine = secureOutputEngine;
            this.prefixes = Collections.unmodifiableList( Arrays.asList( prefixes ) );
        }

        public List<String> getPrefixes()
        {
            return prefixes;
        }

        public String getPrefix()
        {
            return prefixes.iterator().next();
        }

        public SecureOutputEngine getSecureOutputEngine()
        {
            return secureOutputEngine;
        }
    }


    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredValueEncoder.class );
    private static final String DELIMITER = ":";

    public static Optional<String> decode(
            final String input,
            final StoredValue.OutputConfiguration outputConfiguration
    )
            throws PwmOperationalException
    {
        return decode( input, outputConfiguration.getSecureOutputMode(), outputConfiguration.getPwmSecurityKey() );
    }


    public static Optional<String> decode(
            final String input,
            final SecureOutputMode modeHint,
            final PwmSecurityKey pwmSecurityKey
    )
            throws PwmOperationalException
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Optional.empty();
        }

        final ParsedInput parsedInput = ParsedInput.parseInput( input );
        final SecureOutputMode requestedMode = modeHint == null ? SecureOutputMode.Plain : modeHint;
        final SecureOutputMode effectiveMode = parsedInput.getSecureOutputMode() == null
                ? requestedMode
                : parsedInput.getSecureOutputMode();
        return Optional.ofNullable( effectiveMode.getSecureOutputEngine().decode( parsedInput, pwmSecurityKey ) );
    }

    public static String encode( final String realValue, final SecureOutputMode mode, final PwmSecurityKey pwmSecurityKey )
            throws PwmOperationalException
    {
        return mode.getSecureOutputEngine().encode( realValue, pwmSecurityKey );
    }

    @Value
    private static class ParsedInput
    {
        private SecureOutputMode secureOutputMode;
        private String value;

        static ParsedInput parseInput( final String value )
        {
            if ( !StringUtil.isEmpty( value ) )
            {
                for ( final SecureOutputMode mode : SecureOutputMode.values() )
                {
                    for ( final String prefix : mode.getPrefixes() )
                    {
                        if ( value.startsWith( prefix ) )
                        {
                            return new ParsedInput( mode, value.substring( prefix.length() ) );
                        }
                    }
                }
            }

            return new ParsedInput( null, value );
        }
    }

    private interface SecureOutputEngine
    {
        String encode( String rawOutput, PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException;

        String decode( ParsedInput input, PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException;
    }


    private static class PlaintextModeEngine implements SecureOutputEngine
    {
        @Override
        public String encode( final String rawValue, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            return SecureOutputMode.Plain.getPrefix() + rawValue;
        }

        @Override
        public String decode( final ParsedInput input, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            return input.getValue();
        }
    }

    private static class StrippedModeEngine implements SecureOutputEngine
    {
        @Override
        public String encode( final String rawValue, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            return SecureOutputMode.Plain.getPrefix() + rawValue;
        }

        @Override
        public String decode( final ParsedInput input, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
        }
    }

    private static class EncodedModeEngine implements SecureOutputEngine
    {
        @Override
        public String encode( final String rawValue, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            if ( rawValue == null )
            {
                return SecureOutputMode.Encoded.getPrefix();
            }

            // make sure value isn't already encoded
            if ( ParsedInput.parseInput( rawValue ).getSecureOutputMode() == null )
            {
                try
                {
                    final String salt = PwmRandom.getInstance().alphaNumericString( 32 );
                    final StoredPwData storedPwData = new StoredPwData( salt, rawValue );
                    final String jsonData = JsonUtil.serialize( storedPwData );
                    final String encryptedValue = SecureEngine.encryptToString( jsonData, pwmSecurityKey, PwmBlockAlgorithm.CONFIG );
                    return SecureOutputMode.Encoded.getPrefix() + encryptedValue;
                }
                catch ( final Exception e )
                {
                    final String errorMsg = "unable to encrypt password value for setting: " + e.getMessage();
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                    throw new PwmOperationalException( errorInfo );
                }
            }

            return rawValue;
        }

        @Override
        public String decode( final ParsedInput input, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            try
            {
                final String pwValueSuffix = input.getValue( );
                final String decryptedValue = SecureEngine.decryptStringValue( pwValueSuffix, pwmSecurityKey, PwmBlockAlgorithm.CONFIG );
                final StoredPwData storedPwData = JsonUtil.deserialize( decryptedValue, StoredPwData.class );
                return storedPwData.getValue();
            }
            catch ( final Exception e )
            {
                final String errorMsg = "unable to decrypt password value for setting: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                LOGGER.warn( errorInfo.toDebugStr() );
                throw new PwmOperationalException( errorInfo );
            }
        }
    }

    @Value
    private static class StoredPwData implements Serializable
    {
        private String salt;
        private String value;
    }

}
