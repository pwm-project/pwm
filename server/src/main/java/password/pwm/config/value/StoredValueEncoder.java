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

package password.pwm.config.value;

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

public abstract class StoredValueEncoder
{
    private StoredValueEncoder()
    {
    }

    public enum Mode
    {
        PLAIN( new PlaintextModeEngine(), "PLAIN" + DELIMITER, "PLAINTEXT" + DELIMITER, "RAW" + DELIMITER ),
        STRIPPED( new StrippedModeEngine(), "REMOVED" + DELIMITER ),
        CONFIG_PW( new ConfigPwModeEngine(), "CONFIG-PW" + DELIMITER ),
        ENCODED( new EncodedModeEngine(), "ENC-PW" + DELIMITER, "ENCODED" + DELIMITER ),;

        private final List<String> prefixes;
        private final SecureOutputEngine secureOutputEngine;

        Mode( final SecureOutputEngine secureOutputEngine, final String... prefixes )
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
            final Mode modeHint,
            final PwmSecurityKey pwmSecurityKey
    )
            throws PwmOperationalException
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Optional.empty();
        }

        final ParsedInput parsedInput = ParsedInput.parseInput( input );
        final Mode requestedMode = modeHint == null ? Mode.PLAIN : modeHint;
        final Mode effectiveMode = parsedInput.getMode() == null
                ? requestedMode
                : parsedInput.getMode();
        return Optional.ofNullable( effectiveMode.getSecureOutputEngine().decode( parsedInput, pwmSecurityKey ) );
    }

    public static String encode( final String realValue, final Mode mode, final PwmSecurityKey pwmSecurityKey )
            throws PwmOperationalException
    {
        return mode.getSecureOutputEngine().encode( realValue, pwmSecurityKey );
    }

    @Value
    private static class ParsedInput
    {
        private Mode mode;
        private String value;

        static ParsedInput parseInput( final String value )
        {
            if ( !StringUtil.isEmpty( value ) )
            {
                for ( final Mode mode : Mode.values() )
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
            return Mode.PLAIN.getPrefix() + rawValue;
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
            return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
        }

        @Override
        public String decode( final ParsedInput input, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
        }
    }

    private static class ConfigPwModeEngine implements SecureOutputEngine
    {
        @Override
        public String encode( final String rawValue, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            try
            {
                final String encryptedValue = SecureEngine.encryptToString( rawValue, pwmSecurityKey, PwmBlockAlgorithm.CONFIG );
                return Mode.CONFIG_PW + encryptedValue;
            }
            catch ( final Exception e )
            {
                final String errorMsg = "unable to encrypt config-password value for setting: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                throw new PwmOperationalException( errorInfo );
            }
        }

        @Override
        public String decode( final ParsedInput input, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            try
            {
                return SecureEngine.decryptStringValue( input.getValue(), pwmSecurityKey, PwmBlockAlgorithm.CONFIG );
            }
            catch ( final Exception e )
            {
                final String errorMsg = "unable to decrypt config password value for setting: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                LOGGER.warn( () -> errorInfo.toDebugStr() );
                throw new PwmOperationalException( errorInfo );
            }
        }
    }

    private static class EncodedModeEngine implements SecureOutputEngine
    {
        @Override
        public String encode( final String rawValue, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
        {
            if ( rawValue == null )
            {
                return Mode.ENCODED.getPrefix();
            }

            // make sure value isn't already encoded
            if ( ParsedInput.parseInput( rawValue ).getMode() == null )
            {
                try
                {
                    final String salt = PwmRandom.getInstance().alphaNumericString( 32 );
                    final StoredPwData storedPwData = new StoredPwData( salt, rawValue );
                    final String jsonData = JsonUtil.serialize( storedPwData );
                    final String encryptedValue = SecureEngine.encryptToString( jsonData, pwmSecurityKey, PwmBlockAlgorithm.CONFIG );
                    return Mode.ENCODED.getPrefix() + encryptedValue;
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
                LOGGER.warn( () -> errorInfo.toDebugStr() );
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
