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


<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ page import="password.pwm.http.ContextManager" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.PwmRequestFlag" %>
<%@ page import="password.pwm.http.tag.url.PwmThemeURL" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ taglib uri="pwm" prefix="pwm" %>

<pwm:if test="<%=PwmIfTest.authenticated%>" negate="true"><title><pwm:display key="Title_TitleBar"/></title></pwm:if>
<pwm:if test="<%=PwmIfTest.authenticated%>"><title><pwm:display key="Title_TitleBarAuthenticated"/></title></pwm:if>
<meta http-equiv="content-type" content="text/html;charset=utf-8"/>
<meta name="robots" content="noindex,nofollow"/>
<meta id="application-info" name="application-name" content="<%=PwmConstants.PWM_APP_NAME%> Password Self Service"
      <pwm:if test="<%=PwmIfTest.showVersionHeader%>">data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-version="<%=PwmConstants.BUILD_VERSION%>" data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-build="<%=PwmConstants.BUILD_NUMBER%>"</pwm:if>
      data-<%=PwmConstants.PWM_APP_NAME.toLowerCase()%>-instance="<pwm:value name="<%=PwmValue.instanceID%>"/>"
      data-session-id="<pwm:value name="<%=PwmValue.sessionID%>"/>"
      data-jsp-name="<pwm:value name="<%=PwmValue.currentJspFilename%>"/>"
      data-url-context="<pwm:context/>"
      data-pwmFormID="<pwm:FormID/>"
      data-clientEtag="<pwm:value name="<%=PwmValue.clientETag%>"/>">
<meta name="viewport" content="width=device-width, initial-scale = 1.0, user-scalable=no"/>
<meta http-equiv="X-UA-Compatible" content="IE=10; IE=9; IE=8; IE=7" />
<link rel="icon" type="image/png" href="<pwm:url url='/public/resources/favicon.png' addContext="true"/>"/>
<link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/pwm-icons.css' addContext="true"/>"/>
<link href="<pwm:url url='/public/resources/style.css' addContext="true"/>" rel="stylesheet" type="text/css" media="screen"/>
<link href="<pwm:url url='/public/resources/style-print.css' addContext="true"/>" rel="stylesheet" type="text/css" media="print"/>
<link href="<pwm:url url="%THEME_URL%"/>" rel="stylesheet" type="text/css" media="screen"/>
<pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.NO_MOBILE_CSS%>" negate="true">
    <link media="only screen and (max-width: 600px)" href="<pwm:url url='/public/resources/mobileStyle.css' addContext="true"/>" type="text/css" rel="stylesheet"/><%-- iphone css --%>
    <link media="only screen and (max-width: 600px)" href="<pwm:url url="%MOBILE_THEME_URL%"/>" type="text/css" rel="stylesheet"/><%-- mobile css --%>
</pwm:if>
<pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.INCLUDE_CONFIG_CSS%>">
    <link href="<pwm:url url='<%=PwmThemeURL.CONFIG_THEME_URL.token()%>' addContext="true"/>" rel="stylesheet" type="text/css" media="screen"/>
</pwm:if>
<pwm:script>
    <script type="text/javascript">
        var PWM_GLOBAL = PWM_GLOBAL || {}; PWM_GLOBAL['startupFunctions'] = [];
    </script>
</pwm:script>
