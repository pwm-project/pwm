/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.bean;

import lombok.Data;
import password.pwm.ldap.UserInfoBean;
import password.pwm.util.EventRateMeter;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Only information that is particular to the http session is stored in the
 * session bean.  Information more topical to the user is stored in {@link UserInfoBean}.</p>
 *
 * <p>For any given HTTP session using PWM, one and only one {@link LocalSessionStateBean} will be
 * created.</p>
 *
 * @author Jason D. Rivard
 */

@Data
public class LocalSessionStateBean implements Serializable
{

    private String srcAddress;
    private String srcHostname;
    private String forwardURL;
    private String logoutURL;
    private Locale locale;
    private String sessionID;
    private String theme;
    private String lastRequestURL;

    private String sessionVerificationKey = "key";

    private boolean debugInitialized;
    private boolean sessionVerified;

    private Instant pageLeaveNoticeTime;
    private Instant sessionCreationTime;
    private Instant sessionLastAccessedTime;

    private boolean passwordModified;
    private boolean privateUrlAccessed;

    private final AtomicInteger intruderAttempts = new AtomicInteger( 0 );
    private final AtomicInteger requestCount = new AtomicInteger( 0 );
    private final EventRateMeter.MovingAverage avgRequestDuration = new EventRateMeter.MovingAverage( TimeDuration.DAY );
    private boolean oauthInProgress;

    private boolean sessionIdRecycleNeeded;
    private boolean sameSiteCookieRecycleRequested;

    public void incrementIntruderAttempts( )
    {
        intruderAttempts.incrementAndGet();
    }

    public void clearIntruderAttempts( )
    {
        intruderAttempts.set( 0 );
    }
}

