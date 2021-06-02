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


<%@ page import="password.pwm.PwmEnvironment" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.util.secure.PwmHashAlgorithm" %>
<%@ page import="password.pwm.util.secure.SecureEngine" %>
<%@ page import="password.pwm.util.secure.X509Utils" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <% if (configGuideBean.getLdapCertificates() == null) { %>
        <div>
            <pwm:display key="Display_ConfigGuideNotSecureLDAP" bundle="Config"/>
        </div>
        <% } else { %>
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    LDAP Server Certificates
                </div>
                <div class="setting_body">
                    <% final String serverInfo = configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_HOST) + ":" + configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_LDAP_PORT); %>
                    <pwm:display key="ldap_cert_description" bundle="ConfigGuide" value1="<%=serverInfo%>"/>
                    <div>
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                            <% int counter=0;for (final X509Certificate certificate : configGuideBean.getLdapCertificates()) {%>
                            <% final String md5sum = SecureEngine.hash(new ByteArrayInputStream(certificate.getEncoded()), PwmHashAlgorithm.MD5); %>
                            <% final String sha1sum = SecureEngine.hash(new ByteArrayInputStream(certificate.getEncoded()), PwmHashAlgorithm.SHA1); %>
                            <table style="width:100%" id="table_certificate0">
                                <tr><td colspan="2" class="key" style="text-align: center">
                                    Certificate <%=counter%>&nbsp;<a style="font-size: smaller" href="#" id="button-showCert_<%=md5sum%>">(details)</a>
                                </td></tr>
                                <tr><td>Subject Name</td><td><div class="setting_table_value"><%=certificate.getSubjectX500Principal().getName()%></div></td></tr>
                                <tr><td>Issuer Name</td><td><div class="setting_table_value"><%=certificate.getIssuerX500Principal().getName()%></div></td></tr>
                                <% final String serialNum = X509Utils.hexSerial(certificate); %>
                                <tr><td>Serial Number</td><td><div class="setting_table_value"><%=serialNum%></div></td></tr>
                                <tr><td>Issue Date</td><td><div class="setting_table_value timestamp"><%=JavaHelper.toIsoDate(certificate.getNotBefore())%></div></td></tr>
                                <tr><td>Expire Date</td><td><div class="setting_table_value timestamp"><%=JavaHelper.toIsoDate(certificate.getNotAfter())%></div></td></tr>
                            </table>
                            <pwm:script>
                                <script type="text/javascript">
                                    PWM_GLOBAL['startupFunctions'].push(function(){
                                        PWM_MAIN.addEventHandler('button-showCert_<%=md5sum%>','click',function(){
                                            var body = '<pre style="white-space: pre-wrap; word-wrap: break-word">';
                                            body += 'md5sum: <%=md5sum%>\n';
                                            body += 'sha1sum: <%=sha1sum%>\n';
                                            body += '<%=StringUtil.escapeJS(certificate.toString())%>';
                                            body += '</pre>';
                                            PWM_MAIN.showDialog({
                                                title: "Certificate <%=counter%> Detail",
                                                text: body,
                                                showClose: true,
                                                showOk: false,
                                                dialogClass:'wide'
                                            });
                                        });
                                    });
                                </script>
                            </pwm:script>
                            <% counter++; } %>
                        </div>
                    </div>
                </div>
            </div>
            <br class="clear"/>
            <div id="outline_ldapcert-options" class="setting_outline">
                <div class="setting_title">Certificate Settings</div>
                <div class="setting_body">
                    <% if (!JspUtility.getPwmRequest(pageContext).getPwmApplication().getPwmEnvironment().getFlags().contains(PwmEnvironment.ApplicationFlag.Appliance)) { %>
                    <div style="padding-left: 5px; padding-top: 5px">
                        At least one of the following options must be selected to continue.
                    </div>
                    <br/>
                    <div id="titlePane_<%=ConfigGuideFormField.PARAM_LDAP_PROXY_DN%>" style="padding-left: 5px; padding-top: 5px">
                        Certificate(s) are trusted by default Java keystore
                        <br/>
                        <label class="checkboxWrapper">
                            <input readonly disabled type="checkbox" id="defaultTrustStore" name="defaultTrustStore" <%=configGuideBean.isCertsTrustedbyKeystore() ? "checked" : ""%>/> Enabled
                        </label>
                        (Import/remove certificate manually into Java keystore to change)
                    </div>
                    <br/>
                    <% } %>
                    <div id="titlePane_useConfig" style="padding-left: 5px; padding-top: 5px">
                        Use application to manage certificate(s) and import certificates into configuration file
                        <br/>
                        <label class="checkboxWrapper">
                            <input type="checkbox" id="useConfig" name="useConfig" <%=configGuideBean.isUseConfiguredCerts() ? "checked" : ""%>/> Enabled
                        </label>

                    </div>
                </div>
            </div>
        </form>
        <% } %>
        <br/>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});
            PWM_MAIN.addEventHandler('configForm','input',function(){PWM_GUIDE.updateForm()});

            PWM_MAIN.addEventHandler('useConfig','change',function(){
                var checked = PWM_MAIN.getObject('useConfig').checked;
                PWM_GUIDE.setUseConfiguredCerts(checked);
                checkIfNextEnabled();
            });
            checkIfNextEnabled();
        });

        function checkIfNextEnabled() {
            var useConfigChecked = PWM_MAIN.getObject('useConfig').checked;
            var defaultTrustStoreChecked = PWM_MAIN.getObject('defaultTrustStore') && PWM_MAIN.getObject('defaultTrustStore').checked;

            if (useConfigChecked || defaultTrustStoreChecked) {
                PWM_MAIN.getObject('button_next').disabled = false;
            } else {
                PWM_MAIN.getObject('button_next').disabled = true;
            }
        }

    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
