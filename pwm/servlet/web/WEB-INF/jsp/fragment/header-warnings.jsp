<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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

<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.health.HealthRecord" %>
<%@ page import="password.pwm.health.HealthStatus" %>
<%@ page import="java.util.Set" %>
<%@ page import="password.pwm.Permission" %>
<%@ page import="password.pwm.util.PwmServletURLHelper" %>
<% if (!PwmServletURLHelper.isConfigManagerURL(request)) { %>
<% final boolean showHeader = pwmApplicationHeaderBody.getApplicationMode() == PwmApplication.MODE.CONFIGURATION || PwmConstants.TRIAL_MODE; %>
<% final boolean healthCheck = pwmApplicationHeaderBody.getApplicationMode() != PwmApplication.MODE.RUNNING || Permission.checkPermission(Permission.PWMADMIN,pwmSessionHeaderBody,pwmApplicationHeaderBody); %>
<% if (showHeader || healthCheck) { %>
<div id="header-warning" style="width: 100%; <%=showHeader?"":"display: none"%>">
    <% final String configManagerUrl = request.getContextPath() + "/private/config/ConfigManager"; %>
    <% if (PwmConstants.TRIAL_MODE) { %>
    <pwm:Display key="Header_ConfigModeTrial" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>" value2="<%=configManagerUrl%>"/>
    <% } else if (pwmApplicationHeaderBody.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) { %>
    <pwm:Display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>" value2="<%=configManagerUrl%>"/>
    <% } %>
    &nbsp;&nbsp;<a onclick="openLogViewer()" style="font-size: smaller; float:right; cursor: pointer; padding-right: 5px; position: absolute;">View Log</a>
    <script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
    <div id="headerHealthData" onclick="window.location = '<%=configManagerUrl%>'" style="cursor: pointer">
    </div>
</div>
<% if (healthCheck) { %>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        showHeaderHealth();
    });
</script>
<% } %>
<% } %>
<% } %>
