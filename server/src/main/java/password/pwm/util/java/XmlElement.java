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

import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Text;
import org.jdom2.input.DOMBuilder;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
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

    String getName();

    void setAttribute( String name, String value );

    void detach();

    void removeContent();

    void removeAttribute( String attributeName );

    void addContent( XmlElement element );

    void addContent( List<XmlElement> elements );

    void addText( String text );

    void setComment( List<String> textLines );

    List<XmlElement> getChildren();

    class XmlElementJDOM implements XmlElement
    {
        private final Element element;

        XmlElementJDOM( final Element element )
        {
            this.element = element;
        }

        @Override
        public String getName()
        {
            return element.getName();
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
        public List<XmlElement> getChildren()
        {
            return getChildren( null );
        }

        @Override
        public List<XmlElement> getChildren( final String elementName )
        {

            final List<Element> children = elementName == null
                    ? element.getChildren()
                    : element.getChildren( elementName );
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

        @Override
        public void setAttribute( final String name, final String value )
        {
            element.setAttribute( name, value );
        }

        @Override
        public void detach()
        {
            element.detach();
        }

        @Override
        public void removeContent()
        {
            element.removeContent();
        }

        @Override
        public void removeAttribute( final String attributeName )
        {
            element.removeAttribute( attributeName );
        }

        @Override
        public void addContent( final XmlElement element )
        {
            this.element.addContent( ( ( XmlElementJDOM) element ).element );
        }

        public void addContent( final List<XmlElement> elements )
        {
            for ( final XmlElement loopElement : elements )
            {
                final Element jdomElement = ( ( XmlElementJDOM ) loopElement ).element;
                this.element.addContent( jdomElement );
            }
        }

        @Override
        public void addText( final String text )
        {
            element.addContent( new Text( text ) );
        }

        @Override
        public void setComment( final List<String> textLines )
        {
            final List<Content> contentList = new ArrayList<>( element.getContent() );
            for ( final Content content : contentList )
            {
                if ( content instanceof Comment )
                {
                    content.detach();
                }
            }

            final List<String> reversedList = new ArrayList<>( textLines );
            Collections.reverse( reversedList );
            for ( final String text : textLines )
            {
                element.addContent( 0, new Comment( text ) );
            }
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
        public String getName()
        {
            return element.getTagName();
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
        public List<XmlElement> getChildren()
        {
            final NodeList nodeList = element.getChildNodes();
            return XmlFactory.XmlFactoryW3c.nodeListToElementList( nodeList );
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

        @Override
        public void setAttribute( final String name, final String value )
        {
            element.setAttribute( name, value );
        }

        @Override
        public void detach()
        {
            element.getParentNode().removeChild( element );
        }

        @Override
        public void removeContent()
        {
            final NodeList nodeList = element.getChildNodes();
            for ( final XmlElement child : XmlFactory.XmlFactoryW3c.nodeListToElementList( nodeList ) )
            {
                element.removeChild( ( (XmlElementW3c) child ).element );
            }
        }

        @Override
        public void removeAttribute( final String attributeName )
        {
            element.removeAttribute( attributeName );
        }

        @Override
        public void addContent( final XmlElement element )
        {
            final org.w3c.dom.Element w3cElement = ( ( XmlElementW3c ) element ).element;
            this.element.getOwnerDocument().adoptNode( w3cElement );
            this.element.appendChild( w3cElement );
        }

        public void addContent( final List<XmlElement> elements )
        {
            for ( final XmlElement element : elements )
            {
                final org.w3c.dom.Element w3cElement = ( ( XmlElementW3c ) element ).element;
                this.element.getOwnerDocument().adoptNode( w3cElement );
                this.element.appendChild( w3cElement );
            }
        }

        @Override
        public void addText( final String text )
        {
            final DocumentBuilder documentBuilder = XmlFactory.XmlFactoryW3c.getBuilder();
            final org.w3c.dom.Document document = documentBuilder.newDocument();
            final org.w3c.dom.Text textNode = document.createTextNode( text );
            this.element.getOwnerDocument().adoptNode( textNode );
            element.appendChild( textNode );
        }

        @Override
        public void setComment( final List<String> textLines )
        {
            final NodeList nodeList = element.getChildNodes();
            for ( int i = 0; i < nodeList.getLength(); i++ )
            {
                final Node node = nodeList.item( i );
                if ( node.getNodeType() == Node.COMMENT_NODE )
                {
                    element.removeChild( node );
                }
            }

            final DocumentBuilder documentBuilder = XmlFactory.XmlFactoryW3c.getBuilder();
            final org.w3c.dom.Document document = documentBuilder.newDocument();

            final List<String> reversedList = new ArrayList<>( textLines );
            Collections.reverse( reversedList );
            for ( final String text : reversedList )
            {
                final org.w3c.dom.Comment textNode = document.createComment( text );
                this.element.getOwnerDocument().adoptNode( textNode );

                if ( element.hasChildNodes() )
                {
                    element.insertBefore( textNode, element.getFirstChild() );
                }
                else
                {
                    element.appendChild( textNode );
                }

            }
        }
    }
}
