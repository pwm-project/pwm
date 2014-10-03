<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
    boolean headerEnabled = false;
    boolean adminUser = false;
    boolean showHeader = false;
    boolean healthCheck = false;
    boolean configMode = false;
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request,response);
        headerEnabled = Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.CLIENT_WARNING_HEADER_SHOW))
                && !new PwmURL(request).isConfigManagerURL();
        if (headerEnabled) {
            final PwmApplication.MODE mode = pwmRequest.getPwmApplication().getApplicationMode();
            adminUser = pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN);
            configMode = mode == PwmApplication.MODE.CONFIGURATION;
            showHeader = configMode || PwmConstants.TRIAL_MODE;
            healthCheck = mode != PwmApplication.MODE.RUNNING || adminUser;
        }
    } catch (Exception e) {
        /* noop */
    }
%>
<% if (headerEnabled && (showHeader || healthCheck)) { %>
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<div id="header-warning" style="<%=showHeader?"":"display: none"%>">
    <span onclick="PWM_MAIN.goto('/private/config/ConfigManager')" style="cursor:pointer; white-space: nowrap">
        <a class="btn">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-gears"></span></pwm:if>
            <pwm:display key="MenuItem_ConfigManager" bundle="Admin"/>
        </a>
    </span>
    &nbsp;&nbsp;
    <span onclick="PWM_CONFIG.startConfigurationEditor()" style="cursor:pointer; white-space: nowrap">
        <a class="btn">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-edit"></span></pwm:if>
            <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
        </a>
    </span>
    &nbsp;&nbsp;
    <span onclick="PWM_CONFIG.openLogViewer(null)" style="cursor:pointer; white-space: nowrap">
        <a class="btn">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-list-alt"></span></pwm:if>
            <pwm:display key="MenuItem_ViewLog" bundle="Config"/>
        </a>
    </span>
    <br/>
    <br/>
    <span id="header-warning-message" style="padding-right: 15px; font-weight: bold">
    <% if (PwmConstants.TRIAL_MODE) { %>
    <pwm:display key="Header_TrialMode" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
    <% } else if (configMode) { %>
    <pwm:display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
<pwm:script>
    <script nonce="<pwm:value name="cspNonce"/>" type="application/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.showTooltip({
                id: ['header-warning-message'],
                position: ['below','above'],
                text: '<pwm:display key="HealthMessage_Config_ConfigMode" bundle="Health"/>',
                width: 500
            });
        });
    </script>
</pwm:script>
    <% } else if (adminUser) { %>
    <pwm:display key="Header_AdminUser" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
    <% } %>
    </span>
    <div id="headerHealthData" onclick="PWM_MAIN.goto('/private/config/ConfigManager')" style="cursor: pointer">
    </div>
    <div style="position: absolute; top: 3px; right: 3px;">
        <span onclick="PWM_MAIN.getObject('header-warning').style.display = 'none';" style="cursor: pointer">
            <span class="fa fa-caret-up"></span>&nbsp;
        </span>
    </div>
</div>
<% if (healthCheck) { %>
<pwm:script>
    <script nonce="<pwm:value name="cspNonce"/>" type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_CONFIG.showHeaderHealth();
        });
    </script>
</pwm:script>
<% } %>
<% } %>
