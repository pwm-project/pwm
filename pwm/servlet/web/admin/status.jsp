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

<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="password.pwm.util.pwmdb.PwmDB" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ContextManager contextManager = ContextManager.getContextManager(this.getServletConfig().getServletContext()); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(request.getLocale()); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, request.getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="/WEB-INF/jsp/header.jsp" %>
<body class="tundra" onload="pwmPageLoadHandler();">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/header-body.jsp">
    <jsp:param name="pwm.PageName" value="PWM Status"/>
</jsp:include>
<div id="centerbody">
<%@ include file="admin-nav.jsp" %>
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
            <%= PwmConstants.SERVLET_VERSION %>
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
            Start Time
        </td>
        <td>
            <%= dateFormat.format(contextManager.getStartupTime()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Install Time
        </td>
        <td>
            <%= dateFormat.format(contextManager.getInstallTime()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Configuration Time
        </td>
        <td>
            <%= dateFormat.format(contextManager.getConfig().getModifyTime()) %>
            (epoch <%= contextManager.getConfigReader().getConfigurationEpoch() %>)
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
            <%= contextManager.getLastLdapFailure() != null ? dateFormat.format(contextManager.getLastLdapFailure().getDate()) : "n/a" %>
        </td>
    </tr>
    <tr>
        <td class="key">
            LDAP Vendor
        </td>
        <td>
            <%
                String vendor = "[detection error]";
                try {
                    vendor = contextManager.getProxyChaiProvider().getDirectoryVendor().toString();
                } catch (Exception e) { /* nothing */ }
            %>
            <%= vendor %>
        </td>
    </tr>
</table>
<br class="clear"/>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            PWM Health
        </td>
    </tr>
    <td colspan="10" style="border:0; margin:0; padding:0">
        <div id="healthBody" style="border:0; margin:0; padding:0"></div>
        <script type="text/javascript">
            dojo.addOnLoad(function() {
                showPwmHealth('healthBody', false);
            });
        </script>
    </td>
</table>
<p style="text-align:center; width:100%">Public PWM Health Page is at <a
        href="<%=request.getContextPath()%>/public/health.jsp"><%=request.getContextPath()%>/public/health.jsp</a></p>
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
            <%= contextManager.getSharedHistoryManager().status() %>
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
            Email Queue Status
        </td>
        <td>
            <%= contextManager.getEmailQueue().status() %>
        </td>
        <td class="key">
            Email Queue Size
        </td>
        <td>
            <%= contextManager.getEmailQueue().queueSize() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            SMS Queue Status
        </td>
        <td>
            <%= contextManager.getSmsQueue().status() %>
        </td>
        <td class="key">
            SMS Queue Size
        </td>
        <td>
            <%= contextManager.getSmsQueue().queueSize() %>
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
            <a href="<pwm:url url='eventlog.jsp'/>">
                Log Events
            </a>
        </td>
        <td>
            <a href="<pwm:url url='eventlog.jsp'/>">
                <%= contextManager.getPwmDBLogger() != null ? numberFormat.format(contextManager.getPwmDBLogger().getStoredEventCount()) : "n/a" %>
            </a>
        </td>
    </tr>
    <tr>
        <td class="key">
            Oldest Log Event in Write Queue
        </td>
        <td>
            <%= contextManager.getPwmDBLogger() != null ? contextManager.getPwmDBLogger().getDirtyQueueTime().asCompactString() : "n/a"%>
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
            Oldest Shared Password Entry
        </td>
        <td>
            <% final long oldestEntryAge = contextManager.getSharedHistoryManager().getOldestEntryAge(); %>
            <%= oldestEntryAge == 0 ? "n/a" : TimeDuration.asCompactString(oldestEntryAge) %>
        </td>
        <td class="key">
            PwmDB Size On Disk
        </td>
        <td>
            <%= contextManager.getPwmDB() == null ? "n/a" : contextManager.getPwmDB().getFileLocation() == null ? "n/a" : Helper.formatDiskSize(Helper.getFileDirectorySize(contextManager.getPwmDB().getFileLocation())) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            User Responses in PwmDB
        </td>
        <td>
            <%
                String responseCount = "n/a";
                try {
                    responseCount = String.valueOf(contextManager.getPwmDB().size(PwmDB.DB.RESPONSE_STORAGE));
                } catch (Exception e) { /* na */ }
            %>
            <%= responseCount %>
        </td>
        <td class="key">
            PwmDB Free Space
        </td>
        <td>
            <%= contextManager.getPwmDB() == null ? "n/a" : contextManager.getPwmDB().getFileLocation() == null ? "n/a" : Helper.formatDiskSize(Helper.diskSpaceRemaining(contextManager.getPwmDB().getFileLocation())) %>
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
            <a href="#threads">Threads</a>
        </td>
        <td>
            <a href="#threads"><%= Thread.activeCount() %>
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
<br class="clear"/>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            <a name="threads"></a>Java Threads
        </td>
    </tr>
    <tr>
        <td style="font-weight:bold;">
            Id
        </td>
        <td style="font-weight:bold;">
            Name
        </td>
        <td style="font-weight:bold;">
            Priority
        </td>
        <td style="font-weight:bold;">
            State
        </td>
        <td style="font-weight:bold;">
            Daemon
        </td>
    </tr>
    <%
        final Thread[] tArray = new Thread[Thread.activeCount()];
        Thread.enumerate(tArray);
        try {
            for (final Thread t : tArray) {
    %>
    <tr>
        <td>
            <%= t.getId() %>
        </td>
        <td>
            <%= t.getName() != null ? t.getName() : "n/a" %>
        </td>
        <td>
            <%= t.getPriority() %>
        </td>
        <td>
            <%= t.getState().toString().toLowerCase() %>
        </td>
        <td>
            <%= String.valueOf(t.isDaemon()) %>
        </td>
    </tr>
    <% } %>
    <% } catch (Exception e) { /* */ } %>
</table>
</div>
</div>
<%@ include file="/WEB-INF/jsp/footer.jsp" %>
</body>
</html>


