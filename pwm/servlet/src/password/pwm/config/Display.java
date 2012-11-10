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

package password.pwm.config;

import password.pwm.util.Helper;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Empty class to facilitate easy resourcebundle loading of "Display" resource bundle.
 */
public abstract class Display {

    public static String getLocalizedMessage(final Locale locale, final String key, final Configuration config) {
        return getRawString(config, key,locale);
    }

    private static ResourceBundle getMessageBundle(final Locale locale) {
        final ResourceBundle messagesBundle;
        if (locale == null) {
            messagesBundle = ResourceBundle.getBundle(Display.class.getName());
        } else {
            messagesBundle = ResourceBundle.getBundle(Display.class.getName(), locale);
        }

        return messagesBundle;
    }

    private static String getRawString(final Configuration config, final String key, final Locale locale) {
        if (config != null) {
            final Map<Locale,String> configuredBundle = config.readLocalizedBundle(Display.class.getName(),key);
            if (configuredBundle != null) {
                final Locale resolvedLocale = Helper.localeResolver(locale, configuredBundle.keySet());
                return configuredBundle.get(resolvedLocale);
            }
        }
        final ResourceBundle bundle = getMessageBundle(locale);
        return bundle.getString(key);

    }

}

