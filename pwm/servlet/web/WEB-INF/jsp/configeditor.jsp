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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.servlet.ConfigEditorServlet" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<% request.setAttribute(PwmConstants.REQUEST_ATTR_HIDE_THEME,"true"); %>
<%@ include file="fragment/header.jsp" %>
<% final Collection<Locale> localeList = new ArrayList<Locale>(ContextManager.getPwmApplication(session).getConfig().getKnownLocales()); %>
<% localeList.remove(Helper.localeResolver(PwmConstants.DEFAULT_LOCALE, localeList)); %>
<% final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(request, response); %>
<% final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<% final password.pwm.config.PwmSetting.Category category = cookie.getCategory(); %>
<body class="nihilo">
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<script type="text/javascript">
    var PWM_VAR = PWM_VAR || {};
    PWM_VAR['configurationNotes'] = '<%=StringEscapeUtils.escapeJavaScript(configManagerBean.getConfiguration().readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_NOTES))%>';
    PWM_VAR['selectedTemplate'] = '<%=configManagerBean.getConfiguration().getTemplate().toString()%>';
    PWM_VAR['ldapProfileIds'] = [];
    <% for (final String id : (List<String>)configManagerBean.getConfiguration().readSetting(PwmSetting.LDAP_PROFILE_LIST).toNativeObject()) { %>
    PWM_VAR['ldapProfileIds'].push('<%=StringEscapeUtils.escapeJavaScript(id)%>');
    <% } %>
</script>
<div id="wrapper" style="border:1px; background-color: black">
    <div id="header" style="height: 25px; position: fixed">
        <div id="header-center">
            <div id="header-title">
                <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.SETTINGS) { %>
                <% if (category.getType() == PwmSetting.Category.Type.SETTING) { %>
                Settings - <%=category.getLabel(locale)%>
                <% } else if (category.getType() == PwmSetting.Category.Type.PROFILE) { %>
                Profile - <%=category.getLabel(locale)%>
                <% } else { %>
                Modules - <%=category.getLabel(locale)%>
                <% } %>
                <% } else { %>
                <% final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = cookie.getLocaleBundle(); %>
                Custom Text - <%=bundleName.getTheClass().getSimpleName()%>
                <% } %>
                <span style="visibility: hidden; color: orange" id="working_icon" class="headerIcon fa fa-spinner fa-spin"></span>
                <div class="headerIcon" style="float: right" id="cancelButton_icon" onclick="PWM_CFGEDIT.cancelEditing()">
                    <span class="fa fa-sign-out"></span>
                </div>
                <div class="headerIcon" style="float: right" id="saveButton_icon" onclick="PWM_CFGEDIT.saveConfiguration()">
                    <span class="fa fa-save"></span>
                </div>
                <div class="headerIcon" style="float: right" id="setPassword_icon" onclick="PWM_CFGEDIT.setConfigurationPassword()">
                    <span class="fa fa-key"></span>
                </div>
                <div class="headerIcon" style="float: right" id="searchButton_icon" onclick="PWM_CFGEDIT.searchDialog()">
                    <span class="fa fa-search"></span>
                </div>
                <script>
                    PWM_GLOBAL['startupFunctions'].push(function() {
                        PWM_MAIN.showTooltip({id:'cancelButton_icon',text:'Cancel',position:'below'});
                        PWM_MAIN.showTooltip({id:'saveButton_icon',text:'Save',position:'below'});
                        PWM_MAIN.showTooltip({id:'setPassword_icon',text:'Set Configuration Password',position:'below'});
                        PWM_MAIN.showTooltip({id:'searchButton_icon',text:'Search',position:'below'});
                    });
                </script>
            </div>
        </div>
    </div>
    <div id="TopMenu_Wrapper" class="menu-wrapper">
        <div id="TopMenu" class="menu-bar">
        </div>
        <div id="TopMenu_Underflow" class="menu-underflow"><%-- gradient for page to fade under menu --%>
        </div>
    </div>
    <div id="centerbody-config" class="centerbody-config">
        <div style="height: 45px">
        </div>
        <div id="mainContentPane" style="width: 600px">
            <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.SETTINGS || cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.PROFILE) { %>
            <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.PROFILE || category.getType() == PwmSetting.Category.Type.PROFILE) { %>
            <jsp:include page="<%=PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR_PROFILE.getPath()%>"/>
            <% } else { %>
            <jsp:include page="<%=PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR_SETTINGS.getPath()%>"/>
            <% } %>
            <% } else { %>
            <jsp:include page="<%=PwmConstants.JSP_URL.CONFIG_MANAGER_EDITOR_LOCALEBUNDLE.getPath()%>"/>
            <% } %>
        </div>
        <span style="display:none; visibility: hidden" id="message" class="message"></span>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['setting_alwaysFloatMessages'] = true;
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_CFGEDIT.initConfigEditor();
    });
</script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
