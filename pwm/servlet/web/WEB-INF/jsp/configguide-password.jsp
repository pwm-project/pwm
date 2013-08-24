<%@ page import="password.pwm.bean.servlet.ConfigGuideBean" %>
<%@ page import="password.pwm.servlet.ConfigGuideServlet" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="password.pwm.servlet.ConfigManagerServlet" %>
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
<% ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
        </div>
        <div id="header-title">
            Password
        </div>
    </div>
    <div id="centerbody">
        <form id="configForm" data-dojo-type="dijit/form/Form">
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
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <input type="password" id="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>" name="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>" />
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                    new ValidationTextBox({
                                        id: '<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>',
                                        name: '<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>',
                                        required: true,
                                        type: "password",
                                        style: "width: 200px",
                                        onKeyUp: function() {
                                            handleFormActivity();
                                        },
                                        value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_CONFIG_PASSWORD)%>'
                                    }, "<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>");
                                });
                            });
                        </script>
                    </div>
                    <div class="setting_item">
                        <b>Verify Configuration Password</b>
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <input type="password" id="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>" name="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>" />
                        <div style="display: inline; padding-top:45px;">
                            <img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15"
                                 src="<%=request.getContextPath()%><pwm:url url='/public/resources/greenCheck.png'/>">
                            <img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15"
                                 src="<%=request.getContextPath()%><pwm:url url='/public/resources/redX.png'/>">
                        </div>
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                    new ValidationTextBox({
                                        id: '<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>',
                                        name: '<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>',
                                        required: true,
                                        type: "password",
                                        style: "width: 200px",
                                        onKeyUp: function() {
                                            handleFormActivity();
                                        }
                                    }, "<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>");
                                });
                            });
                        </script>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="gotoStep('LDAP3');"><pwm:Display key="Button_Previous" bundle="Config"></pwm:Display></button>
            <button class="btn" id="button_next" onclick="gotoStep('END');"><pwm:Display key="Button_Next" bundle="Config"></pwm:Display></button>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    function handleFormActivity() {
        updateForm();
        checkIfNextEnabled();
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
            checkIfNextEnabled();
        });
    });

    function checkIfNextEnabled() {
        var password = getObject('<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>').value;
        var password2 = getObject('<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>').value;

        getObject('button_next').disabled = true;
        getObject('confirmCheckMark').style.visibility = 'hidden';
        getObject('confirmCrossMark').style.visibility = 'hidden';
        if (password2.length > 0) {
            if (password === password2) {
                console.log('yep');
                getObject('confirmCheckMark').style.visibility = 'visible';
                getObject('confirmCrossMark').style.visibility = 'hidden';
                getObject('button_next').disabled = false;
            } else {
                console.log('nope');
                getObject('confirmCheckMark').style.visibility = 'hidden';
                getObject('confirmCrossMark').style.visibility = 'visible';
            }
        }
    }
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
