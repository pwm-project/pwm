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


<%@ page import="password.pwm.bean.LocalSessionStateBean" %>
<%@ page import="password.pwm.ldap.UserInfo" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="java.sql.Date" %>
<%@ page import="java.text.DateFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final UserInfo uiBean = JspUtility.getPwmSession(pageContext).getUserInfo(); %>
<% final LocalSessionStateBean ssBean = JspUtility.getPwmSession(pageContext).getSessionStateBean(); %>
<% final DateFormat dateFormatter = java.text.DateFormat.getDateInstance(DateFormat.FULL, ssBean.getLocale()); %>
<% final DateFormat timeFormatter = java.text.DateFormat.getTimeInstance(DateFormat.FULL, ssBean.getLocale()); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PasswordWarning"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_PasswordWarning" displayIfMissing="true"/></h1>
        <p>
            <% if (uiBean.getPasswordExpirationTime() != null) { %>
            <pwm:display key="Display_PasswordWarn"
                         value1="<%= dateFormatter.format(Date.from(uiBean.getPasswordExpirationTime())) %>"
                         value2="<%= timeFormatter.format(Date.from(uiBean.getPasswordExpirationTime())) %>"/>
            <% } else { %>
            <pwm:display key="Display_PasswordNoExpire"/>
            <% } %>
        </p>

        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="processAction" value="warnResponse"/>
                <input type="hidden" name="warnResponse" value="change"/>

                <button type="submit" <pwm:autofocus/> name="changePassword" class="btn" id="changePassword">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-key"></span></pwm:if>
                    <pwm:display key="Button_ChangePassword"/>
                </button>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<pwm:current-url/>" method="post" name="setupOtpSecret-skip"
                  enctype="application/x-www-form-urlencoded" id="setupOtpSecret-skip" class="pwm-form">
                <input type="hidden" name="processAction" value="warnResponse"/>
                <input type="hidden" name="warnResponse" value="skip"/>
                <button type="submit" <pwm:autofocus/> name="skipbutton" class="btn" id="skipbutton">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-fighter-jet"></span></pwm:if>
                    <pwm:display key="Button_Skip"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
