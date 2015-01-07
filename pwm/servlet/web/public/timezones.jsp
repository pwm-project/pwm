<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.util.TimeZone" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
  <% final TimeDuration offset = new TimeDuration(0,tz.getOffset(System.currentTimeMillis())); %>
  <tr>
    <td><%=tzID%></td>
    <td><%=tz.getDisplayName()%></td>
    <td><%=offset.getHours()%>h <%=offset.getMinutes()%>m <%=offset.getSeconds()%>s</td>
  </tr>
  <% } %>
  </tbody>
</table>