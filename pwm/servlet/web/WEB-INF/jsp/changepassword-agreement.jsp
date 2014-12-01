<%@ page import="password.pwm.bean.PasswordStatus" %>
<%@ page import="password.pwm.http.JspUtility" %>
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_BUTTONS); %>
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody">
        <% final PasswordStatus passwordStatus = PwmSession.getPwmSession(session).getUserInfoBean().getPasswordState(); %>
        <% if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isViolatesPolicy()) { %>
        <h1><pwm:display key="Display_PasswordExpired"/></h1><br/>
        <% } %>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <div id="agreementText" class="agreementText"><%= (String)request.getAttribute(PwmConstants.REQUEST_ATTR_AGREEMENT_TEXT) %></div>
        <div id="buttonbar">
            <form action="<pwm:url url='ChangePassword'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <%-- remove the next line to remove the "I Agree" checkbox --%>
                <label class="checkboxWrapper">
                    <input type="checkbox" id="agreeCheckBox"/>
                    <pwm:display key="Button_Agree"/>
                </label>
                <input type="hidden" name="processAction" value="agree"/>
                <button type="submit" name="button" class="btn" id="button_continue">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <br/>
            <form action="<pwm:url url='/public/Logout' addContext="true"/>" method="post" enctype="application/x-www-form-urlencoded">
                <button type="submit" name="button" class="btn" id="button_logout">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-out"></span></pwm:if>
                    <pwm:display key="Button_Logout"/>
                </button>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function updateContinueButton() {
            var checkBox = PWM_MAIN.getObject("agreeCheckBox");
            var continueButton = PWM_MAIN.getObject("button_continue");
            if (checkBox != null && continueButton != null) {
                if (checkBox.checked) {
                    continueButton.removeAttribute('disabled');
                } else {
                    continueButton.disabled = "disabled";
                }
            }
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('agreeCheckBox','click, change',function(){ updateContinueButton() });
            updateContinueButton();
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
