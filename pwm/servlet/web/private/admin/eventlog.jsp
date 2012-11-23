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

<%@ page import="password.pwm.util.PwmDBLogger" %>
<%@ page import="password.pwm.util.PwmLogLevel" %>
<%@ page import="java.text.NumberFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final PwmDBLogger pwmDBLogger = ContextManager.getPwmApplication(session).getPwmDBLogger(); %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<script type="text/javascript">
    var advancedPanelVisible = false;
    function toggleAdvancedPanel() {
        advancedPanelVisible = !advancedPanelVisible;
        if (advancedPanelVisible) {
            getObject('advanced_panel').style.visibility = 'visible';
            getObject('advanced_button').innerHTML = "Simple";
        } else {
            getObject('advanced_panel').style.visibility = 'hidden';
            getObject('advanced_button').innerHTML = "Advanced";
        }
        return false;
    }
</script>
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="PWM Event Log"/>
</jsp:include>
<div id="centerbody">
<%@ include file="admin-nav.jsp" %>
<p>
    This page shows PWM debug log
    history. This history is stored in the pwmDB cache of the debug log. For a
    permanent log
    record of events, see the application server's log file.
    All times listed are in
    the <%= (java.text.DateFormat.getDateTimeInstance()).getTimeZone().getDisplayName() %>
    timezone. The pwmDB contains <%=numberFormat.format(pwmDBLogger.getStoredEventCount())%> events. The oldest event is from
    <%= SimpleDateFormat.getInstance().format(ContextManager.getPwmApplication(session).getPwmDBLogger().getTailDate()) %>
    .
</p>

<p>
    The pwmDB is configured to capture events of level
    <b><%=ContextManager.getPwmApplication(session).getConfig().readSettingAsString(PwmSetting.EVENTS_PWMDB_LOG_LEVEL)%>
    </b> and higher.
</p>
<br class="clear"/>

<form action="<pwm:url url='eventlog.jsp'/>" method="GET" enctype="application/x-www-form-urlencoded"
      name="eventlogParameters" onsubmit="handleFormSubmit('submit_button',this)">
    <table style="border: 0; max-width:600px; width:600px">
        <tr style="border: 0">
            <td class="key" style="border: 0">
                <label for="level">Level</label>
            </td>
            <td style="border: 0">
                <% final String selectedLevel = password.pwm.Validator.readStringFromRequest(request, "level", 255, "INFO");%>
                <select id="level" name="level" style="width: auto;">
                    <option value="FATAL" <%= "FATAL".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>FATAL
                    </option>
                    <option value="ERROR" <%= "ERROR".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>ERROR
                    </option>
                    <option value="WARN" <%= "WARN".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>WARN
                    </option>
                    <option value="INFO" <%= "INFO".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>INFO
                    </option>
                    <option value="DEBUG" <%= "DEBUG".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>DEBUG
                    </option>
                    <option value="TRACE" <%= "TRACE".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>TRACE
                    </option>
                </select>
            </td>
        </tr>
        <tr style="border: 0">
            <td class="key" style="border: 0">
                <label for="type">Type</label>
            </td>
            <td style="border: 0">
                <% final String selectedType = password.pwm.Validator.readStringFromRequest(request, "type", 255, "Both");%>
                <select id="type" name="type">
                    <option value="User" <%= "User".equals(selectedType) ? "selected=\"selected\"" : "" %>>User</option>
                    <option value="System" <%= "System".equals(selectedType) ? "selected=\"selected\"" : "" %>>System
                    </option>
                    <option value="Both" <%= "Both".equals(selectedType) ? "selected=\"selected\"" : "" %>>Both</option>
                </select>
            </td>
        </tr>
        <tr style="border: 0">
            <td class="key" style="border: 0">
                Username
            </td>
            <td style="border: 0">
                <input name="username" type="text"
                       value="<%=password.pwm.Validator.readStringFromRequest(request,"username")%>"/>
            </td>
        </tr>
        <tr style="border: 0">
            <td class="key" style="border: 0">
                Containing text
            </td>
            <td style="border: 0">
                <input name="text" type="text"
                       value="<%=password.pwm.Validator.readStringFromRequest(request,"text")%>"/>
            </td>
        </tr>
        <tr style="border: 0">
            <td class="key" style="border: 0">
                &nbsp;
            </td>
            <td style="border: 0">
                <input type="submit" name="submit" id="submit_button" value=" Search " class="btn"/>
                &nbsp;&nbsp;
                <button type="button" id="advanced_button" class="btn" onclick="toggleAdvancedPanel()">Advanced</button>
            </td>
        </tr>
    </table>
    <div id="advanced_panel" style="visibility: hidden;">
        <table style="border: 0; max-width:600px; width:600px">
            <tr style="border: 0" id="ad">
                <td class="key" style="border: 0">
                    Maximum Count
                </td>
                <td style="border: 0">
                    <% final String selectedCount = password.pwm.Validator.readStringFromRequest(request, "count");%>
                    <select name="count">
                        <option value="100" <%= "100".equals(selectedCount) ? "selected=\"selected\"" : "" %>>100</option>
                        <option value="500" <%= "500".equals(selectedCount) ? "selected=\"selected\"" : "" %>>500</option>
                        <option value="2000" <%= "2000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>2000
                        </option>
                        <option value="5000" <%= "5000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>5000
                        </option>
                        <option value="10000" <%= "10000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>10000
                        </option>
                        <option value="100000" <%= "100000".equals(selectedCount) ? "selected=\"selected\"" : "" %>>100000
                        </option>
                    </select>
                </td>
            </tr>
            <tr style="border: 0">
                <td class="key" style="border: 0">
                    Maximum Search Time
                </td>
                <td style="border: 0">
                    <% final String selectedTime = password.pwm.Validator.readStringFromRequest(request, "maxTime");%>
                    <select name="maxTime">
                        <option value="10000" <%= "10000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>10 seconds
                        </option>
                        <option value="30000" <%= "30000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>30 seconds
                        </option>
                        <option value="60000" <%= "60000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>1 minute
                        </option>
                        <option value="120000" <%= "120000".equals(selectedTime) ? "selected=\"selected\"" : "" %>>2 minutes
                        </option>
                    </select>
                </td>
            </tr>
            <tr style="border: 0">
                <td class="key" style="border: 0">
                    <label for="displayText">Display text</label>
                </td>
                <td style="border: 0">
                    <% final String displayText = password.pwm.Validator.readStringFromRequest(request, "displayText", 255, "Both");%>
                    <select id="displayText" name="displayText">
                        <option value="false" <%= "false".equals(displayText) ? "selected=\"selected\"" : "" %>>No</option>
                        <option value="true" <%= "true".equals(displayText) ? "selected=\"selected\"" : "" %>>Yes</option>
                    </select>
                </td>
            </tr>
        </table>
    </div>
    <br/>
</form>
<%
    PwmLogLevel logLevel = PwmLogLevel.INFO;
    PwmDBLogger.EventType logType = PwmDBLogger.EventType.Both;
    int eventCount = 100;
    long maxTime = 10000;
    final String username = password.pwm.Validator.readStringFromRequest(request, "username");
    final String text = password.pwm.Validator.readStringFromRequest(request, "text");
    final boolean displayAsText = Boolean.parseBoolean(displayText);
    try {
        logLevel = PwmLogLevel.valueOf(password.pwm.Validator.readStringFromRequest(request, "level"));
    } catch (Exception e) {
    }
    try {
        logType = PwmDBLogger.EventType.valueOf(password.pwm.Validator.readStringFromRequest(request, "type"));
    } catch (Exception e) {
    }
    try {
        eventCount = Integer.parseInt(password.pwm.Validator.readStringFromRequest(request, "count"));
    } catch (Exception e) {
    }
    try {
        maxTime = Long.parseLong(password.pwm.Validator.readStringFromRequest(request, "maxTime"));
    } catch (Exception e) {
    }

    PwmDBLogger.SearchResults searchResults = null;
    try {
        searchResults = pwmDBLogger.readStoredEvents(PwmSession.getPwmSession(session), logLevel, eventCount, username, text, maxTime, logType);
    } catch (Exception e) {
        out.write("<p>Unexpected error while searching: " + e.getMessage()+"</p>");
    }
%>
<% if (searchResults == null || searchResults.getEvents().isEmpty()) { %>
<p>No events matched your search. Please refine your search query and try again.</p>
<% } else { %>
<p style="text-align:center;">Matched <%= numberFormat.format(searchResults.getEvents().size()) %> entries after
    searching <%= numberFormat.format(searchResults.getSearchedEvents()) %> log entries
    in <%= searchResults.getSearchTime().asCompactString() %>.</p>
<br class="clear"/>
<%--
<% if (displayAsText) { %>
<hr/>
<pre><% for (final PwmLogEvent event : searchResults.getEvents()) { %><%= event.toLogString(true) %><%="\n"%><% } %></pre>
<hr/>
<% } else {%>
<table>
    <tr>
        <td class="title" style="width: 1px">
            &nbsp;
        </td>
        <td class="title">
            Timestamp
        </td>
        <td class="title">
            Level
        </td>
        <td class="title">
            Src
        <td class="title">
            User
        </td>
        <td class="title">
            Component
        </td>
        <td class="title">
            Detail
        </td>
    </tr>
    <% int counter = 0;
        for (final PwmLogEvent event : searchResults.getEvents()) { %>
    <tr>
        <td class="key" style="font-family: Courier, sans-serif; width: 1px">
            <%= ++counter %>
        </td>
        <td>
            <%= DateFormat.getDateTimeInstance().format(event.getDate()) %>
        </td>
        <td>
            <%= event.getLevel() %>
        </td>
        <td>
            <%= event.getSource() %>
        </td>
        <td>
            <%= event.getActor() %>
        </td>
        <td>
            <%
                final int lastDot = event.getTopic().lastIndexOf(".");
                out.write(lastDot != -1 ? event.getTopic().substring(lastDot + 1, event.getTopic().length()) : event.getTopic());
            %>
        </td>
        <td>
            <%
                final String eventMessage = event.getHtmlSafeMessage();
                if (eventMessage.contains("\n")) {
                    out.append("<pre>").append(eventMessage).append("</pre>");
                } else {
                    out.append(eventMessage);
                }
                //noinspection ThrowableResultOfMethodCallIgnored
                if (event.getThrowable() != null) {
                    out.append("<br/>Throwable: ");
                    out.append("<pre>");
                    final PrintWriter strWriter = new PrintWriter(new StringWriter());
                    //noinspection ThrowableResultOfMethodCallIgnored
                    event.getThrowable().printStackTrace(strWriter);
                    out.append(strWriter.toString());
                    out.append("</pre>");
                }
            %>
        </td>
    </tr>
    <% } %>
</table>
<% } %>
--%>
<% } %>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
