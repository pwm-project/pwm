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

<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.util.ServletHelper" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.servlet.ConfigEditorServlet" %>
<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
    final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean();
    final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(request, response);
    final boolean showDesc = cookie.isShowDesc();
    final password.pwm.config.PwmSetting.Category category = cookie.getCategory();
    final boolean hasNotes = configManagerBean.getConfiguration().readConfigProperty(StoredConfiguration.PROPERTY_KEY_NOTES) != null && configManagerBean.getConfiguration().readConfigProperty(StoredConfiguration.PROPERTY_KEY_NOTES).length() > 0;
%>
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<% if (showDesc && category.getDescription(locale) != null && category.getDescription(locale).length() > 1) { %>
<div id="categoryDescription" style="background-color: #F5F5F5; border-radius: 5px; padding: 10px 15px 10px 15px">
    <%= category.getDescription(locale)%>
</div>
<% } %>
<% if (hasNotes && !cookie.isNotesSeen()) { %>
<div style="font-size: small">
    <img src="<%=request.getContextPath()%><pwm:url url="/public/resources/warning.gif"/>" alt="warning"/>
    <pwm:Display key="Warning_ShowNotes" bundle="Config"/>
    <a style="font-weight: normal; font-size: smaller" onclick="preferences['notesSeen']=true;setConfigEditorCookie()" href="ConfigEditor">(hide)</a>
</div>
<% } %>
<br/>
<% for (final PwmSetting loopSetting : PwmSetting.getSettings(category,0)) { %>
<% request.setAttribute("setting",loopSetting); %>
<jsp:include page="configeditor-setting.jsp"/>
<% } %>
<% final List<PwmSetting> advancedSettings = PwmSetting.getSettings(category,1);%>
<% final boolean showAdvanced = cookie.getLevel() > 1; %>
<% if (!advancedSettings.isEmpty()) { %>
<button class="btn" id="showAdvancedSettingsButton" onclick="toggleAdvancedSettingsDisplay()">Show Advanced Settings</button>
<div id="advancedSettings" style="display: none">
    <hr/>
    <% for (final PwmSetting loopSetting : advancedSettings) { %>
    <% request.setAttribute("setting",loopSetting); %>
    <jsp:include page="configeditor-setting.jsp"/>
    <% } %>
    <% if (!showAdvanced) { %>
    <button class="btn" onclick="toggleAdvancedSettingsDisplay()">Hide Advanced Settings</button>
    <% } %>
</div>
<script type="text/javascript">
    var advancedSettingsAreVisible = false;
    function toggleAdvancedSettingsDisplay() {
        require(['dojo/fx'], function(fx) {
            var advSetElement = getObject('advancedSettings');
            if (advancedSettingsAreVisible) {
                fx.wipeOut({node:advSetElement }).play();
                getObject('showAdvancedSettingsButton').style.display='block';
            } else {
                fx.wipeIn({ node:advSetElement }).play();
                getObject('showAdvancedSettingsButton').style.display='none';
            }
            advancedSettingsAreVisible = !advancedSettingsAreVisible;
        });
    }
    <% if (showAdvanced) { %>
    PWM_GLOBAL['startupFunctions'].push(function(){
        toggleAdvancedSettingsDisplay();
    });
    <% } %>
</script>
<% } %>

