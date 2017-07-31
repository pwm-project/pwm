package password.pwm.util.form;

import password.pwm.bean.SessionLabel;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FormState {
    private final List<FormConfiguration> formConfigurations;
    private final Map<String,String> values;

    private FormState(final List<FormConfiguration> formConfigurations, final Map<String, String> values) {
        this.formConfigurations = Collections.unmodifiableList(formConfigurations);
        this.values = values;
    }

    public static FormState initialize(final List<FormConfiguration> formConfigurations) {
        final FormState formState = new FormState(
                Collections.unmodifiableList(formConfigurations),
                new HashMap<>()
        );
        return formState;
    }

    public void readDataFromSources(final SessionLabel sessionLabel, final UserInfo userInfo)
            throws PwmUnrecoverableException
    {
        FormUtility.populateFormMapFromLdap(formConfigurations, sessionLabel, userInfo);
    }
}
