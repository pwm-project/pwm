<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@page import="password.pwm.config.option.ForceSetupPolicy"%>
<%@page import="password.pwm.http.bean.SetupOtpBean"%>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.util.operations.otp.OTPUserRecord" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.config.profile.SetupOtpProfile" %>
<%@ page import="password.pwm.http.servlet.SetupOtpServlet" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    OTPUserRecord otpUserRecord = null;
    boolean allowSkip = false;
    boolean forcedPageView = false;
    try {
        final PwmRequest pwmRequest = JspUtility.getPwmRequest( pageContext );
        final SetupOtpBean setupOtpBean = JspUtility.getSessionBean(pageContext, SetupOtpBean.class);
        final SetupOtpProfile setupOtpProfile = SetupOtpServlet.getSetupOtpProfile( pwmRequest );
        otpUserRecord = setupOtpBean.getOtpUserRecord();
        allowSkip = setupOtpProfile.readSettingAsEnum(PwmSetting.OTP_FORCE_SETUP, ForceSetupPolicy.class) == ForceSetupPolicy.FORCE_ALLOW_SKIP;
        forcedPageView = pwmRequest.isForcedPageView();
    } catch (PwmUnrecoverableException e) {
        /* application must be unavailable */
    }

%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
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
                <% if (forcedPageView) { %>
                <% if (allowSkip) { %>
                <button type="submit" name="continue" class="btn" id="skipbutton">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-fighter-jet"></span></pwm:if>
                    <pwm:display key="Button_Skip"/>
                </button>
                <% } %>
                <% } else { %>
                <pwm:if test="<%=PwmIfTest.showCancel%>">
                    <pwm:if test="<%=PwmIfTest.forcedPageView%>" negate="true">
                        <button type="submit" name="button" class="btn" id="button-cancel">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                            <pwm:display key="Button_Cancel"/>
                        </button>
                    </pwm:if>
                </pwm:if>
                <% } %>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script-ref url="/public/resources/js/responses.js"/>
<pwm:script-ref url="/public/resources/js/otpsecret.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
