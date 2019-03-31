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

package password.pwm.config.stored;

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
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
import java.util.LinkedHashMap;
import java.util.Map;


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
            final Map<StoredConfigReference, StoredValue> values = new LinkedHashMap<>();
            final Map<StoredConfigReference, ValueMetaData> metaData = new LinkedHashMap<>();

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
                        readInterestingElement( propertyElement, pwmSecurityKey, values, metaData );
                    }
                }
                else if ( StoredConfiguration.XML_ELEMENT_SETTING.equals( loopElement.getName() ) )
                {
                    readInterestingElement( loopElement, pwmSecurityKey, values, metaData );
                }
            }
            return new NGStoredConfiguration( values, metaData, readSecurityKey( rootElement ) );
        }

        static void readInterestingElement(
                final XmlElement loopElement,
                final PwmSecurityKey pwmSecurityKey,
                final Map<StoredConfigReference, StoredValue> values,
                final Map<StoredConfigReference, ValueMetaData> metaData
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
                            values.put( reference, storedValue );
                        }
                    }
                    break;

                    case PROPERTY:
                    {
                        final StoredValue storedValue = readPropertyValue( reference, loopElement );
                    }
                    break;

                    default:
                        throw new IllegalArgumentException( "unimplemented setting recordtype in reader" );
                }
                final ValueMetaData valueMetaData = readValueMetaData( loopElement );
                if ( valueMetaData != null )
                {
                    metaData.put( reference, valueMetaData );
                }
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

            return new ValueMetaData( modifyDate, userIdentity );
        }
    }

}
