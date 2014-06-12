/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util.macro;

import password.pwm.PwmApplication;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.ldap.UserDataReader;
import password.pwm.util.PwmLogger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroMachine {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(MacroMachine.class);

    private final PwmApplication pwmApplication;
    private final UserInfoBean userInfoBean;
    private final UserDataReader userDataReader;
    private final Map<Pattern,MacroImplementation> macroImplementations;

    public MacroMachine(
            PwmApplication pwmApplication,
            UserInfoBean userInfoBean
    )
    {
        this.pwmApplication = pwmApplication;
        this.userInfoBean = userInfoBean;
        this.userDataReader = null;
        this.macroImplementations = makeImplementations();
    }

    public MacroMachine(
            PwmApplication pwmApplication,
            UserInfoBean userInfoBean,
            UserDataReader userDataReader
    )
    {
        this.pwmApplication = pwmApplication;
        this.userInfoBean = userInfoBean;
        this.userDataReader = userDataReader;
        this.macroImplementations = makeImplementations();
    }

    private Map<Pattern,MacroImplementation> makeImplementations() {
        final Set<Class<? extends MacroImplementation>> implementations = new HashSet<Class<? extends MacroImplementation>>();
        implementations.addAll(StandardMacros.STANDARD_MACROS);
        implementations.addAll(InternalMacros.INTERNAL_MACROS);
        final LinkedHashMap<Pattern,MacroImplementation> map = new LinkedHashMap<Pattern, MacroImplementation>();

        for (Class macroClass : implementations) {
            try {
                final MacroImplementation macroImplementation = (MacroImplementation)macroClass.newInstance();
                macroImplementation.init(pwmApplication,userInfoBean,userDataReader);
                final Pattern pattern = macroImplementation.getRegExPattern();
                map.put(pattern,macroImplementation);
            } catch (Exception e) {
                LOGGER.error("unable to load macro class " + macroClass.getName() + ", error: " + e.getMessage());
            }
        }

        final List<String> externalMethods = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EXTERNAL_MACROS_REST_URLS);

        int iteration = 0;
        for (final String url : externalMethods) {
            iteration++;
            final MacroImplementation macroImplementation = new ExternalRestMacro(iteration,url);
            macroImplementation.init(pwmApplication,userInfoBean,userDataReader);
            final Pattern pattern = macroImplementation.getRegExPattern();
            map.put(pattern,macroImplementation);
        }
        return map;
    }




    public String expandMacros(
            final String input
    ) {
        return expandMacros(input, null);
    }

    public String expandMacros(
            final String input,
            final StringReplacer stringReplacer
    )
    {
        if (input == null) {
            return null;
        }

        if (input.length() < 1) {
            return input;
        }

        String workingString = input;

        for (final Pattern pattern : macroImplementations.keySet()) {
            final MacroImplementation pwmMacro = macroImplementations.get(pattern);
            boolean matched = true;
            while (matched) {
                final Matcher matcher = pattern.matcher(workingString);
                if (matcher.find()) {
                    workingString = doReplace(workingString, pwmMacro, matcher, stringReplacer);
                } else {
                    matched = false;
                }
            }
        }

        return workingString;
    }

    private String doReplace(
            final String input,
            final MacroImplementation configVar,
            final Matcher matcher,
            final StringReplacer stringReplacer
    ) {
        final String matchedStr = matcher.group();
        final int startPos = matcher.start();
        final int endPos = matcher.end();
        String replaceStr = "";
        try {
            replaceStr = configVar.replaceValue(matchedStr);
        }  catch (Exception e) {
            LOGGER.error("error while replacing macro '" + matchedStr + "', error: " + e.getMessage());
        }

        if (replaceStr == null) {
            return input;
        }

        if (stringReplacer != null) {
            try {
                replaceStr = stringReplacer.replace(matchedStr, replaceStr);
            }  catch (Exception e) {
                LOGGER.error("error while executing '" + matchedStr + "' during StringReplacer.replace(), error: " + e.getMessage());
            }
        }

        if (replaceStr != null && replaceStr.length() > 0) {
            LOGGER.trace("replaced Macro " + matchedStr + " with value: " + replaceStr);
        }
        return new StringBuilder(input).replace(startPos, endPos, replaceStr).toString();
    }

    public static interface StringReplacer {
        public String replace(final String matchedMacro, final String newValue);
    }

    public static class URLEncoderReplacer implements StringReplacer {
        public String replace(String matchedMacro, String newValue) {
            try {
                return URLEncoder.encode(newValue, "UTF8"); // make sure replacement values are properly encoded
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("unexpected error attempting to url-encode macro values: " + e.getMessage(),e);
            }
            return newValue;
        }
    }
}