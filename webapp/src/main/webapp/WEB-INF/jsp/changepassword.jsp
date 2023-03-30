<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.servlet.changepw.ChangePasswordServlet" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ChangePassword" displayIfMissing="true"/></h1>
        <pwm:if test="<%=PwmIfTest.passwordExpired%>">
            <h1><pwm:display key="Display_PasswordExpired"/></h1><br/>
        </pwm:if>
        <p><pwm:display key="Display_ChangePassword"/></p>
        <div id="PasswordRequirements">
            <ul>
                <pwm:DisplayPasswordRequirements separator="</li>" prepend="<li>"/>
            </ul>
        </div>
        <div id="PasswordChangeMessage">
            <p><pwm:PasswordChangeMessageTag/></p>
        </div>
        <br/>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" id="changePasswordForm" autocomplete="off">
            <jsp:include page="fragment/form-field-newpassword.jsp" />

            <input type="hidden" name="processAction" value="change"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>

            <div class="buttonbar">
                <button type="submit" name="password_button" class="btn" id="password_button" tabindex="<pwm:tabindex/>">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_ChangePassword"/>
                </button>
                <pwm:if test="<%=PwmIfTest.passwordExpired%>" negate="true">
                    <button id="button-reset" type="button" name="button-reset" class="btn" form="form-reset" tabindex="<pwm:tabindex/>">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                </pwm:if>
            </div>
        </form>
        <form id="form-reset" name="form-reset" action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" >
            <input type="hidden" name="processAction" value="<%=ChangePasswordServlet.ChangePasswordAction.reset%>"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_CHANGEPW.startupChangePasswordPage();
        });
    </script>
</pwm:script>

<pwm:script-ref url="/public/resources/js/changepassword.js"/>

<%@ include file="fragment/footer.jsp" %>
</body>
</html>
