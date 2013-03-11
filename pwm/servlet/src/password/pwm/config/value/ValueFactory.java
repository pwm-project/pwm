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

import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.PwmLogger;

import java.lang.reflect.Method;

public class ValueFactory {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ValueFactory.class);

    public static StoredValue fromJson(final PwmSetting setting, final String input)
            throws PwmOperationalException
    {
        try {
            final Class valueClass = setting.getSyntax().getStoredValueImpl();
            final Method fromXmlElementMethod = valueClass.getDeclaredMethod("fromJson", String.class);
            fromXmlElementMethod.setAccessible(true);
            final Object storedValueInstance = fromXmlElementMethod.invoke(null, input);
            return (StoredValue)storedValueInstance;
        } catch (Exception e) {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("error parsing value stored configuration value: ").append(e.getMessage());
            if (e.getCause() != null) {
                errorMsg.append(", cause: ").append(e.getCause().getMessage());
            }
            LOGGER.error(errorMsg,e);
            throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg.toString()));
        }
    }

    public static StoredValue fromXmlValues(final PwmSetting setting, final Element settingElement, final String key)
            throws PwmOperationalException
    {
        if (setting.getSyntax() == PwmSettingSyntax.PASSWORD) {
            return PasswordValue.fromXmlValue(settingElement, key);
        }

        try {
            final Class valueClass = setting.getSyntax().getStoredValueImpl();
            final Method fromXmlElementMethod = valueClass.getDeclaredMethod("fromXmlElement",Element.class);
            fromXmlElementMethod.setAccessible(true);
            final Object storedValueInstance = fromXmlElementMethod.invoke(null,settingElement);
            return (StoredValue)storedValueInstance;
        } catch (Exception e) {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("error parsing stored configuration value: " + e.getMessage());
            if (e.getCause() != null) {
                errorMsg.append(", cause: " + e.getCause().getMessage());
            }
            LOGGER.error(errorMsg,e);
            throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg.toString()));
        }
    }
}

