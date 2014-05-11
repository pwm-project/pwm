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

<%--
  ~ This file is imported by most JSPs, it shows the main 'header' in the html
  - which by default is a blue-gray gradieted and rounded block.
  --%>
<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmSession pwmSessionHeaderBody = null;
    PwmApplication pwmApplicationHeaderBody = null;
    try {
        pwmApplicationHeaderBody = ContextManager.getPwmApplication(session);
        pwmSessionHeaderBody = PwmSession.getPwmSession(session);
    } catch (PwmUnrecoverableException e) {
        /* application must be unavailable */
    }

    final boolean hideButtons = "true".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_HIDE_HEADER_BUTTONS));
    final boolean loggedIn = pwmSessionHeaderBody != null && pwmSessionHeaderBody.getSessionStateBean().isAuthenticated();
    final boolean showLogout = !hideButtons && loggedIn && pwmApplicationHeaderBody != null && pwmApplicationHeaderBody.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_LOGOUT_BUTTON);
    final boolean showHome = !hideButtons && loggedIn && pwmApplicationHeaderBody != null && pwmApplicationHeaderBody.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_HOME_BUTTON);
%>
<%@ include file="header-warnings.jsp" %>
<div id="header">
    <div id="header-company-logo">
    </div>
    <div style="position: absolute; text-align:left; border-width:0; top: 19px; left:18px;">
        <br/><%-- balance div for ie 6 --%>
    </div>
    <div id="header-right-logo" style="position: absolute">
    </div>
    <div id="header-center">
        <div id="header-center-left">
            <div id="header-page"><pwm:Display key="${param['pwm.PageName']}" displayIfMissing="true"/></div>
            <div id="header-title"><pwm:Display key="Title_Application"/></div>
        </div>
        <div id="header-center-right">
            <%-- this section handles the home button link (if user is logged in) --%>
            <a class="header-button" href="<%=request.getContextPath()%><pwm:url url='/'/>" style="visibility: <%=showHome ? "inline" : "hidden"%>" id="HomeButton">
                <span class="fa fa-home"></span>&nbsp;
                <pwm:Display key="Button_Home"/>
            </a>
            <%-- this section handles the logout link (if user is logged in) --%>
            <a class="header-button" href="<%=request.getContextPath()%><pwm:url url='/public/Logout'/>" style="visibility: <%=showLogout ? "inline" : "hidden"%>" id="LogoutButton">
                <span class="fa fa-sign-out"></span>&nbsp;
                <pwm:Display key="Button_Logout"/>
            </a>
        </div>
    </div>
</div>
<% if (showLogout) { %>
<script type="application/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.showTooltip({
            id: 'LogoutButton',
            position: ['below','above'],
            text: '<pwm:Display key="Long_Title_Logout"/>',
            width: 500
        });
    });
</script>
<% } %>
