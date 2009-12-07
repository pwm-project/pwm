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

package password.pwm;

/**
 * Classes implementing this interface will be called during password change events.
 * <p/>
 * The {@link #passwordChange} method will be called just after a successful password change.
 * To be invoked, implementations of this class must be specified in the "externalPasswordMethods"
 * configuration option in the <i>pwmServlet.properties</i> file.
 *
 * @author Jason D. Rivard
 */
public interface ExternalPasswordMethod {
// -------------------------- OTHER METHODS --------------------------

    /**
     * This method is invoked immediately after a successful password change in PWM.  If the password
     * does not meet rule requirements, or is not successfully set in the primary LDAP directory, this
     * method will never be invoked.
     *
     * @param userDN      The Distinguished Name (DN) of the user.
     * @param oldPassword The old password of the user.  Under certain circumstances, it is possible this
     *                    value may be null or an empty string.
     * @param newPassword The new password of the user.
     * @return true if the operation was successful
     */
    public boolean passwordChange(String userDN, String oldPassword, String newPassword);
}

