<%@ page import="password.pwm.PwmEnvironment" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2017 The PWM Project
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
