<%@ page import="password.pwm.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.servlet.ConfigGuideServlet" %>
<%@ page import="java.util.Map" %>
<%@ page import="com.google.gson.Gson" %>
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
<% Map<String,String> DEFAULT_FORM = ConfigGuideServlet.defaultForm(configGuideBean.getStoredConfiguration().getTemplate()); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
        </div>
        <div id="header-title">
            <pwm:Display key="Title_ConfigGuide_ldap" bundle="Config"/>
        </div>
    </div>
    <div id="centerbody">
        <form id="configForm" data-dojo-type="dijit/form/Form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="configDiv">
                <div id="titlePaneHeader-ldap-server" title="Server Information" style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="open:false">
                    Please enter the connection information for your ldap server.
                </div>
                <br/>
                <div style="padding-left: 10px; padding-bottom: 5px">
                <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                    <b>LDAP Hostname / Server Address</b>
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="value_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" name="setting_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=ConfigGuideServlet.PARAM_LDAP_HOST%>',
                                    name: '<%=ConfigGuideServlet.PARAM_LDAP_HOST%>',
                                    required: true,
                                    style: "width: 300px",
                                    placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_HOST)%>',
                                    onKeyUp: function() {
                                        handleFormActivity();
                                    },
                                    value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>'
                                }, "value_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>");
                            });
                        });
                    </script>
                </div>

                <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" style="padding-left: 5px; padding-top: 5px">
                    <b>LDAP Port</b>
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" name="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/NumberSpinner"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=ConfigGuideServlet.PARAM_LDAP_PORT%>',
                                    name: '<%=ConfigGuideServlet.PARAM_LDAP_PORT%>',
                                    required: true,
                                    style: "width: 80px",
                                    placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_PORT)%>',
                                    onKeyUp: function() {
                                        handleFormActivity();
                                    },
                                    value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%>'
                                }, "<%=ConfigGuideServlet.PARAM_LDAP_PORT%>");
                            });
                        });
                    </script>
                </div>
                <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" style="padding-left: 5px; padding-top: 5px">
                    <b>Secure (SSL) Connection</b>
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input type="hidden" id="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" name="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>"/>
                    <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/registry","dijit/form/ToggleButton"],function(registry,ToggleButton){
                                new ToggleButton({
                                    id: 'widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>',
                                    iconClass:'dijitCheckBoxIcon',
                                    label: 'Secure',
                                    style: 'width:100px',
                                    onChange: function() {
                                        if (this.checked) {
                                            getObject('<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').value="true";
                                            registry.byId('<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').set('value','636');
                                        } else {
                                            getObject('<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').value="false";
                                            registry.byId('<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').set('value','389');
                                        }
                                        handleFormActivity();
                                    },
                                    checked: <%="true".equalsIgnoreCase(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_SECURE))%>
                                },'widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>');
                            });
                        });
                    </script>
                </div>
                </div>
            </div>

            <br class="clear"/>
            <div id="outline_ldap-user" class="configDiv">
                <div id="titlePaneHeader-ldap-user" style="width:580px" data-dojo-type="dijit/TitlePane" title="Admin/Proxy Account" data-dojo-props="open:false">
                    Please enter the credentials for your ldap server.  You must enter the fully qualified LDAP DN of the
                    admin account here.  In most cases, you should use an account created specially for this purpose, with enough rights to
                    administer the users that will be logging into this system.
                </div>
                <br/>
                <div style="padding-left: 10px; padding-bottom: 5px">
                <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" style="padding-left: 5px; padding-top: 5px">
                    <b>Proxy/Admin LDAP DN</b>
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" name="setting_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>',
                                    name: '<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>',
                                    required: true,
                                    style: "width: 500px",
                                    placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_ADMIN_DN)%>',
                                    onKeyUp: function() {
                                        handleFormActivity();
                                    },
                                    value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_ADMIN_DN)%>'
                                }, "<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>");
                            });
                        });
                    </script>
                </div>

                <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" style="padding-left: 5px; padding-top: 5px">
                    <b>Password</b>
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" name="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" type="password"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>',
                                    name: '<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>',
                                    required: true,
                                    type: "password",
                                    style: "width: 200px",
                                    placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_ADMIN_PW)%>',
                                    onKeyUp: function() {
                                        handleFormActivity();
                                    },
                                    value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_ADMIN_PW)%>'
                                }, "<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>");
                            });
                        });
                    </script>
                </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="healthBody" style="border:0; margin:0; padding:0" onclick="loadHealth()">
            <div style="text-align: center">
                <button class="menubutton" onclick="loadHealth()">Check Settings</button>
            </div>
        </div>
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="gotoStep('TEMPLATE')"><pwm:Display key="Button_Previous" bundle="Config"/></button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="gotoStep('LDAPCERT')"><pwm:Display key="Button_Next" bundle="Config"/></button>
        </div>
    </div>
</div>
<script type="text/javascript">
    function handleFormActivity() {
        updateForm();
        clearHealthDiv();
    }

    function clearHealthDiv() {
        var healthBodyObj = getObject('healthBody');
        var newHtml = '<div style="text-align: center">';
        newHtml += '<button class="menubutton">Check Settings</button>';
        newHtml += '</div>'
        healthBodyObj.innerHTML = newHtml;
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            clearHealthDiv();
        });
        checkIfNextEnabled();
    });

    function checkIfNextEnabled() {
        if (PWM_GLOBAL['pwm-health'] == 'GOOD') {
            getObject('button_next').disabled = false;
        } else {
            getObject('button_next').disabled = true;
        }
    }

    function loadHealth() {
        var options = {};
        options['sourceUrl'] = 'ConfigGuide?processAction=ldapHealth';
        options['showRefresh'] = false;
        options['refreshTime'] = -1;
        options['finishFunction'] = function(){
            closeWaitDialog();
            checkIfNextEnabled();
        };
        showWaitDialog();
        showPwmHealth('healthBody', options);
    }
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
