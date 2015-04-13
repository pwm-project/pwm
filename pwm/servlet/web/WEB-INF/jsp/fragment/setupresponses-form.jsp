<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.http.bean.SetupResponsesBean" %>
<%@ page import="password.pwm.util.JsonUtil" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
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

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final SetupResponsesBean.SetupData setupData = (SetupResponsesBean.SetupData)request.getAttribute("setupData");
%>
<%-------------------------------- display fields for REQUIRED challenges ----------------------------------------------%>
<% if (!setupData.getChallengeSet().getRequiredChallenges().isEmpty()) { %>
<p><pwm:display key="Display_SetupRequiredResponses"/></p>
<%
    for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
        final Challenge challenge = setupData.getIndexedChallenges().get(indexKey);
        if (challenge.isRequired()) {
%>
<% if (challenge.isAdminDefined()) { %>
<h2>
    <label for="PwmResponse_R_<%=indexKey%>"><%= StringUtil.escapeHtml(challenge.getChallengeText()) %></label>
</h2>
<% } else { %>
<label for="PwmResponse_Q_<%=indexKey%>"><pwm:display key="Field_User_Supplied_Question"/>:</label>&nbsp;
<textarea name="PwmResponse_Q_<%=indexKey%>" id="PwmResponse_Q_<%=indexKey%>" style="width: 70%"
          <pwm:autofocus/> class="inputfield"></textarea>
<% } %>
<p>
    <span class="fa fa-chevron-circle-right"></span>
    <input type="<pwm:value name="responseFieldType"/>" name="PwmResponse_R_<%=indexKey%>" class="inputfield passwordfield" maxlength="255"
           <pwm:autofocus/> id="PwmResponse_R_<%=indexKey%>" required="required"/>
</p>
<% } %>
<% } %>
<% } %>
<%---------------------- display fields for RANDOM challenges using SIMPLE mode ----------------------------------------%>
<% if (setupData.isSimpleMode()) {  %>
<% // need to output all the random options for the javascript functions.
    final List<String> questionTexts = new ArrayList<String>();
    for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
        final Challenge challenge = setupData.getIndexedChallenges().get(indexKey);
        if (!challenge.isRequired()) {
            questionTexts.add(challenge.getChallengeText());
        }
    }
%>
<p><pwm:display key="Display_SetupRandomResponses" value1="<%= String.valueOf(setupData.getChallengeSet().getMinRandomRequired()) %>"/></p>
<% for (int index = 0; index < setupData.getMinRandomSetup(); index++) { %>
<h2>
    <select name="PwmResponse_Q_Random_<%=index%>" id="PwmResponse_Q_Random_<%=index%>" style="width:70%" <pwm:autofocus/> class="simpleModeResponseSelection"
            data-response-id="PwmResponse_R_Random_<%=index%>">
        <option value="UNSELECTED" data-unselected-option="true">&nbsp;&mdash;&nbsp;<pwm:display key="Display_SelectionIndicator"/>&nbsp;&mdash;&nbsp;</option>
        <% for (final String questionText : questionTexts) {
        %>
        <option value="<%=StringUtil.escapeHtml(questionText)%>"><%=StringUtil.escapeHtml(questionText)%>
        </option>
        <% } %>
    </select>
</h2>
<p>
    <span class="fa fa-chevron-circle-right"></span>
    <input type="<pwm:value name="responseFieldType"/>" name="PwmResponse_R_Random_<%=index%>" class="inputfield passwordfield" maxlength="255" type="text"
            <pwm:autofocus/> id="PwmResponse_R_Random_<%=index%>" required="required"/>
</p>
<% } %>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_VAR['simpleRandomOptions'] = <%=JsonUtil.serializeCollection(questionTexts)%>;
    });
</script>
</pwm:script>
<% } else { %>
<%---------------------- display fields for RANDOM challenges using non-SIMPLE mode ----------------------------------------%>
<% if (!setupData.getChallengeSet().getRandomChallenges().isEmpty()) { %>
<p><pwm:display key="Display_SetupRandomResponses" value1="<%= String.valueOf(setupData.getChallengeSet().getMinRandomRequired()) %>"/></p>
<%
    for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
        final Challenge challenge = setupData.getIndexedChallenges().get(indexKey);
        if (!challenge.isRequired()) {
%>
<% if (challenge.isAdminDefined()) { %>
<h2>
    <label for="PwmResponse_R_<%=indexKey%>"><%= StringUtil.escapeHtml(challenge.getChallengeText()) %></label>
</h2>
<% } else { %>
<label for="PwmResponse_Q_<%=indexKey%>"><pwm:display key="Field_User_Supplied_Question"/>:</label>&nbsp;
<textarea name="PwmResponse_Q_<%=indexKey%>" id="PwmResponse_Q_<%=indexKey%>" style="width: 70%" <pwm:autofocus/>
          class="inputfield"></textarea>
<% } %>
<p>
    <span class="fa fa-chevron-circle-right"></span>
    <input type="<pwm:value name="responseFieldType"/>" name="PwmResponse_R_<%=indexKey%>" class="inputfield passwordfield" maxlength="255" id="PwmResponse_R_<%=indexKey%>"/>
</p>
<% } %>
<% } %>
<% } %>
<% } %>
