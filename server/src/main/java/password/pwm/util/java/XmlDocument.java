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
import java.util.Optional;

public interface XmlDocument
{
    XmlElement getRootElement();

    Optional<XmlElement> evaluateXpathToElement( String xpathExpression );

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
        public Optional<XmlElement> evaluateXpathToElement(
                final String xpathExpression
        )
        {
            final XPathFactory xpfac = XPathFactory.instance();
            final XPathExpression<Element> xp = xpfac.compile( xpathExpression, Filters.element() );
            final Element element = xp.evaluateFirst( document );
            return element == null
                    ? Optional.empty()
                    : Optional.of( new XmlElement.XmlElementJDOM( element ) );
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
        public Optional<XmlElement> evaluateXpathToElement(
                final String xpathExpression
        )
        {
            final List<XmlElement> elements = evaluateXpathToElements( xpathExpression );
            if ( JavaHelper.isEmpty( elements ) )
            {
                return Optional.empty();
            }
            return Optional.of( elements.iterator().next() );
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
            catch ( final XPathExpressionException e )
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
