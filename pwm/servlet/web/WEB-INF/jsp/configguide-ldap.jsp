<%@ page import="password.pwm.bean.servlet.ConfigGuideBean" %>
<%@ page import="password.pwm.servlet.ConfigGuideServlet" %>
<%@ page import="java.util.Map" %>
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
            <pwm:Display key="Title_ConfigGuide_ldap" bundle="Config"/>
        </div>
    </div>
    <div id="centerbody">
        <form id="widgetForm" name="widgetForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <div id="outline_ldap-server" class="setting_outline">
                <div id="titlePaneHeader-ldap-server" class="setting_title">LDAP Server</div>
                <div class="setting_body">
                    Enter the connection information for your ldap server.
                    <div class="setting_body">
                        <b>LDAP Hostname / Server Address</b>
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" value="<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>"/>
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                    new ValidationTextBox({
                                        required: true,
                                        style: "width: 550px",
                                        placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_HOST)%>',
                                        onChange: function() {
                                            handleFormActivity();
                                        },
                                        onKeyUp: function() {
                                            handleFormActivity();
                                        },
                                        value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>'
                                    }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>");
                                });
                            });
                        </script>
                        <table style="border:0; padding:0; margin-top: 10px">
                            <tr style="border:0; padding:0">
                                <td style="border:0; padding: 0; width: 30%">
                                    <b>LDAP Port</b>
                                </td>
                                <td style="border:0; padding: 0">
                                    <b>Secure (SSL) Connection</b>
                                </td>
                            </tr>
                            <tr style="border:0; padding:0">
                                <td style="border:0; padding:0">
                                    <span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                                    <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" value="<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%>"/>
                                    <script type="text/javascript">
                                        PWM_GLOBAL['startupFunctions'].push(function(){
                                            require(["dijit/registry","dijit/form/NumberSpinner"],function(registry,NumberSpinner){
                                                new NumberSpinner({
                                                    id: 'widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>',
                                                    required: true,
                                                    style: "width: 70px",
                                                    placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_PORT)%>',
                                                    constraints:{min:1,max:65535,places:0,pattern:'#'},
                                                    onKeyUp: function() {
                                                        handleFormActivity();
                                                    },
                                                    onChange: function() {
                                                        handleFormActivity();
                                                    },
                                                    value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%>'
                                                }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>");
                                            });
                                        });
                                    </script>
                                </td>
                                <td style="border:0; padding:0">
                                    <span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                                    <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>"/>
                                    <script type="text/javascript">
                                        PWM_GLOBAL['startupFunctions'].push(function(){
                                            require(["dijit/registry","dijit/form/ToggleButton"],function(registry,ToggleButton){
                                                new ToggleButton({
                                                    iconClass:'dijitCheckBoxIcon',
                                                    label: 'Secure',
                                                    style: 'width:100px',
                                                    onChange: function() {
                                                        console.log('onchange trigger!');
                                                        getObject('widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').value = this.checked ? '636' : '389';
                                                        handleFormActivity();
                                                        if (!this.checked) {
                                                            showConfirmDialog(null,'<pwm:Display key="Confirm_SSLDisable" bundle="Config"/>', null, function(){
                                                                registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').set('checked','true');
                                                            });
                                                        }
                                                    },
                                                    checked: <%="true".equalsIgnoreCase(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_SECURE))%>
                                                },'widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>');
                                            });
                                        });
                                    </script>
                                </td>
                            </tr>
                        </table>
                    </div>
                </div>
            </div>
            <br/>
            <div id="outline_ldap-user" class="setting_outline">
                <div id="titlePaneHeader-ldap-credentials" class="setting_title">
                    LDAP Credentials
                </div>
                <div class="setting_body">
                    Enter the credentials for your ldap server.  You must enter the fully qualified LDAP DN of the
                    admin account here.  In most cases, you should use an account created specially for this purpose, with sufficient rights to
                    administer the users that will be logging into this system.
                    <div class="setting_item">
                        <b>Proxy/Admin LDAP DN</b>
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>"/>
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                    new ValidationTextBox({
                                        required: true,
                                        style: "width: 550px",
                                        placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_ADMIN_DN)%>',
                                        onKeyUp: function() {
                                            handleFormActivity();
                                        },
                                        value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_ADMIN_DN)%>'
                                    }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>");
                                });
                            });
                        </script>
                    </div>
                    <div class="setting_item">
                        <b>Password</b>
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" type="password"/>
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                    new ValidationTextBox({
                                        required: true,
                                        type: "password",
                                        style: "width: 200px",
                                        placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_ADMIN_PW)%>',
                                        onKeyUp: function() {
                                            handleFormActivity();
                                        },
                                        onChange: function() {
                                            handleFormActivity();
                                        },
                                        value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_ADMIN_PW)%>'
                                    }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>");
                                });
                            });
                        </script>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="healthBody" style="border:0; margin:0; padding:0" onclick="loadHealth();">
            <div style="text-align: center">
                <a class="menubutton" style="max-width: 100px; margin-left: auto; margin-right: auto">Check Settings</a>
            </div>
        </div>
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="gotoStep('TEMPLATE');"><pwm:Display key="Button_Previous" bundle="Config"/></button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="gotoStep('LDAPCERT');"><pwm:Display key="Button_Next" bundle="Config"/></button>
        </div>
    </div>
    <div class="push"></div>
</div>
<form id="configForm" name="configForm">
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" id="<%=ConfigGuideServlet.PARAM_LDAP_HOST%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" id="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" id="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" id="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" id="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>"/>
</form>
<script type="text/javascript">
    function handleFormActivity() {
        require(["dijit/registry"],function(registry){
            getObject('<%=ConfigGuideServlet.PARAM_LDAP_HOST%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>').get('value');
            getObject('<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').get('value');
            getObject('<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').checked;
            getObject('<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>').get('value');
            getObject('<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>').get('value');
            updateForm();
            clearHealthDiv();
        });
    }

    function clearHealthDiv() {
        var healthBodyObj = getObject('healthBody');
        var newHtml = '<div style="text-align: center">';
        newHtml += '<a class="menubutton" style="max-width: 100px; margin-left: auto; margin-right: auto">Check Settings</a>';
        newHtml += '</div>';
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
        if (PWM_GLOBAL['pwm-health'] === 'GOOD') {
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
