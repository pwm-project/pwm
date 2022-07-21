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

package password.pwm.config;

import lombok.Builder;
import lombok.Value;
import org.jrivard.xmlchai.XmlElement;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.macro.MacroRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Value
@Builder
/**
 * Utility class for reading PwmSetting.xml values and making them available to PWM.  Since PwmSetting
 * enums are fairly complex, there can be issues with static initialization circular dependencies during
 * initialization of the classes in this package.
 *
 * This class tries to be self-sufficient during initialization, and then provide values for {@link PwmSetting}
 * method invocations.
 */
class PwmSettingMetaData
{
    private static final Map<PwmSetting, PwmSettingMetaData> META_DATA_MAP = initMap();

    private final List<TemplateSetReference<String>> examples;
    private final Map<String, String> options;
    private final Set<PwmSettingFlag> flags;
    private final Map<PwmSettingProperty, String> properties;
    private final Collection<LDAPPermissionInfo> ldapPermissionInfo;
    private final boolean required;
    private final boolean hidden;
    private final int level;
    private final Pattern pattern;

    private static Map<PwmSetting, PwmSettingMetaData> initMap()
    {
        final EnumMap<PwmSetting, PwmSettingMetaData> map = new EnumMap<>( PwmSetting.class );

        for ( final XmlElement settingElement : PwmSettingXml.readAllSettingXmlElements() )
        {
            final PwmSetting pwmSetting = PwmSetting.forKey( settingElement.getAttribute( PwmSettingXml.XML_ATTRIBUTE_KEY )
                    .orElseThrow() ).orElseThrow();

            final PwmSettingMetaData pwmSettingMetaData = PwmSettingMetaData.builder()
                    .examples( InitReader.readExamples( settingElement ) )
                    .properties( InitReader.readProperties( pwmSetting, settingElement ) )
                    .options( InitReader.readOptions( pwmSetting, settingElement ) )
                    .flags( InitReader.readFlags( settingElement ) )
                    .ldapPermissionInfo( InitReader.readLdapPermissionInfo( settingElement ) )
                    .required( InitReader.readRequired( settingElement ) )
                    .hidden( InitReader.readHidden( pwmSetting, settingElement ) )
                    .level( InitReader.readLevel( settingElement ) )
                    .pattern( InitReader.readPattern( pwmSetting, settingElement ) )
                    .build();

            map.put( pwmSetting, pwmSettingMetaData );
        }

        return map;
    }

    static PwmSettingMetaData forSetting( final PwmSetting pwmSetting )
    {
        return META_DATA_MAP.get( pwmSetting );
    }

    private static class InitReader
    {
        private static Set<PwmSettingFlag> readFlags( final XmlElement settingElement )
        {
            final Set<PwmSettingFlag> returnObj = EnumSet.noneOf( PwmSettingFlag.class );
            settingElement.getChild( PwmSettingXml.XML_ELEMENT_FLAGS ).ifPresent( flagElement ->
                    flagElement.getChildren( PwmSettingXml.XML_ELEMENT_FLAG ).forEach( flagsElement ->
                    {
                        final String value = flagsElement.getText().orElse( "" ).trim();
                        JavaHelper.readEnumFromString( PwmSettingFlag.class, value ).ifPresent( returnObj::add );
                    } )
            );
            return Collections.unmodifiableSet( returnObj );
        }

        private static Map<String, String> readOptions( final PwmSetting pwmSetting, final XmlElement settingElement )
        {
            final Map<String, String> returnData = new LinkedHashMap<>();
            final Optional<XmlElement> optionsElement = settingElement.getChild( PwmSettingXml.XML_ELEMENT_OPTIONS );
            if ( optionsElement.isPresent() )
            {
                final List<XmlElement> optionElements = optionsElement.get().getChildren( PwmSettingXml.XML_ELEMENT_OPTION );
                if ( optionElements != null )
                {
                    for ( final XmlElement optionElement : optionElements )
                    {
                        final String value = optionElement.getAttribute( PwmSettingXml.XML_ELEMENT_VALUE )
                                .orElseThrow( () -> new IllegalStateException( "option element is missing 'value' attribute for key " + pwmSetting.getKey() ) );

                        optionElement.getText().ifPresent( textValue ->  returnData.put( value, textValue ) );
                    }
                }
            }
            return Collections.unmodifiableMap( returnData );
        }

        private static Collection<LDAPPermissionInfo> readLdapPermissionInfo( final XmlElement settingElement )
        {
            final List<LDAPPermissionInfo> returnObj = new ArrayList<>();

            settingElement.getChild( PwmSettingXml.XML_ELEMENT_PERMISSION ).ifPresent( permissionElement ->
            {
                permissionElement.getChildren( PwmSettingXml.XML_ELEMENT_LDAP ).forEach( ldapElement ->
                {
                    final Optional<LDAPPermissionInfo.Actor> actor = JavaHelper.readEnumFromString(
                            LDAPPermissionInfo.Actor.class,
                            permissionElement.getAttribute( PwmSettingXml.XML_ATTRIBUTE_PERMISSION_ACTOR ).orElse( "" ) );
                    final Optional<LDAPPermissionInfo.Access> type = JavaHelper.readEnumFromString(
                            LDAPPermissionInfo.Access.class,
                            permissionElement.getAttribute( PwmSettingXml.XML_ATTRIBUTE_PERMISSION_ACCESS ).orElse( "" ) );

                    if ( actor.isPresent() && type.isPresent() )
                    {
                        returnObj.add( new LDAPPermissionInfo( type.get(), actor.get() ) );
                    }
                } );
            } );

            return Collections.unmodifiableList( returnObj );
        }

        private static List<TemplateSetReference<String>> readExamples( final XmlElement settingElement )
        {
            final List<TemplateSetReference<String>> returnObj = new ArrayList<>();
            final MacroRequest macroRequest = MacroRequest.forStatic();
            final List<XmlElement> exampleElements = settingElement.getChildren( PwmSettingXml.XML_ELEMENT_EXAMPLE );
            for ( final XmlElement exampleElement : exampleElements )
            {
                final Set<PwmSettingTemplate> definedTemplates = PwmSettingXml.parseTemplateAttribute( exampleElement );
                exampleElement.getText().ifPresent( textValue ->
                {
                    final String exampleString = macroRequest.expandMacros( textValue );
                    returnObj.add( new TemplateSetReference<>( exampleString, Collections.unmodifiableSet( definedTemplates ) ) );
                } );
            }
            if ( returnObj.isEmpty() )
            {
                returnObj.add( new TemplateSetReference<>( "", Collections.emptySet() ) );
            }
            return Collections.unmodifiableList( returnObj );
        }

        private static Map<PwmSettingProperty, String> readProperties( final PwmSetting pwmSetting, final XmlElement settingElement )
        {
            final Map<PwmSettingProperty, String> newProps = new EnumMap<>( PwmSettingProperty.class );
            final Optional<XmlElement> propertiesElement = settingElement.getChild( PwmSettingXml.XML_ELEMENT_PROPERTIES );
            if ( propertiesElement.isPresent() )
            {
                final List<XmlElement> propertyElements = propertiesElement.get().getChildren( PwmSettingXml.XML_ELEMENT_PROPERTY );
                if ( propertyElements != null )
                {
                    for ( final XmlElement propertyElement : propertyElements )
                    {
                        final String keyAttribute = propertyElement.getAttribute( PwmSettingXml.XML_ATTRIBUTE_KEY )
                                .orElseThrow( () -> new IllegalStateException( "property element is missing 'key' attribute for value " + pwmSetting.getKey() ) );

                        final PwmSettingProperty property = JavaHelper.readEnumFromString( PwmSettingProperty.class, keyAttribute )
                                .orElseThrow( () -> new IllegalStateException( "property element has unknown 'key' attribute for value " + pwmSetting.getKey() ) );

                        propertyElement.getText().ifPresent( value -> newProps.put( property, value ) );
                    }
                }
            }
            return Collections.unmodifiableMap( newProps );
        }

        private static boolean readRequired( final XmlElement settingElement )
        {
            final String requiredAttribute = settingElement.getAttribute( PwmSettingXml.XML_ELEMENT_REQUIRED ).orElse( "" );
            return "true".equalsIgnoreCase( requiredAttribute );
        }

        private static boolean readHidden( final PwmSetting pwmSetting, final XmlElement settingElement )
        {
            final String requiredAttribute = settingElement.getAttribute( PwmSettingXml.XML_ELEMENT_HIDDEN ).orElse( "" );
            return "true".equalsIgnoreCase( requiredAttribute ) || pwmSetting.getCategory().isHidden();
        }

        private static int readLevel( final XmlElement settingElement )
        {
            final String levelAttribute = settingElement.getAttribute( PwmSettingXml.XML_ELEMENT_LEVEL ).orElse( "" );
            return JavaHelper.silentParseInt( levelAttribute, 0 );
        }

        private static Pattern readPattern( final PwmSetting pwmSetting, final XmlElement settingElement )
        {
            final Optional<XmlElement> regexNode = settingElement.getChild( PwmSettingXml.XML_ELEMENT_REGEX );
            if ( regexNode.isPresent() )
            {
                final Optional<String> regexText = regexNode.get().getText();
                if ( regexText.isPresent() )
                {
                    try
                    {
                        return Pattern.compile( regexText.get() );
                    }
                    catch ( final PatternSyntaxException e )
                    {
                        final String errorMsg = "error compiling regex constraints for setting " + pwmSetting + ", error: " + e.getMessage();
                        throw new IllegalStateException( errorMsg, e );
                    }
                }
            }
            return Pattern.compile( ".*", Pattern.DOTALL );
        }
    }
}
