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


<%@ page import="com.novell.ldapchai.cr.bean.ChallengeBean" %>
<%@ page import="com.novell.ldapchai.cr.bean.ChallengeSetBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ChallengeSetBean challengeSet = (ChallengeSetBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ForgottenPasswordChallengeSet); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<%--
in the body onload below, the true parameter toggles the hide button an extra time to default the page to hiding the responses.
this is handled this way so on browsers where hiding fields is not possible, the default is to show the fields.
--%>
<body>
<div id="wrapper">
<jsp:include page="fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_RecoverPassword"/>
</jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_RecoverPassword" displayIfMissing="true"/></h1>
        <p><pwm:display key="Display_RecoverPassword"/></p>

        <form name="responseForm" action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form" autocomplete="off">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

            <% // loop through challenges
                int counter = 0;
                for (final ChallengeBean loopChallenge : challengeSet.getChallenges()) {
                    counter++;
            %>
            <h2><label for="PwmResponse_R_<%=counter%>"><%= loopChallenge.getChallengeText() %>
            </label></h2>
            <input type="<pwm:value name="<%=PwmValue.responseFieldType%>"/>" name="PwmResponse_R_<%= counter %>" class="inputfield passwordfield" maxlength="255"
                   <pwm:autofocus/> id="PwmResponse_R_<%=counter%>" required="required"/>
            <% } %>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="checkResponses"/>
                <button type="submit" name="checkResponses" class="btn" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                                                   <pwm:display key="Button_RecoverPassword"/>
                </button>
                <% if ("true".equals(JspUtility.getAttribute(pageContext, PwmRequestAttribute.ForgottenPasswordOptionalPageView))) { %>
                <button type="button" id="button-goBack" name="button-goBack" class="btn" >
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <% } %>
                <%@ include file="/WEB-INF/jsp/fragment/forgottenpassword-cancel.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script>
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button-goBack','click',function() {
                PWM_MAIN.submitPostAction('<%=PwmServletDefinition.ForgottenPassword.servletUrlName()%>', '<%=ForgottenPasswordServlet.ForgottenPasswordAction.verificationChoice%>');
            });
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/responses.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
