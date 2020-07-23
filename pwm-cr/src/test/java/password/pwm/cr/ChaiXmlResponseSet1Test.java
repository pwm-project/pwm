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

import org.junit.Assert;
import org.junit.Test;
import password.pwm.cr.api.ResponseLevel;
import password.pwm.cr.api.StoredChallengeItem;
import password.pwm.cr.api.StoredResponseItem;
import password.pwm.cr.api.StoredResponseSet;
import password.pwm.cr.hash.HashFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

@SuppressWarnings( "checkstyle:MultipleStringLiterals" )
public class ChaiXmlResponseSet1Test
{

    @Test
    public void testReadingStoredChaiXmlChallengeSet()
            throws IOException
    {
        /*
        final Reader reader = readInputXmlFile();
        StoredResponseSet storedResponseSet = new ChaiXmlResponseSetSerializer().read(reader, ChaiXmlResponseSetSerializer.Type.USER);

        testUserResponseSetValidity(storedResponseSet);
        */
    }


    @Test
    public void testReadingStoredChaiHelpdeskXmlChallengeSet() throws IOException
    {
        final Reader reader = readInputXmlFile();
        final StoredResponseSet storedResponseSet = new ChaiXmlResponseSetSerializer().read( reader, ChaiXmlResponseSetSerializer.Type.HELPDESK );

        testHelpdeskResponseSetValidity( storedResponseSet );
    }

    @Test
    public void testReadWriteRead() throws IOException
    {
        /*
        final ChaiXmlResponseSetSerializer chaiXmlResponseSetSerializer = new ChaiXmlResponseSetSerializer();


        final Map<ChaiXmlResponseSetSerializer.Type,StoredResponseSet> firstResponsesRead;
        {
            final Reader reader = readInputXmlFile();
            firstResponsesRead = chaiXmlResponseSetSerializer.read(reader);
        }

        final String firstResponsesWritten;
        {
            final StringWriter writer = new StringWriter();
            new ChaiXmlResponseSetSerializer().write(writer, firstResponsesRead);
            firstResponsesWritten = writer.toString();
        }

        final Map<ChaiXmlResponseSetSerializer.Type,StoredResponseSet> secondResponsesRead;
        {
            final Reader reader = new StringReader(firstResponsesWritten);
            secondResponsesRead = chaiXmlResponseSetSerializer.read(reader);
        }

        testUserResponseSetValidity(secondResponsesRead.get(ChaiXmlResponseSetSerializer.Type.USER));
        testHelpdeskResponseSetValidity(secondResponsesRead.get(ChaiXmlResponseSetSerializer.Type.HELPDESK));
        */
    }

    private static Reader readInputXmlFile()
    {
        return new InputStreamReader( ChaiXmlResponseSet1Test.class.getResourceAsStream( "ChaiXmlResponseSet1.xml" ), Charset.forName( "UTF8" ) );
    }


    private void testUserResponseSetValidity( final StoredResponseSet storedResponseSet )
    {
        Assert.assertEquals( 4, storedResponseSet.getStoredChallengeItems().size() );
        Assert.assertEquals( 4, StoredItemUtils.filterStoredChallenges( storedResponseSet.getStoredChallengeItems(), ResponseLevel.RANDOM ).size() );

        for ( final StoredChallengeItem storedChallengeItem : storedResponseSet.getStoredChallengeItems() )
        {
            final String questionText = storedChallengeItem.getQuestionText();
            if ( "What is the name of the main character in your favorite book?".equals( questionText ) )
            {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue( HashFactory.testResponseItem( storedResponseItem, "book" ) );
                Assert.assertFalse( HashFactory.testResponseItem( storedResponseItem, "wrong answer" ) );
            }

            if ( "What is the name of your favorite teacher?".equals( questionText ) )
            {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue( HashFactory.testResponseItem( storedResponseItem, "teacher" ) );
                Assert.assertFalse( HashFactory.testResponseItem( storedResponseItem, "wrong answer" ) );
            }

            if ( "What was the name of your childhood best friend?".equals( questionText ) )
            {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue( HashFactory.testResponseItem( storedResponseItem, "friend" ) );
                Assert.assertFalse( HashFactory.testResponseItem( storedResponseItem, "wrong answer" ) );
            }

            if ( "What was your favorite show as a child?".equals( questionText ) )
            {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue( HashFactory.testResponseItem( storedResponseItem, "child" ) );
                Assert.assertFalse( HashFactory.testResponseItem( storedResponseItem, "wrong answer" ) );
            }
        }

    }

    private void testHelpdeskResponseSetValidity( final StoredResponseSet storedResponseSet )
    {
        Assert.assertEquals( 2, storedResponseSet.getStoredChallengeItems().size() );

        for ( final StoredChallengeItem storedChallengeItem : storedResponseSet.getStoredChallengeItems() )
        {
            final String questionText = storedChallengeItem.getQuestionText();
            if ( "What is the name of the main character in your favorite book?".equals( questionText ) )
            {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();

            }
        }
    }

}
