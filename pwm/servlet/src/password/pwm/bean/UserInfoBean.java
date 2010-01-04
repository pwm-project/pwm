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

package password.pwm.bean;

import com.novell.ldapchai.cr.ChallengeSet;
import password.pwm.Permission;
import password.pwm.PwmPasswordPolicy;
import password.pwm.config.PasswordStatus;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A bean that is stored in the user's session.   Only information that is particular to logged in user is stored in the
 * user info bean.  Information more topical to the session is stored in {@link SessionStateBean}.
 * <p/>
 * For any given HTTP session using PWM, several {@link UserInfoBean}s may be created during
 * the life of the session, however at any given time, no more than one will be stored in
 * the HTTP session.  If the user is not authenticated (determined by {@link SessionStateBean#isAuthenticated()})
 * then there should not be a {@link UserInfoBean} in the HTTP session.
 *
 * @author Jason D. Rivard
 * @see password.pwm.UserStatusHelper#populateActorUserInfoBean(password.pwm.PwmSession, String, String)
 */
public class UserInfoBean implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private String userDN;

    /**
     * The logged in user's password,
     */
    private String userCurrentPassword;
    private String userID;

    private boolean authFromUnknownPw;

    /**
     * A listing of all readable attributes on the ldap user object
     */
    private Properties allUserAttributes = new Properties();

    private PasswordStatus passwordState = new PasswordStatus();
    private long authTime;

    private PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.defaultPolicy();
    private ChallengeSet challengeSet = null;

    private Date passwordExpirationTime;
    private Date passwordLastModifiedTime;

    private Map<Permission, Permission.PERMISSION_STATUS> permissions = new HashMap<Permission, Permission.PERMISSION_STATUS>();

    private boolean requiresNewPassword;
    private boolean requiresResponseConfig;

// --------------------- GETTER / SETTER METHODS ---------------------

    public Properties getAllUserAttributes()
    {
        return this.allUserAttributes;
    }

    public void setAllUserAttributes(final Properties userAttributes)
    {
        allUserAttributes = userAttributes;
    }

    public long getAuthTime()
    {
        return authTime;
    }

    public void setAuthTime(final long authTime)
    {
        this.authTime = authTime;
    }

    public ChallengeSet getChallengeSet()
    {
        return challengeSet;
    }

    public void setChallengeSet(final ChallengeSet challengeSet)
    {
        this.challengeSet = challengeSet;
    }

    public PwmPasswordPolicy getPasswordPolicy()
    {
        return passwordPolicy;
    }

    public void setPasswordPolicy(final PwmPasswordPolicy passwordPolicy)
    {
        this.passwordPolicy = passwordPolicy;
    }

    public String getUserCurrentPassword()
    {
        return userCurrentPassword;
    }

    public void setUserCurrentPassword(final String userCurrentPassword)
    {
        this.userCurrentPassword = userCurrentPassword;
    }

    public String getUserDN()
    {
        return userDN;
    }

    public void setUserDN(final String userDN)
    {
        this.userDN = userDN;
    }

    public Date getPasswordExpirationTime() {
        return passwordExpirationTime;
    }

    public void setPasswordExpirationTime(final Date passwordExpirationTime) {
        this.passwordExpirationTime = passwordExpirationTime;
    }

    public Map<Permission, Permission.PERMISSION_STATUS> getPermissions() {
        return permissions;
    }

    public void setPermissions(final Map<Permission, Permission.PERMISSION_STATUS> permissions) {
        this.permissions = permissions;
    }

    public String getUserID()
    {
        return userID;
    }

    public void setUserID(final String userID)
    {
        this.userID = userID;
    }

    public PasswordStatus getPasswordState()
    {
        return passwordState;
    }

    public void setPasswordState(final PasswordStatus passwordState)
    {
        this.passwordState = passwordState;
    }

    public boolean isAuthFromUnknownPw() {
        return authFromUnknownPw;
    }

    public void setAuthFromUnknownPw(final boolean authFromUnknownPw) {
        this.authFromUnknownPw = authFromUnknownPw;
    }

    public boolean isRequiresNewPassword() {
        return requiresNewPassword;
    }

    public void setRequiresNewPassword(final boolean requiresNewPassword) {
        this.requiresNewPassword = requiresNewPassword;
    }

    public boolean isRequiresResponseConfig() {
        return requiresResponseConfig;
    }

    public void setRequiresResponseConfig(final boolean requiresResponseConfig) {
        this.requiresResponseConfig = requiresResponseConfig;
    }

    public Date getPasswordLastModifiedTime() {
        return passwordLastModifiedTime;
    }

    public void setPasswordLastModifiedTime(Date passwordLastModifiedTime) {
        this.passwordLastModifiedTime = passwordLastModifiedTime;
    }

    // -------------------------- OTHER METHODS --------------------------

    public void clearPermissions()
    {
        permissions.clear();
    }

    public Permission.PERMISSION_STATUS getPermission(final Permission permission)
    {
        final Permission.PERMISSION_STATUS status = permissions.get(permission);
        return status == null ? Permission.PERMISSION_STATUS.UNCHECKED : status;
    }

    public void setPermission(final Permission permission, final Permission.PERMISSION_STATUS status)
    {
        permissions.put(permission, status);
    }
}

