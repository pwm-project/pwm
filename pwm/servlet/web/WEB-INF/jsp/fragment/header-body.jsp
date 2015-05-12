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

<%--
  ~ This file is imported by most JSPs, it shows the main 'header' in the html
  - which by default is a blue-gray gradieted and rounded block.
  --%>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    boolean showWarnings = false;
    boolean showLogout = false;
    boolean showHome = false;
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        showWarnings = !pwmRequest.isFlag(PwmRequest.Flag.HIDE_HEADER_WARNINGS);
        final boolean showButtons = !pwmRequest.isFlag(PwmRequest.Flag.HIDE_HEADER_BUTTONS) && !pwmRequest.isForcedPageView();
        final boolean loggedIn = pwmRequest.isAuthenticated();
        if (showButtons && loggedIn) {
            showHome = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_HOME_BUTTON);
        }
        if (loggedIn) {
            showLogout = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_LOGOUT_BUTTON);
        }
    } catch (PwmUnrecoverableException e) {
        /* application must be unavailable */
    }

%>
<% if (showWarnings) { %><%@ include file="header-warnings.jsp" %><% } %>
<div id="header">
    <div id="header-company-logo">
    </div>
    <div id="header-balance-div">
        <br/><%-- balance div for ie 6 --%>
    </div>
    <div id="header-right-logo" style="position: absolute">
    </div>
    <div id="header-center">
        <div id="header-center-left">
            <div id="header-page"><pwm:display key="${param['pwm.PageName']}" displayIfMissing="true"/></div>
            <div id="header-title"><pwm:display key="Title_Application"/></div>
        </div>
        <div id="header-center-right">
            <%-- this section handles the home button link (if user is logged in) --%>
            <a class="header-button" href="<pwm:value name="homeURL"/>" style="visibility: <%=showHome ? "inline" : "hidden"%>" id="HomeButton">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-home"></span></pwm:if>
                <pwm:display key="Button_Home"/>
            </a>
            <%-- this section handles the logout link (if user is logged in) --%>
            <a class="header-button" href="<pwm:context/><pwm:url url='/public/Logout'/>" style="visibility: <%=showLogout ? "inline" : "hidden"%>" id="LogoutButton">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-out"></span></pwm:if>
                <pwm:display key="Button_Logout"/>
            </a>
        </div>
    </div>
</div>
