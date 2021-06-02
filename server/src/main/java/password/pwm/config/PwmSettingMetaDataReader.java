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

package password.pwm.config;

import password.pwm.PwmConstants;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.i18n.Config;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PwmSettingMetaDataReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSettingMetaDataReader.class );

    private final PwmSetting pwmSetting;

    // cached values read from XML file
    private final Supplier<List<PwmSetting.TemplateSetReference<StoredValue>>> defaultValues = new LazySupplier<>( () -> InternalReader.readDefaultValue( getPwmSetting() ) );
    private final Supplier<List<PwmSetting.TemplateSetReference<String>>> examples = new LazySupplier<>( () -> InternalReader.readExamples( getPwmSetting() ) );
    private final Supplier<Map<String, String>> options = new LazySupplier<>( () -> InternalReader.readOptions( getPwmSetting() ) );
    private final Supplier<Set<PwmSettingFlag>> flags = new LazySupplier<>( () -> InternalReader.readFlags( getPwmSetting() ) );
    private final Supplier<Map<PwmSettingProperty, String>> properties = new LazySupplier<>( () -> InternalReader.readProperties( getPwmSetting() ) );
    private final Supplier<Collection<LDAPPermissionInfo>> ldapPermissionInfo = new LazySupplier<>( () -> InternalReader.readLdapPermissionInfo( getPwmSetting() ) );
    private final Supplier<Boolean> required = new LazySupplier<>( () -> InternalReader.readRequired( getPwmSetting() ) );
    private final Supplier<Boolean> hidden = new LazySupplier<>( () -> InternalReader.readHidden( getPwmSetting() ) );
    private final Supplier<Integer> level = new LazySupplier<>( () -> InternalReader.readLevel( getPwmSetting() ) );
    private final Supplier<Pattern> pattern = new LazySupplier<>( () -> InternalReader.readPattern( getPwmSetting() ) );
    private final Supplier<String> defaultLocaleLabel = new LazySupplier<>( () -> InternalReader.readLabel( getPwmSetting(), PwmConstants.DEFAULT_LOCALE ) );
    private final Supplier<String> defaultLocaleDescription = new LazySupplier<>( () -> InternalReader.readDescription( getPwmSetting(), PwmConstants.DEFAULT_LOCALE ) );
    private final Supplier<String> defaultMenuLocation = new LazySupplier<>( () -> InternalReader.readMenuLocationDebugDefault( getPwmSetting() ) );

    public PwmSettingMetaDataReader( final PwmSetting pwmSetting )
    {
        this.pwmSetting = pwmSetting;
    }

    public PwmSetting getPwmSetting()
    {
        return pwmSetting;
    }

    public Map<PwmSettingProperty, String> getProperties( )
    {
        return properties.get();
    }

    public Set<PwmSettingFlag> getFlags( )
    {
        return flags.get();
    }

    public Map<String, String> getOptions()
    {
        return options.get();
    }


    public String getLabel( final Locale locale )
    {
        if ( PwmConstants.DEFAULT_LOCALE.equals( locale ) )
        {
            return defaultLocaleLabel.get();
        }

        return InternalReader.readLabel( pwmSetting, locale );
    }

    public String getDescription( final Locale locale )
    {
        if ( PwmConstants.DEFAULT_LOCALE.equals( locale ) )
        {
            return defaultLocaleDescription.get();
        }

        return InternalReader.readDescription( pwmSetting, locale );
    }

    public String getExample( final PwmSettingTemplateSet template )
    {
         return PwmSetting.TemplateSetReference.referenceForTempleSet( examples.get(), template );
    }

    public boolean isRequired( )
    {
        return required.get();
    }

    public boolean isHidden( )
    {
        return hidden.get();
    }

    public int getLevel( )
    {
        return level.get();
    }

    public Pattern getRegExPattern( )
    {
        return pattern.get();
    }

    public Collection<LDAPPermissionInfo> getLDAPPermissionInfo()
    {
        return ldapPermissionInfo.get();
    }

    List<PwmSetting.TemplateSetReference<StoredValue>> getDefaultValue()
    {
        return defaultValues.get();
    }

    public String toMenuLocationDebug(
            final String profileID,
            final Locale locale
    )
    {
        if ( PwmConstants.DEFAULT_LOCALE.equals( locale ) && StringUtil.isEmpty( profileID ) )
        {
            return defaultMenuLocation.get();
        }
        else
        {
            return InternalReader.readMenuLocationDebug( pwmSetting, profileID, locale );
        }
    }

    private static class InternalReader
    {
        private static Set<PwmSettingFlag> readFlags( final PwmSetting pwmSetting )
        {
            final Set<PwmSettingFlag> returnObj = EnumSet.noneOf( PwmSettingFlag.class );
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final List<XmlElement> flagElements = settingElement.getChildren( "flag" );
            for ( final XmlElement flagElement : flagElements )
            {
                final String value = flagElement.getTextTrim();

                try
                {
                    final PwmSettingFlag flag = PwmSettingFlag.valueOf( value );
                    returnObj.add( flag );
                }
                catch ( final IllegalArgumentException e )
                {
                    LOGGER.error( () -> "unknown flag for setting " + pwmSetting.getKey() + ", error: unknown flag value: " + value );
                }

            }
            return Collections.unmodifiableSet( returnObj );
        }

        private static Map<String, String> readOptions( final PwmSetting pwmSetting )
        {
            final Map<String, String> returnList = new LinkedHashMap<>();
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final Optional<XmlElement> optionsElement = settingElement.getChild( PwmSettingXml.XML_ELEMENT_OPTIONS );
            if ( optionsElement.isPresent() )
            {
                final List<XmlElement> optionElements = optionsElement.get().getChildren( PwmSettingXml.XML_ELEMENT_OPTION );
                if ( optionElements != null )
                {
                    for ( final XmlElement optionElement : optionElements )
                    {
                        if ( optionElement.getAttributeValue( PwmSettingXml.XML_ELEMENT_VALUE ) == null )
                        {
                            throw new IllegalStateException( "option element is missing 'value' attribute for key " + pwmSetting.getKey() );
                        }
                        returnList.put( optionElement.getAttributeValue( PwmSettingXml.XML_ELEMENT_VALUE ), optionElement.getText() );
                    }
                }
            }
            final Map<String, String> finalList = Collections.unmodifiableMap( returnList );
            return Collections.unmodifiableMap( finalList );
        }

        private static Collection<LDAPPermissionInfo> readLdapPermissionInfo( final PwmSetting pwmSetting )
        {
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final List<XmlElement> permissionElements = settingElement.getChildren( PwmSettingXml.XML_ELEMENT_LDAP_PERMISSION );
            final List<LDAPPermissionInfo> returnObj = new ArrayList<>();
            if ( permissionElements != null )
            {
                for ( final XmlElement permissionElement : permissionElements )
                {
                    final Optional<LDAPPermissionInfo.Actor> actor = JavaHelper.readEnumFromString(
                            LDAPPermissionInfo.Actor.class,
                            permissionElement.getAttributeValue( PwmSettingXml.XML_ATTRIBUTE_PERMISSION_ACTOR )
                    );
                    final Optional<LDAPPermissionInfo.Access> type = JavaHelper.readEnumFromString(
                            LDAPPermissionInfo.Access.class,
                            permissionElement.getAttributeValue( PwmSettingXml.XML_ATTRIBUTE_PERMISSION_ACCESS )
                    );
                    if ( actor.isPresent() && type.isPresent() )
                    {
                        final LDAPPermissionInfo permissionInfo = new LDAPPermissionInfo( type.get(), actor.get() );
                        returnObj.add( permissionInfo );
                    }
                }
            }
            return Collections.unmodifiableList( returnObj );
        }

        private static List<PwmSetting.TemplateSetReference<String>> readExamples( final PwmSetting pwmSetting )
        {
            final List<PwmSetting.TemplateSetReference<String>> returnObj = new ArrayList<>();
            final MacroRequest macroRequest = MacroRequest.forStatic();
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final List<XmlElement> exampleElements = settingElement.getChildren( PwmSettingXml.XML_ELEMENT_EXAMPLE );
            for ( final XmlElement exampleElement : exampleElements )
            {
                final Set<PwmSettingTemplate> definedTemplates = PwmSettingXml.parseTemplateAttribute( exampleElement );
                final String exampleString = macroRequest.expandMacros( exampleElement.getText() );
                returnObj.add( new PwmSetting.TemplateSetReference<>( exampleString, Collections.unmodifiableSet( definedTemplates ) ) );
            }
            if ( returnObj.isEmpty() )
            {
                returnObj.add( new PwmSetting.TemplateSetReference<>( "", Collections.emptySet() ) );
            }
            return Collections.unmodifiableList( returnObj );
        }

        private static Map<PwmSettingProperty, String> readProperties( final PwmSetting pwmSetting )
        {
            final Map<PwmSettingProperty, String> newProps = new EnumMap<>( PwmSettingProperty.class );
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final Optional<XmlElement> propertiesElement = settingElement.getChild( PwmSettingXml.XML_ELEMENT_PROPERTIES );
            if ( propertiesElement.isPresent() )
            {
                final List<XmlElement> propertyElements = propertiesElement.get().getChildren( PwmSettingXml.XML_ELEMENT_PROPERTY );
                if ( propertyElements != null )
                {
                    for ( final XmlElement propertyElement : propertyElements )
                    {
                        if ( propertyElement.getAttributeValue( PwmSettingXml.XML_ATTRIBUTE_KEY ) == null )
                        {
                            throw new IllegalStateException( "property element is missing 'key' attribute for value " + pwmSetting.getKey() );
                        }
                        final PwmSettingProperty property = JavaHelper.readEnumFromString(
                                PwmSettingProperty.class,
                                null,
                                propertyElement.getAttributeValue( PwmSettingXml.XML_ATTRIBUTE_KEY ) );
                        if ( property == null )
                        {
                            throw new IllegalStateException( "property element has unknown 'key' attribute for value " + pwmSetting.getKey() );
                        }
                        newProps.put( property, propertyElement.getText() );
                    }
                }
            }
            return Collections.unmodifiableMap( newProps );
        }

        private static List<PwmSetting.TemplateSetReference<StoredValue>> readDefaultValue( final PwmSetting pwmSetting )
        {
            final List<PwmSetting.TemplateSetReference<StoredValue>> returnObj = new ArrayList<>();
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final List<XmlElement> defaultElements = settingElement.getChildren( PwmSettingXml.XML_ELEMENT_DEFAULT );
            if ( pwmSetting.getSyntax() == PwmSettingSyntax.PASSWORD )
            {
                returnObj.add( new PwmSetting.TemplateSetReference<>( new PasswordValue( null ), Collections.emptySet() ) );
            }
            else
            {
                for ( final XmlElement defaultElement : defaultElements )
                {
                    final Set<PwmSettingTemplate> definedTemplates = PwmSettingXml.parseTemplateAttribute( defaultElement );
                    final StoredValue storedValue = ValueFactory.fromXmlValues( pwmSetting, defaultElement, null );
                    returnObj.add( new PwmSetting.TemplateSetReference<>( storedValue, definedTemplates ) );
                }
            }
            if ( returnObj.isEmpty() )
            {
                throw new IllegalStateException( "no default value for setting " + pwmSetting.getKey() );
            }
            return Collections.unmodifiableList( returnObj );
        }


        private static boolean readRequired( final PwmSetting pwmSetting )
        {
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final String requiredAttribute = settingElement.getAttributeValue( PwmSettingXml.XML_ELEMENT_REQUIRED );
            return "true".equalsIgnoreCase( requiredAttribute );
        }

        private static boolean readHidden( final PwmSetting pwmSetting )
        {
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final String requiredAttribute = settingElement.getAttributeValue( PwmSettingXml.XML_ELEMENT_HIDDEN );
            return "true".equalsIgnoreCase( requiredAttribute ) || pwmSetting.getCategory().isHidden();
        }

        private static int readLevel( final PwmSetting pwmSetting )
        {
            final XmlElement settingElement = PwmSettingXml.readSettingXml( pwmSetting );
            final String levelAttribute = settingElement.getAttributeValue( PwmSettingXml.XML_ELEMENT_LEVEL );
            return JavaHelper.silentParseInt( levelAttribute, 0 );
        }

        private static Pattern readPattern( final PwmSetting pwmSetting )
        {
            final XmlElement settingNode = PwmSettingXml.readSettingXml( pwmSetting );
            final Optional<XmlElement> regexNode = settingNode.getChild( PwmSettingXml.XML_ELEMENT_REGEX );
            if ( regexNode.isPresent() )
            {
                try
                {
                    return Pattern.compile( regexNode.get().getText() );
                }
                catch ( final PatternSyntaxException e )
                {
                    final String errorMsg = "error compiling regex constraints for setting " + pwmSetting.toString() + ", error: " + e.getMessage();
                    LOGGER.error( () -> errorMsg, e );
                    throw new IllegalStateException( errorMsg, e );
                }
            }
            return Pattern.compile( ".*", Pattern.DOTALL );
        }

        private static String readMenuLocationDebugDefault( final PwmSetting pwmSetting )
        {
            final Locale locale = PwmConstants.DEFAULT_LOCALE;
            final String separator = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );
            return pwmSetting.getCategory().toMenuLocationDebug( null, locale ) + separator + pwmSetting.getLabel( locale );
        }

        private static String readMenuLocationDebug( final PwmSetting pwmSetting, final String profileID, final Locale locale )
        {
            final String separator = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );
            return pwmSetting.getCategory().toMenuLocationDebug( profileID, locale ) + separator + pwmSetting.getLabel( locale );
        }

        private static String readLabel( final PwmSetting pwmSetting, final Locale locale )
        {
            return readStringProperty( password.pwm.i18n.PwmSetting.SETTING_LABEL_PREFIX + pwmSetting.getKey(), locale );
        }

        private static String readDescription( final PwmSetting pwmSetting, final Locale locale )
        {
            return readStringProperty( password.pwm.i18n.PwmSetting.SETTING_DESCRIPTION_PREFIX + pwmSetting.getKey(), locale );
        }

        private static String readStringProperty( final String key, final Locale locale )
        {
            final String storedText = LocaleHelper.getLocalizedMessage( locale, key, null, password.pwm.i18n.PwmSetting.class );
            final MacroRequest macroRequest = MacroRequest.forStatic();
            return macroRequest.expandMacros( storedText );
        }
    }
}
