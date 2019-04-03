/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config.value;

import com.google.gson.reflect.TypeToken;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.ActionConfigurationOldVersion1;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.X509Utils;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ActionValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ActionValue.class );
    private static final int CURRENT_SYNTAX_VERSION = 2;

    final List<ActionConfiguration> values;

    public ActionValue( final List<ActionConfiguration> values )
    {
        this.values = Collections.unmodifiableList( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public ActionValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new ActionValue( Collections.emptyList() );
                }
                else
                {
                    List<ActionConfiguration> srcList = JsonUtil.deserialize( input,
                            new TypeToken<List<ActionConfiguration>>()
                            {
                            }
                    );

                    srcList = srcList == null ? Collections.emptyList() : srcList;
                    while ( srcList.contains( null ) )
                    {
                        srcList.remove( null );
                    }
                    return new ActionValue( Collections.unmodifiableList( srcList ) );
                }
            }

            public ActionValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey pwmSecurityKey
            )
                    throws PwmOperationalException
            {
                final int syntaxVersion = figureCurrentStoredSyntax( settingElement );
                final List<ActionConfiguration> values = new ArrayList<>();

                final boolean oldType = PwmSettingSyntax.STRING_ARRAY.toString().equals(
                        settingElement.getAttributeValue( "syntax" ) );
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final String stringValue = loopValueElement.getText();
                    if ( !StringUtil.isEmpty( stringValue ) )
                    {
                        if ( syntaxVersion < 2 )
                        {
                            if ( oldType )
                            {
                                if ( loopValueElement.getAttributeValue( "locale" ) == null )
                                {
                                    final ActionConfigurationOldVersion1 oldVersion1 = ActionConfigurationOldVersion1.parseOldConfigString( stringValue );
                                    values.add( convertOldVersion1Values( oldVersion1 ) );
                                }
                            }
                            else
                            {
                                final ActionConfigurationOldVersion1 parsedAc = JsonUtil.deserialize( stringValue, ActionConfigurationOldVersion1.class );
                                parsedAc.setPassword( decryptPwValue( parsedAc.getPassword(), pwmSecurityKey ) );
                                values.add( convertOldVersion1Values( parsedAc ) );
                            }
                        }
                        else if ( syntaxVersion == 2 )
                        {
                            final ActionConfiguration value = JsonUtil.deserialize( stringValue, ActionConfiguration.class );
                            final List<ActionConfiguration.WebAction> clonedWebActions = new ArrayList<>();
                            for ( final ActionConfiguration.WebAction webAction : value.getWebActions() )
                            {
                                // add success status if empty list
                                final List<Integer> successStatus = JavaHelper.isEmpty( webAction.getSuccessStatus() )
                                        ? Collections.singletonList( 200 )
                                        : webAction.getSuccessStatus();

                                // decrypt pw
                                try
                                {
                                    clonedWebActions.add( webAction.toBuilder()
                                            .password( decryptPwValue( webAction.getPassword(), pwmSecurityKey ) )
                                            .successStatus( successStatus )
                                            .build() );
                                }
                                catch ( PwmOperationalException e )
                                {
                                    LOGGER.warn( "error decoding stored pw value: " + e.getMessage() );
                                }
                            }

                            final ActionConfiguration clonedAction = value.toBuilder().webActions( clonedWebActions ).build();
                            values.add( clonedAction );
                        }
                        else
                        {
                            throw new IllegalStateException( "unexpected syntax type " + syntaxVersion );
                        }
                    }
                }

                return new ActionValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final ActionConfiguration value : values )
        {
            final List<ActionConfiguration.WebAction> clonedWebActions = new ArrayList<>();
            for ( final ActionConfiguration.WebAction webAction : value.getWebActions() )
            {
                try
                {
                    clonedWebActions.add( webAction.toBuilder()
                            .password( encryptPwValue( webAction.getPassword(), pwmSecurityKey ) )
                            .build() );
                }
                catch ( PwmOperationalException e )
                {
                    LOGGER.warn( "error encoding stored pw value: " + e.getMessage() );
                }
            }

            final ActionConfiguration clonedAction = value.toBuilder().webActions( clonedWebActions ).build();


            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );

            valueElement.addText( JsonUtil.serialize( clonedAction ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    public List<ActionConfiguration> toNativeObject( )
    {
        return Collections.unmodifiableList( values );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.get( 0 ) == null )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Set<String> seenNames = new HashSet<>();
        for ( final ActionConfiguration actionConfiguration : values )
        {
            if ( seenNames.contains( actionConfiguration.getName().toLowerCase() ) )
            {
                return Collections.singletonList( "each action name must be unique: " + actionConfiguration.getName() );
            }
            seenNames.add( actionConfiguration.getName().toLowerCase() );
        }


        for ( final ActionConfiguration loopConfig : values )
        {
            try
            {
                loopConfig.validate();
            }
            catch ( PwmOperationalException e )
            {
                return Collections.singletonList( "format error: " + e.getErrorInformation().toDebugStr() );
            }
        }

        return Collections.emptyList();
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        final ArrayList<ActionConfiguration> output = new ArrayList<>();
        for ( final ActionConfiguration actionConfiguration : values )
        {
            final List<ActionConfiguration.WebAction> clonedWebActions = new ArrayList<>();
            for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
            {
                final String debugPwdValue = !StringUtil.isEmpty( webAction.getPassword() )
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
                sb.append( "\n    headers=" ).append( JsonUtil.serializeMap( webAction.getHeaders() ) );
                sb.append( "\n    username=" ).append( webAction.getUsername() );
                sb.append( "\n    password=" ).append(
                        StringUtil.isEmpty( webAction.getPassword() )
                                ? ""
                                : PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT
                );
                if ( !JavaHelper.isEmpty( webAction.getSuccessStatus() ) )
                {
                    sb.append( "\n    successStatus=" ).append( StringUtil.collectionToString( webAction.getSuccessStatus() ) );
                }
                if ( StringUtil.isEmpty( webAction.getBody() ) )
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
                sb.append( "\n" );
            }
        }
        return sb.toString();
    }


    /**
     * Convert to json map where the certificate values are replaced with debug info for display in the config editor.
     *
     * @return a map suitable for json serialization for debug purposes
     */
    public List<Map<String, Object>> toInfoMap( )
    {
        final String originalJson = JsonUtil.serializeCollection( values );
        final List<Map<String, Object>> tempObj = JsonUtil.deserialize( originalJson, new TypeToken<List<Map<String, Object>>>()
        {
        } );

        int actionConfigurationCounter = 0;
        for ( final ActionConfiguration actionConfiguration : values )
        {
            final Map actionConfigurationMap = tempObj.get( actionConfigurationCounter );
            int webActionCounter = 0;
            for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
            {
                final List webActionsList = (List) actionConfigurationMap.get( "webActions" );
                if ( !JavaHelper.isEmpty( webAction.getCertificates() ) )
                {
                    final Map webActionMap = (Map) webActionsList.get( webActionCounter );
                    final List<Map<String, String>> certificateInfos = new ArrayList<>();
                    for ( final X509Certificate certificate : webAction.getCertificates() )
                    {
                        certificateInfos.add( X509Utils.makeDebugInfoMap( certificate, X509Utils.DebugInfoFlag.IncludeCertificateDetail ) );
                    }
                    webActionMap.put( "certificateInfos", certificateInfos );
                }
                webActionCounter++;
            }
            actionConfigurationCounter++;
        }


        return tempObj;
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
        final String storedSyntaxVersionString = settingElement.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_SYNTAX_VERSION );
        if ( !StringUtil.isEmpty( storedSyntaxVersionString ) )
        {
            try
            {
                return Integer.parseInt( storedSyntaxVersionString );
            }
            catch ( NumberFormatException e )
            {
                LOGGER.debug( () -> "unable to parse syntax version for setting " + e.getMessage() );
            }
        }
        return 0;
    }

    private static ActionConfiguration convertOldVersion1Values( final ActionConfigurationOldVersion1 oldAction )
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
                JavaHelper.unhandledSwitchStatement( oldAction.getType() );

        }


        return builder.build();
    }
}
