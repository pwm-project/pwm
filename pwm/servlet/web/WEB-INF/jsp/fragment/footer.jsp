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

<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.error.PwmUnrecoverableException" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmSession pwmSessionFooter = null;
    PwmApplication pwmApplicationFooter = null;
    PwmRequest footer_pwmRequest = null;
    try {
        footer_pwmRequest = PwmRequest.forRequest(request,response);
        pwmApplicationFooter = footer_pwmRequest.getPwmApplication();
        pwmSessionFooter = footer_pwmRequest.getPwmSession();
    } catch (PwmUnrecoverableException e) {
        /* application must be unavailable */
    }
%>
<% final Locale userLocaleFooter = pwmSessionFooter == null ? PwmConstants.DEFAULT_LOCALE : pwmSessionFooter.getSessionStateBean().getLocale(); %>
<% boolean segmentDisplayed = false; %>
<%-- begin pwm footer --%>
<% if (footer_pwmRequest != null && !footer_pwmRequest.isFlag(PwmRequest.Flag.HIDE_FOOTER_TEXT)) { %>
<div id="footer">
    <div id="footer-content">
        <span class="infotext">
            <pwm:display key="Display_FooterInfoText"/>&nbsp;
        </span>
        <div>
            <% if (footer_pwmRequest.isAuthenticated()) { %>
            <% if (footer_pwmRequest.getPwmSession().getUserInfoBean().getUsername() != null) {%>
            <%= footer_pwmRequest.getPwmSession().getUserInfoBean().getUsername()  %>
            <% } %>
            <% segmentDisplayed = true; } %>
            <% if (pwmApplicationFooter.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_IDLE_TIMEOUT)) { %>
            <% if (!footer_pwmRequest.isFlag(PwmRequest.Flag.HIDE_IDLE)) { %>
            <% if (segmentDisplayed) { %>&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;<%}%>
            <span id="idle_wrapper">
            <span id="idle_status">
                <pwm:display key="Display_IdleTimeout"/> <%=new TimeDuration(request.getSession().getMaxInactiveInterval() * 1000).asLongString(userLocaleFooter)%>
            </span>
            </span>
            <% segmentDisplayed = true; } %>
            <% } %>
            <% if (!footer_pwmRequest.isFlag(PwmRequest.Flag.HIDE_LOCALE)) { %>
            <% if (segmentDisplayed) { %>&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;<%}%>
            <span id="localeSelectionMenu">
                <% String flagFileName = pwmApplicationFooter.getConfig().getKnownLocaleFlagMap().get(userLocaleFooter);%>
                <% if (flagFileName != null && !flagFileName.isEmpty()) { %>
                <img src="<pwm:context/><pwm:url url='/public/resources/flags/png/'/><%=flagFileName%>.png"/>
                <% } %>
                &nbsp;<%=userLocaleFooter == null ? "" : userLocaleFooter.getDisplayName(userLocaleFooter)%>
            </span>
            <% segmentDisplayed = true; } %>
        </div>
    </div>
</div>
<% } %>
<% if (footer_pwmRequest.isFlag(PwmRequest.Flag.NO_IDLE_TIMEOUT)) { %>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['idle_suspendTimeout'] = true;
    </script>
</pwm:script>
<% } %>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            <pwm:value name="customJavascript"/>
        });
    </script>
</pwm:script>
<script nonce="<pwm:value name="cspNonce"/>" data-dojo-config="async: true" dojo-sync-loader="false" type="text/javascript" src="<pwm:context/><pwm:url url='/public/resources/dojo/dojo/dojo.js'/>"></script>
<pwm:script-ref url="/public/resources/js/main.js"/>
