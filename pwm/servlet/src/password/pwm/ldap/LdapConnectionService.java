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

package password.pwm.ldap;

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.*;

public class LdapConnectionService implements PwmService {
    final private static PwmLogger LOGGER = PwmLogger.forClass(LdapConnectionService.class);

    private final Map<String,ChaiProvider> proxyChaiProviders = new HashMap<>();
    private final Map<LdapProfile,ErrorInformation> lastLdapErrors = new HashMap<>();
    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;

    public STATUS status()
    {
        return status;
    }

    public void init(PwmApplication pwmApplication)
            throws PwmException
    {
        this.pwmApplication = pwmApplication;

        // read the lastLoginTime
        this.lastLdapErrors.putAll(readLastLdapFailure(pwmApplication));

        status = STATUS.OPEN;
    }

    public void close()
    {
        status = STATUS.CLOSED;
        LOGGER.trace("closing ldap proxy connections");
        for (final String id : proxyChaiProviders.keySet()) {
            final ChaiProvider existingProvider = proxyChaiProviders.get(id);

            try {
                existingProvider.close();
            } catch (Exception e) {
                LOGGER.error("error closing ldap proxy connection: " + e.getMessage(), e);
            }
        }
        proxyChaiProviders.clear();
    }

    public List<HealthRecord> healthCheck()
    {
        return null;
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.singletonList(DataStorageMethod.LDAP));
    }


    public ChaiProvider getProxyChaiProvider(final LdapProfile ldapProfile)
            throws PwmUnrecoverableException
    {
        return getProxyChaiProvider(ldapProfile.getIdentifier());
    }

    public ChaiProvider getProxyChaiProvider(final String identifier)
            throws PwmUnrecoverableException
    {
        final ChaiProvider proxyChaiProvider = proxyChaiProviders.get(identifier == null ? "" : identifier);
        if (proxyChaiProvider != null) {
            return proxyChaiProvider;
        }

        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get(identifier == null ? "" : identifier);
        if (ldapProfile == null) {
            final String errorMsg = "unknown ldap profile requested connection: " + identifier;
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NO_LDAP_CONNECTION,errorMsg));
        }

        try {
            final ChaiProvider newProvider = LdapOperationsHelper.openProxyChaiProvider(
                    null,
                    ldapProfile,
                    pwmApplication.getConfig(),
                    pwmApplication.getStatisticsManager()
            );
            proxyChaiProviders.put(identifier, newProvider);
            return newProvider;
        } catch (PwmUnrecoverableException e) {
            setLastLdapFailure(ldapProfile,e.getErrorInformation());
            throw e;
        }
    }

    public void setLastLdapFailure(final LdapProfile ldapProfile, final ErrorInformation errorInformation) {
        lastLdapErrors.put(ldapProfile, errorInformation);
        final HashMap<String,ErrorInformation> outputMap = new HashMap<>();
        for (final LdapProfile loopProfile : lastLdapErrors.keySet()) {
            outputMap.put(loopProfile.getIdentifier(), lastLdapErrors.get(loopProfile));
        }
        final String jsonString = JsonUtil.serialize(outputMap);
        pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.LAST_LDAP_ERROR, jsonString);
    }

    public Map<LdapProfile,ErrorInformation> getLastLdapFailure() {
        return Collections.unmodifiableMap(lastLdapErrors);
    }

    public Date getLastLdapFailureTime(final LdapProfile ldapProfile) {
        final ErrorInformation errorInformation = lastLdapErrors.get(ldapProfile);
        if (errorInformation != null) {
            return errorInformation.getDate();
        }
        return null;
    }

    private static Map<LdapProfile,ErrorInformation> readLastLdapFailure(final PwmApplication pwmApplication) {
        String lastLdapFailureStr = null;
        try {
            lastLdapFailureStr = pwmApplication.readAppAttribute(PwmApplication.AppAttribute.LAST_LDAP_ERROR, String.class);
            if (lastLdapFailureStr != null && lastLdapFailureStr.length() > 0) {
                final Map<String, ErrorInformation> fromJson = JsonUtil.deserialize(lastLdapFailureStr,new TypeToken<Map<String, ErrorInformation>>() {});
                final Map<LdapProfile, ErrorInformation> returnMap = new HashMap<>();
                for (final String id : fromJson.keySet()) {
                    final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get(id);
                    if (ldapProfile != null) {
                        returnMap.put(ldapProfile, fromJson.get(id));
                    }
                }
                return returnMap;
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error loading cached lastLdapFailure statuses: " + e.getMessage() + ", input=" + lastLdapFailureStr);
        }
        return Collections.emptyMap();
    }
}
