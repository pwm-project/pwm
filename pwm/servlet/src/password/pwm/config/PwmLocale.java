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

import password.pwm.PwmConstants;

import java.io.Serializable;
import java.util.*;

public class PwmLocale implements Serializable {
    private Locale locale;
    private String flagCountryCode;

    public PwmLocale(Locale locale, String flagCountryCode) {
        this.locale = locale;
        this.flagCountryCode = flagCountryCode;
    }

    public static Locale pwmLocaleResolver(final Locale desiredLocale, final Collection<PwmLocale> localePool) {
        final List<Locale> tempList = new ArrayList<Locale>();
        for (final PwmLocale pwmLocale : localePool) {
            tempList.add(pwmLocale.getLocale());
        }

        return localeResolver(desiredLocale, tempList);
    }

    public static String resolveStringKeyLocaleMap(Locale desiredLocale, final Map<String,String> inputMap) {
        if (inputMap == null || inputMap.isEmpty()) {
            return null;
        }

        if (desiredLocale == null) {
            desiredLocale = PwmConstants.DEFAULT_LOCALE;
        }

        final Map<Locale,String> localeMap = new LinkedHashMap<Locale, String>();
        for (final String localeStringKey : inputMap.keySet()) {
            localeMap.put(PwmLocale.parseLocaleString(localeStringKey),inputMap.get(localeStringKey));
        }

        final Locale selectedLocale = PwmLocale.localeResolver(desiredLocale,localeMap.keySet());
        return localeMap.get(selectedLocale);
    }

    public static Locale localeResolver(final Locale desiredLocale, final Collection<Locale> localePool) {
        if (desiredLocale == null || localePool == null || localePool.isEmpty()) {
            return null;
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                if (loopLocale.getCountry().equalsIgnoreCase(desiredLocale.getCountry())) {
                    if (loopLocale.getVariant().equalsIgnoreCase(desiredLocale.getVariant())) {
                        return loopLocale;
                    }
                }
            }
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                if (loopLocale.getCountry().equalsIgnoreCase(desiredLocale.getCountry())) {
                    return loopLocale;
                }
            }
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                return loopLocale;
            }
        }

        final Locale emptyLocale = parseLocaleString("");
        if (localePool.contains(emptyLocale)) {
            return emptyLocale;
        }

        return null;
    }

    public static Locale parseLocaleString(final String localeString) {
        if (localeString == null) {
            return new Locale("");
        }

        final StringTokenizer st = new StringTokenizer(localeString, "_");

        if (!st.hasMoreTokens()) {
            return new Locale("");
        }

        final String language = st.nextToken();
        if (!st.hasMoreTokens()) {
            return new Locale(language);
        }

        final String country = st.nextToken();
        if (!st.hasMoreTokens()) {
            return new Locale(language, country);
        }

        final String variant = st.nextToken("");
        return new Locale(language, country, variant);
    }

    public Locale getLocale() {
        return locale;
    }

    public String getFlagCountryCode() {
        return flagCountryCode;
    }
}
