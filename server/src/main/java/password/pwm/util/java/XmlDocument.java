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

package password.pwm.util.java;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.util.List;

public interface XmlDocument
{
    XmlElement getRootElement();

    XmlElement evaluateXpathToElement( String xpathExpression );

    class XmlDocumentJDOM implements XmlDocument
    {
        final Document document;

        public XmlDocumentJDOM( final Document document )
        {
            this.document = document;
        }

        @Override
        public XmlElement getRootElement()
        {
            return new XmlElement.XmlElementJDOM( document.getRootElement() );
        }

        @Override
        public XmlElement evaluateXpathToElement(
                final String xpathExpression
        )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final XPathExpression<Object> xp = xpfac.compile( xpathExpression );
            final Element settingElement = ( Element ) xp.evaluateFirst( document );
            return settingElement == null ? null : new XmlElement.XmlElementJDOM( settingElement );
        }

    }

    class XmlDocumentW3c implements XmlDocument
    {
        final org.w3c.dom.Document document;

        public XmlDocumentW3c( final org.w3c.dom.Document document )
        {
            this.document = document;
        }

        @Override
        public XmlElement getRootElement()
        {
            return new XmlElement.XmlElementW3c( document.getDocumentElement() );
        }

        @Override
        public XmlElement evaluateXpathToElement(
                final String xpathExpression
        )
        {
            try
            {
                final XPath xPath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                final javax.xml.xpath.XPathExpression expression = xPath.compile( xpathExpression );
                final NodeList nodeList = (NodeList) expression.evaluate( document, XPathConstants.NODESET );
                final List<XmlElement> elementList = XmlFactory.XmlFactoryW3c.nodeListToElementList( nodeList );
                if ( JavaHelper.isEmpty( elementList ) )
                {
                    return null;
                }
                return elementList.iterator().next();
            }
            catch ( XPathExpressionException e )
            {
                throw new IllegalStateException( "error evaluating xpath expression: " + e.getMessage() );
            }
        }

    }
}
