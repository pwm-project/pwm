<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<% final PwmRequest index_pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body>
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
                    <div class="tile-image forgotten-image"></div>
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
                    <div class="tile-image forgotten-image"></div>
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
                    <div class="tile-image activation-image"></div>
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
                    <div class="tile-image newuser-image"></div>
                    <div class="tile-title" title="<pwm:display key='Title_NewUser'/>"><pwm:display key="Title_NewUser"/></div>
                    <div class="tile-subtitle" title="<pwm:display key='Long_Title_NewUser'/>"><pwm:display key="Long_Title_NewUser"/></div>
                </div>
            </div>
        </a>
        <% } %>
        <% if (index_pwmRequest.getConfig() != null && index_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC)) { %>
        <a id="Button_PeopleSearch" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.PublicPeopleSearch.servletUrl()%>'/>">
            <div class="tile">
                <div class="tile-content">
                    <div class="tile-image search-image"></div>
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
