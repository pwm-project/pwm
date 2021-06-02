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


<!DOCTYPE html>
<%@ page import="password.pwm.bean.PasswordStatus" %>
<%@ page import="password.pwm.http.bean.ChangePasswordBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PasswordStatus passwordStatus = JspUtility.getPwmSession(pageContext).getUserInfo().getPasswordStatus(); %>
<% final ChangePasswordBean changePasswordBean = JspUtility.getSessionBean(pageContext, ChangePasswordBean.class); %>

<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ChangePassword" displayIfMissing="true"/></h1>
        <% if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isViolatesPolicy()) { %>
        <h1><pwm:display key="Display_PasswordExpired"/></h1><br/>
        <% } %>
        <p><pwm:display key="Display_ChangePasswordForm"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form" name="changePasswordForm" id="changePasswordForm" autocomplete="off">
            <% if (changePasswordBean.isCurrentPasswordRequired()) { %>
            <h1>
                <label for="currentPassword"><pwm:display key="Field_CurrentPassword"/></label>
            </h1>
            <input id="currentPassword" type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" class="inputfield" name="currentPassword" <pwm:autofocus/>  />
            <br/>
            <% } %>
            <jsp:include page="fragment/form.jsp"/>
            <div class="buttonbar" style="width:100%">
                <input type="hidden" name="processAction" value="form"/>
                <button type="submit" name="change" class="btn" id="continue_button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <% if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired() && !passwordStatus.isViolatesPolicy()) { %>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <% } %>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>


