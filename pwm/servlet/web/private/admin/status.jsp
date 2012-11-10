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

<%@ page import="password.pwm.health.HealthRecord" %>
<%@ page import="password.pwm.servlet.ResourceFileServlet" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="password.pwm.util.pwmdb.PwmDB" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="PWM Status"/>
</jsp:include>
<div id="centerbody">
<%@ include file="admin-nav.jsp" %>
<div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
<div data-dojo-type="dijit.layout.ContentPane" title="About">
    <table>
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
                Current Published Version
            </td>
            <td>
                <%
                    String publishedVersion = "n/a";
                    if (pwmApplication != null && pwmApplication.getVersionChecker() != null) {
                        publishedVersion = pwmApplication.getVersionChecker().currentVersion();
                    }
                %>
                <%= publishedVersion %>
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
                <%= dateFormat.format(pwmApplication.getStartupTime()) %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Up Time
            </td>
            <td>
                <%= TimeDuration.fromCurrent(pwmApplication.getStartupTime()).asLongString() %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Install Time
            </td>
            <td>
                <%= dateFormat.format(pwmApplication.getInstallTime()) %>
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
                Instance ID
            </td>
            <td>
                <%= pwmApplication.getInstanceID() %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Last LDAP Unavailable Time
            </td>
            <td>
                <%= pwmApplication.getLastLdapFailure() != null ? dateFormat.format(pwmApplication.getLastLdapFailure().getDate()) : "n/a" %>
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
                        vendor = pwmApplication.getProxyChaiProvider().getDirectoryVendor().toString();
                    } catch (Exception e) { /* nothing */ }
                %>
                <%= vendor %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Chai API Version
            </td>
            <td>
                <%= com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION %> (<%= com.novell.ldapchai.ChaiConstant.CHAI_API_BUILD_INFO %>)
            </td>
        </tr>
        <tr>
            <td class="key">
                Dojo API Version
            </td>
            <td>
                <span id="dojoVersionSpan"></span>
                <script type="text/javascript">
                    require(["dojo"],function(dojo){
                        dojo.byId('dojoVersionSpan').innerHTML = dojo.version;
                    });
                </script>
            </td>
        </tr>
        <tr>
            <td class="key">
                Website
            </td>
            <td>
                <a target="pwmproject" href="<%=PwmConstants.PWM_URL_HOME%>">PWM Project</a>
            </td>
        </tr>
    </table>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Activity">
    <table class="tablemain">
        <tr>
            <td class="key">
                <a href="<pwm:url url='activesessions.jsp'/>">
                    Active HTTP Sessions
                </a>
            </td>
            <td>
                <a href="<pwm:url url='activesessions.jsp'/>">
                    <%= ContextManager.getContextManager(session).getPwmSessions().size() %>
                </a>
            </td>
            <td class="key">
                Active LDAP Connections
            </td>
            <td>
                <%= Helper.figureLdapConnectionCount(pwmApplication,ContextManager.getContextManager(session)) %>
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
                    <%= numberFormat.format(pwmApplication.getIntruderManager().currentLockedUsers()) %>
                </a>
            </td>
            <td class="key">
                <a href="<pwm:url url='intruderstatus.jsp'/>">
                    Locked Addresses
                </a>
            </td>
            <td>
                <a href="<pwm:url url='intruderstatus.jsp'/>">
                    <%= numberFormat.format(pwmApplication.getIntruderManager().currentLockedAddresses()) %>
                </a>
            </td>
        </tr>
    </table>
    <table class="tablemain">
        <tr>
            <td>
            </td>
            <td style="text-align: center; font-weight: bold;">
                Last Minute
            </td>
            <td style="text-align: center; font-weight: bold;">
                Last Hour
            </td>
            <td style="text-align: center; font-weight: bold;">
                Last Day
            </td>
        </tr>
        <% for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) { %>
        <tr>
            <td class="key">
                <%= loopEpsType.getDescription(pwmSessionHeader.getSessionStateBean().getLocale()) %> / Minute
            </td>
            <td style="text-align: center">
                <%= pwmApplication.getStatisticsManager().readEps(loopEpsType, Statistic.EpsDuration.MINUTE).multiply(BigDecimal.valueOf(60)).setScale(3, RoundingMode.UP) %>
            </td>
            <td style="text-align: center">
                <%= pwmApplication.getStatisticsManager().readEps(loopEpsType, Statistic.EpsDuration.HOUR).multiply(BigDecimal.valueOf(60)).setScale(3, RoundingMode.UP) %>
            </td>
            <td style="text-align: center">
                <%= pwmApplication.getStatisticsManager().readEps(loopEpsType, Statistic.EpsDuration.DAY).multiply(BigDecimal.valueOf(60)).setScale(3, RoundingMode.UP) %>
            </td>
        </tr>
        <% } %>
        <tr>
            <td colspan="10" style="text-align: center">
                <a onclick="location.reload()" href="#">refresh</a>
            </td>
        </tr>
    </table>
    <br/>
    <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
        <div data-dojo-type="dijit.layout.ContentPane" title="Authentications">
            <table class="tablemain">
                <tr>
                    <td colspan="10" style="margin:0; padding:0">
                        <div style="max-width: 600px; text-align: center">
                            <div id="EPS-GAUGE-AUTHENTICATION_MINUTE" style="float: left; width: 33%">Last Minute</div>
                            <div id="EPS-GAUGE-AUTHENTICATION_HOUR" style="float: left; width: 33%">Last Hour</div>
                            <div id="EPS-GAUGE-AUTHENTICATION_DAY" style="float: left; width: 33%">Last Day</div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
        <div data-dojo-type="dijit.layout.ContentPane" title="Password Changes">
            <table class="tablemain">
                <tr>
                    <td colspan="10" style="margin:0; padding:0">
                        <div style="max-width: 600px; text-align: center">
                            <div id="EPS-GAUGE-PASSWORD_CHANGES_MINUTE" style="float: left; width: 33%">Last Minute</div>
                            <div id="EPS-GAUGE-PASSWORD_CHANGES_HOUR" style="float: left; width: 33%">Last Hour</div>
                            <div id="EPS-GAUGE-PASSWORD_CHANGES_DAY" style="float: left; width: 33%">Last Day</div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
        <div data-dojo-type="dijit.layout.ContentPane" title="Intruder Attempts">
            <table class="tablemain">
                <tr>
                    <td colspan="10" style="margin:0; padding:0">
                        <div style="max-width: 600px; text-align: center">
                            <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_MINUTE" style="float: left; width: 33%">Last Minute</div>
                            <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_HOUR" style="float: left; width: 33%">Last Hour</div>
                            <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_DAY" style="float: left; width: 33%">Last Day</div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
        <div style="width: 100%; font-size: smaller; font-style: italic; text-align: center">events per hour, this content is dynamically refreshed</div>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Health">
    <div id="healthBody">
        <div id="WaitDialogBlank"></div>
    </div>
    <script type="text/javascript">
        require(["dojo/domReady!"],function(){
            showPwmHealth('healthBody', false, true);
        });
    </script>
    <div style="width: 100%; font-size: smaller; font-style: italic; text-align: center">
        public health page at
        <a href="<%=request.getContextPath()%>/public/health.jsp"><%=request.getContextPath()%>/public/health.jsp</a>
        , this content is dynamically refreshed
    </div>

</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Local PwmDB">
    <table class="tablemain">
        <tr>
            <td class="key">
                Wordlist Dictionary Status
            </td>
            <td style="white-space:nowrap;">
                <%= pwmApplication.getWordlistManager().getDebugStatus() %>
            </td>
            <td class="key">
                Wordlist Dictionary Size
            </td>
            <td>
                <%= numberFormat.format(pwmApplication.getWordlistManager().size()) %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Seedlist Status
            </td>
            <td style="white-space:nowrap;">
                <%= pwmApplication.getSeedlistManager().getDebugStatus() %>
            </td>
            <td class="key">
                Seedlist Size
            </td>
            <td>
                <%= numberFormat.format(pwmApplication.getSeedlistManager().size()) %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Shared Password History Status
            </td>
            <td>
                <%= pwmApplication.getSharedHistoryManager().status() %>
            </td>
            <td class="key">
                Shared Password History Size
            </td>
            <td>
                <%= numberFormat.format(pwmApplication.getSharedHistoryManager().size()) %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Email Queue Status
            </td>
            <td>
                <%= pwmApplication.getEmailQueue().status() %>
            </td>
            <td class="key">
                Email Queue Size
            </td>
            <td>
                <%= pwmApplication.getEmailQueue().queueSize() %>
            </td>
        </tr>
        <tr>
            <td class="key">
                SMS Queue Status
            </td>
            <td>
                <%= pwmApplication.getSmsQueue().status() %>
            </td>
            <td class="key">
                SMS Queue Size
            </td>
            <td>
                <%= pwmApplication.getSmsQueue().queueSize() %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Intruder Address Table Size
            </td>
            <td>
                <%= pwmApplication.getIntruderManager().currentAddressTableSize() %>
            </td>
            <td class="key">
                Intruder User Table Size
            </td>
            <td>
                <%= pwmApplication.getIntruderManager().currentUserTableSize() %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Log Events in Write Queue
            </td>
            <td>
                <%= pwmApplication.getPwmDBLogger() != null ? numberFormat.format(pwmApplication.getPwmDBLogger().getPendingEventCount()) : "n/a" %>
            </td>
            <td class="key">
                <a href="<pwm:url url='eventlog.jsp'/>">
                    Log Events
                </a>
            </td>
            <td>
                <a href="<pwm:url url='eventlog.jsp'/>">
                    <%= pwmApplication.getPwmDBLogger() != null ? numberFormat.format(pwmApplication.getPwmDBLogger().getStoredEventCount()) : "n/a" %>
                </a>
            </td>
        </tr>
        <tr>
            <td class="key">
                Oldest Log Event in Write Queue
            </td>
            <td>
                <%= pwmApplication.getPwmDBLogger() != null ? pwmApplication.getPwmDBLogger().getDirtyQueueTime().asCompactString() : "n/a"%>
            </td>
            <td class="key">
                Oldest Log Event
            </td>
            <td>
                <%= pwmApplication.getPwmDBLogger() != null ? TimeDuration.fromCurrent(pwmApplication.getPwmDBLogger().getTailDate()).asCompactString() : "n/a" %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Oldest Shared Password Entry
            </td>
            <td>
                <% final long oldestEntryAge = pwmApplication.getSharedHistoryManager().getOldestEntryAge(); %>
                <%= oldestEntryAge == 0 ? "n/a" : TimeDuration.asCompactString(oldestEntryAge) %>
            </td>
            <td class="key">
                PwmDB Size On Disk
            </td>
            <td>
                <%= pwmApplication.getPwmDB() == null ? "n/a" : pwmApplication.getPwmDB().getFileLocation() == null ? "n/a" : Helper.formatDiskSize(Helper.getFileDirectorySize(pwmApplication.getPwmDB().getFileLocation())) %>
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
                        responseCount = String.valueOf(pwmApplication.getPwmDB().size(PwmDB.DB.RESPONSE_STORAGE));
                    } catch (Exception e) { /* na */ }
                %>
                <%= responseCount %>
            </td>
            <td class="key">
                PwmDB Free Space
            </td>
            <td>
                <%= pwmApplication.getPwmDB() == null ? "n/a" : pwmApplication.getPwmDB().getFileLocation() == null ? "n/a" : Helper.formatDiskSize(Helper.diskSpaceRemaining(pwmApplication.getPwmDB().getFileLocation())) %>
            </td>
        </tr>
    </table>
    <% if (pwmApplication.getPwmDB() != null && "true".equalsIgnoreCase(request.getParameter("showPwmDBCounts"))) { %>
    <table class="tablemain">
        <tr>
            <td class="key">
                Name
            </td>
            <td class="key" style="text-align: left">
                Record Count
            </td>
        </tr>
        <% for (final PwmDB.DB loopDB : PwmDB.DB.values()) { %>
        <tr>
            <td style="text-align: right">
                <%= loopDB %>
            </td>
            <td>
                <%= pwmApplication.getPwmDB().size(loopDB) %>
            </td>
        </tr>
        <% } %>
    </table>
    <% } else { %>
    <div style="text-align:center; width:100%; border: 0">
        <a onclick="showWaitDialog()" href="status.jsp?showPwmDBCounts=true">Show PwmDB record counts</a> (may be slow to load)
    </div>
    <% } %>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Services">
    <table>
        <tr>
            <td style="font-weight:bold;">
                Service
            </td>
            <td style="font-weight:bold;">
                Status
            </td>
            <td style="font-weight:bold;">
                Health
            </td>
        </tr>
        <% for (final password.pwm.PwmService loopService : pwmApplication.getPwmServices()) { %>
        <tr>
            <td>
                <%= loopService.getClass().getSimpleName() %>
            </td>
            <td>
                <%= loopService.status() %>
                <% List<HealthRecord> healthRecords = loopService.healthCheck(); %>
            </td>
            <td>
                <% if (healthRecords != null && !healthRecords.isEmpty()) { %>
                <% for (HealthRecord loopRecord : healthRecords) { %>
                <%= loopRecord.getTopic() %> - <%= loopRecord.getStatus().toString() %> - <%= loopRecord.getDetail() %>
                <br/>
                <% } %>
                <% } else { %>
                No Issues
                <% } %>
            </td>
        </tr>
        <% } %>
    </table>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Java">
    <table>
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
                ResourceFileServlet Cache
            </td>
            <td>
                <%= numberFormat.format(ResourceFileServlet.itemsInCache(session.getServletContext())) %>
                (<%= numberFormat.format(ResourceFileServlet.bytesInCache(session.getServletContext())) %>)
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
                Threads
            </td>
            <td>
                <%= Thread.activeCount() %>
            </td>
        </tr>
    </table>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Java Threads">
    <div style="max-height: 400px; overflow: auto;">
        <table class="tablemain">
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
</div>
</div>
</div>
<script type="text/javascript">
    function startupPage() {
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog"],function(dojoParser){
            dojoParser.parse();

            showStatChart('PASSWORD_CHANGES',14,'statsChart');
            setInterval(function(){
                showStatChart('PASSWORD_CHANGES',14,'statsChart');
            }, 61 * 1000);
        });
    }
    startupPage();
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


