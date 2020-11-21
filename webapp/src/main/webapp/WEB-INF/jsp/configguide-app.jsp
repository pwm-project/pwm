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
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <div id="outline_ldap" class="setting_outline">
                <div class="setting_title">
                    Site URL
                </div>
                <div class="setting_body">
                    <%=PwmSetting.PWM_SITE_URL.getDescription(JspUtility.locale(request))%>
                    <br/><br/>
                    Example: <code><%=PwmSetting.PWM_SITE_URL.getExample(ConfigGuideForm.generateStoredConfig(configGuideBean).getTemplateSet())%></code>
                    <br/><br/>
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_APP_SITEURL%>" style="padding-left: 5px; padding-top: 5px">
                            <label>
                                <b>Site URL</b>
                                <br/>
                                <input class="configStringInput" id="<%=ConfigGuideFormField.PARAM_APP_SITEURL%>" name="<%=ConfigGuideFormField.PARAM_APP_SITEURL%>"
                                       value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_APP_SITEURL))%>" required autofocus
                                       pattern="<%=PwmSetting.PWM_SITE_URL.getRegExPattern()%>"/>
                            </label>
                        </div>
                    </div>
                </div>
            </div>
        </form>
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

            checkIfNextEnabled();

            populateSiteUrl();
        });

        function populateSiteUrl() {
            var siteUrlInput = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_APP_SITEURL%>');
            if (siteUrlInput.value.length > 0) {
                return;
            }

            var suggestedSiteUrl = window.location.protocol + '//' + window.location.host + PWM_GLOBAL['url-context'];
            siteUrlInput.value = suggestedSiteUrl;

            handleFormActivity();
            checkIfNextEnabled();
        }

        function checkIfNextEnabled() {
            var siteUrlInput = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_APP_SITEURL%>');
            var passed = siteUrlInput.value.length > 1 && new RegExp(siteUrlInput.getAttribute('pattern')).test(siteUrlInput.value);
            if (passed) {
                PWM_MAIN.getObject('button_next').disabled = false;
            } else {
                PWM_MAIN.getObject('button_next').disabled = true;
            }
        }
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
