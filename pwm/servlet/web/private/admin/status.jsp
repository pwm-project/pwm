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
<%@ page import="password.pwm.util.localdb.LocalDB" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(locale); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="System"/>
</jsp:include>
<div id="centerbody">
<%@ include file="admin-nav.jsp" %>
<div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
<div data-dojo-type="dijit.layout.ContentPane" title="About">
    <div style="max-height: 400px; overflow: auto;">
        <table>
            <tr>
                <td class="key">
                    <%=PwmConstants.PWM_APP_NAME%> Version
                </td>
                <td>
                    <%= PwmConstants.SERVLET_VERSION %>
                </td>
            </tr>
            <% if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) { %>
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
            <% } %>
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
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dojo"],function(dojo){
                                dojo.byId('dojoVersionSpan').innerHTML = dojo.version;
                            });
                        });
                    </script>
                </td>
            </tr>
            <tr>
                <td class="key">
                    License Information
                </td>
                <td>
                    <a href="<%=request.getContextPath()%><pwm:url url="/public/license.jsp"/>">License Information</a>
                </td>
            </tr>
        </table>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="LocalDB">
    <div style="max-height: 400px; overflow: auto;">
        <table class="tablemain">
            <tr>
                <td class="key">
                    Wordlist Dictionary Size
                </td>
                <td>
                    <%= numberFormat.format(pwmApplication.getWordlistManager().size()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Seedlist Size
                </td>
                <td>
                    <%= numberFormat.format(pwmApplication.getSeedlistManager().size()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Shared Password History Size
                </td>
                <td>
                    <%= numberFormat.format(pwmApplication.getSharedHistoryManager().size()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Email Queue Size
                </td>
                <td>
                    <%= pwmApplication.getEmailQueue().queueSize() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    SMS Queue Size
                </td>
                <td>
                    <%= pwmApplication.getSmsQueue().queueSize() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <a href="<pwm:url url='auditlog.jsp'/>">
                        Local Audit Records
                    </a>
                </td>
                <td>
                    <a href="<pwm:url url='auditlog.jsp'/>">
                        <%= numberFormat.format(pwmApplication.getAuditManager().localSize()) %>
                    </a>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Intruder Address Table Size
                </td>
                <td>
                    <%= pwmApplication.getIntruderManager().addressRecordCount() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Intruder User Table Size
                </td>
                <td>
                    <%= pwmApplication.getIntruderManager().userRecordCount() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Log Events in Write Queue
                </td>
                <td>
                    <%= pwmApplication.getLocalDBLogger() != null ? numberFormat.format(pwmApplication.getLocalDBLogger().getPendingEventCount()) : "n/a" %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <a href="<pwm:url url='eventlog.jsp'/>">
                        Log Events
                    </a>
                </td>
                <td>
                    <a href="<pwm:url url='eventlog.jsp'/>">
                        <%= pwmApplication.getLocalDBLogger() != null ? numberFormat.format(pwmApplication.getLocalDBLogger().getStoredEventCount()) : "n/a" %>
                    </a>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Log Event in Write Queue
                </td>
                <td>
                    <%= pwmApplication.getLocalDBLogger() != null ? pwmApplication.getLocalDBLogger().getDirtyQueueTime().asCompactString() : "n/a"%>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Log Event
                </td>
                <td>
                    <%= pwmApplication.getLocalDBLogger() != null ? TimeDuration.fromCurrent(pwmApplication.getLocalDBLogger().getTailDate()).asCompactString() : "n/a" %>
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
            </tr>
            <tr>
                <td class="key">
                    LocalDB Size On Disk
                </td>
                <td>
                    <%= pwmApplication.getLocalDB() == null ? "n/a" : pwmApplication.getLocalDB().getFileLocation() == null ? "n/a" : Helper.formatDiskSize(Helper.getFileDirectorySize(pwmApplication.getLocalDB().getFileLocation())) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    User Responses in LocalDB
                </td>
                <td>
                    <%
                        String responseCount = "n/a";
                        try {
                            responseCount = String.valueOf(pwmApplication.getLocalDB().size(LocalDB.DB.RESPONSE_STORAGE));
                        } catch (Exception e) { /* na */ }
                    %>
                    <%= responseCount %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    LocalDB Free Space
                </td>
                <td>
                    <%= pwmApplication.getLocalDB() == null ? "n/a" : pwmApplication.getLocalDB().getFileLocation() == null ? "n/a" : Helper.formatDiskSize(Helper.diskSpaceRemaining(pwmApplication.getLocalDB().getFileLocation())) %>
                </td>
            </tr>
        </table>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="LocalDB Sizes">
    <% if (pwmApplication.getLocalDB() != null && "true".equalsIgnoreCase(request.getParameter("showLocalDBCounts"))) { %>
    <table class="tablemain">
        <tr>
            <td class="key">
                Name
            </td>
            <td class="key" style="text-align: left">
                Record Count
            </td>
        </tr>
        <% for (final LocalDB.DB loopDB : LocalDB.DB.values()) { %>
        <tr>
            <td style="text-align: right">
                <%= loopDB %>
            </td>
            <td>
                <%= pwmApplication.getLocalDB().size(loopDB) %>
            </td>
        </tr>
        <% } %>
    </table>
    <% } else { %>
    <div style="text-align:center; width:100%; border: 0">
        <a onclick="showWaitDialog()" href="status.jsp?showLocalDBCounts=true">Show LocalDB record counts</a> (may be slow to load)
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
                <%= loopRecord.getTopic(locale,pwmApplication.getConfig()) %> - <%= loopRecord.getStatus().toString() %> - <%= loopRecord.getDetail(locale,pwmApplication.getConfig()) %>
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
<div id="buttonbar">
    <button class="btn" type="button" onclick="showWaitDialog(null,null,function(){location.reload()})">Refresh</button>
</div>
</div>
<div class="push"></div>
</div>
<script type="text/javascript">
    function startupPage() {
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog"],function(dojoParser){
            dojoParser.parse();
        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        startupPage();
    });
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


