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

    public void init(final PwmApplication pwmApplication) throws PwmUnrecoverableException {
        this.pwmApplication = pwmApplication;
        final Configuration config = this.pwmApplication.getConfig();
        final String classNameString = config.readSettingAsString(PwmSetting.URL_SHORTENER_CLASS);
        if (classNameString != null && classNameString.length() > 0) {
            final Properties sConfig = new Properties();
            final List<String> sConfigList = config.readSettingAsStringArray(PwmSetting.URL_SHORTENER_PARAMETERS);
            // Parse configuration
            if (sConfigList != null) {
                for (final String p : sConfigList) {
                    final List<String> pl = Arrays.asList(p.split("=", 2));
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
    }

    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    public String shortenUrl(final String text) throws PwmUnrecoverableException {
        if (theShortener != null) {
            return theShortener.shorten(text, pwmApplication);
        }
        return text;
    }
    
    public String shortenUrlInText(final String text) throws PwmUnrecoverableException {
        final String urlRegex = pwmApplication.getConfig().readAppProperty(AppProperty.URL_SHORTNER_URL_REGEX);
        try {
            final Pattern p = Pattern.compile(urlRegex);
            final Matcher m = p.matcher(text);
            final StringBuilder result = new StringBuilder();
            Boolean found = m.find();
            if (found) {
                int start = 0;
                int end = m.start();
                result.append(text.substring(start,end));
                start = end;
                end = m.end();
                while (found) {
                    result.append(shortenUrl(text.substring(start,end)));
                    start = end;
                    found = m.find();
                    if (found) {
                        end = m.start();
                        result.append(text.substring(start,end));
                        start = end;
                        end = m.end();
                    }
                }
                result.append(text.substring(end));
                return result.toString();
            }
        } catch (PatternSyntaxException e) {
            LOGGER.error("Error compiling pattern: " + e.getMessage());
        }
        return text;
    }

    public ServiceInfoBean serviceInfo()
    {
        return new ServiceInfoBean(Collections.<DataStorageMethod>emptyList());
    }
}
