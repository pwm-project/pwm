/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.*;

public class FormValue extends AbstractValue implements StoredValue {
    final List<FormConfiguration> values;

    private boolean needsXmlUpdate;

    public FormValue(final List<FormConfiguration> values) {
        this.values = values;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public FormValue fromJson(final String input)
            {
                if (input == null) {
                    return new FormValue(Collections.<FormConfiguration>emptyList());
                } else {
                    List<FormConfiguration> srcList = JsonUtil.deserialize(input, new TypeToken<List<FormConfiguration>>() {
                    });
                    srcList = srcList == null ? Collections.<FormConfiguration>emptyList() : srcList;
                    while (srcList.contains(null)) {
                        srcList.remove(null);
                    }
                    return new FormValue(Collections.unmodifiableList(srcList));
                }
            }

            public FormValue fromXmlElement(Element settingElement, final PwmSecurityKey key)
                    throws PwmOperationalException
            {
                final boolean oldType = PwmSettingSyntax.LOCALIZED_STRING_ARRAY.toString().equals(
                        settingElement.getAttributeValue("syntax"));
                final List valueElements = settingElement.getChildren("value");
                final List<FormConfiguration> values = new ArrayList<>();
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String value = loopValueElement.getText();
                    if (value != null && value.length() > 0 && loopValueElement.getAttribute("locale") == null) {
                        if (oldType) {
                            values.add(FormConfiguration.parseOldConfigString(value));
                        } else {
                            values.add(JsonUtil.deserialize(value, FormConfiguration.class));
                        }
                    }
                }
                final FormValue formValue = new FormValue(values);
                formValue.needsXmlUpdate = oldType;
                return formValue;
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final FormConfiguration value : values) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(JsonUtil.serialize(value));
            returnList.add(valueElement);
        }
        return returnList;
    }

    public List<FormConfiguration> toNativeObject() {
        return Collections.unmodifiableList(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.size() < 1 || values.get(0) == null) {
                return Collections.singletonList("required value missing");
            }
        }

        final Set<String> seenNames = new HashSet<>();
        for (final FormConfiguration loopConfig : values) {
            if (seenNames.contains(loopConfig.getName().toLowerCase())) {
                return Collections.singletonList("each form name must be unique: " + loopConfig.getName());
            }
            seenNames.add(loopConfig.getName().toLowerCase());
        }

        for (final FormConfiguration loopConfig : values) {
            try {
                loopConfig.validate();
            } catch (PwmOperationalException e) {
                return Collections.singletonList("format error: " + e.getErrorInformation().toDebugStr());
            }
        }

        return Collections.emptyList();
    }

    public boolean isNeedsXmlUpdate()
    {
        return needsXmlUpdate;
    }

    public String toDebugString(Locale locale) {
        if (values != null && !values.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final FormConfiguration formRow : values) {
                sb.append("FormItem Name:").append(formRow.getName()).append("\n");
                sb.append(" Type:").append(formRow.getType());
                sb.append(" Min:").append(formRow.getMinimumLength());
                sb.append(" Max:").append(formRow.getMaximumLength());
                sb.append(" ReadOnly:").append(formRow.isReadonly());
                sb.append(" Required:").append(formRow.isRequired());
                sb.append(" Confirm:").append(formRow.isConfirmationRequired());
                sb.append(" Unique:").append(formRow.isUnique());
                sb.append("\n");
                sb.append(" Label:").append(JsonUtil.serializeMap(formRow.getLabelLocaleMap())).append("\n");
                sb.append(" Description:").append(JsonUtil.serializeMap(formRow.getLabelDescriptionLocaleMap())).append("\n");
                if (formRow.getSelectOptions() != null && !formRow.getSelectOptions().isEmpty()) {
                    sb.append(" Select Options: ").append(JsonUtil.serializeMap(formRow.getSelectOptions())).append("\n");
                }

            }
            return sb.toString();
        } else {
            return "";
        }
    }

}
