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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.servlet.ConfigEditorServlet" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<% request.setAttribute("noTheme","true"); %>
<%@ include file="fragment/header.jsp" %>
<% final Collection<Locale> localeList = new ArrayList<Locale>(ContextManager.getPwmApplication(session).getConfig().getKnownLocales()); %>
<% localeList.remove(Helper.localeResolver(PwmConstants.DEFAULT_LOCALE, localeList)); %>
<% final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(request, response); %>
<% final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<% final password.pwm.config.PwmSetting.Category category = cookie.getCategory(); %>
<body class="nihilo" onload="initConfigPage()">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<script type="text/javascript">
    PWM_GLOBAL['configurationNotes'] = '<%=StringEscapeUtils.escapeJavaScript(configManagerBean.getConfiguration().readConfigProperty(StoredConfiguration.PROPERTY_KEY_NOTES))%>';
    PWM_GLOBAL['selectedTemplate'] = '<%=configManagerBean.getConfiguration().getTemplate().toString()%>';
</script>
<style type="text/css">
    #centerbody-config {
        width: 600px;
        min-width: 600px;
        padding: 10px;
        position: relative;
        margin-left: auto;
        margin-right: auto;
        margin-top: 0;
        clear: both;
        border-radius: 0 0 5px 5px;
        box-shadow: 0;
        background-color: white;
    }
</style>
<div id="wrapper" style="border:1px; background-color: black">
<div id="header">
    <div id="header-company-logo"></div>
    <div id="header-page">
        <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.SETTINGS) { %>
        <%=category.getType() == PwmSetting.Category.Type.SETTING ? "Settings - " : "Modules - "%>
        <%=category.getLabel(locale)%>
        <% } else if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.LOCALEBUNDLE) { %>
        <% final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = cookie.getLocaleBundle(); %>
        Custom Text - <%=bundleName.getTheClass().getSimpleName()%>
        <% } %>
    </div>
    <div id="header-title">
        Configuration Editor
    </div>
</div>
<div id="TopMenu">
</div>
<div id="centerbody-config">
<%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
<script type="text/javascript">
PWM_GLOBAL['startupFunctions'].push(function(){
    buildMenuBar();
    readConfigEditorCookie();
});
</script>
<form action="<pwm:url url='ConfigManager'/>" method="post" name="cancelEditing"
      enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="processAction" value="cancelEditing"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<div id="mainContentPane" style="width: 600px">
    <% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.SETTINGS) { %>
    <jsp:include page="fragment/configeditor-settings.jsp"/>
    <% } else if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.LOCALEBUNDLE) { %>
    <jsp:include page="fragment/configeditor-localeBundle.jsp"/>
    <% } %>
</div>
</div>
<div class="push"></div>
</div>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
