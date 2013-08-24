<%@ page import="password.pwm.bean.servlet.ConfigGuideBean" %>
<%@ page import="password.pwm.servlet.ConfigGuideServlet" %>
<%@ page import="java.security.cert.X509Certificate" %>
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
            <pwm:Display key="Title_ConfigGuide_ldapcert" bundle="Config"/>
        </div>
    </div>
    <div id="centerbody">
        <% if (configGuideBean.getLdapCertificates() == null) { %>
        <div>
            <pwm:Display key="Display_ConfigGuideNotSecureLDAP" bundle="Config"/>
        </div>
        <% } else { %>
        <form id="formData" data-dojo-type="dijit/form/Form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    LDAP Server Certificates
                </div>
                <div class="setting_body">
                    LDAP Server Certificates for <%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>:<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%>.
                    Please verify these certificates match your LDAP server.
                    <div>
                        <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                            <% for (X509Certificate certificate : configGuideBean.getLdapCertificates()) {%>
                            <% request.setAttribute("certificate",certificate); %>
                            <jsp:include page="fragment/setting-certificate.jsp"/>
                            <br/>
                            <% } %>
                        </div>
                    </div>
                </div>
            </div>
            <br class="clear"/>
            <div id="outline_ldapcert-options" class="setting_outline">
                <div class="setting_title">Certificate Settings</div>
                <div class="setting_body">
                    <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" style="padding-left: 5px; padding-top: 5px">
                        Certificate(s) are trusted by default keystore
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <button id="button_defaultTrustStore">Enabled</button> (Import/remove certificate manually into java keystore to change)
                    </div>
                    <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" style="padding-left: 5px; padding-top: 5px">
                        Use application configuration to manage certificate(s) and import certificates into configuration
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <button id="button_useConfig">Enabled</button>
                        <% if (!configGuideBean.isUseConfiguredCerts()) { %>
                        <span id="span_useConfig_unselected">(LDAP Promiscuous mode will be enabled)</span>
                        <% } %>
                    </div>
                </div>
                <script type="text/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        require(["dijit/form/ToggleButton"],function(ToggleButton){
                            new ToggleButton({
                                id: 'button_defaultTrustStore',
                                iconClass:'dijitCheckBoxIcon',
                                showLabel: 'Enabled',
                                disabled: true,
                                checked: <%=configGuideBean.isCertsTrustedbyKeystore()%>
                            },'button_defaultTrustStore');

                            new ToggleButton({
                                id: 'button_useConfig',
                                iconClass:'dijitCheckBoxIcon',
                                showLabel: 'Enabled',
                                checked: <%=configGuideBean.isUseConfiguredCerts()%>,
                                onChange: function(){
                                    setUseConfiguredCerts(<%=!configGuideBean.isUseConfiguredCerts()%>);
                                }
                            },'button_useConfig');
                        });
                    });
                </script>
            </div>
        </form>
        <% } %>
        <br/>
        <div id="buttonbar">
            <button class="btn" onclick="gotoStep('LDAP');"><pwm:Display key="Button_Previous" bundle="Config"></pwm:Display></button>
            <button class="btn" onclick="gotoStep('LDAP2');"><pwm:Display key="Button_Next" bundle="Config"></pwm:Display></button>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
