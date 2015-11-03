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
<%@ page import="password.pwm.bean.PasswordStatus" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest changepassword_pwmRequest = PwmRequest.forRequest(request,response); %>
<% final PasswordStatus passwordStatus = changepassword_pwmRequest.getPwmSession().getUserInfoBean().getPasswordState(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody">
        <% if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isViolatesPolicy()) { %>
        <h1><pwm:display key="Display_PasswordExpired"/></h1><br/>
        <% } %>
        <pwm:display key="Display_ChangePassword"/>
        <div id="PasswordRequirements">
            <ul>
                <pwm:DisplayPasswordRequirements separator="</li>" prepend="<li>"/>
            </ul>
        </div>
        <% final String passwordPolicyChangeMessage = changepassword_pwmRequest.getPwmSession().getUserInfoBean().getPasswordPolicy().getRuleHelper().getChangeMessage(); %>
        <% if (passwordPolicyChangeMessage.length() > 1) { %>
        <p><%= passwordPolicyChangeMessage %></p>
        <% } %>
        <br/>
        <%@ include file="fragment/message.jsp" %>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" id="changePasswordForm" autocomplete="off">
            <table style="border:0">
                <tr>
                    <td style="border:0;">
                        <div style="width: 100%">
                            <h2 style="display: inline">
                                <label style="" for="password1"><pwm:display key="Field_NewPassword"/></label>
                            </h2>
                            &nbsp;&nbsp;
                            <div class="fa fa-question-circle icon_button" id="password-guide-icon" style="cursor: pointer; visibility: hidden"></div>
                            <pwm:if test="showRandomPasswordGenerator">
                            &nbsp;&nbsp;
                            <div class="fa fa-retweet icon_button" id="autogenerate-icon" style="cursor: pointer; visibility: hidden" ></div>
                            </pwm:if>
                        </div>
                        <input type="<pwm:value name="passwordFieldType"/>" name="password1" id="password1" class="changepasswordfield passwordfield" <pwm:autofocus/>/>
                    </td>
                    <td style="border:0; width:15%">
                        <pwm:if test="showStrengthMeter">
                        <div id="strengthBox" style="visibility:hidden;">
                            <div id="strengthLabel" style="padding-top:40px;">
                                <pwm:display key="Display_StrengthMeter"/>
                            </div>
                            <div class="progress-container" style="margin-bottom:10px">
                                <div id="strengthBar" style="width: 0">&nbsp;</div>
                            </div>
                        </div>
                        </pwm:if>
                    </td>
                    <td style="border:0; width:10%">&nbsp;</td>
                </tr>
                <tr>
                    <td style="border:0; width:75%">
                        <h2 style="display: inline"><label for="password2"><pwm:display key="Field_ConfirmPassword"/></label></h2>
                        <input type="<pwm:value name="passwordFieldType"/>" name="password2" id="password2" class="changepasswordfield passwordfield"/>
                    </td>
                    <td style="border:0; width:15%">
                        <%-- confirmation mark [not shown initially, enabled by javascript; see also changepassword.js:markConfirmationMark() --%>
                        <div style="padding-top:45px;">
                            <img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15"
                                 src="<pwm:context/><pwm:url url='/public/resources/greenCheck.png'/>">
                            <img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15"
                                 src="<pwm:context/><pwm:url url='/public/resources/redX.png'/>">
                        </div>
                    </td>
                    <td style="border:0; width:10%">&nbsp;</td>
                </tr>
            </table>
            <div class="buttonbar" style="width:100%">
                <input type="hidden" name="processAction" value="change"/>
                <button type="submit" name="change" class="btn" id="password_button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_ChangePassword"/>
                </button>
                <% if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired() && !passwordStatus.isViolatesPolicy()) { %>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <% } %>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_CHANGEPW.startupChangePasswordPage();
    });
</script>
</pwm:script>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<pwm:script-ref url="/public/resources/js/changepassword.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>


