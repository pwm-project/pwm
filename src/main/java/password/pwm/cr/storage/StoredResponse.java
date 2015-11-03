package password.pwm.cr.storage;

import com.novell.ldapchai.exception.ChaiOperationException;
import org.jdom2.Element;

public interface StoredResponse {
    boolean testAnswer(final String answer);

    Element toXml() throws ChaiOperationException;

    interface ImplementationFactory {
        StoredResponse newStoredResponse(StoredResponseFactory.AnswerConfiguration answerConfiguration, String answerText);

        StoredResponse fromXml(org.jdom2.Element element, boolean caseInsensitive, String challengeText);
    }


}
