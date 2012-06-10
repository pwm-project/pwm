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
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.util.PwmMacroMachine" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%-- begin pwm footer --%>
<div id="footer">
    <span class="infotext">
        <pwm:Display key="Display_FooterInfoText"/>&nbsp;
    </span>
    <br/>
    <br/>
    <span class="idle_status" id="idle_status">
        &nbsp;
    </span>
    <br/>
    <% if (PwmSession.getPwmSession(request).getSessionStateBean().isAuthenticated()) { %>
    <%= PwmSession.getPwmSession(request).getUserInfoBean().getUserID()%>
    |
    <% } %>
    <%
        final password.pwm.bean.SessionStateBean sessionStateBean = PwmSession.getPwmSession(session).getSessionStateBean();
        final String userIP = sessionStateBean.getSrcAddress();
        if (userIP != null) {
            out.write(userIP);
        }
    %>
    <% String currentLocaleName = sessionStateBean.getLocale() != null && !sessionStateBean.getLocale().getDisplayName().equals("") ? sessionStateBean.getLocale().getDisplayName() : new Locale("en").getDisplayName(); %>
    | <span id="localeSelectionMenu"><%=currentLocaleName%></span>
    <script type="text/javascript">
        require(["dojo/ready"],function(){
            initLocaleSelectorMenu('localeSelectionMenu');
            IdleTimeoutHandler.initCountDownTimer(<%= request.getSession().getMaxInactiveInterval() %>);
        });
    </script>
    <% final String customScript = ContextManager.getPwmApplication(session).getConfig().readSettingAsString(PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT); %>
    <% if (customScript != null && customScript.length() > 0) { %>
    <script type="text/javascript">
        <%=PwmMacroMachine.expandMacros(customScript,ContextManager.getPwmApplication(session),PwmSession.getPwmSession(session).getUserInfoBean())%>
    </script>
    <% } %>
    <% final String googleTrackingCode = ContextManager.getPwmApplication(session).getConfig().readSettingAsString(password.pwm.config.PwmSetting.GOOGLE_ANAYLTICS_TRACKER); %>
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
</div>
