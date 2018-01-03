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

package password.pwm.svc.report;

public class ReportColumnFilter
{
    boolean userDnVisible;
    boolean ldapProfileVisible;
    boolean usernameVisible;
    boolean emailVisible;
    boolean userGuidVisible;
    boolean accountExpirationTimeVisible;
    boolean lastLoginTimeVisible;
    boolean passwordExpirationTimeVisible;
    boolean passwordChangeTimeVisible;
    boolean responseSetTimeVisible;
    boolean hasResponsesVisible;
    boolean hasHelpdeskResponsesVisible;
    boolean responseStorageMethodVisible;
    boolean responseFormatTypeVisible;
    boolean passwordStatusExpiredVisible;
    boolean passwordStatusPreExpiredVisible;
    boolean passwordStatusViolatesPolicyVisible;
    boolean passwordStatusWarnPeriodVisible;
    boolean requiresPasswordUpdateVisible;
    boolean requiresResponseUpdateVisible;
    boolean requiresProfileUpdateVisible;
    boolean cacheTimestampVisible;

    public boolean isUserDnVisible( )
    {
        return userDnVisible;
    }

    public void setUserDnVisible( final boolean userDnVisible )
    {
        this.userDnVisible = userDnVisible;
    }

    public boolean isLdapProfileVisible( )
    {
        return ldapProfileVisible;
    }

    public void setLdapProfileVisible( final boolean ldapProfileVisible )
    {
        this.ldapProfileVisible = ldapProfileVisible;
    }

    public boolean isUsernameVisible( )
    {
        return usernameVisible;
    }

    public void setUsernameVisible( final boolean usernameVisible )
    {
        this.usernameVisible = usernameVisible;
    }

    public boolean isEmailVisible( )
    {
        return emailVisible;
    }

    public void setEmailVisible( final boolean emailVisible )
    {
        this.emailVisible = emailVisible;
    }

    public boolean isUserGuidVisible( )
    {
        return userGuidVisible;
    }

    public void setUserGuidVisible( final boolean userGuidVisible )
    {
        this.userGuidVisible = userGuidVisible;
    }

    public boolean isAccountExpirationTimeVisible( )
    {
        return accountExpirationTimeVisible;
    }

    public void setAccountExpirationTimeVisible( final boolean accountExpirationTimeVisible )
    {
        this.accountExpirationTimeVisible = accountExpirationTimeVisible;
    }

    public boolean isLastLoginTimeVisible( )
    {
        return lastLoginTimeVisible;
    }

    public void setLastLoginTimeVisible( final boolean lastLoginTimeVisible )
    {
        this.lastLoginTimeVisible = lastLoginTimeVisible;
    }

    public boolean isPasswordExpirationTimeVisible( )
    {
        return passwordExpirationTimeVisible;
    }

    public void setPasswordExpirationTimeVisible( final boolean passwordExpirationTimeVisible )
    {
        this.passwordExpirationTimeVisible = passwordExpirationTimeVisible;
    }

    public boolean isPasswordChangeTimeVisible( )
    {
        return passwordChangeTimeVisible;
    }

    public void setPasswordChangeTimeVisible( final boolean passwordChangeTimeVisible )
    {
        this.passwordChangeTimeVisible = passwordChangeTimeVisible;
    }

    public boolean isResponseSetTimeVisible( )
    {
        return responseSetTimeVisible;
    }

    public void setResponseSetTimeVisible( final boolean responseSetTimeVisible )
    {
        this.responseSetTimeVisible = responseSetTimeVisible;
    }

    public boolean isHasResponsesVisible( )
    {
        return hasResponsesVisible;
    }

    public void setHasResponsesVisible( final boolean hasResponsesVisible )
    {
        this.hasResponsesVisible = hasResponsesVisible;
    }

    public boolean isHasHelpdeskResponsesVisible( )
    {
        return hasHelpdeskResponsesVisible;
    }

    public void setHasHelpdeskResponsesVisible( final boolean hasHelpdeskResponsesVisible )
    {
        this.hasHelpdeskResponsesVisible = hasHelpdeskResponsesVisible;
    }

    public boolean isResponseStorageMethodVisible( )
    {
        return responseStorageMethodVisible;
    }

    public void setResponseStorageMethodVisible( final boolean responseStorageMethodVisible )
    {
        this.responseStorageMethodVisible = responseStorageMethodVisible;
    }

    public boolean isResponseFormatTypeVisible( )
    {
        return responseFormatTypeVisible;
    }

    public void setResponseFormatTypeVisible( final boolean responseFormatTypeVisible )
    {
        this.responseFormatTypeVisible = responseFormatTypeVisible;
    }

    public boolean isPasswordStatusExpiredVisible( )
    {
        return passwordStatusExpiredVisible;
    }

    public void setPasswordStatusExpiredVisible( final boolean passwordStatusExpiredVisible )
    {
        this.passwordStatusExpiredVisible = passwordStatusExpiredVisible;
    }

    public boolean isPasswordStatusPreExpiredVisible( )
    {
        return passwordStatusPreExpiredVisible;
    }

    public void setPasswordStatusPreExpiredVisible( final boolean passwordStatusPreExpiredVisible )
    {
        this.passwordStatusPreExpiredVisible = passwordStatusPreExpiredVisible;
    }

    public boolean isPasswordStatusViolatesPolicyVisible( )
    {
        return passwordStatusViolatesPolicyVisible;
    }

    public void setPasswordStatusViolatesPolicyVisible( final boolean passwordStatusViolatesPolicyVisible )
    {
        this.passwordStatusViolatesPolicyVisible = passwordStatusViolatesPolicyVisible;
    }

    public boolean isPasswordStatusWarnPeriodVisible( )
    {
        return passwordStatusWarnPeriodVisible;
    }

    public void setPasswordStatusWarnPeriodVisible( final boolean passwordStatusWarnPeriodVisible )
    {
        this.passwordStatusWarnPeriodVisible = passwordStatusWarnPeriodVisible;
    }

    public boolean isRequiresPasswordUpdateVisible( )
    {
        return requiresPasswordUpdateVisible;
    }

    public void setRequiresPasswordUpdateVisible( final boolean requiresPasswordUpdateVisible )
    {
        this.requiresPasswordUpdateVisible = requiresPasswordUpdateVisible;
    }

    public boolean isRequiresResponseUpdateVisible( )
    {
        return requiresResponseUpdateVisible;
    }

    public void setRequiresResponseUpdateVisible( final boolean requiresResponseUpdateVisible )
    {
        this.requiresResponseUpdateVisible = requiresResponseUpdateVisible;
    }

    public boolean isRequiresProfileUpdateVisible( )
    {
        return requiresProfileUpdateVisible;
    }

    public void setRequiresProfileUpdateVisible( final boolean requiresProfileUpdateVisible )
    {
        this.requiresProfileUpdateVisible = requiresProfileUpdateVisible;
    }

    public boolean isCacheTimestampVisible( )
    {
        return cacheTimestampVisible;
    }

    public void setCacheTimestampVisible( final boolean cacheTimestampVisible )
    {
        this.cacheTimestampVisible = cacheTimestampVisible;
    }
}
