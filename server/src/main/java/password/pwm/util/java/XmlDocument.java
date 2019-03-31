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
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface XmlDocument
{
    XmlElement getRootElement();

    XmlElement evaluateXpathToElement( String xpathExpression );

    List<XmlElement> evaluateXpathToElements( String xpathExpression );

    XmlDocument copy();

    class XmlDocumentJDOM implements XmlDocument
    {
        final Document document;

        XmlDocumentJDOM( final Document document )
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
            final XPathExpression<Element> xp = xpfac.compile( xpathExpression, Filters.element() );
            final Element element = xp.evaluateFirst( document );
            return element == null ? null : new XmlElement.XmlElementJDOM( element );
        }

        @Override
        public List<XmlElement> evaluateXpathToElements(
                final String xpathExpression
        )
        {
            final List<XmlElement> returnList = new ArrayList<>(  );

            final XPathFactory xpfac = XPathFactory.instance();
            final XPathExpression<Element> xp = xpfac.compile( xpathExpression, Filters.element() );
            for ( final Element element : xp.evaluate( document ) )
            {
                returnList.add( new XmlElement.XmlElementJDOM( element ) );
            }
            return Collections.unmodifiableList( returnList );
        }

        @Override
        public XmlDocument copy()
        {
            return new XmlDocumentJDOM( document.clone() );
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
            final List<XmlElement> elements = evaluateXpathToElements( xpathExpression );
            if ( JavaHelper.isEmpty( elements ) )
            {
                return null;
            }
            return elements.iterator().next();
        }

        @Override
        public List<XmlElement> evaluateXpathToElements(
                final String xpathExpression
        )
        {
            try
            {
                final XPath xPath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                final javax.xml.xpath.XPathExpression expression = xPath.compile( xpathExpression );
                final NodeList nodeList = (NodeList) expression.evaluate( document, XPathConstants.NODESET );
                return XmlFactory.XmlFactoryW3c.nodeListToElementList( nodeList );
            }
            catch ( XPathExpressionException e )
            {
                throw new IllegalStateException( "error evaluating xpath expression: " + e.getMessage() );
            }
        }

        @Override
        public XmlDocument copy()
        {
            return new XmlDocumentW3c( ( org.w3c.dom.Document) document.cloneNode( true ) );
        }
    }
}
