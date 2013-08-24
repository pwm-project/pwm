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
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
        </div>
        <div id="header-title">
            <pwm:Display key="Title_ConfigGuide_start" bundle="Config"/>
        </div>
    </div>
    <div id="centerbody">
        <pwm:Display key="Display_ConfigManagerNew" bundle="Config" value1="<%=PwmConstants.PWM_APP_NAME%>"/>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <br/>
        <table style="border:0">
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" href="#" onclick="if (PWM_GLOBAL['setting-displayEula']) {showEula(true,function(){gotoStep('TEMPLATE');}); } else {gotoStep('TEMPLATE');};"><pwm:Display key="MenuItem_StartConfigGuide" bundle="Config"/></a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_StartConfigGuide" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" href="#" onclick="if (PWM_GLOBAL['setting-displayEula']) {showEula(true,function(){skipWizard();}); } else {skipWizard();}"><pwm:Display key="MenuItem_ManualConfig" bundle="Config"/></a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_ManualConfig" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" href="#" onclick="if (PWM_GLOBAL['setting-displayEula']) {showEula(true,function(){uploadConfigDialog();}); } else {uploadConfigDialog();};"><pwm:Display key="MenuItem_UploadConfig" bundle="Config"/></a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_UploadConfig" bundle="Config"/></p>
                </td>
            </tr>
            </table>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    function skipWizard() {
        showConfirmDialog(null,'<pwm:Display key="Confirm_SkipGuide" bundle="Config"/>',function(){
            gotoStep('FINISH');
        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
