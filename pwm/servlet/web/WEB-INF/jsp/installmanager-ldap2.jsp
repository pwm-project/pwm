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
            Additional LDAP Settings
        </div>
    </div>
    <div id="centerbody">
        <form id="ldap2Form" data-dojo-type="dijit/form/Form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
                <div id="titlePaneHeader-ldap-server" title="LDAP Root Context" style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="open:true">
                    Please enter the top level ldap context for your ldap directory.  This is the top level ldap container that your users exist under.  If
                    you need to enter multiple values, you can do so after the wizard is complete.
                </div>
                <div style="padding-left: 10px; padding-bottom: 5px">
                    <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP2_CONTEXT%>" style="padding-left: 5px; padding-top: 5px">
                        LDAP Contextless Login Root
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <input id="value_<%=InstallManagerServlet.PARAM_LDAP2_CONTEXT%>" name="setting_<%=InstallManagerServlet.PARAM_LDAP2_CONTEXT%>"/>
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                    new ValidationTextBox({
                                        id: '<%=InstallManagerServlet.PARAM_LDAP2_CONTEXT%>',
                                        name: '<%=InstallManagerServlet.PARAM_LDAP2_CONTEXT%>',
                                        required: true,
                                        style: "width: 300px",
                                        placeholder: '<%=DEFAULT_FORM.get(InstallManagerServlet.PARAM_LDAP2_CONTEXT)%>',
                                        onKeyUp: function() {
                                            updateLdap2Form();
                                        },
                                        value: '<%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP2_CONTEXT)%>'
                                    }, "value_<%=InstallManagerServlet.PARAM_LDAP2_CONTEXT%>");
                                });
                            });
                        </script>
                    </div>
                </div>
            </div>
            <br/>
            <div id="outline_ldap" style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
                <div id="titlePaneHeader-ldap" title="Test User Account" style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="open:true">
                    Please enter the LDAP DN of a test user account.  You will need to create a new test user account for this purpose.  This test user account should be created with the same privledges and policies
                    as a typical user in your system.  This application will modify the password and perform other operations against the test user account to
                    validate the configuration and health of both the LDAP server and this server.
                    <br/>
                    This setting is recommend, but optional.  If you do not wish to configure an ldap test user at this time, you can leave this setting blank.

                </div>
                <div style="padding-left: 10px; padding-bottom: 5px">
                    <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP2_TEST_USER%>" style="padding-left: 5px; padding-top: 5px">
                        LDAP Test User DN
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <input id="value_<%=InstallManagerServlet.PARAM_LDAP2_TEST_USER%>" name="setting_<%=InstallManagerServlet.PARAM_LDAP2_TEST_USER%>"/>
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                    new ValidationTextBox({
                                        id: '<%=InstallManagerServlet.PARAM_LDAP2_TEST_USER%>',
                                        name: '<%=InstallManagerServlet.PARAM_LDAP2_TEST_USER%>',
                                        required: false,
                                        style: "width: 300px",
                                        placeholder: '<%=DEFAULT_FORM.get(InstallManagerServlet.PARAM_LDAP2_TEST_USER)%>',
                                        onKeyUp: function() {
                                            updateLdap2Form();
                                        },
                                        value: '<%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP2_TEST_USER)%>'
                                    }, "value_<%=InstallManagerServlet.PARAM_LDAP2_TEST_USER%>");
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
            <button class="btn" id="button_previous" onclick="gotoStep('LDAPCERT')"> << Previous <<</button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="gotoStep('END')">>> Next >></button>
        </div>
    </div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });
        updateLdap2Form();
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
