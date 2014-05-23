<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<%@ page import="password.pwm.bean.servlet.SetupOtpBean" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = PwmSession.getPwmSession(session).getSetupOtpBean();%>
<html dir="<pwm:LocaleOrientation/>">
    <%@ include file="fragment/header.jsp" %>
    <body class="nihilo">
        <script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/otpsecret.js'/>"></script>
        <div id="wrapper">
            <jsp:include page="fragment/header-body.jsp">
                <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
            </jsp:include>
            <div id="centerbody">
                <p><pwm:Display key="Display_PleaseVerifyOtp"/></p>
                <form action="<pwm:url url='SetupOtpSecret'/>" method="post" name="setupOtpSecret"
                      enctype="application/x-www-form-urlencoded" onchange="" id="setupOtpSecret"
                      onsubmit="PWM_MAIN.handleFormSubmit('setotpsecret_button', this); return false;">
                    <%@ include file="fragment/message.jsp" %>
                    <script type="text/javascript">PWM_GLOBAL['responseMode'] = "user";</script>
                    <h1>
                        <label for="PwmOneTimePassword"><pwm:Display key="Field_OneTimePassword"/></label>
                    </h1>
                    <input type="password" name="<%= PwmConstants.PARAM_OTP_TOKEN%>" class="inputfield" maxlength="<%= PwmConstants.OTP_TOKEN_LENGTH%>" type="text"
                           id="<%= PwmConstants.PARAM_OTP_TOKEN%>" required="required"
                           onkeyup="validateResponses();"/>
                    <div id="buttonbar">
                        <input type="hidden" name="processAction" value="setOtpSecret"/>
                        <button type="submit" name="setOtpSecret" class="btn" id="setotpsecret_button">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span>&nbsp</pwm:if>
                            <pwm:Display key="Button_CheckCode"/>
                        </button>
                        <%@ include file="fragment/button-cancel.jsp"%>
                        <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                    </div>
                </form>
            </div>
            <div class="push"></div>
        </div>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function() {
                document.getElementById("<%= PwmConstants.PARAM_OTP_TOKEN%>").focus();
                ShowHidePasswordHandler.initAllForms();
            });
        </script>
        <%@ include file="fragment/footer.jsp" %>
    </body>
</html>
