<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.error.ErrorInformation" %>
<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%
    final ErrorInformation startupError = request.getAttribute(PwmRequestAttribute.PwmErrorInfo.toString()) == null
            ? new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE)
            : (ErrorInformation)request.getAttribute(PwmRequestAttribute.PwmErrorInfo.toString());
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
