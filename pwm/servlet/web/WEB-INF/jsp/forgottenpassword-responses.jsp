<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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
<%@ page import="password.pwm.bean.ForgottenPasswordBean" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SessionStateBean ssBean = PwmSession.getPwmSession(session).getSessionStateBean(); %>
<% final ForgottenPasswordBean recoverBean = PwmSession.getPwmSession(session).getForgottenPasswordBean(); %>
<% final List<FormConfiguration> requiredAttrParams = PwmSession.getPwmSession(session).getConfig().readSettingAsForm(PwmSetting.CHALLENGE_REQUIRED_ATTRIBUTES, ssBean.getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<%--
in the body onload below, the true parameter toggles the hide button an extra time to default the page to hiding the responses.
this is handled this way so on browsers where hiding fields is not possible, the default is to show the fields.
--%>
<body onload="pwmPageLoadHandler(); startupResponsesPage(); document.forms.responseForm.elements[0].focus();"
      class="tundra">
<div id="wrapper">
<jsp:include page="header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_RecoverPassword"/>
</jsp:include>
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='responses.js'/>"></script>
    <div id="centerbody">
        <p><pwm:Display key="Display_RecoverPassword"/></p>

        <form name="responseForm" action="<pwm:url url='ForgottenPassword'/>" method="post"
              enctype="application/x-www-form-urlencoded"
              onsubmit="handleFormSubmit('submitBtn',this);return false" onreset="handleFormClear();return false">
            <% //check to see if there is an error
                if (PwmSession.getPwmSession(session).getSessionStateBean().getSessionError() != null) {
            %>
            <span id="error_msg" class="msg-error">
                <pwm:ErrorMessage/>
            </span>
            <% } %>

            <% // loop through required attributes (challenge.requiredAttributes), if any are configured
                for (final FormConfiguration paramConfig : requiredAttrParams) {
            %>
            <h2><label for="attribute-<%= paramConfig.getAttributeName()%>"><%= paramConfig.getLabel() %>
            </label></h2>
            <input type="password" name="<%= paramConfig.getAttributeName()%>" class="inputfield" maxlength="255"
                   id="attribute-<%= paramConfig.getAttributeName()%>"
                   value="<%= ssBean.getLastParameterValues().getProperty(paramConfig.getAttributeName(),"") %>"/>
            <% } %>

            <% if (Configuration.getConfig(session).readSettingAsBoolean(PwmSetting.CHALLENGE_REQUIRE_RESPONSES)) { %>
            <% // loop through challenges
                int counter = 0;
                for (final Challenge loopChallenge : recoverBean.getResponseSet().getChallengeSet().getChallenges()) {
                    counter++;
            %>
            <h2><label for="PwmResponse_R_<%=counter%>"><%= loopChallenge.getChallengeText() %>
            </label></h2>
            <input type="password" name="PwmResponse_R_<%= counter %>" class="inputfield" maxlength="255"
                   id="PwmResponse_R_<%=counter%>"
                   value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_R_" + counter,"") %>"/>
            <% } %>
            <% } %>

            <div id="buttonbar">
                <input type="hidden" name="processAction" value="checkResponses"/>
                <input type="submit" name="checkResponses" class="btn"
                       value="     <pwm:Display key="Button_RecoverPassword"/>     "
                       id="submitBtn"/>
                <% if (password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="     <pwm:Display key="Button_Reset"/>     "/>
                <% } %>
                <input type="hidden" name="hideButton" class="btn"
                       value="    <pwm:Display key="Button_Hide_Responses"/>    "
                       onclick="toggleHideResponses();" id="hide_responses_button"/>
                <% if (password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                        onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                    &nbsp;&nbsp;&nbsp;<pwm:Display key="Button_Cancel"/>&nbsp;&nbsp;&nbsp;
                </button>
                <script type="text/javascript">getObject('button_cancel').style.visibility = 'visible';</script>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <br class="clear"/>
</div>
<script type="text/javascript">
    PWM_STRINGS['Button_Hide_Responses'] = '<pwm:Display key="Button_Hide_Responses"/>';
    PWM_STRINGS['Button_Show_Responses'] = '<pwm:Display key="Button_Show_Responses"/>';
</script>
<%@ include file="footer.jsp" %>
</body>
</html>