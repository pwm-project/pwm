/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.error.*;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Jason D. Rivard
 */
public class CustomLinkConfiguration implements Serializable {
// ------------------------------ FIELDS ------------------------------

    public enum Type {text, email, number, password, random, tel, hidden, date, datetime, time, week, month, url, select, userDN, checkbox, customLink}

    private String name;
    private Type type = Type.text;
    private Map<String,String> labels = Collections.singletonMap("", "");
    private Map<String,String> description = Collections.singletonMap("","");
    private String customLinkUrl = "";
    private boolean customLinkNewWindow = true;
    private Map<String,String> selectOptions = Collections.emptyMap();

// -------------------------- STATIC METHODS --------------------------


// --------------------- GETTER / SETTER METHODS ---------------------

    public String getName() {
        return name;
    }

    public String getLabel(final Locale locale) {
        return LocaleHelper.resolveStringKeyLocaleMap(locale, labels);
    }

    public String getDescription(final Locale locale) {
        return LocaleHelper.resolveStringKeyLocaleMap(locale, description);
    }

    public Map<String,String> getLabelDescriptionLocaleMap() {
        return Collections.unmodifiableMap(this.description);
    }

    public Type getType() {
        return type;
    }

    public boolean isCustomLinkNewWindow() {
        return customLinkNewWindow;
    }

    public String getcustomLinkUrl() {
        return customLinkUrl;
    }

    public Map<String,String> getSelectOptions() {
        return Collections.unmodifiableMap(selectOptions);
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("FormItem: ");
        sb.append(JsonUtil.serialize(this));

        return sb.toString();
    }

// -------------------------- OTHER METHODS --------------------------

    public String displayValue(final String value, final Locale locale, final Configuration config) {
        if (value == null) {
            return LocaleHelper.getLocalizedMessage(locale, Display.Value_NotApplicable, config);
        }

        if (this.getType() == Type.select) {
            if (this.getSelectOptions() != null) {
                for (final String key : selectOptions.keySet()) {
                    if (value.equals(key)) {
                        final String displayValue = selectOptions.get(key);
                        if (!StringUtil.isEmpty(displayValue)) {
                            return displayValue;
                        }
                    }
                }
            }
        }

        return value;
    }
}
