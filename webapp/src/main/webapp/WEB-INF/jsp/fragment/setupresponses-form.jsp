<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.http.bean.SetupResponsesBean" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.util.java.JsonUtil" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
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
    <span class="pwm-icon pwm-icon-chevron-circle-right"></span>
    <input type="<pwm:value name="<%=PwmValue.responseFieldType%>"/>" name="PwmResponse_R_<%=indexKey%>" class="inputfield passwordfield response" maxlength="255"
           <pwm:autofocus/> id="PwmResponse_R_<%=indexKey%>" required="required"/>
</p>
<% } %>
<% } %>
<% } %>
<%---------------------- display fields for pwmRandom challenges using SIMPLE mode ----------------------------------------%>
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
    <span class="pwm-icon pwm-icon-chevron-circle-right"></span>
    <input type="<pwm:value name="<%=PwmValue.responseFieldType%>"/>" name="PwmResponse_R_Random_<%=index%>" class="inputfield passwordfield response" maxlength="255" type="text"
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
<%---------------------- display fields for pwmRandom challenges using non-SIMPLE mode ----------------------------------------%>
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
    <span class="pwm-icon pwm-icon-chevron-circle-right"></span>
    <input type="<pwm:value name="<%=PwmValue.responseFieldType%>"/>" name="PwmResponse_R_<%=indexKey%>" class="inputfield passwordfield response" maxlength="255" id="PwmResponse_R_<%=indexKey%>"/>
</p>
<% } %>
<% } %>
<% } %>
<% } %>
