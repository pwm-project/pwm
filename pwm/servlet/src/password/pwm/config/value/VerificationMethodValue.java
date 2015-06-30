package password.pwm.config.value;

import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.RecoveryVerificationMethods;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class VerificationMethodValue extends AbstractValue implements StoredValue {
    private static final PwmLogger LOGGER = PwmLogger.forClass(VerificationMethodValue.class);

    private VerificationMethodSettings value = new VerificationMethodSettings();


    public enum EnabledState {
        disabled,
        required,
        optional,
    }

    public static class VerificationMethodSettings implements Serializable {
        private Map<RecoveryVerificationMethods,VerificationMethodSetting> methodSettings = new HashMap<>();
        private int minOptionalRequired = 0;

        public VerificationMethodSettings() {
        }

        public VerificationMethodSettings(Map<RecoveryVerificationMethods, VerificationMethodSetting> methodSettings, int minOptionalRequired) {
            this.methodSettings = methodSettings;
            this.minOptionalRequired = minOptionalRequired;
        }

        public Map<RecoveryVerificationMethods, VerificationMethodSetting> getMethodSettings() {
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
        for (final RecoveryVerificationMethods recoveryVerificationMethods : RecoveryVerificationMethods.availableValues()) {
            if (!value.methodSettings.containsKey(recoveryVerificationMethods)) {
                value.methodSettings.put(recoveryVerificationMethods,new VerificationMethodSetting(EnabledState.disabled));
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

    @Override
    public String toDebugString(Locale locale) {
        if (value == null) {
            return "No Verification Methods";
        }
        final StringBuilder out = new StringBuilder();
        for (final RecoveryVerificationMethods method : value.getMethodSettings().keySet()) {
            out.append(" ").append(method.toString()).append(": ").append(value.getMethodSettings().get(method).getEnabledState());
            out.append("\n");
        }
        out.append("  Minimum Optional Methods Required: ").append(value.getMinOptionalRequired());
        return out.toString();
    }
}
