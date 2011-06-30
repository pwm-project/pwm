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
<%@ page import="com.novell.ldapchai.cr.ChallengeSet" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.SetupResponsesBean" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final SessionStateBean ssBean = PwmSession.getPwmSession(session).getSessionStateBean();
    final ChallengeSet challengeSet = PwmSession.getPwmSession(session).getUserInfoBean().getChallengeSet();
    final SetupResponsesBean responseBean = PwmSession.getPwmSession(session).getSetupResponseBean();
%>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<body class="tundra"
      onload="pwmPageLoadHandler();startupResponsesPage(false); document.forms.setupResponses.elements[0].focus();">
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='responses.js'/>"></script>
<div id="wrapper">
    <jsp:include page="header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupResponses"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_SetupResponses"/></p>

        <form action="<pwm:url url='SetupResponses'/>" method="post" name="setupResponses"
              onkeypress="checkForCapsLock(event);"
              enctype="application/x-www-form-urlencoded"
              onreset="handleFormClear();showSuccess('<pwm:Display key="Display_ResponsesPrompt"/>');return false">
            <% if (PwmSession.getPwmSession(session).getSessionStateBean().getSessionError() != null) { %>
            <span id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
            <% } else { %>
            <span id="error_msg" class="msg-success"><pwm:Display key="Display_ResponsesPrompt"/></span>
            <% } %>
            <% // display fields for REQUIRED challenges.
                if (!challengeSet.getRequiredChallenges().isEmpty()) {
            %>
            <%--<h1><pwm:Display key="Title_SetupRequiredResponses"/></h1>--%>
            <p><pwm:Display key="Display_SetupRequiredResponses"/></p>
            <%
                for (final String indexKey : responseBean.getIndexedChallenges().keySet()) {
                    final Challenge challenge = responseBean.getIndexedChallenges().get(indexKey);
                    if (challenge.isRequired()) {
            %>
            <% if (challenge.isAdminDefined()) { %>
            <h2><label for="PwmResponse_R_<%=indexKey%>"><%= StringEscapeUtils.escapeHtml(challenge.getChallengeText()) %>
            </label></h2>
            <% } else { %>
            <label for="PwmResponse_R_<%=indexKey%>"><pwm:Display key="Field_User_Supplied_Question"/>:</label>&nbsp;
            <input type="text" name="PwmResponse_Q_<%=indexKey%>" class="inputfield"
                   value="<%= StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty("PwmResponse_Q_" + indexKey, ""))%>"
                   onkeyup="validateResponses();"/>
            <% } %>
            <p>
                &nbsp;»&nbsp;
                <input type="text" name="PwmResponse_R_<%=indexKey%>" class="inputfield" maxlength="255"
                       id="PwmResponse_R_<%=indexKey%>"
                       value="<%= StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty("PwmResponse_R_" + indexKey,"")) %>"
                       onkeyup="validateResponses();"/>
            </p>
            <% } %>
            <% } %>
            <% } %>
            <% // display fields for RANDOM challenges using SIMPLE mode
                if (responseBean.isSimpleMode()) {
                    for (int i = 0; i < responseBean.getMinRandomSetup(); i++) {
            %>
            <h2>
                <select name="PwmResponse_Q_Random_<%=i%>" id="PwmResponse_Q_Random_<%=i%>" style="font-weight:normal;"
                        onchange="makeSelectOptionsDistinct();getObject('PwmResponse_R_Random_<%=i%>').value = '';validateResponses();getObject('PwmResponse_R_Random_<%=i%>').focus()">
                    <%
                        for (final String indexKey : responseBean.getIndexedChallenges().keySet()) {
                            final Challenge challenge = responseBean.getIndexedChallenges().get(indexKey);
                            if (!challenge.isRequired()) {
                                final boolean selected = challenge.getChallengeText().equals(ssBean.getLastParameterValues().getProperty("PwmResponse_Q_Random_" + i, ""));
                    %>
                    <option <%=selected ? "selected=\"yes\"" : ""%>
                            value="<%=StringEscapeUtils.escapeHtml(challenge.getChallengeText())%>"><%=StringEscapeUtils.escapeHtml(challenge.getChallengeText())%>
                    </option>
                    <% } %>
                    <% } %>
                </select>
                <script type="text/javascript">
                    simpleRandomSelectElements.push(getObject('PwmResponse_Q_Random_<%=i%>'));
                </script>
            </h2>
            <p>
                &nbsp;»&nbsp;
                <input type="password" name="PwmResponse_R_Random_<%=i%>" class="inputfield" maxlength="255" type="text"
                       id="PwmResponse_R_Random_<%=i%>"
                       value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_R_Random_" + i,"") %>"
                       onkeyup="validateResponses();"/>
            </p>
            <% } %>
            <script type="text/javascript"> dojo.addOnLoad(function() {
                makeSelectOptionsDistinct();
            })</script>
            <% } else { %>
            <% // display fields for RANDOM challenges.
                if (!challengeSet.getRandomChallenges().isEmpty()) {
            %>
            <p><pwm:Display key="Display_SetupRandomResponses"
                            value1="<%= String.valueOf(challengeSet.getMinRandomRequired()) %>"/></p>
            <%
                for (final String indexKey : responseBean.getIndexedChallenges().keySet()) {
                    final Challenge challenge = responseBean.getIndexedChallenges().get(indexKey);
                    if (!challenge.isRequired()) {
            %>
            <% if (challenge.isAdminDefined()) { %>
            <h2><%= StringEscapeUtils.escapeHtml(challenge.getChallengeText()) %>
            </h2>
            <% } else { %>
            <pwm:Display key="Field_User_Supplied_Question"/>&nbsp;
            <input type="text" name="PwmResponse_Q_<%=indexKey%>" class="inputfield"
                   value="<%=StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty("PwmResponse_Q_" + indexKey, ""))%>"
                   onkeyup="validateResponses();"/>
            <% } %>
            <p>
                &nbsp;»&nbsp;
                <input type="text" name="PwmResponse_R_<%=indexKey%>" class="inputfield" maxlength="255"
                       value="<%=StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty("PwmResponse_R_" + indexKey,""))%>"
                       onkeyup="validateResponses();"/>
            </p>
            <% } %>
            <% } %>
            <% } %>
            <% } %>

            <div id="buttonbar">
                <span>
                    <div id="capslockwarning" style="visibility:hidden;"><pwm:Display key="Display_CapsLockIsOn"/></div>
                </span>
                <input type="hidden" name="processAction" value="setResponses"/>
                <input type="submit" name="setResponses" class="btn" id="setresponses_button"
                       value="    <pwm:Display key="Button_SetResponses"/>    "/>
                <% if (password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="    <pwm:Display key="Button_Reset"/>    "/>
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
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <br class="clear"/>
</div>
<script type="text/javascript">
    PWM_STRINGS['Button_Hide_Responses'] = "<pwm:Display key="Button_Hide_Responses"/>";
    PWM_STRINGS['Button_Show_Responses'] = "<pwm:Display key="Button_Show_Responses"/>";
    PWM_STRINGS['Display_CheckingResponses'] = "<pwm:Display key="Display_CheckingResponses"/>";
    PWM_STRINGS['Display_CommunicationError'] = "<pwm:Display key="Display_CommunicationError"/>";
    PWM_STRINGS['url-setupresponses'] = '<pwm:url url='SetupResponses'/>';
</script>
<%@ include file="footer.jsp" %>
</body>
</html>
