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
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.PwmOperationalException;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class VerificationMethodValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( VerificationMethodValue.class );

    private final VerificationMethodSettings value;


    public enum EnabledState
    {
        disabled,
        required,
        optional,
    }

    public static class VerificationMethodSettings implements Serializable
    {
        private final Map<IdentityVerificationMethod, VerificationMethodSetting> methodSettings;
        private final int minOptionalRequired;

        public VerificationMethodSettings( )
        {
            methodSettings = Collections.emptyMap();
            minOptionalRequired = 0;
        }

        public VerificationMethodSettings( final Map<IdentityVerificationMethod, VerificationMethodSetting> methodSettings, final int minOptionalRequired )
        {
            this.methodSettings = methodSettings == null ? Collections.emptyMap() : Collections.unmodifiableMap( methodSettings );
            this.minOptionalRequired = minOptionalRequired;
        }

        public Map<IdentityVerificationMethod, VerificationMethodSetting> getMethodSettings( )
        {
            final Map<IdentityVerificationMethod, VerificationMethodSetting> tempMap = new LinkedHashMap<>( methodSettings );
            tempMap.remove( null );
            return Collections.unmodifiableMap( tempMap );
        }

        public int getMinOptionalRequired( )
        {
            return minOptionalRequired;
        }
    }

    @Value
    public static class VerificationMethodSetting implements Serializable
    {
        private EnabledState enabledState;
    }

    public VerificationMethodValue( )
    {
        this( new VerificationMethodSettings() );
    }

    public VerificationMethodValue( final VerificationMethodSettings value )
    {
        this.value = value;
        for ( final IdentityVerificationMethod recoveryVerificationMethods : IdentityVerificationMethod.availableValues() )
        {
            if ( !value.methodSettings.containsKey( recoveryVerificationMethods ) )
            {
                value.methodSettings.put( recoveryVerificationMethods, new VerificationMethodSetting( EnabledState.disabled ) );
            }
        }
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public VerificationMethodValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new VerificationMethodValue();
                }
                else
                {
                    final VerificationMethodSettings settings = JsonUtil.deserialize( input, VerificationMethodSettings.class );
                    return new VerificationMethodValue( settings );
                }
            }

            public VerificationMethodValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
                    throws PwmOperationalException
            {
                final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                if ( valueElement.isPresent() )
                {
                    final String inputStr = valueElement.get().getText();
                    final VerificationMethodSettings settings = JsonUtil.deserialize( inputStr, VerificationMethodSettings.class );
                    return new VerificationMethodValue( settings );
                }
                return  new VerificationMethodValue(  );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
        valueElement.addText( JsonUtil.serialize( value ) );
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
                : JsonUtil.serializeCollection( optionals )
        );
        out.append( ", required methods: " ).append( required.isEmpty()
                ? LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, null )
                : JsonUtil.serializeCollection( required )
        );

        if ( value.getMinOptionalRequired() > 0 )
        {
            out.append( ",  minimum optional methods required: " ).append( value.getMinOptionalRequired() );
        }
        return out.toString();
    }
}
