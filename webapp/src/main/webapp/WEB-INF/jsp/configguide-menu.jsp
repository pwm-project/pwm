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


<%@ page import="password.pwm.PwmEnvironment" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.util.java.StringUtil" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <br/>
        <table class="noborder">
            <tr class="noborder">
                <td class="noborder" class="menubutton_key">
                    <a class="menubutton" id="button-startConfigGuide">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-rocket"></span></pwm:if>
                        <pwm:display key="MenuItem_StartConfigGuide" bundle="Config"/>
                    </a>
                </td>
                <td class="noborder">
                    <p><pwm:display key="MenuDisplay_StartConfigGuide" bundle="Config"/></p>
                </td>
            </tr>
            <tr class="noborder">
                <td class="noborder" class="menubutton_key">
                    <a class="menubutton" id="button-manualConfig">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-cogs"></span></pwm:if>
                        <pwm:display key="MenuItem_ManualConfig" bundle="Config"/>
                    </a>
                </td>
                <td class="noborder">
                    <p><pwm:display key="MenuDisplay_ManualConfig" bundle="Config"/></p>
                </td>
            </tr>
            <tr class="noborder">
                <td class="noborder" class="menubutton_key">
                    <a class="menubutton" id="button-uploadConfig">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-upload"></span></pwm:if>
                        <pwm:display key="MenuItem_UploadConfig" bundle="Config"/>
                    </a>
                </td>
                <td class="noborder">
                    <p><pwm:display key="MenuDisplay_UploadConfig" bundle="Config"/></p>
                </td>
            </tr>
        </table>
        <div class="buttonbar configguide">
            <button class="btn" id="button_previous">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('button-startConfigGuide', 'click', function () {
                    PWM_GUIDE.gotoStep('NEXT');
            });
            PWM_MAIN.addEventHandler('button-manualConfig', 'click', function () {
                    PWM_GUIDE.skipGuide();
            });
            PWM_MAIN.addEventHandler('button-uploadConfig', 'click', function () {
                    PWM_CONFIG.uploadConfigDialog();
            });

        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
