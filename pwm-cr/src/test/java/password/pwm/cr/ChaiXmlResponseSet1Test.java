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

public class ChaiXmlResponseSet1Test {

    @Test
    public void testReadingStoredChaiXmlChallengeSet() throws IOException {
        /*
        final Reader reader = readInputXmlFile();
        StoredResponseSet storedResponseSet = new ChaiXmlResponseSetSerializer().read(reader, ChaiXmlResponseSetSerializer.Type.USER);

        testUserResponseSetValidity(storedResponseSet);
        */
    }


    @Test
    public void testReadingStoredChaiHelpdeskXmlChallengeSet() throws IOException {
        final Reader reader = readInputXmlFile();
        StoredResponseSet storedResponseSet = new ChaiXmlResponseSetSerializer().read(reader, ChaiXmlResponseSetSerializer.Type.HELPDESK);

        testHelpdeskResponseSetValidity(storedResponseSet);
    }

    @Test
    public void testReadWriteRead() throws IOException {
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

    private static Reader readInputXmlFile() {
        return new InputStreamReader(ChaiXmlResponseSet1Test.class.getResourceAsStream("ChaiXmlResponseSet1.xml"), Charset.forName("UTF8"));
    }


    private void testUserResponseSetValidity(final StoredResponseSet storedResponseSet) {
        Assert.assertEquals(4, storedResponseSet.getStoredChallengeItems().size());
        Assert.assertEquals(4, StoredItemUtils.filterStoredChallenges(storedResponseSet.getStoredChallengeItems(), ResponseLevel.RANDOM).size());

        for (final StoredChallengeItem storedChallengeItem : storedResponseSet.getStoredChallengeItems()) {
            final String questionText = storedChallengeItem.getQuestionText();
            if ("What is the name of the main character in your favorite book?".equals(questionText)) {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue(HashFactory.testResponseItem(storedResponseItem, "book"));
                Assert.assertFalse(HashFactory.testResponseItem(storedResponseItem, "wrong answer"));
            }

            if ("What is the name of your favorite teacher?".equals(questionText)) {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue(HashFactory.testResponseItem(storedResponseItem, "teacher"));
                Assert.assertFalse(HashFactory.testResponseItem(storedResponseItem, "wrong answer"));
            }

            if ("What was the name of your childhood best friend?".equals(questionText)) {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue(HashFactory.testResponseItem(storedResponseItem, "friend"));
                Assert.assertFalse(HashFactory.testResponseItem(storedResponseItem, "wrong answer"));
            }

            if ("What was your favorite show as a child?".equals(questionText)) {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();
                Assert.assertTrue(HashFactory.testResponseItem(storedResponseItem, "child"));
                Assert.assertFalse(HashFactory.testResponseItem(storedResponseItem, "wrong answer"));
            }
        }

    }

    private void testHelpdeskResponseSetValidity(final StoredResponseSet storedResponseSet) {
        Assert.assertEquals(2, storedResponseSet.getStoredChallengeItems().size());

        for (final StoredChallengeItem storedChallengeItem : storedResponseSet.getStoredChallengeItems()) {
            final String questionText = storedChallengeItem.getQuestionText();
            if ("What is the name of the main character in your favorite book?".equals(questionText)) {
                final StoredResponseItem storedResponseItem = storedChallengeItem.getAnswer();

            }
        }
    }

}
