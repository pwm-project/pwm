<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.PwmRequestFlag" %>
<%@ page import="password.pwm.http.tag.url.PwmThemeURL" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<head>
    <pwm:if test="<%=PwmIfTest.authenticated%>" negate="true"><title><pwm:display key="Title_TitleBar"/></title></pwm:if>
    <pwm:if test="<%=PwmIfTest.authenticated%>"><title><pwm:display key="Title_TitleBarAuthenticated"/></title></pwm:if>
    <meta http-equiv="content-type" content="text/html;charset=utf-8"/>
    <meta name="robots" content="noindex,nofollow"/>
    <meta id="application-info" name="application-name" content="<%=PwmConstants.PWM_APP_NAME%> Password Self Service"
          <pwm:if test="<%=PwmIfTest.showVersionHeader%>">data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-version="<%=PwmConstants.BUILD_VERSION%> (<%=PwmConstants.BUILD_TYPE%>)" data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-build="<%=PwmConstants.BUILD_NUMBER%>"</pwm:if>
          data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-instance="<pwm:value name="<%=PwmValue.instanceID%>"/>"
          data-jsp-name="<pwm:value name="<%=PwmValue.currentJspFilename%>"/>"
          data-url-context="<pwm:context/>"
          data-pwmFormID="<pwm:FormID/>"
          data-clientEtag="<pwm:value name="<%=PwmValue.clientETag%>"/>"
          data-restClientKey="<pwm:value name="<%=PwmValue.restClientKey%>"/>">
    <meta name="viewport" content="width=device-width, initial-scale = 1.0, user-scalable=no"/>
    <meta http-equiv="X-UA-Compatible" content="IE=10; IE=9; IE=8; IE=7" />
    <link rel="icon" type="image/x-icon" href="<pwm:url url='/public/resources/favicon.ico' addContext="true"/>"/>
    <link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/pwm-icons.css' addContext="true"/>"/>
    <link href="<pwm:url url='/public/resources/style.css' addContext="true"/>" rel="stylesheet" type="text/css" media="screen"/>
    <link href="<pwm:url url="%THEME_URL%"/>" rel="stylesheet" type="text/css" media="screen"/>
    <pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.NO_MOBILE_CSS%>" negate="true">
        <link media="only screen and (max-width: 600px)" href="<pwm:url url='/public/resources/mobileStyle.css' addContext="true"/>" type="text/css" rel="stylesheet"/><%-- iphone css --%>
        <link media="only screen and (max-width: 600px)" href="<pwm:url url="%MOBILE_THEME_URL%"/>" type="text/css" rel="stylesheet"/><%-- mobile css --%>
    </pwm:if>
    <link href="<pwm:url url='/public/resources/dojo/dijit/themes/nihilo/nihilo.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
    <link href="<pwm:url url='/public/resources/dojo/dgrid/css/dgrid.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
    <pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.INCLUDE_CONFIG_CSS%>">
        <link href="<pwm:url url='<%=PwmThemeURL.CONFIG_THEME_URL.token()%>' addContext="true"/>" rel="stylesheet" type="text/css" media="screen"/>
    </pwm:if>
    <pwm:script>
        <script type="text/javascript">
            var PWM_GLOBAL = PWM_GLOBAL || {}; PWM_GLOBAL['startupFunctions'] = [];
        </script>
    </pwm:script>
</head>
