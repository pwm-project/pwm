<%@ page import="password.pwm.http.JspUtility" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
<% final PwmRequest index_pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>
    <div id="centerbody">
        <table class="noborder">
            <tr>
                <td class="menubutton_key">
                    <a class="menubutton" id="Button_Login" href="<pwm:context/><pwm:url url='/private'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-in"></span></pwm:if>
                        <pwm:display key="Button_Login"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Display_Login"/></p>
                </td>
            </tr>
            <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) { %>
            <tr>
                <td class="menubutton_key">
                    <a class="menubutton" id="Title_ForgottenPassword" href="<pwm:context/><pwm:url url='/public/ForgottenPassword'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
                        <pwm:display key="Title_ForgottenPassword"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_ForgottenPassword"/></p>
                </td>
            </tr>
            <% } %>
            <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) { %>
            <tr>
                <td class="menubutton_key">
                    <a class="menubutton" id="Title_ForgottenUsername" href="<pwm:context/><pwm:url url='/public/ForgottenUsername'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
                        <pwm:display key="Title_ForgottenUsername"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_ForgottenUsername"/></p>
                </td>
            </tr>
            <% } %>
            <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) { %>
            <tr>
                <td class="menubutton_key">
                    <a class="menubutton" id="Title_ActivateUser" href="<pwm:context/><pwm:url url='/public/ActivateUser'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-graduation-cap"></span></pwm:if>
                        <pwm:display key="Title_ActivateUser"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_ActivateUser"/></p>
                </td>
            </tr>
            <% } %>
            <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) { %>
            <tr>
                <td class="menubutton_key">
                    <a class="menubutton" id="Title_NewUser" href="<pwm:context/><pwm:url url='/public/NewUser'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-file-text-o"></span></pwm:if>
                        <pwm:display key="Title_NewUser"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:display key="Long_Title_NewUser"/></p>
                </td>
            </tr>
            <% } %>
            <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC)) { %>
            <tr>
                <td class="menubutton_key">
                    <a class="menubutton" href="<pwm:url url='PeopleSearch'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-search"></span></pwm:if>
                        <pwm:display key="Title_PeopleSearch"/>
                    </a>
                </td>
                <td>
                    <p><pwm:display key="Long_Title_PeopleSearch"/></p>
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
