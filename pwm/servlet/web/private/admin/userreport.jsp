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

<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.util.PwmDBLogger" %>
<%@ page import="password.pwm.util.PwmLogEvent" %>
<%@ page import="password.pwm.util.UserReport" %>
<%@ page import="java.io.PrintWriter" %>
<%@ page import="java.io.StringWriter" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Iterator" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="/WEB-INF/jsp/header.jsp" %>
<% final PwmDBLogger pwmDBLogger = PwmSession.getPwmSession(session).getContextManager().getPwmDBLogger(); %>
<body onload="pwmPageLoadHandler();">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/header-body.jsp">
    <jsp:param name="pwm.PageName" value="PWM Event Log"/>
</jsp:include>
<div id="centerbody" style="width:98%">
<%@ include file="admin-nav.jsp" %>
<p>
    This page shows a report of all users visable to PWM.
</p>

<p>
    The pwmDB is configured to capture events of level
    <b><%=PwmSession.getPwmSession(session).getConfig().readSettingAsString(PwmSetting.EVENTS_PWMDB_LOG_LEVEL)%>
    </b> and higher.
</p>
<br class="clear"/>
<% if (request.getParameter("doReport").equals("1")) { %>
<%
    final PwmSession pwmSession = PwmSession.getPwmSession(session);
    final UserReport userReport = new UserReport(pwmSession.getConfig(),pwmSession.getContextManager().getProxyChaiProvider());
    final Iterator<UserReport.UserInformation> reportIterator = userReport.resultIterator();
%>
<br class="clear"/>
<table>
    <tr>
        <td class="title" width="5">
            &nbsp;
        </td>
        <td class="title">
            Timestamp
        </td>
        <td class="title">
            Level
        </td>
        <td class="title">
            Src
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
    <% int counter = 0;
        for (final PwmLogEvent event : searchResults.getEvents()) { %>
    <tr>
        <td class="key" style="font-family: Courier, sans-serif" width="5">
            <%= ++counter %>
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
                out.write(lastDot != -1 ? event.getTopic().substring(lastDot + 1, event.getTopic().length()) : event.getTopic());
            %>
        </td>
        <td>
            <%
                final String eventMessage = event.getHtmlSafeMessage();
                if (eventMessage.contains("\n")) {
                    out.append("<pre>").append(eventMessage).append("</pre>");
                } else {
                    out.append(eventMessage);
                }
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
<% } %>
</div>
<%@ include file="/WEB-INF/jsp/footer.jsp" %>
</body>
</html>
