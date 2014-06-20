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
<% if (category.getDescription(locale) != null && category.getDescription(locale).length() > 1) { %>
<div id="categoryDescription" class="categoryDescription">
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
    <button class="btn" onclick="preferences['editMode'] = 'SETTINGS'; setConfigEditorCookie();  loadMainPageBody();" style="cursor:pointer">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-chevron-circle-left"></span></pwm:if>
        Return to configuration editor
    </button>
</div>
<% } else { %>
<% final List<String> profiles = configManagerBean.getConfiguration().profilesForSetting(category.getProfileSetting()); %>
<div class="profileSelectPanel">
    <label for="profileSelect">Selected Profile:</label>
    <span id="profileSelect" style="font-weight: bolder"><%="".equals(cookie.getProfile()) ? "Default" : cookie.getProfile() %></span>
</div>
<div style="width: 100%; text-align: center">
    <% if (profiles.size() > 1) { %>
    <button class="btn" onclick="selectProfile()">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-tag"></span></pwm:if>
        Select Profile
    </button>
    &nbsp;&nbsp;&nbsp;
    <% } %>
    <button class="btn" onclick="editProfiles()">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-tags"></span></pwm:if>
        Define Profiles
    </button>
</div>
<script>
    function selectProfile() {
        var htmlBody = '<br/><div style="width:100%; text-align: center">';
        htmlBody += '<a onclick="gotoProfile(\'\')">' + 'Default' + '</a>';
        <% for (final String profile : profiles) { %>
        htmlBody += '<a onclick="gotoProfile(\'<%=profile%>\')">' + '<%=profile%>' + '</a><br/>';
        <% } %>
        htmlBody += '</div><br/>';
        PWM_MAIN.showDialog({
            title:'Select Profile',
            text:htmlBody,
            showOk: false,
            showCancel: true
        });
    }

    function gotoProfile(profile) {
        preferences['profile'] = profile;

        PWM_MAIN.showWaitDialog({loadFunction:function(){
            setConfigEditorCookie();
            loadMainPageBody();
        }});
    }

    function editProfiles() {
        preferences['editMode'] = 'PROFILE';
        PWM_MAIN.showWaitDialog({loadFunction:function() {
            setConfigEditorCookie();
            loadMainPageBody();
        }});
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
<a id="showAdvancedSettingsButton" style="cursor:pointer" onclick="PWM_CFGEDIT.toggleAdvancedSettingsDisplay()">
    <pwm:if test="showIcons"><span class="btn-icon fa fa-arrow-down"></span></pwm:if>
    <pwm:Display key="Button_ShowAdvanced" bundle="Config" value1="<%=String.valueOf(advancedSettings.size())%>"/>
</a>
<% if (!showAdvanced) { %>
<a onclick="PWM_CFGEDIT.toggleAdvancedSettingsDisplay({})" style="cursor:pointer; display: none" id="hideAdvancedSettingsButton">
    <pwm:if test="showIcons"><span class="btn-icon fa fa-arrow-up"></span></pwm:if>
    <pwm:Display key="Button_HideAdvanced" bundle="Config"/>
</a>
<% } %>
<br/>
<div id="advancedSettings" style="display: none">
    <% for (final PwmSetting loopSetting : advancedSettings) { %>
    <% if (!"true".equalsIgnoreCase(loopSetting.getOptions().get("HideForDefaultProfile")) || !"".equals(cookie.getProfile())) { %>
    <% request.setAttribute("setting",loopSetting); %>
    <% request.setAttribute("showDescription",cookie.isShowDesc()); %>
    <% request.setAttribute("profileID",cookie.getProfile()); %>
    <jsp:include page="configeditor-setting.jsp"/>
    <% } %>
    <% } %>
</div>
<jsp:include page="settings-scripts.jsp"/>
<% } %>
<% } %>

