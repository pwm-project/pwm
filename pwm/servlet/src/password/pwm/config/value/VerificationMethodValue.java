package password.pwm.config.value;

import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.RecoveryVerificationMethod;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VerificationMethodValue extends AbstractValue implements StoredValue {
    private static final PwmLogger LOGGER = PwmLogger.forClass(VerificationMethodValue.class);

    private VerificationMethodSettings value = new VerificationMethodSettings();


    public enum EnabledState {
        disabled,
        required,
        optional,
    }

    public static class VerificationMethodSettings implements Serializable {
        private Map<RecoveryVerificationMethod,VerificationMethodSetting> methodSettings = new HashMap<>();
        private int minOptionalRequired = 0;

        public VerificationMethodSettings() {
        }

        public VerificationMethodSettings(Map<RecoveryVerificationMethod, VerificationMethodSetting> methodSettings, int minOptionalRequired) {
            this.methodSettings = methodSettings;
            this.minOptionalRequired = minOptionalRequired;
        }

        public Map<RecoveryVerificationMethod, VerificationMethodSetting> getMethodSettings() {
            return Collections.unmodifiableMap(methodSettings);
        }

        public int getMinOptionalRequired() {
            return minOptionalRequired;
        }
    }

    public static class VerificationMethodSetting {
        private EnabledState enabledState = EnabledState.disabled;

        public VerificationMethodSetting(EnabledState enabledState) {
            this.enabledState = enabledState;
        }

        public EnabledState getEnabledState() {
            return enabledState;
        }
    }

    public VerificationMethodValue() {
        this(new VerificationMethodSettings());
    }

    public VerificationMethodValue(VerificationMethodSettings value) {
        this.value = value;
        for (final RecoveryVerificationMethod recoveryVerificationMethod : RecoveryVerificationMethod.values()) {
            if (!value.methodSettings.containsKey(recoveryVerificationMethod)) {
                value.methodSettings.put(recoveryVerificationMethod,new VerificationMethodSetting(EnabledState.disabled));
            }
        }
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public VerificationMethodValue fromJson(final String input)
            {
                if (input == null) {
                    return new VerificationMethodValue();
                } else {
                    VerificationMethodSettings settings = JsonUtil.deserialize(input,VerificationMethodSettings.class);
                    return new VerificationMethodValue(settings);
                }
            }

            public VerificationMethodValue fromXmlElement(Element settingElement, final String key)
                    throws PwmOperationalException
            {
                final Element valueElement = settingElement.getChild("value");
                final String inputStr = valueElement.getText();
                VerificationMethodSettings settings = JsonUtil.deserialize(inputStr,VerificationMethodSettings.class);
                return new VerificationMethodValue(settings);
            }
        };
    }

    @Override
    public List<Element> toXmlValues(String valueElementName) {
        final Element valueElement = new Element(valueElementName);
        valueElement.addContent(new CDATA(JsonUtil.serialize(value)));
        return Collections.singletonList(valueElement);
    }

    @Override
    public Object toNativeObject() {
        return value;
    }

    @Override
    public List<String> validateValue(PwmSetting pwm) {
        return Collections.emptyList();
    }
}
