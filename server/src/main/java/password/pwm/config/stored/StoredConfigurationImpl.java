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

import org.jdom2.Attribute;
import org.jdom2.CDATA;
import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Text;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
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
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.value.FormValue;
import password.pwm.config.value.NamedSecretValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.LocaleHelper;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.java.XmlUtil;
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
    private static final String XML_FORMAT_VERSION = "4";

    private Document document = new Document( new Element( XML_ELEMENT_ROOT ) );
    private ChangeLog changeLog = new ChangeLog();

    private boolean locked;
    private final boolean setting_writeLabels = true;
    private final ReentrantReadWriteLock domModifyLock = new ReentrantReadWriteLock();

    public static StoredConfigurationImpl newStoredConfiguration( ) throws PwmUnrecoverableException
    {
        return new StoredConfigurationImpl();
    }

    public static StoredConfigurationImpl copy( final StoredConfigurationImpl input ) throws PwmUnrecoverableException
    {
        final StoredConfigurationImpl copy = new StoredConfigurationImpl();
        copy.document = input.document.clone();
        return copy;
    }

    public static StoredConfigurationImpl fromXml( final InputStream xmlData )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        //validateXmlSchema(xmlData);

        final Document inputDocument = XmlUtil.parseXml( xmlData );
        final StoredConfigurationImpl newConfiguration = StoredConfigurationImpl.newStoredConfiguration();

        try
        {
            newConfiguration.document = inputDocument;
            newConfiguration.createTime(); // verify create time;
            ConfigurationCleaner.cleanup( newConfiguration );
        }
        catch ( Exception e )
        {
            final String errorMsg = "error reading configuration file format, error=" + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] { errorMsg } );
            throw new PwmUnrecoverableException( errorInfo );
        }

        checkIfXmlRequiresUpdate( newConfiguration );
        LOGGER.debug( "successfully loaded configuration (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
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
                    final XPathExpression xp = XPathBuilder.xpathForSetting( settingValueRecord.getSetting(), settingValueRecord.getProfile() );
                    final Element settingElement = ( Element ) xp.evaluateFirst( document );
                    if ( settingElement != null )
                    {
                        settingElement.addContent( new Comment( comment ) );
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
        ConfigurationCleaner.cleanup( this );
        final String createTime = JavaHelper.toIsoDate( Instant.now() );
        document.getRootElement().setAttribute( XML_ATTRIBUTE_CREATE_TIME, createTime );
    }


    @Override
    public String readConfigProperty( final ConfigurationProperty propertyName )
    {
        final XPathExpression xp = XPathBuilder.xpathForConfigProperty( propertyName );
        final Element propertyElement = ( Element ) xp.evaluateFirst( document );
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

            final XPathExpression xp = XPathBuilder.xpathForConfigProperty( propertyName );
            final List<Element> propertyElements = xp.evaluate( document );
            for ( final Element propertyElement : propertyElements )
            {
                propertyElement.detach();
            }

            final Element propertyElement = new Element( XML_ELEMENT_PROPERTY );
            propertyElement.setAttribute( new Attribute( XML_ATTRIBUTE_KEY, propertyName.getKey() ) );
            propertyElement.setContent( new Text( value ) );

            if ( null == XPathBuilder.xpathForConfigProperties().evaluateFirst( document ) )
            {
                final Element configProperties = new Element( XML_ELEMENT_PROPERTIES );
                configProperties.setAttribute( new Attribute( XML_ATTRIBUTE_TYPE, XML_ATTRIBUTE_VALUE_CONFIG ) );
                document.getRootElement().addContent( configProperties );
            }

            final XPathExpression xp2 = XPathBuilder.xpathForConfigProperties();
            final Element propertiesElement = ( Element ) xp2.evaluateFirst( document );
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
            final XPathExpression xp = XPathBuilder.xpathForLocaleBundleSetting( bundleName, keyName );
            final Element localeBundleElement = ( Element ) xp.evaluateFirst( document );
            if ( localeBundleElement != null )
            {
                final Map<String, String> bundleMap = new LinkedHashMap<>();
                for ( final Element valueElement : localeBundleElement.getChildren( "value" ) )
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
            final XPathExpression xp = XPathBuilder.xpathForLocaleBundleSetting( bundleName, keyName );
            final List<Element> oldBundleElements = xp.evaluate( document );
            if ( oldBundleElements != null )
            {
                for ( final Element element : oldBundleElements )
                {
                    element.detach();
                }
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
            final Element settingElement = createOrGetSettingElement( document, setting, profileID );
            settingElement.removeContent();
            settingElement.addContent( new Element( XML_ELEMENT_DEFAULT ) );
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
        templates.add( readTemplateValue( document, PwmSetting.TEMPLATE_LDAP ) );
        templates.add( readTemplateValue( document, PwmSetting.TEMPLATE_STORAGE ) );
        templates.add( readTemplateValue( document, PwmSetting.DB_VENDOR_TEMPLATE ) );
        return new PwmSettingTemplateSet( templates );
    }

    private static PwmSettingTemplate readTemplateValue( final Document document, final PwmSetting pwmSetting )
    {
        final XPathExpression xp = XPathBuilder.xpathForSetting( pwmSetting, null );
        final Element settingElement = ( Element ) xp.evaluateFirst( document );
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
        ConfigurationCleaner.updateMandatoryElements( document );
        XmlUtil.outputDocument( document, outputStream );
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
        final XPathExpression xp = XPathBuilder.xpathForSetting( setting, profileID );
        final Element settingElement = ( Element ) xp.evaluateFirst( document );

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
            final XPathExpression xp = XPathBuilder.xpathForSetting( setting, profileID );
            final Element settingElement = ( Element ) xp.evaluateFirst( document );

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
            LOGGER.info( "ignoring unknown locale bundle for bundle=" + bundleName + ", key=" + keyName );
            return;
        }

        if ( theBundle.getString( keyName ) == null )
        {
            LOGGER.info( "ignoring unknown key for bundle=" + bundleName + ", key=" + keyName );
            return;
        }


        resetLocaleBundleMap( bundleName, keyName );
        if ( localeMap == null || localeMap.isEmpty() )
        {
            LOGGER.info( "cleared locale bundle map for bundle=" + bundleName + ", key=" + keyName );
            return;
        }

        preModifyActions();
        changeLog.updateChangeLog( bundleName, keyName, localeMap );
        try
        {
            domModifyLock.writeLock().lock();
            final Element localeBundleElement = new Element( "localeBundle" );
            localeBundleElement.setAttribute( "bundle", bundleName );
            localeBundleElement.setAttribute( "key", keyName );
            for ( final Map.Entry<String, String> entry : localeMap.entrySet() )
            {
                final String locale = entry.getKey();
                final String value = entry.getValue();
                final Element valueElement = new Element( "value" );
                if ( locale != null && locale.length() > 0 )
                {
                    valueElement.setAttribute( "locale", locale );
                }
                valueElement.setContent( new CDATA( value ) );
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
            throw new IllegalArgumentException( "reading of setting " + setting.getKey() + " requires a non-null profileID" );
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
            final Element settingElement = createOrGetSettingElement( document, setting, profileID );
            settingElement.removeContent();
            settingElement.setAttribute( XML_ATTRIBUTE_SYNTAX, setting.getSyntax().toString() );
            settingElement.setAttribute( XML_ATTRIBUTE_SYNTAX_VERSION, Integer.toString( value.currentSyntaxVersion() ) );

            if ( setting_writeLabels )
            {
                final Element labelElement = new Element( "label" );
                labelElement.addContent( setting.getLabel( PwmConstants.DEFAULT_LOCALE ) );
                settingElement.addContent( labelElement );
            }

            if ( setting.getSyntax() == PwmSettingSyntax.PASSWORD )
            {
                final List<Element> valueElements = ( ( PasswordValue ) value ).toXmlValues( "value", getKey() );
                settingElement.addContent( new Comment( "Note: This value is encrypted and can not be edited directly." ) );
                settingElement.addContent( new Comment( "Please use the Configuration Manager GUI to modify this value." ) );
                settingElement.addContent( valueElements );
            }
            else if ( setting.getSyntax() == PwmSettingSyntax.PRIVATE_KEY )
            {
                final List<Element> valueElements = ( ( PrivateKeyValue ) value ).toXmlValues( "value", getKey() );
                settingElement.addContent( valueElements );
            }
            else if ( setting.getSyntax() == PwmSettingSyntax.NAMED_SECRET )
            {
                final List<Element> valueElements = ( ( NamedSecretValue ) value ).toXmlValues( "value", getKey() );
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

    private abstract static class XPathBuilder
    {
        private static XPathExpression xpathForLocaleBundleSetting( final String bundleName, final String keyName )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//localeBundle[@bundle=\"" + bundleName + "\"][@key=\"" + keyName + "\"]";
            return xpfac.compile( xpathString );
        }

        private static XPathExpression xpathForSetting( final PwmSetting setting, final String profileID )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            if ( profileID == null || profileID.length() < 1 )
            {
                xpathString = "//setting[@key=\"" + setting.getKey() + "\"][(not (@profile)) or @profile=\"\"]";
            }
            else
            {
                xpathString = "//setting[@key=\"" + setting.getKey() + "\"][@profile=\"" + profileID + "\"]";
            }

            return xpfac.compile( xpathString );
        }

        private static XPathExpression xpathForAppProperty( final AppProperty appProperty )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_APP + "\"]/"
                    + XML_ELEMENT_PROPERTY + "[@" + XML_ATTRIBUTE_KEY + "=\"" + appProperty.getKey() + "\"]";
            return xpfac.compile( xpathString );
        }

        private static XPathExpression xpathForAppProperties( )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_APP + "\"]";
            return xpfac.compile( xpathString );
        }

        private static XPathExpression xpathForConfigProperty( final ConfigurationProperty configProperty )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_CONFIG + "\"]/"
                    + XML_ELEMENT_PROPERTY + "[@" + XML_ATTRIBUTE_KEY + "=\"" + configProperty.getKey() + "\"]";
            return xpfac.compile( xpathString );
        }

        private static XPathExpression xpathForConfigProperties( )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final String xpathString;
            xpathString = "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_CONFIG + "\"]";
            return xpfac.compile( xpathString );
        }
    }


    private static class ConfigurationCleaner
    {
        private static void cleanup( final StoredConfigurationImpl configuration ) throws PwmUnrecoverableException
        {
            updateProperitiesWithoutType( configuration );
            updateMandatoryElements( configuration.document );
            profilizeNonProfiledSettings( configuration );
            stripOrphanedProfileSettings( configuration );
            migrateAppProperties( configuration );
            updateDeprecatedSettings( configuration );
            migrateDeprecatedProperties( configuration );
        }


        private static void updateMandatoryElements( final Document document )
        {
            final Element rootElement = document.getRootElement();

            {
                final XPathExpression commentXPath = XPathFactory.instance().compile( "//comment()[1]" );
                final Comment existingComment = ( Comment ) commentXPath.evaluateFirst( rootElement );
                if ( existingComment != null )
                {
                    existingComment.detach();
                }
                final Comment comment = new Comment( generateCommentText() );
                rootElement.addContent( 0, comment );
            }

            rootElement.setAttribute( "pwmVersion", PwmConstants.BUILD_VERSION );
            rootElement.setAttribute( "pwmBuild", PwmConstants.BUILD_NUMBER );
            rootElement.setAttribute( "xmlVersion", XML_FORMAT_VERSION );

            { // migrate old properties

                // read correct (new) //properties[@type="config"]
                final XPathExpression configPropertiesXpath = XPathFactory.instance().compile(
                        "//" + XML_ELEMENT_PROPERTIES + "[@" + XML_ATTRIBUTE_TYPE + "=\"" + XML_ATTRIBUTE_VALUE_CONFIG + "\"]" );
                final Element configPropertiesElement = ( Element ) configPropertiesXpath.evaluateFirst( rootElement );

                // read list of old //properties[not (@type)]/property
                final XPathExpression nonAttributedProperty = XPathFactory.instance().compile(
                        "//" + XML_ELEMENT_PROPERTIES + "[not (@" + XML_ATTRIBUTE_TYPE + ")]/" + XML_ELEMENT_PROPERTY );
                final List<Element> nonAttributedProperties = nonAttributedProperty.evaluate( rootElement );

                if ( configPropertiesElement != null && nonAttributedProperties != null )
                {
                    for ( final Element element : nonAttributedProperties )
                    {
                        element.detach();
                        configPropertiesElement.addContent( element );
                    }
                }

                // remove old //properties[not (@type] element
                final XPathExpression oldPropertiesXpath = XPathFactory.instance().compile(
                        "//" + XML_ELEMENT_PROPERTIES + "[not (@" + XML_ATTRIBUTE_TYPE + ")]" );
                final List<Element> oldPropertiesElements = oldPropertiesXpath.evaluate( rootElement );
                if ( oldPropertiesElements != null )
                {
                    for ( final Element element : oldPropertiesElements )
                    {
                        element.detach();
                    }
                }
            }
        }

        private static String generateCommentText( )
        {
            final StringBuilder commentText = new StringBuilder();
            commentText.append( "\t\t" ).append( " " ).append( "\n" );
            commentText.append( "\t\t" ).append( "This configuration file has been auto-generated by the " ).append( PwmConstants.PWM_APP_NAME ).append( " password self service application." ).append( "\n" );
            commentText.append( "\t\t" ).append( "" ).append( "\n" );
            commentText.append( "\t\t" ).append( "WARNING: This configuration file contains sensitive security information, please handle with care!" ).append( "\n" );
            commentText.append( "\t\t" ).append( "" ).append( "\n" );
            commentText.append( "\t\t" ).append( "WARNING: If a server is currently running using this configuration file, it will be restarted" ).append( "\n" );
            commentText.append( "\t\t" ).append( "         and the configuration updated immediately when it is modified." ).append( "\n" );
            commentText.append( "\t\t" ).append( "" ).append( "\n" );
            commentText.append( "\t\t" ).append( "NOTICE: This file is encoded as UTF-8.  Do not save or edit this file with an editor that does not" ).append( "\n" );
            commentText.append( "\t\t" ).append( "        support UTF-8 encoding." ).append( "\n" );
            commentText.append( "\t\t" ).append( "" ).append( "\n" );
            commentText.append( "\t\t" ).append( "If unable to edit using the application ConfigurationEditor web UI, the following options are available." ).append( "\n" );
            commentText.append( "\t\t" ).append( "   or 1. Edit this file directly by hand." ).append( "\n" );
            commentText.append( "\t\t" ).append( "   or 2. Remove restrictions of the configuration by setting the property 'configIsEditable' to 'true' in this file.  This will " ).append( "\n" );
            commentText.append( "\t\t" ).append( "         allow access to the ConfigurationEditor web UI without having to authenticate to an LDAP server first." ).append( "\n" );
            commentText.append( "\t\t" ).append( "   or 3. Remove restrictions of the configuration by using the the command line utility. " ).append( "\n" );
            commentText.append( "\t\t" ).append( "" ).append( "\n" );
            return commentText.toString();
        }


        private static void profilizeNonProfiledSettings( final StoredConfigurationImpl storedConfiguration ) throws PwmUnrecoverableException
        {
            final String NEW_PROFILE_NAME = "default";
            final Document document = storedConfiguration.document;
            for ( final PwmSetting setting : PwmSetting.values() )
            {
                if ( setting.getCategory().hasProfiles() )
                {

                    final XPathExpression xp = XPathBuilder.xpathForSetting( setting, null );
                    final Element settingElement = ( Element ) xp.evaluateFirst( document );
                    if ( settingElement != null )
                    {
                        settingElement.detach();

                        final PwmSetting profileSetting = setting.getCategory().getProfileSetting();
                        final List<String> profileStringDefinitions = new ArrayList<>();
                        {
                            final StringArrayValue profileDefinitions = ( StringArrayValue ) storedConfiguration.readSetting( profileSetting );
                            if ( profileDefinitions != null )
                            {
                                if ( profileDefinitions.toNativeObject() != null )
                                {
                                    profileStringDefinitions.addAll( profileDefinitions.toNativeObject() );
                                }
                            }
                        }

                        if (profileStringDefinitions.isEmpty()) {
                            profileStringDefinitions.add( NEW_PROFILE_NAME );
                        }

                        final UserIdentity userIdentity = settingElement.getAttribute( XML_ATTRIBUTE_MODIFY_USER ) != null
                                ? UserIdentity.fromDelimitedKey( settingElement.getAttribute(  XML_ATTRIBUTE_MODIFY_USER ).getValue() )
                                : null;

                        for ( final String destProfile : profileStringDefinitions )
                        {
                            LOGGER.info( "moving setting " + setting.getKey() + " without profile attribute to profile \"" + destProfile + "\"." );
                            {
                                storedConfiguration.writeSetting( profileSetting, new StringArrayValue( profileStringDefinitions ), userIdentity );
                            }
                        }
                    }
                }
            }
        }

        private static void migrateDeprecatedProperties( final StoredConfigurationImpl storedConfiguration ) throws PwmUnrecoverableException
        {
            final Document document = storedConfiguration.document;
            final XPathFactory xpfac = XPathFactory.instance();
            {
                final String xpathString = "//property[@key=\"" + ConfigurationProperty.LDAP_TEMPLATE.getKey() + "\"]";
                final XPathExpression xp = xpfac.compile( xpathString );
                final List<Element> propertyElement = ( List<Element> ) xp.evaluate( document );
                if ( propertyElement != null && !propertyElement.isEmpty() )
                {
                    final String value = propertyElement.get( 0 ).getText();
                    storedConfiguration.writeSetting( PwmSetting.TEMPLATE_LDAP, new StringValue( value ), null );
                    propertyElement.get( 0 ).detach();
                }
            }
            {
                final String xpathString = "//property[@key=\"" + ConfigurationProperty.NOTES.getKey() + "\"]";
                final XPathExpression xp = xpfac.compile( xpathString );
                final List<Element> propertyElement = ( List<Element> ) xp.evaluate( document );
                if ( propertyElement != null && !propertyElement.isEmpty() )
                {
                    final String value = propertyElement.get( 0 ).getText();
                    storedConfiguration.writeSetting( PwmSetting.NOTES, new StringValue( value ), null );
                    propertyElement.get( 0 ).detach();
                }
            }
        }

        private static void updateProperitiesWithoutType( final StoredConfigurationImpl storedConfiguration )
        {
            final Document document = storedConfiguration.document;
            final String xpathString = "//properties[not(@type)]";
            final XPathFactory xpfac = XPathFactory.instance();
            final XPathExpression xp = xpfac.compile( xpathString );
            final List<Element> propertiesElements = ( List<Element> ) xp.evaluate( document );
            for ( final Element propertiesElement : propertiesElements )
            {
                propertiesElement.setAttribute( XML_ATTRIBUTE_TYPE, XML_ATTRIBUTE_VALUE_CONFIG );
            }
        }

        private static void stripOrphanedProfileSettings( final StoredConfigurationImpl storedConfiguration )
        {
            final Document document = storedConfiguration.document;
            final XPathFactory xpfac = XPathFactory.instance();
            for ( final PwmSetting setting : PwmSetting.values() )
            {
                if ( setting.getCategory().hasProfiles() )
                {
                    final List<String> validProfiles = storedConfiguration.profilesForSetting( setting );
                    final String xpathString = "//setting[@key=\"" + setting.getKey() + "\"]";
                    final XPathExpression xp = xpfac.compile( xpathString );
                    final List<Element> settingElements = ( List<Element> ) xp.evaluate( document );
                    for ( final Element settingElement : settingElements )
                    {
                        final String profileID = settingElement.getAttributeValue( XML_ATTRIBUTE_PROFILE );
                        if ( profileID != null )
                        {
                            if ( !validProfiles.contains( profileID ) )
                            {
                                LOGGER.info( "removing setting " + setting.getKey() + " with profile \"" + profileID + "\", profile is not a valid profile" );
                                settingElement.detach();
                            }
                        }
                    }
                }
            }
        }

        private static void migrateAppProperties( final StoredConfigurationImpl storedConfiguration ) throws PwmUnrecoverableException
        {
            final Document document = storedConfiguration.document;
            final XPathExpression xPathExpression = XPathBuilder.xpathForAppProperties();
            final List<Element> appPropertiesElements = ( List<Element> ) xPathExpression.evaluate( document );
            for ( final Element element : appPropertiesElements )
            {
                final List<Element> properties = element.getChildren();
                for ( final Element property : properties )
                {
                    final String key = property.getAttributeValue( "key" );
                    final String value = property.getText();
                    if ( key != null && !key.isEmpty() && value != null && !value.isEmpty() )
                    {
                        LOGGER.info( "migrating app-property config element '" + key + "' to setting " + PwmSetting.APP_PROPERTY_OVERRIDES.getKey() );
                        final String newValue = key + "=" + value;
                        List<String> existingValues = ( List<String> ) storedConfiguration.readSetting( PwmSetting.APP_PROPERTY_OVERRIDES ).toNativeObject();
                        if ( existingValues == null )
                        {
                            existingValues = new ArrayList<>();
                        }
                        existingValues = new ArrayList<>( existingValues );
                        existingValues.add( newValue );
                        storedConfiguration.writeSetting( PwmSetting.APP_PROPERTY_OVERRIDES, new StringArrayValue( existingValues ), null );
                    }
                }
                element.detach();
            }
        }

        private static void updateDeprecatedSettings( final StoredConfigurationImpl storedConfiguration ) throws PwmUnrecoverableException
        {
            final UserIdentity actor = new UserIdentity( "UpgradeProcessor", null );
            for ( final String profileID : storedConfiguration.profilesForSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY ) )
            {
                if ( !storedConfiguration.isDefaultValue( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ) )
                {
                    final boolean ad2003Enabled = ( boolean ) storedConfiguration.readSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ).toNativeObject();
                    final StoredValue value;
                    if ( ad2003Enabled )
                    {
                        value = new StringValue( ADPolicyComplexity.AD2003.toString() );
                    }
                    else
                    {
                        value = new StringValue( ADPolicyComplexity.NONE.toString() );
                    }
                    LOGGER.warn( "converting deprecated non-default setting " + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY.getKey() + "/" + profileID
                            + " to replacement setting " + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL + ", value=" + value.toNativeObject().toString() );
                    storedConfiguration.writeSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL, profileID, value, actor );
                    storedConfiguration.resetSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID, actor );
                }
            }

            for ( final String profileID : storedConfiguration.profilesForSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME ) )
            {
                if ( !storedConfiguration.isDefaultValue( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID ) )
                {
                    final boolean enforceEnabled = ( boolean ) storedConfiguration.readSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID ).toNativeObject();
                    final StoredValue value = enforceEnabled
                            ? new StringValue( "NONE" )
                            : new StringValue( "ALLOW" );
                    final ValueMetaData existingData = storedConfiguration.readSettingMetadata( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID );
                    LOGGER.warn( "converting deprecated non-default setting "
                            + PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME.toMenuLocationDebug(profileID,PwmConstants.DEFAULT_LOCALE) + "/" + profileID
                            + " to replacement setting " + PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE )
                            + ", value=" + value.toNativeObject().toString() );
                    final UserIdentity newActor = existingData != null && existingData.getUserIdentity() != null
                            ? existingData.getUserIdentity()
                            : actor;
                    storedConfiguration.writeSetting( PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS, profileID, value, newActor );
                    storedConfiguration.resetSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID, actor );
                }
            }

            {
                if ( !storedConfiguration.isDefaultValue( PwmSetting.PEOPLE_SEARCH_SEARCH_ATTRIBUTES ) )
                {
                    final List<String> oldValues = ( List<String> ) storedConfiguration.readSetting( PwmSetting.PEOPLE_SEARCH_SEARCH_ATTRIBUTES ).toNativeObject();

                    final List<FormConfiguration> newValues = new ArrayList<>();
                    for ( final String attribute : oldValues )
                    {
                        final FormConfiguration formConfiguration = FormConfiguration.builder()
                                .name( attribute )
                                .labels( Collections.singletonMap( "", attribute ) )
                                .build();
                        newValues.add( formConfiguration );
                    }

                    final ValueMetaData existingData = storedConfiguration.readSettingMetadata( PwmSetting.PEOPLE_SEARCH_SEARCH_ATTRIBUTES, null );
                    final UserIdentity newActor = existingData != null && existingData.getUserIdentity() != null
                            ? existingData.getUserIdentity()
                            : actor;

                    storedConfiguration.writeSetting( PwmSetting.PEOPLE_SEARCH_SEARCH_FORM, null, new FormValue( newValues ), newActor );
                    storedConfiguration.resetSetting( PwmSetting.PEOPLE_SEARCH_SEARCH_ATTRIBUTES, null, actor );
                }
            }
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

    private static void updateMetaData( final Element settingElement, final UserIdentity userIdentity )
    {
        final Element settingsElement = settingElement.getDocument().getRootElement().getChild( XML_ELEMENT_SETTINGS );
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

    private static Element createOrGetSettingElement(
            final Document document,
            final PwmSetting setting,
            final String profileID
    )
    {
        final XPathExpression xp = XPathBuilder.xpathForSetting( setting, profileID );
        final Element existingSettingElement = ( Element ) xp.evaluateFirst( document );
        if ( existingSettingElement != null )
        {
            return existingSettingElement;
        }

        final Element settingElement = new Element( XML_ELEMENT_SETTING );
        settingElement.setAttribute( XML_ATTRIBUTE_KEY, setting.getKey() );
        settingElement.setAttribute( XML_ATTRIBUTE_SYNTAX, setting.getSyntax().toString() );
        if ( profileID != null && profileID.length() > 0 )
        {
            settingElement.setAttribute( XML_ATTRIBUTE_PROFILE, profileID );
        }

        Element settingsElement = document.getRootElement().getChild( XML_ELEMENT_SETTINGS );
        if ( settingsElement == null )
        {
            settingsElement = new Element( XML_ELEMENT_SETTINGS );
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
        final Element rootElement = document.getRootElement();
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
        final Element rootElement = document.getRootElement();
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

        LOGGER.debug( "initialized new random security key" );
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

}
