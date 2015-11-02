package password.pwm.cr.storage;


import java.io.Serializable;

public class StoredResponseFactory {
    private StoredResponseFactory() {
    }

    public static StoredResponse newAnswer(final AnswerConfiguration answerConfiguration, final String answerText) {
        final StoredResponse.ImplementationFactory implementationFactory = answerConfiguration.getStoredResponseFormatType().getFactory();
        return implementationFactory.newStoredResponse(answerConfiguration, answerText);
    }

    public static StoredResponse fromXml(final org.jdom2.Element element, final boolean caseInsensitive, final String challengeText) {
        final String formatStr = element.getAttribute(CrStorageXmlParser.XML_ATTRIBUTE_CONTENT_FORMAT).getValue();
        final StoredResponseFormatType respFormat;
        if (formatStr != null && formatStr.length() > 0) {
            respFormat = StoredResponseFormatType.valueOf(formatStr);
        } else {
            respFormat = StoredResponseFormatType.TEXT;
        }
        return respFormat.getFactory().fromXml(element, caseInsensitive, challengeText);
    }

    public static class AnswerConfiguration implements Serializable {
        public boolean caseInsensitive;
        public int hashCount;
        public StoredResponseFormatType storedResponseFormatType;
        public String challengeText;

        public boolean isCaseInsensitive() {
            return caseInsensitive;
        }

        public void setCaseInsensitive(boolean caseInsensitive) {
            this.caseInsensitive = caseInsensitive;
        }

        public int getHashCount() {
            return hashCount;
        }

        public void setHashCount(int hashCount) {
            this.hashCount = hashCount;
        }

        public StoredResponseFormatType getStoredResponseFormatType() {
            return storedResponseFormatType;
        }

        public void setStoredResponseFormatType(StoredResponseFormatType storedResponseFormatType) {
            this.storedResponseFormatType = storedResponseFormatType;
        }

        public String getChallengeText() {
            return challengeText;
        }

        public void setChallengeText(String challengeText) {
            this.challengeText = challengeText;
        }
    }
}
