/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import java.util.*;

/**
 * Empty class to facilitate easy resourcebundle loading of "Display" resource bundle.
 */
public abstract class Display {

    private static ResourceBundle getMessageBundle(final Locale locale) {
        final ResourceBundle messagesBundle;
        if (locale == null) {
            messagesBundle = ResourceBundle.getBundle(Display.class.getName());
        } else {
            messagesBundle = ResourceBundle.getBundle(Display.class.getName(), locale);
        }

        return messagesBundle;
    }

    public static String getDisplayString(final String key, final Locale locale) {
        final ResourceBundle bundle = getMessageBundle(locale);
        return bundle.getString(key);
    }

    public static Set<String> getDisplayKeys() {
        final ResourceBundle bundle = getMessageBundle(null);
        final Set<String> returnSet = new TreeSet<String>();
        for (final Enumeration keyEnum = bundle.getKeys(); keyEnum.hasMoreElements(); ) {
            returnSet.add(keyEnum.nextElement().toString());
        }
        return returnSet;
    }

}

