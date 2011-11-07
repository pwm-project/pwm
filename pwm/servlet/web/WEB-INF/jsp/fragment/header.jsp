<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2011 The PWM Project
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
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSessionHeader = PwmSession.getPwmSession(session); %>
<% final PwmApplication pwmApplicationHeader = ContextManager.getPwmApplication(session); %>
<head>
    <title><pwm:Display key="Title_Application"/>
        <%
            final String userField = pwmSessionHeader.getUserInfoBean().getUserID();
            if (userField != null && userField.length() > 0) {
                out.write(" - ");
                out.write(userField);
            }
        %>
    </title>
    <meta http-equiv="content-type" content="text/html;charset=utf-8"/>
    <meta name="Description" content="PWM Password Self Service"/>
    <meta name="X-Pwm-Instance"
          content="<%=pwmApplicationHeader.getInstanceID()%>>"/>
    <meta name="X-Pwm-Version"
          content="<%=PwmConstants.PWM_VERSION%> (<%=PwmConstants.BUILD_TYPE%>)"/>
    <meta name="X-Pwm-Build"
          content="<%=PwmConstants.BUILD_NUMBER%>"/>
    <meta name="viewport" content="width=320, user-scalable=no"/>
    <link rel="icon" type="image/x-icon"
          href="<%=request.getContextPath()%>/resources/<pwm:url url='favicon.ico'/>"/>
    <link href="<%=request.getContextPath()%>/resources/<pwm:url url='pwmStyle.css'/>"
          rel="stylesheet" type="text/css" media="screen"/>
    <%
      final String theme = pwmApplicationHeader.getConfig().readSettingAsString(password.pwm.config.PwmSetting.INTERFACE_THEME);
      if (theme != null && !request.getRequestURI().contains("WEB-INF/jsp/configmanager-editor.jsp")) {
    %>
    <link href="<%=request.getContextPath()%>/resources/themes/<%=theme%>/<pwm:url url='pwmStyle.css'/>"
          rel="stylesheet" type="text/css" media="screen"/>
    <%
      }
    %>
    <link media="only screen and (max-device-width: 480px)" <%-- iphone css --%>
          href="<%=request.getContextPath()%>/resources/<pwm:url url='pwmMobileStyle.css'/>" type="text/css"
          rel="stylesheet"/>
    <%
      if (theme != null) {
    %>
    <link media="only screen and (max-device-width: 480px)" <%-- iphone css --%>
          href="<%=request.getContextPath()%>/resources/themes/<%=theme%>/<pwm:url url='pwmMobileStyle.css'/>" type="text/css"
          rel="stylesheet"/>
    <%
      }
    %>
    <link href="<%=request.getContextPath()%>/resources/dojo/dijit/themes/tundra/tundra.css" rel="stylesheet"
          type="text/css"/>
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dojo/dojo.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dijit/dijit.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='pwmHelper.js'/>"></script>
    <% if (pwmApplicationHeader.getConfig() != null) { %>
    <% final String googleTrackingCode = pwmApplicationHeader.getConfig().readSettingAsString(password.pwm.config.PwmSetting.GOOGLE_ANAYLTICS_TRACKER); %>
    <% if (googleTrackingCode != null && googleTrackingCode.length() > 0) { %>
    <script type="text/javascript">
        var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl." : "http://www.");
        document.write(unescape("%3Cscript src='" + gaJsHost + "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
    </script>
    <script type="text/javascript">
        try {
            var pageTracker = _gat._getTracker("<%=googleTrackingCode%>");
            pageTracker._trackPageview();
        } catch(err) {
        }
    </script>
    <% } %>
    <% } %>
    <script type="text/javascript">
        <% pwmSessionHeader.getSessionStateBean().incrementRequestCounter();%>
        PWM_GLOBAL['pwmFormID'] = '<pwm:FormID/>';
        <% if (pwmApplicationHeader.getConfig() != null) { %>
        PWM_GLOBAL['setting-showHidePasswordFields'] =<%=pwmApplicationHeader.getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_SHOW_HIDE_PASSWORD_FIELDS)%>;
        <% } %>
        PWM_GLOBAL['url-logout'] = "<%=request.getContextPath()%>/public/<pwm:url url='Logout?idle=true'/>";
        PWM_GLOBAL['url-command'] = "<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>";
        PWM_GLOBAL['url-resources'] = "<%=request.getContextPath()%>/resources";
        PWM_GLOBAL['url-rest-private'] = "<%=request.getContextPath()%>/private/rest";
        PWM_GLOBAL['url-rest-public'] = "<%=request.getContextPath()%>/public/rest";
    </script>
</head>
