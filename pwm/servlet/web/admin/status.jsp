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

<%@ page import="com.novell.ldapchai.ChaiConstant" %>
<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.util.StatisticsManager" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="password.pwm.Helper" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ContextManager contextManager = ContextManager.getContextManager(this.getServletConfig().getServletContext()); %>
<% final StatisticsManager stats = ContextManager.getContextManager(session).getStatisticsManager(); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(); %>
<% final DateFormat dateFormat = DateFormat.getInstance(); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">

<div id="wrapper">
<jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Status"/></jsp:include>
<div id="centerbody">
<br class="clear"/>
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
            <%= stats.getCurrentStat(StatisticsManager.Statistic.PWM_START_TIME) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Current Time
        </td>
        <td>
            <%= new java.util.Date().toString() %>
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
            <%= stats.getCurrentStat(StatisticsManager.Statistic.LDAP_UNAVAILABLE_TIME) %>
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
        <td class="key">
            Active LDAP Connections
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.CURENT_LDAP_CONNECTIONS) %>
        </td>
    </tr>
</table>
<br class="clear"/>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            Counters
        </td>
    </tr>
    <tr>
        <td colspan="10" style="text-align: center">
            since pwm startup ( <%= stats.getCurrentStat(StatisticsManager.Statistic.PWM_START_TIME) %> )
        </td>
    </tr>
    <tr>
        <td class="key">
            Locked Users
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.LOCKED_USERS) %>
        </td>
        <td class="key">
            Locked Addresses
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.LOCKED_ADDRESSES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Servlet HTTP Requests
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.HTTP_REQUESTS) %>
        </td>
        <td class="key">
            PWM Logins
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.PWM_AUTHENTICATIONS) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Failed Logins
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.FAILED_LOGIN_ATTEMPTS) %>
        </td>
        <td class="key">
            Passwords Changed
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.PASSWORD_CHANGES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Activated Users
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.ACTIVATED_USERS) %>
        </td>
        <td class="key">
            New Users Created
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.NEW_USERS) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Password Recoveries
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.RECOVERY_SUCCESSES) %>
        </td>
        <td class="key">
            Password Recovery Attempts
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.RECOVERY_ATTEMPTS) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Emails Sent
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.EMAIL_SEND_SUCCESSES) %>
        </td>
        <td class="key">
            Email Send Failures
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.EMAIL_SEND_FAILURES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Captcha Attempts
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.CAPTCHA_SUCCESSES) %>
        </td>
        <td class="key">
            Captcha Failures
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.CAPTCHA_FAILURES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            HTTP Sessions
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.HTTP_SESSIONS) %>
        </td>
        <td class="key">
            LDAP Unavailable Errors
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.LDAP_UNAVAILABLE_COUNT) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Average Password Replicate Time
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.AVG_PASSWORD_SYNC_TIME) %>
            ms
        </td>
        <td class="key">
            Average word check time
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.AVG_WORDLIST_CHECK_TIME) %>
            ms
        </td>
    </tr>
    <tr>
        <td class="key">
            Real-Time Password Validations
        </td>
        <td>
            <%= stats.getCurrentStat(StatisticsManager.Statistic.PASSWORD_RULE_CHECKS) %>
        </td>
        <td colspan="2">
            &nbsp;
        </td>
    </tr>
</table>
<br class="clear"/>
<% if (stats.hasCummulativeValues()) { %>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            Cummulative Counters
        </td>
    </tr>
    <tr>
        <td colspan="10" style="text-align: center">
            since pwm installation ( <%= stats.getCumulativeStat(StatisticsManager.Statistic.PWM_INSTALL_TIME) %> )
        </td>
    </tr>
    <tr>
        <td class="key">
            Locked Users
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.LOCKED_USERS) %>
        </td>
        <td class="key">
            Locked Addresses
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.LOCKED_ADDRESSES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Servlet HTTP Requests
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.HTTP_REQUESTS) %>
        </td>
        <td class="key">
            PWM Logins
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.PWM_AUTHENTICATIONS) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Failed Logins
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.FAILED_LOGIN_ATTEMPTS) %>
        </td>
        <td class="key">
            Passwords Changed
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.PASSWORD_CHANGES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Activated Users
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.ACTIVATED_USERS) %>
        </td>
        <td class="key">
            New Users Created
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.NEW_USERS) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Password Recoveries
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.RECOVERY_SUCCESSES) %>
        </td>
        <td class="key">
            Password Recovery Attempts
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.RECOVERY_ATTEMPTS) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Emails Sent
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.EMAIL_SEND_SUCCESSES) %>
        </td>
        <td class="key">
            Email Send Failures
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.EMAIL_SEND_FAILURES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Captcha Attempts
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.CAPTCHA_SUCCESSES) %>
        </td>
        <td class="key">
            Captcha Failures
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.CAPTCHA_FAILURES) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            HTTP Sessions
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.HTTP_SESSIONS) %>
        </td>
        <td class="key">
            LDAP Unavailable Errors
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.LDAP_UNAVAILABLE_COUNT) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Real-Time Password Validations
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.PASSWORD_RULE_CHECKS) %>
        </td>
        <td class="key">
            PWM Startups
        </td>
        <td>
            <%= stats.getCumulativeStat(StatisticsManager.Statistic.PWM_STARTUPS) %>
        </td>
    </tr>
</table>
<% } %>
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
            Oldest Shared Password History Entry
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
            <%= ChaiConstant.CHAI_API_VERSION %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Chai API Build Number
        </td>
        <td>
            <%= ChaiConstant.CHAI_API_BUILD_INFO %>
        </td>
    </tr>
</table>
</div>
<br class="clear"/>
</div>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>


