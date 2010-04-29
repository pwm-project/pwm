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

import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import password.pwm.Helper;
import password.pwm.PwmConstants;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class NewConfiguration implements Serializable {

    private Map<PwmSetting,String> settingMap = new HashMap<PwmSetting,String>();
    private boolean locked = false;

    public boolean isLocked() {
        return locked;
    }

    public void lock() {
        locked = true;
        settingMap = Collections.unmodifiableMap(settingMap);
    }

    public static NewConfiguration getDefaultConfiguration() {
        final NewConfiguration config = new NewConfiguration();
        for (final PwmSetting loopSetting : PwmSetting.values() ) {
            if (loopSetting.isLocalizable()) {
                config.settingMap.put(loopSetting, JSONObject.toJSONString(Collections.singletonMap("",loopSetting.getDefaultValue())));
            } else {
                config.settingMap.put(loopSetting, loopSetting.getDefaultValue());
            }
        }
        return config;
    }

    public String readStringSetting(final PwmSetting setting) {
        if (setting.isLocalizable()) {
            throw new IllegalArgumentException("can't read localizable setting as string");
        }
        return settingMap.get(setting);
    }

    public void writeStringSetting(final PwmSetting setting, final String value) {
        if (setting.isLocalizable()) {
            throw new IllegalArgumentException("can't read localizable setting as string");
        }
        settingMap.put(setting, value);
    }

    public Map<Locale, String> readLocalizedStringSetting(final PwmSetting setting) {
        final String rawString = settingMap.get(setting);
        final Map<Locale, String> returnMap = new HashMap<Locale, String>();

        final JSONObject srcMap = (JSONObject) JSONValue.parse(rawString);
        for (final Object key : srcMap.keySet()) {
            final Locale keyLocale = Helper.parseLocaleString((String)key);
            final String value = (String)srcMap.get(key);
            returnMap.put(keyLocale, value);
        }

        return returnMap;
    }

    private String toXml(
            final HttpServletRequest req,
            final HttpServletResponse resp
    ) throws IOException {
        Element settingsElement = new Element("settings");
        for (PwmSetting setting : PwmSetting.values()) {
            settingsElement.addContent(setting.toXmlElement(settingMap.toString()));
        }

        final Element pwmConfigElement = new Element("PwmConfigurationBean");
        pwmConfigElement.addContent(new Comment("WARNING: This configuration file contains sensitive security information, please handle with care!"));
        pwmConfigElement.addContent(new Comment("Configuration file generated for PWM Servlet"));
        pwmConfigElement.addContent(settingsElement);
        pwmConfigElement.setAttribute("version", PwmConstants.PWM_VERSION);
        pwmConfigElement.setAttribute("build", PwmConstants.BUILD_NUMBER);
        pwmConfigElement.setAttribute("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        final XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        return outputter.outputString(new Document(pwmConfigElement));
    }

}
