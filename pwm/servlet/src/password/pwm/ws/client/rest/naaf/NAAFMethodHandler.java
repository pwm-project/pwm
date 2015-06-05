package password.pwm.ws.client.rest.naaf;

import password.pwm.error.PwmUnrecoverableException;

import java.util.Locale;
import java.util.Map;

public interface NAAFMethodHandler {
    void init(NAAFLoginSequence naafLoginSequence);

    Map<String,String> getPrompts(Locale locale) throws PwmUnrecoverableException;

    String answerPrompts(final Map<String,String> answers) throws PwmUnrecoverableException;
}
