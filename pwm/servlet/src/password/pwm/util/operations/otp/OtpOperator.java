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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.util.operations.otp;

import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.util.otp.OTPUserRecord;

/**
 *
 * @author mpieters
 */
public interface OtpOperator {
    
    public OTPUserRecord readOtpUserConfiguration(
            final UserIdentity theUser,
            final String userGUID
    )
            throws PwmUnrecoverableException;

    public void writeOtpUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity theUser,
            final String userGuid,
            final OTPUserRecord otpConfig
    )
            throws PwmUnrecoverableException;

    public void clearOtpUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity theUser,
            final String userGuid
    )
            throws PwmUnrecoverableException;

    public void close();
}
