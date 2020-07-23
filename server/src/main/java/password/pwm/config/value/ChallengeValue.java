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

import com.google.gson.reflect.TypeToken;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.ChallengeItemConfiguration;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class ChallengeValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ChallengeValue.class );

    //locale str as key.
    private final Map<String, List<ChallengeItemConfiguration>> values;

    ChallengeValue( final Map<String, List<ChallengeItemConfiguration>> values )
    {
        this.values = values == null ? Collections.emptyMap() : Collections.unmodifiableMap( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {

            public ChallengeValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new ChallengeValue( Collections.emptyMap() );
                }
                else
                {
                    Map<String, List<ChallengeItemConfiguration>> srcMap = JsonUtil.deserialize( input,
                            new TypeToken<Map<String, List<ChallengeItemConfiguration>>>()
                            {
                            }
                    );
                    srcMap = srcMap == null ? Collections.emptyMap() : new TreeMap<>(
                            srcMap );
                    return new ChallengeValue( Collections.unmodifiableMap( srcMap ) );
                }
            }

            public ChallengeValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey input
            )
            {
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final Map<String, List<ChallengeItemConfiguration>> values = new TreeMap<>();
                final boolean oldStyle = "LOCALIZED_STRING_ARRAY".equals( settingElement.getAttributeValue( "syntax" ) );
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final String localeString = loopValueElement.getAttributeValue(
                            "locale" ) == null ? "" : loopValueElement.getAttributeValue( "locale" );
                    final String value = loopValueElement.getText();
                    if ( !values.containsKey( localeString ) )
                    {
                        values.put( localeString, new ArrayList<ChallengeItemConfiguration>() );
                    }
                    final ChallengeItemConfiguration challengeItemBean;
                    if ( oldStyle )
                    {
                        challengeItemBean = parseOldVersionString( value );
                    }
                    else
                    {
                        challengeItemBean = JsonUtil.deserialize( value, ChallengeItemConfiguration.class );
                    }
                    if ( challengeItemBean != null )
                    {
                        values.get( localeString ).add( challengeItemBean );
                    }
                }
                return new ChallengeValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final Map.Entry<String, List<ChallengeItemConfiguration>> entry : values.entrySet() )
        {
            final String locale = entry.getKey();
            for ( final ChallengeItemConfiguration value : entry.getValue() )
            {
                if ( value != null )
                {
                    final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
                    valueElement.addText( JsonUtil.serialize( value ) );
                    if ( locale != null && locale.length() > 0 )
                    {
                        valueElement.setAttribute( "locale", locale );
                    }
                    returnList.add( valueElement );
                }
            }
        }
        return returnList;
    }

    public Map<String, List<ChallengeItemConfiguration>> toNativeObject( )
    {
        return Collections.unmodifiableMap( values );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.keySet().iterator().next().length() < 1 )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        if ( values != null )
        {
            for ( final Map.Entry<String, List<ChallengeItemConfiguration>> entry : values.entrySet() )
            {
                final String localeKey = entry.getKey();
                for ( final ChallengeItemConfiguration itemBean : entry.getValue() )
                {
                    if ( itemBean != null )
                    {
                        if ( itemBean.isAdminDefined() && ( itemBean.getText() == null || itemBean.getText().length() < 1 ) )
                        {
                            return Collections.singletonList( "admin-defined challenge must contain text (locale='" + localeKey + "')" );
                        }
                        if ( itemBean.getMinLength() < 1 )
                        {
                            return Collections.singletonList( "challenge minimum length must be greater than 0 (text=" + itemBean.getText() + ", locale='" + localeKey + "')" );
                        }
                        if ( itemBean.getMaxLength() > 255 )
                        {
                            return Collections.singletonList( "challenge maximum length must be less than 256 (text=" + itemBean.getText() + ", locale='" + localeKey + "')" );
                        }
                        if ( itemBean.getMinLength() > itemBean.getMaxLength() )
                        {
                            return Collections.singletonList( "challenge minimum length must be less than maximum length (text="
                                    + itemBean.getText() + ", locale='" + localeKey + "')" );
                        }
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    private static ChallengeItemConfiguration parseOldVersionString(
            final String inputString
    )
    {
        if ( inputString == null || inputString.length() < 1 )
        {
            return null;
        }

        int minLength = 2;
        int maxLength = 255;

        String challengeText = "";
        final String[] s1 = inputString.split( "::" );
        if ( s1.length > 0 )
        {
            challengeText = s1[ 0 ].trim();
        }
        if ( s1.length > 1 )
        {
            try
            {
                minLength = Integer.parseInt( s1[ 1 ] );
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "unexpected error parsing config input '" + inputString + "' " + e.getMessage() );
            }
        }
        if ( s1.length > 2 )
        {
            try
            {
                maxLength = Integer.parseInt( s1[ 2 ] );
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "unexpected error parsing config input '" + inputString + "' " + e.getMessage() );
            }
        }

        boolean adminDefined = true;
        if ( "%user%".equalsIgnoreCase( challengeText ) )
        {
            challengeText = "";
            adminDefined = false;
        }

        return ChallengeItemConfiguration.builder()
                .text( challengeText )
                .minLength( minLength )
                .maxLength( maxLength )
                .adminDefined( adminDefined )
                .build();
    }

    public String toDebugString( final Locale locale )
    {
        if ( values == null )
        {
            return "No Actions";
        }
        final StringBuilder sb = new StringBuilder();
        for ( final Map.Entry<String, List<ChallengeItemConfiguration>> entry : values.entrySet() )
        {
            final String localeKey = entry.getKey();
            final List<ChallengeItemConfiguration> challengeItems = entry.getValue();
            sb.append( "Locale: " ).append( LocaleHelper.debugLabel( LocaleHelper.parseLocaleString( localeKey ) ) ).append( "\n" );
            for ( final ChallengeItemConfiguration challengeItemBean : challengeItems )
            {
                sb.append( " ChallengeItem: [AdminDefined: " ).append( challengeItemBean.isAdminDefined() );
                sb.append( " MinLength:" ).append( challengeItemBean.getMinLength() );
                sb.append( " MaxLength:" ).append( challengeItemBean.getMaxLength() );
                sb.append( " MaxQuestionCharsInAnswer:" ).append( challengeItemBean.getMaxQuestionCharsInAnswer() );
                sb.append( " EnforceWordlist:" ).append( challengeItemBean.isEnforceWordlist() );
                sb.append( "]\n" );
                sb.append( "  Text:" ).append( challengeItemBean.getText() ).append( "\n" );
            }
        }
        return sb.toString();
    }
}
