<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.http.PwmRequest" %>
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
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ConfigGuideBean configGuideBean = (ConfigGuideBean) PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request,response); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                Save Configuration
            </div>
        </div>
    </div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>The installation process is now complete.  You can go back to any previous step if you would like to make changes, or click
            <i>Save Configuration</i> to save the configuration and restart the application.</p>
        <br/>
        <div id="outline_ldap-server" class="setting_outline">
            <div id="titlePaneHeader-ldap-server" class="setting_title">Summary</div>
            <div class="setting_body">
                <table>
                    <tr>
                        <td><b>Template</b>
                        </td>
                        <td>
                            <%=StringEscapeUtils.escapeHtml(configGuideBean.getStoredConfiguration().getTemplate().getLabel(pwmRequest.getLocale()))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Server Hostname</b>
                        </td>
                        <td>
                            <%=StringEscapeUtils.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Port</b>
                        </td>
                        <td>
                            <%=StringEscapeUtils.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Secure (SSL) Connection</b>
                        </td>
                        <td>
                            <%if (Boolean.parseBoolean(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_SECURE))) {%>
                            <pwm:Display key="Value_True"/>
                            <% } else { %>
                            <pwm:Display key="Value_False"/>
                            <% } %>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Proxy/Admin LDAP DN</b>
                        </td>
                        <td>
                            <%=StringEscapeUtils.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_ADMIN_DN))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Contextless Login Root</b>
                        </td>
                        <td>
                            <%=StringEscapeUtils.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP2_CONTEXT))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Administrator Search Filter</b>
                        </td>
                        <td>
                            <%=StringEscapeUtils.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP2_ADMINS))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>LDAP Test User DN</b>
                        </td>
                        <td>
                            <%=StringEscapeUtils.escapeHtml(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP2_TEST_USER))%>
                        </td>
                    </tr>
                    <tr>
                        <td><b>Response Storage Preference</b>
                        </td>
                        <td>
                            <% if ("LDAP".equals(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_CR_STORAGE_PREF))) { %>
                            LDAP
                            <% } else if ("DB".equals(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_CR_STORAGE_PREF))) { %>
                            Remote Database
                            <% } else if ("LOCALDB".equals(configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_CR_STORAGE_PREF))) { %>
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
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="PWM_GUIDE.gotoStep('PASSWORD');">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:Display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="PWM_GUIDE.gotoStep('FINISH');">
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
        });
    </script>
</pwm:script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
