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
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.error.PwmOperationalException;
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
import java.util.Objects;
import java.util.Set;

public class RemoteWebServiceValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RemoteWebServiceValue.class );

    final List<RemoteWebServiceConfiguration> values;

    public RemoteWebServiceValue( final List<RemoteWebServiceConfiguration> values )
    {
        this.values = Collections.unmodifiableList( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public RemoteWebServiceValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new RemoteWebServiceValue( Collections.emptyList() );
                }
                else
                {
                    List<RemoteWebServiceConfiguration> srcList = JsonUtil.deserialize( input,
                            new TypeToken<List<RemoteWebServiceConfiguration>>()
                            {
                            }
                    );

                    srcList = srcList == null ? Collections.emptyList() : srcList;
                    srcList.removeIf( Objects::isNull );
                    return new RemoteWebServiceValue( Collections.unmodifiableList( srcList ) );
                }
            }

            public RemoteWebServiceValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey pwmSecurityKey
            )
                    throws PwmOperationalException
            {
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final List<RemoteWebServiceConfiguration> values = new ArrayList<>();
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final String value = loopValueElement.getText();
                    if ( value != null && value.length() > 0 )
                    {
                        final RemoteWebServiceConfiguration parsedValue = JsonUtil.deserialize( value, RemoteWebServiceConfiguration.class );
                        parsedValue.setPassword( decryptPwValue( parsedValue.getPassword(), pwmSecurityKey ) );
                        values.add( parsedValue );
                    }
                }
                return new RemoteWebServiceValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final RemoteWebServiceConfiguration value : values )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            final RemoteWebServiceConfiguration clonedValue = JsonUtil.cloneUsingJson( value, RemoteWebServiceConfiguration.class );
            try
            {
                clonedValue.setPassword( encryptPwValue( clonedValue.getPassword(), pwmSecurityKey ) );
            }
            catch ( PwmOperationalException e )
            {
                LOGGER.warn( "error decoding stored pw value: " + e.getMessage() );
            }

            valueElement.addText( JsonUtil.serialize( clonedValue ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    public List<RemoteWebServiceConfiguration> toNativeObject( )
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
        for ( final RemoteWebServiceConfiguration item : values )
        {
            if ( seenNames.contains( item.getName().toLowerCase() ) )
            {
                return Collections.singletonList( "each action name must be unique: " + item.getName().toLowerCase() );
            }
            seenNames.add( item.getName().toLowerCase() );
        }


        return Collections.emptyList();
    }

    public List<Map<String, Object>> toInfoMap( )
    {
        final String originalJson = JsonUtil.serializeCollection( values );
        final List<Map<String, Object>> tempObj = JsonUtil.deserialize( originalJson, new TypeToken<List<Map<String, Object>>>()
        {
        } );
        for ( final Map<String, Object> mapObj : tempObj )
        {
            final RemoteWebServiceConfiguration serviceConfig = forName( ( String ) mapObj.get( "name" ) );
            if ( serviceConfig != null && serviceConfig.getCertificates() != null )
            {
                final List<Map<String, String>> certificateInfos = new ArrayList<>();
                for ( final X509Certificate certificate : serviceConfig.getCertificates() )
                {
                    certificateInfos.add( X509Utils.makeDebugInfoMap( certificate, X509Utils.DebugInfoFlag.IncludeCertificateDetail ) );
                }
                mapObj.put( "certificateInfos", certificateInfos );
            }
        }
        return tempObj;
    }


    public RemoteWebServiceConfiguration forName( final String name )
    {
        if ( name == null )
        {
            return null;
        }
        for ( final RemoteWebServiceConfiguration config : values )
        {
            if ( name.equals( config.getName() ) )
            {
                return config;
            }
        }
        return null;
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        final ArrayList<RemoteWebServiceConfiguration> output = new ArrayList<>();
        for ( final RemoteWebServiceConfiguration remoteWebServiceConfiguration : values )
        {
            final RemoteWebServiceConfiguration clone = JsonUtil.cloneUsingJson( remoteWebServiceConfiguration, RemoteWebServiceConfiguration.class );
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
        return JsonUtil.serialize( this.toDebugJsonObject( locale ) );
    }

}
