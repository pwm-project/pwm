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

import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.util.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BooleanValue implements StoredValue {
    boolean value;

    public BooleanValue(boolean value) {
        this.value = value;
    }


    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public BooleanValue fromJson(String value) {
                return new BooleanValue(JsonUtil.deserialize(value, Boolean.class));
            }

            public BooleanValue fromXmlElement(final Element settingElement, final PwmSecurityKey input)
            {
                final Element valueElement = settingElement.getChild("value");
                final String value = valueElement.getText();
                return new BooleanValue(Boolean.valueOf(value));
            }

        };
    }

    public List<String> validateValue(PwmSetting pwmSetting)
    {
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

    public String toDebugString(Locale locale) {
        locale = locale == null ? PwmConstants.DEFAULT_LOCALE : locale;
        return value
                ? Display.getLocalizedMessage(locale,Display.Value_True,null)
                : Display.getLocalizedMessage(locale,Display.Value_False,null);
    }

    @Override
    public Serializable toDebugJsonObject(Locale locale) {
        return value;
    }

    @Override
    public boolean requiresStoredUpdate()
    {
        return false;
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 0;
    }

    @Override
    public String valueHash() throws PwmUnrecoverableException {
        return value ? "1" : "0";
    }
}
