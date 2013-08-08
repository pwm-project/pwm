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

package password.pwm.config.value;

import com.google.gson.Gson;
import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.Helper;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.List;

public class PasswordValue extends StringValue {
    PasswordValue() {
    }

    public PasswordValue(String value) {
        super(value);
    }

    static PasswordValue fromJson(final String value) {
        return new PasswordValue(new Gson().fromJson(value, String.class));
    }

    static PasswordValue fromXmlValue(final Element settingElement, final String key) throws PwmOperationalException {
        final Element valueElement = settingElement.getChild("value");
        final String encodedValue = valueElement.getText();
        try {
            final SecretKey secretKey = Helper.SimpleTextCrypto.makeKey(key);
            final String decodedValue = Helper.SimpleTextCrypto.decryptValue(encodedValue, secretKey);
            final PasswordValue passwordValue = new PasswordValue();
            passwordValue.value = decodedValue;
            return passwordValue;
        } catch (Exception e) {
            final String errorMsg = "unable to decode encrypted password value for setting: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

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
            final SecretKey secretKey = Helper.SimpleTextCrypto.makeKey(key);
            final String encodedValue = Helper.SimpleTextCrypto.encryptValue(value, secretKey);
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

    public String toDebugString() {
        return "**removed**";
    }
}
