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
<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.servlet.ConfigEditorServlet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
    final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean();
    final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(request, response);
    final boolean showDesc = cookie.isShowDesc();
    final password.pwm.config.PwmSetting.Category category = cookie.getCategory();
%>
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<% if (showDesc && category.getDescription(locale) != null && category.getDescription(locale).length() > 1) { %>
<div id="categoryDescription" style="background-color: #F5F5F5; border-radius: 5px; padding: 10px 15px 10px 15px">
    <%= category.getDescription(locale)%>
</div>
<% } %>
<br/>
<% if (cookie.getEditMode() == ConfigEditorCookie.EDIT_MODE.PROFILE) { %>
<% cookie.setProfile(""); %>
<% request.setAttribute("setting",cookie.getCategory().getProfileSetting()); %>
<% request.setAttribute("showDescription",true); %>
<jsp:include page="configeditor-setting.jsp"/>
<div style="width: 100%; padding-bottom: 10px">
    <a onclick="preferences['editMode'] = 'SETTINGS'; setConfigEditorCookie();  loadMainPageBody();" style="cursor:pointer">Return to configuration editor</a>
</div>
<% } else { %>
<% final List<String> profiles = configManagerBean.getConfiguration().profilesForSetting(category.getProfileSetting()); %>
<div style="width: 100%; padding-bottom: 10px">
    <div style="display:inline-block; width:300px">
        Selected Profile:
        <select id="profileSelect" data-dojo-type="dijit/form/Select">
            <option onclick="gotoProfile('')">Default</option>
            <% for (final String profile : profiles) { if (profile.length() > 0) { %>
            <option <% if (cookie.getProfile().equals(profile)) {%>selected="selected"<%}%> onclick="gotoProfile('<%=profile%>')"><%=profile%></option>
            <% } } %>

            <option onclick="preferences['editMode'] = 'PROFILE'; setConfigEditorCookie();  loadMainPageBody();">
                -- Edit Profile List
            </option>
        </select>
    </div>
</div>
<script>
    function gotoProfile(profile) {
        PWM_MAIN.showWaitDialog(null,null,function(){
            preferences['profile'] = profile;
            setConfigEditorCookie();
            loadMainPageBody();
        });
    }
</script>
<% for (final PwmSetting loopSetting : PwmSetting.getSettings(category,0)) { %>
<% if (!"true".equalsIgnoreCase(loopSetting.getOptions().get("HideForDefaultProfile")) || !"".equals(cookie.getProfile())) { %>
<% request.setAttribute("setting",loopSetting); %>
<% request.setAttribute("showDescription",cookie.isShowDesc()); %>
<jsp:include page="configeditor-setting.jsp"/>
<% } %>
<% } %>
<% final List<PwmSetting> advancedSettings = PwmSetting.getSettings(category,1);%>
<% boolean showAdvanced = cookie.getLevel() > 1; %>
<% if (!advancedSettings.isEmpty()) { %>
<a id="showAdvancedSettingsButton" style="cursor:pointer" onclick="toggleAdvancedSettingsDisplay()">Show <%=advancedSettings.size()%> Advanced Settings</a>
<div id="advancedSettings" style="display: none">
    <hr/>
    <% for (final PwmSetting loopSetting : advancedSettings) { %>
    <% if (!"true".equalsIgnoreCase(loopSetting.getOptions().get("HideForDefaultProfile")) || !"".equals(cookie.getProfile())) { %>
    <% request.setAttribute("setting",loopSetting); %>
    <% request.setAttribute("showDescription",cookie.isShowDesc()); %>
    <jsp:include page="configeditor-setting.jsp"/>
    <% } %>
    <% } %>
    <% if (!showAdvanced) { %>
    <a onclick="toggleAdvancedSettingsDisplay()" style="cursor:pointer">Hide Advanced Settings</a>
    <% } %>
</div>
<jsp:include page="settings-scripts.jsp"/>
<% } %>
<% } %>

