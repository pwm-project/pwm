/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.bean;

import lombok.Data;
import password.pwm.ldap.UserInfoBean;
import password.pwm.util.java.MovingAverage;
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
    private boolean captchaBypassedViaParameter;

    private final AtomicInteger intruderAttempts = new AtomicInteger( 0 );
    private final AtomicInteger requestCount = new AtomicInteger( 0 );
    private final MovingAverage avgRequestDuration = new MovingAverage( TimeDuration.DAY );
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

