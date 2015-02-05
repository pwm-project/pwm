<%@ page import="password.pwm.http.bean.SetupOtpBean" %>
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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = JspUtility.getPwmSession(pageContext).getSetupOtpBean();%>
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
        <table class="noborder" style="table-layout: fixed; width: 150px">
            <colgroup>
                <col style="width:130px;"/>
                <col style="width:20px;padding-top: 5px"/>
            </colgroup>
                
            <tr>
                <td>
                    <input type="text" class="inputfield" style="max-width: 130px; width: 130px" pattern="[0-9].*" id="verifyCodeInput" autofocus maxlength="6" />
                </td>
                <td>
                    <span style="display:none;color:green" id="checkIcon" class="btn-icon fa fa-lg fa-check"></span>
                    <span style="display:none;color:red" id="crossIcon" class="btn-icon fa fa-lg fa-times"></span>
                    <span style="display:none" id="workingIcon" class="fa fa-lg fa-spin fa-spinner"></span>
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <button type="submit" name="button-verifyCode" class="btn" id="button-verifyCode">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                        <pwm:display key="Button_CheckCode"/>
                    </button>
                </td>
            </tr>
        </table>
        <div class="buttonbar">
            <form action="<pwm:url url='SetupOtp'/>" method="post" name="setupOtpSecretForm" style="display: inline"
                  enctype="application/x-www-form-urlencoded" id="setupOtpSecretForm">
                <input type="hidden" name="processAction" value="clearOtp"/>
                <button type="submit" name="Button_Continue" class="btn" id="continue_button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-recycle"></span></pwm:if>
                    <pwm:display key="Button_ClearOtpReEnroll"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="application/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_OTP.initExistingOtpPage();
        PWM_MAIN.addEventHandler('button-verifyCode','click',function(){
            PWM_OTP.checkExistingCode();
        })
    });
</script>
</pwm:script>
<script type="text/javascript" src="<pwm:context/><pwm:url url='/public/resources/js/otpsecret.js'/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
