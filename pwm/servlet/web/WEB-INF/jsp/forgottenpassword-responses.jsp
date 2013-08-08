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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.bean.servlet.ForgottenPasswordBean" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SessionStateBean ssBean = PwmSession.getPwmSession(session).getSessionStateBean(); %>
<% final ForgottenPasswordBean recoverBean = PwmSession.getPwmSession(session).getForgottenPasswordBean(); %>
<% final List<FormConfiguration> requiredAttrParams = recoverBean.getAttributeForm(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<%--
in the body onload below, the true parameter toggles the hide button an extra time to default the page to hiding the responses.
this is handled this way so on browsers where hiding fields is not possible, the default is to show the fields.
--%>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
<jsp:include page="fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_RecoverPassword"/>
</jsp:include>
    <script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/responses.js'/>"></script>
    <div id="centerbody">
        <p><pwm:Display key="Display_RecoverPassword"/></p>

        <form name="responseForm" action="<pwm:url url='ForgottenPassword'/>" method="post"
              enctype="application/x-www-form-urlencoded"
              onsubmit="handleFormSubmit('submitBtn',this);return false">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

            <% // loop through required attributes (challenge.requiredAttributes), if any are configured
                for (final FormConfiguration paramConfig : requiredAttrParams) {
            %>
            <h2><label for="attribute-<%= paramConfig.getName()%>"><%= paramConfig.getLabel(ssBean.getLocale()) %>
            </label></h2>
            <input type="password" name="<%= paramConfig.getName()%>" class="inputfield" maxlength="255"
                   id="attribute-<%= paramConfig.getName()%>" required="required"
                   value="<%= ssBean.getLastParameterValues().get(paramConfig.getName(),"") %>"/>
            <% } %>

            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) { %>
            <% // loop through challenges
                int counter = 0;
                for (final Challenge loopChallenge : recoverBean.getChallengeSet().getChallenges()) {
                    counter++;
            %>
            <h2><label for="PwmResponse_R_<%=counter%>"><%= loopChallenge.getChallengeText() %>
            </label></h2>
            <input type="password" name="PwmResponse_R_<%= counter %>" class="inputfield" maxlength="255"
                   id="PwmResponse_R_<%=counter%>" required="required"
                   value="<%= ssBean.getLastParameterValues().get("PwmResponse_R_" + counter,"") %>"/>
            <% } %>
            <% } %>

            <div id="buttonbar">
                <input type="hidden" name="processAction" value="checkResponses"/>
                <input type="submit" name="checkResponses" class="btn"
                       value="<pwm:Display key="Button_RecoverPassword"/>"
                       id="submitBtn"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        startupResponsesPage();
        document.forms.responseForm.elements[0].focus();
        ShowHidePasswordHandler.initAllForms();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
