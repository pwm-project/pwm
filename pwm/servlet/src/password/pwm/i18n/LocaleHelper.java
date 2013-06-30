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

package password.pwm.i18n;

import password.pwm.util.*;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.util.Helper;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class LocaleHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LocaleHelper.class);

    public static String getLocalizedMessage(final String key, final Configuration config, final Class bundleClass) {
        return getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,key,config,bundleClass);
    }

    public static String getLocalizedMessage(final Locale locale, final String key, final Configuration config, final Class bundleClass) {
        return getLocalizedMessage(locale,key,config,bundleClass,null);
    }

    public static String getLocalizedMessage(final Locale locale, final String key, final Configuration config, final Class bundleClass, final String[] values) {
        String returnValue = null;
        if (config != null) {
            final Map<Locale,String> configuredBundle = config.readLocalizedBundle(bundleClass.getName(),key);
            if (configuredBundle != null) {
                final Locale resolvedLocale = Helper.localeResolver(locale, configuredBundle.keySet());
                returnValue = configuredBundle.get(resolvedLocale);
            }
        }

        if (returnValue == null || returnValue.isEmpty()) {
            final ResourceBundle bundle = getMessageBundle(locale, bundleClass);
            final String rawValue = bundle.getString(key);
            returnValue = rawValue;
        }

        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    final String replaceKey = "%" + (i+1) + "%";
                    returnValue = returnValue.replace(replaceKey,values[i]);
                }
            }
        }
        return returnValue;
    }

    private static ResourceBundle getMessageBundle(final Locale locale, final Class bundleClass) {
        final ResourceBundle messagesBundle;
        if (locale == null) {
            messagesBundle = ResourceBundle.getBundle(bundleClass.getName());
        } else {
            messagesBundle = ResourceBundle.getBundle(bundleClass.getName(), locale);
        }

        return messagesBundle;
    }
}
