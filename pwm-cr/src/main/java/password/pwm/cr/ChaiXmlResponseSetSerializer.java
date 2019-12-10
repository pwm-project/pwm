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

package password.pwm.cr;

import net.iharder.Base64;
import org.jdom2.Attribute;
import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Text;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import password.pwm.cr.api.StoredChallengeItem;
import password.pwm.cr.api.StoredResponseItem;
import password.pwm.cr.api.StoredResponseSet;
import password.pwm.cr.api.ResponseLevel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ChaiXmlResponseSetSerializer
{

    public enum Type
    {
        USER,
        HELPDESK,
    }

    static final String SALT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    static final String XML_NODE_ROOT = "ResponseSet";
    static final String XML_ATTRIBUTE_MIN_RANDOM_REQUIRED = "minRandomRequired";
    static final String XML_ATTRIBUTE_LOCALE = "locale";


    static final String XML_NODE_RESPONSE = "response";
    static final String XML_NODE_HELPDESK_RESPONSE = "helpdesk-response";
    static final String XML_NODE_CHALLENGE = "challenge";
    static final String XML_NODE_ANSWER_VALUE = "answer";

    static final String XML_ATTRIBUTE_VERSION = "version";
    static final String XML_ATTRIBUTE_CHAI_VERSION = "chaiVersion";
    static final String XML_ATTRIBUTE_ADMIN_DEFINED = "adminDefined";
    static final String XML_ATTRIBUTE_REQUIRED = "required";
    static final String XML_ATTRIBUTE_HASH_COUNT = "hashcount";
    static final String XML_ATTRIBUTE_CONTENT_FORMAT = "format";
    static final String XML_ATTRIBUTE_SALT = "salt";
    static final String XNL_ATTRIBUTE_MIN_LENGTH = "minLength";
    static final String XNL_ATTRIBUTE_MAX_LENGTH = "maxLength";
    static final String XML_ATTRIBUTE_CASE_INSENSITIVE = "caseInsensitive";

    // identifier from challenge set.
    static final String XML_ATTRIBUTE_CHALLENGE_SET_IDENTIFER = "challengeSetID";
    static final String XML_ATTRIBUTE_TIMESTAMP = "time";

    static final String VALUE_VERSION = "pwmCR-1";


    public StoredResponseSet read( final Reader input, final Type type )
    {
        final Map<Type, StoredResponseSet> values = read( input );
        return values.get( type );
    }

    public Map<Type, StoredResponseSet> read( final Reader input )
    {
        if ( input == null )
        {
            throw new NullPointerException( "input can not be null" );
        }
        final List<StoredChallengeItem> crMap = new ArrayList<>();
        final List<StoredChallengeItem> helpdeskCrMap = new ArrayList<>();
        final int minRandRequired;
        final Attribute localeAttr;
        boolean caseInsensitive = false;
        String csIdentifier = null;
        Instant timestamp = null;

        try
        {
            final SAXBuilder builder = new SAXBuilder();
            final Document doc = builder.build( input );
            final Element rootElement = doc.getRootElement();
            minRandRequired = rootElement.getAttribute( XML_ATTRIBUTE_MIN_RANDOM_REQUIRED ).getIntValue();
            localeAttr = rootElement.getAttribute( XML_ATTRIBUTE_LOCALE );

            {
                final Attribute caseAttr = rootElement.getAttribute( XML_ATTRIBUTE_CASE_INSENSITIVE );
                if ( caseAttr != null && caseAttr.getBooleanValue() )
                {
                    caseInsensitive = true;
                }
            }

            {
                final Attribute csIdentiferAttr = rootElement.getAttribute( XML_ATTRIBUTE_CHALLENGE_SET_IDENTIFER );
                if ( csIdentiferAttr != null )
                {
                    csIdentifier = csIdentiferAttr.getValue();
                }
            }

            {
                final Attribute timeAttr = rootElement.getAttribute( XML_ATTRIBUTE_TIMESTAMP );
                if ( timeAttr != null )
                {
                    final String timeStr = timeAttr.getValue();
                    try
                    {
                        timestamp = CrUtils.parseDateString( timeStr );
                    }
                    catch ( final ParseException e )
                    {
                        throw new IllegalArgumentException( "unexpected error attempting to parse timestamp: " + e.getMessage() );
                    }
                }
            }

            for ( final Element loopResponseElement : rootElement.getChildren() )
            {
                final Type type = XML_NODE_HELPDESK_RESPONSE.equals( loopResponseElement.getName() )
                        ? Type.HELPDESK
                        : XML_NODE_RESPONSE.equals( loopResponseElement.getName() )
                        ? Type.USER
                        : null;
                if ( type != null )
                {
                    final StoredResponseItem storedResponseItem = parseAnswerElement( loopResponseElement.getChild( XML_NODE_ANSWER_VALUE ) );
                    if ( storedResponseItem != null )
                    {
                        final StoredChallengeItem storedChallengeItem = parseResponseElement( loopResponseElement, storedResponseItem );
                        switch ( type )
                        {
                            case USER:
                                crMap.add( storedChallengeItem );
                                break;

                            case HELPDESK:
                                helpdeskCrMap.add( storedChallengeItem );
                                break;

                            default:
                                throw new IllegalStateException( "unknown response type '" + type + '\'' );

                        }
                    }
                }
            }
        }
        catch ( final JDOMException | IOException | NullPointerException e )
        {
            throw new IllegalArgumentException( "error parsing stored response record: " + e.getMessage() );
        }

        final String strLocale = localeAttr != null ? localeAttr.getValue() : null;


        final Map<Type, StoredResponseSet> returnMap = new HashMap<>();
        {
            final StoredResponseSet userResponseSet = StoredResponseSet.builder()
                    .id( csIdentifier )
                    .caseSensitive( !caseInsensitive )
                    .minRandomsDuringResponse( minRandRequired )
                    .storedChallengeItems( Collections.unmodifiableList( crMap ) )
                    .locale( strLocale )
                    .timestamp( timestamp )
                    .build();
            returnMap.put( Type.USER, userResponseSet );
        }

        {
            final StoredResponseSet helpdeskStoredResponseSet = StoredResponseSet.builder()
                    .id( csIdentifier )
                    .caseSensitive( !caseInsensitive )
                    .minRandomsDuringResponse( minRandRequired )
                    .storedChallengeItems( Collections.unmodifiableList( helpdeskCrMap ) )
                    .locale( strLocale )
                    .timestamp( timestamp )
                    .build();
            returnMap.put( Type.HELPDESK, helpdeskStoredResponseSet );
        }


        return Collections.unmodifiableMap( returnMap );
    }

    private static String elementNameForType( final Type type )
    {
        switch ( type )
        {
            case USER:
                return XML_NODE_RESPONSE;

            case HELPDESK:
                return XML_NODE_HELPDESK_RESPONSE;

            default:
                throw new IllegalArgumentException( "unknown type '" + type + '\'' );
        }
    }

    private static StoredChallengeItem parseResponseElement(
            final Element responseElement,
            final StoredResponseItem storedResponseItem
    )

            throws DataConversionException
    {
        /*
        final boolean adminDefined = responseElement.getAttribute( XML_ATTRIBUTE_ADMIN_DEFINED ) != null
                && responseElement.getAttribute( XML_ATTRIBUTE_ADMIN_DEFINED ).getBooleanValue();

        final int minLength = responseElement.getAttribute( XNL_ATTRIBUTE_MIN_LENGTH ) == null
                ? 0
                : responseElement.getAttribute( XNL_ATTRIBUTE_MIN_LENGTH ).getIntValue();

        final int maxLength = responseElement.getAttribute( XNL_ATTRIBUTE_MAX_LENGTH ) == null
                ? 0
                : responseElement.getAttribute( XNL_ATTRIBUTE_MAX_LENGTH ).getIntValue();

                */

        final boolean required = responseElement.getAttribute( XML_ATTRIBUTE_REQUIRED ) != null
                && responseElement.getAttribute( XML_ATTRIBUTE_REQUIRED ).getBooleanValue();

        final String challengeText = responseElement.getChild( XML_NODE_CHALLENGE ) == null
                ? ""
                : responseElement.getChild( XML_NODE_CHALLENGE ).getText();

        return StoredChallengeItem.builder()
                .responseLevel( required ? ResponseLevel.REQUIRED : ResponseLevel.RANDOM )
                .questionText( challengeText )
                .id( makeId( challengeText ) )
                .answer( storedResponseItem )
                .build();
    }

    private static StoredResponseItem parseAnswerElement( final Element element )
    {
        final String answerValue = element.getText();
        final String salt = element.getAttribute( XML_ATTRIBUTE_SALT ) == null ? "" : element.getAttribute( XML_ATTRIBUTE_SALT ).getValue();
        final String hashCount = element.getAttribute( XML_ATTRIBUTE_HASH_COUNT ) == null ? "1" : element.getAttribute( XML_ATTRIBUTE_HASH_COUNT ).getValue();
        int saltCount = 1;

        try
        {
            saltCount = Integer.parseInt( hashCount );
        }
        catch ( final NumberFormatException e )
        {
            /* noop */
        }

        final String formatStr = element.getAttributeValue( XML_ATTRIBUTE_CONTENT_FORMAT ) == null ? "" : element.getAttributeValue( XML_ATTRIBUTE_CONTENT_FORMAT );

        return StoredResponseItem.builder()
                .format( formatStr )
                .salt( salt )
                .hash( answerValue )
                .iterations( saltCount )
                .build();
    }

    private static String makeId(
            final String questionText
    )
            throws IllegalStateException
    {
        final MessageDigest md;
        try
        {
            md = MessageDigest.getInstance( "SHA1" );
            final byte[] hashedBytes = md.digest( questionText.getBytes( StandardCharsets.UTF_8 ) );
            return net.iharder.Base64.encodeBytes( hashedBytes, Base64.URL_SAFE );
        }
        catch ( final NoSuchAlgorithmException | IOException e )
        {
            throw new IllegalStateException( "unable to load SHA1 message digest algorithm: " + e.getMessage() );
        }
    }


    public void write( final Writer writer, final Map<Type, StoredResponseSet> responseSets ) throws IOException
    {
        final StoredResponseSet rs = responseSets.get( Type.USER );
        if ( rs == null )
        {
            throw new IllegalArgumentException( "responseSet must contain user type responses" );
        }

        final Element rootElement = new Element( XML_NODE_ROOT );
        rootElement.setAttribute( XML_ATTRIBUTE_MIN_RANDOM_REQUIRED, String.valueOf( rs.getMinRandomsDuringResponse() ) );
        rootElement.setAttribute( XML_ATTRIBUTE_LOCALE, rs.getLocale().toString() );
        rootElement.setAttribute( XML_ATTRIBUTE_VERSION, VALUE_VERSION );
        rootElement.setAttribute( XML_ATTRIBUTE_CHAI_VERSION, VALUE_VERSION );

        if ( !rs.isCaseSensitive() )
        {
            rootElement.setAttribute( XML_ATTRIBUTE_CASE_INSENSITIVE, "true" );
        }

        if ( rs.getId() != null )
        {
            rootElement.setAttribute( XML_ATTRIBUTE_CHALLENGE_SET_IDENTIFER, rs.getId() );
        }

        if ( rs.getTimestamp() != null )
        {
            rootElement.setAttribute( XML_ATTRIBUTE_TIMESTAMP, CrUtils.formatDateString( rs.getTimestamp() ) );
        }

        attachChallenges( rootElement, rs.getStoredChallengeItems(), Type.USER );
        if ( responseSets.containsKey( Type.HELPDESK ) )
        {
            final List<StoredChallengeItem> helpdeskChallengeItems = responseSets.get( Type.HELPDESK ).getStoredChallengeItems();
            attachChallenges( rootElement, helpdeskChallengeItems, Type.HELPDESK );
        }


        final Document doc = new Document( rootElement );
        final XMLOutputter outputter = new XMLOutputter();
        final Format format = Format.getRawFormat();
        format.setTextMode( Format.TextMode.PRESERVE );
        format.setLineSeparator( "" );
        outputter.setFormat( format );
        outputter.output( doc, writer );
    }

    private static void attachChallenges(
            final Element parentElement,
            final List<StoredChallengeItem> storedChallengeItems,
            final Type type
    )
    {
        if ( storedChallengeItems == null )
        {
            return;
        }

        if ( storedChallengeItems != null )
        {
            for ( final StoredChallengeItem storedChallengeItem : storedChallengeItems )
            {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                final String responseElementName = elementNameForType( type );
                final Element responseElement = challengeToXml( storedChallengeItem, storedResponseItem, responseElementName );
                parentElement.addContent( responseElement );
            }
        }

    }

    private static Element challengeToXml(
            final StoredChallengeItem loopChallenge,
            final StoredResponseItem answer,
            final String elementName
    )
    {
        final Element responseElement = new Element( elementName );
        responseElement.addContent( new Element( XML_NODE_CHALLENGE ).addContent( new Text( loopChallenge.getQuestionText() ) ) );
        final Element answerElement = answerToXml( loopChallenge.getAnswer() );
        responseElement.addContent( answerElement );
        responseElement.setAttribute( XML_ATTRIBUTE_REQUIRED, Boolean.toString( loopChallenge.getResponseLevel() == ResponseLevel.REQUIRED ) );
        return responseElement;
    }

    private static Element answerToXml( final StoredResponseItem storedResponseItem )
    {
        final Element answerElement = new Element( XML_NODE_ANSWER_VALUE );
        answerElement.setText( storedResponseItem.getHash() );
        if ( storedResponseItem.getSalt() != null && !storedResponseItem.getSalt().isEmpty() )
        {
            answerElement.setAttribute( XML_ATTRIBUTE_SALT, storedResponseItem.getSalt() );
        }
        answerElement.setAttribute( XML_ATTRIBUTE_CONTENT_FORMAT, storedResponseItem.getFormat() );
        if ( storedResponseItem.getIterations() > 1 )
        {
            answerElement.setAttribute( XML_ATTRIBUTE_HASH_COUNT, String.valueOf( storedResponseItem.getIterations() ) );
        }
        return answerElement;
    }


}
