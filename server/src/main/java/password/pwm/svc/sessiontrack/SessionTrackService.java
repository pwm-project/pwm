/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.svc.sessiontrack;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import password.pwm.PwmApplication;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.pub.SessionStateInfoBean;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmSession;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionTrackService implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(SessionTrackService.class);

    private final transient Map<PwmSession,Boolean> pwmSessions = new ConcurrentHashMap<>();

    private final Cache<UserIdentity,Object> recentLoginCache = Caffeine.newBuilder()
            .maximumSize(10)
            .build();

    private PwmApplication pwmApplication;

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(final PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
    }

    @Override
    public void close() {
        pwmSessions.clear();
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo() {
        return null;
    }

    public enum DebugKey {
        HttpSessionCount,
        HttpSessionTotalSize,
        HttpSessionAvgSize,
    }

    public void addSessionData(final PwmSession pwmSession) {
        pwmSessions.put(pwmSession,Boolean.FALSE);
    }

    public void removeSessionData(final PwmSession pwmSession) {
        pwmSessions.remove(pwmSession);
    }

    private Set<PwmSession> copyOfSessionSet() {
        final Set<PwmSession> newSet = new HashSet<>();
        newSet.addAll(pwmSessions.keySet());
        return newSet;

    }

    public Map<DebugKey, String> getDebugData() {
        try {
            final Collection<PwmSession> sessionCopy = copyOfSessionSet();
            int sessionCounter = 0;
            long sizeTotal = 0;
            for (final PwmSession pwmSession : sessionCopy) {
                try {
                    sizeTotal += pwmSession.size();
                    sessionCounter++;
                } catch (Exception e) {
                    LOGGER.error("error during session size calculation: " + e.getMessage());
                }
            }
            final Map<DebugKey, String> returnMap = new HashMap<>();
            returnMap.put(DebugKey.HttpSessionCount, String.valueOf(sessionCounter));
            returnMap.put(DebugKey.HttpSessionTotalSize, String.valueOf(sizeTotal));
            returnMap.put(DebugKey.HttpSessionAvgSize,
                    sessionCounter < 1 ? "0" : String.valueOf((int) (sizeTotal / sessionCounter)));
            return returnMap;
        } catch (Exception e) {
            LOGGER.error("error during session debug generation: " + e.getMessage());
        }
        return Collections.emptyMap();
    }

    private Set<PwmSession> currentValidSessionSet() {
        final Set<PwmSession> returnSet = new HashSet<>();
        for (final PwmSession pwmSession : copyOfSessionSet()) {
            if (pwmSession != null) {
                returnSet.add(pwmSession);
            }
        }
        return returnSet;
    }

    public Iterator<SessionStateInfoBean> getSessionInfoIterator() {
        final Iterator<PwmSession> sessionIterator = new HashSet<>(currentValidSessionSet()).iterator();
        return new Iterator<SessionStateInfoBean>() {
            @Override
            public boolean hasNext() {
                return sessionIterator.hasNext();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SessionStateInfoBean next() {
                final PwmSession pwmSession = sessionIterator.next();
                if (pwmSession != null) {
                    return infoBeanFromPwmSession(pwmSession);
                }
                return null;
            }
        };
    }


    private static SessionStateInfoBean infoBeanFromPwmSession(final PwmSession loopSession) {
        final LocalSessionStateBean loopSsBean = loopSession.getSessionStateBean();
        final LoginInfoBean loginInfoBean = loopSession.getLoginInfoBean();

        final SessionStateInfoBean sessionStateInfoBean = new SessionStateInfoBean();

        sessionStateInfoBean.setLabel(loopSession.getSessionStateBean().getSessionID());
        sessionStateInfoBean.setCreateTime(loopSession.getSessionStateBean().getSessionCreationTime());
        sessionStateInfoBean.setLastTime(loopSession.getSessionStateBean().getSessionLastAccessedTime());
        sessionStateInfoBean.setIdle(loopSession.getIdleTime().asCompactString());
        sessionStateInfoBean.setLocale(loopSsBean.getLocale());
        sessionStateInfoBean.setSrcAddress(loopSsBean.getSrcAddress());
        sessionStateInfoBean.setSrcHost(loopSsBean.getSrcHostname());
        sessionStateInfoBean.setLastUrl(loopSsBean.getLastRequestURL());
        sessionStateInfoBean.setIntruderAttempts(loopSsBean.getIntruderAttempts());

        if (loopSession.isAuthenticated()) {
            final UserInfo loopUiBean = loopSession.getUserInfo();
            sessionStateInfoBean.setLdapProfile(loginInfoBean.isAuthenticated()
                    ? loopUiBean.getUserIdentity().getLdapProfileID()
                    : "");

            sessionStateInfoBean.setUserDN(loginInfoBean.isAuthenticated()
                    ? loopUiBean.getUserIdentity().getUserDN()
                    : "");

            try {
                sessionStateInfoBean.setUserID(loginInfoBean.isAuthenticated()
                        ? loopUiBean.getUsername()
                        : "");
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("unexpected error reading username: " + e.getMessage(),e);
            }
        }

        return sessionStateInfoBean;
    }

    public int sessionCount() {
        return currentValidSessionSet().size();
    }

    public void addRecentLogin(final UserIdentity userIdentity) {
        recentLoginCache.put(userIdentity,this);
    }

    public List<UserIdentity> getRecentLogins() {
        return Collections.unmodifiableList(new ArrayList<>(recentLoginCache.asMap().keySet()));
    }


}
