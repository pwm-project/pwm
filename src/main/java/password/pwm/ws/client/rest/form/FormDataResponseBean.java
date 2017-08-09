package password.pwm.ws.client.rest.form;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Builder
public class FormDataResponseBean implements Serializable {
    private boolean error;
    private String errorMessage;
    private String errorDetail;
    private Map<String,String> formValues;

}
