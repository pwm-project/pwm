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
<% final Locale userLocaleFooter = PwmSession.getPwmSession(request).getSessionStateBean().getLocale(); %>
<%-- begin pwm footer --%>
<div id="emptyDiv" style="max-width:1px"></div>
<div id="footer">
    <br/>
    <br/>
    <span class="infotext">
        <pwm:Display key="Display_FooterInfoText"/>&nbsp;
    </span>
    <br/>
    <div>
        <% if (PwmSession.getPwmSession(request).getSessionStateBean().isAuthenticated()) { %>
        <%= PwmSession.getPwmSession(request).getUserInfoBean().getUserID()%>
        &nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;
        <% } %>
    <span id="idle_status">
        &nbsp;
    </span>
        &nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;&nbsp;
    <span id="localeSelectionMenu" style="white-space: nowrap">
        <img alt="flag" src="<%=request.getContextPath()%><pwm:url url='/resources/flags/png/'/><%=ContextManager.getPwmApplication(session).getConfig().getKnownLocaleFlagMap().get(userLocaleFooter)%>.png"/>
        &nbsp;<%=userLocaleFooter == null ? "" : userLocaleFooter.getDisplayLanguage(userLocaleFooter)%>
    </span>
    </div>
    <% final String customScript = ContextManager.getPwmApplication(session).getConfig().readSettingAsString(PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT); %>
    <% if (customScript != null && customScript.length() > 0) { %>
    <script type="text/javascript">
        <%=PwmMacroMachine.expandMacros(customScript,ContextManager.getPwmApplication(session),PwmSession.getPwmSession(session).getUserInfoBean())%>
    </script>
    <% } %>
</div>
