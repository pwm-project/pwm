<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.PwmRequestFlag" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%-- begin pwm footer --%>
<pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.HIDE_FOOTER_TEXT%>" negate="true">
    <div id="footer">
        <span class="infotext"><pwm:display key="Display_FooterInfoText"/>&nbsp;</span>
        <div id="footer-content">
            <pwm:if test="<%=PwmIfTest.authenticated%>">
                <span class="footer-segment">
                    <span id="session-username"><pwm:display key="Display_UsernameFooter"/></span>
                </span>
            </pwm:if>
            <pwm:if test="<%=PwmIfTest.booleanSetting%>" setting="<%=PwmSetting.DISPLAY_IDLE_TIMEOUT%>">
                <pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.HIDE_IDLE%>" negate="true">
                <span class="footer-segment">
                <span id="idle_wrapper">
                <span id="idle_status">
                <pwm:display key="Display_IdleTimeout"/>&nbsp;<pwm:value name="<%=PwmValue.inactiveTimeRemaining%>"/>
                </span>
                </span>
                </span>
                </pwm:if>
            </pwm:if>
            <pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.HIDE_LOCALE%>" negate="true">
                <span class="footer-segment">
                    <button id="localeSelectionMenu">
                        <img src="<pwm:context/><pwm:url url='/public/resources/flags/png/'/><pwm:value name="<%=PwmValue.localeFlagFile%>"/>.png"/>
                        <span class="localeDisplayName"><pwm:value name="<%=PwmValue.localeName%>"/></span>
                    </button>
                </span>
            </pwm:if>
        </div>
    </div>
</pwm:if>
<pwm:if test="<%=PwmIfTest.requestFlag%>" requestFlag="<%=PwmRequestFlag.NO_IDLE_TIMEOUT%>">
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['idle_suspendTimeout'] = true;
        </script>
    </pwm:script>
</pwm:if>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            <pwm:value name="<%=PwmValue.customJavascript%>"/>
        });
    </script>
</pwm:script>
<script nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" data-dojo-config="async: true" dojo-sync-loader="false" type="text/javascript" src="<pwm:url addContext="true" url='/public/resources/dojo/dojo/dojo.js'/>"></script>
<pwm:script-ref url="/public/resources/js/main.js"/>
