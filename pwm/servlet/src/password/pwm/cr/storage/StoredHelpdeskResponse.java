/*
 * LDAP Chai API
 * Copyright (c) 2006-2010 Novell, Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.cr.storage;

import com.novell.ldapchai.cr.bean.AnswerBean;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.util.internal.Base64Util;
import org.jdom2.Element;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class StoredHelpdeskResponse implements StoredResponse {
    private final String challengeText;
    private final String answer;

    StoredHelpdeskResponse(final String answer, final String challengeText) {
        if (answer == null || answer.length() < 1) {
            throw new IllegalArgumentException("missing answer text");
        }

        this.answer = answer;
        this.challengeText = challengeText;
    }

    public String answerText() {
        return answer;
    }

    public Element toXml() throws ChaiOperationException {
        final Element answerElement = new Element(CrStorageXmlParser.XML_NODE_ANSWER_VALUE);
        answerElement.setText(encryptValue(answer, challengeText));
        answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT, StoredResponseFormatType.HELPDESK.toString());
        return answerElement;
    }

    public boolean testAnswer(final String testResponse) {
        if (testResponse == null) {
            return false;
        }

        return answer.equalsIgnoreCase(testResponse);
    }

    private static String encryptValue(final String value, final String key)
            throws ChaiOperationException
    {
        try {
            if (value == null || value.length() < 1) {
                return "";
            }

            final SecretKey secretKey = makeKey(key);
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, cipher.getParameters());
            final byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64Util.encodeBytes(encrypted, Base64Util.URL_SAFE | Base64Util.GZIP);
        } catch (Exception e) {
            final String errorMsg = "unexpected error performing helpdesk answer crypt operation: " + e.getMessage();
            throw new ChaiOperationException(errorMsg,ChaiError.CHAI_INTERNAL_ERROR);
        }
    }

    private static String decryptValue(final String value, final String key)
    {
        try {
            if (value == null || value.length() < 1) {
                return "";
            }

            final SecretKey secretKey = makeKey(key);
            final byte[] decoded = Base64Util.decode(value, Base64Util.URL_SAFE | Base64Util.GZIP);
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            final byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        } catch (Exception e) {
            final String errorMsg = "unexpected error performing helpdesk answer decrypt operation: " + e.getMessage();
            throw new IllegalArgumentException(errorMsg);
        }
    }

    private static SecretKey makeKey(final String text)
            throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        final MessageDigest md = MessageDigest.getInstance("SHA1");
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        final byte[] key = new byte[16];
        System.arraycopy(md.digest(), 0, key, 0, 16);
        return new SecretKeySpec(key, "AES");
    }

    static class StoredHelpdeskResponseFactory implements ImplementationFactory {
        public StoredResponse newStoredResponse(StoredResponseFactory.AnswerConfiguration answerConfiguration, String answerText) {
            return new StoredHelpdeskResponse(answerText, answerConfiguration.getChallengeText());
        }

        public StoredResponse fromAnswerBean(AnswerBean input, String challengeText) {
            return new StoredHelpdeskResponse(input.answerText, challengeText);
        }

        public StoredResponse fromXml(final Element element, final boolean caseInsensitive, final String challengeText) {
            final String hashedAnswer = element.getText();
            final String answerValue = decryptValue(hashedAnswer, challengeText);
            return new StoredHelpdeskResponse(answerValue,challengeText);
        }

    }
}
