<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ page import="password.pwm.http.bean.SetupResponsesBean" %>
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

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final SetupResponsesBean.SetupData setupData = (SetupResponsesBean.SetupData)request.getAttribute("setupData");
    final SessionStateBean ssBean = PwmSession.getPwmSession(session).getSessionStateBean();
    final String responseFieldType = "password";
%>
<%-------------------------------- display fields for REQUIRED challenges ----------------------------------------------%>
<% if (!setupData.getChallengeSet().getRequiredChallenges().isEmpty()) { %>
<p><pwm:Display key="Display_SetupRequiredResponses"/></p>
<%
    for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
        final Challenge challenge = setupData.getIndexedChallenges().get(indexKey);
        if (challenge.isRequired()) {
%>
<% if (challenge.isAdminDefined()) { %>
<h2>
    <label for="PwmResponse_R_<%=indexKey%>"><%= StringEscapeUtils.escapeHtml(challenge.getChallengeText()) %></label>
</h2>
<% } else { %>
<label for="PwmResponse_Q_<%=indexKey%>"><pwm:Display key="Field_User_Supplied_Question"/>:</label>&nbsp;
<textarea name="PwmResponse_Q_<%=indexKey%>" id="PwmResponse_Q_<%=indexKey%>" data-dojo-type="dijit/form/Textarea" style="width: 70%"
          class="inputfield" onkeyup="PWM_RESPONSES.validateResponses();"><%= StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().get("PwmResponse_Q_" + indexKey, ""))%></textarea>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parse","dijit/form/Select"],function(parser){
            parser.parse(PWM_MAIN.getObject('PwmResponse_Q_<%=indexKey%>'));
        });
    });
</script>
</pwm:script>
<% } %>
<p>
    <span class="fa fa-chevron-circle-right"></span>
    <input type="<%=responseFieldType%>" name="PwmResponse_R_<%=indexKey%>" class="inputfield" maxlength="255"
           id="PwmResponse_R_<%=indexKey%>" required="required"
           onkeyup="PWM_RESPONSES.validateResponses();"/>
</p>
<% } %>
<% } %>
<% } %>
<%---------------------- display fields for RANDOM challenges using SIMPLE mode ----------------------------------------%>
<% if (setupData.isSimpleMode()) {  %>
<p><pwm:Display key="Display_SetupRandomResponses" value1="<%= String.valueOf(setupData.getChallengeSet().getMinRandomRequired()) %>"/></p>
<% for (int index = 0; index < setupData.getMinRandomSetup(); index++) { %>
<h2>
    <select name="PwmResponse_Q_Random_<%=index%>" id="PwmResponse_Q_Random_<%=index%>" data-dojo-type="dijit/form/Select" style="width:70%">
        <option value="UNSELECTED">&nbsp;&nbsp;--- <pwm:Display key="Display_SelectionIndicator"/> ---</option>
        <%
            for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
                final Challenge challenge = setupData.getIndexedChallenges().get(indexKey);
                if (!challenge.isRequired()) {
                    final boolean selected = challenge.getChallengeText().equals(ssBean.getLastParameterValues().get("PwmResponse_Q_Random_" + index, ""));
        %>
        <option <%=selected ? "selected=\"selected\"" : ""%>
                value="<%=StringEscapeUtils.escapeHtml(challenge.getChallengeText())%>"><%=StringEscapeUtils.escapeHtml(challenge.getChallengeText())%>
        </option>
        <% } %>
        <% } %>
    </select>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_VAR['simpleRandomSelectElements']['PwmResponse_Q_Random_<%=index%>'] = 'PwmResponse_R_Random_<%=index%>'
            require(["dijit/registry","dojo/parser","dijit/form/Select"],function(registry,parser){
                parser.parse();
            });
        });
    </script>
    </pwm:script>
</h2>
<p>
    <span class="fa fa-chevron-circle-right"></span>
    <input type="<%=responseFieldType%>" name="PwmResponse_R_Random_<%=index%>" class="inputfield" maxlength="255" type="text"
           id="PwmResponse_R_Random_<%=index%>" required="required"
           onkeyup="PWM_RESPONSES.validateResponses()"/>
</p>
<% } %>
<pwm:script>
<script type="text/javascript">
    <% // need to output all the random options for the javascript functions.
        for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
        final Challenge challenge = setupData.getIndexedChallenges().get(indexKey);
        if (!challenge.isRequired()) {
    %>
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_VAR['simpleRandomOptions'].push('<%=StringEscapeUtils.escapeJavaScript(challenge.getChallengeText())%>')
    });
    <% } %>
    <% } %>
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/form/Select"],function(parser){
                parser.parse();
                PWM_RESPONSES.makeSelectOptionsDistinct();
            });
        });
    });
</script>
</pwm:script>
<% } else { %>
<%---------------------- display fields for RANDOM challenges using non-SIMPLE mode ----------------------------------------%>
<% if (!setupData.getChallengeSet().getRandomChallenges().isEmpty()) { %>
<p><pwm:Display key="Display_SetupRandomResponses" value1="<%= String.valueOf(setupData.getChallengeSet().getMinRandomRequired()) %>"/></p>
<%
    for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
        final Challenge challenge = setupData.getIndexedChallenges().get(indexKey);
        if (!challenge.isRequired()) {
%>
<% if (challenge.isAdminDefined()) { %>
<h2>
    <label for="PwmReseesponse_R_<%=indexKey%>"><%= StringEscapeUtils.escapeHtml(challenge.getChallengeText()) %></label>
</h2>
<% } else { %>
<label for="PwmResponse_Q_<%=indexKey%>"><pwm:Display key="Field_User_Supplied_Question"/>:</label>&nbsp;
<textarea name="PwmResponse_Q_<%=indexKey%>" id="PwmResponse_Q_<%=indexKey%>" data-dojo-type="dijit.form.Textarea" style="width: 70%"
          class="inputfield" onkeyup="PWM_RESPONSES.validateResponses();"><%= StringEscapeUtils.escapeHtml(ssBean.getLastParameterValues().get("PwmResponse_Q_" + indexKey, ""))%></textarea>
<% } %>
<p>
    <span class="fa fa-chevron-circle-right"></span>
    <input type="<%=responseFieldType%>" name="PwmResponse_R_<%=indexKey%>" class="inputfield" maxlength="255" id="PwmResponse_R_<%=indexKey%>"
           onkeyup="PWM_RESPONSES.validateResponses();"/>
</p>
<% } %>
<% } %>
<% } %>
<% } %>
