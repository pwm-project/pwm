/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StoredValueEncoder;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.PwmExceptionLoggingConsumer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StoredConfigXmlSerializer implements StoredConfigSerializer
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigXmlSerializer.class );
    private static final String XML_FORMAT_VERSION = "5";
    private static final boolean ENABLE_PERF_LOGGING = false;

    @Override
    public StoredConfiguration readInput( final InputStream inputStream )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final XmlFactory xmlFactory = XmlFactory.getFactory();
        final XmlDocument xmlDocument = xmlFactory.parseXml( inputStream );
        perfLog( "parseXml", startTime );

        final Instant startPreProcessXml = Instant.now();
        XmlCleaner.preProcessXml( xmlDocument );
        perfLog( "startPreProcessXml", startPreProcessXml );


        final XmlInputDocumentReader xmlInputDocumentReader = new XmlInputDocumentReader( xmlDocument );
        final StoredConfigData storedConfigData = xmlInputDocumentReader.getStoredConfigData();
        final StoredConfiguration storedConfiguration = new StoredConfigurationImpl( storedConfigData );
        perfLog( "readInputTotal", startTime );
        return storedConfiguration;
    }

    @Override
    public void writeOutput(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream,
            final StoredConfigurationFactory.OutputSettings outputSettings
    )
            throws PwmUnrecoverableException, IOException
    {
        final XmlFactory xmlFactory = XmlFactory.getFactory();
        final XmlDocument xmlDocument = xmlFactory.newDocument( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_ROOT );

        XmlOutputHandler.makeXmlOutput( storedConfiguration, xmlDocument.getRootElement(), outputSettings );

        xmlFactory.outputDocument( xmlDocument, outputStream );
    }

    private static void perfLog( final CharSequence msg, final Instant startTimestamp )
    {
        if ( ENABLE_PERF_LOGGING )
        {
            final String output = msg + "::" + TimeDuration.compactFromCurrent( startTimestamp );
            LOGGER.trace( () -> output );
            System.out.println( output );
        }
    }

    static class XmlInputDocumentReader
    {
        private final EnumMap<PwmSettingCategory, List<String>> cachedProfiles = new EnumMap<>( PwmSettingCategory.class );

        private final XmlDocument document;
        private final PwmSecurityKey pwmSecurityKey;

        XmlInputDocumentReader( final XmlDocument document )
                throws PwmUnrecoverableException
        {
            this.document = document;
            this.pwmSecurityKey = new PwmSecurityKey( readCreateTime() + "StoredConfiguration" );
        }

        StoredConfigData getStoredConfigData()
        {
            final String createTime = readCreateTime();
            final Instant modifyTime = readModifyTime();

            // define the parallelized the readers
            final List<Supplier<List<StoredConfigData.ValueAndMetaCarrier>>> suppliers = new ArrayList<>();
            suppliers.add( this::readProperties );
            suppliers.add( this::readSettings );
            suppliers.add( this::readLocaleBundles );

            // execute the readers and put results in the queue
            final Queue<StoredConfigData.ValueAndMetaCarrier> values = new ConcurrentLinkedQueue<>();
            suppliers.parallelStream().forEach( ( supplier ) -> values.addAll( supplier.get() ) );

            final Instant startStoredConfigDataBuild = Instant.now();
            final StoredConfigData storedConfigData = StoredConfigData.builder()
                    .createTime( createTime )
                    .modifyTime( modifyTime )
                    .metaDatas( StoredConfigData.carrierAsMetaDataMap( values ) )
                    .storedValues( StoredConfigData.carrierAsStoredValueMap( values ) )
                    .build();
            perfLog( "startStoredConfigDataBuild", startStoredConfigDataBuild );
            return storedConfigData;
        }

        private List<StoredConfigData.ValueAndMetaCarrier> readProperties()
        {
            final Instant startReadProperties = Instant.now();
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
            perfLog( "startReadProperties", startReadProperties );
            return valueAndMetaWrapper;
        }

        private List<StoredConfigData.ValueAndMetaCarrier> readSettings( )
        {
            final Instant startReadSettings = Instant.now();
            final Function<XmlElement, Stream<StoredConfigData.ValueAndMetaCarrier>> readSettingForXmlElement = xmlElement ->
            {
                final Optional<StoredConfigData.ValueAndMetaCarrier> valueAndMetaCarrier = readSetting( xmlElement );
                return valueAndMetaCarrier.map( Stream::of ).orElseGet( Stream::empty );
            };

            final List<XmlElement> settingElements = xpathForAllSetting();
            final List<StoredConfigData.ValueAndMetaCarrier> results = settingElements
                    .parallelStream()
                    .flatMap( readSettingForXmlElement )
                    .collect( Collectors.toList() );
            perfLog( "startReadSettings", startReadSettings );
            return results;
        }

        Optional<StoredConfigData.ValueAndMetaCarrier> readSetting( final PwmSetting setting, final String profileID )
        {
            final Optional<XmlElement> settingElement = xpathForSetting( setting, profileID );

            return settingElement.isPresent()
                    ? readSetting( settingElement.get() )
                    : Optional.empty();
        }

        Optional<StoredConfigData.ValueAndMetaCarrier> readSetting( final XmlElement settingElement )
        {
            final String settingKey = settingElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY );
            final String profileID = settingElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE );
            final Optional<PwmSetting> optionalPwmSetting = PwmSetting.forKey( settingKey );
            if ( optionalPwmSetting.isPresent() )
            {
                final PwmSetting pwmSetting = optionalPwmSetting.get();
                final boolean defaultValueSaved = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_DEFAULT ).isPresent();
                final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( pwmSetting, profileID );
                final ValueMetaData metaData = readMetaDataFromXmlElement( key, settingElement ).orElse( null );

                final StoredValue storedValue = defaultValueSaved
                        ? null
                        : ValueFactory.fromXmlValues( pwmSetting, settingElement, pwmSecurityKey );

                return Optional.of( new StoredConfigData.ValueAndMetaCarrier( key, storedValue, metaData ) );
            }

            return Optional.empty();
        }

        public PwmSecurityKey getKey()
        {
            return this.pwmSecurityKey;
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
            final Set<PwmSettingTemplate> templates = EnumSet.noneOf( PwmSettingTemplate.class );
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

        private List<StoredConfigData.ValueAndMetaCarrier> readLocaleBundles()
        {
            final Instant startReadLocaleBundles = Instant.now();
            final Function<XmlElement, Stream<StoredConfigData.ValueAndMetaCarrier>> xmlToLocaleBundleReader = xmlElement ->
            {
                final String bundleName = xmlElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_BUNDLE );
                final Optional<PwmLocaleBundle> pwmLocaleBundle = PwmLocaleBundle.forKey( bundleName );
                if ( pwmLocaleBundle.isPresent() )
                {
                    final String key = xmlElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY );
                    if ( pwmLocaleBundle.get().getDisplayKeys().contains( key ) )
                    {
                        final Map<String, String> bundleMap = new LinkedHashMap<>();
                        for ( final XmlElement valueElement : xmlElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE ) )
                        {
                            final String localeStrValue = valueElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_LOCALE );
                            bundleMap.put( localeStrValue == null ? "" : localeStrValue, valueElement.getText() );
                        }
                        if ( !bundleMap.isEmpty() )
                        {
                            final StoredConfigItemKey storedConfigItemKey = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle.get(), key );
                            final StoredValue storedValue = new LocalizedStringValue( bundleMap );
                            final ValueMetaData metaData = readMetaDataFromXmlElement( storedConfigItemKey, xmlElement ).orElse( null );
                            return Stream.of( new StoredConfigData.ValueAndMetaCarrier( storedConfigItemKey, storedValue, metaData ) );
                        }
                    }
                }

                return Stream.empty();
            };

            final List<StoredConfigData.ValueAndMetaCarrier> results = xpathForLocaleBundles()
                    .parallelStream()
                    .flatMap( xmlToLocaleBundleReader )
                    .collect( Collectors.toList() );
            perfLog( "startReadLocaleBundles", startReadLocaleBundles );
            return results;
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

        List<XmlElement> xpathForAllSetting()
        {
            final String xpathString = "//" + StoredConfigXmlConstants.XML_ELEMENT_SETTING;
            return document.evaluateXpathToElements( xpathString );
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
        static void makeXmlOutput( final StoredConfiguration storedConfiguration, final XmlElement rootElement, final StoredConfigurationFactory.OutputSettings outputSettings )
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
            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_PWM_BUILD, PwmConstants.BUILD_NUMBER );
            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_XML_VERSION, XML_FORMAT_VERSION );

            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_CREATE_TIME, storedConfiguration.createTime() );
            rootElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( storedConfiguration.modifyTime() ) );
        }

        static XmlElement makeSettingsXmlElement(
                final StoredConfiguration storedConfiguration,
                final StoredConfigurationFactory.OutputSettings outputSettings
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

            final Consumer<StoredConfigItemKey> xmlSettingWriter = storedConfigItemKey ->
            {
                final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
                final String profileID = storedConfigItemKey.getProfileID();
                final StoredValue storedValue = storedConfiguration.readSetting( pwmSetting, profileID );
                final XmlElement settingElement = makeSettingXmlElement( storedConfiguration, pwmSetting, profileID, storedValue, xmlOutputProcessData );
                decorateElementWithMetaData( storedConfiguration, StoredConfigItemKey.fromSetting( pwmSetting, profileID ), settingElement );
                settingsElement.addContent( settingElement );
            };

            StoredConfigurationUtil.allPossibleSettingKeysForConfiguration( storedConfiguration )
                    .parallelStream()
                    .filter( ( key ) -> StoredConfigItemKey.RecordType.SETTING.equals( key.getRecordType() ) )
                    .filter( ( key ) -> !key.toPwmSetting().getFlags().contains( PwmSettingFlag.Deprecated ) )
                    .sorted()
                    .forEachOrdered( xmlSettingWriter );

            return settingsElement;
        }

        static StoredValueEncoder.Mode figureEncoderMode(
                final StoredConfiguration storedConfiguration,
                final StoredConfigurationFactory.OutputSettings outputSettings
        )
        {
            if ( outputSettings == null || outputSettings.getMode() == null )
            {
                return StoredValueEncoder.Mode.ENCODED;
            }

            if ( outputSettings.getMode() == StoredConfigurationFactory.OutputSettings.SecureOutputMode.STRIPPED )
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
            final Optional<ValueMetaData> valueMetaData = storedConfiguration.readMetaData( key );

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
                        final XmlElement localeBundleElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_LOCALE_BUNDLE );
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
            return MacroRequest.forStatic().expandMacros( resourceText );
        }
    }

    public static class StoredConfigXmlConstants
    {
        public static final String XML_ATTRIBUTE_TYPE = "type";
        public static final String XML_ELEMENT_ROOT = "PwmConfiguration";
        public static final String XML_ELEMENT_PROPERTIES = "properties";
        public static final String XML_ELEMENT_PROPERTY = "property";
        public static final String XML_ELEMENT_SETTINGS = "settings";
        public static final String XML_ELEMENT_SETTING = "setting";
        public static final String XML_ELEMENT_DEFAULT = "default";
        public static final String XML_ELEMENT_LOCALE_BUNDLE = "localeBundle";
        public static final String XML_ELEMENT_LABEL = "label";
        public static final String XML_ELEMENT_VALUE = "value";

        public static final String XML_ATTRIBUTE_KEY = "key";
        public static final String XML_ATTRIBUTE_SYNTAX = "syntax";
        public static final String XML_ATTRIBUTE_PROFILE = "profile";
        public static final String XML_ATTRIBUTE_VALUE_APP = "app";
        public static final String XML_ATTRIBUTE_VALUE_CONFIG = "config";
        public static final String XML_ATTRIBUTE_CREATE_TIME = "createTime";
        public static final String XML_ATTRIBUTE_MODIFY_TIME = "modifyTime";
        public static final String XML_ATTRIBUTE_MODIFY_USER = "modifyUser";
        public static final String XML_ATTRIBUTE_SYNTAX_VERSION = "syntaxVersion";
        public static final String XML_ATTRIBUTE_BUNDLE = "bundle";
        public static final String XML_ATTRIBUTE_XML_VERSION = "xmlVersion";
        public static final String XML_ATTRIBUTE_PWM_BUILD = "pwmBuild";
        public static final String XML_ATTRIBUTE_PWM_VERSION = "pwmVersion";
        public static final String XML_ATTRIBUTE_LOCALE = "locale";
    }

    static class XmlCleaner
    {
        private static final List<PwmExceptionLoggingConsumer<XmlDocument>> XML_PRE_PROCESSORS = Collections.unmodifiableList( Arrays.asList(
                new MigratePreValueXmlElements(),
                new MigrateOldPropertyFormat(),
                new AppPropertyOverrideMigration(),
                new MigrateDeprecatedProperties(),
                new UpdatePropertiesWithoutType()
        ) );

        static void preProcessXml(
                final XmlDocument document
        )
        {
            XML_PRE_PROCESSORS.forEach( ( c ) ->
            {
                final Instant startTime = Instant.now();
                PwmExceptionLoggingConsumer.wrapConsumer( c ).accept( document );
                perfLog( "preProcessor-" + c.getClass().getName(), startTime );
            } );
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
                        + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_SETTING );
                for ( final XmlElement settingElement : settingElements )
                {
                    final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                    final Optional<XmlElement> defaultElement = settingElement.getChild( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_DEFAULT );
                    if ( valueElement.isPresent() && defaultElement.isPresent() )
                    {
                        final String textValue = settingElement.getTextTrim();
                        if ( !StringUtil.isEmpty( textValue ) )
                        {
                            final XmlElement newValueElement = XmlFactory.getFactory().newElement( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                            newValueElement.addText( textValue );
                            settingElement.addContent( newValueElement );
                            final String key = settingElement.getAttributeValue( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_KEY );
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
                final String configPropertiesXpath = "//" + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                        + "[@" + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + "=\""
                        + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG + "\"]";
                final Optional<XmlElement> configPropertiesElement = xmlDocument.evaluateXpathToElement( configPropertiesXpath );

                // read list of old //properties[not (@type)]/property
                final String nonAttributedPropertyXpath = "//" + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                        + "[not (@" + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + ")]/"
                        + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_PROPERTY;
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
                final String oldPropertiesXpath = "//" + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                        + "[not (@" + StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + ")]";
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
                final StoredConfigXmlSerializer.XmlInputDocumentReader inputDocumentReader = new StoredConfigXmlSerializer.XmlInputDocumentReader( xmlDocument );

                final PwmSecurityKey pwmSecurityKey = inputDocumentReader.getKey();

                final XmlElement settingElement = StoredConfigXmlSerializer.XmlOutputHandler.makeSettingXmlElement(
                        null,
                        pwmSetting,
                        null,
                        new StringValue( stringValue ),
                        XmlOutputProcessData.builder().storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN ).pwmSecurityKey( pwmSecurityKey ).build() );
                final Optional<XmlElement> settingsElement = xmlDocument.getRootElement().getChild( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_SETTING );
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
                    propertiesElement.setAttribute(
                            StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE,
                            StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG );
                }
            }
        }

        private static class AppPropertyOverrideMigration implements PwmExceptionLoggingConsumer<XmlDocument>
        {
            @Override
            public void accept( final XmlDocument xmlDocument ) throws PwmUnrecoverableException
            {
                final StoredConfigXmlSerializer.XmlInputDocumentReader documentReader = new StoredConfigXmlSerializer.XmlInputDocumentReader( xmlDocument );
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
                                valueAndMetaTuple.ifPresent( ( t ) ->
                                {
                                    if ( t.getValue() != null )
                                    {
                                        existingValues.addAll( ValueTypeConverter.valueToStringArray( t.getValue() ) );
                                    }
                                } );
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
                final StoredConfigXmlSerializer.XmlInputDocumentReader inputDocumentReader = new StoredConfigXmlSerializer.XmlInputDocumentReader( xmlDocument );

                {
                    final Optional<XmlElement> existingAppPropertySetting = inputDocumentReader.xpathForSetting( PwmSetting.APP_PROPERTY_OVERRIDES, null );
                    existingAppPropertySetting.ifPresent( XmlElement::detach );
                }

                final PwmSecurityKey pwmSecurityKey = inputDocumentReader.getKey();

                final XmlElement settingElement = StoredConfigXmlSerializer.XmlOutputHandler.makeSettingXmlElement(
                        null,
                        PwmSetting.APP_PROPERTY_OVERRIDES,
                        null,
                        new StringArrayValue( newValues ),
                        XmlOutputProcessData.builder().storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN ).pwmSecurityKey( pwmSecurityKey ).build() );
                final Optional<XmlElement> settingsElement = xmlDocument.getRootElement().getChild( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_SETTING );
                settingsElement.ifPresent( ( s ) -> s.addContent( settingElement ) );
            }
        }

        private static int readDocVersion( final XmlDocument xmlDocument )
        {
            final String xmlVersionStr = xmlDocument.getRootElement().getAttributeValue( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ATTRIBUTE_XML_VERSION );
            return JavaHelper.silentParseInt( xmlVersionStr, 0 );
        }
    }
}
