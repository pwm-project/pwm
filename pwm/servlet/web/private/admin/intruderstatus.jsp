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

<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.util.IntruderManager" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Intruder Lockouts"/>
    </jsp:include>
    <div id="centerbody" style="width:98%">
        <%@ include file="admin-nav.jsp" %>
        <% final Map<String, IntruderManager.IntruderRecord> userLockTable = ContextManager.getPwmApplication(session).getIntruderManager().getUserLockTable(); %>
        <% if (userLockTable.isEmpty()) { %>
        <br/>
        <br/>

        <div style="font-weight: bold; text-align:center; width:100%">No user accounts are currently locked.</div>
        <br/>
        <% } else { %>
        <table>
            <tr>
                <td class="title" colspan="10">
                    User Table
                </td>
            </tr>
            <tr>
                <td class="title">
                    Username
                </td>
                <td class="title">
                    Status
                </td>
                <td class="title">
                    Time Remaining
                </td>
                <td class="title">
                    Bad Attempts
                </td>
                <td class="title">
                    Last Bad Attempt
                </td>
            </tr>
            <%
                for (final String key : userLockTable.keySet()) {
                    final IntruderManager.IntruderRecord record = userLockTable.get(key);
            %>
            <tr>
                <td>
                    <%= key %>
                </td>
                <td>
                    <% if (record.isLocked()) { %>
                    locked
                    <% } else { %>
                    <% if (record.timeRemaining() < 0) { %>
                    retired
                    <% } else { %>
                    watching
                    <% } %>
                    <% } %>
                </td>
                <td>
                    <% if (record.timeRemaining() < 0) { %>
                    n/a
                    <% } else { %>
                    <%= TimeDuration.asCompactString(record.timeRemaining()) %>
                    <% } %>
                </td>
                <td>
                    <%= record.getAttemptCount() %>
                </td>
                <td>
                    <%= (DateFormat.getDateTimeInstance()).format(new Date(record.getTimeStamp())) %>
                </td>

            </tr>
            <% } %>
        </table>
        <% } %>
        <br class="clear"/>
        <% final Map<String, IntruderManager.IntruderRecord> addressLockTable = ContextManager.getPwmApplication(session).getIntruderManager().getAddressLockTable(); %>
        <% if (addressLockTable.isEmpty()) { %>
        <div style="font-weight: bold; text-align:center; width:100%">No network addresses are currently locked.</div>
        <br/>
        <% } else { %>
        <table>
            <tr>
                <td class="title" colspan="10">
                    Address Table
                </td>
            </tr>
            <tr>
                <td class="title">
                    Address
                </td>
                <td class="title">
                    Status
                </td>
                <td class="title">
                    Time Remaining
                </td>
                <td class="title">
                    Bad Attempts
                </td>
                <td class="title">
                    Last Bad Attempt
                </td>
            </tr>
            <%
                for (final String key : addressLockTable.keySet()) {
                    final IntruderManager.IntruderRecord record = addressLockTable.get(key);
            %>
            <tr>
                <td>
                    <%= key %>
                </td>
                <td>
                    <% if (record.isLocked()) { %>
                    locked
                    <% } else { %>
                    <% if (record.timeRemaining() < 0) { %>
                    retired
                    <% } else { %>
                    watching
                    <% } %>
                    <% } %>
                </td>
                <td>
                    <% if (record.timeRemaining() < 0) { %>
                    n/a
                    <% } else { %>
                    <%= TimeDuration.asCompactString(record.timeRemaining()) %>
                    <% } %>
                </td>
                <td>
                    <%= record.getAttemptCount() %>
                </td>
                <td>
                    <%= (DateFormat.getDateTimeInstance()).format(new Date(record.getTimeStamp())) %>
                </td>

            </tr>
            <% } %>
        </table>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


