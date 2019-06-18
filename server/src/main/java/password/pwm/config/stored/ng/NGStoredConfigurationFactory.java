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

package password.pwm.config.stored.ng;

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigReference;
import password.pwm.config.stored.StoredConfigReferenceBean;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.ValueMetaData;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;


public class NGStoredConfigurationFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NGStoredConfigurationFactory.class );

    //@Override
    public NGStoredConfiguration fromXml( final InputStream inputStream ) throws PwmUnrecoverableException
    {
        return XmlEngine.fromXmlImpl( inputStream );
    }

    //@Override
    public void toXml( final OutputStream outputStream )
    {
    }

    private static class XmlEngine
    {
        static NGStoredConfiguration fromXmlImpl( final InputStream inputStream )
                throws PwmUnrecoverableException
        {
            final NGStorageEngineImpl storageEngine = new NGStorageEngineImpl();

            final XmlDocument inputDocument = XmlFactory.getFactory().parseXml( inputStream );
            final XmlElement rootElement = inputDocument.getRootElement();

            final PwmSecurityKey pwmSecurityKey = readSecurityKey( rootElement );

            final XmlElement settingsElement = rootElement.getChild( StoredConfiguration.XML_ELEMENT_SETTINGS );

            for ( final XmlElement loopElement : settingsElement.getChildren() )
            {
                if ( StoredConfiguration.XML_ELEMENT_PROPERTIES.equals( loopElement.getName() ) )
                {
                    for ( final XmlElement propertyElement : loopElement.getChildren( StoredConfiguration.XML_ELEMENT_PROPERTY ) )
                    {
                        readInterestingElement( propertyElement, pwmSecurityKey, storageEngine );
                    }
                }
                else if ( StoredConfiguration.XML_ELEMENT_SETTING.equals( loopElement.getName() ) )
                {
                    readInterestingElement( loopElement, pwmSecurityKey, storageEngine );
                }
            }
            return new NGStoredConfiguration( storageEngine, pwmSecurityKey );
        }

        static void readInterestingElement(
                final XmlElement loopElement,
                final PwmSecurityKey pwmSecurityKey,
                final NGStorageEngineImpl engine
        )
        {
            final StoredConfigReference reference = referenceForElement( loopElement );
            if ( reference != null )
            {
                switch ( reference.getRecordType() )
                {
                    case SETTING:
                    {
                        final StoredValue storedValue = readSettingValue( reference, loopElement, pwmSecurityKey );
                        if ( storedValue != null )
                        {
                            engine.write( reference, storedValue, null );
                        }
                    }
                    break;

                    case PROPERTY:
                    {
                        final StoredValue storedValue = readPropertyValue( reference, loopElement );
                        if ( storedValue != null )
                        {
                            engine.write( reference, storedValue, null );
                        }
                    }
                    break;

                    default:
                        throw new IllegalArgumentException( "unimplemented setting recordtype in reader" );
                }
                engine.writeMetaData( reference, readValueMetaData( loopElement ) );
            }
        }

        static PwmSecurityKey readSecurityKey( final XmlElement rootElement )
                throws PwmUnrecoverableException
        {
            final String createTime = rootElement.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_CREATE_TIME );
            return new PwmSecurityKey( createTime + "StoredConfiguration" );
        }

        static StoredValue readSettingValue(
                final StoredConfigReference storedConfigReference,
                final XmlElement settingElement,
                final PwmSecurityKey pwmSecurityKey
        )
        {
            final String key = storedConfigReference.getRecordID();
            final PwmSetting pwmSetting = PwmSetting.forKey( key );

            if ( pwmSetting == null )
            {
                LOGGER.debug( () -> "ignoring setting for unknown key: " + key );
            }
            else
            {
                LOGGER.trace( () -> "parsing setting key=" + key + ", profile=" + storedConfigReference.getProfileID() );
                final XmlElement defaultElement = settingElement.getChild( StoredConfiguration.XML_ELEMENT_DEFAULT );
                if ( defaultElement != null )
                {
                    return null;
                }

                {
                    try
                    {
                        return ValueFactory.fromXmlValues( pwmSetting, settingElement, pwmSecurityKey );
                    }
                    catch ( IllegalStateException e )
                    {
                        LOGGER.error( "error parsing configuration setting " + storedConfigReference + ", error: " + e.getMessage() );
                    }
                }
            }
            return null;
        }

        static StoredValue readPropertyValue(
                final StoredConfigReference storedConfigReference,
                final XmlElement settingElement
        )
        {
            final String key = storedConfigReference.getRecordID();

            LOGGER.trace( () -> "parsing property key=" + key + ", profile=" + storedConfigReference.getProfileID() );
            if ( settingElement.getChild( StoredConfiguration.XML_ELEMENT_DEFAULT ) != null )
            {
                return new StringValue( settingElement.getText() );
            }
            return null;
        }

        static StoredConfigReference referenceForElement( final XmlElement settingElement )
        {
            final String key = settingElement.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_KEY );
            final String profileID = readProfileID( settingElement );
            final StoredConfigReference.RecordType recordType;
            switch ( settingElement.getName() )
            {
                case StoredConfiguration.XML_ELEMENT_SETTING:
                    recordType = StoredConfigReference.RecordType.SETTING;
                    break;

                case StoredConfiguration.XML_ELEMENT_PROPERTY:
                    recordType = StoredConfigReference.RecordType.PROPERTY;
                    break;

                case StoredConfiguration.XML_ELEMENT_LOCALEBUNDLE:
                    recordType = StoredConfigReference.RecordType.LOCALE_BUNDLE;
                    break;

                default:
                    LOGGER.warn( "unrecognized xml element " + settingElement.getName() + " in configuration" );
                    return null;
            }


            return new StoredConfigReferenceBean(
                    recordType,
                    key,
                    profileID
            );
        }

        static String readProfileID( final XmlElement settingElement )
        {
            final String profileIDStr = settingElement.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_PROFILE );
            return profileIDStr != null && !profileIDStr.isEmpty() ? profileIDStr : null;
        }

        static ValueMetaData readValueMetaData( final XmlElement element )
        {
            final String modifyDateStr = element.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_MODIFY_TIME );
            Instant modifyDate = null;
            try
            {
                modifyDate = modifyDateStr == null || modifyDateStr.isEmpty()
                        ? null
                        : JavaHelper.parseIsoToInstant( modifyDateStr );
            }
            catch ( Exception e )
            {
                LOGGER.warn( "error parsing stored date: " + e.getMessage() );
            }
            final String modifyUser = element.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_MODIFY_USER );
            final String modifyUserProfile = element.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_MODIFY_USER_PROFILE );
            final UserIdentity userIdentity;
            userIdentity = modifyUser != null
                    ? new UserIdentity( modifyUser, modifyUserProfile )
                    : null;

            return ValueMetaData.builder()
                    .modifyDate( modifyDate )
                    .userIdentity( userIdentity )
                    .build();
        }
    }

}
