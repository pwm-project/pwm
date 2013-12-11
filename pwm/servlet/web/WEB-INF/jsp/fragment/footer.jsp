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
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.util.MacroMachine" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmSession pwmSessionFooter = null;
    PwmApplication pwmApplicationFooter = null;
    try {
        pwmApplicationFooter = ContextManager.getPwmApplication(session);
        pwmSessionFooter = PwmSession.getPwmSession(session);
    } catch (PwmUnrecoverableException e) {
        /* application must be unavailable */
    }
%>
<% final Locale userLocaleFooter = pwmSessionFooter.getSessionStateBean().getLocale(); %>
<% boolean segmentDisplayed = false; %>
<%-- begin pwm footer --%>
<div id="footer">
    <div id="footer-content">
        <span class="infotext">
            <pwm:Display key="Display_FooterInfoText"/>&nbsp;
        </span>
        <div>
            <% if (pwmSessionFooter.getSessionStateBean().isAuthenticated()) { %>
            <%= pwmSessionFooter.getUserInfoBean().getUsername()%>
            <% segmentDisplayed = true; } %>
            <% if (pwmApplicationFooter.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_IDLE_TIMEOUT)) { %>
            <% if (!"false".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE))) { %>
            <% if (segmentDisplayed) { %>&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;<%}%>
            <span id="idle_wrapper">
            <span id="idle_status">&nbsp;</span>
            </span>
            <% segmentDisplayed = true; } %>
            <% } %>
            <% if (!"false".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE))) { %>
            <% if (segmentDisplayed) { %>&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;<%}%>
            <span id="localeSelectionMenu" style="white-space: nowrap; cursor: pointer">
                <img alt="flag" src="<%=request.getContextPath()%><pwm:url url='/public/resources/flags/png/'/><%=pwmApplicationFooter.getConfig().getKnownLocaleFlagMap().get(userLocaleFooter)%>.png"/>
                &nbsp;<%=userLocaleFooter == null ? "" : userLocaleFooter.getDisplayLanguage(userLocaleFooter)%>
            </span>
            <% segmentDisplayed = true; } %>
        </div>
        <% final String customScript = pwmApplicationFooter.getConfig().readSettingAsString(PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT); %>
        <% if (customScript != null && customScript.length() > 0) { %>
        <script type="text/javascript">
            <%=MacroMachine.expandMacros(customScript,pwmApplicationFooter,pwmSessionFooter.getUserInfoBean(),pwmSessionFooter.getSessionManager().getUserDataReader())%>
        </script>
        <% } %>
        <script type="text/javascript">
            PWM_GLOBAL["url-context"]='<%=request.getContextPath()%>';
            PWM_GLOBAL['pwmFormID']='<pwm:FormID/>';
            PWM_GLOBAL['clientEtag']='<%=password.pwm.ws.server.rest.RestAppDataServer.makeClientEtag(request,pwmApplicationFooter,pwmSessionFooter)%>';
            PWM_GLOBAL['restClientKey']='<%=pwmSessionFooter.getRestClientKey()%>';
        </script>
        <script data-dojo-config="async: true" type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/dojo/dojo/dojo.js'/>"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/main.js'/>"></script>
    </div>
</div>
