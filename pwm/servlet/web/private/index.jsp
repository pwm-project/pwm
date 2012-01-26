<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.config.PwmSetting" %>
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
<html xmlns="http://www.w3.org/1999/xhtml" dir="<pwm:LocaleOrientation/>">
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="tundra">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>
    <div id="centerbody">
        <% if (Permission.checkPermission(Permission.CHANGE_PASSWORD, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
        <h2><a href="<pwm:url url='ChangePassword'/>"><pwm:Display key="Title_ChangePassword"/></a></h2>
        <p><pwm:Display key="Long_Title_ChangePassword"/></p>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) { %>
        <% if (Permission.checkPermission(Permission.SETUP_RESPONSE, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
        <h2><a href="<pwm:url url='SetupResponses'/>"><pwm:Display key="Title_SetupResponses"/></a></h2>
        <p><pwm:Display key="Long_Title_SetupResponses"/></p>
        <% } %>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) { %>
        <h2><a href="<pwm:url url='UpdateProfile'/>" class="tablekey"><pwm:Display key="Title_UpdateProfile"/></a></h2>
        <p><pwm:Display key="Long_Title_UpdateProfile"/></p>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_PASSWORD_HISTORY)) { %>        <h2><a href="<pwm:url url='history.jsp'/>" class="tablekey"><pwm:Display key="Title_UserEventHistory"/></a></h2>
        <p><pwm:Display key="Long_Title_UserEventHistory"/></p>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.SHORTCUT_ENABLE)) { %>
        <h2><a href="<pwm:url url='Shortcuts'/>" class="tablekey"><pwm:Display key="Title_Shortcuts"/></a></h2>
        <p><pwm:Display key="Long_Title_Shortcuts"/></p>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) { %>
        <% if (Permission.checkPermission(Permission.PEOPLE_SEARCH, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
        <h2><a href="<pwm:url url='PeopleSearch'/>" class="tablekey"><pwm:Display key="Title_PeopleSearch"/></a></h2>
        <p><pwm:Display key="Long_Title_PeopleSearch"/></p>
        <% } %>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_ACCOUNT_INFORMATION)) { %>
        <h2><a href="<pwm:url url='userinfo.jsp'/>" class="tablekey"><pwm:Display key="Title_UserInformation"/></a></h2>
        <p><pwm:Display key="Long_Title_UserInformation"/></p>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE)) { %>
        <% if (Permission.checkPermission(Permission.HELPDESK, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
        <h2><a href="<pwm:url url='Helpdesk'/>" class="tablekey"><pwm:Display key="Title_Helpdesk"/></a></h2>
        <p><pwm:Display key="Long_Title_Helpdesk"/></p>
        <% } %>
        <% } %>

        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) { %>
        <% if (Permission.checkPermission(Permission.GUEST_REGISTRATION, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
        <h2><a href="<pwm:url url='GuestRegistration'/>" class="tablekey"><pwm:Display key="Title_GuestRegistration"/> & <pwm:Display key="Title_GuestUpdate"/></a></h2>
        <p><pwm:Display key="Long_Title_GuestRegistration"/></p>
        <% } %>
        <% } %>

        <% if (Permission.checkPermission(Permission.PWMADMIN, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
        <h2><a href="<pwm:url url='admin/status.jsp'/>" class="tablekey"><pwm:Display key="Title_Admin"/></a></h2>
        <p><pwm:Display key="Long_Title_Admin"/></p>
        <% } %>

        <h2><a href="<pwm:url url='../public/Logout'/>" class="tablekey"><pwm:Display key="Title_Logout"/></a></h2>
        <p><pwm:Display key="Long_Title_Logout"/></p>
    </div>
</div>
<%@ include file="../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
