<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
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

<%@ taglib uri="pwm" prefix="pwm" %>
<div style="text-align: center">
  <form action="<pwm:context/><%=PwmServletDefinition.ConfigManager.servletUrl()%>" method="get">
    <button type="submit" class="navbutton">
      <pwm:if test="showIcons"><span class="btn-icon fa fa-dashboard"></span></pwm:if>
      Config Manager
    </button>
  </form>
  <form action="<pwm:context/><%=PwmServletDefinition.ConfigManager_Wordlists.servletUrl()%>" method="get">
    <button type="submit" class="navbutton">
      <pwm:if test="showIcons"><span class="btn-icon fa fa-book"></span></pwm:if>
      Wordlists
    </button>
  </form>
  <button type="submit" id="MenuItem_ConfigEditor" title="<pwm:display key="MenuDisplay_ConfigEditor" bundle="Config"/>">
    <pwm:if test="showIcons"><span class="btn-icon fa fa-book"></span></pwm:if>
    <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
  </button>
  <pwm:script>
    <script type="application/javascript">
      PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.addEventHandler('MenuItem_ConfigEditor','click',function(){PWM_CONFIG.startConfigurationEditor()});
      });
    </script>
  </pwm:script>
</div>
<br/>


