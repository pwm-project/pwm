<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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
<%@ page import="password.pwm.PwmConstants" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<p id="admin-menu-bar" style="text-align:center;">
    <a href="status.jsp">Status</a> | <a href="statistics.jsp">Statistics</a> | <a
        href="intruderstatus.jsp">Intruders</a> | <a href="activesessions.jsp">Sessions</a> | <a href="eventlog.jsp">Event Log</a>
    <br/>
    <a href="userreport.jsp">User Report</a> | <a href="config.jsp">Configuration</a> | <a href="http-request-information.jsp">Http Debug</a> | <a
        href="<%=PwmConstants.PWM_URL_HOME%>">PWM Project</a> | <a href="<%=request.getContextPath()%>">Main Menu</a>
</p>
