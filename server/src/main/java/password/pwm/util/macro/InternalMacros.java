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

package password.pwm.util.macro;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class InternalMacros
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( InternalMacros.class );

    static final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> INTERNAL_MACROS;

    static
    {
        final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> defaultMacros = new HashMap<>();
        defaultMacros.put( PwmSettingReference.class, MacroImplementation.Scope.Static );
        defaultMacros.put( PwmSettingCategoryReference.class, MacroImplementation.Scope.Static );
        defaultMacros.put( PwmAppName.class, MacroImplementation.Scope.Static );
        defaultMacros.put( PwmVendorName.class, MacroImplementation.Scope.Static );
        defaultMacros.put( PwmContextPath.class, MacroImplementation.Scope.System );
        defaultMacros.put( EncodingMacro.class, MacroImplementation.Scope.Static );
        defaultMacros.put( CasingMacro.class, MacroImplementation.Scope.Static );
        defaultMacros.put( HashingMacro.class, MacroImplementation.Scope.Static );

        INTERNAL_MACROS = Collections.unmodifiableMap( defaultMacros );
    }

    abstract static class InternalAbstractMacro extends AbstractMacro
    {
        @Override
        public MacroDefinitionFlag[] flags( )
        {
            return new MacroDefinitionFlag[]
                    {
                            MacroDefinitionFlag.OnlyDebugLogging,
                    };
        }
    }

    public static class PwmSettingReference extends InternalAbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@PwmSettingReference" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue( final String matchValue, final MacroRequestInfo macroRequestInfo )
                throws MacroParseException
        {
            final String settingKeyStr = matchValue.substring( 21, matchValue.length() - 1 );
            if ( settingKeyStr.isEmpty() )
            {
                throw new MacroParseException( "PwmSettingReference macro requires a setting key value" );
            }
            final PwmSetting setting = PwmSetting.forKey( settingKeyStr );
            if ( setting == null )
            {
                throw new MacroParseException( "PwmSettingReference macro has unknown key value '" + settingKeyStr + "'" );
            }
            return setting.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
        }
    }

    public static class PwmSettingCategoryReference extends InternalAbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@PwmSettingCategoryReference" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue( final String matchValue, final MacroRequestInfo macroRequestInfo )
                throws MacroParseException
        {
            final String settingKeyStr = matchValue.substring( 29, matchValue.length() - 1 );
            if ( settingKeyStr.isEmpty() )
            {
                throw new MacroParseException( "PwmSettingCategoryReference macro requires a setting key value" );
            }
            final PwmSettingCategory category = PwmSettingCategory.forKey( settingKeyStr );
            if ( category == null )
            {
                throw new MacroParseException( "PwmSettingCategoryReference macro has unknown key value '" + settingKeyStr + "'" );
            }
            return category.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
        }
    }

    public static class PwmContextPath extends InternalAbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@PwmContextPath@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue( final String matchValue, final MacroRequestInfo macroRequestInfo )
                throws MacroParseException
        {
            String contextName = "[context]";
            final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();
            if ( pwmApplication != null )
            {
                final PwmEnvironment pwmEnvironment = pwmApplication.getPwmEnvironment();
                if ( pwmEnvironment != null )
                {
                    final ContextManager contextManager = pwmEnvironment.getContextManager();
                    if ( contextManager != null && contextManager.getContextPath() != null )
                    {
                        contextName = contextManager.getContextPath();
                    }
                }
            }
            return contextName;
        }
    }

    public static class EncodingMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@Encode:[^:]+:\\[\\[.*\\]\\]@" );
        // @Encode:ENCODE_TYPE:[[value]]@


        @Override
        public Sequence getSequence( )
        {
            return Sequence.post;
        }

        private enum EncodeType
        {
            urlPath,
            urlParameter,
            base64,;

            private String encode( final String input ) throws MacroParseException
            {
                switch ( this )
                {
                    case urlPath:
                        return StringUtil.urlEncode( input );

                    case urlParameter:
                        return StringUtil.urlEncode( input );

                    case base64:
                        return StringUtil.base64Encode( input.getBytes( PwmConstants.DEFAULT_CHARSET ) );

                    default:
                        throw new MacroParseException( "unimplemented encodeType '" + this.toString() + "' for Encode macro" );
                }
            }

            private static EncodeType forString( final String input )
            {
                for ( final EncodeType encodeType : EncodeType.values() )
                {
                    if ( encodeType.toString().equalsIgnoreCase( input ) )
                    {
                        return encodeType;
                    }
                }
                return null;
            }
        }


        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
                throws MacroParseException
        {
            if ( matchValue == null || matchValue.length() < 1 )
            {
                return "";
            }

            final String[] colonParts = matchValue.split( ":" );

            if ( colonParts.length < 3 )
            {
                throw new MacroParseException( "not enough arguments for Encode macro" );
            }

            final String encodeMethodStr = colonParts[ 1 ];
            final EncodeType encodeType = EncodeType.forString( encodeMethodStr );
            if ( encodeType == null )
            {
                throw new MacroParseException( "unknown encodeType '" + encodeMethodStr + "' for Encode macro" );
            }

            // can't use colonParts[2] as it may be split if value contains a colon.
            String value = matchValue;
            value = value.replaceAll( "^@Encode:[^:]+:\\[\\[", "" );
            value = value.replaceAll( "\\]\\]@$", "" );
            return encodeType.encode( value );
        }
    }

    public static class HashingMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@Hash:[^:]+:\\[\\[.*\\]\\]@" );
        // @Hash:HASH_TYPE:[[value]]@


        @Override
        public Sequence getSequence( )
        {
            return Sequence.post;
        }

        private enum HashType
        {
            md5,
            sha1,
            sha256,
            sha512,;

            private String hash( final String input ) throws MacroParseException
            {
                switch ( this )
                {
                    case md5:
                        return doHash( input, PwmHashAlgorithm.MD5 );

                    case sha1:
                        return doHash( input, PwmHashAlgorithm.SHA1 );

                    case sha256:
                        return doHash( input, PwmHashAlgorithm.SHA256 );

                    case sha512:
                        return doHash( input, PwmHashAlgorithm.SHA512 );

                    default:
                        throw new MacroParseException( "unimplemented hashtype '" + this.toString() + "' for Hash macro" );
                }
            }

            private String doHash( final String input, final PwmHashAlgorithm pwmHashAlgorithm )
                    throws MacroParseException
            {
                if ( StringUtil.isEmpty( input ) )
                {
                    return "";
                }
                final byte[] inputBytes = input.getBytes( PwmConstants.DEFAULT_CHARSET );
                final String hashOutput;
                try
                {
                    hashOutput = SecureEngine.hash( inputBytes, pwmHashAlgorithm );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    throw new MacroParseException( "error during hash operation: " + e.getMessage() );
                }
                return hashOutput.toLowerCase();
            }

            private static HashType forString( final String input )
            {
                for ( final HashType encodeType : HashType.values() )
                {
                    if ( encodeType.toString().equalsIgnoreCase( input ) )
                    {
                        return encodeType;
                    }
                }
                return null;
            }
        }


        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
                throws MacroParseException
        {
            if ( matchValue == null || matchValue.length() < 1 )
            {
                return "";
            }

            final String[] colonParts = matchValue.split( ":" );

            if ( colonParts.length < 3 )
            {
                throw new MacroParseException( "not enough arguments for Encode macro" );
            }

            final String encodeMethodStr = colonParts[ 1 ];
            final HashType encodeType = HashType.forString( encodeMethodStr );
            if ( encodeType == null )
            {
                throw new MacroParseException( "unknown encodeType '" + encodeMethodStr + "' for Encode macro" );
            }

            // can't use colonParts[2] as it may be split if value contains a colon.
            String value = matchValue;
            value = value.replaceAll( "^@Hash:[^:]+:\\[\\[", "" );
            value = value.replaceAll( "\\]\\]@$", "" );
            return encodeType.hash( value );
        }
    }

    public static class CasingMacro extends AbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@Case:[^:]+:\\[\\[.*\\]\\]@" );
        // @Case:CASE_TYPE:[[value]]@


        @Override
        public Sequence getSequence( )
        {
            return Sequence.post;
        }

        private enum CaseType
        {
            upper,
            lower,;

            private String hash( final String input ) throws MacroParseException
            {
                switch ( this )
                {
                    case upper:
                        return StringUtil.isEmpty( input )
                                ? ""
                                : input.toUpperCase();

                    case lower:
                        return StringUtil.isEmpty( input )
                                ? ""
                                : input.toLowerCase();

                    default:
                        throw new MacroParseException( "unimplemented casetype '" + this.toString() + "' for Case macro" );
                }
            }

            private static CaseType forString( final String input )
            {
                for ( final CaseType encodeType : CaseType.values() )
                {
                    if ( encodeType.toString().equalsIgnoreCase( input ) )
                    {
                        return encodeType;
                    }
                }
                return null;
            }
        }


        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
                throws MacroParseException
        {
            if ( matchValue == null || matchValue.length() < 1 )
            {
                return "";
            }

            final String[] colonParts = matchValue.split( ":" );

            if ( colonParts.length < 3 )
            {
                throw new MacroParseException( "not enough arguments for Case macro" );
            }

            final String encodeMethodStr = colonParts[ 1 ];
            final CaseType encodeType = CaseType.forString( encodeMethodStr );
            if ( encodeType == null )
            {
                throw new MacroParseException( "unknown caseType '" + encodeMethodStr + "' for Case macro" );
            }

            // can't use colonParts[2] as it may be split if value contains a colon.
            String value = matchValue;
            value = value.replaceAll( "^@Case:[^:]+:\\[\\[", "" );
            value = value.replaceAll( "\\]\\]@$", "" );
            return encodeType.hash( value );
        }
    }

    public static class PwmAppName extends InternalAbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@PwmAppName@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue( final String matchValue, final MacroRequestInfo macroRequestInfo )
                throws MacroParseException
        {
            return PwmConstants.PWM_APP_NAME;
        }
    }

    public static class PwmVendorName extends InternalAbstractMacro
    {
        private static final Pattern PATTERN = Pattern.compile( "@PwmVendorName@" );

        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        public String replaceValue( final String matchValue, final MacroRequestInfo macroRequestInfo )
                throws MacroParseException
        {
            return PwmConstants.PWM_VENDOR_NAME;
        }
    }
}
