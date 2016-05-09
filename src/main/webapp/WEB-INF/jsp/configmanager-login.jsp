<%@ page import="password.pwm.http.filter.ConfigAccessFilter" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
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
        <div id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></div>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <form action="<pwm:current-url/>" method="post" id="configLogin" name="configLogin" enctype="application/x-www-form-urlencoded"
              class="pwm-form">
            <div>
            <h1>Configuration Password</h1>
            <input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" class="inputfield passwordfield" name="password" id="password" placeholder="<pwm:display key="Field_Password"/>" <pwm:autofocus/>/>
            </div>
            <% if (!pwmRequest.getConfig().isDefaultValue(PwmSetting.PWM_SECURITY_KEY)) { %>
            <div class="checkboxWrapper">
                <label>
                    <input type="checkbox" id="remember" name="remember"/>
                    <pwm:display key="Display_RememberLogin" bundle="Config" value1="<%=(String)JspUtility.getAttribute(pageContext,PwmRequest.Attribute.ConfigPasswordRememberTime)%>"/>
                </label>
            </div>
            <% } %>
            <div class="buttonbar">
                <button type="submit" class="btn" name="button" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-sign-in"></span></pwm:if>
                    <pwm:display key="Button_Login"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>" autofocus/>
            </div>
        </form>
        <% final ConfigAccessFilter.ConfigLoginHistory configLoginHistory = (ConfigAccessFilter.ConfigLoginHistory)JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ConfigLoginHistory); %>
        <% if (configLoginHistory != null && !configLoginHistory.successEvents().isEmpty()) { %>
        <h2 style="margin-top: 15px;">Previous Authentications</h2>
        <table>
            <tr>
                <td class="title">Identity</td>
                <td class="title">Timestamp</td>
                <td class="title">Network Address</td>
            </tr>
            <% for (final ConfigAccessFilter.ConfigLoginEvent event : configLoginHistory.successEvents()) { %>
            <tr>
                <td><%=event.getUserIdentity()%></td>
                <td><span  class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(event.getDate())%></span></td>
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
            <% for (final ConfigAccessFilter.ConfigLoginEvent event : configLoginHistory.failedEvents()) { %>
            <tr>
                <td><%=event.getUserIdentity()%></td>
                <td><span  class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(event.getDate())%></span></td>
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