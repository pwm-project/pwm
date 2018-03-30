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
import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
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
                    final Element settingElement,
                    final PwmSecurityKey pwmSecurityKey
            )
                    throws PwmOperationalException
            {
                final boolean oldType = PwmSettingSyntax.STRING_ARRAY.toString().equals(
                        settingElement.getAttributeValue( "syntax" ) );
                final List valueElements = settingElement.getChildren( "value" );
                final List<ActionConfiguration> values = new ArrayList<>();
                for ( final Object loopValue : valueElements )
                {
                    final Element loopValueElement = ( Element ) loopValue;
                    final String value = loopValueElement.getText();
                    if ( value != null && value.length() > 0 )
                    {
                        if ( oldType )
                        {
                            if ( loopValueElement.getAttribute( "locale" ) == null )
                            {
                                values.add( ActionConfiguration.parseOldConfigString( value ) );
                            }
                        }
                        else
                        {
                            final ActionConfiguration parsedAc = JsonUtil.deserialize( value, ActionConfiguration.class );
                            parsedAc.setPassword( decryptPwValue( parsedAc.getPassword(), pwmSecurityKey ) );
                            values.add( parsedAc );
                        }
                    }
                }
                return new ActionValue( values );
            }
        };
    }

    public List<Element> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<Element> returnList = new ArrayList<>();
        for ( final ActionConfiguration value : values )
        {
            final Element valueElement = new Element( valueElementName );
            final ActionConfiguration clonedValue = JsonUtil.cloneUsingJson( value, ActionConfiguration.class );
            try
            {
                clonedValue.setPassword( encryptPwValue( clonedValue.getPassword(), pwmSecurityKey ) );
            }
            catch ( PwmOperationalException e )
            {
                LOGGER.warn( "error decoding stored pw value: " + e.getMessage() );
            }

            valueElement.addContent( JsonUtil.serialize( clonedValue ) );
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
            final ActionConfiguration clone = JsonUtil.cloneUsingJson( actionConfiguration, ActionConfiguration.class );
            if ( !StringUtil.isEmpty( clone.getPassword() ) )
            {
                clone.setPassword( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT );
            }
            output.add( clone );
        }
        return output;
    }

    public String toDebugString( final Locale locale )
    {
        final StringBuilder sb = new StringBuilder();
        int counter = 0;
        for ( final ActionConfiguration actionConfiguration : values )
        {
            sb.append( "Action" );
            if ( values.size() > 1 )
            {
                sb.append( counter );
            }
            sb.append( "-" );
            sb.append( actionConfiguration.getType() == null ? ActionConfiguration.Type.ldap.toString() : actionConfiguration.getType().toString() );
            sb.append( ": [" );
            switch ( actionConfiguration.getType() )
            {
                case webservice:
                {
                    sb.append( "WebService: " );
                    sb.append( "method=" ).append( actionConfiguration.getMethod() );
                    sb.append( " url=" ).append( actionConfiguration.getUrl() );
                    sb.append( " headers=" ).append( JsonUtil.serializeMap( actionConfiguration.getHeaders() ) );
                    sb.append( " username=" ).append( actionConfiguration.getUsername() );
                    sb.append( " password=" ).append(
                            StringUtil.isEmpty( actionConfiguration.getPassword() )
                                    ? ""
                                    : PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT
                    );
                    sb.append( " body=" ).append( actionConfiguration.getBody() );
                }
                break;

                case ldap:
                {
                    sb.append( "LDAP: " );
                    sb.append( "method=" ).append( actionConfiguration.getLdapMethod() );
                    sb.append( " attribute=" ).append( actionConfiguration.getAttributeName() );
                    sb.append( " value=" ).append( actionConfiguration.getAttributeValue() );
                }
                break;

                default:
                    JavaHelper.unhandledSwitchStatement( actionConfiguration.getType() );
            }
            sb.append( "]" );
            counter++;
            if ( counter != values.size() )
            {
                sb.append( "\n" );
            }
        }
        return sb.toString();
    }


    public List<Map<String, Object>> toInfoMap( )
    {
        final String originalJson = JsonUtil.serializeCollection( values );
        final List<Map<String, Object>> tempObj = JsonUtil.deserialize( originalJson, new TypeToken<List<Map<String, Object>>>()
        {
        } );
        for ( final Map<String, Object> mapObj : tempObj )
        {
            final ActionConfiguration actionConfiguration = forName( ( String ) mapObj.get( "name" ) );
            if ( actionConfiguration != null && actionConfiguration.getCertificates() != null )
            {
                final List<Map<String, String>> certificateInfos = new ArrayList<>();
                for ( final X509Certificate certificate : actionConfiguration.getCertificates() )
                {
                    certificateInfos.add( X509Utils.makeDebugInfoMap( certificate, X509Utils.DebugInfoFlag.IncludeCertificateDetail ) );
                }
                mapObj.put( "certificateInfos", certificateInfos );
            }
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


}
