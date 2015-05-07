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

package password.pwm.config.value;

import org.jdom2.Element;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.SecureHelper;

import javax.crypto.SecretKey;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PasswordValue implements StoredValue {
    private PasswordData value;

    PasswordValue() {
    }

    boolean requiresStoredUpdate;

    public PasswordValue(PasswordData passwordData) {
        value = passwordData;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public PasswordValue fromJson(final String value)
            {
                final String strValue = JsonUtil.deserialize(value, String.class);
                if (strValue != null && !strValue.isEmpty()) {
                    try {
                        return new PasswordValue(new PasswordData(strValue));
                    } catch (PwmUnrecoverableException e) {
                        throw new IllegalStateException(
                                "PasswordValue can not be json de-serialized: " + e.getMessage());
                    }
                }
                return new PasswordValue();
            }

            public PasswordValue fromXmlElement(
                    final Element settingElement,
                    final String key
            )
                    throws PwmOperationalException, PwmUnrecoverableException
            {
                final Element valueElement = settingElement.getChild("value");
                final String rawValue = valueElement.getText();

                final PasswordValue newPasswordValue = new PasswordValue();
                if (rawValue == null || rawValue.isEmpty()) {
                    return newPasswordValue;
                }

                final boolean plainTextSetting;
                {
                    final String plainTextAttributeStr = valueElement.getAttributeValue("plaintext");
                    plainTextSetting = plainTextAttributeStr != null && Boolean.parseBoolean(plainTextAttributeStr);
                }

                if (plainTextSetting) {
                    newPasswordValue.value = new PasswordData(rawValue);
                    newPasswordValue.requiresStoredUpdate = true;
                } else {
                    try {
                        final SecretKey secretKey = SecureHelper.makeKey(key);
                        newPasswordValue.value = new PasswordData(SecureHelper.decryptStringValue(rawValue, secretKey, false, SecureHelper.BlockAlgorithm.CONFIG));
                        return newPasswordValue;
                    } catch (Exception e) {
                        final String errorMsg = "unable to decode encrypted password value for setting: " + e.getMessage();
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorMsg);
                        throw new PwmOperationalException(errorInfo);
                    }
                }
                return newPasswordValue;
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        throw new IllegalStateException("password xml output requires hash key");
    }

    @Override
    public Object toNativeObject()
    {
        return value;
    }

    @Override
    public List<String> validateValue(PwmSetting pwm)
    {
        return Collections.emptyList();
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 0;
    }

    public List<Element> toXmlValues(final String valueElementName, final String key) {
        if (value == null) {
            final Element valueElement = new Element(valueElementName);
            return Collections.singletonList(valueElement);
        }
        final Element valueElement = new Element(valueElementName);
        try {
            final String encodedValue = encryptValue(key,value.getStringValue());
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

    private static String encryptValue(final String key, final String value)
            throws PwmUnrecoverableException, UnsupportedEncodingException, NoSuchAlgorithmException
    {
        final SecretKey secretKey = SecureHelper.makeKey(key);
        return SecureHelper.encryptToString(value, secretKey);
    }

    public boolean requiresStoredUpdate()
    {
        return requiresStoredUpdate;
    }

    @Override
    public String valueHash() throws PwmUnrecoverableException {
        return value == null ? "" : SecureHelper.hash(JsonUtil.serialize(value.getStringValue()));
    }
}
