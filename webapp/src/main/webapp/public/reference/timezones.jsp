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


<%@ page import="password.pwm.util.java.TimeDuration" %>
<%@ page import="java.util.TimeZone" %>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<table>
    <thead>
    <tr>
        <td class="title">ID</td>
        <td class="title">Description</td>
        <td class="title">Offset</td>
    </tr>
    </thead>
    <tbody>
    <% for (final String tzID : TimeZone.getAvailableIDs()) { %>
    <% final TimeZone tz = TimeZone.getTimeZone(tzID); %>
    <% final TimeDuration offset = TimeDuration.of(tz.getOffset(System.currentTimeMillis()), TimeDuration.Unit.MILLISECONDS); %>
    <tr>
        <td><%=tzID%></td>
        <td><%=tz.getDisplayName()%></td>
        <td><%=offset.as( TimeDuration.Unit.HOURS)%>h <%=offset.as( TimeDuration.Unit.MINUTES)%>m <%=offset.as( TimeDuration.Unit.SECONDS)%>s</td>
    </tr>
    <% } %>
    </tbody>
</table>
