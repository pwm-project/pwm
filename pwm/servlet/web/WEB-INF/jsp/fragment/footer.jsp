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
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.util.PwmMacroMachine" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%-- begin pwm footer --%>
<div id="footer">
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
    <script type="text/javascript"> <%-- locale selector menu, uses jsscript to write to prevent restricted environments from showing menu --%>
    var localeInfo = {};
    <% for (final Locale loopLocale : PwmConstants.KNOWN_LOCALES) { %>localeInfo['<%=loopLocale.toString()%>'] = '<%=loopLocale.getDisplayName()%>'; <% } %>
    document.write('| <span id="localeSelectionMenu"><%=currentLocaleName%></span>');
    dojo.addOnLoad(function(){setTimeout(function(){
            startupLocaleSelectorMenu(localeInfo, 'localeSelectionMenu');
    },100);});
    </script>
    <style type="text/css"> <%-- stylesheets used by flag routine on locale menu --%>
    <% for (final Locale loopLocale : PwmConstants.KNOWN_LOCALES) { %>
    <% if ("".equals(loopLocale.toString())) { %>
    .flagLang_en { background-image: url(<%=request.getContextPath()%>/resources/flags/languages/en.png); }
    <% } else { %>.flagLang_<%=loopLocale.toString()%> { background-image: url(<%=request.getContextPath()%>/resources/flags/languages/<%=loopLocale.toString()%>.png); } <% } %>
    <% } %>
    </style>
    <%-- fields for javascript display fields --%>
    <script type="text/javascript">
        PWM_STRINGS['Button_Logout'] = "<pwm:Display key="Button_Logout"/>";
        PWM_STRINGS['Display_IdleTimeout'] = "<pwm:Display key="Display_IdleTimeout"/>";
        PWM_STRINGS['Display_Day'] = "<pwm:Display key="Display_Day"/>";
        PWM_STRINGS['Display_Days'] = "<pwm:Display key="Display_Days"/>";
        PWM_STRINGS['Display_Hour'] = "<pwm:Display key="Display_Hour"/>";
        PWM_STRINGS['Display_Hours'] = "<pwm:Display key="Display_Hours"/>";
        PWM_STRINGS['Display_Minute'] = "<pwm:Display key="Display_Minute"/>";
        PWM_STRINGS['Display_Minutes'] = "<pwm:Display key="Display_Minutes"/>";
        PWM_STRINGS['Display_Second'] = "<pwm:Display key="Display_Second"/>";
        PWM_STRINGS['Display_Seconds'] = "<pwm:Display key="Display_Seconds"/>";
        PWM_STRINGS['Display_PleaseWait'] = "<pwm:Display key="Display_PleaseWait"/>";
        PWM_STRINGS['Display_IdleWarningTitle'] = "<pwm:Display key="Display_IdleWarningTitle"/>";
        PWM_STRINGS['Display_IdleWarningMessage'] = "<pwm:Display key="Display_IdleWarningMessage"/>";
        PWM_STRINGS['Display_CommunicationError'] = "<pwm:Display key="Display_CommunicationError"/>";
        PWM_STRINGS['Display_LeaveDirtyPasswordPage'] = "<pwm:Display key="Display_LeaveDirtyPasswordPage"/>";
        PWM_STRINGS['Button_Hide'] = "<pwm:Display key="Button_Hide"/>";
        PWM_STRINGS['Button_Show'] = "<pwm:Display key="Button_Show"/>";
        PWM_STRINGS['Button_Cancel'] = "<pwm:Display key="Button_Cancel"/>";
        PWM_STRINGS['Button_More'] = "<pwm:Display key="Button_More"/>";
        PWM_STRINGS['Display_CheckingPassword'] = "<pwm:Display key="Display_CheckingPassword"/>";
        PWM_STRINGS['Display_PasswordPrompt'] = "<pwm:Display key="Display_PasswordPrompt"/>";
        PWM_STRINGS['url-changepassword'] = "<pwm:url url='ChangePassword'/>";
        dojo.addOnLoad(function(){setTimeout(function(){ // pre-fetch dojo/dijit objects
            dojo.require("dijit.Dialog");dojo.require("dijit.Tooltip");dojo.require("dijit.Menu");dojo.require("dijit.MenuItem");
        },3500);});
        dojo.addOnLoad(function(){setTimeout(function(){
            initCountDownTimer(<%= sessionStateBean.getMaxInactiveSeconds() %>);
        },90);});
        dojo.addOnUnload(function(){
            dojo.xhrGet({
                url: PWM_GLOBAL['url-command'] + "?processAction=pageLeaveNotice&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                sync: true,
                load: function() {},
                error: function() {}
            });
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
