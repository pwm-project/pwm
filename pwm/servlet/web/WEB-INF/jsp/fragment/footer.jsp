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
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
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
        startupLocaleSelectorMenu(localeInfo, 'localeSelectionMenu');
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
        PWM_STRINGS['Tooltip_PasswordStrength'] = "<pwm:Display key="Tooltip_PasswordStrength"/>";
        PWM_STRINGS['Display_PasswordPrompt'] = "<pwm:Display key="Display_PasswordPrompt"/>";
        PWM_STRINGS['Display_CheckingPassword'] = "<pwm:Display key="Display_CheckingPassword"/>";
        PWM_STRINGS['Display_PasswordGeneration'] = "<pwm:Display key="Display_PasswordGeneration"/>";
        PWM_STRINGS['Display_CommunicationError'] = "<pwm:Display key="Display_CommunicationError"/>";
        PWM_STRINGS['Display_LeaveDirtyPasswordPage'] = "<pwm:Display key="Display_LeaveDirtyPasswordPage"/>";
        PWM_STRINGS['Strength_Low'] = "<pwm:Display key="Display_PasswordStrengthLow"/>";
        PWM_STRINGS['Strength_Medium'] = "<pwm:Display key="Display_PasswordStrengthMedium"/>";
        PWM_STRINGS['Strength_High'] = "<pwm:Display key="Display_PasswordStrengthHigh"/>";
        PWM_STRINGS['Button_Hide'] = "<pwm:Display key="Button_Hide"/>";
        PWM_STRINGS['Button_Show'] = "<pwm:Display key="Button_Show"/>";
        PWM_STRINGS['Button_Cancel'] = "<pwm:Display key="Button_Cancel"/>";
        PWM_STRINGS['Button_More'] = "<pwm:Display key="Button_More"/>";
        PWM_STRINGS['Title_RandomPasswords'] = "<pwm:Display key="Title_RandomPasswords"/>";
        PWM_STRINGS['Title_PasswordGuide'] = "<pwm:Display key="Title_PasswordGuide"/>";
        PWM_STRINGS['url-changepassword'] = "<pwm:url url='ChangePassword'/>";
        PWM_STRINGS['passwordGuideText'] = '<%=ContextManager.getPwmApplication(session).getConfig().readSettingAsLocalizedString(PwmSetting.DISPLAY_PASSWORD_GUIDE_TEXT,PwmSession.getPwmSession(session).getSessionStateBean().getLocale())%>';
        dojo.addOnLoad(function(){var img = new Image();img.src='<%=request.getContextPath()%>/resources/wait.gif'});
        dojo.require("dijit.Dialog");
    </script>
    <script type="text/javascript">
        dojo.addOnLoad(function(){
            initCountDownTimer(<%= sessionStateBean.getMaxInactiveSeconds() %>);
        });
        dojo.addOnUnload(function(){
            dojo.xhrGet({
                url: PWM_GLOBAL['url-command'] + "?processAction=pageLeaveNotice&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                sync: true,
                failOk: true
            });
        });
    </script>

</div>
