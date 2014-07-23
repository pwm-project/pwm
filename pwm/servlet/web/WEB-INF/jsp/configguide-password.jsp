<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<% ConfigGuideBean configGuideBean = (ConfigGuideBean) PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                Password
            </div>
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
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input type="password" id="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>" name="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD%>" />
                        <pwm:script>
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
                        </pwm:script>
                    </div>
                    <div class="setting_item">
                        <b>Verify Configuration Password</b>
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input type="password" id="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>" name="<%=ConfigGuideServlet.PARAM_CONFIG_PASSWORD_VERIFY%>" />
                        <div style="display: inline; padding-top:45px;">
                            <img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15"
                                 src="<%=request.getContextPath()%><pwm:url url='/public/resources/greenCheck.png'/>">
                            <img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15"
                                 src="<%=request.getContextPath()%><pwm:url url='/public/resources/redX.png'/>">
                        </div>
                        <pwm:script>
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
                        </pwm:script>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="PWM_GUIDE.gotoStep('CR_STORAGE');">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:Display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="PWM_GUIDE.gotoStep('END');">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:Display key="Button_Next" bundle="Config"/>
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
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
