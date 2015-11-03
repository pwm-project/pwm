<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.StringUtil" %>
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
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ConfigGuideBean configGuideBean = (ConfigGuideBean) JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request,response); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
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
                        <td><b>Template</b>
                        </td>
                        <td>
                            <%=StringUtil.escapeHtml(configGuideBean.getStoredConfiguration().getTemplate().getLabel(pwmRequest.getLocale()))%>
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
                            <% if ("LDAP".equals(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_CR_STORAGE_PREF))) { %>
                            LDAP
                            <% } else if ("DB".equals(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_CR_STORAGE_PREF))) { %>
                            Remote Database
                            <% } else if ("LOCALDB".equals(configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_CR_STORAGE_PREF))) { %>
                            Local Embedded Database (Testing only)
                            <% } else { %>
                            Not Configured
                            <% } %>
                        </td>
                    </tr>
                </table>
            </div>
        </div>
        <br/>
        <div class="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-save"></span></pwm:if>
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
                var htmlBody = '<p>After saving the configuration, the application will be automatically restarted.</p>'
                        + '<p>The application will then be in open configuration mode.  While in open configuration mode, the configuration can be accessed '
                        + 'without LDAP authentication.  Once you have completed any LDAP configuration changes you may wish to make, close the configuration so that '
                        + 'LDAP authentication will be required. </p>';

                htmlBody += '<br/><br/><table><tr><td colspan="3" class="title">URLs</td></tr>';
                htmlBody += '<tr><td class="key">Application</td><td> <a href="<pwm:context/>"><pwm:context/></a></td></tr>';
                htmlBody += '<tr><td class="key">Configuration Manager</td><td> <a href="<pwm:context/>/private/config/ConfigManager"><pwm:context/>/private/config/ConfigManager</a></td></tr>';
                htmlBody += '<tr><td class="key">Configuration Editor</td><td> <a href="<pwm:context/>/private/config/ConfigEditor"><pwm:context/>/private/config/ConfigEditor</a></td></tr>';
                htmlBody += '</table>';

                PWM_MAIN.showConfirmDialog({text:htmlBody,okAction:function(){
                    PWM_GUIDE.gotoStep('FINISH');
                }});
            });
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
