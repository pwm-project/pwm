<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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
<%@ page import="password.pwm.config.PasswordStatus" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PasswordStatus passwordStatus = PwmSession.getPwmSession(session).getUserInfoBean().getPasswordState(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody">
        <% if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isViolatesPolicy()) { %>
        <h1><pwm:Display key="Display_PasswordExpired"/></h1><br/>
        <%-- <p/>You have <pwm:LdapValue name="loginGraceRemaining"/> remaining logins. --%>
        <% } %>
        <p><pwm:Display key="Display_ChangePasswordForm"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:url url='ChangePassword'/>" method="post" enctype="application/x-www-form-urlencoded"
              onsubmit="handleFormSubmit('change_button',this);return false"
              onreset="setInputFocus()" name="changePasswordForm" id="changePasswordForm">
            <% if (PwmSession.getPwmSession(session).getChangePasswordBean().isCurrentPasswordRequired()) { %>
            <h1>
                <label for="currentPassword"><pwm:Display key="Field_CurrentPassword"/></label>
            </h1>
            <input id="currentPassword" type="password" class="inputfield" name="currentPassword"/>
            <br/>
            <% } %>
            <% request.setAttribute("form",PwmSetting.PASSWORD_REQUIRE_FORM); %>
            <jsp:include page="fragment/form.jsp"/>
            <div id="buttonbar" style="width:100%">
                <input type="hidden" name="processAction" value="form"/>
                <input type="submit" name="change" class="btn"
                       id="continue_button"
                       value="<pwm:Display key="Button_Continue"/>"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <input type="hidden" name="hideButton" class="btn"
                       value="<pwm:Display key="Button_Show"/>"
                       onclick="toggleMaskPasswords()" id="hide_button"/>
                <% if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired() && !passwordStatus.isViolatesPolicy()) { %>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <% } %>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        document.forms.changePasswordForm.elements[0].focus();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>


