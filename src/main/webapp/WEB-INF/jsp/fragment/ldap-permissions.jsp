<%@ page import="password.pwm.config.LDAPPermissionInfo" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.util.LDAPPermissionCalculator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="pwm" prefix="pwm" %>

<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<% final LDAPPermissionCalculator outputData = (LDAPPermissionCalculator)JspUtility.getAttribute(pageContext, PwmRequest.Attribute.LdapPermissionItems); %>
<p>
    <pwm:display key="Display_LdapPermissionRecommendations" bundle="Config"/>
</p>
<h1>Attribute Permissions</h1>
<% for (final LDAPPermissionInfo.Actor actor : LDAPPermissionInfo.Actor.values()) { %>
<% Map<String,Map<LDAPPermissionInfo.Access,List<LDAPPermissionCalculator.PermissionRecord>>> baseMap = outputData.getPermissionsByActor(actor); %>
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
    <% for (final String attribute : baseMap.keySet()) { %>
    <% for (final LDAPPermissionInfo.Access access : baseMap.get(attribute).keySet()) { %>
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
                for (final LDAPPermissionCalculator.PermissionRecord record : baseMap.get(attribute).get(access)) {
                    if (record.getPwmSetting() != null) {
                        menuLocations.add(record.getPwmSetting().toMenuLocationDebug(record.getProfile(), JspUtility.locale(request)));
                    } else {
                        menuLocations.add(LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, JspUtility.getPwmRequest(pageContext)));
                    }
                }
            %>
            <% for (String menuLocation : menuLocations) { %>
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
