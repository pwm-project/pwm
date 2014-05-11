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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.config.LdapProfile" %>
<%@ page import="password.pwm.config.option.DataStorageMethod" %>
<%@ page import="password.pwm.health.HealthRecord" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.servlet.ResourceFileServlet" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="password.pwm.util.localdb.LocalDB" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.*" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(locale); %>
<% final DateFormat dateFormat = PwmConstants.DEFAULT_DATETIME_FORMAT; %>
<% final Map<Thread,StackTraceElement[]> threads = Thread.getAllStackTraces(); %>
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
                <pwm:Display key="Title_Sessions" bundle="Admin"/>
            </td>
            <td>
                <%= ContextManager.getContextManager(session).getPwmSessions().size() %>
            </td>
            <td class="key">
                <pwm:Display key="Title_LDAPConnections" bundle="Admin"/>

            </td>
            <td>
                <%= Helper.figureLdapConnectionCount(pwmApplication, ContextManager.getContextManager(session)) %>
            </td>
        </tr>
    </table>
    <table class="tablemain">
        <tr>
            <td>
            </td>
            <td style="text-align: center; font-weight: bold;">
                <pwm:Display key="Title_LastMinute" bundle="Admin"/>
            </td>
            <td style="text-align: center; font-weight: bold;">
                <pwm:Display key="Title_LastHour" bundle="Admin"/>
            </td>
            <td style="text-align: center; font-weight: bold;">
                <pwm:Display key="Title_LastDay" bundle="Admin"/>
            </td>
        </tr>
        <% for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) { %>
        <% if ((loopEpsType != Statistic.EpsType.DB_READS && loopEpsType != Statistic.EpsType.DB_WRITES) || pwmApplication.getConfig().hasDbConfigured()) { %>
        <tr>
            <td class="key">
                <%= loopEpsType.getDescription(pwmSessionHeader.getSessionStateBean().getLocale()) %> / Minute
            </td>
            <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_MINUTE">
                <span style="font-size: smaller; font-style: italic"><pwm:Display key="Display_PleaseWait"/></span>
            </td>
            <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_HOUR">
                <span style="font-size: smaller; font-style: italic"><pwm:Display key="Display_PleaseWait"/></span>
            </td>
            <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_DAY">
                <span style="font-size: smaller; font-style: italic"><pwm:Display key="Display_PleaseWait"/></span>
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
        <div class="noticebar">Events rates are per minute.  <pwm:Display key="Notice_DynamicRefresh" bundle="Admin"/></div>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="Health">
    <div id="healthBody">
        <div id="WaitDialogBlank"></div>
    </div>
    <br/>
    <div class="noticebar">
        <pwm:Display key="Notice_DynamicRefresh" bundle="Admin"/>  A public health page at
        <a href="<%=request.getContextPath()%>/public/health.jsp"><%=request.getContextPath()%>/public/health.jsp</a>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_About" bundle="Admin"/>">
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
                        String publishedVersion = Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig());
                        Date readDate = null;
                        if (pwmApplication != null && pwmApplication.getVersionChecker() != null) {
                            publishedVersion = pwmApplication.getVersionChecker().currentVersion();
                            readDate = pwmApplication.getVersionChecker().lastReadTimestamp();

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
                    <pwm:Display key="Field_CurrentTime" bundle="Admin"/>
                </td>
                <td class="timestamp">
                    <%= dateFormat.format(new java.util.Date()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:Display key="Field_StartTime" bundle="Admin"/>
                </td>
                <td class="timestamp">
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
                    <pwm:Display key="Field_InstallTime" bundle="Admin"/>
                </td>
                <td class="timestamp">
                    <%= dateFormat.format(pwmApplication.getInstallTime()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Site URL
                </td>
                <td>
                    <%= pwmApplication.getSiteURL() %>
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
                <% final Collection<LdapProfile> ldapProfiles = pwmApplication.getConfig().getLdapProfiles().values(); %>
                <td>
                    <% if (ldapProfiles.size() < 2) { %>
                    <% Date lastError = pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfiles.iterator().next()); %>
                    <span class="timestamp">
                    <%= lastError == null ? Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) : dateFormat.format(lastError) %>
                        </span>
                    <% } else { %>
                    <table>
                        <% for (LdapProfile ldapProfile : ldapProfiles) { %>
                        <tr>
                            <td><%=ldapProfile.getDisplayName(pwmSessionHeader.getSessionStateBean().getLocale())%></td>
                            <td class="timestamp">
                                <% Date lastError = pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfile); %>
                                <%= lastError == null ? Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) : dateFormat.format(lastError) %>
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
                Storage
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
                    Syslog Queue Size
                </td>
                <td>
                    <%= pwmApplication.getAuditManager().syslogQueueSize() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Local Audit Records
                </td>
                <td>
                    <%= numberFormat.format(pwmApplication.getAuditManager().vaultSize()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Log Events in Write Queue
                </td>
                <td>
                    <%= pwmApplication.getLocalDBLogger() != null ? numberFormat.format(pwmApplication.getLocalDBLogger().getPendingEventCount()) : Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) %>
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
                        <%= pwmApplication.getLocalDBLogger().sizeToDebugString() %>
                    </a>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Log Event in Write Queue
                </td>
                <td>
                    <%= pwmApplication.getLocalDBLogger() != null ? pwmApplication.getLocalDBLogger().getDirtyQueueTime().asCompactString() : Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Log Event in LocalDB
                </td>
                <td>
                    <%= pwmApplication.getLocalDBLogger() != null ? TimeDuration.fromCurrent(pwmApplication.getLocalDBLogger().getTailDate()).asCompactString() : Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Oldest Shared Password Entry
                </td>
                <td>
                    <% final long oldestEntryAge = pwmApplication.getSharedHistoryManager().getOldestEntryAge(); %>
                    <%= oldestEntryAge == 0 ? Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) : TimeDuration.asCompactString(oldestEntryAge) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    LocalDB Size On Disk
                </td>
                <td>
                    <%= pwmApplication.getLocalDB() == null ? Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) : pwmApplication.getLocalDB().getFileLocation() == null ? Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) : Helper.formatDiskSize(Helper.getFileDirectorySize(pwmApplication.getLocalDB().getFileLocation())) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    User Responses in LocalDB
                </td>
                <td>
                    <%
                        String responseCount = Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig());
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
                    <%= pwmApplication.getLocalDB() == null ? Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) : pwmApplication.getLocalDB().getFileLocation() == null ? Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) : Helper.formatDiskSize(
                            Helper.diskSpaceRemaining(pwmApplication.getLocalDB().getFileLocation())) %>
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
                <%= numberFormat.format(pwmApplication.getLocalDB().size(loopDB)) %>
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
                ResourceFileServlet Cache
            </td>
            <td>
                <%= numberFormat.format(ResourceFileServlet.itemsInCache(session.getServletContext())) %>
                (<%= numberFormat.format(ResourceFileServlet.bytesInCache(session.getServletContext())) %>)
            </td>
        </tr>
        <% Map<ContextManager.DebugKey,String> debugInfoMap = ContextManager.getContextManager(session).getDebugData(); %>
        <tr>
            <td class="key">
                Session Total Size
            </td>
            <td>
                <%= numberFormat.format(Integer.valueOf(debugInfoMap.get(ContextManager.DebugKey.HttpSessionTotalSize))) %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Session Average Size
            </td>
            <td>
                <%= numberFormat.format(Integer.valueOf(debugInfoMap.get(ContextManager.DebugKey.HttpSessionAvgSize))) %>
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
                    <%= t.getName() != null ? t.getName() : Display.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig()) %>
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
            <script type="application/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    showTooltip('thread_<%=t.getId()%>','<%=StringEscapeUtils.escapeJavaScript(threadTrace.toString())%>');
                });
            </script>
            <% } %>
            <% } catch (Exception e) { /* */ } %>
        </table>
    </div>
</div>
</div>
</div>
<div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dojo/ready","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog","dojo/domReady!"],function(dojoParser,ready){
            ready(function(){
                dojoParser.parse();

                PWM_ADMIN.showStatChart('PASSWORD_CHANGES',14,'statsChart',{refreshTime:11*1000});
                PWM_ADMIN.showAppHealth('healthBody', {showRefresh:true,showTimestamp:true});
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
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/admin.js'/>"></script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


