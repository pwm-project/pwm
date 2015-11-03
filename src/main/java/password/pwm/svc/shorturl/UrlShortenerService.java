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

package password.pwm.svc.shorturl;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Menno Pieters
 */
public class UrlShortenerService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UrlShortenerService.class);

    private PwmApplication pwmApplication;
    private BasicUrlShortener theShortener = null;
    private STATUS status = PwmService.STATUS.NEW;

    public UrlShortenerService() {
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface PwmService ---------------------

    public void init(final PwmApplication pwmApplication) throws PwmUnrecoverableException {
        this.pwmApplication = pwmApplication;
        Configuration config = this.pwmApplication.getConfig();
        String classNameString = config.readSettingAsString(PwmSetting.URL_SHORTENER_CLASS);
        if (classNameString != null && classNameString.length() > 0) {
            Properties sConfig = new Properties();
            List<String> sConfigList = config.readSettingAsStringArray(PwmSetting.URL_SHORTENER_PARAMETERS);
            // Parse configuration
            if (sConfigList != null) {
                for (final String p : sConfigList) {
                    List<String> pl = Arrays.asList(p.split("=", 2));
                    if (pl.size() == 2) {
                        sConfig.put(pl.get(0), pl.get(1));
                    }
                }
            }
            try {
                final Class<?> theClass = Class.forName(classNameString);
                theShortener = (BasicUrlShortener) theClass.newInstance();
                theShortener.setConfiguration(sConfig);
            } catch (java.lang.IllegalAccessException e) {
                LOGGER.error("Illegal access to class "+classNameString+": "+e.toString());
            } catch (java.lang.InstantiationException e) {
                LOGGER.error("Cannot instantiate class "+classNameString+": "+e.toString());
            } catch (java.lang.ClassNotFoundException e) {
                LOGGER.error("Class "+classNameString+" not found: "+e.getMessage());
            }
        }
        status = PwmService.STATUS.OPEN;
    }

    public STATUS status() {
        return status;
    }

    public void close() {
        status = PwmService.STATUS.CLOSED;
        LOGGER.debug("closed");
    }

    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

// -------------------------- OTHER METHODS --------------------------
    public String shortenUrl(String text) throws PwmUnrecoverableException {
        if (theShortener != null) {
            return theShortener.shorten(text, pwmApplication);
        }
        return text;
    }
    
    public String shortenUrlInText(String text) throws PwmUnrecoverableException {
        final String urlRegex = pwmApplication.getConfig().readAppProperty(AppProperty.URL_SHORTNER_URL_REGEX);
        try {
            final Pattern p = Pattern.compile(urlRegex);
            final Matcher m = p.matcher(text);
            String result = "";
            Boolean found = m.find();
            if (found) {
                int start = 0;
                int end = m.start();
                result += text.substring(start,end);
                start = end;
                end = m.end();
                while (found) {
                    result += shortenUrl(text.substring(start,end));
                    start = end;
                    found = m.find();
                    if (found) {
                        end = m.start();
                        result += text.substring(start,end);
                        start = end;
                        end = m.end();
                    }
                }
                result += text.substring(end);
                return result;
            }
        } catch (PatternSyntaxException e) {
            LOGGER.error("Error compiling pattern: " + e.getMessage());
        }
        return text;
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
    }
}