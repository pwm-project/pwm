<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.error.ErrorInformation" %>
<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.util.ServletHelper" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="java.util.Locale" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<% request.setAttribute("noTheme","true"); %>
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% try { password.pwm.PwmSession.getPwmSession(session).unauthenticateUser(); } catch (Exception e) { }%>
<%
    if (!ContextManager.getPwmApplication(request).getConfig().readSettingAsBoolean(PwmSetting.ENABLE_EXTERNAL_WEBSERVICES)) {
        final Locale locale = PwmSession.getPwmSession(request).getSessionStateBean().getLocale();
        PwmSession.getPwmSession(request).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                "Configuration setting " + PwmSetting.Category.MISC.getLabel(locale) + " --> " + PwmSetting.ENABLE_EXTERNAL_WEBSERVICES.getLabel(locale) + " must be enabled for this page to function."));
        ServletHelper.forwardToErrorPage(request, response, true);
    }
%>
<body id="body" class="nihilo" style="background-color: black" onload="pwmPageLoadHandler();">
<div id="centerbody" style="margin-top: 0">
    <div id style="z-index: 3; position: relative; background: white; opacity: 0.9">
        <table id="form">
            <tr>
                <td class="title" colspan="10">
                    <pwm:Display key="Title_Application"/> Health
                </td>
            </tr>
            <tr>
                <td colspan="10"  style="margin:0; padding:0">
                    <div id="healthBody" style="border:0; margin:0; padding:0">
                        <div id="WaitDialogBlank"></div>
                    </div>
                </td>
            </tr>
            <tr>
                <td colspan="10" style="margin:0; padding:0">
                    <div style="max-width: 600px; text-align: center">
                        <div id="EPS-GAUGE-AUTHENTICATION_MINUTE" style="float: left; width: 33%">Authentications</div>
                        <div id="EPS-GAUGE-PASSWORD_CHANGES_MINUTE" style="float: left; width: 33%">Password Changes</div>
                        <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_MINUTE" style="float: left; width: 33%">Intruder Attempts</div>
                    </div>
                    <div style="width: 100%; font-size: smaller; font-style: italic; text-align: center">Events Per Minute</div>
                </td>
            </tr>
        </table>
    </div>
</div>
<div id="floatparent">
</div>
<script type="text/javascript">
    var H_RANGE = 20;
    var V_RANGE = 20;
    var MAX_NODES = 150;

    var splatCount = 0;
    var errorColor = '#d20734';
    var posV = 0;
    var posH = 0;
    var deltaV = Math.floor((Math.random() * V_RANGE * 2)) - V_RANGE;
    var deltaH = Math.floor((Math.random() * H_RANGE * 2)) - H_RANGE;
    var passwordValue = null;

    function drawNextSprite() {
        require(["dojo","dojo/window"],function(dojo){
            if (passwordValue) {
                var floatParent = getObject("floatparent");
                var vs = dojo.window.getBox();

                posV += deltaV;
                posH += deltaH;

                var styleText = "position: absolute; ";
                styleText += "top: " + posV + "px; ";
                styleText += "left: " + posH +"px; ";
                styleText += "padding: 4px; z-index:2; border-radius: 5px; ";
                styleText += "filter:alpha(opacity=30); opacity: 0.3; ";


                splatCount++;
                var divId = "randomPwDiv" + splatCount % MAX_NODES;
                { // remove old node
                    var existingDiv = getObject(divId);
                    if (existingDiv != null) {
                        floatParent.removeChild(existingDiv);
                    }
                }

                var div = document.createElement('div');
                div.innerHTML = passwordValue + ':' + PWM_GLOBAL['epsActivityCount'];
                div.id = divId;
                div.setAttribute("class",'health-' + PWM_GLOBAL['pwm-health']);

                div.setAttribute("style",styleText);
                floatParent.appendChild(div);

                var change = false;
                if (posV < 0) {
                    posV += deltaV * -1;
                    deltaV = Math.floor((Math.random() * V_RANGE));
                    change = true;
                } else if (posV + div.offsetHeight > vs.h) {
                    posV += deltaV * -1;
                    deltaV = Math.floor((Math.random() * V_RANGE)) * -1;
                    change = true;
                }
                if (posH < 0) {
                    posH += deltaH * -1;
                    deltaH = Math.floor((Math.random() * H_RANGE));
                    change = true;
                } else if (posH + div.offsetWidth > vs.w) {
                    posH += deltaH * -1;
                    deltaH = Math.floor((Math.random() * H_RANGE)) * -1;
                    change = true;
                }
                if (change) {
                    splatCount--;
                    deltaV = deltaV == 0 ? 1 : deltaV;
                    deltaH = deltaH == 0 ? 1 : deltaH;
                    drawNextSprite();
                    return;
                }
            }
            var timeOutTime = 1000 - (PWM_GLOBAL['epsActivityCount'] != null ? Math.floor(PWM_GLOBAL['epsActivityCount']) : 0);
            timeOutTime = timeOutTime < 100 ? 100 : timeOutTime;
            setTimeout(function(){
                drawNextSprite();
            },timeOutTime);
        });
    }

    function fetchRandomPassword() {
        require(["dojo"],function(dojo){
            dojo.xhrPost({
                url: PWM_GLOBAL['url-restservice'] + "/randompassword" + "?pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                headers: {"Accept":"application/json"},
                dataType: "json",
                timeout: 15000,
                sync: false,
                preventCache: true,
                handleAs: "json",
                load:  function(resultInfo) {
                    passwordValue = resultInfo['data']["password"];
                },
                error: function(errorObj){
                    passwordValue = "server unreachable";
                }
            });
        });
    }

    function handleWarnFlash() {
        if (PWM_GLOBAL['pwm-health'] == "WARN") {
            flashDomElement(errorColor,'body',3000);
        }
    }

    function verticalCenter(divName) {
        require(["dojo","dojo/window"],function(dojo){
            var vs = dojo.window.getBox();
            if (document.getElementById) {
                var windowHeight = vs.h;
                if (windowHeight > 0) {
                    var contentElement = document.getElementById(divName);
                    var contentHeight = contentElement.offsetHeight;
                    if (windowHeight - contentHeight > 0) {
                        contentElement.style.position = 'relative';
                        contentElement.style.top = ((windowHeight / 2) - (contentHeight / 2)) + 'px';
                    }
                    else {
                        contentElement.style.position = 'static';
                    }
                }
            }
        });
    }

    function startupHealthPage() {
        PWM_GLOBAL['pwm-health'] = 'GOOD';
        require(["dojo","dojo/domReady!","dojo/window"],function(dojo){
            flashDomElement('white','body',9000);

            var vs = dojo.window.getBox();
            posH = Math.floor((Math.random() * (vs.w - 30)));
            posV = Math.floor((Math.random() * (vs.h - 100)));

            fetchRandomPassword();
            setInterval(function(){
                fetchRandomPassword();
            },30 * 1000);

            setInterval(function(){
                handleWarnFlash();
            },30 * 1000);

            drawNextSprite();

            showPwmHealth('healthBody', {showTimestamp:true});

            showStatChart('<%=Statistic.PASSWORD_CHANGES%>',1,'statsChart');
            setInterval(function(){
                showStatChart('<%=Statistic.PASSWORD_CHANGES%>',1,'statsChart');
            }, 31 * 1000);

            verticalCenter('centerbody');
            setInterval(function(){
                verticalCenter('centerbody');
            }, 1000);

        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        startupHealthPage();
        PWM_GLOBAL['idle_suspendTimeout'] = true;
    });
</script>
</body>
</html>
