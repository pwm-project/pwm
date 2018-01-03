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

package password.pwm.bean;

import lombok.Data;
import password.pwm.ldap.UserInfoBean;
import password.pwm.util.secure.PwmRandom;

import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;

/**
 * Only information that is particular to the http session is stored in the
 * session bean.  Information more topical to the user is stored in {@link UserInfoBean}.
 * <p/>
 * For any given HTTP session using PWM, one and only one {@link LocalSessionStateBean} will be
 * created.
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

    private int intruderAttempts;
    private boolean oauthInProgress;

    private int sessionVerificationKeyLength;
    private boolean sessionIdRecycleNeeded;

    public LocalSessionStateBean( final int sessionVerificationKeyLength )
    {
        this.sessionVerificationKeyLength = sessionVerificationKeyLength;
    }

    public void incrementIntruderAttempts( )
    {
        intruderAttempts++;
    }

    public void clearIntruderAttempts( )
    {
        intruderAttempts = 0;
    }

    public void regenerateSessionVerificationKey( )
    {
        sessionVerificationKey = PwmRandom.getInstance().alphaNumericString( sessionVerificationKeyLength ) + Long.toHexString( System.currentTimeMillis() );
    }
}

