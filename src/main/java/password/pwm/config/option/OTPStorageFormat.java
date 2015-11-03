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

package password.pwm.config.option;

/**
 * One Time Password Storage Format
 */
public enum OTPStorageFormat {

    PWM(true, true),
    BASE32SECRET(false),
    OTPURL(false),
    PAM(true, false);

    private final boolean useRecoveryCodes;
    private final boolean hashRecoveryCodes;

    /**
     * Constructor.
     *
     * @param useRecoveryCodes
     */
    OTPStorageFormat(boolean useRecoveryCodes) {
        this.useRecoveryCodes = useRecoveryCodes;
        this.hashRecoveryCodes = useRecoveryCodes; // defaults to true, if recovery codes enabled.
    }

    /**
     * Constructor.
     *
     * @param useRecoveryCodes
     * @param hashRecoveryCodes
     */
    OTPStorageFormat(
            boolean useRecoveryCodes,
            boolean hashRecoveryCodes
    ) {
        this.useRecoveryCodes = useRecoveryCodes;
        this.hashRecoveryCodes = useRecoveryCodes && hashRecoveryCodes;
    }

    /**
     * Check support for recovery codes.
     * @return true if recovery codes are supported.
     */
    public boolean supportsRecoveryCodes() {
        return useRecoveryCodes;
    }

    /**
     * Check support for hashed recovery codes.
     * @return true if recovery codes are supported and hashes are to be used.
     */
    public boolean supportsHashedRecoveryCodes() {
        return useRecoveryCodes && hashRecoveryCodes;
    }

}
