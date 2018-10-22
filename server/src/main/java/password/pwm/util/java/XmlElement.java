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

import org.jdom2.Element;
import org.jdom2.input.DOMBuilder;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface XmlElement
{
    XmlElement getChild( String elementName );

    String getAttributeValue( String attribute );

    List<XmlElement> getChildren( String elementName );

    String getText();

    String getTextTrim();

    String getChildText( String elementName );

    org.jdom2.Element asJdomElement();

    class XmlElementJDOM implements XmlElement
    {
        private final Element element;

        XmlElementJDOM( final Element element )
        {
            this.element = element;
        }

        @Override
        public XmlElement getChild( final String elementName )
        {
            final List<XmlElement> children = getChildren( elementName );
            if ( JavaHelper.isEmpty( children ) )
            {
                return null;
            }
            return children.iterator().next();
        }

        @Override
        public String getAttributeValue( final String attribute )
        {
            return element.getAttributeValue( attribute );
        }

        @Override
        public List<XmlElement> getChildren( final String elementName )
        {
            final List<Element> children = element.getChildren( elementName );
            if ( children == null )
            {
                return Collections.emptyList();
            }
            final List<XmlElement> xmlElements = new ArrayList<>();
            for ( final Element element : children )
            {
                xmlElements.add( new XmlElementJDOM( element ) );
            }
            return xmlElements;
        }

        @Override
        public String getText()
        {
            return element.getText();
        }

        @Override
        public String getTextTrim()
        {
            return element.getTextTrim();
        }

        @Override
        public String getChildText( final String elementName )
        {
            final XmlElement child = getChild( elementName );
            if ( child == null )
            {
                return null;
            }
            return child.getText();
        }

        @Override
        public Element asJdomElement()
        {
            return element;
        }
    }

    class XmlElementW3c implements XmlElement
    {
        private final org.w3c.dom.Element element;

        XmlElementW3c( final org.w3c.dom.Element element )
        {
            this.element = element;
        }

        @Override
        public XmlElement getChild( final String elementName )
        {
            final List<XmlElement> children = getChildren( elementName );
            if ( JavaHelper.isEmpty( children ) )
            {
                return null;
            }
            return children.iterator().next();
        }

        @Override
        public String getAttributeValue( final String attribute )
        {
            final String attrValue = element.getAttribute( attribute );
            return StringUtil.isEmpty( attrValue ) ? null : attrValue;
        }

        @Override
        public List<XmlElement> getChildren( final String elementName )
        {
            final NodeList nodeList = element.getElementsByTagName( elementName );
            return XmlFactory.XmlFactoryW3c.nodeListToElementList( nodeList );
        }

        @Override
        public String getText()
        {
            final String value = element.getTextContent();
            return StringUtil.isEmpty( value ) ? null : value;
        }

        @Override
        public String getTextTrim()
        {
            final String result = element.getTextContent();
            return result == null ? null : result.trim();
        }

        @Override
        public String getChildText( final String elementName )
        {
            final XmlElement child = getChild( elementName );
            if ( child == null )
            {
                return null;
            }
            return child.getText();
        }

        @Override
        public Element asJdomElement()
        {
            final DOMBuilder domBuilder = new DOMBuilder();
            return domBuilder.build( element );
        }
    }
}
