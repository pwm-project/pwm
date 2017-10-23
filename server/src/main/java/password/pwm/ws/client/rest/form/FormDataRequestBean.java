package password.pwm.ws.client.rest.form;

import lombok.Builder;
import lombok.Getter;
import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FormDataRequestBean implements Serializable {

    @Getter
    @Builder
    public static class FormInfo implements Serializable {
        private FormType module;
        private String moduleProfileID;
        private Mode mode;
        private String sessionID;
    }

    private FormInfo formInfo;
    private String userDN;
    private String ldapProfileID;

    public enum FormType {
        NewUser,
    }

    public enum Mode {
        read,
        verify,
        write,
    }

    private Map<String,String> formValues;
    private List<FormConfiguration> formConfigurations;
}
