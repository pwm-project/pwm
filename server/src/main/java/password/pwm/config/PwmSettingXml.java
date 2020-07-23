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

package password.pwm.config;

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySoftReference;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PwmSettingXml
{
    public static final String SETTING_XML_FILENAME = ( PwmSetting.class.getPackage().getName()
            + "." + PwmSetting.class.getSimpleName() ).replace( ".", "/" ) + ".xml";

    static final String XML_ELEMENT_LDAP_PERMISSION = "ldapPermission";
    static final String XML_ELEMENT_EXAMPLE = "example";
    static final String XML_ELEMENT_DEFAULT = "default";

    static final String XML_ATTRIBUTE_PERMISSION_ACTOR = "actor";
    static final String XML_ATTRIBUTE_PERMISSION_ACCESS = "access";
    static final String XML_ATTRIBUTE_TEMPLATE = "template";
    static final String XML_ELEMENT_REGEX = "regex";
    static final String XML_ELEMENT_HIDDEN = "hidden";
    static final String XML_ELEMENT_REQUIRED = "required";
    static final String XML_ELEMENT_LEVEL = "level";
    static final String XML_ELEMENT_PROPERTIES = "properties";
    static final String XML_ELEMENT_PROPERTY = "property";
    static final String XML_ATTRIBUTE_KEY = "key";
    static final String XML_ELEMENT_VALUE = "value";
    static final String XML_ELEMENT_OPTION = "option";
    static final String XML_ELEMENT_OPTIONS = "options";


    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSettingXml.class );

    private static LazySoftReference<XmlDocument> xmlDocCache = new LazySoftReference<>( () -> readXml() );
    private static final AtomicInteger LOAD_COUNTER = new AtomicInteger( 0 );

    private static XmlDocument readXml( )
    {
        try ( InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( SETTING_XML_FILENAME ) )
        {
            final Instant startTime = Instant.now();
            final XmlDocument newDoc = XmlFactory.getFactory().parseXml( inputStream );
            final TimeDuration parseDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.trace( () -> "parsed PwmSettingXml in " + parseDuration.asCompactString() + ", loads=" + LOAD_COUNTER.getAndIncrement() );
            return newDoc;
        }
        catch ( final IOException | PwmUnrecoverableException e )
        {
            throw new IllegalStateException( "error parsing " + SETTING_XML_FILENAME + ": " + e.getMessage() );
        }
    }

    private static void validateXmlSchema( )
    {
        try
        {
            final InputStream xsdInputStream = PwmSetting.class.getClassLoader().getResourceAsStream( "password/pwm/config/PwmSetting.xsd" );
            final InputStream xmlInputStream = PwmSetting.class.getClassLoader().getResourceAsStream( "password/pwm/config/PwmSetting.xml" );
            final SchemaFactory factory = SchemaFactory.newInstance( XMLConstants.W3C_XML_SCHEMA_NS_URI );
            final Schema schema = factory.newSchema( new StreamSource( xsdInputStream ) );
            final Validator validator = schema.newValidator();
            validator.validate( new StreamSource( xmlInputStream ) );
        }
        catch ( final Exception e )
        {
            throw new IllegalStateException( "error validating PwmSetting.xml schema using PwmSetting.xsd definition: " + e.getMessage() );
        }
    }

    static XmlElement readSettingXml( final PwmSetting setting )
    {
        final String expression = "/settings/setting[@key=\"" + setting.getKey() + "\"]";
        return xmlDocCache.get().evaluateXpathToElement( expression )
                .orElseThrow( () -> new IllegalStateException( "PwmSetting.xml is missing setting for key '" + setting.getKey() + "'" ) );
    }

    static XmlElement readCategoryXml( final PwmSettingCategory category )
    {
        final String expression = "/settings/category[@key=\"" + category.toString() + "\"]";
        return xmlDocCache.get().evaluateXpathToElement( expression )
                .orElseThrow( () -> new IllegalStateException( "PwmSetting.xml is missing category for key '" + category.getKey() + "'" ) );
    }

    static XmlElement readTemplateXml( final PwmSettingTemplate template )
    {
        final String expression = "/settings/template[@key=\"" + template.toString() + "\"]";
        return xmlDocCache.get().evaluateXpathToElement( expression )
                .orElseThrow( () -> new IllegalStateException( "PwmSetting.xml is missing template for key '" + template.toString() + "'" ) );
    }

    static Set<PwmSettingTemplate> parseTemplateAttribute( final XmlElement element )
    {
        if ( element == null )
        {
            return Collections.emptySet();
        }
        final String templateStrValues = element.getAttributeValue( XML_ATTRIBUTE_TEMPLATE );
        final String[] templateSplitValues = templateStrValues == null
                ? new String[ 0 ]
                : templateStrValues.split( "," );
        final Set<PwmSettingTemplate> definedTemplates = new LinkedHashSet<>();
        for ( final String templateStrValue : templateSplitValues )
        {
            final PwmSettingTemplate template = JavaHelper.readEnumFromString( PwmSettingTemplate.class, null, templateStrValue );
            if ( template != null )
            {
                definedTemplates.add( template );
            }
        }
        return definedTemplates;
    }
}

