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

<!--

TODO: focus on input field
TODO: show/hide the entered code.
TODO: support HOTP

-->
<%@ page import="password.pwm.http.bean.SetupOtpBean" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = JspUtility.getPwmSession(pageContext).getSetupOtpBean();%>
<% final int otpTokenLength = PwmRequest.forRequest(request,response).getPwmApplication().getOtpService().getSettings().getOtpTokenLength(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_PleaseVerifyOtp"/></p>
        <%@ include file="fragment/message.jsp" %>
        <form action="<pwm:url url='SetupOtp'/>" method="post" name="setupOtpSecret"
              enctype="application/x-www-form-urlencoded" id="setupOtpSecret" class="pwm-form">
            <div style="width:100%; text-align: center">
                <input type="text" pattern="[0-9]*" name="<%= PwmConstants.PARAM_OTP_TOKEN%>" class="inputfield" maxlength="<%=otpTokenLength%>" type="text"
                       id="<%= PwmConstants.PARAM_OTP_TOKEN%>" required="required" style="max-width: 100px"
                       autofocus/>
            </div>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="testOtpSecret"/>
                <button type="submit" name="testOtpSecret" class="btn" id="setotpsecret_button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span>&nbsp</pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <button type="submit" name="testOtpSecret" class="btn" id="button-goback">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span>&nbsp</pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <form action="<pwm:url url='SetupOtp'/>" method="post" name="goBackForm"
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
    });
</script>
</pwm:script>
<script type="text/javascript" src="<pwm:context/><pwm:url url='/public/resources/js/otpsecret.js'/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
