<%@ page import="password.pwm.bean.InstallManagerBean" %>
<%@ page import="password.pwm.servlet.InstallManagerServlet" %>
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
<% InstallManagerBean installManagerBean = (InstallManagerBean)PwmSession.getPwmSession(session).getSessionBean(InstallManagerBean.class);%>
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
            LDAP Certificates
        </div>
    </div>
    <div id="centerbody">
        <% if (installManagerBean.getLdapCertificates() == null) { %>
        <div>
            LDAP Server connection is not configured to be secure.  If you wish to secure the connection between
            this system and your LDAP server, return to the previous page and enable secure connections.
        </div>
        <% } else { %>
        <form id="formData" data-dojo-type="dijit/form/Form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
                <div id="titlePaneHeader-ldap-server" title="LDAP Server Certificates for <%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP_HOST)%>:<%=installManagerBean.getFormData().get(InstallManagerServlet.PARAM_LDAP_PORT)%>" style="width:580px" data-dojo-type="dijit/TitlePane" data-dojo-props="open:false">
                    Please verify the certificates below match those installed on your LDAP server.
                </div>
                <div style="padding: 5px; padding-left: 10px;">
                    <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                        <% for (X509Certificate certificate : installManagerBean.getLdapCertificates()) {%>
                        <% request.setAttribute("certificate",certificate); %>
                        <jsp:include page="fragment/setting-certificate.jsp"/>
                        <br/>
                        <% } %>
                    </div>
                </div>
            </div>


            <br class="clear"/>
            <div id="outline_ldapcert-options" style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
                <div id="titlePaneHeader-ldap-user" style="width:580px" data-dojo-type="dijit/TitlePane" title="Certificate Trust Options" data-dojo-props="open:false">
                    &nbsp;
                </div>
                <div style="padding-left: 10px; padding-bottom: 5px">
                    <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_ADMIN_DN%>" style="padding-left: 5px; padding-top: 5px">
                        Certificate(s) are trusted by default keystore
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <button id="button_defaultTrustStore">Enabled</button> (Import/remove certificate manually into java keystore to change)
                    </div>
                    <div id="titlePane_<%=InstallManagerServlet.PARAM_LDAP_ADMIN_PW%>" style="padding-left: 5px; padding-top: 5px">
                        Use application configuration to manage certificate(s) and import certificates into configuration
                        <br/><span>&nbsp;<%="\u00bb"%>&nbsp;&nbsp;</span>
                        <button id="button_useConfig">Enabled</button>
                        <% if (!installManagerBean.isUseConfiguredCerts()) { %>
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
                                checked: <%=installManagerBean.isCertsTrustedbyKeystore()%>
                            },'button_defaultTrustStore');

                            new ToggleButton({
                                id: 'button_useConfig',
                                iconClass:'dijitCheckBoxIcon',
                                showLabel: 'Enabled',
                                checked: <%=installManagerBean.isUseConfiguredCerts()%>,
                                onChange: function(){
                                    setUseConfiguredCerts(<%=!installManagerBean.isUseConfiguredCerts()%>);
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
            <button class="btn" onclick="gotoStep('LDAP')"><< Previous <<</button>
            <button class="btn" onclick="gotoStep('LDAP2')">>> Next >></button>
        </div>
    </div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
