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

<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.Helper" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="password.pwm.util.stats.StatisticsBundle" %>
<%@ page import="password.pwm.util.stats.StatisticsManager" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ContextManager contextManager = ContextManager.getContextManager(this.getServletConfig().getServletContext()); %>
<% final StatisticsManager statsManager = ContextManager.getContextManager(session).getStatisticsManager(); %>
<% final String statsPeriodSelect = request.getParameter("statsPeriodSelect"); %>
<% final String statsChartSelect = request.getParameter("statsChartSelect") != null && request.getParameter("statsChartSelect").length() > 0 ? request.getParameter("statsChartSelect") : Statistic.PASSWORD_CHANGES.toString() ; %>
<% final StatisticsBundle stats = statsManager.getStatBundleForKey(statsPeriodSelect); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(request.getLocale()); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, request.getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
<jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Status"/></jsp:include>
<div id="centerbody">
<p style="text-align:center;">
    <a href="status.jsp">Status</a> | <a href="eventlog.jsp">Event Log</a> | <a href="intruderstatus.jsp">Intruder Status</a> | <a href="activesessions.jsp">Active Sessions</a> | <a href="config.jsp">Configuration</a> | <a href="threads.jsp">Threads</a> | <a href="UserInformation">User Information</a>
</p>
<form action="<pwm:url url='status.jsp'/>" method="GET" enctype="application/x-www-form-urlencoded" name="statsUpdateForm"
      id="statsUpdateForm" onsubmit="getObject('submit_button').value = ' Please Wait ';getObject('submit_button').disabled = true">
<table>
    <tr>
        <td colspan="10" class="title">
            PWM Status
        </td>
    </tr>
    <tr>
        <td class="key">
            PWM Version
        </td>
        <td>
            <%= password.pwm.Constants.SERVLET_VERSION %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Start Time
        </td>
        <td>
            <%= dateFormat.format(contextManager.getStartupTime()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Current Time
        </td>
        <td>
            <%= dateFormat.format(new java.util.Date()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Server Timezone
        </td>
        <td>
            <%= dateFormat.getTimeZone().getDisplayName() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Server Locale
        </td>
        <td>
            <% final java.util.Locale theLocale = java.util.Locale.getDefault();
                out.print(theLocale.toString());
                out.print(" [" + theLocale.getDisplayName() + "]");
            %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Instance ID
        </td>
        <td>
            <%= contextManager.getInstanceID() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Last LDAP Unavailable Time
        </td>
        <td>
            <%= contextManager.getLastLdapFailure() != null ? dateFormat.format(contextManager.getLastLdapFailure()) : "" %> 
        </td>
    </tr>
    <tr>
        <td class="key">
            LDAP Vendor
        </td>
        <td>
            <%
                String vendor = "[detection error]";
                try { vendor = contextManager.getProxyChaiProvider().getDirectoryVendor().toString(); } catch (Exception e) { /* nothing */ }
            %>
            <%= vendor %>
        </td>
    </tr>
</table>
<br class="clear"/>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            Statistics
        </td>
    </tr>
    <tr>
        <td colspan="10" style="text-align: center">
            <select name="statsPeriodSelect" onchange="getObject('statsUpdateForm').submit();">
                <option value="<%=StatisticsManager.KEY_CUMULATIVE%>" <%= StatisticsManager.KEY_CUMULATIVE.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>since installation - <%= dateFormat.format(contextManager.getInstallTime()) %></option>
                <option value="<%=StatisticsManager.KEY_CURRENT%>" <%= StatisticsManager.KEY_CURRENT.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>since startup - <%= dateFormat.format(contextManager.getStartupTime()) %></option>
                <% final Map<StatisticsManager.Key,String> availableKeys = statsManager.getAvailabileKeys(request.getLocale()); %>
                <% for (final StatisticsManager.Key key : availableKeys.keySet()) { %>
                <option value="<%=key%>" <%= key.toString().equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>><%= availableKeys.get(key) %> GMT</option>
                <% } %>
            </select>
            <noscript>
                <input type="submit" id="submit_button_period" class="btn" value="Update"/>
            </noscript>
        </td>
    </tr>
    <% for (Iterator<Statistic> iter = Statistic.sortedValues(request.getLocale()).iterator(); iter.hasNext() ;) { %>
    <tr>
        <% Statistic leftStat = iter.next(); %>
        <td class="key">
            <%= leftStat.getLabel(request.getLocale()) %>
        </td>
        <td>
            <%= stats.getStatistic(leftStat) %><%= leftStat.getType() == Statistic.Type.AVERAGE ? " ms" : "" %>
        </td>
        <% if (iter.hasNext()) { %>
        <% Statistic rightStat = iter.next(); %>
        <td class="key">
            <%= rightStat.getLabel(request.getLocale()) %>
        </td>
        <td>
            <%= stats.getStatistic(rightStat) %><%= rightStat.getType() == Statistic.Type.AVERAGE ? " ms" : "" %>
        </td>
        <% } else { %>
        <td colspan="2">
            &nbsp;
        </td>
        <% } %>
    </tr>
    <% } %>
    <tr>
        <td class="title" colspan="10">
            Activity
        </td>
    </tr>
    <tr>
        <td colspan="10" style="text-align: center">
            <select name="statsChartSelect" onchange="getObject('statsUpdateForm').submit();">
                <% for (final Statistic loopStat : Statistic.sortedValues(request.getLocale())) { %>
                <option value="<%=loopStat %>" <%= loopStat.toString().equals(statsChartSelect) ? "selected=\"selected\"" : "" %>><%=loopStat.getLabel(request.getLocale())%></option>
                <% } %>
            </select>
            <br/>
            <noscript>
                <input type="submit" id="submit_button_chart" class="btn" value="Update"/>
            </noscript>
            <%
                final Statistic selectedStat = Statistic.valueOf(statsChartSelect);
                final Map<String,String> chartData = statsManager.getStatHistory(selectedStat,31);
                int topValue = 0;
                for (final String value: chartData.values()) topValue = Integer.parseInt(value) > topValue ? Integer.parseInt(value) : topValue;
                final StringBuilder imgURL = new StringBuilder();
                imgURL.append(request.isSecure() ? "https://www.google.com/charts" : "http://chart.apis.google.com/chart");
                imgURL.append("?cht=bvs");
                imgURL.append("&chs=590x150");
                imgURL.append("&chds=0," + topValue);
                imgURL.append("&chco=d20734");
                imgURL.append("&chxt=x,y");
                imgURL.append("&chxr=1,0," + topValue);
                imgURL.append("&chbh=14");
                imgURL.append("&chd=t:");
                for (final String value: chartData.values()) imgURL.append(value).append(",");
                imgURL.delete(imgURL.length() - 1, imgURL.length());
                imgURL.append("&chl=");
                int counter = 0;
                for (final String value: chartData.keySet()) {
                    if (counter % 3 == 0) {
                        imgURL.append(value).append("|");
                    } else {
                        imgURL.append(" |");
                    }
                    counter++;
                }
                imgURL.delete(imgURL.length() - 1, imgURL.length());
            %>
            <img src="<%=imgURL.toString()%>" alt="[ Google Chart Image ]"/>
        </td>
    </tr>
</table>
<br class="clear"/>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            Current Status
        </td>
    </tr>
    <tr>
        <td class="key">
            <a href="<pwm:url url='intruderstatus.jsp'/>">
                Locked Users
            </a>
        </td>
        <td>
            <a href="<pwm:url url='intruderstatus.jsp'/>">
                <%= numberFormat.format(contextManager.getIntruderManager().currentLockedUsers()) %>
            </a>
        </td>
        <td class="key">
            <a href="<pwm:url url='intruderstatus.jsp'/>">
                Locked Addresses
            </a>
        </td>
        <td>
            <a href="<pwm:url url='intruderstatus.jsp'/>">
                <%= numberFormat.format(contextManager.getIntruderManager().currentLockedAddresses()) %>
            </a>
        </td>
    </tr>
    <tr>
        <td class="key">
            <a href="<pwm:url url='activesessions.jsp'/>">
                Active HTTP Sessions
            </a>
        </td>
        <td>
            <a href="<pwm:url url='activesessions.jsp'/>">
                <%= contextManager.getPwmSessions().size() %>
            </a>
        </td>
        <td colspan="2">
            &nbsp;
        </td>
    </tr>
</table>
<br class="clear"/>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            Local PWM Database
        </td>
    </tr>
    <tr>
        <td class="key">
            Wordlist Dictionary Status
        </td>
        <td style="white-space:nowrap;">
            <%= contextManager.getWordlistManager().getDebugStatus() %>
        </td>
        <td class="key">
            Wordlist Dictionary Size
        </td>
        <td>
            <%= numberFormat.format(contextManager.getWordlistManager().size()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Seedlist Status
        </td>
        <td style="white-space:nowrap;">
            <%= contextManager.getSeedlistManager().getDebugStatus() %>
        </td>
        <td class="key">
            Seedlist Size
        </td>
        <td>
            <%= numberFormat.format(contextManager.getSeedlistManager().size()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Shared Password History Status
        </td>
        <td>
            <%= contextManager.getSharedHistoryManager().getStatus() %>
        </td>
        <td class="key">
            Shared Password History Size
        </td>
        <td>
            <%= numberFormat.format(contextManager.getSharedHistoryManager().size()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Oldest Shared Password Entry
        </td>
        <td>
            <% final long oldestEntryAge = contextManager.getSharedHistoryManager().getOldestEntryAge(); %>
            <%= oldestEntryAge == 0 ? "n/a" : TimeDuration.asCompactString(oldestEntryAge) %>
        </td>
        <td class="key">
            <a href="<pwm:url url='eventlog.jsp'/>">
                Log Events
            </a>
        </td>
        <td>
            <a href="<pwm:url url='eventlog.jsp'/>">
                <%= contextManager.getPwmDBLogger() != null ? numberFormat.format(contextManager.getPwmDBLogger().getEventCount()) : "n/a" %>
            </a>
        </td>
    </tr>
    <tr>
        <td class="key">
            Log Events in Write Queue
        </td>
        <td>
            <%= contextManager.getPwmDBLogger() != null ? numberFormat.format(contextManager.getPwmDBLogger().getPendingEventCount()) : "n/a" %>
        </td>
        <td class="key">
            Oldest Log Event
        </td>
        <td>
            <%= contextManager.getPwmDBLogger() != null ? TimeDuration.fromCurrent(contextManager.getPwmDBLogger().getTailTimestamp()).asCompactString() : "n/a" %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Oldest Log Event in Write Queue
        </td>
        <td>
            <%= contextManager.getPwmDBLogger().getDirtyQueueTime().asCompactString() %>
        </td>
        <td class="key">
            Database Size
        </td>
        <td>
            <%= Helper.formatDiskSize(contextManager.getPwmDbDiskSize()) %>
        </td>
    </tr>
</table>
<br class="clear"/>
<table>
    <tr>
        <td class="title" colspan="10">
            Java Status
        </td>
    </tr>
    <tr>
        <td class="key">
            Java Vendor
        </td>
        <td>
            <%= System.getProperty("java.vm.vendor") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Java Runtime Version
        </td>
        <td>
            <%= System.getProperty("java.runtime.version") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Java VM Version
        </td>
        <td>
            <%= System.getProperty("java.vm.version") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Java Name
        </td>
        <td>
            <%= System.getProperty("java.vm.name") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Java Home
        </td>
        <td>
            <%= System.getProperty("java.home") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            OS Name
        </td>
        <td>
            <%= System.getProperty("os.name") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            OS Version
        </td>
        <td>
            <%= System.getProperty("os.version") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Free Memory
        </td>
        <td>
            <%= numberFormat.format(Runtime.getRuntime().freeMemory()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Memory Allocated
        </td>
        <td>
            <%= numberFormat.format(Runtime.getRuntime().totalMemory()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Memory Limit
        </td>
        <td>
            <%= numberFormat.format(Runtime.getRuntime().maxMemory()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            <a href="<pwm:url url='threads.jsp'/>">Threads</a>
        </td>
        <td>
            <a href="<pwm:url url='threads.jsp'/>"><%= Thread.activeCount() %>
            </a>
        </td>
    </tr>
    <tr>
        <td class="key">
            Chai API Version
        </td>
        <td>
            <%= com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Chai API Build Number
        </td>
        <td>
            <%= com.novell.ldapchai.ChaiConstant.CHAI_API_BUILD_INFO %>
        </td>
    </tr>
</table>
</form>
</div>
</div>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>


