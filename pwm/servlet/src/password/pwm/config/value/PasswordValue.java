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

package password.pwm.config.value;

import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;

import javax.crypto.SecretKey;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PasswordValue extends StringValue {
    PasswordValue() {
    }

    boolean requiresStoredUpdate;

    public PasswordValue(String value) {
        super(value);
    }

    static PasswordValue fromJson(final String value) {
        return new PasswordValue(JsonUtil.getGson().fromJson(value, String.class));
    }

    static PasswordValue fromXmlValue(final Element settingElement, final String key) throws PwmOperationalException {
        final Element valueElement = settingElement.getChild("value");
        final String encodedValue = valueElement.getText();
        final String plainTextAttributeStr = valueElement.getAttributeValue("plaintext");
        final boolean plainText = plainTextAttributeStr != null && Boolean.parseBoolean(plainTextAttributeStr);
        final PasswordValue passwordValue = new PasswordValue();
        if (plainText) {
            passwordValue.value = encodedValue;
            passwordValue.requiresStoredUpdate = true;
        } else {
            try {
                final SecretKey secretKey = Helper.SimpleTextCrypto.makeKey(key);
                passwordValue.value = Helper.SimpleTextCrypto.decryptValue(encodedValue, secretKey);
                return passwordValue;
            } catch (Exception e) {
                final String errorMsg = "unable to decode encrypted password value for setting: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorMsg);
                throw new PwmOperationalException(errorInfo);
            }
        }
        return passwordValue;
    }

    public List<Element> toXmlValues(final String valueElementName) {
        throw new IllegalStateException("password xml output requires hash key");
    }

    public List<Element> toXmlValues(final String valueElementName, final String key) {
        if (value == null || value.length() < 1) {
            final Element valueElement = new Element(valueElementName);
            return Collections.singletonList(valueElement);
        }
        final Element valueElement = new Element(valueElementName);
        try {
            final String encodedValue = encryptValue(key,value);
            valueElement.addContent(encodedValue);
        } catch (Exception e) {
            valueElement.addContent("");
            throw new RuntimeException("missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage());
        }
        return Collections.singletonList(valueElement);
    }

    public String toString() {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    public String toDebugString(boolean prettyFormat, Locale locale) {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    public static String encryptValue(final String key, final String value)
            throws PwmUnrecoverableException, UnsupportedEncodingException, NoSuchAlgorithmException
    {
        final SecretKey secretKey = Helper.SimpleTextCrypto.makeKey(key);
        return Helper.SimpleTextCrypto.encryptValue(value, secretKey);
    }

    @Override
    public boolean requiresStoredUpdate()
    {
        return requiresStoredUpdate;
    }
}
