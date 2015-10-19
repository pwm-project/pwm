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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;

import java.util.*;

public class NAAFMethods {
    public static class NAAFPasswordMethodHandler implements NAAFMethodHandler {
        private static final String PASSWORD_FIELD_NAME = "answer";
        private NAAFLoginSequence naafLoginSequence;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) {
            final String prompt = LocaleHelper.getLocalizedMessage(locale, Display.Field_CurrentPassword, null);
            return Collections.singletonMap(PASSWORD_FIELD_NAME, prompt);
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFLdapPasswordMethodHandler implements NAAFMethodHandler {
        private static final String PASSWORD_FIELD_NAME = "answer";
        private NAAFLoginSequence naafLoginSequence;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) {
            final String prompt = LocaleHelper.getLocalizedMessage(locale, Display.Field_CurrentPassword, null);
            return Collections.singletonMap(PASSWORD_FIELD_NAME, prompt);
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFRadiusMethodHandler implements NAAFMethodHandler {
        private static final String PASSWORD_FIELD_NAME = "answer";
        private NAAFLoginSequence naafLoginSequence;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) {
            final String prompt = LocaleHelper.getLocalizedMessage(locale, Display.Field_CurrentPassword, null);
            return Collections.singletonMap(PASSWORD_FIELD_NAME, prompt);
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFSecurityQuestionsMethodHandler implements NAAFMethodHandler {
        private NAAFLoginSequence naafLoginSequence;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) throws PwmUnrecoverableException {
            naafLoginSequence.sendResponse(null);
            return naafLoginSequence.getLastResponseBean().getQuestions();
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            final HashMap<String,Object> responseData = new HashMap<>();
            responseData.put("answers",answers);
            return naafLoginSequence.sendResponse(responseData);
        }
   }

    public static class NAAFEmailOTPMethodHandler implements NAAFMethodHandler {
        private NAAFLoginSequence naafLoginSequence;
        private boolean otpSent;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) throws PwmUnrecoverableException {
            if (!otpSent) {
                naafLoginSequence.sendResponse(null); // triggers sms send
                otpSent = true;
            }
            final Map<String,String> prompts = new LinkedHashMap<>();
            prompts.put("answer","Password");
            return prompts;
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFSMSOTPMethodHandler implements NAAFMethodHandler {
        private NAAFLoginSequence naafLoginSequence;
        private boolean otpSent;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) throws PwmUnrecoverableException {
            if (!otpSent) {
                naafLoginSequence.sendResponse(null); // triggers sms send
                otpSent = true;
            }
            final Map<String,String> prompts = new LinkedHashMap<>();
            prompts.put("answer","Password");
            return prompts;
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFSmartphoneMethodHandler implements NAAFMethodHandler {
        private NAAFLoginSequence naafLoginSequence;
        private boolean initialized;


        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) throws PwmUnrecoverableException {
            if (!initialized) {
                naafLoginSequence.sendResponse(null); // triggers sms send
                initialized= true;
            }
            return Collections.emptyMap();
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFTOTPMethodHandler implements NAAFMethodHandler {
        private NAAFLoginSequence naafLoginSequence;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) throws PwmUnrecoverableException {
            final Map<String,String> prompts = new LinkedHashMap<>();
            prompts.put("answer","Password");
            return prompts;
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFHOTPMethodHandler implements NAAFMethodHandler {
        private NAAFLoginSequence naafLoginSequence;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) throws PwmUnrecoverableException {
            final Map<String,String> prompts = new LinkedHashMap<>();
            prompts.put("answer","Password");
            return prompts;
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

    public static class NAAFVoiceMethodHandler implements NAAFMethodHandler {
        private NAAFLoginSequence naafLoginSequence;
        private boolean initialized;

        @Override
        public void init(NAAFLoginSequence naafLoginSequence) {
            this.naafLoginSequence = naafLoginSequence;
        }

        @Override
        public Map<String, String> getPrompts(Locale locale) throws PwmUnrecoverableException {
            if (!initialized) {
                naafLoginSequence.sendResponse(null); // triggers sms send
                initialized= true;
            }
            return Collections.emptyMap();
        }

        @Override
        public String answerPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
            return naafLoginSequence.sendResponse(new HashMap<>(answers));
        }
    }

}