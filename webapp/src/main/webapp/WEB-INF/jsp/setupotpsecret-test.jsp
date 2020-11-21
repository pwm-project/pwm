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


<%@ page import="password.pwm.http.bean.SetupOtpBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="org.apache.commons.lang3.StringEscapeUtils" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = JspUtility.getSessionBean(pageContext,SetupOtpBean.class); %>
<% final int otpTokenLength = PwmRequest.forRequest(request,response).getPwmApplication().getOtpService().getSettings().getOtpTokenLength(); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_SetupOtpSecret" displayIfMissing="true"/></h1>
        <p><pwm:display key="Display_PleaseVerifyOtp"/></p>
        <%@ include file="fragment/message.jsp" %>
        <form action="<pwm:current-url/>" method="post" name="setupOtpSecret"
              enctype="application/x-www-form-urlencoded" id="setupOtpSecret" class="pwm-form">
            <div style="width:100%; text-align: center">
                <input type="text" pattern="^[0-9]*$" name="<%= PwmConstants.PARAM_OTP_TOKEN%>" class="inputfield passwordfield" maxlength="<%=otpTokenLength%>" type="text"
                       id="<%= PwmConstants.PARAM_OTP_TOKEN%>" required="required" style="max-width: 100px"
                       autofocus title="0-9"/>
            </div>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="testOtpSecret"/>
                <button type="submit" name="testOtpSecret" class="btn" id="setotpsecret_button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span>&nbsp</pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <button type="submit" name="testOtpSecret" class="btn" id="button-goback">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span>&nbsp</pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <form action="<pwm:current-url/>" method="post" name="goBackForm"
          enctype="application/x-www-form-urlencoded" id="goBackForm">
        <input type="hidden" name="processAction" value="toggleSeen"/>
        <input type="hidden" id="pwmFormID_" name="pwmFormID" value="<pwm:FormID/>"/>
    </form>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['responseMode'] = "user";
    PWM_GLOBAL['startupFunctions'].push(function() {
        PWM_MAIN.addEventHandler('button-goback','click',function(){PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('goBackForm'))});
        document.getElementById("<%= PwmConstants.PARAM_OTP_TOKEN%>").focus();

        <%--Calling setCustomValidity allows us to set a custom error message on the input field--%>
        PWM_MAIN.addEventHandler('<%= PwmConstants.PARAM_OTP_TOKEN%>', 'input', function (event) {
            event.target.setCustomValidity("");
        });
        PWM_MAIN.addEventHandler('<%= PwmConstants.PARAM_OTP_TOKEN%>', 'invalid', function (event) {
            event.target.setCustomValidity("<%=StringEscapeUtils.escapeEcmaScript(LocaleHelper.getLocalizedMessage(Display.Display_SetupOtp_VerificationCodeFormat, JspUtility.getPwmRequest(pageContext)))%>");
        });
    });
</script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/otpsecret.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
