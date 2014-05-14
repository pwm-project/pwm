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
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.util.PwmServletURLHelper" %>
<%
    boolean headerEnabled = Boolean.parseBoolean(pwmApplicationHeaderBody.getConfig().readAppProperty(AppProperty.CLIENT_WARNING_HEADER_SHOW))
            && !PwmServletURLHelper.isConfigManagerURL(request);
%>
<% if (headerEnabled) { %>
<%
        final boolean adminUser = Permission.checkPermission(Permission.PWMADMIN,pwmSessionHeaderBody,pwmApplicationHeaderBody);
        final boolean showHeader = pwmApplicationHeaderBody.getApplicationMode() == PwmApplication.MODE.CONFIGURATION || PwmConstants.TRIAL_MODE;
        final boolean healthCheck = pwmApplicationHeaderBody.getApplicationMode() != PwmApplication.MODE.RUNNING || adminUser;
%>
<% if (showHeader || healthCheck) { %>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<div id="header-warning" style="width: 100%; <%=showHeader?"":"display: none"%>">
    <% if (PwmConstants.TRIAL_MODE) { %>
    <pwm:Display key="Header_TrialMode" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
    <% } else if (pwmApplicationHeaderBody.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) { %>
    <span id="header-warning-message">
    <pwm:Display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
    </span>
    <script type="application/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.showTooltip({
                id: ['header-warning-message'],
                position: ['below','above'],
                text: '<pwm:Display key="HealthMessage_Config_ConfigMode" bundle="Health"/>',
                width: 500
            });
        });
    </script>
    <% } else if (adminUser) { %>
    <pwm:Display key="Header_AdminUser" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
    <% } %>
    &nbsp;&nbsp;
    <span onclick="PWM_MAIN.goto('/private/config/ConfigManager')" style="cursor:pointer; white-space: nowrap">
        <span class="fa fa-gears"></span>&nbsp;
        <a>
            <pwm:Display key="MenuItem_ConfigManager" bundle="Admin"/>
        </a>
    </span>
    &nbsp;&nbsp;
    <span onclick="PWM_CONFIG.startConfigurationEditor()" style="cursor:pointer; white-space: nowrap">
        <span class="fa fa-edit"></span>&nbsp;
        <a>
            <pwm:Display key="MenuItem_ConfigEditor" bundle="Admin"/>
        </a>
    </span>
    &nbsp;&nbsp;
    <span onclick="PWM_CONFIG.openLogViewer(null)" style="cursor:pointer; white-space: nowrap">
        <span class="fa fa-list-alt"></span>&nbsp;
        <a>
            <pwm:Display key="MenuItem_ViewLog" bundle="Config"/>
        </a>
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
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_CONFIG.showHeaderHealth();
    });
</script>
<% } %>
<% } %>
<% } %>
