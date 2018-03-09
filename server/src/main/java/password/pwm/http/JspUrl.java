/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.http;

public enum JspUrl
{

    INIT( "init.jsp" ),
    ERROR( "error.jsp" ),
    SUCCESS( "success.jsp" ),
    APP_UNAVAILABLE( "application-unavailable.jsp" ),
    ADMIN_DASHBOARD( "admin-dashboard.jsp" ),
    ADMIN_ANALYSIS( "admin-analysis.jsp" ),
    ADMIN_ACTIVITY( "admin-activity.jsp" ),
    ADMIN_TOKEN_LOOKUP( "admin-tokenlookup.jsp" ),
    ADMIN_LOGVIEW_WINDOW( "admin-logview-window.jsp" ),
    ADMIN_LOGVIEW( "admin-logview.jsp" ),
    ADMIN_URLREFERENCE( "admin-urlreference.jsp" ),
    ADMIN_DEBUG( "admin-user-debug.jsp" ),
    ACTIVATE_USER( "activateuser.jsp" ),
    ACTIVATE_USER_AGREEMENT( "activateuser-agreement.jsp" ),
    ACTIVATE_USER_TOKEN_CHOICE( "activateuser-tokenchoice.jsp" ),
    ACTIVATE_USER_ENTER_CODE( "activateuser-entercode.jsp" ),
    LOGIN( "login.jsp" ),
    LOGIN_PW_ONLY( "login-passwordonly.jsp" ),
    LOGOUT( "logout.jsp" ),
    LOGOUT_PUBLIC( "logout-public.jsp" ),
    PASSWORD_CHANGE( "changepassword.jsp" ),
    PASSWORD_FORM( "changepassword-form.jsp" ),
    PASSWORD_CHANGE_WAIT( "changepassword-wait.jsp" ),
    PASSWORD_AGREEMENT( "changepassword-agreement.jsp" ),
    PASSWORD_COMPLETE( "changepassword-complete.jsp" ),
    PASSWORD_WARN( "changepassword-warn.jsp" ),
    RECOVER_PASSWORD_SEARCH( "forgottenpassword-search.jsp" ),
    RECOVER_PASSWORD_RESPONSES( "forgottenpassword-responses.jsp" ),
    RECOVER_PASSWORD_ATTRIBUTES( "forgottenpassword-attributes.jsp" ),
    RECOVER_PASSWORD_ACTION_CHOICE( "forgottenpassword-actionchoice.jsp" ),
    RECOVER_PASSWORD_METHOD_CHOICE( "forgottenpassword-method.jsp" ),
    RECOVER_PASSWORD_TOKEN_CHOICE( "forgottenpassword-tokenchoice.jsp" ),
    RECOVER_PASSWORD_ENTER_TOKEN( "forgottenpassword-entertoken.jsp" ),
    RECOVER_PASSWORD_ENTER_OTP( "forgottenpassword-enterotp.jsp" ),
    RECOVER_PASSWORD_REMOTE( "forgottenpassword-remote.jsp" ),
    SELF_DELETE_AGREE( "deleteaccount-agreement.jsp" ),
    SELF_DELETE_CONFIRM( "deleteaccount-confirm.jsp" ),
    SETUP_RESPONSES( "setupresponses.jsp" ),
    SETUP_RESPONSES_CONFIRM( "setupresponses-confirm.jsp" ),
    SETUP_RESPONSES_HELPDESK( "setupresponses-helpdesk.jsp" ),
    SETUP_RESPONSES_EXISTING( "setupresponses-existing.jsp" ),
    SETUP_OTP_SECRET_EXISTING( "setupotpsecret-existing.jsp" ),
    SETUP_OTP_SECRET( "setupotpsecret.jsp" ),
    SETUP_OTP_SECRET_TEST( "setupotpsecret-test.jsp" ),
    SETUP_OTP_SECRET_SUCCESS( "setupotpsecret-success.jsp" ),
    FORGOTTEN_USERNAME( "forgottenusername-search.jsp" ),
    FORGOTTEN_USERNAME_COMPLETE( "forgottenusername-complete.jsp" ),
    UPDATE_ATTRIBUTES( "updateprofile.jsp" ),
    UPDATE_ATTRIBUTES_AGREEMENT( "updateprofile-agreement.jsp" ),
    UPDATE_ATTRIBUTES_ENTER_CODE( "updateprofile-entercode.jsp" ),
    UPDATE_ATTRIBUTES_CONFIRM( "updateprofile-confirm.jsp" ),
    NEW_USER( "newuser.jsp" ),
    NEW_USER_ENTER_CODE( "newuser-entercode.jsp" ),
    NEW_USER_WAIT( "newuser-wait.jsp" ),
    NEW_USER_PROFILE_CHOICE( "newuser-profilechoice.jsp" ),
    NEW_USER_AGREEMENT( "newuser-agreement.jsp" ),
    GUEST_REGISTRATION( "guest-create.jsp" ),
    GUEST_UPDATE( "guest-update.jsp" ),
    GUEST_UPDATE_SEARCH( "guest-search.jsp" ),
    ACCOUNT_INFORMATION( "accountinformation.jsp" ),
    SHORTCUT( "shortcut.jsp" ),
    PEOPLE_SEARCH( "peoplesearch.jsp" ),
    CONFIG_MANAGER_EDITOR( "configeditor.jsp" ),
    CONFIG_MANAGER_EDITOR_SUMMARY( "configmanager-summary.jsp" ),
    CONFIG_MANAGER_PERMISSIONS( "configmanager-permissions.jsp" ),
    CONFIG_MANAGER_MODE_CONFIGURATION( "configmanager.jsp" ),
    CONFIG_MANAGER_WORDLISTS( "configmanager-wordlists.jsp" ),
    CONFIG_MANAGER_PWNOTIFY( "configmanager-pwnotify.jsp" ),
    CONFIG_MANAGER_CERTIFICATES( "configmanager-certificates.jsp" ),
    CONFIG_MANAGER_LOCALDB( "configmanager-localdb.jsp" ),
    CONFIG_MANAGER_LOGIN( "configmanager-login.jsp" ),
    HELPDESK_SEARCH( "helpdesk.jsp" ),
    HELPDESK_DETAIL( "helpdesk-detail.jsp" ),;

    private String path;
    private static final String JSP_ROOT_URL = "/WEB-INF/jsp/";

    JspUrl( final String path )
    {
        this.path = path;
    }

    public String getPath( )
    {
        return JSP_ROOT_URL + path;
    }
}
