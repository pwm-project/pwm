<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret" />
    </jsp:include>
    <div id="centerbody">
        <p>
            <pwm:if test="hasStoredOtpTimestamp">
                <pwm:display key="Display_WarnExistingOtpSecretTime" value1="@OtpSetupTime@"/>
            </pwm:if>
            <pwm:if test="hasStoredOtpTimestamp" negate="true">
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
                <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                <pwm:display key="Button_CheckCode"/>
            </button>
            <form action="<pwm:current-url/>" method="post" name="setupOtpSecretForm" style="display: inline"
                  enctype="application/x-www-form-urlencoded" id="setupOtpSecretForm">
                <input type="hidden" name="processAction" value="clearOtp"/>
                <button type="submit" name="Button_Continue" class="btn" id="continue_button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-recycle"></span></pwm:if>
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
