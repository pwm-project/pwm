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

package password.pwm.bean.pub;

import java.time.Instant;
import java.util.Locale;

public class SessionStateInfoBean implements PublishedBean {
    private String label;
    private Instant createTime;
    private Instant lastTime;
    private String idle;
    private Locale locale;
    private String ldapProfile;
    private String userDN;
    private String userID;
    private String srcAddress;
    private String srcHost;
    private String lastUrl;
    private int intruderAttempts;

    public String getLabel() {
        return label;
    }

    public void setLabel(final String label) {
        this.label = label;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final Instant createTime) {
        this.createTime = createTime;
    }

    public Instant getLastTime() {
        return lastTime;
    }

    public void setLastTime(final Instant lastTime) {
        this.lastTime = lastTime;
    }

    public String getIdle() {
        return idle;
    }

    public void setIdle(final String idle) {
        this.idle = idle;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(final Locale locale) {
        this.locale = locale;
    }

    public String getLdapProfile() {
        return ldapProfile;
    }

    public void setLdapProfile(final String ldapProfile) {
        this.ldapProfile = ldapProfile;
    }

    public String getUserDN() {
        return userDN;
    }

    public void setUserDN(final String userDN) {
        this.userDN = userDN;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(final String userID) {
        this.userID = userID;
    }

    public String getSrcAddress() {
        return srcAddress;
    }

    public void setSrcAddress(final String srcAddress) {
        this.srcAddress = srcAddress;
    }

    public String getSrcHost() {
        return srcHost;
    }

    public void setSrcHost(final String srcHost) {
        this.srcHost = srcHost;
    }

    public String getLastUrl() {
        return lastUrl;
    }

    public void setLastUrl(final String lastUrl) {
        this.lastUrl = lastUrl;
    }

    public int getIntruderAttempts() {
        return intruderAttempts;
    }

    public void setIntruderAttempts(final int intruderAttempts) {
        this.intruderAttempts = intruderAttempts;
    }
}
