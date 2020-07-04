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


<%@ page import="password.pwm.Permission" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<pwm:if test="<%=PwmIfTest.showHeaderMenu%>">
    <pwm:script-ref url="/public/resources/js/configmanager.js"/>
    <pwm:script-ref url="/public/resources/js/admin.js"/>
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_CONFIG.initConfigHeader();
            });
        </script>
    </pwm:script>
    <div id="header-warning" class="nodisplay">
        <div id="header-warning-message" class="header-warning-row header-warning-message">
            <pwm:if test="<%=PwmIfTest.trialMode%>">
                <pwm:display key="Header_TrialMode" bundle="Admin"/>
            </pwm:if>
            <pwm:if test="<%=PwmIfTest.trialMode%>" negate="true">
                <pwm:if test="<%=PwmIfTest.configurationOpen%>">
                    <pwm:display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
                    <span id="icon-configModeHelp" class="btn-icon pwm-icon pwm-icon-question-circle"></span>
                </pwm:if>
                <pwm:if test="<%=PwmIfTest.configurationOpen%>" negate="true">
                    <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
                        <pwm:display key="Header_AdminUser" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
                    </pwm:if>
                </pwm:if>
            </pwm:if>
        </div>
        <div class="header-warning-row header-warning-buttons">
            <a class="header-warning-button" id="header_configManagerButton" href="<pwm:url addContext="true" url="<%=PwmServletDefinition.ConfigManager.servletUrl()%>"/>">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-gears"></span></pwm:if>
                <pwm:display key="MenuItem_ConfigManager" bundle="Admin"/>
            </a>
            <a class="header-warning-button" id="header_configEditorButton" href="<pwm:url addContext="true" url="<%=PwmServletDefinition.ConfigEditor.servletUrl()%>"/>">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-edit"></span></pwm:if>
                <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
            </a>
            <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
                <a class="header-warning-button" id="header_administrationButton" href="<pwm:url url="/private/admin"/>">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-list-alt"></span></pwm:if>
                    <pwm:display key="Title_Admin"/>
                </a>
                <pwm:if test="<%=PwmIfTest.forcedPageView%>" negate="true">
                    <a class="header-warning-button" id="header_openLogViewerButton" href="<pwm:url url="/private/admin/logs"/>">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-list-alt"></span></pwm:if>
                        <pwm:display key="MenuItem_ViewLog" bundle="Config"/>
                    </a>
                </pwm:if>
            </pwm:if>
        </div>
        <div id="panel-header-healthData" class="header-warning-row header-warning-healthDat display-none">
            <div id="panel-healthHeaderErrors" class="header-error">
                <span class="pwm-icon pwm-icon-warning"></span><pwm:display key="Header_HealthWarningsPresent" bundle="Admin"/>
            </div>
        </div>
        <div class="header-warning-row header-warning-version"><%=PwmConstants.PWM_APP_NAME_VERSION%></div>
        <div id="button-closeHeader" title="<pwm:display key="Button_Hide"/>"></div>
    </div>
</pwm:if>
