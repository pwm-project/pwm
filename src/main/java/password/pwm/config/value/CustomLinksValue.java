package password.pwm.config.value;

import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.error.PwmOperationalException;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.*;

/**
 * Created by RKeil on 6/14/2017.
 */
public class CustomLinksValue extends AbstractValue implements StoredValue {
    private static final PwmLogger LOGGER = PwmLogger.forClass(CustomLinksValue.class);

    private CustomLinksSettings value = new CustomLinksSettings();

    public enum EnabledState {
        disabled,
        required,
        optional,
    }

    public static class CustomLinksSettings implements Serializable {
        private Map<IdentityVerificationMethod, CustomLinksSetting> methodSettings = new HashMap<>();
        private int minOptionalRequired;

        public CustomLinksSettings() {
        }

        public CustomLinksSettings(final Map<IdentityVerificationMethod, CustomLinksSetting> methodSettings, final int minOptionalRequired) {
            this.methodSettings = methodSettings;
            this.minOptionalRequired = minOptionalRequired;
        }

        public Map<IdentityVerificationMethod, CustomLinksSetting> getMethodSettings() {
            final Map<IdentityVerificationMethod, CustomLinksSetting> tempMap = new LinkedHashMap<>(methodSettings);
            tempMap.remove(null);
            return Collections.unmodifiableMap(tempMap);
        }

        public int getMinOptionalRequired() {
            return minOptionalRequired;
        }
    }


    public static class CustomLinksSetting {
        private EnabledState enabledState = EnabledState.disabled;

        public CustomLinksSetting(final EnabledState enabledState) {
            this.enabledState = enabledState;
        }

        public CustomLinksValue.EnabledState getEnabledState() {
            return enabledState;
        }
    }

    public CustomLinksValue() { this(new CustomLinksSettings()); }

    public CustomLinksValue(final CustomLinksSettings value) {
        this.value = value;
        for (final IdentityVerificationMethod recoveryVerificationMethods : IdentityVerificationMethod.availableValues()) {
            if (!value.methodSettings.containsKey(recoveryVerificationMethods)) {
                value.methodSettings.put(recoveryVerificationMethods,new CustomLinksSetting(EnabledState.disabled));
            }
        }
    }


    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public CustomLinksValue fromJson(final String input)
            {
                if (input == null) {
                    return new CustomLinksValue();
                } else {
                    final CustomLinksSettings settings = JsonUtil.deserialize(input, CustomLinksSettings.class);
                    return new CustomLinksValue();
                }
            }

            public CustomLinksValue fromXmlElement(final Element settingElement, final PwmSecurityKey key)
                    throws PwmOperationalException
            {
                final Element valueElement = settingElement.getChild("value");
                final String inputStr = valueElement.getText();
                final CustomLinksSettings settings = JsonUtil.deserialize(inputStr, CustomLinksSettings.class);
                return new CustomLinksValue();
            }
        };
    }

    @Override
    public List<Element> toXmlValues(final String valueElementName) {
        final Element valueElement = new Element(valueElementName);
        valueElement.addContent(new CDATA(JsonUtil.serialize(value)));
        return Collections.singletonList(valueElement);
    }

    @Override
    public Object toNativeObject() {
        return value;
    }

    @Override
    public List<String> validateValue(final PwmSetting pwm) {
        return Collections.emptyList();
    }

    @Override
    public String toDebugString(final Locale locale) {
        if (value == null) {
            return "No Verification Methods";
        }
        final StringBuilder out = new StringBuilder();
        final List<String> optionals = new ArrayList<>();
        final List<String> required = new ArrayList<>();

        for (final IdentityVerificationMethod method : value.getMethodSettings().keySet()) {
            switch (value.getMethodSettings().get(method).getEnabledState()) {
                case optional:
                    optionals.add(method.getLabel(null, locale));
                    break;

                case required:
                    required.add(method.getLabel(null, locale));
                    break;

                default:
                    // continue processing
                    break;
            }
            method.getLabel(null,locale);
        }

        out.append("optional methods: ").append(optionals.isEmpty()
                ? LocaleHelper.getLocalizedMessage(locale, Display.Value_NotApplicable, null)
                : JsonUtil.serializeCollection(optionals)
        );
        out.append(", required methods: ").append(required.isEmpty()
                ? LocaleHelper.getLocalizedMessage(locale, Display.Value_NotApplicable, null)
                : JsonUtil.serializeCollection(required)
        );

        if (value.getMinOptionalRequired() > 0) {
            out.append(",  minimum optional methods required: ").append(value.getMinOptionalRequired());
        }

        return out.toString();
    }
}
