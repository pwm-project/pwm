/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config.value;

import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.error.PwmOperationalException;
import password.pwm.i18n.Display;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

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
        private Map<IdentityVerificationMethod,VerificationMethodSetting> methodSettings = new HashMap<>();
        private int minOptionalRequired = 0;

        public VerificationMethodSettings() {
        }

        public VerificationMethodSettings(Map<IdentityVerificationMethod, VerificationMethodSetting> methodSettings, int minOptionalRequired) {
            this.methodSettings = methodSettings;
            this.minOptionalRequired = minOptionalRequired;
        }

        public Map<IdentityVerificationMethod, VerificationMethodSetting> getMethodSettings() {
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
        for (final IdentityVerificationMethod recoveryVerificationMethods : IdentityVerificationMethod.availableValues()) {
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

            public VerificationMethodValue fromXmlElement(Element settingElement, final PwmSecurityKey key)
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
