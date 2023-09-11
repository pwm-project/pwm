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


<%@ page import="password.pwm.http.bean.SetupOtpBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final int otpTokenLength = (int)JspUtility.getAttribute( pageContext, PwmRequestAttribute.SetupOtp_TokenLength ); %>
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
                <input type="text" pattern="^[0-9]*$" name="<%= PwmConstants.PARAM_OTP_TOKEN%>" class="inputfield passwordfield" maxlength="<%=otpTokenLength%>"
                       id="<%= PwmConstants.PARAM_OTP_TOKEN%>" style="max-width: 100px"
                        <pwm:autofocus/> title="0-9"/>
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
<script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
    import {PWM_OTP} from "<pwm:url url="/public/resources/js/otpsecret.js" addContext="true"/>";
    PWM_OTP.initTestPage();
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
