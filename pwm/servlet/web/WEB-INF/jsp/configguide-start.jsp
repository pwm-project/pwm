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

<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_THEME); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_IDLE_TIMEOUT); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                <pwm:display key="Title_ConfigGuide_start" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <pwm:display key="Display_ConfigManagerNew" bundle="Config" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <br/>
        <table style="border:0">
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="button-startConfigGuide">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-rocket"></span></pwm:if>
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
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-cogs"></span></pwm:if>
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
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-upload"></span></pwm:if>
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
                        skipWizard();
                    });
                } else {
                    skipWizard();
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

        function skipWizard() {
            PWM_MAIN.showConfirmDialog({text:'<pwm:display key="Confirm_SkipGuide" bundle="Config"/>',okAction:function() {
                PWM_GUIDE.gotoStep('NEXT');
            }});
        }
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
