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

package password.pwm.ldap;

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LdapConnectionService implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LdapConnectionService.class);

    private final Map<LdapProfile, Map<Integer,ChaiProvider>> proxyChaiProviders = new ConcurrentHashMap<>();
    private final Map<LdapProfile, ErrorInformation> lastLdapErrors = new ConcurrentHashMap<>();
    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private AtomicLoopIntIncrementer slotIncrementer;
    private final ThreadLocal<ChaiProvider> threadLocalProvider = new ThreadLocal<>();

    public STATUS status()
    {
        return status;
    }

    public void init(final PwmApplication pwmApplication)
            throws PwmException
    {
        this.pwmApplication = pwmApplication;

        // read the lastLoginTime
        this.lastLdapErrors.putAll(readLastLdapFailure(pwmApplication));

        final int connectionsPerProfile = maxSlotsPerProfile(pwmApplication);
        LOGGER.trace("allocating " + connectionsPerProfile + " ldap proxy connections per profile");
        slotIncrementer = new AtomicLoopIntIncrementer(connectionsPerProfile);

        for (final LdapProfile ldapProfile: pwmApplication.getConfig().getLdapProfiles().values()) {
            proxyChaiProviders.put(ldapProfile, new ConcurrentHashMap<>());
        }

        status = STATUS.OPEN;
    }

    public void close()
    {
        status = STATUS.CLOSED;
        LOGGER.trace("closing ldap proxy connections");
        for (final LdapProfile ldapProfile : proxyChaiProviders.keySet()) {
            for (final int slot : proxyChaiProviders.get(ldapProfile).keySet()) {
                final ChaiProvider existingProvider = proxyChaiProviders.get(ldapProfile).get(slot);

                try {
                    existingProvider.close();
                } catch (Exception e) {
                    LOGGER.error("error closing ldap proxy connection: " + e.getMessage(), e);
                }
            }
        }
        proxyChaiProviders.clear();
    }

    public List<HealthRecord> healthCheck()
    {
        return null;
    }

    public ServiceInfoBean serviceInfo()
    {
        return new ServiceInfoBean(Collections.singletonList(DataStorageMethod.LDAP));
    }


    public ChaiProvider getProxyChaiProvider(final String identifier)
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get(identifier);
        return getProxyChaiProvider(ldapProfile);
    }

    public ChaiProvider getProxyChaiProvider(final LdapProfile identifier)
            throws PwmUnrecoverableException
    {
        if (threadLocalProvider.get() != null) {
            return threadLocalProvider.get();
        }

        final int slot = slotIncrementer.next();

        final ChaiProvider proxyChaiProvider = proxyChaiProviders.get(identifier).get(slot);

        if (proxyChaiProvider != null) {
            return proxyChaiProvider;
        }

        final LdapProfile ldapProfile = identifier == null
                ? pwmApplication.getConfig().getDefaultLdapProfile()
                : identifier;

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
            proxyChaiProviders.get(identifier).put(slot, newProvider);
            threadLocalProvider.set(newProvider);

            return newProvider;
        } catch (PwmUnrecoverableException e) {
            setLastLdapFailure(ldapProfile,e.getErrorInformation());
            throw e;
        } catch (Exception e) {
            final String errorMsg = "unexpected error creating new proxy ldap connection: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            LOGGER.error(errorInformation);
            throw new PwmUnrecoverableException(errorInformation);
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

    public Instant getLastLdapFailureTime(final LdapProfile ldapProfile) {
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

    private int maxSlotsPerProfile(final PwmApplication pwmApplication) {
        final int maxConnections = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.LDAP_PROXY_MAX_CONNECTIONS));
        final int perProfile = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.LDAP_PROXY_CONNECTION_PER_PROFILE));
        final int profileCount = pwmApplication.getConfig().getLdapProfiles().size();

        if ((perProfile * profileCount) >= maxConnections) {
            final int adjustedConnections = Math.min(1, (maxConnections / profileCount));
            LOGGER.warn("connections per profile (" + perProfile + ") multiplied by number of profiles ("
                    + profileCount + ") exceeds max connections (" + maxConnections  + "), will limit to " + adjustedConnections);
            return adjustedConnections;
        }

        return perProfile;
    }
}
