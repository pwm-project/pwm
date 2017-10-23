/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import com.google.gson.reflect.TypeToken;
import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NamedSecretValue implements StoredValue {
    private static final String ELEMENT_NAME = "name";
    private static final String ELEMENT_PASSWORD = "password";
    private static final String ELEMENT_USAGE = "usage";

    private Map<String,NamedSecretData> values;

    NamedSecretValue() {
    }


    public NamedSecretValue(final Map<String,NamedSecretData> values) {
        this.values = values;
    }

    public static StoredValue.StoredValueFactory factory()
    {
        return new StoredValue.StoredValueFactory() {
            public NamedSecretValue fromJson(final String value)
            {
                try {
                    final Map<String,NamedSecretData> values = JsonUtil.deserialize(value,new TypeToken<Map<String,NamedSecretData>>() {
                    }.getType());
                    final Map<String,NamedSecretData> linkedValues = new LinkedHashMap<>(values);
                    return new NamedSecretValue(linkedValues);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "NamedPasswordValue can not be json de-serialized: " + e.getMessage());
                }
            }

            public NamedSecretValue fromXmlElement(
                    final Element settingElement,
                    final PwmSecurityKey key
            )
                    throws PwmOperationalException, PwmUnrecoverableException
            {
                final Map<String,NamedSecretData> values = new LinkedHashMap<>();
                final List<Element> valueElements = settingElement.getChildren("value");

                try {
                    if (valueElements != null) {
                        for (final Element value : valueElements) {
                            if (value.getChild(ELEMENT_NAME) != null && value.getChild(ELEMENT_PASSWORD) != null) {
                                final String name = value.getChild(ELEMENT_NAME).getText();
                                final String encodedValue = value.getChild(ELEMENT_PASSWORD).getText();
                                final PasswordData passwordData = new PasswordData(SecureEngine.decryptStringValue(encodedValue, key, PwmBlockAlgorithm.CONFIG));
                                final List<Element> usages = value.getChildren(ELEMENT_USAGE);
                                final List<String> strUsages = new ArrayList<>();
                                if (usages != null) {
                                    for (final Element usageElement : usages) {
                                        strUsages.add(usageElement.getText());
                                    }
                                }
                                values.put(name, new NamedSecretData(passwordData, Collections.unmodifiableList(strUsages)));
                            }
                        }
                    }
                } catch (Exception e) {
                    final String errorMsg = "unable to decode encrypted password value for setting: " + e.getMessage();
                    final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorMsg);
                    throw new PwmOperationalException(errorInfo);
                }
                return new NamedSecretValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        throw new IllegalStateException("password xml output requires hash key");
    }

    @Override
    public Object toNativeObject()
    {
        return values;
    }

    @Override
    public List<String> validateValue(final PwmSetting pwm)
    {
        return Collections.emptyList();
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 0;
    }

    public List<Element> toXmlValues(final String valueElementName, final PwmSecurityKey key) {
        if (values == null) {
            final Element valueElement = new Element(valueElementName);
            return Collections.singletonList(valueElement);
        }
        final List<Element> valuesElement = new ArrayList<>();
        try {
            for (final Map.Entry<String,NamedSecretData> entry : values.entrySet()) {
                final String name = entry.getKey();
                final PasswordData passwordData = entry.getValue().getPassword();
                final String encodedValue = SecureEngine.encryptToString(passwordData.getStringValue(), key, PwmBlockAlgorithm.CONFIG);
                final Element newValueElement = new Element("value");
                final Element nameElement = new Element(ELEMENT_NAME);
                nameElement.setText(name);
                final Element encodedValueElement = new Element(ELEMENT_PASSWORD);
                encodedValueElement.setText(encodedValue);

                newValueElement.addContent(nameElement);
                newValueElement.addContent(encodedValueElement);

                for (final String usages : values.get(name).getUsage()) {
                    final Element usageElement = new Element(ELEMENT_USAGE);
                    usageElement.setText(usages);
                    newValueElement.addContent(usageElement);
                }


                valuesElement.add(newValueElement);
            }
        } catch (Exception e) {
            throw new RuntimeException("missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage());
        }
        return Collections.unmodifiableList(valuesElement);
    }

    public String toString() {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public String toDebugString(final Locale locale) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String,NamedSecretData> entry: values.entrySet()) {
            final NamedSecretData existingData = entry.getValue();
            sb.append("Named password '").append(entry.getKey()).append("' with usage for ");
            sb.append(StringUtil.collectionToString(existingData.getUsage(), ","));
            sb.append("\n");

        }
        return sb.toString();
    }

    @Override
    public Serializable toDebugJsonObject(final Locale locale) {
        if (values == null) {
            return null;
        }

        try {
            final LinkedHashMap<String,NamedSecretData> copiedValues = new LinkedHashMap<>();
            for (final Map.Entry<String,NamedSecretData> entry: values.entrySet()) {
                final String name = entry.getKey();
                final NamedSecretData existingData = entry.getValue();
                final NamedSecretData newData = new NamedSecretData(
                        PasswordData.forStringValue(PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT),
                        existingData.getUsage()
                );
                copiedValues.put(name, newData);
            }
            return copiedValues;
        } catch (PwmUnrecoverableException e) {
            throw new IllegalStateException(e.getErrorInformation().toDebugStr());
        }
    }

    public boolean requiresStoredUpdate()
    {
        return false;
    }

    @Override
    public String valueHash() throws PwmUnrecoverableException {
        return values == null ? "" : SecureEngine.hash(JsonUtil.serializeMap(values), PwmConstants.SETTING_CHECKSUM_HASH_METHOD);
    }

}
