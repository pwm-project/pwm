<%@ page import="password.pwm.PwmEnvironment" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.util.StringUtil" %>
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

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <span><%=PwmConstants.PWM_APP_NAME_VERSION%></span>
        <pwm:if test="<%=PwmIfTest.trialMode%>"><span><pwm:display key="Header_TrialMode" bundle="Admin"/></span></pwm:if>
        <br/>
        <pwm:display key="Display_ConfigManagerNew" bundle="Config" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

        <p>
            <pwm:if test="<%=PwmIfTest.appliance%>" negate="true">
                Application Configuration Path: <code><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getPwmApplication().getPwmEnvironment().getApplicationPath().getAbsolutePath())%></code>
            </pwm:if>
        </p>

        <br/>
        <table style="border:0">
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="button-startConfigGuide">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-rocket"></span></pwm:if>
                        <pwm:display key="MenuItem_StartConfigGuide" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:display key="MenuDisplay_StartConfigGuide" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="button-manualConfig">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-cogs"></span></pwm:if>
                        <pwm:display key="MenuItem_ManualConfig" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:display key="MenuDisplay_ManualConfig" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="button-uploadConfig">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-upload"></span></pwm:if>
                        <pwm:display key="MenuItem_UploadConfig" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:display key="MenuDisplay_UploadConfig" bundle="Config"/></p>
                </td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            PWM_MAIN.addEventHandler('button-startConfigGuide', 'click', function () {
                if (PWM_GLOBAL['setting-displayEula']) {
                    PWM_MAIN.showEula(true, function () {
                        PWM_GUIDE.gotoStep('NEXT');
                    });
                } else {
                    PWM_GUIDE.gotoStep('NEXT');
                }
            });
            PWM_MAIN.addEventHandler('button-manualConfig', 'click', function () {
                if (PWM_GLOBAL['setting-displayEula']) {
                    PWM_MAIN.showEula(true,function(){
                        PWM_GUIDE.skipGuide();
                    });
                } else {
                    PWM_GUIDE.skipGuide();
                }
            });
            PWM_MAIN.addEventHandler('button-uploadConfig', 'click', function () {
                if (PWM_GLOBAL['setting-displayEula']) {
                    PWM_MAIN.showEula(true,function(){
                        PWM_CONFIG.uploadConfigDialog();
                    });
                } else {
                    PWM_CONFIG.uploadConfigDialog();
                }
            });

        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
