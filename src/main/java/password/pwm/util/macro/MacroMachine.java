/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.Helper;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroMachine {
    private static final PwmLogger LOGGER = PwmLogger.forClass(MacroMachine.class);

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final UserInfoBean userInfoBean;
    private final LoginInfoBean loginInfoBean;
    private final UserDataReader userDataReader;

    private static final Map<MacroImplementation.Scope,Map<Pattern,MacroImplementation>> BUILTIN_MACROS = makeImplementations();

    public MacroMachine(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfoBean userInfoBean,
            final LoginInfoBean loginInfoBean,
            final UserDataReader userDataReader
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.userInfoBean = userInfoBean;
        this.loginInfoBean = loginInfoBean;
        this.userDataReader = userDataReader;
    }

    private static  Map<MacroImplementation.Scope,Map<Pattern,MacroImplementation>> makeImplementations() {
        final Map<Class<? extends MacroImplementation>, MacroImplementation.Scope> implementations = new LinkedHashMap<>();
        implementations.putAll(StandardMacros.STANDARD_MACROS);
        implementations.putAll(InternalMacros.INTERNAL_MACROS);
        final LinkedHashMap<MacroImplementation.Scope,Map<Pattern,MacroImplementation>> map = new LinkedHashMap<>();

        for (Class macroClass : implementations.keySet()) {
            final MacroImplementation.Scope scope = implementations.get(macroClass);
            try {
                final MacroImplementation macroImplementation = (MacroImplementation)macroClass.newInstance();
                final Pattern pattern = macroImplementation.getRegExPattern();
                if (!map.containsKey(scope)) {
                    map.put(scope, new LinkedHashMap<Pattern, MacroImplementation>());
                }
                map.get(scope).put(pattern,macroImplementation);
            } catch (Exception e) {
                LOGGER.error("unable to load macro class " + macroClass.getName() + ", error: " + e.getMessage());
            }
        }

        return map;
    }

    private Map<Pattern,MacroImplementation> makeExternalImplementations(PwmApplication pwmApplication) {
        final LinkedHashMap<Pattern,MacroImplementation> map = new LinkedHashMap<>();
        final List<String> externalMethods = pwmApplication == null
                ? Collections.<String>emptyList()
                : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EXTERNAL_MACROS_REST_URLS);

        int iteration = 0;
        for (final String url : externalMethods) {
            iteration++;
            final MacroImplementation macroImplementation = new ExternalRestMacro(iteration,url);
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

        final MacroImplementation.MacroRequestInfo macroRequestInfo = new MacroImplementation.MacroRequestInfo() {
            @Override
            public PwmApplication getPwmApplication()
            {
                return pwmApplication;
            }

            @Override
            public UserInfoBean getUserInfoBean()
            {
                return userInfoBean;
            }

            @Override
            public LoginInfoBean getLoginInfoBean()
            {
                return loginInfoBean;
            }

            @Override
            public UserDataReader getUserDataReader()
            {
                return userDataReader;
            }
        };

        final Set<MacroImplementation.Scope> scopes = effectiveScopes(macroRequestInfo);
        final Map<Pattern,MacroImplementation> macroImplementations = new LinkedHashMap<>();
        for (final MacroImplementation.Scope scope : scopes) {
            macroImplementations.putAll(BUILTIN_MACROS.get(scope));
        }
        if (scopes.contains(MacroImplementation.Scope.User)) {
            macroImplementations.putAll(makeExternalImplementations(pwmApplication));
        }

        String workingString = input;
        String previousString = workingString;

        for (final Pattern pattern : macroImplementations.keySet()) {
            final MacroImplementation pwmMacro = macroImplementations.get(pattern);
            boolean matched = true;
            while (matched) {
                final Matcher matcher = pattern.matcher(workingString);
                if (matcher.find()) {
                    workingString = doReplace(workingString, pwmMacro, matcher, stringReplacer, macroRequestInfo);
                    if (workingString.equals(previousString)) {
                        LOGGER.warn(sessionLabel, "macro replace was called but input string was not modified.  "
                                + " macro=" + pwmMacro.getClass().getName() + ", pattern=" + pwmMacro.getRegExPattern().toString());
                        break;
                    }
                } else {
                    matched = false;
                }
            }
        }

        return workingString;
    }

    private static Set<MacroImplementation.Scope> effectiveScopes(MacroImplementation.MacroRequestInfo macroRequestInfo) {
        final Set<MacroImplementation.Scope> scopes = new HashSet<>();
        scopes.add(MacroImplementation.Scope.Static);

        final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();
        final PwmApplicationMode mode = pwmApplication != null ? pwmApplication.getApplicationMode() : PwmApplicationMode.ERROR;
        final boolean appModeOk = mode == PwmApplicationMode.RUNNING || mode == PwmApplicationMode.CONFIGURATION;
        if (appModeOk) {
            scopes.add(MacroImplementation.Scope.System);

            if (macroRequestInfo.getUserInfoBean() != null || macroRequestInfo.getUserDataReader() != null) {
                scopes.add(MacroImplementation.Scope.User);
            }
        }
        return Collections.unmodifiableSet(scopes);
    }


    private String doReplace(
            final String input,
            final MacroImplementation macroImplementation,
            final Matcher matcher,
            final StringReplacer stringReplacer,
            final MacroImplementation.MacroRequestInfo macroRequestInfo
    ) {
        final String matchedStr = matcher.group();
        final int startPos = matcher.start();
        final int endPos = matcher.end();
        String replaceStr = "";
        try {
            replaceStr = macroImplementation.replaceValue(matchedStr, macroRequestInfo);
        } catch (MacroParseException e) {
            LOGGER.debug(sessionLabel, "macro parse error replacing macro '" + matchedStr + "', error: " + e.getMessage());
            if (pwmApplication != null) {
                replaceStr = "[" + e.getErrorInformation().toUserStr(PwmConstants.DEFAULT_LOCALE, macroRequestInfo.getPwmApplication().getConfig()) + "]";
            } else {
                replaceStr = "[" + e.getErrorInformation().toUserStr(PwmConstants.DEFAULT_LOCALE, null) + "]";
            }
        }  catch (Exception e) {
            LOGGER.error(sessionLabel, "error while replacing macro '" + matchedStr + "', error: " + e.getMessage());
        }

        if (replaceStr == null) {
            return input;
        }

        if (stringReplacer != null) {
            try {
                replaceStr = stringReplacer.replace(matchedStr, replaceStr);
            }  catch (Exception e) {
                LOGGER.error(sessionLabel,"unexpected error while executing '" + matchedStr + "' during StringReplacer.replace(), error: " + e.getMessage());
            }
        }

        if (replaceStr != null && replaceStr.length() > 0) {
            final boolean sensitive = Helper.enumArrayContainsValue(macroImplementation.flags(), MacroImplementation.MacroDefinitionFlag.SensitiveValue);
            final boolean debugOnlyLogging = Helper.enumArrayContainsValue(macroImplementation.flags(), MacroImplementation.MacroDefinitionFlag.OnlyDebugLogging);
            if (!debugOnlyLogging || (pwmApplication != null && pwmApplication.getConfig().isDevDebugMode())) {
                LOGGER.trace(sessionLabel, "replaced macro " + matchedStr + " with value: "
                        + (sensitive ? PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT : replaceStr));
            }
        }
        return new StringBuilder(input).replace(startPos, endPos, replaceStr).toString();
    }

    public static MacroMachine forStatic() {
        return new MacroMachine(null,null,null,null,null);
    }

    public interface StringReplacer {
        String replace(final String matchedMacro, final String newValue);
    }

    public static class URLEncoderReplacer implements StringReplacer {
        public String replace(String matchedMacro, String newValue) {
            return StringUtil.urlEncode(newValue); // make sure replacement values are properly encoded
        }
    }

    public static MacroMachine forUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        return forUser(pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getSessionLabel(),userIdentity);
    }

    public static MacroMachine forUser(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);
        final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, sessionLabel);
        final UserInfoBean userInfoBean = userStatusReader.populateUserInfoBean(userLocale, userIdentity);
        return new MacroMachine(pwmApplication, sessionLabel, userInfoBean, null, userDataReader);
    }

    public static MacroMachine forNonUserSpecific(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        return new MacroMachine(pwmApplication, sessionLabel, null, null, null);
    }

}