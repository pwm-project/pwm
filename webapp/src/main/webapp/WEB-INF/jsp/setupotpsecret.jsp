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


<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.util.operations.otp.OTPUserRecord" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% OTPUserRecord otpUserRecord = (OTPUserRecord) JspUtility.getAttribute( pageContext, PwmRequestAttribute.SetupOtp_UserRecord ); %>
<% boolean allowSkip = JspUtility.getBooleanAttribute( pageContext, PwmRequestAttribute.SetupOtp_AllowSkip ); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_SetupOtpSecret" displayIfMissing="true"/></h1>
        <p><pwm:display key="Display_SetupOtpSecret"/></p>
        <%@ include file="fragment/message.jsp" %>
        <div class="tab-container">
            <input name="tabs" type="radio" id="tab-1" checked="checked" class="input"/>
            <label for="tab-1" class="label"><pwm:display key="Display_SetupOtp_Android_Title"/></label>
            <div class="tab-content-pane" title="<pwm:display key="Display_SetupOtp_Android_Title"/>">
                <pwm:display key="Display_SetupOtp_Android_Steps"/>
                <img class="qrcodeimage" src="<%=JspUtility.getAttribute(pageContext,PwmRequestAttribute.SetupOtp_QrCodeValue)%>" alt="QR Code"/>
            </div>

            <input name="tabs" type="radio" id="tab-2" class="input"/>
            <label for="tab-2" class="label"><pwm:display key="Display_SetupOtp_iPhone_Title"/></label>
            <div class="tab-content-pane" title="<pwm:display key="Display_SetupOtp_iPhone_Title"/>">
                <pwm:display key="Display_SetupOtp_iPhone_Steps"/>
                <img class="qrcodeimage" src="<%=JspUtility.getAttribute(pageContext,PwmRequestAttribute.SetupOtp_QrCodeValue)%>" alt="QR Code"/>
            </div>

            <input name="tabs" type="radio" id="tab-3" class="input"/>
            <label for="tab-3" class="label"><pwm:display key="Display_SetupOtp_Other_Title"/></label>
            <div class="tab-content-pane" title="<pwm:display key="Display_SetupOtp_Other_Title"/>">
                <pwm:display key="Display_SetupOtp_Other_Steps"/>
                <img class="qrcodeimage" src="<%=JspUtility.getAttribute(pageContext,PwmRequestAttribute.SetupOtp_QrCodeValue)%>" alt="QR Code"/>
                <table border="0" style="width: 300px; margin-right: auto; margin-left: auto">
                    <tr valign="top">
                        <td><b><pwm:display key="Field_OTP_Identifier"/></b></td>
                        <td><%=otpUserRecord.getIdentifier()%></td>
                    </tr>
                    <tr valign="top">
                        <td><b><pwm:display key="Field_OTP_Secret"/></b></td>
                        <td><%=otpUserRecord.getSecret()%></td>
                    </tr>
                    <tr valign="top">
                        <td><b><pwm:display key="Field_OTP_Type"/></b></td>
                        <td><%=otpUserRecord.getType().toString()%></td>
                    </tr>
                </table>
            </div>
            <div class="tab-end"></div>
        </div>
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post" name="setupOtpSecret" enctype="application/x-www-form-urlencoded" id="setupOtpSecret" class="pwm-form">
                <input type="hidden" name="processAction" value="toggleSeen"/>
                <button type="submit" name="continue" class="btn" id="continuebutton">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<pwm:current-url/>" method="post" name="setupOtpSecret-skip" enctype="application/x-www-form-urlencoded" id="setupOtpSecret-skip" class="pwm-form">
                <input type="hidden" name="processAction" value="skip"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                <% if (allowSkip) { %>
                <button type="submit" name="continue" class="btn" id="skipbutton">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-fighter-jet"></span></pwm:if>
                    <pwm:display key="Button_Skip"/>
                </button>
                <% } %>
            </form>
            <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script-ref url="/public/resources/js/responses.js"/>
<pwm:script-ref url="/public/resources/js/otpsecret.js"/>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
