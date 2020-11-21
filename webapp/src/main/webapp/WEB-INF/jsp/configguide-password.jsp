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


<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <br/>
        <div id="password" class="setting_outline">
            <div class="setting_title">
                <pwm:display key="password_title" bundle="ConfigGuide"/>
            </div>
            <div class="setting_body">
                <pwm:display key="password_description" bundle="ConfigGuide"/>
                <br/><br/>
                <form id="configForm">
                    <input type="hidden" id="<%=ConfigGuideFormField.PARAM_CONFIG_PASSWORD%>" name="<%=ConfigGuideFormField.PARAM_CONFIG_PASSWORD%>"/>
                </form>
                <div style="text-align: center"><button class="btn" id="button-setPassword">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-key"></span></pwm:if>
                    Set Password
                </button></div>
            </div>
        </div>
        <br/>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function handleFormActivity() {
            PWM_GUIDE.updateForm();
            checkIfNextEnabled();
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('configForm','input',function(){handleFormActivity()});

            PWM_MAIN.addEventHandler('button-setPassword','click',function(){
                var writeFunction = function(password) {
                    var hiddenInput = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_CONFIG_PASSWORD%>');
                    hiddenInput.value = password;
                    PWM_GUIDE.updateForm();
                    checkIfNextEnabled();
                };

                UILibrary.passwordDialogPopup({writeFunction:writeFunction})
            });

            checkIfNextEnabled();
        });

        function checkIfNextEnabled() {
            var password = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_CONFIG_PASSWORD%>').value;

            <% final String existingPwd = configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_CONFIG_PASSWORD); %>
            <% if (existingPwd == null || existingPwd.isEmpty()) { %>
            PWM_MAIN.getObject('button_next').disabled = true;
            if (password.length > 0) {
                PWM_MAIN.getObject('button_next').disabled = false;
            }
            <% } %>
        }
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
