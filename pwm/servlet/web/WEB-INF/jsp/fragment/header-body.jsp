<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmSession" %>
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

<%--
  ~ This file is imported by most JSPs, it shows the main 'header' in the html
  - which by default is a blue-gray gradieted and rounded block.
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<% if (ContextManager.getPwmApplication(session).getApplicationMode() == PwmApplication.MODE.CONFIGURATION) { %>
<% if (!request.getRequestURI().contains("configmanager")) { %>
<div id="header-warning">PWM is in configuration mode. Use the <a href="<%=request.getContextPath()%><pwm:url url='/config/ConfigManager'/>">ConfigManager</a>
    to modify or lock the configuration.
</div>
<% } %>
<% } %>
<div id="header">
    <div id="header-company-logo"></div>
    <%-- this section handles the logout link (if user is logged in) --%><% if (PwmSession.getPwmSession(session).getSessionStateBean().isAuthenticated()) { %>
    <div style="align:right; float:right; border-width:0; padding-top: 21px; padding-right:18px" id="logoutDiv">
        <a id="LogoutButton" href="<%=request.getContextPath()%><pwm:url url='/public/Logout'/>"
           title="<pwm:Display key="Button_Logout"/>">
        </a>
    </div>
    <script type="text/javascript">
        require(["dijit/Tooltip"],function(){
            new dijit.Tooltip({
                connectId: ["logoutDiv"],
                label: '<pwm:Display key="Long_Title_Logout"/>'
            });
        });
    </script>
<%-- this extra div is required to "balance" the header in IE, since css float alignment is broken in IE --%>
    <div style="align:left; float:left; border-width:0; padding-top: 21px; padding-left:18px">
        <img src="<%=request.getContextPath()%>/resources/spacer.gif" alt="" border="0"
             style="width:26px; height:26px"/>
    </div>
    <% } %>
    <div id="header-page">
        <pwm:Display key="${param['pwm.PageName']}" displayIfMissing="true"/>
    </div>
    <div id="header-title">
        <pwm:Display key="Title_Application"/>
    </div>
</div>

