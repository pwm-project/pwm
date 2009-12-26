<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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
<%@ page import="password.pwm.config.ParameterConfig" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SessionStateBean ssBean = PwmSession.getSessionStateBean(request.getSession()); %>
<% final ForgottenPasswordBean recoverBean = PwmSession.getForgottenPasswordBean(request); %>
<% final Map<String,ParameterConfig> requiredAttrParams = PwmSession.getPwmSession(request).getLocaleConfig().getChallengeRequiredAttributes(); %>
<% int tabIndex = 0; %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<%--
in the body onload below, the true parameter toggles the hide button an extra time to default the page to hiding the responses.
this is handled this way so on browsers where hiding fields is not possible, the default is to show the fields.
--%>
<body onload="startupPage(true); document.forms.responseForm.elements[0].focus();" onunload="unloadHandler();">
<jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="Title_RecoverPassword"/></jsp:include>
<div id="wrapper">
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='responses.js'/>"></script>
    <div id="centerbody">
        <p><pwm:Display key="Display_RecoverPassword"/></p>
        <form name="responseForm" action="<pwm:url url='ForgottenPassword'/>" method="post" enctype="application/x-www-form-urlencoded" autocomplete="off"
                onsubmit="handleFormSubmit('submitBtn');" onreset="handleFormClear();">
            <%  //check to see if there is an error
                if (PwmSession.getSessionStateBean(session).getSessionError() != null) {
            %>
            <span id="error_msg" class="msg-error">
                <pwm:ErrorMessage/>
            </span>
            <% } %>

            <% // loop through required attributes (challenge.requiredAttributes), if any are configured
                for (final ParameterConfig paramConfig : requiredAttrParams.values()) {
            %>
            <h2><%= paramConfig.getLabel() %></h2>
            <input type="password" name="<%= paramConfig.getAttributeName() %>" class="inputfield" maxlength="255"
                   tabindex="<%=++tabIndex%>"
                   value="<%= ssBean.getLastParameterValues().getProperty(paramConfig.getAttributeName(),"") %>"/>
            <% } %>

            <% // loop through challenges
                int counter = 0;
                for (final Challenge loopChallenge : recoverBean.getResponseSet().getChallengeSet().getChallenges()) {
                    counter++;
            %>
            <h2><%= loopChallenge.getChallengeText() %></h2>
            <input type="password" name="PwmResponse_R_<%= counter %>" class="inputfield" maxlength="255"
                   tabindex="<%=++tabIndex%>" id="PwmResponse_R_<%=counter%>"
                   value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_R_" + counter,"") %>"/>
            <% } %>

            <div id="buttonbar">
                <input type="hidden" name="processAction" value="checkResponses"/>
                <input type="submit" name="checkResponses" tabindex="<%=++tabIndex%>" class="btn"
                       value="     <pwm:Display key="Button_RecoverPassword"/>     "
                       id="submitBtn"/>
                <input type="reset" name="reset" tabindex="<%=++tabIndex%>" class="btn"
                       value="     <pwm:Display key="Button_Reset"/>     "/>
                <input type="hidden" name="hideButton" tabindex="<%=++tabIndex%>" class="btn"
                       value="    <pwm:Display key="Button_Hide_Responses"/>    "
                       onclick="toggleHideResponses();" id="hide_responses_button"/>

            </div>
        </form>
    </div>
    <br class="clear"/>
</div>
<form action="" name="responses_i18n">
    <input type="hidden" name="Js_Button_Hide_Responses" id="Js_Button_Hide_Responses" value="<pwm:Display key="Button_Hide_Responses"/>"/>
    <input type="hidden" name="Js_Button_Show_Responses" id="Js_Button_Show_Responses" value="<pwm:Display key="Button_Show_Responses"/>"/>
</form>
<%@ include file="footer.jsp" %>
</body>
</html>