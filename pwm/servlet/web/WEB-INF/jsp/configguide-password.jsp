<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
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
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<% ConfigGuideBean configGuideBean = JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                Password
            </div>
        </div>
    </div>
    <div id="centerbody">
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <div id="password" class="setting_outline">
                <div class="setting_title">
                    Configuration Password
                </div>
                <div class="setting_body">
                    To protect this system, you will need to set a configuration password.  The configuration password will be required whenever
                    you wish to modify the configuration using the web based configuration manager.
                    <div class="setting_item">
                        <b>Configuration Password</b>
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input type="<pwm:value name="passwordFieldType"/>" id="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>" name="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>" class="configStringInput passwordfield" style="width:200px" <pwm:autofocus/>/>
                    </div>
                    <div class="setting_item">
                        <b>Verify Configuration Password</b>
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input type="<pwm:value name="passwordFieldType"/>" id="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>" name="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>" class="configStringInput passwordfield" style="width:200px"/>
                        <div style="display: inline; padding-top:45px;">
                            <img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15"
                                 src="<pwm:context/><pwm:url url='/public/resources/greenCheck.png'/>">
                            <img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15"
                                 src="<pwm:context/><pwm:url url='/public/resources/redX.png'/>">
                        </div>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div class="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:display key="Button_Next" bundle="Config"/>
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    function handleFormActivity() {
        PWM_GUIDE.updateForm();
        checkIfNextEnabled();
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
            checkIfNextEnabled();
        });

        PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
        PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

        PWM_MAIN.addEventHandler('configForm','input',function(){handleFormActivity()});
    });

    function checkIfNextEnabled() {
        var password = PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>').value;
        var password2 = PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>').value;

        PWM_MAIN.getObject('button_next').disabled = true;
        PWM_MAIN.getObject('confirmCheckMark').style.visibility = 'hidden';
        PWM_MAIN.getObject('confirmCrossMark').style.visibility = 'hidden';
        if (password2.length > 0) {
            if (password === password2) {
                console.log('yep');
                PWM_MAIN.getObject('confirmCheckMark').style.visibility = 'visible';
                PWM_MAIN.getObject('confirmCrossMark').style.visibility = 'hidden';
                PWM_MAIN.getObject('button_next').disabled = false;
            } else {
                console.log('nope');
                PWM_MAIN.getObject('confirmCheckMark').style.visibility = 'hidden';
                PWM_MAIN.getObject('confirmCrossMark').style.visibility = 'visible';
            }
        }
    }
</script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
