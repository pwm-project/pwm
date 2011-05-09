<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2010 The PWM Project
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="WEB-INF/jsp/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="tundra">
<div id="wrapper">
    <jsp:include page="WEB-INF/jsp/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Long_Title_Main_Menu"/></p>

        <h2><a href="<pwm:url url='private/ChangePassword'/>"><pwm:Display key="Title_ChangePassword"/></a></h2>

        <p><pwm:Display key="Long_Title_ChangePassword"/></p>

        <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) { %>
        <h2><a href="<pwm:url url='public/ForgottenPassword'/>"><pwm:Display key="Title_ForgottenPassword"/></a></h2>

        <p>
            <pwm:Display key="Long_Title_ForgottenPassword"/>
                <% } %>


                <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) { %>

        <h2><a href="<pwm:url url='public/ForgottenUsername'/>"><pwm:Display key="Title_ForgottenUsername"/></a></h2>

        <p>
            <pwm:Display key="Long_Title_ForgottenUsername"/>
                <% } %>

                <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) { %>

        <h2><a href="<pwm:url url='private/SetupResponses'/>"><pwm:Display key="Title_SetupResponses"/></a></h2>

        <p><pwm:Display key="Long_Title_SetupResponses"/></p>
        <% } %>

        <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) { %>
        <h2><a href="<pwm:url url='public/ActivateUser'/>"><pwm:Display key="Title_ActivateUser"/></a></h2>

        <p><pwm:Display key="Long_Title_ActivateUser"/><p>
            <% } %>

            <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) { %>

        <h2><a href="<pwm:url url='public/NewUser'/>" class="tablekey"><pwm:Display key="Title_NewUser"/></a></h2>

        <p><pwm:Display key="Long_Title_NewUser"/></p>
        <% } %>

        <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) { %>
        <h2><a href="<pwm:url url='private/UpdateProfile'/>" class="tablekey"><pwm:Display
                key="Title_UpdateProfile"/></a></h2>

        <p><pwm:Display key="Long_Title_UpdateProfile"/></p>
        <% } %>

        <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.DISPLAY_PASSWORD_HISTORY)) { %>
        <h2><a href="<pwm:url url='private/history.jsp'/>" class="tablekey"><pwm:Display
                key="Title_UserEventHistory"/></a></h2>

        <p><pwm:Display key="Long_Title_UserEventHistory"/></p>
        <% } %>

        <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.SHORTCUT_ENABLE)) { %>
        <h2><a href="<pwm:url url='private/Shortcuts'/>" class="tablekey"><pwm:Display key="Title_Shortcuts"/></a></h2>

        <p><pwm:Display key="Long_Title_Shortcuts"/></p>
        <% } %>

        <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.DISPLAY_ACCOUNT_INFORMATION)) { %>
        <h2><a href="<pwm:url url='private/userinfo.jsp'/>" class="tablekey"><pwm:Display
                key="Title_UserInformation"/></a></h2>

        <p><pwm:Display key="Long_Title_UserInformation"/></p>
        <% } %>

        <% if (Configuration.getConfig(session) != null && Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.GUEST_ENABLE) &&
                                                         (!Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.GUEST_HIDDEN))) { %>
        <hr/>

        <h2><a href="<pwm:url url='private/GuestRegistration'/>" class="tablekey"><pwm:Display key="Title_GuestRegistration"/></a></h2>

        <p><pwm:Display key="Long_Title_GuestRegistration"/></p>

        <h2><a href="<pwm:url url='private/GuestUpdate'/>" class="tablekey"><pwm:Display key="Title_GuestUpdate"/></a></h2>

        <p><pwm:Display key="Long_Title_GuestUpdate"/></p>

        <% } %>

        <hr/>

        <h2><a href="<pwm:url url='admin/status.jsp'/>" class="tablekey"><pwm:Display key="Title_Admin"/></a></h2>

        <p><pwm:Display key="Long_Title_Admin"/></p>

        <% if (PwmSession.getSessionStateBean(session).isAuthenticated()) { %>
        <hr/>
        <h2><a href="<pwm:url url='public/Logout'/>" class="tablekey"><pwm:Display key="Title_Logout"/></a></h2>

        <p><pwm:Display key="Long_Title_Logout"/></p>
        <% } %>
    </div>
</div>
<%@ include file="WEB-INF/jsp/footer.jsp" %>
</body>
</html>
