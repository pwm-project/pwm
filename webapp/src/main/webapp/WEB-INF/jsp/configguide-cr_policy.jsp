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


<%@ page import="password.pwm.config.value.FileValue" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<% final Locale userLocale = JspUtility.locale(request); %>
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
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    <pwm:display key="cr_policy_title" bundle="ConfigGuide"/>

                </div>
                <div class="setting_body">
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_DB_CLASSNAME%>" style="padding-left: 5px; padding-top: 5px">
                            <pwm:display key="cr_policy_description" bundle="ConfigGuide"/>
                            <br/>
                            <div id="table_setting_<%=PwmSetting.CHALLENGE_RANDOM_CHALLENGES.getKey()%>">
                            </div>

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
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            initPage();
        });

        function initPage() {
            PWM_CFGEDIT.initConfigSettingsDefinition(function(){
                PWM_VAR['outstandingOperations'] = 0;
                ChallengeSettingHandler.init('<%=PwmSetting.CHALLENGE_RANDOM_CHALLENGES.getKey()%>');
            });
        }
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configeditor.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings.js"/>
<pwm:script-ref url="/public/resources/js/configeditor-settings-challenges.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
