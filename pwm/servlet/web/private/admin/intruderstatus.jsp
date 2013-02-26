<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.util.IntruderManager" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="password.pwm.util.pwmdb.PwmDB" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% IntruderManager intruderManager = ContextManager.getPwmApplication(session).getIntruderManager(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Intruder Lockouts"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <% final Map<String, IntruderManager.IntruderRecord> userLockTable = intruderManager.getUserLockTable(); %>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
            <div data-dojo-type="dijit.layout.ContentPane" title="Users">
                <% if (userLockTable.isEmpty()) { %>
                <div style="font-weight: bold; text-align:center; width:100%">No user accounts are currently locked.</div>
                <% } else { %>
                <div style="max-height: 400px; overflow: auto;">
                    <table>
                        <tr>
                            <td class="key" style="text-align: left">
                                Username
                            </td>
                            <td class="key" style="text-align: left">
                                Status
                            </td>
                            <td class="key" style="text-align: left">
                                Last Activity
                            </td>
                            <td class="key" style="text-align: left">
                                Attempts
                            </td>
                        </tr>
                        <%
                            for (final String key : userLockTable.keySet()) {
                                final IntruderManager.IntruderRecord record = userLockTable.get(key);
                        %>
                        <tr>
                            <td>
                                <%= StringEscapeUtils.escapeHtml(key) %>
                            </td>
                            <td>
                                <% if (intruderManager.isLocked(record, PwmDB.DB.INTRUDER_USER)) { %>
                                locked
                                <% } else { %>
                                watching
                                <% } %>
                            </td>
                            <td>
                                <% if (intruderManager.isLocked(record, PwmDB.DB.INTRUDER_USER)) { %>
                                n/a
                                <% } else { %>
                                <%= TimeDuration.fromCurrent(record.getTimeStamp()).asCompactString() %>
                                <% } %>
                            </td>
                            <td>
                                <%= record.getAttemptCount() %>
                            </td>
                            <% } %>
                        </tr>
                    </table>
                </div>
                <% } %>
            </div>
            <% final Map<String, IntruderManager.IntruderRecord> addressLockTable = ContextManager.getPwmApplication(session).getIntruderManager().getAddressLockTable(); %>
            <div data-dojo-type="dijit.layout.ContentPane" title="Addresses">
                <% if (addressLockTable.isEmpty()) { %>
                <div style="font-weight: bold; text-align:center; width:100%">No network addresses are currently locked.</div>
                <% } else { %>
                <div style="max-height: 400px; overflow: auto;">

                    <table>
                        <tr>
                            <td class="key" style="text-align: left">
                                Address
                            </td>
                            <td class="key" style="text-align: left">
                                Status
                            </td>
                            <td class="key" style="text-align: left">
                                Last Activity
                            </td>
                            <td class="key" style="text-align: left">
                                Attempts
                            </td>
                        </tr>
                        <%
                            for (final String key : addressLockTable.keySet()) {
                                final IntruderManager.IntruderRecord record = addressLockTable.get(key);
                        %>
                        <tr>
                            <td>
                                <%= StringEscapeUtils.escapeHtml(key) %>
                            </td>
                            <td>
                                <% if (intruderManager.isLocked(record, PwmDB.DB.INTRUDER_ADDRESS)) { %>
                                locked
                                <% } else { %>
                                watching
                                <% } %>
                            </td>
                            <td>
                                <% if (intruderManager.isLocked(record, PwmDB.DB.INTRUDER_ADDRESS)) { %>
                                n/a
                                <% } else { %>
                                <%= TimeDuration.fromCurrent(record.getTimeStamp()).asCompactString() %>
                                <% } %>
                            </td>
                            <td>
                                <%= record.getAttemptCount() %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </div>
                <% } %>
            </div>
        </div>
    </div>
</div>
<script type="text/javascript">
    function startupPage() {
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog"],function(dojoParser){
            dojoParser.parse();
        });
    }
    startupPage();
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


