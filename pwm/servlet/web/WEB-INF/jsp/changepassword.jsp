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
<%@ page import="password.pwm.bean.PasswordStatus" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PasswordStatus passwordStatus = PwmSession.getPwmSession(session).getUserInfoBean().getPasswordState(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody">
        <% if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isViolatesPolicy()) { %>
        <h1><pwm:Display key="Display_PasswordExpired"/></h1><br/>
        <% } %>
        <pwm:Display key="Display_ChangePassword"/>
        <div id="PasswordRequirements">
            <ul>
                <pwm:DisplayPasswordRequirements separator="</li>" prepend="<li>"/>
            </ul>
        </div>
        <% final String passwordPolicyChangeMessage = PwmSession.getPwmSession(session).getUserInfoBean().getPasswordPolicy().getRuleHelper().getChangeMessage(); %>
        <% if (passwordPolicyChangeMessage.length() > 1) { %>
        <p><%= passwordPolicyChangeMessage %></p>
        <% } %>
        <br/>
        <%@ include file="fragment/message.jsp" %>
        <form action="<pwm:url url='ChangePassword'/>" method="post" enctype="application/x-www-form-urlencoded"
              onkeyup="PWM_CHANGEPW.validatePasswords(null);" onchange="PWM_CHANGEPW.validatePasswords(null);"
              onsubmit="PWM_CHANGEPW.handleChangePasswordSubmit(); PWM_MAIN.handleFormSubmit(this);return false"
              onreset="PWM_CHANGEPW.validatePasswords(null);PWM_CHANGEPW.setInputFocus();return false;" name="changePasswordForm"
              id="changePasswordForm">
            <table style="border:0">
                <tr>
                    <td style="border:0;">
                        <div style="width: 100%">
                            <h2 style="display: inline">
                                <label style="" for="password1"><pwm:Display key="Field_NewPassword"/></label>
                            </h2>
                            &nbsp;&nbsp;
                            <div class="fa fa-question-circle icon_button" id="password-guide-icon" style="cursor: pointer; visibility: hidden" onclick="PWM_CHANGEPW.showPasswordGuide()" ></div>
                            <pwm:if test="showRandomPasswordGenerator">
                            &nbsp;&nbsp;
                            <div class="fa fa-retweet icon_button" id="autogenerate-icon" style="cursor: pointer; visibility: hidden" onclick="PWM_CHANGEPW.doRandomGeneration();" ></div>
                            </pwm:if>
                        </div>
                        <input type="password" name="password1" id="password1" class="changepasswordfield" autofocus/>
                    </td>
                    <td style="border:0; width:15%">
                        <pwm:if test="showStrengthMeter">
                        <div id="strengthBox" style="visibility:hidden;">
                            <div id="strengthLabel" style="padding-top:40px;">
                                <pwm:Display key="Display_StrengthMeter"/>
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
                        <h2 style="display: inline"><label for="password2"><pwm:Display key="Field_ConfirmPassword"/></label></h2>
                        <input type="password" name="password2" id="password2" class="changepasswordfield"/>
                    </td>
                    <td style="border:0; width:15%">
                        <%-- confirmation mark [not shown initially, enabled by javascript; see also changepassword.js:markConfirmationMark() --%>
                        <div style="padding-top:45px;">
                            <img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15"
                                 src="<%=request.getContextPath()%><pwm:url url='/public/resources/greenCheck.png'/>">
                            <img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15"
                                 src="<%=request.getContextPath()%><pwm:url url='/public/resources/redX.png'/>">
                        </div>
                    </td>
                    <td style="border:0; width:10%">&nbsp;</td>
                </tr>
            </table>
            <div id="buttonbar" style="width:100%">
                <input type="hidden" name="processAction" value="change"/>
                <button type="submit" name="change" class="btn" id="password_button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:Display key="Button_ChangePassword"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <% if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired() && !passwordStatus.isViolatesPolicy()) { %>
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                <% } %>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_CHANGEPW.startupChangePasswordPage();
        ShowHidePasswordHandler.initAllForms();
    });
</script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/changepassword.js'/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>


