<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>The installation process is now complete.  You can go back to any previous step if you would like to make changes, or click
            <i>Save Configuration</i> to save the configuration and restart the application.</p>
            <p>To facilitate further configuration troubleshooting, the setting
                <code><%=PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS.toMenuLocationDebug(null, JspUtility.locale(request))%></code> will be
                enabled by default.  It should be disabled before the server is used in a production environment.
            </p>
        <br/>
        <div id="outline_ldap-server" class="setting_outline">
            <div id="titlePaneHeader-ldap-server" class="setting_title">Configuration Summary</div>
            <div class="setting_body">
                <table>
                    <tr>
                        <td><b>LDAP Template</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(PwmSetting.TEMPLATE_LDAP.getOptions().get(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_TEMPLATE_LDAP)))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Site URL</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_APP_SITEURL))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Server Hostname</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_HOST))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Port</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_PORT))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Secure (SSL) Connection</b>
                        </td>
                        <td>
                            <%if (ConfigGuideForm.readCheckedFormField(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_SECURE))) {%>
                            <pwm:display key="Value_True"/>
                            <% } else { %>
                            <pwm:display key="Value_False"/>
                            <% } %>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Proxy LDAP DN</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_PROXY_DN))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Contextless Login Root</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(
                                    configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_CONTEXT))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Administrator User DN</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_ADMIN_USER))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Test User DN</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_TEST_USER))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Response Storage Preference</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(PwmSetting.TEMPLATE_STORAGE.getOptions().get(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_TEMPLATE_STORAGE)))%>
                        </td>
                    </tr>
                </table>
            </div>
        </div>
        <br/>
        <div class="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-save"></span></pwm:if>
                Save Configuration
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){
                var htmlBody = '<p>After saving the configuration, the application will be automatically restarted.</p>';

                htmlBody += '<br/><br/><table><tr><td colspan="3" class="title">URLs</td></tr>';
                htmlBody += '<tr><td class="key">Application</td><td> <a href="<pwm:context/>"><pwm:context/></a></td></tr>';
                htmlBody += '<tr><td class="key">Configuration</td><td> <a href="<pwm:context/>/private/config"><pwm:context/>/private/config</a></td></tr>';
                htmlBody += '</table>';

                PWM_MAIN.showConfirmDialog({text:htmlBody,okAction:function(){
                    PWM_GUIDE.gotoStep('FINISH');
                }});
            });
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
