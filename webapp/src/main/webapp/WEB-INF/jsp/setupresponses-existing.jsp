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
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<% final ResponseInfoBean responseInfoBean = (ResponseInfoBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.SetupResponses_ResponseInfo); %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ConfirmResponses"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ConfirmResponses" displayIfMissing="true"/></h1>
        <p>
            <% if (responseInfoBean != null && responseInfoBean.getTimestamp() != null) { %>
            <pwm:display key="Display_WarnExistingResponseTime" value1="@ResponseSetupTime@"/>
            <% } else { %>
            <pwm:display key="Display_WarnExistingResponse"/>
            <% } %>
        </p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <h2><pwm:display key="Title_AnsweredQuestions"/></h2>
        <% for (final Challenge loopChallenge : responseInfoBean.getCrMap().keySet()) { %>
                <p><%= StringUtil.escapeHtml(loopChallenge.getChallengeText()) %></p>
        <% } %>
        <br/>
        <div class="buttonbar">
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="clearExistingForm" id="clearExistingForm"
                  enctype="application/x-www-form-urlencoded" class="pwmForm">
                <button type="submit" name="confirm_btn" class="btn" id="confirm_btn" value="">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                    <pwm:display key="Button_ClearResponses"/>
                </button>
                <input type="hidden" name="processAction" value="clearExisting"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('clearExistingForm','submit',function(event){
                PWM_MAIN.cancelEvent(event);
                PWM_MAIN.showConfirmDialog({
                    text: PWM_MAIN.showString("Display_ResponsesClearWarning"),
                    okAction:function(){
                        PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('clearExistingForm'));
                    }
                });
            });
        });
    </script>
</pwm:script>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
