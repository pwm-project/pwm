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

