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

package password.pwm.config.stored;

import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.RecoveryMinLifetimeOption;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.value.OptionListValue;
import password.pwm.config.value.StoredValueEncoder;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmExceptionLoggingConsumer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class ConfigurationCleaner
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationCleaner.class );


    private static final List<PwmExceptionLoggingConsumer<XmlDocument>> XML_PRE_PROCESSORS = Collections.unmodifiableList( Arrays.asList(
            new MigratePreValueXmlElements(),
            new MigrateOldPropertyFormat(),
            new AppPropertyOverrideMigration(),
            new ProfileNonProfiledSettings(),
            new MigrateDeprecatedProperties(),
            new UpdatePropertiesWithoutType()
    ) );

    private static final List<PwmExceptionLoggingConsumer<StoredConfigurationModifier>> STORED_CONFIG_POST_PROCESSORS = Collections.unmodifiableList( Arrays.asList(
            new UpdateDeprecatedAdComplexitySettings(),
            new UpdateDeprecatedMinPwdLifetimeSetting(),
            new UpdateDeprecatedPublicHealthSetting()
    ) );


    static void preProcessXml(
            final XmlDocument document
    )
    {
        XML_PRE_PROCESSORS.forEach( ( c ) -> PwmExceptionLoggingConsumer.wrapConsumer( c ).accept( document ) );
    }


    static void postProcessStoredConfig(
            final StoredConfigurationModifier storedConfiguration
    )
    {
        STORED_CONFIG_POST_PROCESSORS.forEach( aClass -> PwmExceptionLoggingConsumer.wrapConsumer( aClass ).accept( storedConfiguration ) );
    }

    private static class MigratePreValueXmlElements implements PwmExceptionLoggingConsumer<XmlDocument>
    {
        @Override
        public void accept( final XmlDocument xmlDocument )
        {
            if ( readDocVersion( xmlDocument ) >= 4 )
            {
                return;
            }

            final List<XmlElement> settingElements = xmlDocument.evaluateXpathToElements( "//"
                    + StoredConfigXmlConstants.XML_ELEMENT_SETTING );
            for ( final XmlElement settingElement : settingElements )
            {
                final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                final Optional<XmlElement> defaultElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_DEFAULT );
                if ( valueElement.isPresent() && defaultElement.isPresent() )
                {
                    final String textValue = settingElement.getTextTrim();
                    if ( !StringUtil.isEmpty( textValue ) )
                    {
                        final XmlElement newValueElement = XmlFactory.getFactory().newElement( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                        newValueElement.addText( textValue );
                        settingElement.addContent( newValueElement );
                        final String key = settingElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY );
                        LOGGER.info( () -> "migrating pre-xml 'value' tag format to use value element for key: " + key );
                    }
                }
            }
        }
    }

    private static class MigrateOldPropertyFormat implements PwmExceptionLoggingConsumer<XmlDocument>
    {
        @Override
        public void accept( final XmlDocument xmlDocument )
        {
            // read correct (new) //properties[@type="config"]
            final String configPropertiesXpath = "//" + StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                    + "[@" + StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + "=\"" + StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG + "\"]";
            final Optional<XmlElement> configPropertiesElement = xmlDocument.evaluateXpathToElement( configPropertiesXpath );

            // read list of old //properties[not (@type)]/property
            final String nonAttributedPropertyXpath = "//" + StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                    + "[not (@" + StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + ")]/" + StoredConfigXmlConstants.XML_ELEMENT_PROPERTY;
            final List<XmlElement> nonAttributedProperties = xmlDocument.evaluateXpathToElements( nonAttributedPropertyXpath );

            if ( configPropertiesElement.isPresent() && nonAttributedProperties != null )
            {
                for ( final XmlElement element : nonAttributedProperties )
                {
                    element.detach();
                    configPropertiesElement.get().addContent( element );
                }
            }

            // remove old //properties[not (@type] element
            final String oldPropertiesXpath = "//" + StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                    + "[not (@" + StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + ")]";
            final List<XmlElement> oldPropertiesElements = xmlDocument.evaluateXpathToElements( oldPropertiesXpath );
            if ( oldPropertiesElements != null )
            {
                for ( final XmlElement element : oldPropertiesElements )
                {
                    element.detach();
                }
            }
        }
    }

    static class ProfileNonProfiledSettings implements PwmExceptionLoggingConsumer<XmlDocument>
    {
        @Override
        public void accept( final XmlDocument xmlDocument )
        {
            final StoredConfigurationFactory.XmlInputDocumentReader reader = new StoredConfigurationFactory.XmlInputDocumentReader( xmlDocument );
            for ( final PwmSetting setting : PwmSetting.values() )
            {
                if ( setting.getCategory().hasProfiles() )
                {
                    reader.xpathForSetting( setting, null ).ifPresent( existingSettingElement ->
                    {
                        final List<String> profileStringDefinitions = new ArrayList<>();
                        {
                            final List<String> configuredProfiles = reader.profilesForSetting( setting );
                            if ( !JavaHelper.isEmpty( configuredProfiles ) )
                            {
                                profileStringDefinitions.addAll( configuredProfiles );
                            }
                        }

                        if ( profileStringDefinitions.isEmpty() )
                        {
                            profileStringDefinitions.add( PwmConstants.PROFILE_ID_DEFAULT );
                        }

                        for ( final String destProfile : profileStringDefinitions )
                        {
                            LOGGER.info( () -> "moving setting " + setting.getKey() + " without profile attribute to profile \"" + destProfile + "\"." );
                            {
                                //existingSettingElement.detach();
                                final XmlElement newSettingElement = existingSettingElement.copy();
                                newSettingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE, destProfile );

                                final XmlElement settingsElement = reader.xpathForSettings();
                                settingsElement.addContent( newSettingElement );
                            }
                        }
                    } );
                }
            }
        }
    }

    private static class MigrateDeprecatedProperties implements PwmExceptionLoggingConsumer<XmlDocument>
    {
        @Override
        public void accept( final XmlDocument xmlDocument ) throws PwmUnrecoverableException
        {
            {
                final String xpathString = "//property[@key=\"" + ConfigurationProperty.LDAP_TEMPLATE.getKey() + "\"]";
                final List<XmlElement> propertyElement = xmlDocument.evaluateXpathToElements( xpathString );
                if ( propertyElement != null && !propertyElement.isEmpty() )
                {
                    final String value = propertyElement.get( 0 ).getText();
                    propertyElement.get( 0 ).detach();
                    attachStringSettingElement( xmlDocument, PwmSetting.TEMPLATE_LDAP, value );

                }
            }
            {
                final String xpathString = "//property[@key=\"" + ConfigurationProperty.NOTES.getKey() + "\"]";
                final List<XmlElement> propertyElement = xmlDocument.evaluateXpathToElements( xpathString );
                if ( propertyElement != null && !propertyElement.isEmpty() )
                {
                    final String value = propertyElement.get( 0 ).getText();
                    propertyElement.get( 0 ).detach();
                    attachStringSettingElement( xmlDocument, PwmSetting.NOTES, value );
                }
            }
        }

        private static void attachStringSettingElement(
                final XmlDocument xmlDocument,
                final PwmSetting pwmSetting,
                final String stringValue
        )
                throws PwmUnrecoverableException
        {
            final StoredConfigurationFactory.XmlInputDocumentReader inputDocumentReader = new StoredConfigurationFactory.XmlInputDocumentReader( xmlDocument );

            final PwmSecurityKey pwmSecurityKey = inputDocumentReader.getKey();

            final XmlElement settingElement = StoredConfigurationFactory.XmlOutputHandler.makeSettingXmlElement(
                    null,
                    pwmSetting,
                    null,
                    new StringValue( stringValue ),
                    XmlOutputProcessData.builder().storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN ).pwmSecurityKey( pwmSecurityKey ).build() );
            final Optional<XmlElement> settingsElement = xmlDocument.getRootElement().getChild( StoredConfigXmlConstants.XML_ELEMENT_SETTING );
            settingsElement.ifPresent( xmlElement -> xmlElement.addContent( settingElement ) );
        }
    }

    private static class UpdatePropertiesWithoutType implements PwmExceptionLoggingConsumer<XmlDocument>
    {
        @Override
        public void accept( final XmlDocument xmlDocument )
        {
            final String xpathString = "//properties[not(@type)]";
            final List<XmlElement> propertiesElements = xmlDocument.evaluateXpathToElements( xpathString );
            for ( final XmlElement propertiesElement : propertiesElements )
            {
                propertiesElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE, StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG );
            }
        }
    }

    private static class AppPropertyOverrideMigration implements PwmExceptionLoggingConsumer<XmlDocument>
    {
        @Override
        public void accept( final XmlDocument xmlDocument ) throws PwmUnrecoverableException
        {
            final StoredConfigurationFactory.XmlInputDocumentReader documentReader = new StoredConfigurationFactory.XmlInputDocumentReader( xmlDocument );
            final List<XmlElement> appPropertiesElements = documentReader.xpathForAppProperties();
            for ( final XmlElement element : appPropertiesElements )
            {
                final List<XmlElement> properties = element.getChildren();
                for ( final XmlElement property : properties )
                {
                    final String key = property.getAttributeValue( "key" );
                    final String value = property.getText();
                    if ( key != null && !key.isEmpty() && value != null && !value.isEmpty() )
                    {
                        LOGGER.info( () -> "migrating app-property config element '" + key + "' to setting " + PwmSetting.APP_PROPERTY_OVERRIDES.getKey() );
                        final String newValue = key + "=" + value;

                        final List<String> existingValues = new ArrayList<>();
                        {
                            final Optional<StoredConfigData.ValueAndMetaCarrier> valueAndMetaTuple =  documentReader.readSetting( PwmSetting.APP_PROPERTY_OVERRIDES, null );
                            valueAndMetaTuple.ifPresent( ( t ) -> existingValues.addAll( ( List<String> ) t.getValue().toNativeObject() ) );
                        }
                        existingValues.add( newValue );
                        rewriteAppPropertySettingElement( xmlDocument, existingValues );
                    }
                }
                element.detach();
            }
        }

        private static void rewriteAppPropertySettingElement( final XmlDocument xmlDocument, final List<String> newValues )
                throws PwmUnrecoverableException
        {
            final StoredConfigurationFactory.XmlInputDocumentReader inputDocumentReader = new StoredConfigurationFactory.XmlInputDocumentReader( xmlDocument );

            {
                final Optional<XmlElement> existingAppPropertySetting = inputDocumentReader.xpathForSetting( PwmSetting.APP_PROPERTY_OVERRIDES, null );
                existingAppPropertySetting.ifPresent( XmlElement::detach );
            }

            final PwmSecurityKey pwmSecurityKey = inputDocumentReader.getKey();

            final XmlElement settingElement = StoredConfigurationFactory.XmlOutputHandler.makeSettingXmlElement(
                    null,
                    PwmSetting.APP_PROPERTY_OVERRIDES,
                    null,
                    new StringArrayValue( newValues ),
                    XmlOutputProcessData.builder().storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN ).pwmSecurityKey( pwmSecurityKey ).build() );
            final Optional<XmlElement> settingsElement = xmlDocument.getRootElement().getChild( StoredConfigXmlConstants.XML_ELEMENT_SETTING );
            settingsElement.ifPresent( ( s ) -> s.addContent( settingElement ) );
        }
    }

    private static class UpdateDeprecatedAdComplexitySettings implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration oldConfig = modifier.newStoredConfiguration();
            final Configuration configuration = new Configuration( oldConfig );
            for ( final String profileID : configuration.getPasswordProfileIDs() )
            {
                if ( !oldConfig.isDefaultValue( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ) )
                {
                    final boolean ad2003Enabled = ( boolean ) oldConfig.readSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ).toNativeObject();
                    final StoredValue value;
                    if ( ad2003Enabled )
                    {
                        value = new StringValue( ADPolicyComplexity.AD2003.toString() );
                    }
                    else
                    {
                        value = new StringValue( ADPolicyComplexity.NONE.toString() );
                    }
                    LOGGER.info( () -> "converting deprecated non-default setting " + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY.getKey() + "/" + profileID
                            + " to replacement setting " + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL + ", value=" + value.toNativeObject().toString() );
                    final Optional<ValueMetaData> valueMetaData = oldConfig.readMetaData(
                            StoredConfigItemKey.fromSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ) );
                    final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );
                    modifier.writeSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL, profileID, value, userIdentity );
                }
            }
        }
    }

    private static class UpdateDeprecatedMinPwdLifetimeSetting implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration oldConfig = modifier.newStoredConfiguration();
            for ( final String profileID : oldConfig.profilesForSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME ) )
            {
                if ( !oldConfig.isDefaultValue( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID ) )
                {
                    final boolean enforceEnabled = ( boolean ) oldConfig.readSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID ).toNativeObject();
                    final StoredValue value = enforceEnabled
                            ? new StringValue( RecoveryMinLifetimeOption.NONE.name() )
                            : new StringValue( RecoveryMinLifetimeOption.ALLOW.name() );
                    final ValueMetaData existingData = oldConfig.readSettingMetadata( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID );
                    final UserIdentity newActor = existingData != null && existingData.getUserIdentity() != null
                            ? existingData.getUserIdentity()
                            : null;
                    LOGGER.info( () -> "converting deprecated non-default setting "
                            + PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) + "/" + profileID
                            + " to replacement setting " + PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE )
                            + ", value=" + value.toNativeObject().toString() );
                    modifier.writeSetting( PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS, profileID, value, newActor );
                }
            }
        }
    }

    private static class UpdateDeprecatedPublicHealthSetting implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration oldConfig = modifier.newStoredConfiguration();
            if ( !oldConfig.isDefaultValue( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES, null ) )
            {
                LOGGER.info( () -> "converting deprecated non-default setting "
                        + PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE )
                        + " to replacement setting " + PwmSetting.WEBSERVICES_PUBLIC_ENABLE.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) );
                final Set<String> existingValues = ( Set<String> ) oldConfig.readSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, null ).toNativeObject();
                final Set<String> newValues = new LinkedHashSet<>( existingValues );
                newValues.add( WebServiceUsage.Health.name() );
                newValues.add( WebServiceUsage.Statistics.name() );

                final Optional<ValueMetaData> valueMetaData = oldConfig.readMetaData(
                        StoredConfigItemKey.fromSetting( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES, null ) );
                final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );

                modifier.writeSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, null, new OptionListValue( newValues ), userIdentity );
            }
        }
    }

    private static int readDocVersion( final XmlDocument xmlDocument )
    {
        final String xmlVersionStr = xmlDocument.getRootElement().getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_XML_VERSION );
        return JavaHelper.silentParseInt( xmlVersionStr, 0 );
    }
}
