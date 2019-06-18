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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.StoredValue;
import password.pwm.config.value.NamedSecretValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.BCrypt;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author Jason D. Rivard
 */
@SuppressWarnings( "all" ) // this class will be replaced by NGStoredConfiguration
public class StoredConfigurationImpl implements StoredConfiguration
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationImpl.class );
    static final String XML_FORMAT_VERSION = "4";

    private XmlDocument document = XmlFactory.getFactory().newDocument( XML_ELEMENT_ROOT );
    private ChangeLog changeLog = new ChangeLog();

    private boolean locked;
    private final boolean setting_writeLabels = true;
    private final ReentrantReadWriteLock domModifyLock = new ReentrantReadWriteLock();

    private final XmlHelper xmlHelper = new XmlHelper();

    public static StoredConfigurationImpl newStoredConfiguration( ) throws PwmUnrecoverableException
    {
        return new StoredConfigurationImpl();
    }

    public static StoredConfigurationImpl copy( final StoredConfigurationImpl input ) throws PwmUnrecoverableException
    {
        final StoredConfigurationImpl copy = new StoredConfigurationImpl();
        copy.document = input.document.copy();
        return copy;
    }

    public static StoredConfigurationImpl fromXml( final InputStream xmlData )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        //validateXmlSchema(xmlData);

        final XmlDocument inputDocument = XmlFactory.getFactory().parseXml( xmlData );
        final StoredConfigurationImpl newConfiguration = StoredConfigurationImpl.newStoredConfiguration();

        try
        {
            newConfiguration.document = inputDocument;
            newConfiguration.createTime(); // verify create time;
            ConfigurationCleaner.cleanup( newConfiguration, newConfiguration.document );
        }
        catch ( Exception e )
        {
            final String errorMsg = "error reading configuration file format, error=" + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] { errorMsg } );
            throw new PwmUnrecoverableException( errorInfo );
        }

        checkIfXmlRequiresUpdate( newConfiguration );
        LOGGER.debug( () -> "successfully loaded configuration (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
        return newConfiguration;
    }

    /**
     * Loop through all settings to see if setting value has flag {@link StoredValue#requiresStoredUpdate()} set to true.
     * If so, then call {@link #writeSetting(PwmSetting, StoredValue, password.pwm.bean.UserIdentity)} or {@link #writeSetting(PwmSetting, String, StoredValue, password.pwm.bean.UserIdentity)}
     * for that value so that the xml dom can be updated.
     *
     * @param storedConfiguration stored configuration to check
     */
    private static void checkIfXmlRequiresUpdate( final StoredConfigurationImpl storedConfiguration ) throws PwmUnrecoverableException
    {
        for ( final PwmSetting setting : PwmSetting.values() )
        {
            if ( setting.getSyntax() != PwmSettingSyntax.PROFILE && !setting.getCategory().hasProfiles() )
            {
                final StoredValue value = storedConfiguration.readSetting( setting );
                if ( value.requiresStoredUpdate() )
                {
                    storedConfiguration.writeSetting( setting, value, null );
                }
            }
        }

        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            if ( category.hasProfiles() )
            {
                for ( final String profileID : storedConfiguration.profilesForSetting( category.getProfileSetting() ) )
                {
                    for ( final PwmSetting profileSetting : category.getSettings() )
                    {
                        final StoredValue value = storedConfiguration.readSetting( profileSetting, profileID );
                        if ( value.requiresStoredUpdate() )
                        {
                            storedConfiguration.writeSetting( profileSetting, profileID, value, null );
                        }
                    }
                }
            }
        }
    }

    public void resetAllPasswordValues( final String comment )
    {
        for ( final Iterator<SettingValueRecord> settingValueRecordIterator = new StoredValueIterator( false ); settingValueRecordIterator.hasNext(); )
        {
            final SettingValueRecord settingValueRecord = settingValueRecordIterator.next();
            if ( settingValueRecord.getSetting().getSyntax() == PwmSettingSyntax.PASSWORD )
            {
                this.resetSetting( settingValueRecord.getSetting(), settingValueRecord.getProfile(), null );
                if ( comment != null && !comment.isEmpty() )
                {
                    final XmlElement settingElement = xmlHelper.xpathForSetting( settingValueRecord.getSetting(), settingValueRecord.getProfile() );
                    if ( settingElement != null )
                    {
                        settingElement.setComment( Collections.singletonList( comment ) );
                    }
                }
            }
        }

        final String pwdHash = this.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        if ( pwdHash != null && !pwdHash.isEmpty() )
        {
            this.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, comment );
        }
    }

    public StoredConfigurationImpl( ) throws PwmUnrecoverableException
    {
        try
        {
            ConfigurationCleaner.cleanup( this, document );
            final String createTime = JavaHelper.toIsoDate( Instant.now() );
            document.getRootElement().setAttribute( XML_ATTRIBUTE_CREATE_TIME, createTime );
        }
        catch ( Exception e )
        {
            e.printStackTrace(  );
            throw new IllegalStateException( e );
        }
    }


    @Override
    public String readConfigProperty( final ConfigurationProperty propertyName )
    {
        final XmlElement propertyElement = xmlHelper.xpathForConfigProperty( propertyName );
        return propertyElement == null ? null : propertyElement.getText();
    }

    @Override
    public void writeConfigProperty(
            final ConfigurationProperty propertyName,
            final String value
    )
    {
        domModifyLock.writeLock().lock();
        try
        {

            // remove existing element
            {
                final XmlElement propertyElement  = xmlHelper.xpathForConfigProperty( propertyName );
                if ( propertyElement != null )
                {
                    propertyElement.detach();
                }
            }

            // add new property
            final XmlElement propertyElement = xmlHelper.getXmlFactory().newElement( XML_ELEMENT_PROPERTY );
            propertyElement.setAttribute( XML_ATTRIBUTE_KEY, propertyName.getKey() );
            propertyElement.addText( value );

            if ( null == xmlHelper.xpathForConfigProperties() )
            {
                final XmlElement configProperties = xmlHelper.getXmlFactory().newElement( XML_ELEMENT_PROPERTIES );
                configProperties.setAttribute( XML_ATTRIBUTE_TYPE, XML_ATTRIBUTE_VALUE_CONFIG );
                document.getRootElement().addContent( configProperties );
            }

            final XmlElement propertiesElement = xmlHelper.xpathForConfigProperties();
            propertyElement.setAttribute( XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( Instant.now() ) );
            propertiesElement.setAttribute( XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( Instant.now() ) );
            propertiesElement.addContent( propertyElement );
        }
        finally
        {
            domModifyLock.writeLock().unlock();
        }
    }

    public void lock( )
    {
        locked = true;
    }

    public Map<String, String> readLocaleBundleMap( final String bundleName, final String keyName )
    {
        domModifyLock.readLock().lock();
        try
        {
            final XmlElement localeBundleElement = xmlHelper.xpathForLocaleBundleSetting( bundleName, keyName );

            if ( localeBundleElement != null )
            {
                final Map<String, String> bundleMap = new LinkedHashMap<>();
                for ( final XmlElement valueElement : localeBundleElement.getChildren( "value" ) )
                {
                    final String localeStrValue = valueElement.getAttributeValue( "locale" );
                    bundleMap.put( localeStrValue == null ? "" : localeStrValue, valueElement.getText() );
                }
                if ( !bundleMap.isEmpty() )
                {
                    return bundleMap;
                }
            }
        }
        finally
        {
            domModifyLock.readLock().unlock();
        }
        return Collections.emptyMap();
    }

    public Map<String, Object> toOutputMap( final Locale locale )
    {
        final List<Map<String, String>> settingData = new ArrayList<>();
        for ( final StoredConfigurationImpl.SettingValueRecord settingValueRecord : this.modifiedSettings() )
        {
            final Map<String, String> recordMap = new HashMap<>();
            recordMap.put( "label", settingValueRecord.getSetting().getLabel( locale ) );
            if ( settingValueRecord.getProfile() != null )
            {
                recordMap.put( "profile", settingValueRecord.getProfile() );
            }
            if ( settingValueRecord.getStoredValue() != null )
            {
                recordMap.put( "value", settingValueRecord.getStoredValue().toDebugString( locale ) );
            }
            final ValueMetaData settingMetaData = readSettingMetadata( settingValueRecord.getSetting(), settingValueRecord.getProfile() );
            if ( settingMetaData != null )
            {
                if ( settingMetaData.getModifyDate() != null )
                {
                    recordMap.put( "modifyTime", JavaHelper.toIsoDate( settingMetaData.getModifyDate() ) );
                }
                if ( settingMetaData.getUserIdentity() != null )
                {
                    recordMap.put( "modifyUser", settingMetaData.getUserIdentity().toDisplayString() );
                }
            }
            settingData.add( recordMap );
        }

        final HashMap<String, Object> outputObj = new HashMap<>();
        outputObj.put( "settings", settingData );
        outputObj.put( "template", this.getTemplateSet().toString() );

        return Collections.unmodifiableMap( outputObj );
    }

    public void resetLocaleBundleMap( final String bundleName, final String keyName )
    {
        preModifyActions();
        domModifyLock.writeLock().lock();
        try
        {
            final XmlElement oldBundleElements = xmlHelper.xpathForLocaleBundleSetting( bundleName, keyName );
            if ( oldBundleElements != null )
            {
                oldBundleElements.detach();
            }
        }
        finally
        {
            domModifyLock.writeLock().unlock();
        }
    }

    public void resetSetting( final PwmSetting setting, final String profileID, final UserIdentity userIdentity )
    {
        changeLog.updateChangeLog( setting, profileID, defaultValue( setting, this.getTemplateSet() ) );
        domModifyLock.writeLock().lock();
        try
        {
            preModifyActions();
            final XmlElement settingElement = createOrGetSettingElement( setting, profileID );
            settingElement.removeContent();
            settingElement.addContent( xmlHelper.getXmlFactory().newElement( XML_ELEMENT_DEFAULT ) );
            updateMetaData( settingElement, userIdentity );
        }
        finally
        {
            domModifyLock.writeLock().unlock();
        }
    }

    public boolean isDefaultValue( final PwmSetting setting )
    {
        return isDefaultValue( setting, null );
    }

    public boolean isDefaultValue( final PwmSetting setting, final String profileID )
    {
        domModifyLock.readLock().lock();
        try
        {
            final StoredValue currentValue = readSetting( setting, profileID );
            if ( setting.getSyntax() == PwmSettingSyntax.PASSWORD )
            {
                return currentValue == null || currentValue.toNativeObject() == null;
            }
            final StoredValue defaultValue = defaultValue( setting, this.getTemplateSet() );
            final String currentJsonValue = JsonUtil.serialize( ( Serializable ) currentValue.toNativeObject() );
            final String defaultJsonValue = JsonUtil.serialize( ( Serializable ) defaultValue.toNativeObject() );
            return defaultJsonValue.equalsIgnoreCase( currentJsonValue );
        }
        finally
        {
            domModifyLock.readLock().unlock();
        }
    }

    private static StoredValue defaultValue( final PwmSetting pwmSetting, final PwmSettingTemplateSet template )
    {
        return pwmSetting.getDefaultValue( template );
    }

    public PwmSettingTemplateSet getTemplateSet( )
    {
        final Set<PwmSettingTemplate> templates = new HashSet<>();
        templates.add( readTemplateValue( PwmSetting.TEMPLATE_LDAP ) );
        templates.add( readTemplateValue( PwmSetting.TEMPLATE_STORAGE ) );
        templates.add( readTemplateValue( PwmSetting.DB_VENDOR_TEMPLATE ) );
        return new PwmSettingTemplateSet( templates );
    }

    private PwmSettingTemplate readTemplateValue( final PwmSetting pwmSetting )
    {
        final XmlElement settingElement = xmlHelper.xpathForSetting( pwmSetting, null );
        if ( settingElement != null )
        {
            try
            {
                final String strValue = ( String ) ValueFactory.fromXmlValues( pwmSetting, settingElement, null ).toNativeObject();
                return JavaHelper.readEnumFromString( PwmSettingTemplate.class, null, strValue );
            }
            catch ( IllegalStateException e )
            {
                LOGGER.error( "error reading template", e );
            }
        }
        return null;
    }

    public void setTemplate( final PwmSettingTemplate template )
    {
        writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, template.toString() );
    }

    public String toString( final PwmSetting setting, final String profileID )
    {
        final StoredValue storedValue = readSetting( setting, profileID );
        return setting.getKey() + "=" + storedValue.toDebugString( null );
    }

    public Map<String, String> getModifiedSettingDebugValues( final Locale locale, final boolean prettyPrint )
    {
        final Map<String, String> returnObj = new TreeMap<>();
        for ( final SettingValueRecord record : this.modifiedSettings() )
        {
            final String label = record.getSetting().toMenuLocationDebug( record.getProfile(), locale );
            final String value = record.getStoredValue().toDebugString( locale );
            returnObj.put( label, value );
        }
        return returnObj;
    }

    public List<SettingValueRecord> modifiedSettings( )
    {
        final List<SettingValueRecord> returnObj = new ArrayList<>();
        domModifyLock.readLock().lock();
        try
        {
            for ( final PwmSetting setting : PwmSetting.values() )
            {
                if ( setting.getSyntax() != PwmSettingSyntax.PROFILE && !setting.getCategory().hasProfiles() )
                {
                    if ( !isDefaultValue( setting, null ) )
                    {
                        final StoredValue value = readSetting( setting );
                        if ( value != null )
                        {
                            returnObj.add( new SettingValueRecord( setting, null, value ) );
                        }
                    }
                }
            }

            for ( final PwmSettingCategory category : PwmSettingCategory.values() )
            {
                if ( category.hasProfiles() )
                {
                    for ( final String profileID : this.profilesForSetting( category.getProfileSetting() ) )
                    {
                        for ( final PwmSetting profileSetting : category.getSettings() )
                        {
                            if ( !isDefaultValue( profileSetting, profileID ) )
                            {
                                final StoredValue value = readSetting( profileSetting, profileID );
                                if ( value != null )
                                {
                                    returnObj.add( new SettingValueRecord( profileSetting, profileID, value ) );

                                }
                            }
                        }
                    }
                }
            }

            return returnObj;
        }
        finally
        {
            domModifyLock.readLock().unlock();
        }
    }

    public Serializable toJsonDebugObject( )
    {
        domModifyLock.readLock().lock();
        try
        {
            final TreeMap<String, Object> outputObject = new TreeMap<>();

            for ( final PwmSetting setting : PwmSetting.values() )
            {
                if ( setting.getSyntax() != PwmSettingSyntax.PROFILE && !setting.getCategory().hasProfiles() )
                {
                    if ( !isDefaultValue( setting, null ) )
                    {
                        final StoredValue value = readSetting( setting );
                        outputObject.put( setting.getKey(), value.toDebugJsonObject( null ) );
                    }
                }
            }

            for ( final PwmSettingCategory category : PwmSettingCategory.values() )
            {
                if ( category.hasProfiles() )
                {
                    final TreeMap<String, Object> profiles = new TreeMap<>();
                    for ( final String profileID : this.profilesForSetting( category.getProfileSetting() ) )
                    {
                        final TreeMap<String, Object> profileObject = new TreeMap<>();
                        for ( final PwmSetting profileSetting : category.getSettings() )
                        {
                            if ( !isDefaultValue( profileSetting, profileID ) )
                            {
                                final StoredValue value = readSetting( profileSetting, profileID );
                                profileObject.put( profileSetting.getKey(), value.toDebugJsonObject( null ) );
                            }
                        }
                        profiles.put( profileID, profileObject );
                    }
                    outputObject.put( category.getProfileSetting().getKey(), profiles );
                }
            }

            return outputObject;
        }
        finally
        {
            domModifyLock.readLock().unlock();
        }
    }

    public void toXml( final OutputStream outputStream )
            throws IOException, PwmUnrecoverableException
    {
        ConfigurationCleaner.updateMandatoryElements( this, document );
        XmlFactory.getFactory().outputDocument( document, outputStream );
    }

    public List<String> profilesForSetting( final PwmSetting pwmSetting )
    {
        return StoredConfigurationUtil.profilesForSetting( pwmSetting, this );
    }

    public List<String> validateValues( )
    {
        final long startTime = System.currentTimeMillis();
        final List<String> errorStrings = new ArrayList<>();

        for ( final PwmSetting loopSetting : PwmSetting.values() )
        {

            if ( loopSetting.getCategory().hasProfiles() )
            {
                for ( final String profile : profilesForSetting( loopSetting ) )
                {
                    final StoredValue loopValue = readSetting( loopSetting, profile );

                    try
                    {
                        final List<String> errors = loopValue.validateValue( loopSetting );
                        for ( final String loopError : errors )
                        {
                            errorStrings.add( loopSetting.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE ) + " - " + loopError );
                        }
                    }
                    catch ( Exception e )
                    {
                        LOGGER.error( "unexpected error during validate value for " + loopSetting.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE ) + ", error: " + e.getMessage(), e );
                    }
                }
            }
            else
            {
                final StoredValue loopValue = readSetting( loopSetting );

                try
                {
                    final List<String> errors = loopValue.validateValue( loopSetting );
                    for ( final String loopError : errors )
                    {
                        errorStrings.add( loopSetting.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) + " - " + loopError );
                    }
                }
                catch ( Exception e )
                {
                    LOGGER.error( "unexpected error during validate value for " + loopSetting.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) + ", error: " + e.getMessage(), e );
                }
            }
        }

        LOGGER.trace( () -> "StoredConfiguration validator completed in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return errorStrings;
    }

    public ValueMetaData readSettingMetadata( final PwmSetting setting, final String profileID )
    {
        final XmlElement settingElement = xmlHelper.xpathForSetting( setting, profileID );

        if ( settingElement == null )
        {
            return null;
        }

        Instant modifyDate = null;
        try
        {
            if ( settingElement.getAttributeValue( XML_ATTRIBUTE_MODIFY_TIME ) != null )
            {
                modifyDate = JavaHelper.parseIsoToInstant(
                        settingElement.getAttributeValue( XML_ATTRIBUTE_MODIFY_TIME ) );
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "can't read modifyDate for setting " + setting.getKey() + ", profile " + profileID + ", error: " + e.getMessage() );
        }

        UserIdentity userIdentity = null;
        try
        {
            if ( settingElement.getAttributeValue( XML_ATTRIBUTE_MODIFY_USER ) != null )
            {
                userIdentity = UserIdentity.fromDelimitedKey(
                        settingElement.getAttributeValue( XML_ATTRIBUTE_MODIFY_USER ) );
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "can't read userIdentity for setting " + setting.getKey() + ", profile " + profileID + ", error: " + e.getMessage() );
        }

        return new ValueMetaData( modifyDate, userIdentity );
    }

    public List<ConfigRecordID> search( final String searchTerm, final Locale locale )
    {
        if ( searchTerm == null )
        {
            return Collections.emptyList();
        }

        final SortedSet<ConfigRecordID> matches = new TreeSet<>(
                allSettingConfigRecordIDs()
                        .parallelStream()
                        .filter( s -> matchSetting( s, searchTerm, locale ) )
                        .collect( Collectors.toList() )
        );

        return new ArrayList<>( matches );
    }

    private boolean matchSetting(
            final ConfigRecordID configRecordID,
            final String searchTerm,
            final Locale locale
    )
    {

        final PwmSetting pwmSetting = ( PwmSetting ) configRecordID.getRecordID();
        final StoredValue value = readSetting( pwmSetting, configRecordID.getProfileID() );

        return StringUtil.whitespaceSplit( searchTerm )
                .parallelStream()
                .allMatch( s -> matchSetting( pwmSetting, value, s, locale ) );
    }

    public boolean matchSetting( final PwmSetting setting, final StoredValue value, final String searchTerm, final Locale locale )
    {
        if ( setting.isHidden() || setting.getCategory().isHidden() )
        {
            return false;
        }

        if ( searchTerm == null || searchTerm.isEmpty() )
        {
            return false;
        }

        final String lowerSearchTerm = searchTerm.toLowerCase();

        {
            final String key = setting.getKey();
            if ( key.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        {
            final String label = setting.getLabel( locale );
            if ( label.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        {
            final String descr = setting.getDescription( locale );
            if ( descr.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        {
            final String menuLocationString = setting.toMenuLocationDebug( null, locale );
            if ( menuLocationString.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }

        if ( setting.isConfidential() )
        {
            return false;
        }
        {
            final String valueDebug = value.toDebugString( locale );
            if ( valueDebug != null && valueDebug.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        if ( PwmSettingSyntax.SELECT == setting.getSyntax()
                || PwmSettingSyntax.OPTIONLIST == setting.getSyntax()
                || PwmSettingSyntax.VERIFICATION_METHOD == setting.getSyntax()
        )
        {
            for ( final String key : setting.getOptions().keySet() )
            {
                if ( key.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
                final String optionValue = setting.getOptions().get( key );
                if ( optionValue != null && optionValue.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
            }
        }
        return false;
    }


    public StoredValue readSetting( final PwmSetting setting )
    {
        return readSetting( setting, null );
    }

    public StoredValue readSetting( final PwmSetting setting, final String profileID )
    {
        if ( profileID == null && setting.getCategory().hasProfiles() )
        {
            final IllegalArgumentException e = new IllegalArgumentException( "reading of setting " + setting.getKey() + " requires a non-null profileID" );
            LOGGER.error( "error", e );
            throw e;
        }
        if ( profileID != null && !setting.getCategory().hasProfiles() )
        {
            throw new IllegalStateException( "cannot read setting key " + setting.getKey() + " with non-null profileID" );
        }
        domModifyLock.readLock().lock();
        try
        {
            final XmlElement settingElement = xmlHelper.xpathForSetting( setting, profileID );

            if ( settingElement == null )
            {
                return defaultValue( setting, getTemplateSet() );
            }

            if ( settingElement.getChild( XML_ELEMENT_DEFAULT ) != null )
            {
                return defaultValue( setting, getTemplateSet() );
            }

            try
            {
                return ValueFactory.fromXmlValues( setting, settingElement, getKey() );
            }
            catch ( PwmException e )
            {
                final String errorMsg = "unexpected error reading setting '" + setting.getKey() + "' profile '" + profileID + "', error: " + e.getMessage();
                throw new IllegalStateException( errorMsg );
            }
        }
        finally
        {
            domModifyLock.readLock().unlock();
        }
    }

    public void writeLocaleBundleMap( final String bundleName, final String keyName, final Map<String, String> localeMap )
    {
        ResourceBundle theBundle = null;
        for ( final PwmLocaleBundle bundle : PwmLocaleBundle.values() )
        {
            if ( bundle.getTheClass().getName().equals( bundleName ) )
            {
                theBundle = ResourceBundle.getBundle( bundleName );
            }
        }

        if ( theBundle == null )
        {
            LOGGER.info( () -> "ignoring unknown locale bundle for bundle=" + bundleName + ", key=" + keyName );
            return;
        }

        if ( theBundle.getString( keyName ) == null )
        {
            LOGGER.info( () -> "ignoring unknown key for bundle=" + bundleName + ", key=" + keyName );
            return;
        }


        resetLocaleBundleMap( bundleName, keyName );
        if ( localeMap == null || localeMap.isEmpty() )
        {
            LOGGER.info( () -> "cleared locale bundle map for bundle=" + bundleName + ", key=" + keyName );
            return;
        }

        preModifyActions();
        changeLog.updateChangeLog( bundleName, keyName, localeMap );
        try
        {
            domModifyLock.writeLock().lock();
            final XmlElement localeBundleElement = xmlHelper.getXmlFactory().newElement( "localeBundle" );
            localeBundleElement.setAttribute( "bundle", bundleName );
            localeBundleElement.setAttribute( "key", keyName );
            for ( final Map.Entry<String, String> entry : localeMap.entrySet() )
            {
                final String locale = entry.getKey();
                final String value = entry.getValue();
                final XmlElement valueElement = xmlHelper.getXmlFactory().newElement( "value" );
                if ( locale != null && locale.length() > 0 )
                {
                    valueElement.setAttribute( "locale", locale );
                }
                valueElement.addText( value );
                localeBundleElement.addContent( valueElement );
            }
            localeBundleElement.setAttribute( XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( Instant.now() ) );
            document.getRootElement().addContent( localeBundleElement );
        }
        finally
        {
            domModifyLock.writeLock().unlock();
        }
    }


    public void copyProfileID( final PwmSettingCategory category, final String sourceID, final String destinationID, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {

        if ( !category.hasProfiles() )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category " + category + ", category does not have profiles" );
        }
        final List<String> existingProfiles = this.profilesForSetting( category.getProfileSetting() );
        if ( !existingProfiles.contains( sourceID ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category, source profileID '" + sourceID + "' does not exist" );
        }
        if ( existingProfiles.contains( destinationID ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category, destination profileID '" + destinationID + "' already exists" );
        }

        {
            final Collection<PwmSettingCategory> interestedCategories = PwmSettingCategory.associatedProfileCategories( category );
            for ( final PwmSettingCategory interestedCategory : interestedCategories )
            {
                for ( final PwmSetting pwmSetting : interestedCategory.getSettings() )
                {
                    if ( !isDefaultValue( pwmSetting, sourceID ) )
                    {
                        final StoredValue value = readSetting( pwmSetting, sourceID );
                        writeSetting( pwmSetting, destinationID, value, userIdentity );
                    }
                }
            }
        }

        final List<String> newProfileIDList = new ArrayList<>();
        newProfileIDList.addAll( existingProfiles );
        newProfileIDList.add( destinationID );
        writeSetting( category.getProfileSetting(), new StringArrayValue( newProfileIDList ), userIdentity );
    }


    public void writeSetting(
            final PwmSetting setting,
            final StoredValue value,
            final UserIdentity userIdentity
    ) throws PwmUnrecoverableException
    {
        writeSetting( setting, null, value, userIdentity );
    }

    public void writeSetting(
            final PwmSetting setting,
            final String profileID,
            final StoredValue value,
            final UserIdentity userIdentity
    ) throws PwmUnrecoverableException
    {
        if ( profileID == null && setting.getCategory().hasProfiles() )
        {
            throw new IllegalArgumentException( "writing of setting " + setting.getKey() + " requires a non-null profileID" );
        }
        if ( profileID != null && !setting.getCategory().hasProfiles() )
        {
            throw new IllegalArgumentException( "cannot specify profile for non-profile setting" );
        }

        preModifyActions();
        changeLog.updateChangeLog( setting, profileID, value );
        domModifyLock.writeLock().lock();
        try
        {
            final XmlElement settingElement = createOrGetSettingElement( setting, profileID );
            settingElement.removeContent();
            settingElement.setAttribute( XML_ATTRIBUTE_SYNTAX, setting.getSyntax().toString() );
            settingElement.setAttribute( XML_ATTRIBUTE_SYNTAX_VERSION, Integer.toString( value.currentSyntaxVersion() ) );

            if ( setting_writeLabels )
            {
                {
                    final XmlElement existingLabel = settingElement.getChild( "label" );
                    if ( existingLabel != null )
                    {
                        existingLabel.detach();
                    }
                }

                {
                    final XmlElement newLabelElement = xmlHelper.getXmlFactory().newElement( "label" );
                    newLabelElement.addText( setting.getLabel( PwmConstants.DEFAULT_LOCALE ) );
                    settingElement.addContent( newLabelElement );
                }
            }

            if ( setting.getSyntax() == PwmSettingSyntax.PASSWORD )
            {
                final List<String> commentLines = Arrays.asList(
                        "Note: This value is encrypted and can not be edited directly.",
                        "Please use the Configuration Manager GUI to modify this value."
                );
                settingElement.setComment( commentLines );

                final List<XmlElement> valueElements = ( ( PasswordValue ) value ).toXmlValues( "value", getKey() );
                settingElement.addContent( valueElements );
            }
            else if ( setting.getSyntax() == PwmSettingSyntax.PRIVATE_KEY )
            {
                final List<XmlElement> valueElements = ( ( PrivateKeyValue ) value ).toXmlValues( "value", getKey() );
                settingElement.addContent( valueElements );
            }
            else if ( setting.getSyntax() == PwmSettingSyntax.NAMED_SECRET )
            {
                final List<XmlElement> valueElements = ( ( NamedSecretValue ) value ).toXmlValues( "value", getKey() );
                settingElement.addContent( valueElements );
            }
            else
            {
                settingElement.addContent( value.toXmlValues( "value", getKey() ) );
            }


            updateMetaData( settingElement, userIdentity );
        }
        finally
        {
            domModifyLock.writeLock().unlock();
        }
    }

    public String settingChecksum( )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final List<SettingValueRecord> modifiedSettings = modifiedSettings();
        final StringBuilder sb = new StringBuilder();
        sb.append( "PwmSettingsChecksum" );
        for ( final SettingValueRecord settingValueRecord : modifiedSettings )
        {
            final StoredValue storedValue = settingValueRecord.getStoredValue();
            sb.append( storedValue.valueHash() );
        }


        final String result = SecureEngine.hash( sb.toString(), PwmConstants.SETTING_CHECKSUM_HASH_METHOD );
        LOGGER.trace( () -> "computed setting checksum in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return result;
    }


    private void preModifyActions( )
    {
        if ( locked )
        {
            throw new UnsupportedOperationException( "StoredConfiguration is locked and cannot be modified" );
        }
        document.getRootElement().setAttribute( XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( Instant.now() ) );
    }

    public void setPassword( final String password )
            throws PwmOperationalException
    {
        if ( password == null || password.isEmpty() )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] { "can not set blank password" } ) );
        }
        final String trimmedPassword = password.trim();
        if ( trimmedPassword.length() < 1 )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] { "can not set blank password" } ) );
        }


        final String passwordHash = BCrypt.hashPassword( password );
        this.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, passwordHash );
    }

    public boolean verifyPassword( final String password, final Configuration configuration )
    {
        if ( !hasPassword() )
        {
            return false;
        }
        final String passwordHash = this.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        return BCrypt.testAnswer( password, passwordHash, configuration );
    }

    public boolean hasPassword( )
    {
        final String passwordHash = this.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        return passwordHash != null && passwordHash.length() > 0;
    }

    class XmlHelper
    {
        private XmlFactory getXmlFactory()
        {
            return XmlFactory.getFactory();
        }

        private XmlElement xpathForLocaleBundleSetting( final String bundleName, final String keyName )
        {
            final String xpathString = "//localeBundle[@bundle=\"" + bundleName + "\"][@key=\"" + keyName + "\"]";
            return document.evaluateXpathToElement( xpathString );
        }

        XmlElement xpathForSetting( final PwmSetting setting, final String profileID )
        {
            final String xpathString;
            if ( profileID == null || profileID.length() < 1 )
            {
                xpathString = "//setting[@key=\"" + setting.getKey() + "\"][(not (@profile)) or @profile=\"\"]";
            }
            else
            {
                xpathString = "//setting[@key=\"" + setting.getKey() + "\"][@profile=\"" + profileID + "\"]";
            }

            return document.evaluateXpathToElement( xpathString );
        }

        private XmlElement xpathForAppProperty( final AppProperty appProperty )
        {
            final String xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_APP + "\"]/"
                    + XML_ELEMENT_PROPERTY + "[@" + XML_ATTRIBUTE_KEY + "=\"" + appProperty.getKey() + "\"]";
            return document.evaluateXpathToElement( xpathString );
        }

        List<XmlElement> xpathForAppProperties( )
        {
            final String xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_APP + "\"]";
            return document.evaluateXpathToElements( xpathString );
        }

        private XmlElement xpathForConfigProperty( final ConfigurationProperty configProperty )
        {
            final String xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_CONFIG + "\"]/"
                    + XML_ELEMENT_PROPERTY + "[@" + XML_ATTRIBUTE_KEY + "=\"" + configProperty.getKey() + "\"]";
            return document.evaluateXpathToElement( xpathString );
        }

        private XmlElement xpathForConfigProperties( )
        {
            final String xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_CONFIG + "\"]";
            return document.evaluateXpathToElement( xpathString );
        }
    }


    public static class ConfigRecordID implements Serializable, Comparable
    {
        private RecordType recordType;
        private Object recordID;
        private String profileID;

        public enum RecordType
        {
            SETTING,
            LOCALE_BUNDLE,
        }

        public ConfigRecordID(
                final RecordType recordType,
                final Object recordID,
                final String profileID
        )
        {
            this.recordType = recordType;
            this.recordID = recordID;
            this.profileID = profileID;
        }


        public RecordType getRecordType( )
        {
            return recordType;
        }

        public Object getRecordID( )
        {
            return recordID;
        }

        public String getProfileID( )
        {
            return profileID;
        }

        @Override
        public boolean equals( final Object o )
        {
            return o != null
                    && o instanceof ConfigRecordID
                    && toString().equals( o.toString() );

        }

        @Override
        public int hashCode( )
        {
            return toString().hashCode();
        }

        @Override
        public String toString( )
        {
            return this.getRecordType().toString()
                    + "-"
                    + ( this.getProfileID() == null ? "" : this.getProfileID() )
                    + "-"
                    + this.getRecordID();
        }

        @Override
        public int compareTo( final Object o )
        {
            return toString().compareTo( o.toString() );
        }
    }


    public String changeLogAsDebugString( final Locale locale, final boolean asHtml )
    {
        return changeLog.changeLogAsDebugString( locale, asHtml );
    }

    private PwmSecurityKey cachedKey;

    public PwmSecurityKey getKey( ) throws PwmUnrecoverableException
    {
        if ( cachedKey == null )
        {
            cachedKey = new PwmSecurityKey( createTime() + "StoredConfiguration" );
        }
        return cachedKey;
    }

    public boolean isModified( )
    {
        return changeLog.isModified();
    }

    private class ChangeLog
    {
        /* values contain the _original_ toJson version of the value. */
        private Map<ConfigRecordID, String> changeLog = new LinkedHashMap<>();

        public boolean isModified( )
        {
            return !changeLog.isEmpty();
        }

        public String changeLogAsDebugString( final Locale locale, final boolean asHtml )
        {
            final Map<String, String> outputMap = new TreeMap<>();
            final String SEPARATOR = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );

            for ( final ConfigRecordID configRecordID : changeLog.keySet() )
            {
                switch ( configRecordID.recordType )
                {
                    case SETTING:
                    {
                        final StoredValue currentValue = readSetting( ( PwmSetting ) configRecordID.recordID, configRecordID.profileID );
                        final PwmSetting pwmSetting = ( PwmSetting ) configRecordID.recordID;
                        final String keyName = pwmSetting.toMenuLocationDebug( configRecordID.getProfileID(), locale );
                        final String debugValue = currentValue.toDebugString( locale );
                        outputMap.put( keyName, debugValue );
                    }
                    break;

                    case LOCALE_BUNDLE:
                    {
                        final String key = ( String ) configRecordID.recordID;
                        final String bundleName = key.split( "!" )[ 0 ];
                        final String keys = key.split( "!" )[ 1 ];
                        final Map<String, String> currentValue = readLocaleBundleMap( bundleName, keys );
                        final String debugValue = JsonUtil.serializeMap( currentValue, JsonUtil.Flag.PrettyPrint );
                        outputMap.put( "LocaleBundle" + SEPARATOR + bundleName + " " + keys, debugValue );
                    }
                    break;

                    default:
                        // continue processing
                        break;
                }
            }
            final StringBuilder output = new StringBuilder();
            if ( outputMap.isEmpty() )
            {
                output.append( "No setting changes." );
            }
            else
            {
                for ( final Map.Entry<String, String> entry : outputMap.entrySet() )
                {
                    final String keyName = entry.getKey();
                    final String value = entry.getValue();
                    if ( asHtml )
                    {
                        output.append( "<div class=\"changeLogKey\">" );
                        output.append( keyName );
                        output.append( "</div><div class=\"changeLogValue\">" );
                        output.append( StringUtil.escapeHtml( value ) );
                        output.append( "</div>" );
                    }
                    else
                    {
                        output.append( keyName );
                        output.append( "\n" );
                        output.append( " Value: " );
                        output.append( value );
                        output.append( "\n" );
                    }
                }
            }
            return output.toString();
        }

        public void updateChangeLog( final String bundleName, final String keyName, final Map<String, String> localeValueMap )
        {
            final String key = bundleName + "!" + keyName;
            final Map<String, String> currentValue = readLocaleBundleMap( bundleName, keyName );
            final String currentJsonValue = JsonUtil.serializeMap( currentValue );
            final String newJsonValue = JsonUtil.serializeMap( localeValueMap );
            final ConfigRecordID configRecordID = new ConfigRecordID( ConfigRecordID.RecordType.LOCALE_BUNDLE, key, null );
            updateChangeLog( configRecordID, currentJsonValue, newJsonValue );
        }

        public void updateChangeLog( final PwmSetting setting, final String profileID, final StoredValue newValue )
        {
            final StoredValue currentValue = readSetting( setting, profileID );
            final String currentJsonValue = JsonUtil.serialize( currentValue );
            final String newJsonValue = JsonUtil.serialize( newValue );
            final ConfigRecordID configRecordID = new ConfigRecordID( ConfigRecordID.RecordType.SETTING, setting, profileID );
            updateChangeLog( configRecordID, currentJsonValue, newJsonValue );
        }

        public void updateChangeLog( final ConfigRecordID configRecordID, final String currentValueString, final String newValueString )
        {
            if ( changeLog.containsKey( configRecordID ) )
            {
                final String currentRecord = changeLog.get( configRecordID );

                if ( currentRecord == null && newValueString == null )
                {
                    changeLog.remove( configRecordID );
                }
                else if ( currentRecord != null && currentRecord.equals( newValueString ) )
                {
                    changeLog.remove( configRecordID );
                }
            }
            else
            {
                changeLog.put( configRecordID, currentValueString );
            }
        }
    }

    public static void validateXmlSchema( final String xmlDocument )
            throws PwmUnrecoverableException
    {
        return;
                /*
        try {
            final InputStream xsdInputStream = PwmSetting.class.getClassLoader().getResourceAsStream("password/pwm/config/StoredConfiguration.xsd");
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final Schema schema = factory.newSchema(new StreamSource(xsdInputStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlDocument)));
        } catch (Exception e) {
            final String errorMsg = "error while validating setting file schema definition: " + e.getMessage();
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg));
        }
        */
    }

    private void updateMetaData( final XmlElement settingElement, final UserIdentity userIdentity )
    {
        final XmlElement settingsElement = document.getRootElement().getChild( XML_ELEMENT_SETTINGS );
        settingElement.setAttribute( XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( Instant.now() ) );
        settingsElement.setAttribute( XML_ATTRIBUTE_MODIFY_TIME, JavaHelper.toIsoDate( Instant.now() ) );
        settingElement.removeAttribute( XML_ATTRIBUTE_MODIFY_USER );
        settingsElement.removeAttribute( XML_ATTRIBUTE_MODIFY_USER );
        if ( userIdentity != null )
        {
            settingElement.setAttribute( XML_ATTRIBUTE_MODIFY_USER, userIdentity.toDelimitedKey() );
            settingsElement.setAttribute( XML_ATTRIBUTE_MODIFY_USER, userIdentity.toDelimitedKey() );
        }
    }

    private XmlElement createOrGetSettingElement(
            final PwmSetting setting,
            final String profileID
    )
    {
        final XmlElement existingSettingElement = xmlHelper.xpathForSetting( setting, profileID );
        if ( existingSettingElement != null )
        {
            return existingSettingElement;
        }

        final XmlElement settingElement = xmlHelper.getXmlFactory().newElement( XML_ELEMENT_SETTING );
        settingElement.setAttribute( XML_ATTRIBUTE_KEY, setting.getKey() );
        settingElement.setAttribute( XML_ATTRIBUTE_SYNTAX, setting.getSyntax().toString() );
        if ( profileID != null && profileID.length() > 0 )
        {
            settingElement.setAttribute( XML_ATTRIBUTE_PROFILE, profileID );
        }

        XmlElement settingsElement = document.getRootElement().getChild( XML_ELEMENT_SETTINGS );
        if ( settingsElement == null )
        {
            settingsElement = xmlHelper.getXmlFactory().newElement( XML_ELEMENT_SETTINGS );
            document.getRootElement().addContent( settingsElement );
        }
        settingsElement.addContent( settingElement );

        return settingElement;
    }

    public static class SettingValueRecord implements Serializable
    {
        private PwmSetting setting;
        private String profile;
        private StoredValue storedValue;

        public SettingValueRecord(
                final PwmSetting setting,
                final String profile,
                final StoredValue storedValue
        )
        {
            this.setting = setting;
            this.profile = profile;
            this.storedValue = storedValue;
        }

        public PwmSetting getSetting( )
        {
            return setting;
        }

        public String getProfile( )
        {
            return profile;
        }

        public StoredValue getStoredValue( )
        {
            return storedValue;
        }
    }

    class StoredValueIterator implements Iterator<StoredConfigurationImpl.SettingValueRecord>
    {

        private Queue<SettingValueRecord> settingQueue = new LinkedList<>();

        StoredValueIterator( final boolean includeDefaults )
        {
            for ( final PwmSetting setting : PwmSetting.values() )
            {
                if ( setting.getSyntax() != PwmSettingSyntax.PROFILE && !setting.getCategory().hasProfiles() )
                {
                    if ( includeDefaults || !isDefaultValue( setting ) )
                    {
                        final SettingValueRecord settingValueRecord = new SettingValueRecord( setting, null, null );
                        settingQueue.add( settingValueRecord );
                    }
                }
            }

            for ( final PwmSettingCategory category : PwmSettingCategory.values() )
            {
                if ( category.hasProfiles() )
                {
                    for ( final String profileID : profilesForSetting( category.getProfileSetting() ) )
                    {
                        for ( final PwmSetting setting : category.getSettings() )
                        {
                            if ( includeDefaults || !isDefaultValue( setting, profileID ) )
                            {
                                final SettingValueRecord settingValueRecord = new SettingValueRecord( setting, profileID, null );
                                settingQueue.add( settingValueRecord );
                            }
                        }
                    }
                }
            }
        }


        @Override
        public boolean hasNext( )
        {
            return !settingQueue.isEmpty();
        }

        @Override
        public SettingValueRecord next( )
        {
            final StoredConfigurationImpl.SettingValueRecord settingValueRecord = settingQueue.poll();
            return new SettingValueRecord(
                    settingValueRecord.getSetting(),
                    settingValueRecord.getProfile(),
                    readSetting( settingValueRecord.getSetting(), settingValueRecord.getProfile() )
            );
        }

        @Override
        public void remove( )
        {

        }
    }

    private String createTime( )
    {
        final XmlElement rootElement = document.getRootElement();
        final String createTimeString = rootElement.getAttributeValue( XML_ATTRIBUTE_CREATE_TIME );
        if ( createTimeString == null || createTimeString.isEmpty() )
        {
            throw new IllegalStateException( "missing createTime timestamp" );
        }
        return createTimeString;
    }

    @Override
    public Instant modifyTime( )
    {
        final XmlElement rootElement = document.getRootElement();
        final String modifyTimeString = rootElement.getAttributeValue( XML_ATTRIBUTE_MODIFY_TIME );
        if ( modifyTimeString != null )
        {
            try
            {
                return JavaHelper.parseIsoToInstant( modifyTimeString );
            }
            catch ( Exception e )
            {
                LOGGER.error( "error parsing root last modified timestamp: " + e.getMessage() );
            }
        }
        return null;
    }

    public void initNewRandomSecurityKey( )
            throws PwmUnrecoverableException
    {
        if ( !isDefaultValue( PwmSetting.PWM_SECURITY_KEY ) )
        {
            return;
        }

        writeSetting(
                PwmSetting.PWM_SECURITY_KEY,
                new PasswordValue( new PasswordData( PwmRandom.getInstance().alphaNumericString( 1024 ) ) ),
                null
        );

        LOGGER.debug( () -> "initialized new random security key" );
    }


    @Override
    public boolean isLocked( )
    {
        return locked;
    }

    private List<ConfigRecordID> allSettingConfigRecordIDs( )
    {
        final LinkedHashSet<ConfigRecordID> loopResults = new LinkedHashSet<>();
        for ( final PwmSetting loopSetting : PwmSetting.values() )
        {
            if ( loopSetting.getCategory().hasProfiles() )
            {
                for ( final String profile : profilesForSetting( loopSetting ) )
                {
                    loopResults.add( new ConfigRecordID( ConfigRecordID.RecordType.SETTING, loopSetting, profile ) );
                }
            }
            else
            {
                loopResults.add( new ConfigRecordID( ConfigRecordID.RecordType.SETTING, loopSetting, null ) );
            }
        }
        return new ArrayList<>( loopResults );
    }

    XmlHelper getXmlHelper()
    {
        return xmlHelper;
    }
}
