<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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

<%@ page import="password.pwm.AppProperty" %>
<%@ page import="password.pwm.Permission" %>
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.http.PwmURL" %>
<%
    boolean includeHeader = false;
    boolean adminUser = false;
    boolean configMode = false;
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        final PwmApplication.MODE applicationMode = pwmRequest.getPwmApplication().getApplicationMode();
        configMode = applicationMode == PwmApplication.MODE.CONFIGURATION;
        adminUser = pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN);
        if (Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.CLIENT_WARNING_HEADER_SHOW))) {
            if (!new PwmURL(request).isConfigManagerURL()) {
                if (configMode || PwmConstants.TRIAL_MODE) {
                    includeHeader = true;
                } else if (pwmRequest.isAuthenticated()) {
                    if (adminUser && !pwmRequest.isForcedPageView()) {
                        includeHeader = true;
                    }
                }
            }
        }
    } catch (Exception e) {
        /* noop */
    }
%>
<% if (includeHeader) { %>
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
    <div class="header-warning-buttons">
        <a class="header-warning-button" id="header_configManagerButton">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-gears"></span></pwm:if>
            <pwm:display key="MenuItem_ConfigManager" bundle="Admin"/>
        </a>
        <a class="header-warning-button" id="header_configEditorButton">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-edit"></span></pwm:if>
            <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
        </a>
        <% if (adminUser) { %>
        <a class="header-warning-button" id="header_openLogViewerButton">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-list-alt"></span></pwm:if>
            <pwm:display key="MenuItem_ViewLog" bundle="Config"/>
            &nbsp;
            <pwm:if test="showIcons"><span class="btn-icon fa fa-external-link"></span></pwm:if>
        </a>
        <a class="header-admin-button" href="<pwm:url url="/private/admin"/>">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-list-alt"></span></pwm:if>
            <pwm:display key="Title_Admin"/>
        </a>
        <% } %>
    </div>
    <span id="header-warning-message" style="padding-right: 15px; font-weight: bold">
    <% if (PwmConstants.TRIAL_MODE) { %>
    <pwm:display key="Header_TrialMode" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
    <% } else if (configMode) { %>
    <pwm:display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>

    <pwm:if test="showIcons"><span id="icon-configModeHelp" class="btn-icon fa fa-question-circle"></span></pwm:if>
        <br/><br/>
    <% } else if (adminUser) { %>
    <pwm:display key="Header_AdminUser" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
    <% } %>
    </span>
    <div id="panel-header-healthData" style="cursor: pointer"></div>
    <% if (includeHeader) { %>
    <div id="button-closeHeader">
        <span class="fa fa-chevron-circle-right"></span>
    </div>
    <% } %>
    <br/>
    <%=PwmConstants.PWM_APP_NAME_VERSION%>
</div>
<% if (includeHeader) { %>
<div id="button-openHeader">
    <span class="fa fa-chevron-circle-left"></span>
</div>
<% } %>
<% } %>
