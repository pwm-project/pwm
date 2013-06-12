/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.util.PostChangePasswordAction;

import java.util.*;

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
 * @see password.pwm.util.operations.UserStatusHelper#populateUserInfoBean(password.pwm.PwmSession, UserInfoBean, password.pwm.PwmApplication, java.util.Locale, String, String, com.novell.ldapchai.provider.ChaiProvider)
 */
public class UserInfoBean implements PwmSessionBean {
// ------------------------------ FIELDS ------------------------------

    private String userDN;
    private String userCurrentPassword;
    private String userID;
    private String userEmailAddress;
    private String userSmsNumber;
    private String userGuid;


    /**
     * A listing of all readable attributes on the ldap user object
     */
    private Map<String,String> cachedPasswordRuleAttributes = Collections.emptyMap();

    private PasswordStatus passwordState = new PasswordStatus();

    private PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.defaultPolicy();
    private ChallengeSet challengeSet = null;
    private ResponseInfoBean responseInfoBean = null;

    private Date passwordExpirationTime;
    private Date passwordLastModifiedTime;
    private Date authTime;

    private Map<Permission, Permission.PERMISSION_STATUS> permissions = new HashMap<Permission, Permission.PERMISSION_STATUS>();

    private boolean requiresNewPassword;
    private boolean requiresResponseConfig;
    private boolean requiresUpdateProfile;
    
    private AuthenticationType authenticationType = AuthenticationType.UNAUTHENTICATED;

    private Map<String, PostChangePasswordAction> postChangePasswordActions = new HashMap<String, PostChangePasswordAction>();

    public enum AuthenticationType {
        UNAUTHENTICATED,
        AUTHENTICATED,
        AUTH_BIND_INHIBIT,
        AUTH_FROM_FORGOTTEN,
        AUTH_WITHOUT_PASSWORD
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public Map<String,String> getCachedPasswordRuleAttributes() {
        return this.cachedPasswordRuleAttributes;
    }

    public void setCachedPasswordRuleAttributes(final Map<String, String> userAttributes) {
        cachedPasswordRuleAttributes = userAttributes;
    }

    public Date getAuthTime() {
        return authTime;
    }

    public void setAuthTime(final Date authTime) {
        this.authTime = authTime;
    }

    public ChallengeSet getChallengeSet() {
        return challengeSet;
    }

    public void setChallengeSet(final ChallengeSet challengeSet) {
        this.challengeSet = challengeSet;
    }

    public PwmPasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(final PwmPasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public String getUserCurrentPassword() {
        return userCurrentPassword;
    }

    public void setUserCurrentPassword(final String userCurrentPassword) {
        this.userCurrentPassword = userCurrentPassword;
    }

    public String getUserDN() {
        return userDN;
    }

    public void setUserDN(final String userDN) {
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

    public String getUserID() {
        return userID;
    }

    public void setUserID(final String userID) {
        this.userID = userID;
    }

    public PasswordStatus getPasswordState() {
        return passwordState;
    }

    public void setPasswordState(final PasswordStatus passwordState) {
        this.passwordState = passwordState;
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

    public boolean isRequiresUpdateProfile() {
        return requiresUpdateProfile;
    }

    public void setRequiresUpdateProfile(final boolean requiresUpdateProfile) {
        this.requiresUpdateProfile = requiresUpdateProfile;
    }

    public Date getPasswordLastModifiedTime() {
        return passwordLastModifiedTime;
    }

    public void setPasswordLastModifiedTime(final Date passwordLastModifiedTime) {
        this.passwordLastModifiedTime = passwordLastModifiedTime;
    }

    public String getUserEmailAddress() {
        return userEmailAddress;
    }

    public void setUserEmailAddress(final String userEmailAddress) {
        this.userEmailAddress = userEmailAddress;
    }

    public String getUserSmsNumber() {
        return userSmsNumber;
    }

    public void setUserSmsNumber(final String userSmsNumber) {
        this.userSmsNumber = userSmsNumber;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public void setUserGuid(final String userGuid) {
        this.userGuid = userGuid;
    }

    // -------------------------- OTHER METHODS --------------------------

    public void clearPermissions() {
        permissions.clear();
    }

    public Permission.PERMISSION_STATUS getPermission(final Permission permission) {
        final Permission.PERMISSION_STATUS status = permissions.get(permission);
        return status == null ? Permission.PERMISSION_STATUS.UNCHECKED : status;
    }

    public void setPermission(final Permission permission, final Permission.PERMISSION_STATUS status) {
        permissions.put(permission, status);
    }

    public void addPostChangePasswordActions(final String key, final PostChangePasswordAction postChangePasswordAction) {
        if (postChangePasswordAction == null) {
            postChangePasswordActions.remove(key);
        } else {
            postChangePasswordActions.put(key, postChangePasswordAction);
        }
    }

    public List<PostChangePasswordAction> removePostChangePasswordActions() {
        final List<PostChangePasswordAction> copiedList = new ArrayList<PostChangePasswordAction>();
        copiedList.addAll(postChangePasswordActions.values());
        postChangePasswordActions.clear();
        return copiedList;
    }

    public Map<String, PostChangePasswordAction> getPostChangePasswordActions() {
        return postChangePasswordActions;
    }

    public void setPostChangePasswordActions(Map<String, PostChangePasswordAction> postChangePasswordActions) {
        this.postChangePasswordActions = postChangePasswordActions;
    }

    public ResponseInfoBean getResponseInfoBean() {
        return responseInfoBean;
    }

    public void setResponseInfoBean(ResponseInfoBean responseInfoBean) {
        this.responseInfoBean = responseInfoBean;
    }

    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(AuthenticationType authenticationType) {
        this.authenticationType = authenticationType;
    }
}

