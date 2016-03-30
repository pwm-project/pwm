<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<%@ page import="password.pwm.http.bean.SetupOtpBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = JspUtility.getSessionBean(pageContext,SetupOtpBean.class); %>
<% final int otpTokenLength = PwmRequest.forRequest(request,response).getPwmApplication().getOtpService().getSettings().getOtpTokenLength(); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_SetupOtpSecret" displayIfMissing="true"/></div>
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
    });
</script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/otpsecret.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
