<%@ page import="password.pwm.Permission" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
  ~
  ~ This program is free software; you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation; either version 2 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  --%>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>
    <div id="centerbody">
        <table style="border:0">
            <% if (Permission.checkPermission(Permission.CHANGE_PASSWORD, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='ChangePassword'/>"><pwm:Display key="Title_ChangePassword"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_ChangePassword"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.SETUP_RESPONSE, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='SetupResponses'/>"><pwm:Display key="Title_SetupResponses"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_SetupResponses"/></p>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.PROFILE_UPDATE, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='UpdateProfile'/>"><pwm:Display key="Title_UpdateProfile"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_UpdateProfile"/></p>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.SHORTCUT_ENABLE)) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='Shortcuts'/>"><pwm:Display key="Title_Shortcuts"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_Shortcuts"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.PEOPLE_SEARCH, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='PeopleSearch'/>"><pwm:Display key="Title_PeopleSearch"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_PeopleSearch"/></p>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_ACCOUNT_INFORMATION)) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='userinfo.jsp'/>"><pwm:Display key="Title_UserInformation"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_UserInformation"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.HELPDESK, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='Helpdesk'/>"><pwm:Display key="Title_Helpdesk"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_Helpdesk"/></p>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.GUEST_REGISTRATION, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='GuestRegistration'/>"><pwm:Display key="Title_GuestRegistration"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_GuestRegistration"/></p>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (Permission.checkPermission(Permission.PWMADMIN, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='admin/activity.jsp'/>"><pwm:Display key="Title_Admin"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_Admin"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_LOGOUT_BUTTON)) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a onclick="showWaitDialog()" class="menubutton" href="<pwm:url url='../public/Logout'/>"><pwm:Display key="Title_Logout"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_Logout"/></p>
                </td>
            </tr>
            <% } %>
        </table>
    </div>
    <div class="push"></div>
</div>
<%@ include file="../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
