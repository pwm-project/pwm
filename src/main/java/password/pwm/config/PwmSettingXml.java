/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import password.pwm.util.Helper;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PwmSettingXml {
    private static final String SETTING_XML_FILENAME = (PwmSetting.class.getPackage().getName() +
            "." + PwmSetting.class.getSimpleName()).replace(".","/") + ".xml";

    private static Document xmlDocCache = null;

    private static Document readXml() {
        final Document docRefCopy = xmlDocCache;
        if (docRefCopy == null) {
            //validateXmlSchema();
            final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream(SETTING_XML_FILENAME);
            final SAXBuilder builder = new SAXBuilder();
            try {
                final Document newDoc = builder.build(inputStream);
                xmlDocCache = newDoc;

                // clear cached dom after 30 seconds.
                final Thread t = new Thread("PwmSettingXml static cache clear thread") {
                    @Override
                    public void run() {
                        Helper.pause(30 * 1000);
                        xmlDocCache = null;
                    }
                };
                t.setDaemon(false);
                t.start();

                return newDoc;
            } catch (JDOMException e) {
                throw new IllegalStateException("error parsing " + SETTING_XML_FILENAME + ": " + e.getMessage());
            } catch (IOException e) {
                throw new IllegalStateException("unable to load " + SETTING_XML_FILENAME + ": " + e.getMessage());
            }

        }
        return docRefCopy;
    }

    private static void validateXmlSchema() {
        try {
            final InputStream xsdInputStream = PwmSetting.class.getClassLoader().getResourceAsStream("password/pwm/config/PwmSetting.xsd");
            final InputStream xmlInputStream = PwmSetting.class.getClassLoader().getResourceAsStream("password/pwm/config/PwmSetting.xml");
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final Schema schema = factory.newSchema(new StreamSource(xsdInputStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xmlInputStream));
        } catch (Exception e) {
            throw new IllegalStateException("error validating PwmSetting.xml schema using PwmSetting.xsd definition: " + e.getMessage());
        }
    }

    static Element readSettingXml(final PwmSetting setting) {
        final XPathFactory xpfac = XPathFactory.instance();
        final XPathExpression xp = xpfac.compile("/settings/setting[@key=\"" + setting.getKey() + "\"]");
        return (Element)xp.evaluateFirst(readXml());
    }

    static Element readCategoryXml(final PwmSettingCategory category) {
        final XPathFactory xpfac = XPathFactory.instance();
        final XPathExpression xp = xpfac.compile("/settings/category[@key=\"" + category.toString() + "\"]");
        return (Element)xp.evaluateFirst(readXml());
    }

    static Element readTemplateXml(final PwmSettingTemplate template) {
        final XPathFactory xpfac = XPathFactory.instance();
        final XPathExpression xp = xpfac.compile("/settings/template[@key=\"" + template.toString() + "\"]");
        return (Element)xp.evaluateFirst(readXml());
    }

    static List<PwmSettingTemplate> parseTemplateAttribute(final Element element) {
        if (element == null) {
            return Collections.emptyList();
        }
        final String templateStrValues = element.getAttributeValue("template");
        final String[] templateSplitValues = templateStrValues == null
                ? new String[0]
                : templateStrValues.split(",");
        final List<PwmSettingTemplate> definedTemplates = new ArrayList<>();
        for (final String templateStrValue : templateSplitValues) {
            final PwmSettingTemplate template = PwmSettingTemplate.valueOf(templateStrValue);
            if (template != null) {
                definedTemplates.add(template);
            }
        }
        return definedTemplates;
    }
}

