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

package password.pwm.ws.client.rest.naaf;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.RecoveryVerificationMethod;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;
import password.pwm.util.macro.MacroMachine;

import java.util.*;

public class PwmNAAFVerificationMethod implements RecoveryVerificationMethod {
    private PwmApplication pwmApplication;
    private NAAFLoginSequence naafLoginSequence;
    private Locale locale;

    private static class UserPromptImpl implements UserPrompt {

        private final String identifier;
        private final String displayPrompt;

        public UserPromptImpl(String identifier, String displayPrompt) {
            this.identifier = identifier;
            this.displayPrompt = displayPrompt;
        }

        @Override
        public String getDisplayPrompt() {
            return displayPrompt;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }
    }

    @Override
    public List<UserPrompt> getCurrentPrompts() throws PwmUnrecoverableException {
        final Map<String,String> nextPrompts = naafLoginSequence.nextPrompt(locale);
        final List<UserPrompt> returnObj= new ArrayList<>();
        for (final String key : nextPrompts.keySet()) {
            returnObj.add(new UserPromptImpl(key,nextPrompts.get(key)));
        }
        return returnObj;
    }

    @Override
    public String getCurrentDisplayInstructions() {
        final String key = "Display_NAAF_" + naafLoginSequence.currentMethod();
        return LocaleHelper.getLocalizedMessage(locale, key, pwmApplication.getConfig(), Display.class);
    }

    @Override
    public ErrorInformation respondToPrompts(Map<String, String> answers) throws PwmUnrecoverableException {

        final String errorMsg = naafLoginSequence.answerPrompts(answers);
        if (errorMsg == null) {
            return null;
        }

        return new ErrorInformation(PwmError.ERROR_REMOTE_ERROR_VALUE,errorMsg);

    }

    @Override
    public VerificationState getVerificationState() {
        return naafLoginSequence.status();
    }

    @Override
    public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean, SessionLabel sessionLabel, Locale locale)
            throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.locale = locale;

        String serverUrl = pwmApplication.getConfig().readSettingAsString(PwmSetting.NAAF_WS_URL);
        String naafUsername = pwmApplication.getConfig().readSettingAsString(PwmSetting.NAAF_USER_IDENTIFIER);

        final MacroMachine macroMachine = MacroMachine.forUser(pwmApplication, PwmConstants.DEFAULT_LOCALE, sessionLabel, userInfoBean.getUserIdentity());
        serverUrl = macroMachine.expandMacros(serverUrl);
        naafUsername = macroMachine.expandMacros(naafUsername);

        NAAFEndPoint naafEndPoint = new NAAFEndPoint(pwmApplication, serverUrl, locale);
        naafEndPoint.establishEndpointSession();

        Set<NAAFLoginMethod> loginMethods = pwmApplication.getConfig().readSettingAsOptionList(PwmSetting.NAAF_METHODS, NAAFLoginMethod.class);
        naafLoginSequence = new NAAFLoginSequence(naafEndPoint, loginMethods, naafUsername, locale);
    }
}
