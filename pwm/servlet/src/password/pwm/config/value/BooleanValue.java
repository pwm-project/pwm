/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;

import java.util.Collections;
import java.util.List;

public class BooleanValue implements StoredValue {
    boolean value;

    public BooleanValue(boolean value) {
        this.value = value;
    }

    static BooleanValue fromJson(String value) {
        return new BooleanValue(new Gson().fromJson(value,Boolean.class));
    }

    static BooleanValue fromXmlElement(final Element settingElement) {
        final Element valueElement = settingElement.getChild("value");
        final String value = valueElement.getText();
        return new BooleanValue(Boolean.valueOf(value));
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        return Collections.emptyList();
    }

    @Override
    public List<Element> toXmlValues(String valueElementName) {
        final Element valueElement = new Element(valueElementName);
        valueElement.addContent(String.valueOf(value));
        return Collections.singletonList(valueElement);
    }

    @Override
    public Object toNativeObject() {
        return value;
    }

    public String toString() {
        return new Gson().toJson(value);
    }

    public String toDebugString() {
        return toString();
    }
}
