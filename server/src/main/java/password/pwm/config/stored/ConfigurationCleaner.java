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
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.value.OptionListValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class ConfigurationCleaner
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationCleaner.class );
    private static final String NEW_PROFILE_NAME = "default";

    private final StoredConfigurationImpl storedConfiguration;
    private final XmlDocument document;

    ConfigurationCleaner(
            final StoredConfigurationImpl storedConfiguration, final XmlDocument document
    )
    {
        this.storedConfiguration = storedConfiguration;
        this.document = document;
    }


    static void cleanup(
            final StoredConfigurationImpl storedConfiguration, final XmlDocument document
    )
            throws PwmUnrecoverableException
    {
        new ConfigurationCleaner( storedConfiguration, document ).cleanupImpl();
    }

    static void updateMandatoryElements(
            final StoredConfigurationImpl storedConfiguration,
            final XmlDocument document
    )
    {
        new ConfigurationCleaner( storedConfiguration, document ).updateMandatoryElementsImpl();
    }


    private void cleanupImpl(
    )
            throws PwmUnrecoverableException
    {
        updateProperitiesWithoutType( );
        updateMandatoryElementsImpl();
        profilizeNonProfiledSettings( );
        stripOrphanedProfileSettings( );
        migrateAppProperties( );
        updateDeprecatedSettings( );
        migrateDeprecatedProperties( );
    }


    private void updateMandatoryElementsImpl( )
    {
        final XmlElement rootElement = document.getRootElement();
        rootElement.setComment( Collections.singletonList( generateCommentText() ) );

        rootElement.setAttribute( "pwmVersion", PwmConstants.BUILD_VERSION );
        rootElement.setAttribute( "pwmBuild", PwmConstants.BUILD_NUMBER );
        rootElement.setAttribute( "xmlVersion", StoredConfigurationImpl.XML_FORMAT_VERSION );

        // migrate old properties
        {

            // read correct (new) //properties[@type="config"]
            final String configPropertiesXpath = "//" + StoredConfigurationImpl.XML_ELEMENT_PROPERTIES
                    + "[@" + StoredConfigurationImpl.XML_ATTRIBUTE_TYPE + "=\"" + StoredConfigurationImpl.XML_ATTRIBUTE_VALUE_CONFIG + "\"]";
            final XmlElement configPropertiesElement = document.evaluateXpathToElement( configPropertiesXpath );

            // read list of old //properties[not (@type)]/property
            final String nonAttributedPropertyXpath = "//" + StoredConfigurationImpl.XML_ELEMENT_PROPERTIES
                    + "[not (@" + StoredConfigurationImpl.XML_ATTRIBUTE_TYPE + ")]/" + StoredConfigurationImpl.XML_ELEMENT_PROPERTY;
            final List<XmlElement> nonAttributedProperties = document.evaluateXpathToElements( nonAttributedPropertyXpath );

            if ( configPropertiesElement != null && nonAttributedProperties != null )
            {
                for ( final XmlElement element : nonAttributedProperties )
                {
                    element.detach();
                    configPropertiesElement.addContent( element );
                }
            }

            // remove old //properties[not (@type] element
            final String oldPropertiesXpath = "//" + StoredConfigurationImpl.XML_ELEMENT_PROPERTIES + "[not (@" + StoredConfigurationImpl.XML_ATTRIBUTE_TYPE + ")]";
            final List<XmlElement> oldPropertiesElements = document.evaluateXpathToElements( oldPropertiesXpath );
            if ( oldPropertiesElements != null )
            {
                for ( final XmlElement element : oldPropertiesElements )
                {
                    element.detach();
                }
            }
        }
    }

    private String generateCommentText( )
    {
        final StringBuilder commentText = new StringBuilder();
        commentText.append( "\t\t" ).append( " " ).append( "\n" );
        commentText.append( "\t\t" ).append( "This configuration file has been auto-generated by the " ).append( PwmConstants.PWM_APP_NAME )
                .append( " password self service application." ).append( "\n" );
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
        commentText.append( "\t\t" ).append( "   or 2. Remove restrictions of the configuration by setting the property 'configIsEditable' to 'true' in this file.  This will " )
                .append( "\n" );
        commentText.append( "\t\t" ).append( "         allow access to the ConfigurationEditor web UI without having to authenticate to an LDAP server first." ).append( "\n" );
        commentText.append( "\t\t" ).append( "   or 3. Remove restrictions of the configuration by using the the command line utility. " ).append( "\n" );
        commentText.append( "\t\t" ).append( "" ).append( "\n" );
        return commentText.toString();
    }


    private void profilizeNonProfiledSettings()
            throws PwmUnrecoverableException
    {
        for ( final PwmSetting setting : PwmSetting.values() )
        {
            if ( setting.getCategory().hasProfiles() )
            {

                final XmlElement settingElement = storedConfiguration.getXmlHelper().xpathForSetting( setting, null );
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

                    if ( profileStringDefinitions.isEmpty() )
                    {
                        profileStringDefinitions.add( NEW_PROFILE_NAME );
                    }

                    final UserIdentity userIdentity = settingElement.getAttributeValue( StoredConfigurationImpl.XML_ATTRIBUTE_MODIFY_USER ) != null
                            ? UserIdentity.fromDelimitedKey( settingElement.getAttributeValue(  StoredConfigurationImpl.XML_ATTRIBUTE_MODIFY_USER ) )
                            : null;

                    for ( final String destProfile : profileStringDefinitions )
                    {
                        LOGGER.info( () -> "moving setting " + setting.getKey() + " without profile attribute to profile \"" + destProfile + "\"." );
                        {
                            storedConfiguration.writeSetting( profileSetting, new StringArrayValue( profileStringDefinitions ), userIdentity );
                        }
                    }
                }
            }
        }
    }

    private void migrateDeprecatedProperties(
    )
            throws PwmUnrecoverableException
    {
        {
            final String xpathString = "//property[@key=\"" + ConfigurationProperty.LDAP_TEMPLATE.getKey() + "\"]";
            final List<XmlElement> propertyElement = document.evaluateXpathToElements( xpathString );
            if ( propertyElement != null && !propertyElement.isEmpty() )
            {
                final String value = propertyElement.get( 0 ).getText();
                storedConfiguration.writeSetting( PwmSetting.TEMPLATE_LDAP, new StringValue( value ), null );
                propertyElement.get( 0 ).detach();
            }
        }
        {
            final String xpathString = "//property[@key=\"" + ConfigurationProperty.NOTES.getKey() + "\"]";
            final List<XmlElement> propertyElement = document.evaluateXpathToElements( xpathString );
            if ( propertyElement != null && !propertyElement.isEmpty() )
            {
                final String value = propertyElement.get( 0 ).getText();
                storedConfiguration.writeSetting( PwmSetting.NOTES, new StringValue( value ), null );
                propertyElement.get( 0 ).detach();
            }
        }
    }

    private void updateProperitiesWithoutType()
    {
        final String xpathString = "//properties[not(@type)]";
        final List<XmlElement> propertiesElements = document.evaluateXpathToElements( xpathString );
        for ( final XmlElement propertiesElement : propertiesElements )
        {
            propertiesElement.setAttribute( StoredConfigurationImpl.XML_ATTRIBUTE_TYPE, StoredConfigurationImpl.XML_ATTRIBUTE_VALUE_CONFIG );
        }
    }

    private void stripOrphanedProfileSettings()
    {
        for ( final PwmSetting setting : PwmSetting.values() )
        {
            if ( setting.getCategory().hasProfiles() )
            {
                final List<String> validProfiles = storedConfiguration.profilesForSetting( setting );
                final String xpathString = "//setting[@key=\"" + setting.getKey() + "\"]";
                final List<XmlElement> settingElements =  document.evaluateXpathToElements( xpathString );
                for ( final XmlElement settingElement : settingElements )
                {
                    final String profileID = settingElement.getAttributeValue( StoredConfigurationImpl.XML_ATTRIBUTE_PROFILE );
                    if ( profileID != null )
                    {
                        if ( !validProfiles.contains( profileID ) )
                        {
                            LOGGER.info( () -> "removing setting " + setting.getKey() + " with profile \"" + profileID + "\", profile is not a valid profile" );
                            settingElement.detach();
                        }
                    }
                }
            }
        }
    }

    private void migrateAppProperties(
    )
            throws PwmUnrecoverableException
    {
        final List<XmlElement> appPropertiesElements = storedConfiguration.getXmlHelper().xpathForAppProperties();
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

    private void updateDeprecatedSettings( ) throws PwmUnrecoverableException
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
                        + PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) + "/" + profileID
                        + " to replacement setting " + PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE )
                        + ", value=" + value.toNativeObject().toString() );
                final UserIdentity newActor = existingData != null && existingData.getUserIdentity() != null
                        ? existingData.getUserIdentity()
                        : actor;
                storedConfiguration.writeSetting( PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS, profileID, value, newActor );
                storedConfiguration.resetSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID, actor );
            }
        }

        if ( !storedConfiguration.isDefaultValue( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES ) )
        {
            LOGGER.warn( "converting deprecated non-default setting "
                    + PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE )
                    + " to replacement setting " + PwmSetting.WEBSERVICES_PUBLIC_ENABLE.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) );
            final Set<String> existingValues = (Set<String>) storedConfiguration.readSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE ).toNativeObject();
            final Set<String> newValues = new LinkedHashSet<>( existingValues );
            newValues.add( WebServiceUsage.Health.name() );
            newValues.add( WebServiceUsage.Statistics.name() );
            storedConfiguration.writeSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, null, new OptionListValue( newValues ), actor );
            storedConfiguration.resetSetting( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES, null, actor );
        }
    }
}
