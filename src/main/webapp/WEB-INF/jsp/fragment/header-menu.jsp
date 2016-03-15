<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
  ~
  ~ This program is free software; you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation; either version 2 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
    <div id="header-warning" style="display: none">
        <div id="header-warning-message" class="header-warning-row header-warning-message">
            <pwm:if test="<%=PwmIfTest.trialMode%>">
                <pwm:display key="Header_TrialMode" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
            </pwm:if>
            <pwm:if test="<%=PwmIfTest.trialMode%>" negate="true">
                <pwm:if test="<%=PwmIfTest.configMode%>">
                    <pwm:display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>

                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span id="icon-configModeHelp" class="btn-icon pwm-icon pwm-icon-question-circle"></span></pwm:if>
                </pwm:if>
                <pwm:if test="<%=PwmIfTest.configMode%>" negate="true">
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
            <a class="header-warning-button" id="header_configEditorButton">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-edit"></span></pwm:if>
                <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
            </a>
            <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
                <a class="header-warning-button" id="header_administrationButton" href="<pwm:url url="/private/admin"/>">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-list-alt"></span></pwm:if>
                    <pwm:display key="Title_Admin"/>
                </a>
                <pwm:if test="<%=PwmIfTest.forcedPageView%>" negate="true">
                    <a class="header-warning-button" id="header_openLogViewerButton">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-list-alt"></span></pwm:if>
                        <pwm:display key="MenuItem_ViewLog" bundle="Config"/>
                        &nbsp;
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-external-link"></span></pwm:if>
                    </a>
                </pwm:if>
            </pwm:if>
        </div>
        <div id="panel-header-healthData" class="header-warning-row header-warning-healthData"></div>
        <div class="header-warning-row header-warning-version"><%=PwmConstants.PWM_APP_NAME_VERSION%></div>
        <div id="button-closeHeader" title="<pwm:display key="Button_Hide"/>"></div>
    </div>
    <div id="button-openHeader" title="<pwm:display key="Button_Show"/>">
        <span class="pwm-icon pwm-icon-chevron-circle-left"></span>
    </div>
</pwm:if>