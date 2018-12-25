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

import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.value.FormValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ConfigurationCleaner
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationCleaner.class );
    private static final String NEW_PROFILE_NAME = "default";

    static void cleanup( final StoredConfigurationImpl configuration, final Document document )
            throws PwmUnrecoverableException
    {
        updateProperitiesWithoutType( configuration, document );
        updateMandatoryElements( document );
        profilizeNonProfiledSettings( configuration, document );
        stripOrphanedProfileSettings( configuration, document );
        migrateAppProperties( configuration, document );
        updateDeprecatedSettings( configuration );
        migrateDeprecatedProperties( configuration, document );
    }


    static void updateMandatoryElements( final Document document )
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
        rootElement.setAttribute( "xmlVersion", StoredConfigurationImpl.XML_FORMAT_VERSION );

        // migrate old properties
        {

            // read correct (new) //properties[@type="config"]
            final XPathExpression configPropertiesXpath = XPathFactory.instance().compile(
                    "//" + StoredConfiguration.XML_ELEMENT_PROPERTIES + "[@" + StoredConfiguration.XML_ATTRIBUTE_TYPE + "=\""
                            + StoredConfiguration.XML_ATTRIBUTE_VALUE_CONFIG + "\"]" );
            final Element configPropertiesElement = ( Element ) configPropertiesXpath.evaluateFirst( rootElement );

            // read list of old //properties[not (@type)]/property
            final XPathExpression nonAttributedProperty = XPathFactory.instance().compile(
                    "//" + StoredConfiguration.XML_ELEMENT_PROPERTIES + "[not (@" + StoredConfiguration.XML_ATTRIBUTE_TYPE + ")]/"
                            + StoredConfiguration.XML_ELEMENT_PROPERTY );
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
                    "//" + StoredConfiguration.XML_ELEMENT_PROPERTIES + "[not (@" + StoredConfiguration.XML_ATTRIBUTE_TYPE + ")]" );
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


    private static void profilizeNonProfiledSettings( final StoredConfigurationImpl storedConfiguration, final Document document ) throws PwmUnrecoverableException
    {
        for ( final PwmSetting setting : PwmSetting.values() )
        {
            if ( setting.getCategory().hasProfiles() )
            {

                final XPathExpression xp = StoredConfigurationImpl.XPathBuilder.xpathForSetting( setting, null );
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

                    if ( profileStringDefinitions.isEmpty() )
                    {
                        profileStringDefinitions.add( NEW_PROFILE_NAME );
                    }

                    final UserIdentity userIdentity = settingElement.getAttribute( StoredConfiguration.XML_ATTRIBUTE_MODIFY_USER ) != null
                            ? UserIdentity.fromDelimitedKey( settingElement.getAttribute(  StoredConfiguration.XML_ATTRIBUTE_MODIFY_USER ).getValue() )
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

    private static void migrateDeprecatedProperties(
            final StoredConfigurationImpl storedConfiguration,
            final Document document
    )
            throws PwmUnrecoverableException
    {
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

    private static void updateProperitiesWithoutType( final StoredConfigurationImpl storedConfiguration, final Document document )
    {
        final String xpathString = "//properties[not(@type)]";
        final XPathFactory xpfac = XPathFactory.instance();
        final XPathExpression xp = xpfac.compile( xpathString );
        final List<Element> propertiesElements = ( List<Element> ) xp.evaluate( document );
        for ( final Element propertiesElement : propertiesElements )
        {
            propertiesElement.setAttribute( StoredConfiguration.XML_ATTRIBUTE_TYPE, StoredConfiguration.XML_ATTRIBUTE_VALUE_CONFIG );
        }
    }

    private static void stripOrphanedProfileSettings( final StoredConfigurationImpl storedConfiguration, final Document document )
    {
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
                    final String profileID = settingElement.getAttributeValue( StoredConfiguration.XML_ATTRIBUTE_PROFILE );
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

    private static void migrateAppProperties( final StoredConfigurationImpl storedConfiguration, final Document document ) throws PwmUnrecoverableException
    {
        final XPathExpression xPathExpression = StoredConfigurationImpl.XPathBuilder.xpathForAppProperties();
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

        {
            if ( !storedConfiguration.isDefaultValue( PwmSetting.REPORTING_SEARCH_FILTER ) )
            {
                final String oldValue = ( String ) storedConfiguration.readSetting( PwmSetting.REPORTING_SEARCH_FILTER ).toNativeObject();

                final List<UserPermission> newValues = new ArrayList<>();
                final UserPermission newPermission = new UserPermission( UserPermission.Type.ldapQuery, PwmConstants.PROFILE_ID_ALL, oldValue, null );
                newValues.add( newPermission );

                final ValueMetaData existingData = storedConfiguration.readSettingMetadata( PwmSetting.REPORTING_SEARCH_FILTER, null );
                final UserIdentity newActor = existingData != null && existingData.getUserIdentity() != null
                        ? existingData.getUserIdentity()
                        : actor;

                storedConfiguration.writeSetting( PwmSetting.REPORTING_USER_MATCH, null, new UserPermissionValue( newValues ), newActor );
                storedConfiguration.resetSetting( PwmSetting.REPORTING_SEARCH_FILTER, null, actor );
            }
        }
    }
}
