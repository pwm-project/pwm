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
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<% final String selectedTemplate = configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_TEMPLATE_LDAP); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <pwm:display key="template_description" bundle="ConfigGuide"/>
        <br/>
        <form id="configForm">
            <select id="<%=ConfigGuideFormField.PARAM_TEMPLATE_LDAP%>" name="<%=ConfigGuideFormField.PARAM_TEMPLATE_LDAP%>" style="width:300px">
                <% if (selectedTemplate == null || selectedTemplate.isEmpty()) { %>
                <option value="NOTSELECTED" selected disabled>-- Please select a template --</option>
                <% } %>
                <% for (final String loopTemplate : PwmSetting.TEMPLATE_LDAP.getOptions().keySet()) { %>
                <% final boolean selected = loopTemplate.equals(selectedTemplate); %>
                <option value="<%=loopTemplate%>"<% if (selected) { %> selected="selected"<% } %>>
                    <%=PwmSetting.TEMPLATE_LDAP.getOptions().get(loopTemplate)%>
                </option>
                <% } %>
            </select>
        </form>
        <br/>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function formHandler() {
            PWM_GUIDE.updateForm();
            updateNextButton();
        }

        function getSelectedValue() {
            var selectedIndex = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TEMPLATE_LDAP%>').selectedIndex;
            var newTemplate = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TEMPLATE_LDAP%>').options[selectedIndex];
            return newTemplate.value;
        }

        function updateNextButton() {
            var newTemplate = getSelectedValue();
            var notSelected = newTemplate == 'NOTSELECTED';
            PWM_MAIN.getObject('button_next').disabled = notSelected;
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_previous','click',function(){
                PWM_MAIN.showConfirmDialog({
                    text:'Proceeding will cause existing guide settings to be cleared.  Are you sure you wish to continue?',
                    okAction:function(){
                        PWM_GUIDE.gotoStep('PREVIOUS');
                    }
                });
            });
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('<%=ConfigGuideFormField.PARAM_TEMPLATE_LDAP%>','change',function(){formHandler()});
            updateNextButton();
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
