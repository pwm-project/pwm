<%@ page import="password.pwm.bean.servlet.SetupOtpBean" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = PwmSession.getPwmSession(session).getSetupOtpBean();%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret" />
    </jsp:include>
    <div id="centerbody">
        <p>
            <% if (pwmSessionHeader.getUserInfoBean().getOtpUserRecord() != null && pwmSessionHeader.getUserInfoBean().getOtpUserRecord().getTimestamp() != null) { %>
            <pwm:Display key="Display_WarnExistingOtpSecretTime" value1="@OtpSetupTime@"/>
            <% } else { %>
            <pwm:Display key="Display_WarnExistingOtpSecret"/>
            <% } %>
        </p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <h2>Verify Existing Code</h2>
        <table style="width: 130px; border:0; margin-left: 0">
            <tr>
                <td style="width: 110px; border: 0">
                    <input type="text" pattern="[0-9].*" id="verifyCodeInput" autofocus maxlength="6" style="width: 100px" oninput="checkCode()">
                </td>
                <td style="border: 0; width: 10px">
                    <span style="display:none;color:green" id="checkIcon" class="btn-icon fa fa-lg fa-check"></span>
                    <span style="display:none;color:red" id="crossIcon" class="btn-icon fa fa-lg fa-times"></span>
                </td>
                <td style="border: 0; width: 10px">
                    <span style="visibility: hidden" id="workingIcon" class="fa fa-lg fa-spin fa-spinner"></span>
                </td>
            </tr>
        </table>
        <div id="buttonbar">
            <form action="<pwm:url url='SetupOtp'/>" method="post" name="setupOtpSecretForm" style="display: inline"
                  enctype="application/x-www-form-urlencoded" onchange="" id="setupOtpSecretForm"
                  onsubmit="confirmContinue();return false">
                <input type="hidden" name="processAction" value="clearOtp"/>
                <button type="submit" name="Button_Continue" class="btn" id="continue_button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:Display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="application/javascript">
    function checkCode() {
        PWM_MAIN.getObject('crossIcon').style.display = 'none';
        PWM_MAIN.getObject('checkIcon').style.display = 'none';
        PWM_MAIN.getObject('workingIcon').style.visibility = 'visible';
        PWM_MAIN.pwmFormValidator({
            serviceURL:"SetupOtp?processAction=restValidateCode",
            readDataFunction:function(){
                var paramData = { };
                paramData['code'] = PWM_MAIN.getObject('verifyCodeInput').value;
                return paramData;
            },
            showMessage:false,
            processResultsFunction:function(result){
                if (result['data']) {
                    PWM_MAIN.getObject('checkIcon').style.display = 'inherit';
                    PWM_MAIN.getObject('crossIcon').style.display = 'none';
                } else {
                    PWM_MAIN.getObject('checkIcon').style.display = 'none';
                    PWM_MAIN.getObject('crossIcon').style.display = 'inherit';
                }
            },
            completeFunction:function(){
                PWM_MAIN.getObject('workingIcon').style.visibility = 'hidden';
            }
        });
    }

    function confirmContinue() {
        PWM_MAIN.showConfirmDialog({
            text: PWM_MAIN.showString("Display_OtpClearWarning"),
            okFunction:function(){
                PWM_MAIN.handleFormSubmit('setotpsecret_button', PWM_MAIN.getObject('setupOtpSecretForm'));
            }
        });
    }

</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
