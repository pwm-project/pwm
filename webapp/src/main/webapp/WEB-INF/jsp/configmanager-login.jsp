<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2019 The PWM Project
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


<%@ page import="password.pwm.http.filter.ConfigAccessFilter" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.servlet.configmanager.ConfigManagerServlet" %>
<%@ page import="password.pwm.http.servlet.configmanager.ConfigManagerLoginServlet" %>

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
%>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE);%>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<div id="wrapper" class="login-wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <form action="<pwm:current-url/>" method="post" id="configLogin" name="configLogin" enctype="application/x-www-form-urlencoded"
              class="pwm-form">
            <div>
            <h1>Configuration Password</h1>
            <input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" class="inputfield passwordfield" name="password" id="password" placeholder="<pwm:display key="Field_Password"/>" <pwm:autofocus/>/>
            </div>
            <% if ( (Boolean)pwmRequest.getAttribute( PwmRequestAttribute.ConfigEnablePersistentLogin ) ) { %>
            <div class="checkboxWrapper">
                <label>
                    <input type="checkbox" id="remember" name="remember"/>
                    <pwm:display key="Display_RememberLogin" bundle="Config" value1="<%=(String)JspUtility.getAttribute(pageContext,PwmRequestAttribute.ConfigPasswordRememberTime)%>"/>
                </label>
            </div>
            <% } %>
            <div class="buttonbar">
                <button type="submit" class="btn" name="button" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-sign-in"></span></pwm:if>
                    <pwm:display key="Button_Login"/>
                </button>
                <input type="hidden" name="processAction" value="<%=ConfigManagerLoginServlet.ConfigManagerLoginAction.login%>"/>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>" autofocus/>
            </div>
        </form>
        <% final ConfigManagerLoginServlet.ConfigLoginHistory configLoginHistory = (ConfigManagerLoginServlet.ConfigLoginHistory)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ConfigLoginHistory); %>
        <% if (configLoginHistory != null && !configLoginHistory.successEvents().isEmpty()) { %>
        <h2 style="margin-top: 15px;">Previous Authentications</h2>
        <table>
            <tr>
                <td class="title">Identity</td>
                <td class="title">Timestamp</td>
                <td class="title">Network Address</td>
            </tr>
            <% for (final ConfigManagerLoginServlet.ConfigLoginEvent event : configLoginHistory.successEvents()) { %>
            <tr>
                <td><%=event.getUserIdentity()%></td>
                <td><span  class="timestamp"><%=JavaHelper.toIsoDate(event.getDate())%></span></td>
                <td><%=event.getNetworkAddress()%></td>
            </tr>
            <% } %>
        </table>
        <% } %>
        <br/>
        <% if (configLoginHistory != null && !configLoginHistory.failedEvents().isEmpty()) { %>
        <h2>Previous Failed Authentications</h2>
        <table>
            <tr>
                <td class="title">Identity</td>
                <td class="title">Timestamp</td>
                <td class="title">Network Address</td>
            </tr>
            <% for (final ConfigManagerLoginServlet.ConfigLoginEvent event : configLoginHistory.failedEvents()) { %>
            <tr>
                <td><%=event.getUserIdentity()%></td>
                <td><span  class="timestamp"><%=JavaHelper.toIsoDate(event.getDate())%></span></td>
                <td><%=event.getNetworkAddress()%></td>
            </tr>
            <% } %>
        </table>
        <% } %>

    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
