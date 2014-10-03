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
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.PwmSettingCategory" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ page import="password.pwm.http.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.http.servlet.ConfigEditorServlet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
    final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean();
    final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(PwmRequest.forRequest(request, response));
    final boolean showDesc = cookie.isShowDesc();
    final PwmSettingCategory category = cookie.getCategory();
%>
<% if (category.getDescription(locale) != null && category.getDescription(locale).length() > 1) { %>
<div id="categoryDescription" class="categoryDescription">
    <%= category.getDescription(locale)%>
</div>
<% } %>
<% if (cookie.getCategory() == PwmSettingCategory.DATABASE) { %>
<div style="width: 100%; text-align: center">
<button class="btn" onclick="PWM_CFGEDIT.databaseHealthCheck()">
    <pwm:if test="showIcons"><span class="btn-icon fa fa-bolt"></span></pwm:if>
    Test Database Settings
</button>
    </div>
<% } %>
<br/>
<% for (final PwmSetting loopSetting : PwmSetting.getSettings(category,0)) { %>
<% request.setAttribute("setting",loopSetting); %>
<% request.setAttribute("showDescription",cookie.isShowDesc()); %>
<jsp:include page="configeditor-setting.jsp"/>
<% } %>
<% final List<PwmSetting> advancedSettings = PwmSetting.getSettings(category,1);%>
<% boolean showAdvanced = cookie.getLevel() > 1; %>
<% if (!advancedSettings.isEmpty()) { %>
<a id="showAdvancedSettingsButton" style="cursor:pointer" onclick="PWM_CFGEDIT.toggleAdvancedSettingsDisplay()">
    <pwm:if test="showIcons"><span class="btn-icon fa fa-arrow-down"></span></pwm:if>
    <pwm:display key="Button_ShowAdvanced" bundle="Config" value1="<%=String.valueOf(advancedSettings.size())%>"/>
</a>
<% if (!showAdvanced) { %>
<a onclick="PWM_CFGEDIT.toggleAdvancedSettingsDisplay({})" style="cursor:pointer; display: none" id="hideAdvancedSettingsButton">
    <pwm:if test="showIcons"><span class="btn-icon fa fa-arrow-up"></span></pwm:if>
    <pwm:display key="Button_HideAdvanced" bundle="Config"/>
</a>
<% } %>
<br/>
<div id="advancedSettings" style="display: none">
    <% for (final PwmSetting loopSetting : advancedSettings) { %>
    <% request.setAttribute("setting",loopSetting); %>
    <% request.setAttribute("showDescription",cookie.isShowDesc()); %>
    <jsp:include page="configeditor-setting.jsp"/>
    <% } %>
</div>
<br/><br/>
<jsp:include page="settings-scripts.jsp"/>
<% } %>

