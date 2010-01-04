<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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

<%@ page import="password.pwm.util.PwmDBLogger" %>
<%@ page import="password.pwm.util.PwmLogEvent" %>
<%@ page import="password.pwm.util.PwmLogLevel" %>
<%@ page import="java.io.PrintWriter" %>
<%@ page import="java.io.StringWriter" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.Helper" %>
<%@ page import="password.pwm.PwmSession" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<% final PwmDBLogger pwmDBLogger = PwmSession.getPwmSession(session).getContextManager().getPwmDBLogger(); %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Event Log"/></jsp:include>
    <div id="centerbody" style="width:98%">
        <p style="text-align:center;">
            <a href="status.jsp">Status</a> | <a href="eventlog.jsp">Event Log</a> | <a href="intruderstatus.jsp">Intruder Status</a> | <a href="activesessions.jsp">Active Sessions</a> | <a href="config.jsp">Configuration</a> | <a href="threads.jsp">Threads</a> | <a href="UserInformation">User Information</a>
        </p>
        <p>
            This page shows PWM debug log
            history.  This history is stored in the pwmDB cache of the debug log. For a
            permanent log
            record of events, configure the log4jconfig.xml file.
            All times listed are in
            the <%= (java.text.DateFormat.getDateTimeInstance()).getTimeZone().getDisplayName() %>
            timezone.  The pwmDB contains <%=NumberFormat.getInstance().format(pwmDBLogger.getEventCount())%> events in
            <%= Helper.formatDiskSize(PwmSession.getPwmSession(session).getContextManager().getPwmDbDiskSize()) %>.
        </p>
        <br class="clear"/>
        <form action="<pwm:url url='eventlog.jsp'/>" method="GET" enctype="application/x-www-form-urlencoded" name="eventlogParameters"
              onsubmit="getObject('submit_button').value = ' Please Wait ';getObject('submit_button').disabled = true">
            <table style="border: 0; max-width:600px;">
                <tr style="border: 0">
                    <td class="key" style="border: 0">
                        Level
                    </td>
                    <td style="border: 0">
                        <% final String selectedLevel = request.getParameter("level");%>
                        <select name="level">
                            <option value="TRACE" <%= "TRACE".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>TRACE</option>
                            <option value="DEBUG" <%= "DEBUG".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>DEBUG</option>
                            <option value="INFO" <%= "INFO".equals(selectedLevel) || selectedLevel == null ? "selected=\"selected\"" : "" %>>INFO</option>
                            <option value="ERROR" <%= "ERROR".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>ERROR</option>
                            <option value="WARN" <%= "WARN".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>WARN</option>
                            <option value="FATAL" <%= "FATAL".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>FATAL</option>
                        </select>
                    </td>
                </tr>
                <tr style="border: 0">
                    <td class="key" style="border: 0">
                        Type
                    </td>
                    <td style="border: 0">
                        <% final String selectedType = request.getParameter("type");%>
                        <select name="type">
                            <option value="User" <%= "User".equals(selectedType) ? "selected=\"selected\"" : "" %>>User</option>
                            <option value="System" <%= "System".equals(selectedType) ? "selected=\"selected\"" : "" %>>System</option>
                            <option value="Both" <%= "Both".equals(selectedType) ? "selected=\"selected\"" : "" %>>Both</option>
                        </select>
                    </td>
                </tr>
                <tr style="border: 0">
                    <td class="key" style="border: 0">
                        Username
                    </td>
                    <td style="border: 0">
                        <input name="username" type="text" value="<%=password.pwm.Validator.readStringFromRequest(request,"username", 255)%>"/>
                    </td>
                </tr>
                <tr style="border: 0">
                    <td class="key" style="border: 0">
                        Containing text
                    </td>
                    <td style="border: 0">
                        <input name="text" type="text" value="<%=password.pwm.Validator.readStringFromRequest(request,"text", 255)%>"/>
                    </td>
                </tr>
                <tr style="border: 0">
                    <td class="key" style="border: 0">
                        &nbsp;
                    </td>
                    <td style="border: 0">
                        <input type="submit" name="submit"  id="submit_button" value=" Search "/>
                    </td>
                </tr>
                <tr style="border: 0">
                    <td class="key" style="border: 0">
                        Maximum Count
                    </td>
                    <td style="border: 0">
                        <% final String selectedCount = request.getParameter("count");%>
                        <select name="count">
                            <option value="100" <%= "100".equals(selectedCount) ? "selected=\"selected\"" : "" %>>100</option>
                            <option value="500" <%= "500".equals(selectedCount) ? "selected=\"selected\"" : "" %>>500</option>
                            <option value="2000" <%= "2000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>2000</option>
                            <option value="5000" <%= "5000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>5000</option>
                            <option value="10000" <%= "10000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>10000</option>
                            <option value="100000" <%= "100000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>100000</option>
                        </select>
                    </td>
                </tr>
                <tr style="border: 0">
                    <td class="key" style="border: 0">
                        Maximum Search Time
                    </td>
                    <td style="border: 0">
                        <% final String selectedTime = request.getParameter("maxTime");%>
                        <select name="maxTime">
                            <option value="10000" <%= "10000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>10 seconds</option>
                            <option value="30000" <%= "30000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>30 seconds </option>
                            <option value="60000" <%= "60000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>1 minute</option>
                            <option value="120000" <%= "120000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>2 minutes</option>
                        </select>
                    </td>
                </tr>
            </table>
            <br/>
        </form>
        <br class="clear"/>
        <table>
            <tr>
                <td class="title">
                    &nbsp;
                </td>
                <td class="title">
                    Timestamp
                </td>
                <td class="title">
                    Level
                </td>
                <td class="title">
                    Source Address
                </td>
                <td class="title">
                    User
                </td>
                <td class="title">
                    Component
                </td>
                <td class="title">
                    Detail
                </td>
            </tr>
            <%
                PwmLogLevel logLevel = PwmLogLevel.INFO;
                PwmDBLogger.EventType logType = PwmDBLogger.EventType.User;
                int eventCount = 100;
                long maxTime = 10000;
                final String username = password.pwm.Validator.readStringFromRequest(request,"username", 255);
                final String text = password.pwm.Validator.readStringFromRequest(request,"text", 255);
                try { logLevel = PwmLogLevel.valueOf(request.getParameter("level")); } catch (Exception e) { }
                try { logType = PwmDBLogger.EventType.valueOf(request.getParameter("type")); } catch (Exception e) { }
                try { eventCount  = Integer.parseInt(request.getParameter("count")); } catch (Exception e) { }
                try { maxTime  = Long.parseLong(request.getParameter("maxTime")); } catch (Exception e) { }

                final List<PwmLogEvent> logEntries = pwmDBLogger.readStoredEvents(PwmSession.getPwmSession(session), logLevel, eventCount, username, text, maxTime, logType);
                int counter = 0;
                for (final PwmLogEvent event : logEntries) {
            %>
            <tr>
                <td class="key">
                    <pre><%= ++counter %></pre>
                </td>
                <td>
                    <%= DateFormat.getDateTimeInstance().format(event.getDate()) %>
                </td>
                <td>
                    <%= event.getLevel() %>
                </td>
                <td>
                    <%= event.getSource() %>
                </td>
                <td>
                    <%= event.getActor() %>
                </td>
                <td>
                    <%
                        final int lastDot = event.getTopic().lastIndexOf(".");
                        out.write(lastDot != -1 ? event.getTopic().substring(lastDot + 1,event.getTopic().length()) : event.getTopic());
                    %>
                </td>
                <td>
                    <%= event.getMessage() %>
                    <%
                        //noinspection ThrowableResultOfMethodCallIgnored
                        if (event.getThrowable() != null) {
                            out.append("<br/>Throwable: ");
                            out.append("<pre>");
                            final PrintWriter strWriter = new PrintWriter(new StringWriter());
                            //noinspection ThrowableResultOfMethodCallIgnored
                            event.getThrowable().printStackTrace(strWriter);
                            out.append(strWriter.toString());
                            out.append("</pre>");
                        }
                    %>
                </td>
            </tr>
            <% } %>
        </table>
    </div>
    <%@ include file="../jsp/footer.jsp" %>
</body>
</html>
