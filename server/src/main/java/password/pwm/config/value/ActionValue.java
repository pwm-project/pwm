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
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.X509Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class ActionValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ActionValue.class );
    private static final int CURRENT_SYNTAX_VERSION = 2;

    final List<ActionConfiguration> values;

    public ActionValue( final List<ActionConfiguration> values )
    {
        this.values = List.copyOf( CollectionUtil.stripNulls( values ) );
    }

    public static StoredValueFactory factory( )
    {
        return new ActionStoredValueFactory();
    }

    private static class ActionStoredValueFactory implements StoredValueFactory
    {
        @Override
        public ActionValue fromJson( final String input )
        {
            return input == null
                    ? new ActionValue( Collections.emptyList() )
                    : new ActionValue( List.copyOf( JsonFactory.get().deserializeList( input, ActionConfiguration.class ) ) );
        }

        @Override
        public ActionValue fromXmlElement(
                final PwmSetting pwmSetting,
                final XmlElement settingElement,
                final PwmSecurityKey pwmSecurityKey
        )
                throws PwmOperationalException
        {
            final int syntaxVersion = figureCurrentStoredSyntax( settingElement );
            final List<XmlElement> valueElements = settingElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE );

            final List<ActionConfiguration> values = new ArrayList<>( valueElements.size() );
            for ( final XmlElement loopValueElement : valueElements )
            {
                final Optional<String> stringValue = loopValueElement.getText();
                if ( stringValue.isPresent() )
                {
                    if ( syntaxVersion < 2 )
                    {
                        parseV1configurationValue( pwmSecurityKey, loopValueElement, stringValue.get() ).ifPresent( values::add );
                    }
                    else if ( syntaxVersion == 2 )
                    {
                        parseV2configurationValue( pwmSetting, pwmSecurityKey, stringValue.get() ).ifPresent( values::add );
                    }
                    else
                    {
                        throw new IllegalStateException( "unexpected syntax type " + syntaxVersion );
                    }
                }
            }

            return new ActionValue( values );
        }

        private Optional<ActionConfiguration> parseV1configurationValue( final PwmSecurityKey pwmSecurityKey, final XmlElement loopValueElement, final String stringValue )
                throws PwmOperationalException
        {
            final XmlElement settingElement = loopValueElement.parent().orElseThrow();
            final boolean oldType = PwmSettingSyntax.STRING_ARRAY.toString().equals(
                    settingElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_SYNTAX ).orElse( "" ) );

            if ( oldType )
            {
                if ( loopValueElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_LOCALE ).isEmpty() )
                {
                    final ActionConfiguration.ActionConfigurationOldVersion1 oldVersion1 = ActionConfiguration.ActionConfigurationOldVersion1
                            .parseOldConfigString( stringValue );
                    return Optional.of( convertOldVersion1Values( oldVersion1 ) );
                }
            }
            else
            {
                final ActionConfiguration.ActionConfigurationOldVersion1 parsedAc = JsonFactory.get()
                        .deserialize( stringValue, ActionConfiguration.ActionConfigurationOldVersion1.class );
                if ( parsedAc != null )
                {
                    final Optional<String> decodedValue = StoredValueEncoder.decode(
                            parsedAc.getPassword(),
                            StoredValueEncoder.Mode.ENCODED,
                            pwmSecurityKey );

                    if ( decodedValue.isPresent() )
                    {
                        return Optional.of( convertOldVersion1Values( parsedAc.toBuilder().password( decodedValue.get() ).build() ) );
                    }
                }
            }

            return Optional.empty();
        }

        private Optional<ActionConfiguration> parseV2configurationValue( final PwmSetting pwmSetting, final PwmSecurityKey pwmSecurityKey, final String stringValue )
        {
            final ActionConfiguration value = JsonFactory.get().deserialize( stringValue, ActionConfiguration.class );
            final List<ActionConfiguration.WebAction> clonedWebActions = new ArrayList<>( value.getWebActions().size() );

            for ( final ActionConfiguration.WebAction webAction : value.getWebActions() )
            {
                // add success status if empty list
                final List<Integer> successStatus = CollectionUtil.isEmpty( webAction.getSuccessStatus() )
                        ? Collections.singletonList( 200 )
                        : webAction.getSuccessStatus();

                // decrypt pw
                Optional<String> decodedValue = Optional.empty();
                try
                {
                    decodedValue = StoredValueEncoder.decode(
                            webAction.getPassword(),
                            StoredValueEncoder.Mode.ENCODED,
                            pwmSecurityKey );
                }
                catch ( final PwmOperationalException e )
                {
                    LOGGER.warn( () -> "error decoding stored pw value on setting '" + pwmSetting.getKey() + "': " + e.getMessage() );
                }

                final String passwordValue = decodedValue.orElse( "" );
                clonedWebActions.add( webAction.toBuilder()
                        .password( passwordValue )
                        .successStatus( successStatus )
                        .build() );
            }

            return Optional.of( value.toBuilder().webActions( clonedWebActions ).build() );
        }
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final ActionConfiguration value : values )
        {
            final List<ActionConfiguration.WebAction> clonedWebActions = encodePasswordInWebActions( value.getWebActions(), xmlOutputProcessData );

            final ActionConfiguration clonedAction = value.toBuilder()
                    .webActions( clonedWebActions )
                    .build();

            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );

            valueElement.setText( JsonFactory.get().serialize( clonedAction ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    private static List<ActionConfiguration.WebAction> encodePasswordInWebActions(
            final List<ActionConfiguration.WebAction> webActions,
            final XmlOutputProcessData xmlOutputProcessData
    )
    {
        final List<ActionConfiguration.WebAction> clonedWebActions = new ArrayList<>( webActions.size() );

        for ( final ActionConfiguration.WebAction webAction : webActions )
        {
            try
            {
                final String encodedValue = StringUtil.isEmpty( webAction.getPassword() )
                        ? ""
                        : StoredValueEncoder.encode( webAction.getPassword(),
                                xmlOutputProcessData.getStoredValueEncoderMode(),
                                xmlOutputProcessData.getPwmSecurityKey() );
                clonedWebActions.add( webAction.toBuilder()
                        .password( encodedValue )
                        .build() );
            }
            catch ( final PwmOperationalException e )
            {
                throw new PwmInternalException( "error encoding stored pw value: " + e.getMessage() );
            }
        }
        return Collections.unmodifiableList( clonedWebActions );
    }

    @Override
    public List<ActionConfiguration> toNativeObject( )
    {
        return List.copyOf( values );
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.get( 0 ) == null )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        {
            final Set<String> seenNames = new HashSet<>( values.size() );
            for ( final ActionConfiguration actionConfiguration : values )
            {
                if ( seenNames.contains( actionConfiguration.getName().toLowerCase() ) )
                {
                    return Collections.singletonList( "each action name must be unique: " + actionConfiguration.getName() );
                }
                seenNames.add( actionConfiguration.getName().toLowerCase() );
            }
        }


        for ( final ActionConfiguration loopConfig : values )
        {
            try
            {
                loopConfig.validate();
            }
            catch ( final PwmOperationalException e )
            {
                return Collections.singletonList( "format error: " + e.getErrorInformation().toDebugStr() );
            }
        }

        return Collections.emptyList();
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        final ArrayList<ActionConfiguration> output = new ArrayList<>( values.size() );
        for ( final ActionConfiguration actionConfiguration : values )
        {
            final List<ActionConfiguration.WebAction> clonedWebActions = new ArrayList<>( actionConfiguration.getWebActions().size() );
            for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
            {
                final String debugPwdValue = StringUtil.notEmpty( webAction.getPassword() )
                        ? PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT
                        : null;

                clonedWebActions.add( webAction.toBuilder()
                        .password( debugPwdValue )
                        .build() );
            }

            final ActionConfiguration clonedAction = actionConfiguration.toBuilder().webActions( clonedWebActions ).build();
            output.add( clonedAction );
        }
        return output;
    }


    @Override
    public String toDebugString( final Locale locale )
    {
        final StringBuilder sb = new StringBuilder();
        int counter = 0;
        for ( final ActionConfiguration actionConfiguration : values )
        {
            sb.append( "Action name=" );
            sb.append( actionConfiguration.getName() );
            sb.append( " description=" );
            sb.append( actionConfiguration.getDescription() );

            for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
            {
                sb.append( "\n   WebServiceAction: " );
                sb.append( "\n    method=" ).append( webAction.getMethod() );
                sb.append( "\n    url=" ).append( webAction.getUrl() );
                if ( !CollectionUtil.isEmpty( webAction.getCertificates() ) )
                {
                    sb.append( "\n  certs=" ).append( X509Utils.makeDebugTexts( webAction.getCertificates() ) );
                }
                sb.append( "\n    headers=" ).append( JsonFactory.get().serializeMap( webAction.getHeaders() ) );
                sb.append( "\n    username=" ).append( webAction.getUsername() );
                sb.append( "\n    password=" ).append(
                        StringUtil.isEmpty( webAction.getPassword() )
                                ? ""
                                : PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT
                );
                if ( !CollectionUtil.isEmpty( webAction.getSuccessStatus() ) )
                {
                    sb.append( "\n    successStatus=" ).append( StringUtil.collectionToString( webAction.getSuccessStatus() ) );
                }
                if ( StringUtil.notEmpty( webAction.getBody() ) )
                {
                    sb.append( "\n    body=" ).append( webAction.getBody() );
                }
            }

            for ( final ActionConfiguration.LdapAction ldapAction : actionConfiguration.getLdapActions() )
            {
                sb.append( "\n   LdapAction: " );
                sb.append( "\n    method=" ).append( ldapAction.getLdapMethod() );
                sb.append( "\n    attribute=" ).append( ldapAction.getAttributeName() );
                sb.append( "\n    value=" ).append( ldapAction.getAttributeValue() );
            }
            counter++;
            if ( counter != values.size() )
            {
                sb.append( '\n' );
            }
        }
        return sb.toString();
    }

    public ActionConfiguration forName( final String name )
    {
        for ( final ActionConfiguration actionConfiguration : values )
        {
            if ( name.equals( actionConfiguration.getName() ) )
            {
                return actionConfiguration;
            }
        }
        return null;
    }

    @Override
    public int currentSyntaxVersion( )
    {
        return CURRENT_SYNTAX_VERSION;
    }

    private static int figureCurrentStoredSyntax( final XmlElement settingElement )
    {
        final Optional<String> storedSyntaxVersionString = settingElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_SYNTAX_VERSION );
        if ( storedSyntaxVersionString.isPresent() )
        {
            try
            {
                return Integer.parseInt( storedSyntaxVersionString.get() );
            }
            catch ( final NumberFormatException e )
            {
                LOGGER.debug( () -> "unable to parse syntax version for setting " + e.getMessage() );
            }
        }
        return 0;
    }

    private static ActionConfiguration convertOldVersion1Values( final ActionConfiguration.ActionConfigurationOldVersion1 oldAction )
    {
        final ActionConfiguration.ActionConfigurationBuilder builder = ActionConfiguration.builder();
        builder.name( oldAction.getName() );
        builder.description( oldAction.getDescription() );
        switch ( oldAction.getType() )
        {
            case ldap:
            {
                final ActionConfiguration.LdapAction ldapAction = ActionConfiguration.LdapAction.builder()
                        .attributeName( oldAction.getAttributeName() )
                        .attributeValue( oldAction.getAttributeValue() )
                        .ldapMethod( oldAction.getLdapMethod().getNewMethod() )
                        .build();

                builder.ldapActions( Collections.singletonList( ldapAction ) );
            }
            break;

            case webservice:
            {
                final ActionConfiguration.WebAction webAction = ActionConfiguration.WebAction.builder()
                        .username( oldAction.getUsername() )
                        .password( oldAction.getPassword() )
                        .body( oldAction.getBody() )
                        .certificates( oldAction.getCertificates() )
                        .headers( oldAction.getHeaders() )
                        .url( oldAction.getUrl() )
                        .method( oldAction.getMethod().getNewMethod() )
                        .build();

                builder.webActions( Collections.singletonList( webAction ) );

            }
            break;

            default:
                MiscUtil.unhandledSwitchStatement( oldAction.getType() );

        }


        return builder.build();
    }
}
