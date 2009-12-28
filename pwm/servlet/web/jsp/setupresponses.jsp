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
<%@ page import="com.novell.ldapchai.cr.ChallengeSet" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.SetupResponsesBean" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final SessionStateBean ssBean = PwmSession.getSessionStateBean(request.getSession());
    final ChallengeSet challengeSet = PwmSession.getPwmSession(session).getUserInfoBean().getChallengeSet();
    final List<Challenge> allChallenges = challengeSet.getChallenges();
    final SetupResponsesBean responseBean = PwmSession.getPwmSession(session).getSetupResponseBean();
    int tabIndexer = 0;
%>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<body onload="startupPage(false); document.forms.setupResponses.elements[0].focus();" onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="Title_SetupResponses"/></jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_SetupResponses"/></p>
        <form action="<pwm:url url='SetupResponses'/>" method="post" name="setupResponses" onkeypress="checkForCapsLock(event);"
              enctype="application/x-www-form-urlencoded" onreset="handleFormClear();" autocomplete="off">
            <%  // if there is an error, then always show the error block if javascript is enabled.  Otherwise, only show
                // the error block if javascript is available (for ajax use).
                if (PwmSession.getSessionStateBean(session).getSessionError() != null) {
            %>
            <span id="error_msg" class="msg-error"><pwm:ErrorMessage/>&nbsp;</span>
            <% } else { %>
            <script type="text/javascript">
                document.write('<span id="error_msg" class="msg-success">&nbsp;</span>');
            </script>
            <% } %>
            <br/>
            <% // display fields for REQUIRED clallenges.
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
            <h2><%= challenge.getChallengeText() %></h2>
            <% } else { %>
            <pwm:Display key="Field_User_Supplied_Question"/>:&nbsp;
            <input type="text" name="PwmResponse_Q_<%=indexKey%>" class="inputfield"
                   value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_Q_" + indexKey, "")%>"
                   tabindex="<%=++tabIndexer%>" onkeyup="validateResponses();"/>
            <% } %>
            <p>
                &nbsp;»&nbsp;
                <input type="text" name="PwmResponse_R_<%=indexKey%>" class="inputfield" maxlength="255"
                       value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_R_" + indexKey,"") %>"
                       tabindex="<%=++tabIndexer%>" onkeyup="validateResponses();"/>
            </p>
            <% } %>
            <% } %>
            <% } %>
            <% // display fields for RANDOM challenges using SIMPLE mode
                if (responseBean.isSimpleMode()) {
                for (int i = 0; i < challengeSet.getMinRandomRequired(); i++) {
            %>
            <h2>
                <select name="PwmResponse_Q_Random_<%=i%>" onchange="validateResponses();" tabindex="<%=++tabIndexer%>">
                    <option value=""><pwm:Display key="Field_Option_Select"/></option>
                    <option value="">───────────────</option>
                    <%
                        for (final String indexKey : responseBean.getIndexedChallenges().keySet()) {
                            final Challenge challenge = responseBean.getIndexedChallenges().get(indexKey);
                            if (!challenge.isRequired()) {
                                boolean selected = indexKey.equals(ssBean.getLastParameterValues().getProperty("PwmResponse_Q_Random_" + i,""));
                    %>
                    <option <%=selected ? "selected=\"yes\"" : ""%> value="<%=indexKey%>"><%=challenge.getChallengeText()%></option>
                    <% } %>
                    <% } %>
                </select>
            </h2>
            <p>
                &nbsp;»&nbsp;
                <input type="text" name="PwmResponse_R_Random_<%=i%>" class="inputfield" maxlength="255"
                       value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_R_Random_" + i,"") %>"
                       tabindex="<%=++tabIndexer%>" onkeyup="validateResponses();"/>
            </p>
            <% } %>
            <% } else { %>
            <% // display fields for RANDOM clallenges.
                if (!challengeSet.getRandomChallenges().isEmpty()) {
            %>
            <hr/>
            <p><pwm:Display key="Display_SetupRandomResponses" value1="<%= String.valueOf(challengeSet.getMinRandomRequired()) %>"/></p>
            <%
                for (final String indexKey : responseBean.getIndexedChallenges().keySet()) {
                    final Challenge challenge = responseBean.getIndexedChallenges().get(indexKey);
                    if (!challenge.isRequired()) {
            %>
                <% if (challenge.isAdminDefined()) { %>
                <h2><%= challenge.getChallengeText() %></h2>
                <% } else { %>
                <pwm:Display key="Field_User_Supplied_Question"/>&nbsp;
                <input type="text" name="PwmResponse_Q_<%=indexKey%>" class="inputfield"
                       value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_Q_" + indexKey, "")%>"
                       tabindex="<%=++tabIndexer%>" onkeyup="validateResponses();"/>
                <% } %>
            <p>
                &nbsp;»&nbsp;
                <input type="text" name="PwmResponse_R_<%=indexKey%>" class="inputfield" maxlength="255"
                       value="<%= ssBean.getLastParameterValues().getProperty("PwmResponse_R_" + indexKey,"") %>"
                       tabindex="<%=++tabIndexer%>" onkeyup="validateResponses();"/>
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
                <input tabindex="<%=++tabIndexer%>" type="submit" name="setResponses" class="btn" id="setresponses_button"
                       value="    <pwm:Display key="Button_SetResponses"/>    "/>
                <input tabindex="<%=++tabIndexer%>" type="reset" name="reset" class="btn"
                       value="    <pwm:Display key="Button_Reset"/>    "/>
                <input type="hidden" name="hideButton" class="btn"
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
    <input type="hidden" name="Js_Display_CheckingResponses" id="Js_Display_CheckingResponses" value="<pwm:Display key="Display_CheckingResponses"/>"/>
    <input type="hidden" name="Js_Display_CommunicationError" id="Js_Display_CommunicationError" value="<pwm:Display key="Display_CommunicationError"/>"/>
    <input type="hidden" name="Js_SetupResponsesURL" id="Js_SetupResponsesURL" value="<pwm:url url='SetupResponses'/>"/>
</form>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='json2.js'/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='responses.js'/>"></script>
<%@ include file="footer.jsp" %>
</body>
</html>
