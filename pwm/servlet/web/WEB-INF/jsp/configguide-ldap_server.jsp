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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
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
        <form id="configForm" name="configForm">
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
                                <input class="configStringInput" id="<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" name="<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" value="<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>" <pwm:autofocus/> />
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
                                <input class="configNumericInput" type="number" id="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" name="<%=ConfigGuideServlet.PARAM_LDAP_PORT%>" value="<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%>"/>
                            </td>
                            <td>
                                <% boolean secureChecked = "true".equalsIgnoreCase(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_SECURE));%>
                                <label class="checkboxWrapper">
                                    <input type="checkbox" id="widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" name="nope" <%=secureChecked ? "checked" : ""%>/> Secure
                                    <input type="hidden" id="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" name="<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>" value="uninitialized"/>
                                </label>
                            </td>
                        </tr>
                    </table>
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
<pwm:script>
    <script type="text/javascript">
        function handleFormActivity() {
            PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').value =
                    PWM_MAIN.getObject('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').checked ? "true" : "false";
            PWM_GUIDE.updateForm();
            clearHealthDiv();
        }

        function clearHealthDiv() {
            PWM_MAIN.getObject('healthBody').innerHTML = PWM_VAR['originalHealthBody'];
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_VAR['originalHealthBody'] = PWM_MAIN.getObject('healthBody').innerHTML;
            clearHealthDiv();
            checkIfNextEnabled();

            PWM_MAIN.addEventHandler('configForm','input',function(){
                handleFormActivity();
            });

            PWM_MAIN.addEventHandler('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>','change',function() {
                if (!PWM_MAIN.getObject('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').checked) {
                    PWM_MAIN.showConfirmDialog({
                        text: PWM_CONFIG.showString('Confirm_SSLDisable'), cancelAction: function () {
                            PWM_MAIN.getObject('widget_<%=ConfigGuideServlet.PARAM_LDAP_SECURE%>').checked=true;
                            PWM_MAIN.closeWaitDialog();
                            handleFormActivity();

                        }
                    });
                }
                handleFormActivity();
            });

            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('healthBody','click',function(){loadHealth()});
        });

        function checkIfNextEnabled() {
            PWM_MAIN.getObject('button_next').disabled = PWM_GLOBAL['pwm-health'] !== 'GOOD';
        }

        function loadHealth() {
            console.log('loadHealth()');
            var options = {};
            options['sourceUrl'] = 'ConfigGuide?processAction=ldapHealth';
            options['showRefresh'] = false;
            options['refreshTime'] = -1;
            options['finishFunction'] = function(){
                PWM_MAIN.closeWaitDialog();
                checkIfNextEnabled();
            };
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                PWM_ADMIN.showAppHealth('healthBody', options);
            }});
        }
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
