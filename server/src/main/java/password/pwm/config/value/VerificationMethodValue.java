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

package password.pwm.config.value;

import lombok.Value;
import org.jrivard.xmlchai.XmlElement;
import org.jrivard.xmlchai.XmlFactory;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class VerificationMethodValue extends AbstractValue implements StoredValue
{
    private final VerificationMethodSettings value;

    public enum EnabledState
    {
        disabled,
        required,
        optional,
    }

    public static class VerificationMethodSettings
    {
        private final Map<IdentityVerificationMethod, VerificationMethodSetting> methodSettings;
        private final int minOptionalRequired;

        public VerificationMethodSettings( )
        {
            methodSettings = Collections.emptyMap();
            minOptionalRequired = 0;
        }

        public VerificationMethodSettings(
                final Map<IdentityVerificationMethod, VerificationMethodSetting> methodSettings,
                final int minOptionalRequired
        )
        {
            this.methodSettings = Collections.unmodifiableMap( CollectionUtil.copiedEnumMap( methodSettings, IdentityVerificationMethod.class ) );
            this.minOptionalRequired = minOptionalRequired;
        }

        public Map<IdentityVerificationMethod, VerificationMethodSetting> getMethodSettings( )
        {
            return methodSettings;
        }

        public int getMinOptionalRequired( )
        {
            return minOptionalRequired;
        }
    }

    @Value
    public static class VerificationMethodSetting
    {
        private EnabledState enabledState;
    }

    public VerificationMethodValue( )
    {
        this( new VerificationMethodSettings() );
    }

    public VerificationMethodValue( final VerificationMethodSettings value )
    {
        this.value = normalizeSettings( normalizeSettings( value ) );
    }

    private static VerificationMethodSettings normalizeSettings( final VerificationMethodSettings input )
    {
        final Map<IdentityVerificationMethod, VerificationMethodValue.VerificationMethodSetting> tempMap = CollectionUtil.copiedEnumMap(
                input.getMethodSettings(),
                IdentityVerificationMethod.class );

        for ( final IdentityVerificationMethod recoveryVerificationMethods : IdentityVerificationMethod.values() )
        {
            if ( !tempMap.containsKey( recoveryVerificationMethods ) )
            {
                tempMap.put( recoveryVerificationMethods, new VerificationMethodSetting( EnabledState.disabled ) );
            }
        }

        return new VerificationMethodSettings( Collections.unmodifiableMap( tempMap ), input.getMinOptionalRequired() );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public VerificationMethodValue fromJson( final PwmSetting pwmSetting, final String input )
            {
                if ( input == null )
                {
                    return new VerificationMethodValue();
                }
                else
                {
                    final VerificationMethodSettings settings = JsonFactory.get().deserialize( input, VerificationMethodSettings.class );
                    return new VerificationMethodValue( settings );
                }
            }

            @Override
            public VerificationMethodValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                if ( valueElement.isPresent() )
                {
                    final Optional<String> inputStr = valueElement.get().getText();
                    if ( inputStr.isPresent() )
                    {
                        final VerificationMethodSettings settings = JsonFactory.get().deserialize( inputStr.get(), VerificationMethodSettings.class );
                        return new VerificationMethodValue( settings );
                    }
                }
                return  new VerificationMethodValue(  );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
        valueElement.setText( JsonFactory.get().serialize( value ) );
        return Collections.singletonList( valueElement );
    }

    @Override
    public Object toNativeObject( )
    {
        return value;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwm )
    {
        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        if ( value == null )
        {
            return "No Verification Methods";
        }
        final StringBuilder out = new StringBuilder();
        final List<String> optionals = new ArrayList<>();
        final List<String> required = new ArrayList<>();
        for ( final IdentityVerificationMethod method : value.getMethodSettings().keySet() )
        {
            switch ( value.getMethodSettings().get( method ).getEnabledState() )
            {
                case optional:
                    optionals.add( method.getLabel( null, locale ) );
                    break;

                case required:
                    required.add( method.getLabel( null, locale ) );
                    break;

                default:
                    // continue processing
                    break;
            }
            method.getLabel( null, locale );
        }

        out.append( "optional methods: " ).append( optionals.isEmpty()
                ? LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, null )
                : JsonFactory.get().serializeCollection( optionals )
        );
        out.append( ", required methods: " ).append( required.isEmpty()
                ? LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, null )
                : JsonFactory.get().serializeCollection( required )
        );

        if ( value.getMinOptionalRequired() > 0 )
        {
            out.append( ",  minimum optional methods required: " ).append( value.getMinOptionalRequired() );
        }
        return out.toString();
    }
}
