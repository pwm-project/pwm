/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public interface XmlFactory
{
    enum OutputFlag
    {
        Compact,
    }

    XmlDocument parseXml( InputStream inputStream )
            throws PwmUnrecoverableException;

    void outputDocument( XmlDocument document, OutputStream outputStream, OutputFlag... outputFlags )
            throws IOException;

    XmlDocument newDocument( String rootElementName );

    XmlElement newElement( String name );

    static XmlFactory getFactory()
    {
        return XmlFactoryW3c.getW3cFactory();
    }

    class XmlFactoryW3c implements XmlFactory
    {
        private static final XmlFactory W3C_FACTORY = new XmlFactoryW3c();
        private static final Charset STORAGE_CHARSET = StandardCharsets.UTF_8;

        private XmlFactoryW3c()
        {
        }

        static XmlFactory getW3cFactory()
        {
            return W3C_FACTORY;
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
            catch ( final Exception e )
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
            catch ( final ParserConfigurationException e )
            {
                throw new IllegalArgumentException( "unable to generate dom xml builder: " + e.getMessage() );
            }
        }

        @Override
        public void outputDocument( final XmlDocument document, final OutputStream outputStream, final OutputFlag... outputFlags )
                throws IOException
        {
            final Lock lock = ( ( XmlDocument.XmlDocumentW3c ) document ).lock;
            final boolean compact = JavaHelper.enumArrayContainsValue( outputFlags, OutputFlag.Compact );

            lock.lock();
            try
            {
                final Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.setOutputProperty( OutputKeys.INDENT, compact ? "no" : "yes" );
                tr.setOutputProperty( OutputKeys.METHOD, "xml" );
                tr.setOutputProperty( OutputKeys.ENCODING, STORAGE_CHARSET.toString() );

                tr.transform( new DOMSource( ( ( XmlDocument.XmlDocumentW3c ) document ).document ), new StreamResult( outputStream ) );
            }
            catch ( final TransformerException e )
            {
                throw new IOException( "error loading xml transformer: " + e.getMessage() );
            }
            finally
            {
                lock.unlock();
            }
        }

        static List<XmlElement> nodeListToElementList( final NodeList nodeList, final Lock lock )
        {
            final List<XmlElement> returnList = new ArrayList<>();
            if ( nodeList != null )
            {
                for ( int i = 0; i < nodeList.getLength(); i++ )
                {
                    final Node node = nodeList.item( i );
                    if ( node.getNodeType() == Node.ELEMENT_NODE )
                    {
                        returnList.add( new XmlElement.XmlElementW3c( ( org.w3c.dom.Element ) node, lock ) );
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
            document.setXmlStandalone( true );
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
            return new XmlElement.XmlElementW3c( element, new ReentrantLock() );
        }
    }
}
