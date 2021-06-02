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


<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ForgottenPassword" displayIfMissing="true"/></h1>
        <%@ include file="fragment/message.jsp" %>
        <% final String expandedText = (String)JspUtility.getAttribute( pageContext, PwmRequestAttribute.AgreementText ); %>
        <br/><br/>
        <div id="agreementText" class="agreementText"><%= expandedText %></div>
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form" style="display: inline;">
                <%-- remove the next line to remove the "I Agree" checkbox --%>
                <label class="checkboxWrapper">
                    <input type="checkbox" id="agreeCheckBox"/>
                    <pwm:display key="Button_Agree"/>
                </label>
                <br/><br/>
                <input type="hidden" name="processAction" value="agree"/>
                <button type="submit" name="button" class="btn" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" style="display: inline;">
                <input type="hidden" name="processAction" value="reset"/>
                <button type="submit" name="button" class="btn" id="button_reset">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_Cancel"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function updateContinueButton() {
            var checkBox = PWM_MAIN.getObject("agreeCheckBox");
            var continueButton = PWM_MAIN.getObject("submitBtn");
            if (checkBox != null && continueButton != null) {
                if (checkBox.checked) {
                    continueButton.removeAttribute('disabled');
                } else {
                    continueButton.disabled = "disabled";
                }
            }
        }
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('agreeCheckBox','click, change',function(){ updateContinueButton() });
            updateContinueButton();
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
