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

package password.pwm.config.stored;

import org.jrivard.xmlchai.AccessMode;
import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlElement;
import org.jrivard.xmlchai.XmlFactory;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingScope;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StoredValueEncoder;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmExceptionLoggingConsumer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.ResourceBundle;
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
    private static final SessionLabel SESSION_LABEL = SessionLabel.SYSTEM_LABEL;
    private static final boolean ENABLE_PERF_LOGGING = false;

    @Override
    public StoredConfiguration readInput( final InputStream inputStream )
            throws PwmUnrecoverableException
    {
        try
        {
            final Instant startTime = Instant.now();
            final XmlFactory xmlFactory = XmlChai.getFactory();
            final XmlDocument xmlDocument = xmlFactory.parse( inputStream, AccessMode.IMMUTABLE );
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
        catch ( final IOException e )
        {
            throw PwmUnrecoverableException.convert( e );
        }
    }

    @Override
    public void writeOutput(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream,
            final StoredConfigurationFactory.OutputSettings outputSettings
    )
            throws PwmUnrecoverableException, IOException
    {
        final XmlFactory xmlFactory = XmlChai.getFactory();
        final XmlDocument xmlDocument = xmlFactory.newDocument( StoredConfigXmlConstants.XML_ELEMENT_ROOT );

        XmlOutputHandler.makeXmlOutput( storedConfiguration, xmlDocument.getRootElement(), outputSettings );

        xmlFactory.output( xmlDocument, outputStream );
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
            final Optional<Instant> modifyTime = readModifyTime();

            // define the parallelized the readers
            final List<Supplier<List<StoredConfigData.ValueAndMetaCarrier>>> suppliers = new ArrayList<>();
            suppliers.add( this::readProperties );
            suppliers.add( this::readSettings );
            suppliers.add( this::readLocaleBundles );

            // execute the readers and put results in the queue
            final Queue<StoredConfigData.ValueAndMetaCarrier> values = new ConcurrentLinkedQueue<>();
            suppliers.forEach( ( supplier ) -> values.addAll( supplier.get() ) );

            final Instant startStoredConfigDataBuild = Instant.now();
            final StoredConfigData storedConfigData = StoredConfigData.builder()
                    .createTime( createTime )
                    .modifyTime( modifyTime.orElse( Instant.now() ) )
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
                xpathForConfigProperty( configurationProperty ).ifPresent( propertyElement -> propertyElement.getText().ifPresent( propertyText ->
                {
                    final StoredConfigKey key = StoredConfigKey.forConfigurationProperty( configurationProperty );
                    final StoredValue storedValue = new StringValue( propertyText );
                    final ValueMetaData metaData = readMetaDataFromXmlElement( key, propertyElement ).orElse( null );
                    valueAndMetaWrapper.add( new StoredConfigData.ValueAndMetaCarrier( key, storedValue, metaData ) );
                } ) );
            }
            perfLog( "startReadProperties", startReadProperties );
            return valueAndMetaWrapper;
        }

        private List<StoredConfigData.ValueAndMetaCarrier> readSettings()
        {
            final Instant startReadSettings = Instant.now();
            final Function<XmlElement, Stream<StoredConfigData.ValueAndMetaCarrier>> readSettingForXmlElement = xmlElement ->
            {
                final Optional<StoredConfigData.ValueAndMetaCarrier> valueAndMetaCarrier = readSetting( xmlElement );
                return valueAndMetaCarrier.stream();
            };

            final Instant startSettingElementXPath = Instant.now();
            final List<XmlElement> settingElements = xpathForAllSetting();
            perfLog( "startSettingElementXPath", startSettingElementXPath );

            final List<StoredConfigData.ValueAndMetaCarrier> results = settingElements
                    .stream()
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
            final Instant startReadSetting = Instant.now();

            final Optional<String> settingKey = settingElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY );
            final Optional<String> profileID = settingElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE );

            if ( settingKey.isPresent() )
            {
                final Optional<PwmSetting> optionalPwmSetting = PwmSetting.forKey( settingKey.get() );
                if ( optionalPwmSetting.isPresent() )
                {
                    final PwmSetting pwmSetting = optionalPwmSetting.get();
                    final boolean defaultValueSaved = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_DEFAULT ).isPresent();
                    final DomainID domainID = readDomainIdForSetting( settingElement, pwmSetting );
                    final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, profileID.orElse( null ), domainID );
                    final ValueMetaData metaData = readMetaDataFromXmlElement( key, settingElement ).orElse( null );

                    final StoredValue storedValue = defaultValueSaved
                            ? null
                            : ValueFactory.fromXmlValues( pwmSetting, settingElement, pwmSecurityKey );

                    perfLog( "startReadSetting: " + key, startReadSetting );

                    return Optional.of( new StoredConfigData.ValueAndMetaCarrier( key, storedValue, metaData ) );
                }
            }

            return Optional.empty();
        }

        private static DomainID readDomainIdForSetting( final XmlElement xmlElement, final PwmSetting pwmSetting )
        {
            final Optional<DomainID> specifiedDomain = readDomainIDForNonSystemDomainElement( xmlElement );
            if ( specifiedDomain.isPresent() )
            {
                return specifiedDomain.get();
            }

            if ( pwmSetting.getCategory().getScope() == PwmSettingScope.SYSTEM )
            {
                return DomainID.systemId();
            }

            return DomainID.DOMAIN_ID_DEFAULT;
        }

        private static Optional<DomainID> readDomainIDForNonSystemDomainElement( final XmlElement xmlElement )
        {
            final Optional<String> domainID = xmlElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_DOMAIN );
            if ( domainID.isPresent() )
            {
                final String domainIdStr = domainID.get();
                if ( DomainID.systemId().stringValue().equals( domainIdStr ) )
                {
                    return Optional.of( DomainID.systemId() );
                }
                else
                {
                    return Optional.of( DomainID.create( domainIdStr ) );
                }
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
            return rootElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_CREATE_TIME )
                    .orElseThrow( () -> new IllegalStateException( "missing createTime timestamp" ) );
        }

        Optional<Instant> readModifyTime()
        {
            final XmlElement rootElement = document.getRootElement();
            final Optional<String> modifyTimeString = rootElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME );
            if ( modifyTimeString.isPresent() )
            {
                try
                {
                    return Optional.of( JavaHelper.parseIsoToInstant( modifyTimeString.get() ) );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( () -> "error parsing root last modified timestamp: " + e.getMessage() );
                }
            }

            return Optional.empty();
        }

        private List<StoredConfigData.ValueAndMetaCarrier> readLocaleBundles()
        {
            final Instant startReadLocaleBundles = Instant.now();
            final Function<XmlElement, Stream<StoredConfigData.ValueAndMetaCarrier>> xmlToLocaleBundleReader = xmlElement ->
            {
                final Optional<String> bundleName = xmlElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_BUNDLE );
                if ( bundleName.isPresent() )
                {
                    final Optional<PwmLocaleBundle> pwmLocaleBundle = PwmLocaleBundle.forKey( bundleName.get() );
                    if ( pwmLocaleBundle.isPresent() )
                    {
                        final Optional<String> key = xmlElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY );
                        if ( key.isPresent() )
                        {
                            if ( pwmLocaleBundle.get().getDisplayKeys().contains( key.get() ) )
                            {
                                final Map<String, String> bundleMap = new LinkedHashMap<>();
                                for ( final XmlElement valueElement : xmlElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE ) )
                                {
                                    final String localeStrValue = valueElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_LOCALE ).orElse( "" );
                                    valueElement.getText().ifPresent( text -> bundleMap.put( localeStrValue, text ) );
                                }
                                if ( !bundleMap.isEmpty() )
                                {
                                    final DomainID domainID = readDomainIDForNonSystemDomainElement( xmlElement ).orElse( DomainID.systemId() );
                                    final StoredConfigKey storedConfigKey = StoredConfigKey.forLocaleBundle( pwmLocaleBundle.get(), key.get(), domainID );
                                    final StoredValue storedValue = new LocalizedStringValue( bundleMap );
                                    final ValueMetaData metaData = readMetaDataFromXmlElement( storedConfigKey, xmlElement ).orElse( null );
                                    return Stream.of( new StoredConfigData.ValueAndMetaCarrier( storedConfigKey, storedValue, metaData ) );
                                }
                            }
                        }
                    }
                }

                return Stream.empty();
            };

            final List<StoredConfigData.ValueAndMetaCarrier> results = xpathForLocaleBundles()
                    .stream()
                    .flatMap( xmlToLocaleBundleReader )
                    .collect( Collectors.toList() );
            perfLog( "startReadLocaleBundles", startReadLocaleBundles );
            return results;
        }

        private Optional<ValueMetaData> readMetaDataFromXmlElement( final StoredConfigKey key, final XmlElement xmlElement )
        {
            Instant instant = null;
            {
                final Optional<String> modifyTimeValue = xmlElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME );
                if ( modifyTimeValue.isPresent() )
                {
                    try
                    {
                        instant = JavaHelper.parseIsoToInstant( modifyTimeValue.get() );
                    }
                    catch ( final DateTimeParseException e )
                    {
                        LOGGER.warn( () -> "error parsing modifyTime for key '" + key.toString() + "', error: " + e.getMessage() );
                    }
                }
            }

            UserIdentity userIdentity = null;

            // oldStyle modifyUser attribute
            {
                final Optional<String> modifyUserValue = xmlElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_USER );
                if ( modifyUserValue.isPresent() )
                {
                    try
                    {
                        userIdentity = UserIdentity.fromDelimitedKey( SESSION_LABEL, modifyUserValue.get() );
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.trace( () -> "unable to parse userIdentity attribute metadata for key " + key.toString() );
                    }
                }
            }

            // newstyle modifyUser xml value
            {
                final Optional<XmlElement> metaElement = xmlElement.getChild( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_USER );
                if ( metaElement.isPresent() )
                {
                    try
                    {
                        userIdentity = UserIdentity.fromDelimitedKey( SESSION_LABEL, metaElement.get().getText().orElse( "" ) );
                    }
                    catch ( final DateTimeParseException | PwmUnrecoverableException e )
                    {
                        LOGGER.trace( () -> "unable to parse userIdentity element for key " + key.toString() );
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

            rootElement.attachElement( makePropertiesElement( storedConfiguration ) );
            rootElement.attachElement( makeSettingsXmlElement( storedConfiguration, outputSettings ) );
            rootElement.attachElement( XmlOutputHandler.makeLocaleBundleXmlElements( storedConfiguration ) );
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

            final XmlFactory xmlFactory = XmlChai.getFactory();
            final XmlElement settingsElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_SETTINGS );

            final XmlOutputProcessData xmlOutputProcessData = XmlOutputProcessData.builder()
                    .pwmSecurityKey( pwmSecurityKey )
                    .storedValueEncoderMode( figureEncoderMode( storedConfiguration, outputSettings ) )
                    .build();

            final Consumer<StoredConfigKey> xmlSettingWriter = storedConfigItemKey ->
            {
                storedConfiguration.readStoredValue( storedConfigItemKey ).ifPresent( ( storedValue ->
                {
                    final XmlElement settingElement = makeSettingXmlElement( storedConfiguration, storedConfigItemKey, storedValue, xmlOutputProcessData );
                    decorateElementWithMetaData( storedConfiguration, storedConfigItemKey, settingElement );
                    settingsElement.attachElement( settingElement );
                } ) );
            };

            StoredConfigurationUtil.allPossibleSettingKeysForConfiguration( storedConfiguration )
                    .stream()
                    .filter( ( key ) -> StoredConfigKey.RecordType.SETTING.equals( key.getRecordType() ) )
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
                final StoredConfigKey key,
                final StoredValue storedValue,
                final XmlOutputProcessData xmlOutputProcessData
        )
        {
            Objects.requireNonNull( storedValue );

            final PwmSetting pwmSetting = key.toPwmSetting();
            final String profileID = key.getProfileID();

            final XmlFactory xmlFactory = XmlChai.getFactory();

            final XmlElement settingElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_SETTING );


            settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY, pwmSetting.getKey() );

            if ( StringUtil.notEmpty( profileID ) )
            {
                settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_PROFILE, profileID );
            }

            settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_SYNTAX, pwmSetting.getSyntax().name() );

            {
                final XmlElement labelElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_LABEL );
                labelElement.setText( pwmSetting.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) );
                settingElement.attachElement( labelElement );
            }

            final List<XmlElement> valueElements = new ArrayList<>(  );
            if ( storedConfiguration != null && StoredConfigurationUtil.isDefaultValue( storedConfiguration, key ) )
            {
                final XmlElement defaultValue = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_DEFAULT );
                valueElements.add( defaultValue );
            }
            else
            {
                valueElements.addAll( storedValue.toXmlValues( StoredConfigXmlConstants.XML_ELEMENT_VALUE, xmlOutputProcessData ) );
            }

            decorateElementWithDomain( storedConfiguration, key, settingElement );
            settingElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_SYNTAX_VERSION, String.valueOf( storedValue.currentSyntaxVersion() ) );
            settingElement.attachElement( valueElements );
            return settingElement;
        }

        private static void decorateElementWithDomain(
                final StoredConfiguration storedConfiguration,
                final StoredConfigKey key,
                final XmlElement xmlElement
        )
        {
            final DomainID domainID = key.getDomainID();
            if ( !domainID.isSystem() || StoredConfigurationUtil.domainList( storedConfiguration ).size() > 1 )
            {
                xmlElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_DOMAIN, domainID.stringValue() );
            }
        }

        private static void decorateElementWithMetaData(
                final StoredConfiguration storedConfiguration,
                final StoredConfigKey key,
                final XmlElement xmlElement
        )
        {
            final Optional<ValueMetaData> valueMetaData = storedConfiguration.readMetaData( key );

            if ( valueMetaData.isPresent() )
            {
                if ( valueMetaData.get().getUserIdentity() != null )
                {
                    final XmlElement metaElement = XmlChai.getFactory().newElement( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_USER );
                    metaElement.setText( valueMetaData.get().getUserIdentity().toDelimitedKey() );
                    xmlElement.attachElement( metaElement );
                }

                if ( valueMetaData.get().getModifyDate() != null )
                {
                    xmlElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( valueMetaData.get().getModifyDate() ) );
                }
            }
        }

        private static XmlElement makePropertiesElement( final StoredConfiguration storedConfiguration )
        {
            final XmlFactory xmlFactory = XmlChai.getFactory();
            final XmlElement propertiesElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES );
            propertiesElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE, StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG );

            for ( final ConfigurationProperty configurationProperty : ConfigurationProperty.values() )
            {
                storedConfiguration.readConfigProperty( configurationProperty ).ifPresent( s ->
                        {
                            final XmlElement propertyElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_PROPERTY );
                            propertyElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY, configurationProperty.getKey() );
                            propertyElement.setText( s );
                            decorateElementWithMetaData( storedConfiguration, StoredConfigKey.forConfigurationProperty( configurationProperty ), propertyElement );
                            propertiesElement.attachElement( propertyElement );
                        }
                );
            }

            return propertiesElement;
        }

        private static List<XmlElement> makeLocaleBundleXmlElements( final StoredConfiguration storedConfiguration )
        {
            final XmlFactory xmlFactory = XmlChai.getFactory();
            final List<XmlElement> returnList = new ArrayList<>();
            for ( final DomainID domainID : StoredConfigurationUtil.domainList( storedConfiguration ) )
            {
                for ( final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.values() )
                {
                    for ( final String key : pwmLocaleBundle.getDisplayKeys() )
                    {
                        final Map<String, String> localeBundle = storedConfiguration.readLocaleBundleMap( pwmLocaleBundle, key, domainID );
                        if ( !CollectionUtil.isEmpty( localeBundle ) )
                        {
                            final XmlElement localeBundleElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_LOCALE_BUNDLE );
                            localeBundleElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_BUNDLE, pwmLocaleBundle.getKey() );
                            localeBundleElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY, key );

                            final Map<String, String> localeBundleMap = storedConfiguration.readLocaleBundleMap( pwmLocaleBundle, key, domainID );
                            for ( final Map.Entry<String, String> entry : localeBundleMap.entrySet() )
                            {
                                final XmlElement valueElement = xmlFactory.newElement( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                                if ( StringUtil.notEmpty( entry.getKey() ) )
                                {
                                    valueElement.setAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_LOCALE, entry.getKey() );
                                }
                                valueElement.setText( entry.getValue() );
                                localeBundleElement.attachElement( valueElement );
                            }

                            final StoredConfigKey storedConfigKey = StoredConfigKey.forLocaleBundle( pwmLocaleBundle, key, domainID );
                            decorateElementWithDomain( storedConfiguration, storedConfigKey, localeBundleElement );
                            decorateElementWithMetaData( storedConfiguration, storedConfigKey, localeBundleElement );
                            returnList.add( localeBundleElement );
                        }
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

    static class XmlCleaner
    {
        private static final List<PwmExceptionLoggingConsumer<XmlDocument>> XML_PRE_PROCESSORS = List.of(
                new MigratePreValueXmlElements(),
                new MigrateOldPropertyFormat(),
                new AppPropertyOverrideMigration(),
                new MigrateDeprecatedProperties(),
                new UpdatePropertiesWithoutType() );

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
                        + StoredConfigXmlConstants.XML_ELEMENT_SETTING );
                for ( final XmlElement settingElement : settingElements )
                {
                    final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                    final Optional<XmlElement> defaultElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_DEFAULT );
                    if ( valueElement.isPresent() && defaultElement.isPresent() )
                    {
                        final Optional<String> textValue = settingElement.getText();
                        if ( textValue.isPresent() )
                        {
                            final XmlElement newValueElement = XmlChai.getFactory().newElement( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                            newValueElement.setText( textValue.get().trim() );
                            settingElement.attachElement( newValueElement );
                            final String key = settingElement.getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_KEY ).orElse( "" );
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
                        + "[@" + StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + "=\""
                        + StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG + "\"]";
                final Optional<XmlElement> configPropertiesElement = xmlDocument.evaluateXpathToElement( configPropertiesXpath );

                // read list of old //properties[not (@type)]/property
                final String nonAttributedPropertyXpath = "//" + StoredConfigXmlConstants.XML_ELEMENT_PROPERTIES
                        + "[not (@" + StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE + ")]/"
                        + StoredConfigXmlConstants.XML_ELEMENT_PROPERTY;
                final List<XmlElement> nonAttributedProperties = xmlDocument.evaluateXpathToElements( nonAttributedPropertyXpath );

                if ( configPropertiesElement.isPresent() && nonAttributedProperties != null )
                {
                    for ( final XmlElement element : nonAttributedProperties )
                    {
                        element.detach();
                        configPropertiesElement.get().attachElement( element );
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
                        final String value = propertyElement.get( 0 ).getText().orElse( "" );
                        propertyElement.get( 0 ).detach();
                        attachStringSettingElement( xmlDocument, PwmSetting.TEMPLATE_LDAP, value );

                    }
                }
                {
                    final String xpathString = "//property[@key=\"" + ConfigurationProperty.NOTES.getKey() + "\"]";
                    final List<XmlElement> propertyElement = xmlDocument.evaluateXpathToElements( xpathString );
                    if ( propertyElement != null && !propertyElement.isEmpty() )
                    {
                        final String value = propertyElement.get( 0 ).getText().orElse( "" );
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

                final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, null, DomainID.DOMAIN_ID_DEFAULT );

                final XmlElement settingElement = StoredConfigXmlSerializer.XmlOutputHandler.makeSettingXmlElement(
                        null,
                        key,
                        new StringValue( stringValue ),
                        XmlOutputProcessData.builder().storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN ).pwmSecurityKey( pwmSecurityKey ).build() );
                final Optional<XmlElement> settingsElement = xmlDocument.getRootElement().getChild( StoredConfigXmlConstants.XML_ELEMENT_SETTING );
                settingsElement.ifPresent( xmlElement -> xmlElement.attachElement( settingElement ) );
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
                            StoredConfigXmlConstants.XML_ATTRIBUTE_TYPE,
                            StoredConfigXmlConstants.XML_ATTRIBUTE_VALUE_CONFIG );
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
                        final Optional<String> key = property.getAttribute( "key" );
                        final Optional<String> value = property.getText();
                        if ( key.isPresent() && value.isPresent() )
                        {
                            LOGGER.info( () -> "migrating app-property config element '" + key.get() + "' to setting " + PwmSetting.APP_PROPERTY_OVERRIDES.getKey() );
                            final String newValue = key.get() + "=" + value.get();

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

                inputDocumentReader.xpathForSetting( PwmSetting.APP_PROPERTY_OVERRIDES, null ).ifPresent( XmlElement::detach );

                final PwmSecurityKey pwmSecurityKey = inputDocumentReader.getKey();

                final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.APP_PROPERTY_OVERRIDES, null, DomainID.DOMAIN_ID_DEFAULT );

                final XmlElement settingElement = StoredConfigXmlSerializer.XmlOutputHandler.makeSettingXmlElement(
                        null,
                        key,
                        new StringArrayValue( newValues ),
                        XmlOutputProcessData.builder().storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN ).pwmSecurityKey( pwmSecurityKey ).build() );
                final Optional<XmlElement> settingsElement = xmlDocument.getRootElement().getChild( StoredConfigXmlConstants.XML_ELEMENT_SETTING );
                settingsElement.ifPresent( ( s ) -> s.attachElement( settingElement ) );
            }
        }

        private static int readDocVersion( final XmlDocument xmlDocument )
        {
            final String xmlVersionStr = xmlDocument.getRootElement().getAttribute( StoredConfigXmlConstants.XML_ATTRIBUTE_XML_VERSION ).orElse( "0" );
            return JavaHelper.silentParseInt( xmlVersionStr, 0 );
        }
    }
}
