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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.bean.ForgottenPasswordBean" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request, response); %>
<% final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean(); %>
<% final ForgottenPasswordBean recoverBean = pwmRequest.getPwmSession().getForgottenPasswordBean(); %>
<% final List<FormConfiguration> requiredAttrParams = recoverBean.getAttributeForm(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<%--
in the body onload below, the true parameter toggles the hide button an extra time to default the page to hiding the responses.
this is handled this way so on browsers where hiding fields is not possible, the default is to show the fields.
--%>
<body class="nihilo">
<div id="wrapper">
<jsp:include page="fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_RecoverPassword"/>
</jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_RecoverPassword"/></p>

        <form name="responseForm" action="<pwm:url url='ForgottenPassword'/>" method="post"
              enctype="application/x-www-form-urlencoded" class="pwm-form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

            <% // loop through required attributes (challenge.requiredAttributes), if any are configured
                for (final FormConfiguration paramConfig : requiredAttrParams) {
            %>
            <h2><label for="attribute-<%= paramConfig.getName()%>"><%= paramConfig.getLabel(ssBean.getLocale()) %>
            </label></h2>
            <input type="<pwm:value name="responseFieldType"/>" name="<%= paramConfig.getName()%>" class="inputfield passwordfield" maxlength="255"
                   id="attribute-<%= paramConfig.getName()%>" required="required"
                   value="<%= ssBean.getLastParameterValues().get(paramConfig.getName(),"") %>"/>
            <% } %>

            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) { %>
            <% // loop through challenges
                int counter = 0;
                for (final Challenge loopChallenge : recoverBean.getPresentableChallengeSet().getChallenges()) {
                    counter++;
            %>
            <h2><label for="PwmResponse_R_<%=counter%>"><%= loopChallenge.getChallengeText() %>
            </label></h2>
            <input type="<pwm:value name="responseFieldType"/>" name="PwmResponse_R_<%= counter %>" class="inputfield passwordfield" maxlength="255"
                   id="PwmResponse_R_<%=counter%>" required="required"
                   value="<%= ssBean.getLastParameterValues().get("PwmResponse_R_" + counter,"") %>"/>
            <% } %>
            <% } %>
            <div id="buttonbar">
                <input type="hidden" name="processAction" value="checkResponses"/>
                <button type="submit" name="checkResponses" class="btn" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                    <pwm:display key="Button_RecoverPassword"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <pwm:if test="showCancel">
                    <button style="visibility:hidden;" type="button" name="button" class="btn" id="button_cancel" onclick="handleCancelClick()">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                </pwm:if>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<form name="responseResetForm" id="responseResetForm" action="<pwm:url url='ForgottenPassword'/>" method="post"
      enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="processAction" value="reset"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_RESPONSES.startupResponsesPage();
        document.forms.responseForm.elements[0].focus();
    });

    function handleCancelClick() {
        PWM_MAIN.showWaitDialog({
            loadFunction:function(){
                PWM_MAIN.getObject('responseResetForm').submit();
            }
        });
    }
</script>
</pwm:script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/responses.js'/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
