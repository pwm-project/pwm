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
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.lang.reflect.Method" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(session); %>
<% final password.pwm.config.Configuration pwmConfig = pwmSession.getConfig(); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Settings"/></jsp:include>
    <div id="centerbody">
        <p style="text-align:center;">
            <a href="status.jsp">Status</a> | <a href="eventlog.jsp">Event Log</a> | <a href="intruderstatus.jsp">Intruder Status</a> | <a href="activesessions.jsp">Active Sessions</a> | <a href="config.jsp">Configuration</a> | <a href="threads.jsp">Threads</a> | <a href="UserInformation">User Information</a>
        </p>
        <p>
            Configuration load time <%= (java.text.DateFormat.getDateTimeInstance()).format(new Date(ContextManager.getContextManager(session).getConfigReader().getLoadTime())) %>
        </p>
        <ol>
            <% for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory().keySet()) { %>
            <li><a href="#<%=loopCategory%>"><%=loopCategory.getLabel(request.getLocale())%></a></li>
            <% } %>
            <li><a href="#locale-specifc">Locale-Specific Configuration Settings</a></li>
        </ol>
        <%
            for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory().keySet()) {
                final List<PwmSetting> loopSettings = PwmSetting.valuesByCategory().get(loopCategory);
        %>
        <table>
            <tr>
                <td class="title" colspan="10">
                    <a name="<%=loopCategory%>"><%= loopCategory.getLabel(request.getLocale()) %></a>
                </td>
            </tr>
            <%  for (final PwmSetting loopSetting : loopSettings) { %>
            <tr>
                <td class="key" style="width:100px; text-align:center;">
                    <%= loopSetting.getLabel(request.getLocale()) %>
                </td>
                <td>
                    <%= loopSetting.isConfidential() ? "* not shown *" : pwmConfig.toString(loopSetting) %>
                </td>
            </tr>
            <% } %>
        </table>
        <br class="clear"/>
        <% } %>
        <br class="clear"/>
        <% for (final Locale loopLocale : ContextManager.getContextManager(session).getConfigReader().getCurrentlyLoadedLocales()) { %>
        <br class="clear"/>
        <table class="tablemain">
            <tr>
                <td class="title" colspan="10">
                    <a name="locale-specifc">Locale-Specific Configuration Settings for "<%= loopLocale %>"</a>
                </td>
            </tr>
            <tr>
                <td colspan="10">
                    Below are settings for each of the local-specific configuration options in each locale
                    that has been used to access PWM. If a specific configured locale does not list
                    here, access PWM with a browser configured for that locale.
                </td>
            </tr>
            <%
                final password.pwm.config.LocalizedConfiguration localeConfig = ContextManager.getContextManager(session).getConfigReader().getLocalizedConfiguration(loopLocale);
                for (final Method method : localeConfig.getClass().getMethods()) {
                    final String name = method.getName();
                    if (name.matches("(?i)^get.*|^is.*") && !name.matches("(?i).*password.*|^getClass$")) {
                        out.append("<tr><td class=\"key\">");
                        out.append(name.replaceAll("^is|^get", ""));
                        out.append("    </td><td colspan=\"1\">");
                        try {
                            out.append(method.invoke(localeConfig).toString());
                        } catch (Exception e) { /*blah*/ }
                        out.append("</td></tr>");
                    }
                }
            %>
        </table>
        <% } %>
    </div>
</div>
<br class="clear"/>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>












