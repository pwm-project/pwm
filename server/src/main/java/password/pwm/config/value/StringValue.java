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

import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlElement;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringValue extends AbstractValue implements StoredValue
{
    protected String value;

    StringValue( )
    {
    }

    public StringValue( final String value )
    {
        this.value = value == null ? "" : value;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public StringValue fromJson( final PwmSetting pwmSetting, final String input )
            {
                final String newValue = JsonFactory.get().deserialize( input, String.class );
                return new StringValue( newValue );
            }

            @Override
            public StringValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                final String value = valueElement.flatMap( XmlElement::getText ).orElse( "" );
                return new StringValue( value );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
        valueElement.setText( value );
        return Collections.singletonList( valueElement );
    }

    @Override
    public String toNativeObject( )
    {
        return value;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        return validateValue( pwmSetting, value );
    }

    public static List<String> validateValue( final PwmSetting pwmSetting, final String value )
    {
        if ( pwmSetting.isRequired()
                && StringUtil.isEmpty( value ) )
        {
            return Collections.singletonList( "required value missing" );
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        if ( pattern != null )
        {
            final Matcher matcher = pattern.matcher( value );
            if ( StringUtil.notEmpty( value ) && !matcher.matches() )
            {
                return Collections.singletonList( "incorrect value format for value '" + value + "'" );
            }
        }

        if ( pwmSetting.getFlags().contains( PwmSettingFlag.emailSyntax )
                && StringUtil.isEmpty( value )
                && !FormConfiguration.testEmailAddress( null, value ) )
        {
            return Collections.singletonList( "Invalid email address format: '" + value + "'" );
        }

        if ( StringUtil.notEmpty( value ) && pwmSetting.getSyntax() == PwmSettingSyntax.DOMAIN )
        {
            final List<String> errorStrings = DomainID.validateUserValue( value );
            if ( !CollectionUtil.isEmpty( errorStrings ) )
            {
                return List.copyOf( errorStrings );
            }
        }

        if ( StringUtil.notEmpty( value ) && pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE )
        {
            final List<String> errorStrings = ProfileID.validateUserValue( value );
            if ( !CollectionUtil.isEmpty( errorStrings ) )
            {
                return List.copyOf( errorStrings );
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString(
            final Locale locale
    )
    {
        return value == null ? "" : value;
    }
}
