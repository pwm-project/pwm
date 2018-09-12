<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.receiver.SummaryBean" %>
<%@ page import="password.pwm.receiver.TelemetryViewerServlet" %>
<%@ page import="java.time.Instant" %>
<%@ page import="password.pwm.receiver.PwmReceiverApp" %>
<%@ page import="password.pwm.receiver.ContextManager" %>

<!DOCTYPE html>
<%@ page contentType="text/html" %>
<% SummaryBean summaryBean = (SummaryBean)request.getAttribute(TelemetryViewerServlet.SUMMARY_ATTR); %>
<% PwmReceiverApp app = ContextManager.getContextManager(request.getServletContext()).getApp(); %>
<html>
<head>
    <title>Telemetry Data</title>
</head>
<body>
<div>
    Current Time: <%=Instant.now().toString()%>
    <br/>
    <% if (app.getSettings().isFtpEnabled()) {%>
    <% Instant lastIngest = app.getStatus().getLastFtpIngest(); %>
    Last FTP Ingestion: <%= lastIngest == null ? "n/a" : lastIngest.toString()%>
    <br/>
    Last FTP Status: <%= app.getStatus().getLastFtpStatus()%>
    <br/>
    FTP Files On Server: <%= app.getStatus().getLastFtpFilesRead()%>
    <br/>
    <% } %>
    Servers Registered: <%= app.getStorage().count() %>
    <br/>
    Servers Shown: <%= summaryBean.getServerCount() %>
    <br/>
    <br/>

    <form method="get">
        <label>Servers that have sent data in last number of days
            <input type="number" name="days" id="days" value="30" max="3650" min="1">
        </label>
        <button type="submit">Update</button>
    </form>

    <h2>Versions</h2>
    <table border="1">
        <tr>
            <td><b>Version</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String version : summaryBean.getSsprVersionCount().keySet()) { %>
        <tr>
            <td><%=version%></td>
            <td><%=summaryBean.getSsprVersionCount().get(version)%></td>
        </tr>
        <% } %>
    </table>
    <h2>LDAP Vendors</h2>
    <table border="1">
        <tr>
            <td><b>Ldap</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String ldapVendor : summaryBean.getLdapVendorCount().keySet()) { %>
        <tr>
            <td><%=ldapVendor%></td>
            <td><%=summaryBean.getLdapVendorCount().get(ldapVendor)%></td>
        </tr>
        <% } %>
    </table>
    <h2>App Servers</h2>
    <table border="1">
        <tr>
            <td><b>App Server Info</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String appServerInfo : summaryBean.getAppServerCount().keySet()) { %>
        <tr>
            <td><%=appServerInfo%></td>
            <td><%=summaryBean.getAppServerCount().get(appServerInfo)%></td>
        </tr>
        <% } %>
    </table>
    <h2>OS Vendors</h2>
    <table border="1">
        <tr>
            <td><b>OS Vendor</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String osName : summaryBean.getOsCount().keySet()) { %>
        <tr>
            <td><%=osName%></td>
            <td><%=summaryBean.getOsCount().get(osName)%></td>
        </tr>
        <% } %>
    </table>
    <h2>DB Vendors</h2>
    <table border="1">
        <tr>
            <td><b>DB Vendor</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String dbName : summaryBean.getDbCount().keySet()) { %>
        <tr>
            <td><%=dbName%></td>
            <td><%=summaryBean.getDbCount().get(dbName)%></td>
        </tr>
        <% } %>
    </table>
    <h2>Java VMs</h2>
    <table border="1">
        <tr>
            <td><b>Java VM</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String javaName : summaryBean.getJavaCount().keySet()) { %>
        <tr>
            <td><%=javaName%></td>
            <td><%=summaryBean.getJavaCount().get(javaName)%></td>
        </tr>
        <% } %>
    </table>
    <h2>Settings</h2>
    <table border="1">
        <tr>
            <td><b>Setting</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String setting: summaryBean.getSettingCount().keySet()) { %>
        <tr>
            <td><%=setting%></td>
            <td><%=summaryBean.getSettingCount().get(setting)%></td>
        </tr>
        <% } %>
    </table>
    <h2>Statistics</h2>
    <table border="1">
        <tr>
            <td><b>Statistic</b></td>
            <td><b>Count</b></td>
        </tr>
        <% for (final String statistic: summaryBean.getStatCount().keySet()) { %>
        <tr>
            <td><%=statistic%></td>
            <td><%=summaryBean.getStatCount().get(statistic)%></td>
        </tr>
        <% } %>
    </table>
    <br/>
    <h2>Summary Data</h2>
    <table border="1">
        <tr>
            <td><b>SiteHash</b></td>
            <td><b>Description</b></td>
            <td><b>Version</b></td>
            <td><b>Installed</b></td>
            <td><b>Last Updated</b></td>
            <td><b>Ldap</b></td>
            <td><b>OS Name</b></td>
            <td><b>OS Version</b></td>
            <td><b>Servlet Name</b></td>
            <td><b>DB Vendor</b></td>
            <td><b>Appliance</b></td>
        </tr>
        <% for (final String hashID : summaryBean.getSiteSummary().keySet()) { %>
        <% SummaryBean.SiteSummary siteSummary = summaryBean.getSiteSummary().get(hashID); %>
        <tr>
            <td style="max-width: 500px; overflow: auto"><%=hashID%></td>
            <td><%=siteSummary.getDescription()%></td>
            <td><%=siteSummary.getVersion()%></td>
            <td><%=siteSummary.getInstallAge()%></td>
            <td><%=siteSummary.getUpdateAge()%></td>
            <td><%=siteSummary.getLdapVendor()%></td>
            <td><%=siteSummary.getOsName()%></td>
            <td><%=siteSummary.getOsVersion()%></td>
            <td><%=siteSummary.getServletName()%></td>
            <td><%=siteSummary.getDbVendor()%></td>
            <td><%=siteSummary.isAppliance()%></td>
        </tr>
        <% } %>
    </table>
</div>
</body>
</html>
