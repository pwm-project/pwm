<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>
    <div id="centerbody">
        <table style="border:0">
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Button_Login" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/private'/>"><pwm:display key="Button_Login"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Button_Login"/></p>
                </td>
            </tr>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_ForgottenPassword" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/ForgottenPassword'/>"><pwm:display key="Title_ForgottenPassword"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_ForgottenPassword"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_ForgottenUsername" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/ForgottenUsername'/>"><pwm:display key="Title_ForgottenUsername"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_ForgottenUsername"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_ActivateUser" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/ActivateUser'/>"><pwm:display key="Title_ActivateUser"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_ActivateUser"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_NewUser" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/NewUser'/>"><pwm:display key="Title_NewUser"/></a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_NewUser"/></p>
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
