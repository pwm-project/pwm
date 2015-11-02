<%@ page import="password.pwm.http.bean.ActivateUserBean" %>
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
<% final ActivateUserBean activateUserBean = JspUtility.getPwmSession(pageContext).getActivateUserBean(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_BUTTONS); %>
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ActivateUser"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="fragment/message.jsp" %>
        <% final String expandedText = activateUserBean.getAgreementText(); %>
        <br/><br/>
        <div id="agreementText" class="agreementText"><%= expandedText %></div>
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form" style="display: inline;">
                <%-- remove the next line to remove the "I Agree" checkbox --%>
                <label class="checkboxWrapper">
                    <input type="checkbox" id="agreeCheckBox"/>
                    <pwm:display key="Button_Agree"/>
                </label>
                    <br/><br/>
                <input type="hidden" name="processAction" value="agree"/>
                <button type="submit" name="button" class="btn" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" style="display: inline;">
                <input type="hidden" name="processAction" value="reset"/>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function updateContinueButton() {
            var checkBox = PWM_MAIN.getObject("agreeCheckBox");
            var continueButton = PWM_MAIN.getObject("submitBtn");
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
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
