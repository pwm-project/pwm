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
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public interface XmlFactory
{
    enum FactoryType
    {
        JDOM,
        W3C,
    }

    XmlDocument parseXml( InputStream inputStream )
            throws PwmUnrecoverableException;

    void outputDocument( XmlDocument document, OutputStream outputStream )
            throws IOException;

    XmlDocument newDocument( String rootElementName );

    XmlElement newElement( String name );

    static XmlFactory getFactory()
    {
        return new XmlFactoryJDOM();
    }

    static XmlFactory getFactory( final FactoryType factoryType )
    {
        switch ( factoryType )
        {
            case JDOM:
                return new XmlFactoryJDOM();

            case W3C:
                return new XmlFactoryW3c();

            default:
                JavaHelper.unhandledSwitchStatement( factoryType );

        }

        return null;
    }


    class XmlFactoryJDOM implements XmlFactory
    {
        private static final Charset STORAGE_CHARSET = Charset.forName( "UTF8" );

        XmlFactoryJDOM()
        {
        }

        @Override
        public XmlDocument parseXml( final InputStream inputStream )
                throws PwmUnrecoverableException
        {
            final SAXBuilder builder = getBuilder();
            final Document inputDocument;
            try
            {
                inputDocument = builder.build( inputStream );
            }
            catch ( Exception e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                "error parsing xml data: " + e.getMessage(),
                        }
                ) );
            }
            return new XmlDocument.XmlDocumentJDOM( inputDocument );
        }

        public static void outputJDOMDocument( final Document document, final OutputStream outputStream )
                throws IOException
        {
            new XmlFactoryJDOM().outputDocument( document, outputStream );
        }

        @Override
        public void outputDocument( final XmlDocument document, final OutputStream outputStream )
                throws IOException
        {
            final Document jdomDoc =  ( ( XmlDocument.XmlDocumentJDOM )  document ).document;
            this.outputDocument( jdomDoc, outputStream );
        }

        private void outputDocument( final Document document, final OutputStream outputStream )
                throws IOException
        {
            final Format format = Format.getPrettyFormat();
            format.setEncoding( STORAGE_CHARSET.toString() );
            final XMLOutputter outputter = new XMLOutputter();
            outputter.setFormat( format );

            try ( Writer writer = new OutputStreamWriter( outputStream, STORAGE_CHARSET ) )
            {
                outputter.output( document, writer );
            }
        }

        private static SAXBuilder getBuilder( )
        {
            final SAXBuilder builder = new SAXBuilder();
            builder.setExpandEntities( false );
            builder.setXMLReaderFactory( XMLReaders.NONVALIDATING );
            builder.setFeature( "http://xml.org/sax/features/resolve-dtd-uris", false );
            return builder;
        }

        @Override
        public XmlDocument newDocument( final String rootElementName )
        {
            final org.jdom2.Element rootElement = new org.jdom2.Element( rootElementName );
            final org.jdom2.Document newDoc = new org.jdom2.Document( rootElement );
            return new XmlDocument.XmlDocumentJDOM( newDoc );
        }

        @Override
        public XmlElement newElement( final String name )
        {
            return new XmlElement.XmlElementJDOM( new org.jdom2.Element ( name ) );
        }
    }

    class XmlFactoryW3c implements XmlFactory
    {
        private static final Charset STORAGE_CHARSET = Charset.forName( "UTF8" );

        XmlFactoryW3c()
        {
        }

        @Override
        public XmlDocument parseXml( final InputStream inputStream )
                throws PwmUnrecoverableException
        {
            final org.w3c.dom.Document inputDocument;
            try
            {
                final DocumentBuilder builder = getBuilder();
                inputDocument = builder.parse( inputStream );
            }
            catch ( Exception e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                "error parsing xml data: " + e.getMessage(),
                        }
                ) );
            }
            return new XmlDocument.XmlDocumentW3c( inputDocument );
        }

        static DocumentBuilder getBuilder()
        {
            try
            {
                final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                dbFactory.setFeature( "http://apache.org/xml/features/disallow-doctype-decl", false );
                dbFactory.setExpandEntityReferences( false );
                dbFactory.setValidating( false );
                dbFactory.setXIncludeAware( false );
                dbFactory.setExpandEntityReferences( false );
                return dbFactory.newDocumentBuilder();
            }
            catch ( ParserConfigurationException e )
            {
                throw new IllegalArgumentException( "unable to generate dom xml builder: " + e.getMessage() );
            }
        }

        @Override
        public void outputDocument( final XmlDocument document, final OutputStream outputStream )
                throws IOException
        {
            try
            {
                final Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.setOutputProperty( OutputKeys.INDENT, "yes" );
                tr.setOutputProperty( OutputKeys.METHOD, "xml" );
                tr.setOutputProperty( OutputKeys.ENCODING, STORAGE_CHARSET.toString() );
                tr.transform( new DOMSource( ( ( XmlDocument.XmlDocumentW3c ) document ).document ), new StreamResult( outputStream ) );
            }
            catch ( TransformerException e )
            {
                throw new IOException( "error loading xml transformer: " + e.getMessage() );
            }
        }

        static List<XmlElement> nodeListToElementList( final NodeList nodeList )
        {
            final List<XmlElement> returnList = new ArrayList<>();
            if ( nodeList != null )
            {
                for ( int i = 0; i < nodeList.getLength(); i++ )
                {
                    final Node node = nodeList.item( i );
                    if ( node.getNodeType() == Node.ELEMENT_NODE )
                    {
                        returnList.add( new XmlElement.XmlElementW3c( ( org.w3c.dom.Element ) node ) );
                    }
                }
                return returnList;
            }
            return null;
        }

        @Override
        public XmlDocument newDocument( final String rootElementName )
        {
            final DocumentBuilder documentBuilder = getBuilder();
            final org.w3c.dom.Document document = documentBuilder.newDocument();
            final org.w3c.dom.Element rootElement = document.createElement( rootElementName );
            document.appendChild( rootElement );
            return new XmlDocument.XmlDocumentW3c( document );
        }

        @Override
        public XmlElement newElement( final String name )
        {
            final DocumentBuilder documentBuilder = getBuilder();
            final org.w3c.dom.Document document = documentBuilder.newDocument();
            final org.w3c.dom.Element element = document.createElement( name );
            return new XmlElement.XmlElementW3c( element );
        }

    }
}
