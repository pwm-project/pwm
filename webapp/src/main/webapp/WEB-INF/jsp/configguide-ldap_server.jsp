<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <form id="configForm" name="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <div id="outline_ldap-server" class="setting_outline">
                <div id="titlePaneHeader-ldap-server" class="setting_title">
                    <pwm:display key="ldap_server_title" bundle="ConfigGuide"/>
                </div>
                <div class="setting_body">
                    <pwm:display key="ldap_server_description" bundle="ConfigGuide"/>
                    <br/><br/>
                    Example: <code><%=ConfigGuideForm.figureLdapHostnameExample(configGuideBean)%></code>
                    <br/><br/>
                    <table class="noborder" style="border-spacing: 0; padding: 0; margin: 0">
                        <tr>
                            <td colspan="2">
                                <label for="<%=ConfigGuideFormField.PARAM_LDAP_HOST%>">
                                <b><pwm:display key="ldap_server_title_hostname" bundle="ConfigGuide"/></b>
                                </label>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <input class="configStringInput" id="<%=ConfigGuideFormField.PARAM_LDAP_HOST%>" name="<%=ConfigGuideFormField.PARAM_LDAP_HOST%>" value="<%=configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_HOST)%>" <pwm:autofocus/> />
                            </td>
                        </tr>
                        <tr><td>&nbsp;</td></tr>
                        <tr>
                            <td style="width: 30%">
                                <label for="<%=ConfigGuideFormField.PARAM_LDAP_PORT%>">
                                    <b><pwm:display key="ldap_server_title_port" bundle="ConfigGuide"/></b>
                                </label>
                            </td>
                            <td style="">
                                <label for="widget_<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>">
                                    <b><pwm:display key="ldap_server_title_secure" bundle="ConfigGuide"/></b>
                                </label>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <input class="configNumericInput" type="number" min="0" max="65535" id="<%=ConfigGuideFormField.PARAM_LDAP_PORT%>" name="<%=ConfigGuideFormField.PARAM_LDAP_PORT%>" value="<%=configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_PORT)%>"/>
                            </td>
                            <td>
                                <% final boolean secureChecked = "true".equalsIgnoreCase(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_SECURE));%>
                                <label class="checkboxWrapper">
                                    <input type="checkbox" id="widget_<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>" name="nope" <%=secureChecked ? "checked" : ""%>/> Secure
                                    <input type="hidden" id="<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>" name="<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>" value="uninitialized"/>
                                </label>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>
        </form>
        <br/>
        <div id="healthBody" class="noborder nomargin nopadding" style="cursor: pointer">
            <div style="text-align: center">
                <button class="menubutton" style="margin-left: auto; margin-right: auto">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_CheckSettings" bundle="Config"/>
                </button>
            </div>
        </div>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function handleFormActivity() {
            PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>').value =
                    PWM_MAIN.getObject('widget_<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>').checked ? "true" : "false";
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

            PWM_MAIN.addEventHandler('widget_<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>','change',function() {
                if (!PWM_MAIN.getObject('widget_<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>').checked) {
                    PWM_MAIN.showConfirmDialog({
                        text: PWM_CONFIG.showString('Confirm_SSLDisable'),
                        cancelAction: function () {
                            PWM_MAIN.getObject('widget_<%=ConfigGuideFormField.PARAM_LDAP_SECURE%>').checked=true;
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
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
