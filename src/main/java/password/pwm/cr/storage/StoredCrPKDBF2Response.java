package password.pwm.cr.storage;

import com.novell.ldapchai.util.internal.Base64Util;
import org.jdom2.Element;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;

class StoredCrPKDBF2Response implements StoredResponse {
    protected final String answerHash;
    protected final String salt;
    protected final int hashCount;
    protected final boolean caseInsensitive;

    StoredCrPKDBF2Response(
            final String answerHash,
            final String salt,
            final int hashCount,
            final boolean caseInsensitive
    ) {
        if (answerHash == null || answerHash.length() < 1) {
            throw new IllegalArgumentException("missing answerHash");
        }

        this.answerHash = answerHash;
        this.salt = salt;
        this.hashCount = hashCount;
        this.caseInsensitive = caseInsensitive;
    }

    StoredCrPKDBF2Response(final StoredResponseFactory.AnswerConfiguration answerConfiguration, final String answer) {
        this.hashCount = answerConfiguration.hashCount;
        this.caseInsensitive = answerConfiguration.caseInsensitive;
        this.salt = generateSalt(32);

        if (answer == null || answer.length() < 1) {
            throw new IllegalArgumentException("missing answerHash text");
        }

        { // make hash
            final String casedAnswer = caseInsensitive ? answer.toLowerCase() : answer;
            this.answerHash = hashValue(casedAnswer);
        }

    }

    public Element toXml() {
        final Element answerElement = new Element(CrStorageXmlParser.XML_NODE_ANSWER_VALUE);
        answerElement.setText(answerHash);
        if (salt != null && salt.length() > 0) {
            answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_SALT,salt);
        }
        answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT, StoredResponseFormatType.PBKDF2.toString());
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
        final String hashedTest = hashValue(casedResponse);
        return answerHash.equalsIgnoreCase(hashedTest);
    }

    protected String hashValue(final String input) {
        try {
            final char[] chars = input.toCharArray();
            final byte[] saltBytes = salt.getBytes("UTF-8");
            final PBEKeySpec spec = new PBEKeySpec(chars, saltBytes, hashCount, 64 * 8);
            final SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            final byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64Util.encodeBytes(hash);
        } catch (Exception e) {
            throw new IllegalStateException("unable to perform PBKDF2 hashing operation: " + e.getMessage());
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

    static class PKDBF2AnswerFactory implements ImplementationFactory {
        public StoredCrPKDBF2Response newStoredResponse(
                final StoredResponseFactory.AnswerConfiguration answerConfiguration,
                final String answer
        ) {
            return new StoredCrPKDBF2Response(answerConfiguration, answer);
        }

        public StoredCrPKDBF2Response fromXml(final Element element, final boolean caseInsensitive, final String challengeText) {
            final String answerValue = element.getText();

            if (answerValue == null || answerValue.length() < 1) {
                throw new IllegalArgumentException("missing answer value");
            }

            final String salt = element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_SALT) == null ? "" : element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_SALT).getValue();
            final String hashCount = element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_HASH_COUNT) == null ? "1" : element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_HASH_COUNT).getValue();
            int saltCount = 1;
            try { saltCount = Integer.parseInt(hashCount); } catch (NumberFormatException e) { /* noop */ }
            return new StoredCrPKDBF2Response(answerValue,salt,saltCount,caseInsensitive);
        }
    }
}
