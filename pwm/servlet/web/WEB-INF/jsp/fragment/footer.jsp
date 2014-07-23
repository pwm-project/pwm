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

<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ page import="password.pwm.http.ContextManager" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="password.pwm.util.macro.MacroMachine" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
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
    <% if (!"true".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_HIDE_FOOTER_TEXT))) { %>
    <div id="footer-content">
        <span class="infotext">
            <pwm:Display key="Display_FooterInfoText"/>&nbsp;
        </span>
        <div>
            <% if (pwmSessionFooter.getSessionStateBean().isAuthenticated()) { %>
            <%= pwmSessionFooter.getUserInfoBean().getUsername()%>
            <% segmentDisplayed = true; } %>
            <% if (pwmApplicationFooter.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_IDLE_TIMEOUT)) { %>
            <% if (!"false".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_SHOW_IDLE))) { %>
            <% if (segmentDisplayed) { %>&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;<%}%>
            <span id="idle_wrapper">
            <span id="idle_status">
                <pwm:Display key="Display_IdleTimeout"/> <%=new TimeDuration(request.getSession().getMaxInactiveInterval() * 1000).asLongString(userLocaleFooter)%>
            </span>
            </span>
            <% segmentDisplayed = true; } %>
            <% } %>
            <% if (!"false".equalsIgnoreCase((String)request.getAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE))) { %>
            <% if (segmentDisplayed) { %>&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;<%}%>
            <span id="localeSelectionMenu" style="white-space: nowrap; cursor: pointer">
                <% String flagFileName = pwmApplicationFooter.getConfig().getKnownLocaleFlagMap().get(userLocaleFooter);%>
                <% if (flagFileName != null && !flagFileName.isEmpty()) { %>
                <img src="<%=request.getContextPath()%><pwm:url url='/public/resources/flags/png/'/><%=flagFileName%>.png"/>
                <% } %>
                &nbsp;<%=userLocaleFooter == null ? "" : userLocaleFooter.getDisplayName(userLocaleFooter)%>
            </span>
            <% segmentDisplayed = true; } %>
        </div>
    </div>
    <% } %>
    <% final String customScript = pwmApplicationFooter.getConfig().readSettingAsString(PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT); %>
    <% if (customScript != null && customScript.length() > 0) { %>
    <pwm:script>
    <script nonce="<pwm:value name="cspNonce"/>" type="text/javascript">
        <% final MacroMachine macroMachineFooter = new MacroMachine(pwmApplicationFooter,pwmSessionFooter.getUserInfoBean(),pwmSessionFooter.getSessionManager().getUserDataReader(pwmApplicationFooter)); %>
        <%= macroMachineFooter.expandMacros(customScript) %>
    </script>
    </pwm:script>
    <% } %>
    <script nonce="<pwm:value name="cspNonce"/>" data-dojo-config="async: true" dojo-sync-loader="false" type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/dojo/dojo/dojo.js'/>"></script>
    <script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/main.js'/>"></script>
    <pwm:script>
        <script nonce="<pwm:value name="cspNonce"/>" type="text/javascript">
            PWM_MAIN.pageLoadHandler();
        </script>
    </pwm:script>
    <pwm:if test="stripInlineJavascript">
        <script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:url url='/public/CommandServlet?processAction=scriptContents' addContext="true"/>&time=<%=System.currentTimeMillis()%>"></script>
    </pwm:if>
</div>
