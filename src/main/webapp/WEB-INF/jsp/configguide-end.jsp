<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>The installation process is now complete.  You can go back to any previous step if you would like to make changes, or click
            <i>Save Configuration</i> to save the configuration and restart the application.</p>
        <br/>
        <div id="outline_ldap-server" class="setting_outline">
            <div id="titlePaneHeader-ldap-server" class="setting_title">Configuration Summary</div>
            <div class="setting_body">
                <table>
                    <tr>
                        <td><b>LDAP Template</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(PwmSetting.TEMPLATE_LDAP.getOptions().get(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP)))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Site URL</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_APP_SITEURL))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Server Hostname</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_HOST))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Port</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_PORT))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Secure (SSL) Connection</b>
                        </td>
                        <td>
                            <%if (Boolean.parseBoolean(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_SECURE))) {%>
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
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_PROXY_DN))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Contextless Login Root</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(
                                    configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_CONTEXT))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Administrator Group DN</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_ADMIN_GROUP))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Test User DN</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_LDAP_TEST_USER))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Response Storage Preference</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(PwmSetting.TEMPLATE_STORAGE.getOptions().get(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_TEMPLATE_STORAGE)))%>
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
