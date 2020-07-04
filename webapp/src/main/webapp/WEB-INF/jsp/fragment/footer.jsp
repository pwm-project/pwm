<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
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
                    <span id="localeSelectionMenu">
                        <img src="<pwm:context/><pwm:url url='/public/resources/webjars/famfamfam-flags/dist/png/'/><pwm:value name="<%=PwmValue.localeFlagFile%>"/>.png"
                             alt="<pwm:value name="<%=PwmValue.localeFlagFile%>"/>"/>
                        <span class="localeDisplayName"><pwm:value name="<%=PwmValue.localeName%>"/></span>
                    </span>
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
        var dojoConfig = { has: { "csp-restrictions":true }, async:true}
    </script>
</pwm:script>
<pwm:if test="<%=PwmIfTest.hasCustomJavascript%>">
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function() {
                <pwm:value name="<%=PwmValue.customJavascript%>"/>
            });
        </script>
    </pwm:script>
</pwm:if>
<script nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" dojo-sync-loader="false" type="text/javascript" src="<pwm:url addContext="true" url='/public/resources/webjars/dojo/dojo.js'/>"></script><noscript></noscript>
<pwm:script-ref url="/public/resources/js/main.js"/>
