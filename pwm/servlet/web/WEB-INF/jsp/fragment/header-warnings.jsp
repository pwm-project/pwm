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
<% final boolean showConfigHeader = !request.getRequestURI().contains("configmanager") && (pwmApplicationHeaderBody != null && pwmApplicationHeaderBody.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) || PwmConstants.TRIAL_MODE; %>
<% if (showConfigHeader) { %>
<div id="header-warning">
    <% final String configManagerUrl = request.getContextPath() + "/private/config/ConfigManager"; %>
    <% if (PwmConstants.TRIAL_MODE) { %>
    <pwm:Display key="Header_ConfigModeTrial" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>" value2="<%=configManagerUrl%>"/>
    <% } else { %>
    <pwm:Display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>" value2="<%=configManagerUrl%>"/>
    <% } %>
    <% for (HealthRecord healthRecord : pwmApplicationHeaderBody.getHealthMonitor().getHealthRecords()) { %>
    <% if (healthRecord.getStatus() == HealthStatus.WARN) { %>
    <div class="header-error">
        <a href="<%=configManagerUrl%>">
            <%=healthRecord.getDetail(pwmSessionHeaderBody.getSessionStateBean().getLocale(),pwmApplicationHeaderBody.getConfig())%>
        </a>
    </div>
    <% } %>
    <% } %>
    <a href="#" id="header_hide_button" style="font-size: 75%" onclick="fadeOutHeader()"><pwm:Display key="Button_Hide"/></a>
</div>
<script type="text/javascript">
    function fadeOutHeader() {
        require(["dojo/json","dojo/fx"], function(json,fx){
            fx.wipeOut({node:getObject("header-warning")}).play();
        });
    }
</script>
<% } %>
