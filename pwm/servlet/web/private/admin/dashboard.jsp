<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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

<%@ page import="password.pwm.config.LdapProfile" %>
<%@ page import="password.pwm.config.option.DataStorageMethod" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.health.HealthRecord" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.ResourceFileServlet" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.localdb.LocalDB" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.*" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final Locale locale = JspUtility.locale(request);
    final NumberFormat numberFormat = NumberFormat.getInstance(locale);
    final DateFormat dateFormat = PwmConstants.DEFAULT_DATETIME_FORMAT;
    final Map<Thread,StackTraceElement[]> threads = Thread.getAllStackTraces();

    PwmApplication dashboard_pwmApplication = null;
    PwmSession dashboard_pwmSession = null;
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        dashboard_pwmApplication = pwmRequest.getPwmApplication();
        dashboard_pwmSession = pwmRequest.getPwmSession();
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Dashboard"/>
</jsp:include>
<div id="centerbody">
<%@ include file="admin-nav.jsp" %>
<div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
<div data-dojo-type="dijit.layout.ContentPane" title="Status">
    <table>
        <tr>
            <td class="key">
                <pwm:display key="Title_Sessions" bundle="Admin"/>
            </td>
            <td>
                <%= ContextManager.getContextManager(session).getPwmSessions().size() %>
            </td>
            <td class="key">
                <pwm:display key="Title_LDAPConnections" bundle="Admin"/>

            </td>
            <td>
                <%= Helper.figureLdapConnectionCount(dashboard_pwmApplication, ContextManager.getContextManager(session)) %>
            </td>
        </tr>
    </table>
    <table class="tablemain">
        <tr>
            <td>
            </td>
            <td style="text-align: center; font-weight: bold;">
                <pwm:display key="Title_LastMinute" bundle="Admin"/>
            </td>
            <td style="text-align: center; font-weight: bold;">
                <pwm:display key="Title_LastHour" bundle="Admin"/>
            </td>
            <td style="text-align: center; font-weight: bold;">
                <pwm:display key="Title_LastDay" bundle="Admin"/>
            </td>
        </tr>
        <% for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) { %>
        <% if ((loopEpsType != Statistic.EpsType.DB_READS && loopEpsType != Statistic.EpsType.DB_WRITES) || dashboard_pwmApplication.getConfig().hasDbConfigured()) { %>
        <tr>
            <td class="key">
                <%= loopEpsType.getLabel(dashboard_pwmSession.getSessionStateBean().getLocale()) %> / Minute
            </td>
            <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_MINUTE">
                <span style="font-size: smaller; font-style: italic"><pwm:display key="Display_PleaseWait"/></span>
            </td>
            <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_HOUR">
                <span style="font-size: smaller; font-style: italic"><pwm:display key="Display_PleaseWait"/></span>
            </td>
            <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_DAY">
                <span style="font-size: smaller; font-style: italic"><pwm:display key="Display_PleaseWait"/></span>
            </td>
        </tr>
        <% } %>
        <% } %>
    </table>
    <br/>
    <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
        <div data-dojo-type="dijit.layout.ContentPane" title="Last Minute">
            <table class="tablemain">
                <tr>
                    <td colspan="10" style="margin:0; padding:0">
                        <div style="max-width: 600px; text-align: center">
                            <div id="EPS-GAUGE-AUTHENTICATION_MINUTE" style="float: left; width: 33%">Authentications</div>
                            <div id="EPS-GAUGE-PASSWORD_CHANGES_MINUTE" style="float: left; width: 33%">Password Changes</div>
                            <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_MINUTE" style="float: left; width: 33%">Intruder Attempts</div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
        <div data-dojo-type="dijit.layout.ContentPane" title="Last Hour">
            <table class="tablemain">
                <tr>
                    <td colspan="10" style="margin:0; padding:0">
                        <div style="max-width: 600px; text-align: center">
                            <div id="EPS-GAUGE-AUTHENTICATION_HOUR" style="float: left; width: 33%">Authentications</div>
                            <div id="EPS-GAUGE-PASSWORD_CHANGES_HOUR" style="float: left; width: 33%">Password Changes</div>
                            <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_HOUR" style="float: left; width: 33%">Intruder Attempts</div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
        <div data-dojo-type="dijit.layout.ContentPane" title="Last Day">
            <table class="tablemain">
                <tr>
                    <td colspan="10" style="margin:0; padding:0">
                        <div style="max-width: 600px; text-align: center">
                            <div id="EPS-GAUGE-AUTHENTICATION_DAY" style="float: left; width: 33%">Authentications</div>
                            <div id="EPS-GAUGE-PASSWORD_CHANGES_DAY" style="float: left; width: 33%">Password Changes</div>
                            <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_DAY" style="float: left; width: 33%">Intruder Attempts</div>
                        </div>
                    </td>
                </tr>
            </table>
        </div>
        <div class="noticebar">Events rates are per minute.  <pwm:display key="Notice_DynamicRefresh" bundle="Admin"/></div>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Health">
    <div id="healthBody">
        <div class="WaitDialogBlank"></div>
    </div>
    <br/>
    <div class="noticebar">
        <pwm:display key="Notice_DynamicRefresh" bundle="Admin"/>  A public health page at
        <a href="<pwm:context/>/public/health.jsp"><pwm:context/>/public/health.jsp</a>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_About" bundle="Admin"/>">
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
            <% if (dashboard_pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) { %>
            <tr>
                <td class="key">
                    Current Published Version
                </td>
                <td>
                    <%
                        String publishedVersion = Display.getLocalizedMessage(dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig());
                        Date readDate = null;
                        if (dashboard_pwmApplication != null && dashboard_pwmApplication.getVersionChecker() != null) {
                            publishedVersion = dashboard_pwmApplication.getVersionChecker().currentVersion();
                            readDate = dashboard_pwmApplication.getVersionChecker().lastReadTimestamp();

                        }
                    %>
                    <%= publishedVersion %>
                    <% if (readDate != null) { %>
                    as of <span class="timestamp"><%=dateFormat.format(readDate)%></span>
                    <% } %>
                </td>
            </tr>
            <% } %>
            <tr>
                <td class="key">
                    <pwm:display key="Field_CurrentTime" bundle="Admin"/>
                </td>
                <td class="timestamp">
                    <%= dateFormat.format(new java.util.Date()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_StartTime" bundle="Admin"/>
                </td>
                <td class="timestamp">
                    <%= dateFormat.format(dashboard_pwmApplication.getStartupTime()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Up Time
                </td>
                <td>
                    <%= TimeDuration.fromCurrent(dashboard_pwmApplication.getStartupTime()).asLongString() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_InstallTime" bundle="Admin"/>
                </td>
                <td class="timestamp">
                    <%= dateFormat.format(dashboard_pwmApplication.getInstallTime()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Site URL
                </td>
                <td>
                    <%= dashboard_pwmApplication.getConfig().readSettingAsString(PwmSetting.PWM_SITE_URL) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Instance ID
                </td>
                <td>
                    <%= dashboard_pwmApplication.getInstanceID() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Last LDAP Unavailable Time
                </td>
                <% final Collection<LdapProfile> ldapProfiles = dashboard_pwmApplication.getConfig().getLdapProfiles().values(); %>
                <td>
                    <% if (ldapProfiles.size() < 2) { %>
                    <% Date lastError = dashboard_pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfiles.iterator().next()); %>
                    <span class="timestamp">
                    <%= lastError == null ? Display.getLocalizedMessage(dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) : dateFormat.format(lastError) %>
                        </span>
                    <% } else { %>
                    <table>
                        <% for (LdapProfile ldapProfile : ldapProfiles) { %>
                        <tr>
                            <td><%=ldapProfile.getDisplayName(dashboard_pwmSession.getSessionStateBean().getLocale())%></td>
                            <td class="timestamp">
                                <% Date lastError = dashboard_pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfile); %>
                                <%= lastError == null ? Display.getLocalizedMessage(dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) : dateFormat.format(lastError) %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                    <% } %>
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
                    <pwm:script>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dojo"],function(dojo){
                                dojo.byId('dojoVersionSpan').innerHTML = dojo.version;
                            });
                        });
                    </script>
                    </pwm:script>
                </td>
            </tr>
            <tr>
                <td class="key">
                    License Information
                </td>
                <td>
                    <a href="<pwm:context/><pwm:url url="/public/license.jsp"/>">License Information</a>
                </td>
            </tr>
        </table>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Services">
    <table>
        <tr>
            <th style="font-weight:bold;">
                Service
            </td>
            <td style="font-weight:bold;">
                Status
            </td>
            <td style="font-weight:bold;">
                Storage
            </td>
            <td style="font-weight:bold;">
                Health
            </td>
        </tr>
        <% for (final password.pwm.PwmService loopService : dashboard_pwmApplication.getPwmServices()) { %>
        <tr>
            <td>
                <%= loopService.getClass().getSimpleName() %>
            </td>
            <td>
                <%= loopService.status() %>
                <% List<HealthRecord> healthRecords = loopService.healthCheck(); %>
            </td>
            <td>
                <% if (loopService.serviceInfo() != null && loopService.serviceInfo().getUsedStorageMethods() != null) { %>
                <% for (DataStorageMethod loopMethod : loopService.serviceInfo().getUsedStorageMethods()) { %>
                <%=loopMethod.toString()%>
                <br/>
                <% } %>
                <% } %>
            </td>
            <td>
                <% if (healthRecords != null && !healthRecords.isEmpty()) { %>
                <% for (HealthRecord loopRecord : healthRecords) { %>
                <%= loopRecord.getTopic(locale, dashboard_pwmApplication.getConfig()) %> - <%= loopRecord.getStatus().toString() %> - <%= loopRecord.getDetail(locale,
                    dashboard_pwmApplication.getConfig()) %>
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
<div data-dojo-type="dijit.layout.ContentPane" title="LocalDB">
    <div style="max-height: 400px; overflow: auto;">
        <table class="tablemain">
            <tr>
                <td class="key">
                    Wordlist Dictionary Size
                </td>
                <td>
                    <%= numberFormat.format(dashboard_pwmApplication.getWordlistManager().size()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Seedlist Size
                </td>
                <td>
                    <%= numberFormat.format(dashboard_pwmApplication.getSeedlistManager().size()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Shared Password History Size
                </td>
                <td>
                    <%= numberFormat.format(dashboard_pwmApplication.getSharedHistoryManager().size()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Email Queue Size
                </td>
                <td>
                    <%= dashboard_pwmApplication.getEmailQueue().queueSize() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    SMS Queue Size
                </td>
                <td>
                    <%= dashboard_pwmApplication.getSmsQueue().queueSize() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Syslog Queue Size
                </td>
                <td>
                    <%= dashboard_pwmApplication.getAuditManager().syslogQueueSize() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Local Audit Records
                </td>
                <td>
                    <%= numberFormat.format(dashboard_pwmApplication.getAuditManager().vaultSize()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Log Events in Write Queue
                </td>
                <td>
                    <%= dashboard_pwmApplication.getLocalDBLogger() != null ? numberFormat.format(
                            dashboard_pwmApplication.getLocalDBLogger().getPendingEventCount()) : Display.getLocalizedMessage(
                            dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <a href="<pwm:url url='eventlog.jsp'/>">
                        Log Events in LocalDB
                    </a>
                </td>
                <td>
                    <a href="<pwm:url url='eventlog.jsp'/>">
                        <%= dashboard_pwmApplication.getLocalDBLogger().sizeToDebugString() %>
                    </a>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Log Event in Write Queue
                </td>
                <td>
                    <%= dashboard_pwmApplication.getLocalDBLogger() != null ? dashboard_pwmApplication.getLocalDBLogger().getDirtyQueueTime().asCompactString() : Display.getLocalizedMessage(
                            dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Log Event in LocalDB
                </td>
                <td>
                    <%= dashboard_pwmApplication.getLocalDBLogger() != null ? TimeDuration.fromCurrent(
                            dashboard_pwmApplication.getLocalDBLogger().getTailDate()).asCompactString() : Display.getLocalizedMessage(
                            dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Shared Password Entry
                </td>
                <td>
                    <% final long oldestEntryAge = dashboard_pwmApplication.getSharedHistoryManager().getOldestEntryAge(); %>
                    <%= oldestEntryAge == 0 ? Display.getLocalizedMessage(dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) : TimeDuration.asCompactString(oldestEntryAge) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    LocalDB Size On Disk
                </td>
                <td>
                    <%= dashboard_pwmApplication.getLocalDB() == null ? Display.getLocalizedMessage(
                            dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) : dashboard_pwmApplication.getLocalDB().getFileLocation() == null ? Display.getLocalizedMessage(
                            dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) : Helper.formatDiskSize(Helper.getFileDirectorySize(
                            dashboard_pwmApplication.getLocalDB().getFileLocation())) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    User Responses in LocalDB
                </td>
                <td>
                    <%
                        String responseCount = Display.getLocalizedMessage(dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig());
                        try {
                            responseCount = String.valueOf(dashboard_pwmApplication.getLocalDB().size(LocalDB.DB.RESPONSE_STORAGE));
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
                    <%= dashboard_pwmApplication.getLocalDB() == null ? Display.getLocalizedMessage(
                            dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) : dashboard_pwmApplication.getLocalDB().getFileLocation() == null ? Display.getLocalizedMessage(
                            dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) : Helper.formatDiskSize(
                            Helper.diskSpaceRemaining(dashboard_pwmApplication.getLocalDB().getFileLocation())) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Configuration Restart Counter
                </td>
                <td>
                    <%= ContextManager.getContextManager(request.getSession()).getRestartCount() %>
                </td>
            </tr>
        </table>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="LocalDB Sizes">
    <% if (dashboard_pwmApplication.getLocalDB() != null && "true".equalsIgnoreCase(request.getParameter("showLocalDBCounts"))) { %>
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
                <%= numberFormat.format(dashboard_pwmApplication.getLocalDB().size(loopDB)) %>
            </td>
        </tr>
        <% } %>
    </table>
    <% } else { %>
    <div style="text-align:center; width:100%; border: 0">
        <a style="cursor: pointer" onclick="PWM_MAIN.goto('dashboard.jsp?showLocalDBCounts=true')">Show LocalDB record counts</a> (may be slow to load)
    </div>
    <% } %>
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
                <%= threads.size() %>
            </td>
        </tr>
    </table>
    <table>
        <tr>
            <td class="key">
                ResourceFileServlet Cache
            </td>
            <td>
                <%= numberFormat.format(ResourceFileServlet.itemsInCache(session.getServletContext())) %> items
                (<%= numberFormat.format(ResourceFileServlet.bytesInCache(session.getServletContext())) %> bytes)
            </td>
        </tr>
        <tr>
            <td class="key">
                ResourceFileServlet Cache Hit Ratio
            </td>
            <td>
                <%= ResourceFileServlet.cacheHitRatio(session.getServletContext()).pretty(2) %>
            </td>
        </tr>
        <% Map<ContextManager.DebugKey,String> debugInfoMap = ContextManager.getContextManager(session).getDebugData(); %>
        <tr>
            <td class="key">
                Session Total Size
            </td>
            <td>
                <%= numberFormat.format(Integer.valueOf(debugInfoMap.get(ContextManager.DebugKey.HttpSessionTotalSize))) %> bytes
            </td>
        </tr>
        <tr>
            <td class="key">
                Session Average Size
            </td>
            <td>
                <%= numberFormat.format(Integer.valueOf(debugInfoMap.get(ContextManager.DebugKey.HttpSessionAvgSize))) %> bytes
            </td>
        </tr>
    </table>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Threads">
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
                try {
                    final TreeMap<Long,Thread> sortedThreads = new TreeMap<Long, Thread>();
                    for (final Thread t : threads.keySet()) {
                        sortedThreads.put(t.getId(),t);
                    }

                    for (final Thread t : sortedThreads.values()) {
            %>
            <tr id="thread_<%=t.getId()%>">
                <td>
                    <%= t.getId() %>
                </td>
                <td>
                    <%= t.getName() != null ? t.getName() : Display.getLocalizedMessage(dashboard_pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", dashboard_pwmApplication.getConfig()) %>
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
            <%
                final StringBuilder threadTrace = new StringBuilder();
                for (StackTraceElement traceElement : threads.get(t)) {
                    threadTrace.append(traceElement.toString());
                    threadTrace.append("\n");
                }
            %>
            <pwm:script>
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    showTooltip('thread_<%=t.getId()%>','<%=StringUtil.escapeJS(threadTrace.toString())%>');
                });
            </script>
            </pwm:script>
            <% } %>
            <% } catch (Exception e) { /* */ } %>
        </table>
    </div>
</div>
</div>
</div>
<div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dojo/ready","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog","dojo/domReady!"],function(dojoParser,ready){
            ready(function(){
                dojoParser.parse();
                ready(function(){
                    PWM_ADMIN.showStatChart('PASSWORD_CHANGES',14,'statsChart',{refreshTime:11*1000});
                    PWM_ADMIN.showAppHealth('healthBody', {showRefresh:true,showTimestamp:true});
                });
            });
        });
    });

    function showTooltip(nodeID, displayText) {
        PWM_MAIN.showTooltip({
            id:nodeID,
            position: ['below','above'],
            text: '<pre>' + displayText + '</pre>'
        });
    }
</script>
</pwm:script>
<script type="text/javascript" src="<pwm:context/><pwm:url url='/public/resources/js/admin.js'/>"></script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


