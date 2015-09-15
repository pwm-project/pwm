package password.pwm.cr.storage;

import com.novell.ldapchai.util.BCrypt;
import com.novell.ldapchai.util.SCrypt;
import org.jdom2.Element;

class StoredCrCryptResponse implements StoredResponse {
    private final String answerHash;
    private final boolean caseInsensitive;
    private final StoredResponseFormatType storedResponseFormatType;

    private StoredCrCryptResponse(final String answerHash, final boolean caseInsensitive, final StoredResponseFormatType storedResponseFormatType) {
        if (answerHash == null || answerHash.length() < 1) {
            throw new IllegalArgumentException("missing answer text");
        }

        this.answerHash = answerHash;
        this.caseInsensitive = caseInsensitive;
        this.storedResponseFormatType = storedResponseFormatType;
    }

    private StoredCrCryptResponse(final StoredResponseFactory.AnswerConfiguration answerConfiguration, final String answer) {
        if (answer == null || answer.length() < 1) {
            throw new IllegalArgumentException("missing answerHash text");
        }

        this.caseInsensitive = answerConfiguration.isCaseInsensitive();
        this.storedResponseFormatType = answerConfiguration.storedResponseFormatType;
        final String casedAnswer = caseInsensitive ? answer.toLowerCase() : answer;
        switch (storedResponseFormatType) {
            case BCRYPT:
                answerHash = BCrypt.hashpw(casedAnswer, BCrypt.gensalt());
                break;

            case SCRYPT:
                answerHash = SCrypt.scrypt(casedAnswer);
                break;

            default:
                throw new IllegalArgumentException("can't test answer for unknown format " + storedResponseFormatType.toString());
        }
    }

    public Element toXml() {
        final Element answerElement = new Element(CrStorageXmlParser.XML_NODE_ANSWER_VALUE);
        answerElement.setText(answerHash);
        answerElement.setAttribute(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT, storedResponseFormatType.toString());
        return answerElement;
    }

    public boolean testAnswer(final String testResponse) {
        if (testResponse == null) {
            return false;
        }

        final String casedAnswer = caseInsensitive ? testResponse.toLowerCase() : testResponse;
        switch (storedResponseFormatType) {
            case BCRYPT:
                return BCrypt.checkpw(casedAnswer, answerHash);

            case SCRYPT:
                return SCrypt.check(casedAnswer, answerHash);
        }
        throw new IllegalArgumentException("can't test answer for unknown format " + storedResponseFormatType.toString());
    }

    static class PasswordCryptAnswerFactory implements ImplementationFactory{
        public StoredCrCryptResponse newStoredResponse(final StoredResponseFactory.AnswerConfiguration answerConfiguration, final String answer) {
            return new StoredCrCryptResponse(answerConfiguration,answer);
        }

        public StoredCrCryptResponse fromXml(final Element element, final boolean caseInsensitive, final String challengeText) {
            final String answerValue = element.getText();
            final String formatStr = element.getAttributeValue(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT);
            final StoredResponseFormatType storedResponseFormatType;
            try {
                storedResponseFormatType = StoredResponseFormatType.valueOf(formatStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown content format specified in xml format value: '" + formatStr + "'");
            }
            return new StoredCrCryptResponse(answerValue,caseInsensitive, storedResponseFormatType);
        }
    }
}
