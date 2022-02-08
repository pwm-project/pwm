<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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
<%@ page import="password.pwm.http.servlet.setupresponses.SetupResponsesServlet" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.http.servlet.setupresponses.SetupResponsesUtil" %>
<%@ page import="password.pwm.http.servlet.setupresponses.ResponseMode" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupResponsesBean responseBean = (SetupResponsesBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ModuleBean); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ConfirmResponses"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ConfirmResponses" displayIfMissing="true"/></h1>
        <%@ include file="fragment/message.jsp" %>
        <% if ( SetupResponsesUtil.hasChallenges( JspUtility.getPwmRequest( pageContext ), ResponseMode.user ) ) { %>
        <br/>
        <p><pwm:display key="Display_ConfirmResponses"/></p>
        <%
            final Map<Challenge, String> responseMap = responseBean.getChallengeData().get( ResponseMode.user ).getResponseMap();
            for (final Map.Entry<Challenge, String> entry : responseMap.entrySet() ) {
        %>
        <h2><%= StringUtil.escapeHtml(entry.getKey().getChallengeText()) %></h2>
        <p>
            <span class="pwm-icon pwm-icon-chevron-circle-right"></span>
            <%= StringUtil.escapeHtml(entry.getValue()) %>
        </p>
        <% } %>
        <% } %>
        <% if ( SetupResponsesUtil.hasChallenges( JspUtility.getPwmRequest( pageContext ), ResponseMode.helpdesk ) ) { %>
        <br/>
        <p><pwm:display key="Display_ConfirmHelpdeskResponses"/></p>
        <%
            final Map<Challenge, String> responseMap = responseBean.getChallengeData().get( ResponseMode.helpdesk ).getResponseMap();
            for (final Map.Entry<Challenge, String> entry : responseMap.entrySet() ) {
        %>
        <h2><%= StringUtil.escapeHtml(entry.getKey().getChallengeText()) %></h2>
        <p>
            <span class="pwm-icon pwm-icon-chevron-circle-right"></span>
            <%= StringUtil.escapeHtml(entry.getValue()) %>
        </p>
        <% } %>
        <% } %>
        <br/>
        <div class="buttonbar">
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="changeResponses"
                  enctype="application/x-www-form-urlencoded" class="pwm-form">
                <button type="submit" name="confirm_btn" class="btn" id="confirm_btn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_ConfirmResponses"/>
                </button>
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=SetupResponsesServlet.SetupResponsesAction.confirmResponses%>"/>
                <input type="hidden" name="<%=PwmConstants.PARAM_FORM_ID%>" value="<pwm:FormID/>"/>
            </form>
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="confirmResponses"
                  enctype="application/x-www-form-urlencoded" class="pwm-form">
                <button type="submit" name="change_btn" class="btn" id="change_btn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=SetupResponsesServlet.SetupResponsesAction.changeResponses%>"/>
                <input type="hidden" name="<%=PwmConstants.PARAM_FORM_ID%>" value="<pwm:FormID/>"/>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
