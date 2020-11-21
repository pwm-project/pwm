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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret" />
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_SetupOtpSecret" displayIfMissing="true"/></h1>
        <p>
            <pwm:if test="<%=PwmIfTest.hasStoredOtpTimestamp%>">
                <pwm:display key="Display_WarnExistingOtpSecretTime" value1="@OtpSetupTime@"/>
            </pwm:if>
            <pwm:if test="<%=PwmIfTest.hasStoredOtpTimestamp%>" negate="true">
                <pwm:display key="Display_WarnExistingOtpSecret"/>
            </pwm:if>
        </p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <%--
        <br/>
        --%>
        <div class="buttonbar">
            <button type="submit" name="button-verifyCodeDialog" class="btn" id="button-verifyCodeDialog">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                <pwm:display key="Button_CheckCode"/>
            </button>
            <form action="<pwm:current-url/>" method="post" name="setupOtpSecretForm" style="display: inline"
                  enctype="application/x-www-form-urlencoded" id="setupOtpSecretForm">
                <input type="hidden" name="processAction" value="clearOtp"/>
                <button type="submit" name="Button_Continue" class="btn" id="continue_button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-recycle"></span></pwm:if>
                    <pwm:display key="Button_ClearOtpReEnroll"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="application/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_OTP.initExistingOtpPage();
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/otpsecret.js"/>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
