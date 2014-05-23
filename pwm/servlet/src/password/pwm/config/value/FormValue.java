/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jdom2.Element;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.Helper;

import java.util.*;

public class FormValue extends AbstractValue implements StoredValue {
    final List<FormConfiguration> values;

    private boolean needsXmlUpdate;

    public FormValue(final List<FormConfiguration> values) {
        this.values = values;
    }

    static FormValue fromJson(final String input) {
        if (input == null) {
            return new FormValue(Collections.<FormConfiguration>emptyList());
        } else {
            final Gson gson = Helper.getGson();
            List<FormConfiguration> srcList = gson.fromJson(input, new TypeToken<List<FormConfiguration>>() {
            }.getType());
            srcList = srcList == null ? Collections.<FormConfiguration>emptyList() : srcList;
            srcList.removeAll(Collections.singletonList(null));
            return new FormValue(Collections.unmodifiableList(srcList));
        }
    }

    static FormValue fromXmlElement(Element settingElement) throws PwmOperationalException {
        final boolean oldType = PwmSettingSyntax.LOCALIZED_STRING_ARRAY.toString().equals(settingElement.getAttributeValue("syntax"));
        final Gson gson = Helper.getGson();
        final List valueElements = settingElement.getChildren("value");
        final List<FormConfiguration> values = new ArrayList<FormConfiguration>();
        for (final Object loopValue : valueElements) {
            final Element loopValueElement = (Element) loopValue;
            final String value = loopValueElement.getText();
            if (value != null && value.length() > 0 && loopValueElement.getAttribute("locale") == null) {
                if (oldType) {
                    values.add(FormConfiguration.parseOldConfigString(value));
                } else {
                    values.add(gson.fromJson(value,FormConfiguration.class));
                }
            }
        }
        final FormValue formValue = new FormValue(values);
        formValue.needsXmlUpdate = oldType;
        return formValue;
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<Element>();
        final Gson gson = Helper.getGson();
        for (final FormConfiguration value : values) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(gson.toJson(value));
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

        final Set<String> seenNames = new HashSet<String>();
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
}
