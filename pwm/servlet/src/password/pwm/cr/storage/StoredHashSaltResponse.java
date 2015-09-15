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

import com.novell.ldapchai.util.internal.Base64Util;
import org.jdom2.Element;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class StoredHashSaltResponse implements StoredResponse {
    private static final Map<StoredResponseFormatType,String> supportedFormats;
    private static final String VERSION_SEPARATOR = ":";
    private static final VERSION DEFAULT_VERSION = VERSION.B;

    protected final String answerHash;
    protected final String salt;
    protected final int hashCount;
    protected final boolean caseInsensitive;
    protected final StoredResponseFormatType storedResponseFormatType;
    protected final VERSION version;

    enum VERSION {
        A, // original version had bug where only one iteration was ever actually performed regardless of hashCount value
        B, // nominal working version
    }

    static {
        final Map<StoredResponseFormatType,String> map = new HashMap<StoredResponseFormatType,String>();
        map.put(StoredResponseFormatType.MD5,"MD5");
        map.put(StoredResponseFormatType.SHA1,"SHA1");
        map.put(StoredResponseFormatType.SHA1_SALT,"SHA1");
        map.put(StoredResponseFormatType.SHA256_SALT,"SHA-256");
        map.put(StoredResponseFormatType.SHA512_SALT,"SHA-512");
        supportedFormats = Collections.unmodifiableMap(map);
    }

    StoredHashSaltResponse(
            final String answerHash,
            final String salt,
            final int hashCount,
            final boolean caseInsensitive,
            final StoredResponseFormatType storedResponseFormatType,
            final VERSION version
    ) {
        if (answerHash == null || answerHash.length() < 1) {
            throw new IllegalArgumentException("missing answerHash");
        }

        if (storedResponseFormatType == null || !supportedFormats.containsKey(storedResponseFormatType)) {
            throw new IllegalArgumentException("unsupported format type '" + (storedResponseFormatType == null ? "null" : storedResponseFormatType.toString() + "'"));
        }

        this.answerHash = answerHash;
        this.version = version;
        this.storedResponseFormatType = storedResponseFormatType;
        this.salt = salt;
        this.hashCount = hashCount;
        this.caseInsensitive = caseInsensitive;
    }

    StoredHashSaltResponse(final StoredResponseFactory.AnswerConfiguration answerConfiguration, final String answer) {
        this.hashCount = answerConfiguration.hashCount;
        this.caseInsensitive = answerConfiguration.caseInsensitive;
        this.storedResponseFormatType = answerConfiguration.storedResponseFormatType;
        this.version = DEFAULT_VERSION;

        if (answer == null || answer.length() < 1) {
            throw new IllegalArgumentException("missing answerHash text");
        }

        if (storedResponseFormatType == null || !supportedFormats.containsKey(storedResponseFormatType)) {
            throw new IllegalArgumentException("unsupported format type '" + (storedResponseFormatType == null ? "null" : storedResponseFormatType.toString() + "'"));
        }

        { // make hash
            final boolean includeSalt = storedResponseFormatType.toString().contains("SALT");
            final String casedAnswer = caseInsensitive ? answer.toLowerCase() : answer;
            this.salt = includeSalt ? generateSalt(32) : "";
            final String saltedAnswer = includeSalt ? salt + casedAnswer : casedAnswer;
            this.answerHash = hashValue(saltedAnswer);
        }

    }

    public Element toXml() {
        final Element answerElement = new Element(CrStorageXmlParser.XML_NODE_ANSWER_VALUE);
        answerElement.setText(version.toString() + VERSION_SEPARATOR + answerHash);
        if (salt != null && salt.length() > 0) {
            answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_SALT,salt);
        }
        answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT, storedResponseFormatType.toString());
        if (hashCount > 1) {
            answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_HASH_COUNT,String.valueOf(hashCount));
        }
        return answerElement;
    }


    public boolean testAnswer(final String testResponse) {
        if (testResponse == null) {
            return false;
        }

        final String casedResponse = caseInsensitive ? testResponse.toLowerCase() : testResponse;
        final String saltedTest = salt + casedResponse;
        final String hashedTest = hashValue(saltedTest);
        return answerHash.equalsIgnoreCase(hashedTest);
    }

    protected String hashValue(final String input) {
        return doHash(input, hashCount, storedResponseFormatType, version);
    }

    static String doHash(
            final String input,
            final int hashCount,
            final StoredResponseFormatType storedResponseFormatType,
            final VERSION version
    )
            throws IllegalStateException
    {
        final String algorithm = supportedFormats.get(storedResponseFormatType);
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("unable to load " + algorithm + " message digest algorithm: " + e.getMessage());
        }


        byte[] hashedBytes = input.getBytes();
        switch (version) {
            case A:
                hashedBytes = md.digest(hashedBytes);
                return Base64Util.encodeBytes(hashedBytes);

            case B:
                for (int i = 0; i < hashCount; i++) {
                    hashedBytes = md.digest(hashedBytes);
                }
                return Base64Util.encodeBytes(hashedBytes);

            default:
                throw new IllegalStateException("unexpected version enum in hash method");
        }
    }


    private static String generateSalt(final int length)
    {
        final SecureRandom random = new SecureRandom();
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CrStorageXmlParser.SALT_CHARS.charAt(random.nextInt(CrStorageXmlParser.SALT_CHARS.length())));
        }
        return sb.toString();
    }

    static class HashSaltAnswerFactory implements ImplementationFactory {
        public StoredHashSaltResponse newStoredResponse(
                final StoredResponseFactory.AnswerConfiguration answerConfiguration,
                final String answer
        ) {
            return new StoredHashSaltResponse(answerConfiguration, answer);
        }


        public StoredHashSaltResponse fromXml(final Element element, final boolean caseInsensitive, final String challengeText) {
            final String answerValue = element.getText();

            if (answerValue == null || answerValue.length() < 1) {
                throw new IllegalArgumentException("missing answer value");
            }

            final String hashString;
            final VERSION version;
            if (answerValue.contains(VERSION_SEPARATOR)) {
                final String[] s = answerValue.split(VERSION_SEPARATOR);
                try {
                    version = VERSION.valueOf(s[0]);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("unsupported version type " + s[0]);
                }
                hashString = s[1];
            } else {
                version = VERSION.A;
                hashString = answerValue;
            }

            final String salt = element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_SALT) == null
                    ? ""
                    : element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_SALT).getValue();
            final String hashCount = element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_HASH_COUNT) == null
                    ? "1"
                    : element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_HASH_COUNT).getValue();
            final int saltCount;
            try {
                saltCount = Integer.parseInt(hashCount);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("error parsing stored response, hash count parse error: " + e.getMessage());
            }
            final String formatStr = element.getAttributeValue(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT) == null ? "" : element.getAttributeValue(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT);
            final StoredResponseFormatType storedResponseFormatType;
            try {
                storedResponseFormatType = StoredResponseFormatType.valueOf(formatStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown content format specified in xml format value: '" + formatStr + "'");
            }
            return new StoredHashSaltResponse(hashString,salt,saltCount,caseInsensitive, storedResponseFormatType,version);
        }
    }
}
