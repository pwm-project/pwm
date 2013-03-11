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

<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="password.pwm.util.PwmServletURLHelper" %>
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
%>
<head>
    <title><pwm:Display key="Title_TitleBar"/></title>
    <meta http-equiv="content-type" content="text/html;charset=utf-8"/>
    <meta name="application-name" content="PWM Password Self Service" data-pwm-version="<%=PwmConstants.PWM_VERSION%> (<%=PwmConstants.BUILD_TYPE%>)" data-pwm-build="<%=PwmConstants.BUILD_NUMBER%>" data-pwm-instance="<%=pwmApplicationHeader != null ? pwmApplicationHeader.getInstanceID() : ""%>"/>
    <meta name="viewport" content="width=device-width, initial-scale = 1.0, user-scalable=no"/>
    <meta http-equiv="X-UA-Compatible" content="IE=9; IE=8; IE=7" />
    <link rel="icon" type="image/x-icon" href="<%=request.getContextPath()%><pwm:url url='/public/resources/favicon.ico'/>"/>
    <link href="<%=request.getContextPath()%><pwm:url url='/public/resources/pwmStyle.css'/>" rel="stylesheet" type="text/css" media="screen"/>
    <link media="only screen and (max-device-width: 480px)" href="<%=request.getContextPath()%><pwm:url url='/public/resources/pwmMobileStyle.css'/>" type="text/css" rel="stylesheet"/><%-- iphone css --%>
    <% if (!PwmServletURLHelper.isConfigManagerURL(request)) { %>
    <link href="<pwm:ThemeURL/>" rel="stylesheet" type="text/css" media="screen"/>
    <link media="only screen and (max-device-width: 480px)" href="<pwm:ThemeURL type="mobile"/>" type="text/css" rel="stylesheet"/><%-- iphone css --%>
    <% } %>
    <link href="<%=request.getContextPath()%><pwm:url url='/public/resources/dojo/dijit/themes/nihilo/nihilo.css'/>" rel="stylesheet" type="text/css"/>
    <script type="text/javascript">var PWM_GLOBAL = PWM_GLOBAL || {};var PWM_STRINGS = PWM_STRINGS || {};PWM_GLOBAL['startupFunctions'] = new Array();</script>
    <script defer data-dojo-config="async: true" type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/dojo/dojo/dojo.js'/>"></script>
    <script defer type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/pwmHelper.js'/>"></script>
    <script defer type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/jsClientValues.jsp'/>?nonce=<%=Helper.makePwmVariableJsNonce(pwmApplicationHeader, pwmSessionHeader)%>"></script>
    <% final String googleTrackingCode = ContextManager.getPwmApplication(session).getConfig().readSettingAsString(password.pwm.config.PwmSetting.GOOGLE_ANAYLTICS_TRACKER); %>
    <% if (googleTrackingCode != null && googleTrackingCode.length() > 0) { %>
    <script type="text/javascript">
        var _gaq = _gaq || [];
        _gaq.push(['_setAccount', '<%=googleTrackingCode%>']);
        _gaq.push(['_trackPageview']);
        (function() {
            var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
            var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
        })();
    </script>
    <% } %>
</head>
