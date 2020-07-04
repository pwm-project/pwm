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


<%@ page import="password.pwm.config.LDAPPermissionInfo" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.util.LDAPPermissionCalculator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="pwm" prefix="pwm" %>

<% final LDAPPermissionCalculator outputData = (LDAPPermissionCalculator)JspUtility.getAttribute(pageContext, PwmRequestAttribute.LdapPermissionItems); %>
<p>
    <pwm:display key="Display_LdapPermissionRecommendations" bundle="Config"/>
</p>
<h1>Attribute Permissions</h1>
<% for (final LDAPPermissionInfo.Actor actor : LDAPPermissionInfo.Actor.values()) { %>
<% final Map<String,Map<LDAPPermissionInfo.Access,List<LDAPPermissionCalculator.PermissionRecord>>> baseMap = outputData.getPermissionsByActor(actor); %>
<% if (!baseMap.isEmpty()) { %>
<h2>
    <%=actor.getLabel(JspUtility.locale(request),JspUtility.getPwmRequest(pageContext).getConfig())%>
</h2>
<p>
    <%=actor.getDescription(JspUtility.locale(request),JspUtility.getPwmRequest(pageContext).getConfig())%>
</p>
<table style="">
    <tr>
        <td class="title">Attribute Name</td>
        <td class="title">Access</td>
        <td class="title">Associated Configuration Setting</td>
    </tr>
    <% for (final Map.Entry<String,Map<LDAPPermissionInfo.Access,List<LDAPPermissionCalculator.PermissionRecord>>> entry : baseMap.entrySet()) { %>
    <% final String attribute = entry.getKey(); %>
    <% for (final LDAPPermissionInfo.Access access : entry.getValue().keySet()) { %>
    <tr>
        <td style="text-align: left">
            <%= attribute %>
        </td>
        <td style="text-align: left">
            <%= access %>
        </td>
        <td style="text-align: left">
            <%
                final Set<String> menuLocations = new TreeSet<String>();
                for (final LDAPPermissionCalculator.PermissionRecord record : entry.getValue().get(access)) {
                    if (record.getPwmSetting() != null) {
                        menuLocations.add(record.getPwmSetting().toMenuLocationDebug(record.getProfile(), JspUtility.locale(request)));
                    } else {
                        menuLocations.add(LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, JspUtility.getPwmRequest(pageContext)));
                    }
                }
            %>
            <% for (final String menuLocation : menuLocations) { %>
            <%= menuLocation %>
            <br/>
            <% } %>
        </td>
    </tr>
    <% } %>
    <% } %>
</table>
<br/>
<% } %>
<% } %>
