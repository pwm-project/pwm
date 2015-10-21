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

package password.pwm.util.secure;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public class SecureService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SecureService.class);

    private PwmSecurityKey pwmSecurityKey;
    private PwmBlockAlgorithm defaultBlockAlgorithm;
    private PwmHashAlgorithm defaultHashAlgorithm;

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        final Configuration config = pwmApplication.getConfig();
        pwmSecurityKey = config.getSecurityKey();
        {
            final String defaultBlockAlgString = config.readAppProperty(AppProperty.SECURITY_DEFAULT_EPHEMERAL_BLOCK_ALG);
            defaultBlockAlgorithm = Helper.readEnumFromString(PwmBlockAlgorithm.class, PwmBlockAlgorithm.AES, defaultBlockAlgString);
            LOGGER.debug("using default ephemeral block algorithm: "+ defaultBlockAlgorithm.getLabel());
        }
        {
            final String defaultHashAlgString = config.readAppProperty(AppProperty.SECURITY_DEFAULT_EPHEMERAL_HASH_ALG);
            defaultHashAlgorithm = Helper.readEnumFromString(PwmHashAlgorithm.class, PwmHashAlgorithm.SHA512, defaultHashAlgString);
            LOGGER.debug("using default ephemeral hash algorithm: "+ defaultHashAlgString.toString());
        }
    }

    @Override
    public void close() {

    }

    @Override
    public List<HealthRecord> healthCheck() {
        return null;
    }

    @Override
    public ServiceInfo serviceInfo() {
        return null;
    }

    public PwmBlockAlgorithm getDefaultBlockAlgorithm() {
        return defaultBlockAlgorithm;
    }

    public PwmHashAlgorithm getDefaultHashAlgorithm() {
        return defaultHashAlgorithm;
    }

    public String encryptToString(final String value)
            throws PwmUnrecoverableException
    {
        return SecureEngine.encryptToString(value, pwmSecurityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE);
    }

    public String encryptObjectToString(final Serializable serializableObject) throws PwmUnrecoverableException {
        final String jsonValue = JsonUtil.serialize(serializableObject);
        return encryptToString(jsonValue);
    }

    public String decryptStringValue(
            final String value
    )
            throws PwmUnrecoverableException
    {
        return SecureEngine.decryptStringValue(value, pwmSecurityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE);
    }

    public <T extends Serializable> T decryptObject(final String value, Class<T> returnClass) throws PwmUnrecoverableException {
        final String decryptedValue = decryptStringValue(value);
        return JsonUtil.deserialize(decryptedValue, returnClass);
    }

    public String hash(
            final String input
    )
            throws PwmUnrecoverableException
    {
        return SecureEngine.hash(input, defaultHashAlgorithm);
    }

    public String hash(
            final File file
    )
            throws IOException, PwmUnrecoverableException
    {
        return SecureEngine.hash(file, defaultHashAlgorithm);
    }
}
