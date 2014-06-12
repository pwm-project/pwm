<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.Validator" %>
<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.config.LdapProfile" %>
<%@ page import="password.pwm.config.option.SelectableContextMode" %>
<%@ page import="java.util.Map" %>
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

<%@ taglib uri="pwm" prefix="pwm" %>
<% final Configuration selectorConfig = ContextManager.getPwmApplication(session).getConfig(); %>
<% final SelectableContextMode selectableContextMode = selectorConfig.readSettingAsEnum(PwmSetting.LDAP_SELECTABLE_CONTEXT_MODE,SelectableContextMode.class); %>
<% final Map<String,LdapProfile> ldapProfiles = selectorConfig.getLdapProfiles(); %>
<% final String selectedProfileParam = Validator.readStringFromRequest(request,PwmConstants.PARAM_LDAP_PROFILE);%>
<% final LdapProfile selectedProfile = selectorConfig.getLdapProfiles().containsKey(selectedProfileParam) ? selectorConfig.getLdapProfiles().get(selectedProfileParam) : selectorConfig.getLdapProfiles().get(PwmConstants.PROFILE_ID_DEFAULT); %>
<% final boolean showContextSelector = selectableContextMode == SelectableContextMode.SHOW_CONTEXTS && selectedProfile != null && selectedProfile.getLoginContexts().size() > 0; %>
<% if (selectableContextMode != SelectableContextMode.NONE && ldapProfiles.size() > 1) { %>
<h2><label for="<%=PwmConstants.PARAM_LDAP_PROFILE%>"><pwm:Display key="Field_LdapProfile"/></label></h2>
<select name="<%=PwmConstants.PARAM_LDAP_PROFILE%>" id="<%=PwmConstants.PARAM_LDAP_PROFILE%>" class="selectfield" onclick="PWM_MAIN.updateLoginContexts()">
    <% for (final String profileID : ldapProfiles.keySet()) { %>
    <% final String displayName = ldapProfiles.get(profileID).getDisplayName(pwmSessionHeader.getSessionStateBean().getLocale()); %>
    <option value="<%=profileID%>"<%=(profileID.equals(selectedProfileParam))?" selected=\"selected\"":""%>><%=StringEscapeUtils.escapeHtml(displayName)%></option>
    <% } %>
</select>
<% } %>
<% if (showContextSelector) { %>
<h2><label for="<%=PwmConstants.PARAM_CONTEXT%>"><pwm:Display key="Field_Location"/></label></h2>
<select name="<%=PwmConstants.PARAM_CONTEXT%>" id="<%=PwmConstants.PARAM_CONTEXT%>" class="selectfield">
    <% for (final String key : selectedProfile.getLoginContexts().keySet()) { %>
    <option value="<%=StringEscapeUtils.escapeHtml(key)%>"><%=StringEscapeUtils.escapeHtml(selectedProfile.getLoginContexts().get(key))%></option>
    <% } %>
</select>
<% } %>

