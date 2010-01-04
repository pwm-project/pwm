/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.config;

import java.io.Serializable;

public class PasswordStatus implements Serializable {

    boolean expired;
    boolean preExpired;
    boolean violatesPolicy;
    boolean warnPeriod;

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(final boolean expired) {
        this.expired = expired;
    }

    public boolean isPreExpired() {
        return preExpired;
    }

    public void setPreExpired(final boolean preExpired) {
        this.preExpired = preExpired;
    }


    public boolean isViolatesPolicy() {
        return violatesPolicy;
    }

    public void setViolatesPolicy(final boolean violatesPolicy) {
        this.violatesPolicy = violatesPolicy;
    }

    public boolean isWarnPeriod() {
        return warnPeriod;
    }

    public void setWarnPeriod(final boolean warnPeriod) {
        this.warnPeriod = warnPeriod;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PasswordStatus {");
        sb.append("expired=").append(expired);
        sb.append(", pre-expired=").append(preExpired);
        sb.append(", warn=").append(warnPeriod);
        sb.append(", violatesPolicy=").append(violatesPolicy);
        sb.append("}");
        return sb.toString();
    }
}
