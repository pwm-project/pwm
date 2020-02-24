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

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.StoredValue;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StoredValueEncoder;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

public class StoredConfigurationFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationFactory.class );

    private static final String XML_FORMAT_VERSION = "5";


    public static StoredConfiguration newConfig() throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = new StoredConfigurationImpl(  );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        StoredConfigurationUtil.initNewRandomSecurityKey( modifier );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( true ) );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_EPOCH, String.valueOf( 0 ) );


        return modifier.newStoredConfiguration();
    }

    public static StoredConfigurationModifier newModifiableConfig() throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = new StoredConfigurationImpl(  );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        StoredConfigurationUtil.initNewRandomSecurityKey( modifier );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( true ) );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_EPOCH, String.valueOf( 0 ) );


        return modifier;
    }

    public static StoredConfiguration fromXml( final InputStream inputStream )
            throws PwmUnrecoverableException
    {
        final XmlFactory xmlFactory = XmlFactory.getFactory();
        final XmlDocument xmlDocument = xmlFactory.parseXml( inputStream );
        ConfigurationCleaner.preProcessXml( xmlDocument );

        final XmlInputDocumentReader xmlInputDocumentReader = new XmlInputDocumentReader( xmlDocument );
        final StoredConfigData storedConfigData = xmlInputDocumentReader.getStoredConfigData();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier(  new StoredConfigurationImpl( storedConfigData ) );
        ConfigurationCleaner.postProcessStoredConfig( modifier );

        return modifier.newStoredConfiguration();
    }

    public static void toXml(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream
    )
            throws PwmUnrecoverableException, IOException
    {
        toXml( storedConfiguration, outputStream, OutputSettings.builder().build() );
    }

    public static void toXml(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream,
            final OutputSettings outputSettings
    )
            throws PwmUnrecoverableException, IOException
    {
        final XmlFactory xmlFactory = XmlFactory.getFactory();
        final XmlDocument xmlDocument = xmlFactory.newDocument( StoredConfigXmlConstants.XML_ELEMENT_ROOT );

        XmlOutputHandler.makeXmlOutput( storedConfiguration, xmlDocument.getRootElement(), outputSettings );

        xmlFactory.outputDocument( xmlDocument, outputStream );
    }

    static class XmlInputDocumentReader
    {
        private final XmlDocument document;

        XmlInputDocumentReader( final XmlDocument document )
        {
            this.document = document;
        }

        StoredConfigData getStoredConfigData()
        {
            final String createTime = readCreateTime();
            final Instant modifyTime = readModifyTime();

            final List<StoredConfigData.ValueAndMetaCarrier> values = new ArrayList<>();
            values.addAll( readProperties() );
            values.addAll( readSettings() );
            values.addAll( readLocaleBundles() );
            return StoredConfigData.builder()
                    .createTime( createTime )
                    .modifyTime( modifyTime )
                    .metaDatas( StoredConfigData.carrierAsMetaDataMap( values ) )
                    .storedValues( StoredConfigData.carrierAsStoredValueMap( values ) )
                    .build();
        }

        private Collection<StoredConfigData.ValueAndMetaCarrier> readProperties()
        {
            final List<StoredConfigData.ValueAndMetaCarrier> valueAndMetaWrapper = new ArrayList<>();
            for ( final ConfigurationProperty configurationProperty : ConfigurationProperty.values() )
            {
                final Optional<XmlElement> propertyElement = xpathForConfigProperty( configurationProperty );
                if ( propertyElement.isPresent() && !StringUtil.isEmpty( propertyElement.get().getText() ) )
                {
                    final StoredConfigItemKey key = StoredConfigItemKey.fromConfigurationProperty( configurationProperty );
                    final StoredValue storedValue = new StringValue( propertyElement.get().getText() );
                    final ValueMetaData metaData = readMetaDataFromXmlElement( key, propertyElement.get() ).orElse( null );
                    valueAndMetaWrapper.add( new StoredConfigData.ValueAndMetaCarrier( key, storedValue, metaData ) );
                }
            }
            return valueAndMetaWrapper;
        }

        private Collection<StoredConfigData.ValueAndMetaCarrier> readSettings()
        {
            final List<StoredConfigData.ValueAndMetaCarrier> returnList = new ArrayList<>();
            for ( final PwmSetting pwmSetting : PwmSetting.values() )
            {
                if ( !pwmSetting.getCategory().hasProfiles() )
                {
                    readSetting( pwmSetting, null ).ifPresent( returnList::add );
                }
            }

            for ( final PwmSetting pwmSetting : PwmSetting.values() )
            {
                if ( pwmSetting.getCategory().hasProfiles() )
                {
                    final List<String> profileIDs = profilesForSetting( pwmSetting );
                    for ( final String profileID : profileIDs )
                    {
                        readSetting( pwmSetting, profileID ).ifPresent( returnList::add );
                    }
                }
            }
            return returnList;
        }

        Optional<StoredConfigData.ValueAndMetaCarrier> readSetting( final PwmSetting setting, final String profileID )
        {
            final Optional<XmlElement> settingElement = xpathForSetting( setting, profileID );

            if ( !settingElement.isPresent() )
            {
                return Optional.empty();
            }

            if ( settingElement.get().getChild( StoredConfigXmlConstants.XML_ELEMENT_DEFAULT ).isPresent() )
            {
                return Optional.empty();
            }

            try
            {
                final StoredValue storedValue = ValueFactory.fromXmlValues( setting, settingElement.get(), getKey() );
                final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
                final ValueMetaData metaData = readMetaDataFromXmlElement( key, settingElement.get() ).orElse( null );
                return Optional.of( new StoredConfigData.ValueAndMetaCarrier( key, storedValue, metaData ) );
            }
            catch ( final PwmException e )
            {
                final String errorMsg = "unexpected error reading setting '" + setting.getKey() + "' profile '" + profileID + "', error: " + e.getMessage();
                throw new IllegalStateException( errorMsg );
            }
        }

        List<String> profilesForSetting( final PwmSetting pwmSetting )
        {
            if ( !pwmSetting.getCategory().hasProfiles() && pwmSetting.getSyntax() != PwmSettingSyntax.PROFILE )
            {
                return Collections.emptyList();
            }

            final PwmSetting profileSetting;
            if ( pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE )
            {
                profileSetting = pwmSetting;
            }
            else
            {
                profileSetting = pwmSetting.getCategory().getProfileSetting();
            }

            final StoredValue effectiveValue;
            {
                final Optional<StoredConfigData.ValueAndMetaCarrier> configuredValue = readSetting( profileSetting, null );
                if ( configuredValue.isPresent() )
                {
                    effectiveValue = configuredValue.get().getValue();
                }
                else
                {
                    effectiveValue = profileSetting.getDefaultValue( templateSetSupplier.get() );
                }
            }

            final List<String> settingValues = ( List<String> ) effectiveValue.toNativeObject();
            final LinkedList<String> profiles = new LinkedList<>( settingValues );
            profiles.removeIf( StringUtil::isEmpty );
            return Collections.unmodifiableList( profiles );
        }


        public PwmSecurityKey getKey() throws PwmUnrecoverableException
        {
            final XmlElement rootElement = document.getRootElement();
            final String createTimeString = rootElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_CREATE_TIME );
            return new PwmSecurityKey( createTimeString + "StoredConfiguration" );
        }

        String readCreateTime()
        {
            final XmlElement rootElement = document.getRootElement();
            final String createTimeString = rootElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_CREATE_TIME );
            if ( StringUtil.isEmpty( createTimeString ) )
            {
                throw new IllegalStateException( "missing createTime timestamp" );
            }
            else
            {
                return createTimeString;
            }
        }

        Instant readModifyTime()
        {
            final XmlElement rootElement = document.getRootElement();
            final String modifyTimeString = rootElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME );
            if ( !StringUtil.isEmpty( modifyTimeString ) )
            {
                try
                {
                    return JavaHelper.parseIsoToInstant( modifyTimeString );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( () -> "error parsing root last modified timestamp: " + e.getMessage() );
                }
            }

            return null;
        }

        private final LazySupplier<PwmSettingTemplateSet> templateSetSupplier = new LazySupplier<>( () ->
        {
            final Set<PwmSettingTemplate> templates = new HashSet<>();
            templates.add( readTemplateValue( PwmSetting.TEMPLATE_LDAP ) );
            templates.add( readTemplateValue( PwmSetting.TEMPLATE_STORAGE ) );
            templates.add( readTemplateValue( PwmSetting.DB_VENDOR_TEMPLATE ) );
            return new PwmSettingTemplateSet( templates );
        } );

        private PwmSettingTemplate readTemplateValue( final PwmSetting pwmSetting )
        {
            final Optional<XmlElement> settingElement = xpathForSetting( pwmSetting, null );
            if ( settingElement.isPresent() )
            {
                try
                {
                    final String strValue = ( String ) ValueFactory.fromXmlValues( pwmSetting, settingElement.get(), null ).toNativeObject();
                    return JavaHelper.readEnumFromString( PwmSettingTemplate.class, null, strValue );
                }
                catch ( final IllegalStateException e )
                {
                    LOGGER.error( () -> "error reading template", e );
                }
            }
            return null;
        }

        private Collection<StoredConfigData.ValueAndMetaCarrier> readLocaleBundles()
        {
            final List<StoredConfigData.ValueAndMetaCarrier> returnWrapper = new ArrayList<>();

            for ( final XmlElement localeBundleElement : xpathForLocaleBundles() )
            {
                final String bundleName = localeBundleElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_BUNDLE );
                final Optional<PwmLocaleBundle> pwmLocaleBundle = PwmLocaleBundle.forKey( bundleName );
                pwmLocaleBundle.ifPresent( ( bundle ) ->
                {
                    final String key = localeBundleElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY );
                    if ( bundle.getDisplayKeys().contains( key ) )
                    {
                        final Map<String, String> bundleMap = new LinkedHashMap<>();
                        for ( final XmlElement valueElement : localeBundleElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE ) )
                        {
                            final String localeStrValue = valueElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_LOCALE );
                            bundleMap.put( localeStrValue == null ? "" : localeStrValue, valueElement.getText() );
                        }
                        if ( !bundleMap.isEmpty() )
                        {
                            final StoredConfigItemKey storedConfigItemKey = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle.get(), key );
                            final StoredValue storedValue = new LocalizedStringValue( bundleMap );
                            final ValueMetaData metaData = readMetaDataFromXmlElement( storedConfigItemKey, localeBundleElement ).orElse( null );
                            returnWrapper.add( new StoredConfigData.ValueAndMetaCarrier( storedConfigItemKey, storedValue, metaData ) );
                        }
                    }
                } );
            }
            return Collections.unmodifiableList( returnWrapper );
        }

        private Optional<ValueMetaData> readMetaDataFromXmlElement( final StoredConfigItemKey key, final XmlElement xmlElement )
        {
            Instant instant = null;
            {
                final String modifyTimeValue = xmlElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME );
                if ( !StringUtil.isEmpty( modifyTimeValue ) )
                {
                    try
                    {
                        instant = JavaHelper.parseIsoToInstant( modifyTimeValue );
                    }
                    catch ( final DateTimeParseException e )
                    {
                        e.printStackTrace();
                    }
                }
            }

            UserIdentity userIdentity = null;
            {
                final String modifyUserValue = xmlElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_USER );
                if ( !StringUtil.isEmpty( modifyUserValue ) )
                {
                    try
                    {
                        userIdentity = UserIdentity.fromDelimitedKey( modifyUserValue );
                    }
                    catch ( final DateTimeParseException | PwmUnrecoverableException e )
                    {
                        LOGGER.trace( () -> "unable to parse userIdentity metadata for key " + key.toString() );
                    }
                }
            }

            if ( instant != null || userIdentity != null )
            {
                return Optional.of( new ValueMetaData( instant, userIdentity ) );
            }

            return Optional.empty();
        }

        List<XmlElement> xpathForLocaleBundles()
        {
            final String xpathString = "//localeBundle";
            return document.evaluateXpathToElements( xpathString );
        }

        XmlElement xpathForSettings()
        {
            return document.getRootElement().getChild( StoredConfigXmlConstants.XML_ELEMENT_SETTINGS )
                    .orElseThrow( () -> new IllegalStateException( "configuration xml document missing 'settings' element" ) );
        }

        Optional<XmlElement> xpathForSetting( final PwmSetting setting, final String profileID )
        {
            final String xpathString;
            if ( profileID == null || profileID.length() < 1 )
            {
                xpathString = "//" + StoredConfigXmlConstants.XML_ELEMENT_SETTING + "[@" + StoredConfigXmlConstants.XML_ATTRIBUTE_KEY
                        + "=\"" + setting.getKey()
                        + "\"][(not (@" + StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE + ")) or @"
                        + StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE + "=\"\"]";
            }
            else
            {
                xpathString = "//" + StoredConfigXmlConstants.XML_ELEMENT_SETTING + "[@" + StoredConfigXmlConstants.XML_ATTRIBUTE_KEY
                        + "=\"" + setting.getKey()
                        + "\"][@" + StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE + "=\"" + profileID + "\"]";
            }

            return document.evaluateXpathToElement( xpathString );
        }

        Optional<XmlElement> xpathForConfigProperty( final ConfigurationProperty configProperty )
        {
            final String xpathString = "//" + StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES + "[@" + StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE
                    + "=\"" + StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG + "\"]/"
                    + StoredConfigXmlConstants.XML_ELEMENT_PROPERTY + "[@" + StoredConfigXmlConstants.XML_ATTRIBUTE_KEY + "=\"" + configProperty.getKey() + "\"]";
            return document.evaluateXpathToElement( xpathString );
        }

        List<XmlElement> xpathForAppProperties( )
        {
            final String xpathString = "//" + StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                    + "[@" + StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + "=\"" + StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_APP + "\"]";
            return document.evaluateXpathToElements( xpathString );
        }
    }


    static class XmlOutputHandler
    {
        static void makeXmlOutput( final StoredConfiguration storedConfiguration, final XmlElement rootElement, final OutputSettings outputSettings )
                throws PwmUnrecoverableException
        {
            decorateRootElement( rootElement, storedConfiguration );

            rootElement.addContent( makePropertiesElement( storedConfiguration ) );

            rootElement.addContent( makeSettingsXmlElement( storedConfiguration, outputSettings ) );

            rootElement.addContent( XmlOutputHandler.makeLocaleBundleXmlElements( storedConfiguration ) );
        }

        static void decorateRootElement( final XmlElement rootElement, final StoredConfiguration storedConfiguration )
        {
            rootElement.setComment( Collections.singletonList( generateCommentText() ) );
            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_PWM_VERSION, PwmConstants.BUILD_VERSION );
            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRRIBUTE_PWM_BUILD, PwmConstants.BUILD_NUMBER );
            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_XML_VERSION, XML_FORMAT_VERSION );

            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_CREATE_TIME, storedConfiguration.createTime() );
            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( storedConfiguration.modifyTime() ) );
        }

        static XmlElement makeSettingsXmlElement(
                final StoredConfiguration storedConfiguration,
                final OutputSettings outputSettings
        )
                throws PwmUnrecoverableException
        {
            final PwmSecurityKey pwmSecurityKey = storedConfiguration.getKey();

            final XmlFactory xmlFactory = XmlFactory.getFactory();
            final XmlElement settingsElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_SETTINGS );

            final XmlOutputProcessData xmlOutputProcessData = XmlOutputProcessData.builder()
                    .pwmSecurityKey( pwmSecurityKey )
                    .storedValueEncoderMode( figureEncoderMode( storedConfiguration, outputSettings ) )
                    .build();

            for ( final PwmSetting pwmSetting : PwmSetting.sortedByMenuLocation( PwmConstants.DEFAULT_LOCALE ) )
            {
                if ( !pwmSetting.getFlags().contains( PwmSettingFlag.Deprecated ) )
                {
                    if ( pwmSetting.getCategory().hasProfiles() )
                    {
                        for ( final String profileID : storedConfiguration.profilesForSetting( pwmSetting ) )
                        {
                            final StoredValue storedValue = storedConfiguration.readSetting( pwmSetting, profileID );
                            final XmlElement settingElement = makeSettingXmlElement( storedConfiguration, pwmSetting, profileID, storedValue, xmlOutputProcessData );
                            decorateElementWithMetaData( storedConfiguration, StoredConfigItemKey.fromSetting( pwmSetting, profileID ), settingElement );
                            settingsElement.addContent( settingElement );
                        }
                    }
                    else
                    {
                        final StoredValue storedValue = storedConfiguration.readSetting( pwmSetting, null );
                        final XmlElement settingElement = makeSettingXmlElement( storedConfiguration, pwmSetting, null, storedValue, xmlOutputProcessData );
                        decorateElementWithMetaData( storedConfiguration, StoredConfigItemKey.fromSetting( pwmSetting, null ), settingElement );
                        settingsElement.addContent( settingElement );
                    }
                }
            }

            return settingsElement;
        }

        static StoredValueEncoder.Mode figureEncoderMode(
                final StoredConfiguration storedConfiguration,
                final OutputSettings outputSettings
        )
        {
            if ( outputSettings == null || outputSettings.getMode() == null )
            {
                return StoredValueEncoder.Mode.ENCODED;
            }

            if ( outputSettings.getMode() == OutputSettings.SecureOutputMode.STRIPPED )
            {
                return StoredValueEncoder.Mode.STRIPPED;
            }

            final Optional<String> strValue = storedConfiguration.readConfigProperty( ConfigurationProperty.STORE_PLAINTEXT_VALUES );
            if ( strValue.isPresent() && Boolean.parseBoolean( strValue.get() ) )
            {
                return StoredValueEncoder.Mode.PLAIN;
            }

            return StoredValueEncoder.Mode.ENCODED;
        }


        static XmlElement makeSettingXmlElement(
                final StoredConfiguration storedConfiguration,
                final PwmSetting pwmSetting,
                final String profileID,
                final StoredValue storedValue,
                final XmlOutputProcessData xmlOutputProcessData
        )
        {
            final XmlFactory xmlFactory = XmlFactory.getFactory();

            final XmlElement settingElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_SETTING );
            settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY, pwmSetting.getKey() );

            if ( !StringUtil.isEmpty( profileID ) )
            {
                settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE, profileID );
            }

            settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_SYNTAX, pwmSetting.getSyntax().name() );

            {
                final XmlElement labelElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_LABEL );
                labelElement.addText( pwmSetting.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) );
                settingElement.addContent( labelElement );
            }

            final List<XmlElement> valueElements = new ArrayList<>(  );
            if ( storedConfiguration != null && storedConfiguration.isDefaultValue( pwmSetting, profileID ) )
            {
                final XmlElement defaultValue = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_DEFAULT );
                valueElements.add( defaultValue );
            }
            else
            {
                valueElements.addAll( storedValue.toXmlValues( StoredConfigXmlConstants.XML_ELEMENT_VALUE, xmlOutputProcessData ) );
            }

            settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_SYNTAX_VERSION, String.valueOf( storedValue.currentSyntaxVersion() ) );
            settingElement.addContent( valueElements );
            return settingElement;
        }

        private static void decorateElementWithMetaData(
                final StoredConfiguration storedConfiguration,
                final StoredConfigItemKey key,
                final XmlElement xmlElement
        )
        {
            final Optional<ValueMetaData> valueMetaData = ( ( StoredConfiguration ) storedConfiguration ).readMetaData( key );

            if ( valueMetaData.isPresent() )
            {
                if ( valueMetaData.get().getUserIdentity() != null )
                {
                    xmlElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_USER, valueMetaData.get().getUserIdentity().toDelimitedKey() );
                }

                if ( valueMetaData.get().getModifyDate() != null )
                {
                    xmlElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( valueMetaData.get().getModifyDate() ) );
                }
            }
        }

        private static XmlElement makePropertiesElement( final StoredConfiguration storedConfiguration )
        {
            final XmlFactory xmlFactory = XmlFactory.getFactory();
            final XmlElement propertiesElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES );
            propertiesElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE, StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG );

            for ( final ConfigurationProperty configurationProperty : ConfigurationProperty.values() )
            {
                storedConfiguration.readConfigProperty( configurationProperty ).ifPresent( s ->
                        {
                            final XmlElement propertyElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_PROPERTY );
                            propertyElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY, configurationProperty.getKey() );
                            propertyElement.addText( s );
                            decorateElementWithMetaData( storedConfiguration, StoredConfigItemKey.fromConfigurationProperty( configurationProperty ), propertyElement );
                            propertiesElement.addContent( propertyElement );
                        }
                );
            }

            return propertiesElement;
        }

        private static List<XmlElement> makeLocaleBundleXmlElements( final StoredConfiguration storedConfiguration )
        {
            final XmlFactory xmlFactory = XmlFactory.getFactory();
            final List<XmlElement> returnList = new ArrayList<>();
            for ( final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.values() )
            {
                for ( final String key : pwmLocaleBundle.getDisplayKeys() )
                {
                    final Map<String, String> localeBundle = storedConfiguration.readLocaleBundleMap( pwmLocaleBundle, key );
                    if ( !JavaHelper.isEmpty( localeBundle ) )
                    {
                        final XmlElement localeBundleElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_LOCALEBUNDLE );
                        localeBundleElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_BUNDLE, pwmLocaleBundle.getKey() );
                        localeBundleElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY, key );

                        final Map<String, String> localeBundleMap = storedConfiguration.readLocaleBundleMap( pwmLocaleBundle, key );
                        for ( final Map.Entry<String, String> entry : localeBundleMap.entrySet() )
                        {
                            final XmlElement valueElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                            if ( !StringUtil.isEmpty( entry.getKey() ) )
                            {
                                valueElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_LOCALE, entry.getKey() );
                            }
                            valueElement.addText( entry.getValue() );
                            localeBundleElement.addContent( valueElement );
                        }

                        decorateElementWithMetaData( storedConfiguration, StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle, key ), localeBundleElement );
                        returnList.add( localeBundleElement );
                    }
                }
            }
            return Collections.unmodifiableList( returnList );
        }

        private static String generateCommentText()
        {
            final String resourceText = ResourceBundle.getBundle( StoredConfigurationFactory.class.getName() ).getString( "configCommentText" );
            return MacroMachine.forStatic().expandMacros( resourceText );
        }
    }

    @Value
    @Builder
    public static class OutputSettings
    {
        @Builder.Default
        private SecureOutputMode mode = SecureOutputMode.NORMAL;

        public enum SecureOutputMode
        {
            NORMAL,
            STRIPPED,
        }
    }
}

