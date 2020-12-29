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
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final List<String> domainList = ( List<String>) JspUtility.getAttribute( pageContext, PwmRequestAttribute.DomainList );
%>
<% if ( !JavaHelper.isEmpty( domainList ) && domainList.size() > 1 ) { %>
    <h2 class="loginFieldLabel"><label for="<%=PwmConstants.PARAM_DOMAIN%>"><pwm:display key="Field_Domain"/></label></h2>
    <div class="formFieldWrapper">
        <select name="<%=PwmConstants.PARAM_DOMAIN%>" id="<%=PwmConstants.PARAM_DOMAIN%>" class="selectfield" title="<pwm:display key="Field_Domain"/>">
            <% for (final String domain : domainList) { %>
            <option value="<%=StringUtil.escapeHtml(domain)%>"><%=StringUtil.escapeHtml(domain)%></option>
            <% } %>
        </select>
    </div>
<% } %>
