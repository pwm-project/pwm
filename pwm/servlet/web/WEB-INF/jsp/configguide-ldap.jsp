<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
<%@ page import="java.util.Map" %>
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
<% ConfigGuideBean configGuideBean = (ConfigGuideBean) JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<% Map<String,String> DEFAULT_FORM = ConfigGuideServlet.defaultForm(configGuideBean.getStoredConfiguration().getTemplate()); %>
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
                <pwm:display key="Title_ConfigGuide_ldap" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <form id="widgetForm" name="widgetForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <div id="outline_ldap-server" class="setting_outline">
                <div id="titlePaneHeader-ldap-server" class="setting_title">
                    LDAP Server
                </div>
                <div class="setting_body">
                    Enter the connection information for your ldap server.  After the configuration wizard is completed you can enter additional servers.  Enter the actual address of your LDAP server; do not use a virtual address or proxy server address.
                    <br/>
                    <br/>
                    <table class="noborder" style="border-spacing: 0; padding: 0; margin: 0">
                        <tr>
                            <td colspan="2">
                                <b>LDAP Server Hostname</b>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <span class="fa fa-chevron-circle-right"></span>
                                <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" value="<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>"/>
                                <pwm:script>
                                    <script type="text/javascript">
                                        PWM_GLOBAL['startupFunctions'].push(function(){
                                            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                                new ValidationTextBox({
                                                    required: true,
                                                    style: "width: 520px",
                                                    placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_HOST)%>',
                                                    value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>'
                                                }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>");
                                            });
                                        });
                                    </script>
                                </pwm:script>
                            </td>
                        </tr>
                        <tr><td>&nbsp;</td></tr>
                        <tr>
                            <td style="width: 30%">
                                <b>LDAP Port</b>
                            </td>
                            <td style="">
                                <b>Secure (SSL) Connection</b>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <span class="fa fa-chevron-circle-right"></span>
                                <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" value="<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%>"/>
                                <pwm:script>
                                    <script type="text/javascript">
                                        PWM_GLOBAL['startupFunctions'].push(function(){
                                            require(["dijit/registry","dijit/form/NumberSpinner"],function(registry,NumberSpinner){
                                                new NumberSpinner({
                                                    id: 'widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>',
                                                    required: true,
                                                    style: "width: 70px",
                                                    placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_PORT)%>',
                                                    constraints:{min:1,max:65535,places:0,pattern:'#'},
                                                    value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%>'
                                                }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>");
                                            });
                                        });
                                    </script>
                                </pwm:script>
                            </td>
                            <td>
                                <span class="fa fa-chevron-circle-right"></span>
                                <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>"/>
                                <pwm:script>
                                    <script type="text/javascript">
                                        PWM_GLOBAL['startupFunctions'].push(function(){
                                            require(["dijit/registry","dijit/form/ToggleButton"],function(registry,ToggleButton){
                                                new ToggleButton({
                                                    iconClass:'dijitCheckBoxIcon',
                                                    label: 'Secure',
                                                    style: 'width:100px',
                                                    onChange: function() {
                                                        console.log('onchange trigger!');
                                                        PWM_MAIN.getObject('widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').value = this.checked ? '636' : '389';
                                                        if (!this.checked) {
                                                            PWM_MAIN.showConfirmDialog({text:'<pwm:display key="Confirm_SSLDisable" bundle="Config"/>', okAction:function() {
                                                                registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').set('checked', 'true');
                                                                handleFormActivity();
                                                            }});
                                                        }
                                                        handleFormActivity();
                                                    },
                                                    checked: <%="true".equalsIgnoreCase(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_SECURE))%>
                                                },'widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>');
                                            });
                                        });
                                    </script>
                                </pwm:script>
                            </td>
                        </tr>
                    </table>
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
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input id="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>"/>
                        <pwm:script>
                            <script type="text/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                        new ValidationTextBox({
                                            required: true,
                                            style: "width: 520px",
                                            placeholder: '<%=DEFAULT_FORM.get(ConfigGuideServlet.PARAM_LDAP_ADMIN_DN)%>',
                                            value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_ADMIN_DN)%>'
                                        }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>");
                                    });
                                });
                            </script>
                        </pwm:script>
                    </div>
                    &nbsp;<br/>
                    <div class="setting_item">
                        <b>Password</b>
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <input class="passwordfield" id="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" name="widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" type="<pwm:value name="passwordFieldType"/>"/>
                        <pwm:script>
                            <script type="text/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                        new ValidationTextBox({
                                            required: true,
                                            type: "password",
                                            style: "width: 200px",
                                            value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_ADMIN_PW)%>'
                                        }, "widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>");
                                    });
                                });
                            </script>
                        </pwm:script>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="healthBody" style="border:0; margin:0; padding:0; cursor: pointer">
            <div style="text-align: center">
                <button class="menubutton" style="margin-left: auto; margin-right: auto">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                    <pwm:display key="Button_CheckSettings" bundle="Config"/>
                </button>
            </div>
        </div>
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
<form id="configForm" name="configForm">
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" id="<%=ConfigGuideServlet.PARAM_LDAP_HOST%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" id="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" id="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" id="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>"/>
    <input type="hidden" name="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" id="<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>"/>
</form>
<pwm:script>
    <script type="text/javascript">
        function handleFormActivity() {
            require(["dijit/registry"],function(registry){
                PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_LDAP_HOST%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>').get('value');
                PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_PORT%>').get('value');
                PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').checked;
                PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>').get('value');
                PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>').value = registry.byId('widget_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>').get('value');
                PWM_GUIDE.updateForm();
                clearHealthDiv();
            });
        }

        function clearHealthDiv() {
            PWM_MAIN.getObject('healthBody').innerHTML = PWM_VAR['originalHealthBody'];
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_VAR['originalHealthBody'] = PWM_MAIN.getObject('healthBody').innerHTML;
            require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
                clearHealthDiv();
            });
            checkIfNextEnabled();

            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('healthBody','click',function(){loadHealth()});
            PWM_MAIN.addEventHandler('widgetForm','input',function(){handleFormActivity()});

        });

        function checkIfNextEnabled() {
            PWM_MAIN.getObject('button_next').disabled = PWM_GLOBAL['pwm-health'] !== 'GOOD';
        }

        function loadHealth() {
            var options = {};
            options['sourceUrl'] = 'ConfigGuide?processAction=ldapHealth';
            options['showRefresh'] = false;
            options['refreshTime'] = -1;
            options['finishFunction'] = function(){
                PWM_MAIN.closeWaitDialog();
                checkIfNextEnabled();
            };
            PWM_MAIN.showWaitDialog();
            PWM_ADMIN.showAppHealth('healthBody', options);
        }
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
