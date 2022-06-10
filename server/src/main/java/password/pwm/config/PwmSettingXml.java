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

import org.jrivard.xmlchai.AccessMode;
import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlElement;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySoftReference;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PwmSettingXml
{
    public static final String SETTING_XML_FILENAME = ( PwmSetting.class.getPackage().getName()
            + "." + PwmSetting.class.getSimpleName() ).replace( '.', '/' ) + ".xml";

    static final String XML_ELEMENT_PERMISSION = "permission";
    static final String XML_ELEMENT_LDAP = "ldap";
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
    static final String XML_ELEMENT_SCOPE = "scope";
    static final String XML_ELEMENT_SETTING = "setting";

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSettingXml.class );

    private static final LazySoftReference<XmlDocument> XML_DOC_CACHE = new LazySoftReference<>( PwmSettingXml::readXml );
    private static final AtomicInteger LOAD_COUNTER = new AtomicInteger( 0 );

    private static XmlDocument readXml( )
    {
        try ( InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( SETTING_XML_FILENAME ) )
        {
            final Instant startTime = Instant.now();
            final XmlDocument newDoc = XmlChai.getFactory().parse( inputStream, AccessMode.IMMUTABLE );
            final TimeDuration parseDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.trace( () -> "parsed PwmSettingXml in " + parseDuration.asCompactString() + ", loads=" + LOAD_COUNTER.getAndIncrement() );
            return newDoc;
        }
        catch ( final IOException e )
        {
            throw new IllegalStateException( "error parsing " + SETTING_XML_FILENAME + ": " + e.getMessage(), e );
        }
    }

    static List<XmlElement> readAllSettingXmlElements()
    {
        return XML_DOC_CACHE.get().getRootElement().getChildren( XML_ELEMENT_SETTING );
    }

    static XmlElement readSettingXml( final PwmSetting setting )
    {
        final String expression = "/settings/setting[@key=\"" + setting.getKey() + "\"]";
        return XML_DOC_CACHE.get().evaluateXpathToElement( expression )
                .orElseThrow( () -> new IllegalStateException( "PwmSetting.xml is missing setting for key '" + setting.getKey() + "'" ) );
    }

    static XmlElement readCategoryXml( final PwmSettingCategory category )
    {
        final String expression = "/settings/category[@key=\"" + category.toString() + "\"]";
        return XML_DOC_CACHE.get().evaluateXpathToElement( expression )
                .orElseThrow( () -> new IllegalStateException( "PwmSetting.xml is missing category for key '" + category.getKey() + "'" ) );
    }

    static XmlElement readTemplateXml( final PwmSettingTemplate template )
    {
        final String expression = "/settings/template[@key=\"" + template.toString() + "\"]";
        return XML_DOC_CACHE.get().evaluateXpathToElement( expression )
                .orElseThrow( () -> new IllegalStateException( "PwmSetting.xml is missing template for key '" + template + "'" ) );
    }

    static Set<PwmSettingTemplate> parseTemplateAttribute( final XmlElement element )
    {
        if ( element == null )
        {
            return Collections.emptySet();
        }
        final String templateStrValues = element.getAttribute( XML_ATTRIBUTE_TEMPLATE ).orElse( "" );
        final String[] templateSplitValues = templateStrValues.split( "," );
        final Set<PwmSettingTemplate> definedTemplates = new LinkedHashSet<>();
        for ( final String templateStrValue : templateSplitValues )
        {
            final PwmSettingTemplate template = JavaHelper.readEnumFromString( PwmSettingTemplate.class, null, templateStrValue );
            if ( template != null )
            {
                definedTemplates.add( template );
            }
        }
        return Collections.unmodifiableSet( definedTemplates );
    }
}

