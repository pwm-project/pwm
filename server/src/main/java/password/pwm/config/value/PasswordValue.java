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


import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PasswordValue implements StoredValue
{
    private PasswordData value;

    PasswordValue( )
    {
    }

    boolean requiresStoredUpdate;

    public PasswordValue( final PasswordData passwordData )
    {
        value = passwordData;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public PasswordValue fromJson( final String value )
            {
                final String strValue = JsonUtil.deserialize( value, String.class );
                if ( strValue != null && !strValue.isEmpty() )
                {
                    try
                    {
                        return new PasswordValue( new PasswordData( strValue ) );
                    }
                    catch ( PwmUnrecoverableException e )
                    {
                        throw new IllegalStateException(
                                "PasswordValue can not be json de-serialized: " + e.getMessage() );
                    }
                }
                return new PasswordValue();
            }

            public PasswordValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey key
            )
                    throws PwmOperationalException, PwmUnrecoverableException
            {
                final XmlElement valueElement = settingElement.getChild( "value" );
                final String rawValue = valueElement.getText();

                final PasswordValue newPasswordValue = new PasswordValue();
                if ( rawValue == null || rawValue.isEmpty() )
                {
                    return newPasswordValue;
                }

                final boolean plainTextSetting;
                {
                    final String plainTextAttributeStr = valueElement.getAttributeValue( "plaintext" );
                    plainTextSetting = plainTextAttributeStr != null && Boolean.parseBoolean( plainTextAttributeStr );
                }

                if ( plainTextSetting )
                {
                    newPasswordValue.value = new PasswordData( rawValue );
                    newPasswordValue.requiresStoredUpdate = true;
                }
                else
                {
                    try
                    {
                        newPasswordValue.value = new PasswordData( SecureEngine.decryptStringValue( rawValue, key, PwmBlockAlgorithm.CONFIG ) );
                        return newPasswordValue;
                    }
                    catch ( Exception e )
                    {
                        final String errorMsg = "unable to decode encrypted password value for setting: " + e.getMessage();
                        final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                        throw new PwmOperationalException( errorInfo );
                    }
                }
                return newPasswordValue;
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName )
    {
        throw new IllegalStateException( "password xml output requires hash key" );
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
    public int currentSyntaxVersion( )
    {
        return 0;
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final PwmSecurityKey key )
    {
        if ( value == null )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            return Collections.singletonList( valueElement );
        }
        final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
        try
        {
            final String encodedValue = SecureEngine.encryptToString( value.getStringValue(), key, PwmBlockAlgorithm.CONFIG );
            valueElement.addText( encodedValue );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage() );
        }
        return Collections.singletonList( valueElement );
    }

    public String toString( )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    public boolean requiresStoredUpdate( )
    {
        return requiresStoredUpdate;
    }

    @Override
    public String valueHash( ) throws PwmUnrecoverableException
    {
        return value == null ? "" : SecureEngine.hash( JsonUtil.serialize( value.getStringValue() ), PwmConstants.SETTING_CHECKSUM_HASH_METHOD );
    }
}
