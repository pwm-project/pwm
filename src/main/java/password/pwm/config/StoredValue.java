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

package password.pwm.config;

import org.jdom2.Element;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public interface StoredValue extends Serializable {
    List<Element> toXmlValues(final String valueElementName);

    Object toNativeObject();

    List<String> validateValue(PwmSetting pwm);

    Serializable toDebugJsonObject(Locale locale);

    String toDebugString(Locale locale);

    boolean requiresStoredUpdate();

    int currentSyntaxVersion();

    interface StoredValueFactory {
        StoredValue fromJson(final String input);

        StoredValue fromXmlElement(final Element settingElement, final PwmSecurityKey key)
                throws PwmException;
    }

    String valueHash() throws PwmUnrecoverableException;
}