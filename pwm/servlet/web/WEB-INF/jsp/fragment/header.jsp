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
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ page import="password.pwm.http.ContextManager" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmSession pwmSessionHeader = null;
    PwmApplication pwmApplicationHeader = null;
    try {
        pwmApplicationHeader = ContextManager.getPwmApplication(session);
        pwmSessionHeader = PwmSession.getPwmSession(session);
    } catch (PwmUnrecoverableException e) {
        /* application must be unavailable */
    }

    // read parameters from calling jsp;
    final boolean showTheme = !"true".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_HIDE_THEME));
    final boolean includeXVersion = Boolean.parseBoolean(pwmApplicationHeader.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XVERSION));
    final boolean incrementRequestCounter = !"true".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_NO_REQ_COUNTER));

    if (pwmSessionHeader != null) {
        pwmSessionHeader.getSessionStateBean().clearScriptContents();
    }

    if (incrementRequestCounter && pwmSessionHeader != null) {
        pwmSessionHeader.getSessionManager().incrementRequestCounterKey();
    }

    final String jspFileName = this.getClass().getSimpleName().replaceAll("_", ".");
%>
<head>
    <title><pwm:display key="Title_TitleBar"/></title>
    <meta http-equiv="content-type" content="text/html;charset=utf-8"/>
    <meta id="application-info" name="application-name" content="<%=PwmConstants.PWM_APP_NAME%> Password Self Service" <%if (includeXVersion){%>data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-version="<%=PwmConstants.BUILD_VERSION%> (<%=PwmConstants.BUILD_TYPE%>)" data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-build="<%=PwmConstants.BUILD_NUMBER%>" <%}%>data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-instance="<%=pwmApplicationHeader != null ? pwmApplicationHeader.getInstanceID() : ""%>" data-jsp-name="<%=jspFileName%>"
          data-url-context="<%=request.getContextPath()%>" data-pwmFormID="<pwm:FormID/>" data-clientEtag="<%=password.pwm.ws.server.rest.RestAppDataServer.makeClientEtag(request,pwmApplicationHeader,pwmSessionHeader)%>" data-restClientKey="<%=pwmSessionHeader.getRestClientKey()%>"/>
    <meta name="viewport" content="width=device-width, initial-scale = 1.0, user-scalable=no"/>
    <meta http-equiv="X-UA-Compatible" content="IE=10; IE=9; IE=8; IE=7" />
    <link rel="icon" type="image/x-icon" href="<pwm:url url='/public/resources/favicon.ico' addContext="true"/>"/>
    <link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/font/font-awesome.css' addContext="true"/>"/>
    <link href="<pwm:url url='/public/resources/style.css' addContext="true"/>" rel="stylesheet" type="text/css" media="screen"/>
    <link media="only screen and (max-width: 600px)" href="<pwm:url url='/public/resources/mobileStyle.css' addContext="true"/>" type="text/css" rel="stylesheet"/><%-- iphone css --%>
    <% if (showTheme) { %>
    <link href="<pwm:url url="%THEME_URL%"/>" rel="stylesheet" type="text/css" media="screen"/>
    <link media="only screen and (max-width: 600px)" href="<pwm:url url="%MOBILE_THEME_URL%"/>" type="text/css" rel="stylesheet"/><%-- mobile css --%>
    <% } %>
    <link href="<pwm:url url='/public/resources/dojo/dijit/themes/nihilo/nihilo.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
    <pwm:script>
        <script nonce="<pwm:value name="cspNonce"/>" type="text/javascript">
            var PWM_GLOBAL = PWM_GLOBAL || {}; PWM_GLOBAL['startupFunctions'] = [];
        </script>
    </pwm:script>
</head>
