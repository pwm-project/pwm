<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.error.ErrorInformation" %>
<%@ page import="password.pwm.error.PwmError" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%
    final ErrorInformation startupError = request.getAttribute(PwmConstants.REQUEST_ATTR.PwmErrorInfo.toString()) == null
            ? new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE)
            : (ErrorInformation)request.getAttribute(PwmConstants.REQUEST_ATTR.PwmErrorInfo.toString());
%>
<html>
<head>
    <title><%=PwmConstants.PWM_APP_NAME%></title>
    <meta http-equiv="content-type" content="text/html;charset=utf-8"/>
    <meta http-equiv="X-UA-Compatible" content="IE=10; IE=9; IE=8; IE=7" />
    <meta http-equiv="refresh" content="60">
    <link href="<%=request.getContextPath()%>/public/resources/style.css" rel="stylesheet" type="text/css" media="screen"/>
</head>
<body class="nihilo" data-jsp-page="application-unavailable.jsp">
<div id="wrapper">
    <div id="centerbody">
        <br/>
        <h1><%=PwmConstants.PWM_APP_NAME%></h1>
        <br/>
        <br/>
        <h2><%=startupError.toDebugStr()%></h2>
        <br/>
        <br/>
        <p><%=startupError.toUserStr(request.getLocale(), null)%></p>
        <br/>
        <br/>
        <br/>
        <a href="#">Refresh</a>
        <% if (startupError.getError() == PwmError.ERROR_ENVIRONMENT_ERROR) { %>
        <br/><br/><br/>
        <a href="<%=request.getContextPath()%>/public/reference/environment.jsp">Environment Configuration Reference</a>
        <% } %>
    </div>
    <div class="push"></div>
</div>
</body>
</html>
