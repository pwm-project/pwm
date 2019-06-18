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
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PwmSettingXml
{
    public static final String SETTING_XML_FILENAME = ( PwmSetting.class.getPackage().getName()
            + "." + PwmSetting.class.getSimpleName() ).replace( ".", "/" ) + ".xml";

    public static final String XML_ELEMENT_LDAP_PERMISSION = "ldapPermission";
    public static final String XML_ELEMENT_EXAMPLE = "example";
    public static final String XML_ELEMENT_DEFAULT = "default";

    public static final String XML_ATTRIBUTE_PERMISSION_ACTOR = "actor";
    public static final String XML_ATTRIBUTE_PERMISSION_ACCESS = "access";
    public static final String XML_ATTRIBUTE_TEMPLATE = "template";

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSettingXml.class );

    private static WeakReference<XmlDocument> xmlDocCache = new WeakReference<>( null );
    private static final AtomicInteger LOAD_COUNTER = new AtomicInteger( 0 );

    private static XmlDocument readXml( )
    {
        final XmlDocument docRefCopy = xmlDocCache.get();
        if ( docRefCopy == null )
        {
            final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( SETTING_XML_FILENAME );
            try
            {
                final Instant startTime = Instant.now();
                final XmlDocument newDoc = XmlFactory.getFactory().parseXml( inputStream );
                final TimeDuration parseDuration = TimeDuration.fromCurrent( startTime );
                LOGGER.trace( () -> "parsed PwmSettingXml in " + parseDuration.asCompactString() + ", loads=" + LOAD_COUNTER.getAndIncrement() );

                xmlDocCache = new WeakReference<>( newDoc );

                return newDoc;
            }
            catch ( PwmUnrecoverableException e )
            {
                throw new IllegalStateException( "error parsing " + SETTING_XML_FILENAME + ": " + e.getMessage() );
            }
        }
        return docRefCopy;
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
        catch ( Exception e )
        {
            throw new IllegalStateException( "error validating PwmSetting.xml schema using PwmSetting.xsd definition: " + e.getMessage() );
        }
    }

    static XmlElement readSettingXml( final PwmSetting setting )
    {
        final String expression = "/settings/setting[@key=\"" + setting.getKey() + "\"]";
        return readXml().evaluateXpathToElement( expression );
    }

    static XmlElement readCategoryXml( final PwmSettingCategory category )
    {
        final String expression = "/settings/category[@key=\"" + category.toString() + "\"]";
        return readXml().evaluateXpathToElement( expression );
    }

    static XmlElement readTemplateXml( final PwmSettingTemplate template )
    {
        final String expression = "/settings/template[@key=\"" + template.toString() + "\"]";
        return readXml().evaluateXpathToElement( expression );
    }

    static Set<PwmSettingTemplate> parseTemplateAttribute( final XmlElement element )
    {
        if ( element == null )
        {
            return Collections.emptySet();
        }
        final String templateStrValues = element.getAttributeValue( "template" );
        final String[] templateSplitValues = templateStrValues == null
                ? new String[ 0 ]
                : templateStrValues.split( "," );
        final Set<PwmSettingTemplate> definedTemplates = new LinkedHashSet<>();
        for ( final String templateStrValue : templateSplitValues )
        {
            final PwmSettingTemplate template = PwmSettingTemplate.valueOf( templateStrValue );
            if ( template != null )
            {
                definedTemplates.add( template );
            }
        }
        return definedTemplates;
    }
}

