<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<% ConfigGuideBean configGuideBean = (ConfigGuideBean) PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<% boolean enableNext = configGuideBean.isCertsTrustedbyKeystore() || configGuideBean.isUseConfiguredCerts() || configGuideBean.getLdapCertificates() == null; %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                <pwm:display key="Title_ConfigGuide_ldapcert" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <% if (configGuideBean.getLdapCertificates() == null) { %>
        <div>
            <pwm:display key="Display_ConfigGuideNotSecureLDAP" bundle="Config"/>
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
                    The following are the LDAP server certificates read from the server at
                    <style></style><b><%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>:<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%></b>.
                    Please verify these certificates match your LDAP server.
                    <div>
                        <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                            <% request.setAttribute("certificate",configGuideBean.getLdapCertificates()); %>
                            <% request.setAttribute("hideActions","true"); %>
                            <jsp:include page="fragment/setting-certificate.jsp"/>
                        </div>
                    </div>
                </div>
            </div>
            <br class="clear"/>
            <div id="outline_ldapcert-options" class="setting_outline">
                <div class="setting_title">Certificate Settings</div>
                <div class="setting_body">
                    <div style="padding-left: 5px; padding-top: 5px">
                        At least one of the following options must be selected to continue.
                    </div>
                    <br/>
                    <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" style="padding-left: 5px; padding-top: 5px">
                        Certificate(s) are trusted by default Java keystore
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <button id="button_defaultTrustStore">Enabled</button> (Import/remove certificate manually into Java keystore to change)
                    </div>
                    <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" style="padding-left: 5px; padding-top: 5px">
                        Use application configuration to manage certificate(s) and import certificates into configuration file
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <button id="button_useConfig">Enabled</button>
                    </div>
                </div>
                <pwm:script>
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
                                    PWM_GUIDE.setUseConfiguredCerts(<%=!configGuideBean.isUseConfiguredCerts()%>);
                                }
                            },'button_useConfig');
                        });
                    });
                </script>
                </pwm:script>
            </div>
        </form>
        <% } %>
        <br/>
        <div id="buttonbar">
            <button class="btn" onclick="PWM_GUIDE.gotoStep('LDAP');">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" onclick="PWM_GUIDE.gotoStep('LDAP2');"<%=enableNext?"":" disabled=\"disabled\""%>>
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:display key="Button_Next" bundle="Config"/>
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
</pwm:script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
