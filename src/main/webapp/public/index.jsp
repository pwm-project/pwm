<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<% final PwmRequest index_pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>

    <div id="centerbody" class="tile-centerbody">
        <a id="Button_Login" href="<pwm:url addContext="true" url='/private'/>">
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image pwm-icon-sign-in"></div>
                    <div class="tile-title" title="<pwm:display key='Button_Login'/>"><pwm:display key="Button_Login"/></div>
                    <div class="tile-subtitle" title="<pwm:display key='Title_Application'/>"><pwm:display key="Title_Application"/></div>
                </div>
            </div>
        </a>
        <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) { %>
        <a id="Button_ForgottenPassword" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ForgottenPassword.servletUrl()%>'/>">
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image pwm-icon-unlock"></div>
                    <div class="tile-title" title="<pwm:display key='Title_ForgottenPassword'/>"><pwm:display key="Title_ForgottenPassword"/></div>
                    <div class="tile-subtitle" title="<pwm:display key='Long_Title_ForgottenPassword'/>"><pwm:display key="Long_Title_ForgottenPassword"/></div>
                </div>
            </div>
        </a>
        <% } %>
        <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) { %>
        <a id="Button_ForgottenUsername" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ForgottenUsername.servletUrl()%>'/>">
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image pwm-icon-unlock"></div>
                    <div class="tile-title" title="<pwm:display key='Title_ForgottenUsername'/>"><pwm:display key="Title_ForgottenUsername"/></div>
                    <div class="tile-subtitle" title="<pwm:display key='Long_Title_ForgottenUsername'/>"><pwm:display key="Long_Title_ForgottenUsername"/></div>
                </div>
            </div>
        </a>
        <% } %>
        <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) { %>
        <a id="Button_ActivateUser" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ActivateUser.servletUrl()%>'/>">
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image pwm-icon-graduation-cap"></div>
                    <div class="tile-title" title="<pwm:display key='Title_ActivateUser'/>"><pwm:display key="Title_ActivateUser"/></div>
                    <div class="tile-subtitle" title="<pwm:display key='Long_Title_ActivateUser'/>"><pwm:display key="Long_Title_ActivateUser"/></div>
                </div>
            </div>
        </a>
        <% } %>
        <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) { %>
        <a id="Button_NewUser" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.NewUser.servletUrl()%>'/>">
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image pwm-icon-file-text-o"></div>
                    <div class="tile-title" title="<pwm:display key='Title_NewUser'/>"><pwm:display key="Title_NewUser"/></div>
                    <div class="tile-subtitle" title="<pwm:display key='Long_Title_NewUser'/>"><pwm:display key="Long_Title_NewUser"/></div>
                </div>
            </div>
        </a>
        <% } %>
        <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC)) { %>
        <a id="Button_PeopleSearch" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.PeopleSearch.servletUrl()%>'/>">
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image pwm-icon-search"></div>
                    <div class="tile-title" title="<pwm:display key='Title_PeopleSearch'/>"><pwm:display key="Title_PeopleSearch"/></div>
                    <div class="tile-subtitle" title="<pwm:display key='Long_Title_PeopleSearch'/>"><pwm:display key="Long_Title_PeopleSearch"/></div>
                </div>
            </div>
        </a>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<%@ include file="../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
