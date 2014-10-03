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

<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
<%@ page import="password.pwm.config.PwmSettingCategory" %>
<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.http.servlet.ConfigEditorServlet" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_THEME); %>
<%@ include file="fragment/header.jsp" %>
<% final Collection<Locale> localeList = new ArrayList<Locale>(ContextManager.getPwmApplication(session).getConfig().getKnownLocales()); %>
<% localeList.remove(LocaleHelper.localeResolver(PwmConstants.DEFAULT_LOCALE, localeList)); %>
<% final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(PwmRequest.forRequest(request, response)); %>
<% final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<% final PwmSettingCategory category = cookie.getCategory(); %>
<body class="nihilo">
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<pwm:script>
<script type="text/javascript">
    var PWM_VAR = PWM_VAR || {};
    PWM_VAR['configurationNotes'] = '<%=StringUtil.escapeJS(configManagerBean.getConfiguration().readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_NOTES))%>';
    PWM_VAR['selectedTemplate'] = '<%=configManagerBean.getConfiguration().getTemplate().toString()%>';
    PWM_VAR['ldapProfileIds'] = [];
    <% for (final String id : (List<String>)configManagerBean.getConfiguration().readSetting(PwmSetting.LDAP_PROFILE_LIST).toNativeObject()) { %>
    PWM_VAR['ldapProfileIds'].push('<%=StringUtil.escapeJS(id)%>');
    <% } %>
</script>
</pwm:script>
<div id="wrapper" style="border:1px;">
    <div id="header" style="height: 25px; position: fixed;">
        <div id="header-center">
            <div id="header-title">
                <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.SETTINGS) { %>
                <% if (category.getType() == PwmSettingCategory.Type.SETTING) { %>
                Settings - <%=category.getLabel(locale)%>
                <% } else if (category.hasProfiles()) { %>
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
                <pwm:script>
                <script>
                    PWM_GLOBAL['startupFunctions'].push(function() {
                        PWM_MAIN.showTooltip({id:'cancelButton_icon',text:'Cancel',position:'below'});
                        PWM_MAIN.showTooltip({id:'saveButton_icon',text:'Save',position:'below'});
                        PWM_MAIN.showTooltip({id:'setPassword_icon',text:'Set Configuration Password',position:'below'});
                        PWM_MAIN.showTooltip({id:'searchButton_icon',text:'Search',position:'below'});
                    });
                </script>
                </pwm:script>
            </div>
        </div>
    </div>
    <div id="TopMenu_Wrapper" class="menu-wrapper">
        <div id="TopMenu" class="menu-bar">
        </div>
        <div id="TopMenu_Underflow" class="menu-underflow">
        </div>
    </div>
    <div id="centerbody-config" class="centerbody-config" style="height:100%">
        <div style="height: 45px; width:100%">
        </div>
        <%--
        <div style="float: left; width:200px; background-color: white;">
            <% boolean selected = false; %>
            <% for (final PwmSettingCategory menuCategory : PwmSettingCategory.values()) { %>
            <a class="menubutton<%=selected?" selected":""%>" onclick="PWM_CFGEDIT.gotoSetting('<%=menuCategory.getKey()%>');">
                <%=menuCategory.getLabel(locale)%>
            </a>
            <% } %>
        </div>
        --%>
        <div id="mainContentPane" style="width: 600px;float:left; background-color: white">
            <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.SETTINGS || cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.PROFILE) { %>
            <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.PROFILE || category.hasProfiles()) { %>
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
    <br/><br/>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['setting_alwaysFloatMessages'] = true;
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_CFGEDIT.initConfigEditor();
    });
</script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/admin.js"/>"></script>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
