<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.config.option.SelectableContextMode" %>
<%@ page import="password.pwm.config.profile.LdapProfile" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<%
    SelectableContextMode selectableContextMode = SelectableContextMode.NONE;
    Map<String,LdapProfile> ldapProfiles = Collections.emptyMap();
    String selectedProfileParam = "";
    LdapProfile selectedProfile;
    Map<String,String> selectableContexts = null;
    boolean showContextSelector = false;
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        selectableContextMode = pwmRequest.getConfig().readSettingAsEnum(PwmSetting.LDAP_SELECTABLE_CONTEXT_MODE,SelectableContextMode.class);
        ldapProfiles = pwmRequest.getConfig().getLdapProfiles();
        selectedProfileParam = pwmRequest.readParameterAsString(PwmConstants.PARAM_LDAP_PROFILE);
        selectedProfile = pwmRequest.getConfig().getLdapProfiles().containsKey(selectedProfileParam)
                ? pwmRequest.getConfig().getLdapProfiles().get(selectedProfileParam)
                : pwmRequest.getConfig().getDefaultLdapProfile();
        selectableContexts = selectedProfile.getSelectableContexts(pwmRequest.getPwmApplication());
        showContextSelector = selectableContextMode == SelectableContextMode.SHOW_CONTEXTS && selectableContexts.size() > 0;
    } catch (PwmException e) {
        /* noop */
    }
%>
<% if (selectableContextMode != SelectableContextMode.NONE && ldapProfiles.size() > 1) { %>
<h2 class="loginFieldLabel"><label for="<%=PwmConstants.PARAM_LDAP_PROFILE%>"><pwm:display key="Field_LdapProfile"/></label></h2>
<div class="formFieldWrapper">
    <select name="<%=PwmConstants.PARAM_LDAP_PROFILE%>" id="<%=PwmConstants.PARAM_LDAP_PROFILE%>" class="selectfield" title="<pwm:display key="Field_LdapProfile"/>">
        <% for (final LdapProfile ldapProfile : ldapProfiles.values()) { %>
        <% final String displayName = ldapProfile.getDisplayName(JspUtility.locale(request)); %>
        <option value="<%=ldapProfile.getIdentifier()%>"<%=(ldapProfile.getIdentifier().equals(selectedProfileParam))?" selected=\"selected\"":""%>><%=StringUtil.escapeHtml(
                displayName)%></option>
        <% } %>
    </select>
</div>
<% } %>
<div <%=showContextSelector?"":"class=\"display-none\" "%> id="contextSelectorWrapper">
    <h2 class="loginFieldLabel"><label for="<%=PwmConstants.PARAM_CONTEXT%>"><pwm:display key="Field_Location"/></label></h2>
    <div class="formFieldWrapper">
        <select name="<%=PwmConstants.PARAM_CONTEXT%>" id="<%=PwmConstants.PARAM_CONTEXT%>" class="selectfield" title="<pwm:display key="Field_Location"/>">
            <% if (selectableContexts != null) { %>
            <% for (final Map.Entry<String,String> entry : selectableContexts.entrySet()) { %>
            <option value="<%=StringUtil.escapeHtml(entry.getKey())%>"><%=StringUtil.escapeHtml(entry.getValue())%></option>
            <% } %>
            <% } %>
        </select>
    </div>
</div>
