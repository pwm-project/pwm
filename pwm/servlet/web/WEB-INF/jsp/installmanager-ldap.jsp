<%@ page import="password.pwm.bean.InstallManagerBean" %>
<%@ page import="password.pwm.servlet.InstallManagerServlet" %>
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
<% InstallManagerBean installManagerBean = (InstallManagerBean)PwmSession.getPwmSession(session).getSessionBean(InstallManagerBean.class);%>
<% Map<String,String> DEFAULT_FORM = InstallManagerServlet.defaultForm(installManagerBean.getStoredConfiguration().getTemplate()); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/installmanager.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/configeditor.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_InstallManager" bundle="Config"/>
        </div>
        <div id="header-title">
            LDAP Configuration
        </div>
    </div>
    <div id="centerbody">
        <form id="ldapForm" data-dojo-type="dijit/form/Form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
                <div id="titlePaneHeader-ldap-server" title="Server Information" style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="open:false">
                    Please enter the connection information for your ldap server.
                </div>
                <div style="padding-left: 10px; padding-bottom: 5px">
                <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                    LDAP Hostname / Server Address
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="value_<%=InstallManagerServlet.PARAM_LDAP_HOST%>" name="setting_<%=InstallManagerServlet.PARAM_LDAP_HOST%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=InstallManagerServlet.PARAM_LDAP_HOST%>',
                                    name: '<%=InstallManagerServlet.PARAM_LDAP_HOST%>',
                                    required: true,
                                    style: "width: 300px",
                                    placeholder: '<%=DEFAULT_FORM.get(InstallManagerServlet.PARAM_LDAP_HOST)%>',
                                    onKeyUp: function() {
                                        updateLdapForm();
                                    },
                                    value: '<%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP_HOST)%>'
                                }, "value_<%=InstallManagerServlet.PARAM_LDAP_HOST%>");
                            });
                        });
                    </script>
                </div>

                <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_PORT%>" style="padding-left: 5px; padding-top: 5px">
                    LDAP Port
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="<%=InstallManagerServlet.PARAM_LDAP_PORT%>" name="<%=InstallManagerServlet.PARAM_LDAP_PORT%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/NumberSpinner"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=InstallManagerServlet.PARAM_LDAP_PORT%>',
                                    name: '<%=InstallManagerServlet.PARAM_LDAP_PORT%>',
                                    required: true,
                                    style: "width: 80px",
                                    placeholder: '<%=DEFAULT_FORM.get(InstallManagerServlet.PARAM_LDAP_PORT)%>',
                                    onKeyUp: function() {
                                        updateLdapForm();
                                    },
                                    value: '<%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP_PORT)%>'
                                }, "<%=InstallManagerServlet.PARAM_LDAP_PORT%>");
                            });
                        });
                    </script>
                </div>
                <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_SECURE%>" style="padding-left: 5px; padding-top: 5px">
                    Secure Connection
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="<%=InstallManagerServlet.PARAM_LDAP_SECURE%>" name="<%=InstallManagerServlet.PARAM_LDAP_SECURE%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/registry","dijit/form/CheckBox"],function(registry,CheckBox){
                                new CheckBox({
                                    id: '<%=InstallManagerServlet.PARAM_LDAP_SECURE%>',
                                    name: '<%=InstallManagerServlet.PARAM_LDAP_SECURE%>',
                                    required: true,
                                    placeholder: '<%=DEFAULT_FORM.get(InstallManagerServlet.PARAM_LDAP_SECURE)%>',
                                    onChange: function() {
                                        if (this.checked) {
                                            registry.byId('<%=InstallManagerServlet.PARAM_LDAP_PORT%>').set('value','636');
                                        } else {
                                            registry.byId('<%=InstallManagerServlet.PARAM_LDAP_PORT%>').set('value','389');
                                        }
                                        updateLdapForm();
                                    },
                                    checked: <%=("on".equalsIgnoreCase(installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP_SECURE))) ? "true" : "false"%>
                                }, "<%=InstallManagerServlet.PARAM_LDAP_SECURE%>");
                            });
                        });
                    </script>
                </div>
                </div>
            </div>

            <br class="clear"/>
            <div id="outline_ldap-user" style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
                <div id="titlePaneHeader-ldap-user" style="width:580px" data-dojo-type="dijit/TitlePane" title="Admin/Proxy Account" data-dojo-props="open:false">
                    Please enter the connection information for your proxy account information.  You must enter the full LDAP DN of the
                    admin account here.  In most cases, you should use an account created specially for this purpose, with enough rights to
                    administer the users that will be logging into this system.
                </div>
                <div style="padding-left: 10px; padding-bottom: 5px">
                <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_ADMIN_DN%>" style="padding-left: 5px; padding-top: 5px">
                    Proxy/Admin LDAP DN
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="<%=InstallManagerServlet.PARAM_LDAP_ADMIN_DN%>" name="setting_<%=InstallManagerServlet.PARAM_LDAP_ADMIN_DN%>"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=InstallManagerServlet.PARAM_LDAP_ADMIN_DN%>',
                                    name: '<%=InstallManagerServlet.PARAM_LDAP_ADMIN_DN%>',
                                    required: true,
                                    style: "width: 500px",
                                    placeholder: '<%=DEFAULT_FORM.get(InstallManagerServlet.PARAM_LDAP_ADMIN_DN)%>',
                                    onKeyUp: function() {
                                        updateLdapForm();
                                    },
                                    value: '<%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP_ADMIN_DN)%>'
                                }, "<%=InstallManagerServlet.PARAM_LDAP_ADMIN_DN%>");
                            });
                        });
                    </script>
                </div>

                <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_ADMIN_PW%>" style="padding-left: 5px; padding-top: 5px">
                    Password
                    <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                    <input id="<%=InstallManagerServlet.PARAM_LDAP_ADMIN_PW%>" name="<%=InstallManagerServlet.PARAM_LDAP_ADMIN_PW%>" type="password"/>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                new ValidationTextBox({
                                    id: '<%=InstallManagerServlet.PARAM_LDAP_ADMIN_PW%>',
                                    name: '<%=InstallManagerServlet.PARAM_LDAP_ADMIN_PW%>',
                                    required: true,
                                    type: "password",
                                    style: "width: 200px",
                                    placeholder: '<%=DEFAULT_FORM.get(InstallManagerServlet.PARAM_LDAP_ADMIN_PW)%>',
                                    onKeyUp: function() {
                                        updateLdapForm();
                                    },
                                    value: '<%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP_ADMIN_PW)%>'
                                }, "<%=InstallManagerServlet.PARAM_LDAP_ADMIN_PW%>");
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
                <button class="menubutton" onclick="enhanceHealthDiv()">Check Settings</button>
            </div>
        </div>
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="gotoStep('TEMPLATE')"> << Previous <<</button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="gotoStep('LDAPCERT')">>> Next >></button>
        </div>
    </div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });
        checkIfNextEnabled();
    });

    function checkIfNextEnabled() {
        if (PWM_GLOBAL['pwm-health'] == 'GOOD') {
            getObject('button_next').disabled = false;
        } else {
            getObject('button_next').disabled = true;
        }
    };

    function enhanceHealthDiv() {
        require(["dojo/domReady!","dijit/Tooltip"],function(dojo,Tooltip){
            new Tooltip({
                connectId: ["healthBody"],
                label: 'click to refresh'
            });
        });
        /*
        require(["dojo/_base/fx","dojo/mouse", "dojo/on", "dojo/dom"], function(fx, mouse, on, dom){
            on(dom.byId("healthBody"), mouse.enter, function(evt){
                fx.fadeOut({node:'healthBody'}).play();
            });
            on(dom.byId("healthBody"), mouse.leave, function(evt){
                fx.fadeIn({node:'healthBody'}).play();
            });
        });
        */
    }

    function loadHealth() {
        var options = {};
        options['sourceUrl'] = 'InstallManager?processAction=ldapHealth';
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
