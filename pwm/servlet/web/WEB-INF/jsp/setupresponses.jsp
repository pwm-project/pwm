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
<%@ page import="com.novell.ldapchai.cr.ChallengeSet" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.SetupResponsesBean" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final SessionStateBean ssBean = PwmSession.getPwmSession(session).getSessionStateBean();
    final ChallengeSet challengeSet = PwmSession.getPwmSession(session).getUserInfoBean().getChallengeSet();
    final SetupResponsesBean responseBean = PwmSession.getPwmSession(session).getSetupResponseBean();
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo"
      onload="pwmPageLoadHandler();startupResponsesPage('<pwm:Display key="Display_ResponsesPrompt"/>'); document.forms[0].elements[0].focus();">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/resources/responses.js'/>"></script>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupResponses"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_SetupResponses"/></p>

        <form action="<pwm:url url='SetupResponses'/>" method="post" name="setupResponses"
              onkeypress="checkForCapsLock(event);"
              enctype="application/x-www-form-urlencoded"
              onreset="handleFormClear();showSuccess('<pwm:Display key="Display_ResponsesPrompt"/>');return false"
              onsubmit="handleFormSubmit('setresponses_button',this);return false" onreset="handleFormClear();return false">
            <%@ include file="fragment/message.jsp" %>
            <% // display fields for REQUIRED challenges.
                if (!challengeSet.getRequiredChallenges().isEmpty()) {
            %>
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
            <label for="PwmResponse_Q_<%=indexKey%>"><pwm:Display key="Field_User_Supplied_Question"/>:</label>&nbsp;
            <textarea name="PwmResponse_Q_<%=indexKey%>" id="PwmResponse_Q_<%=indexKey%>" data-dojo-type="dijit.form.Textarea" style="width: 410px"
                   class="inputfield" onkeyup="validateResponses();"><%= StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty("PwmResponse_Q_" + indexKey, ""))%></textarea>
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
                <select name="PwmResponse_Q_Random_<%=i%>" id="PwmResponse_Q_Random_<%=i%>" style="font-weight:normal"
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
            <script type="text/javascript">
                require(["dojo"],function(){makeSelectOptionsDistinct();});
            </script>
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
            <label for="PwmResponse_Q_<%=indexKey%>"><pwm:Display key="Field_User_Supplied_Question"/>:</label>&nbsp;
            <textarea name="PwmResponse_Q_<%=indexKey%>" id="PwmResponse_Q_<%=indexKey%>" data-dojo-type="dijit.form.Textarea" style="width: 410px"
                      class="inputfield" onkeyup="validateResponses();"><%= StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty("PwmResponse_Q_" + indexKey, ""))%></textarea>
            <% } %>
            <p>
                &nbsp;»&nbsp;
                <input type="text" name="PwmResponse_R_<%=indexKey%>" class="inputfield" maxlength="255" id="PwmResponse_R_<%=indexKey%>"
                       value="<%=StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().getProperty("PwmResponse_R_" + indexKey,""))%>"
                       onkeyup="validateResponses();"/>
            </p>
            <% } %>
            <% } %>
            <% } %>
            <% } %>

            <div id="buttonbar">
                <input type="hidden" name="processAction" value="setResponses"/>
                <input type="submit" name="setResponses" class="btn" id="setresponses_button"
                       value="<pwm:Display key="Button_SetResponses"/>"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <input type="hidden" name="hideButton" class="btn"
                       value="<pwm:Display key="Button_Hide_Responses"/>"
                       onclick="toggleHideResponses();" id="hide_responses_button"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                        onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
        <script type="text/javascript">
            require(["dojo/parser","dijit/form/Textarea"],function(dojoParser){
                dojoParser.parse(getObject('centerbody'));
            });
        </script>
    </div>
    <br class="clear"/>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
