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

<%@ page import="com.google.gson.Gson" %>
<%@ page import="password.pwm.util.LocalDBLogger" %>
<%@ page import="password.pwm.util.PwmLogEvent" %>
<%@ page import="password.pwm.util.PwmLogLevel" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TimeZone" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final LocalDBLogger localDBLogger = ContextManager.getPwmApplication(session).getLocalDBLogger(); %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Event Log"/>
    </jsp:include>
    <br/>
    <%@ include file="admin-nav.jsp" %>
    <div style="width: 96%; margin-left: 2%; margin-right: 2%; background: white">
        <form action="<pwm:url url='eventlog.jsp'/>" method="get" enctype="application/x-www-form-urlencoded"
              name="searchForm" id="searchForm" onsubmit="handleFormSubmit('submit_button',this)">
            <table style="">
                <tr style="width:0">
                    <td class="key" style="border:0">
                        <label for="level">Level</label>
                        <br/>
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
                    <td class="key" style="border: 0">
                        <label for="type">Type</label>
                        <br/>
                        <% final String selectedType = password.pwm.Validator.readStringFromRequest(request, "type", 255, "Both");%>
                        <select id="type" name="type" style="width:auto">
                            <option value="User" <%= "User".equals(selectedType) ? "selected=\"selected\"" : "" %>>User</option>
                            <option value="System" <%= "System".equals(selectedType) ? "selected=\"selected\"" : "" %>>System
                            </option>
                            <option value="Both" <%= "Both".equals(selectedType) ? "selected=\"selected\"" : "" %>>Both</option>
                        </select>
                    </td>
                    <td class="key" style="border: 0">
                        Username
                        <br/>
                        <input name="username" type="text"
                               value="<%=password.pwm.Validator.readStringFromRequest(request,"username")%>"/>
                    </td>
                    <td class="key" style="border: 0">
                        Containing text
                        <br/>
                        <input name="text" type="text"
                               value="<%=password.pwm.Validator.readStringFromRequest(request,"text")%>"/>
                    </td>
                    <td class="key" style="border: 0">
                        Max Count
                        <br/>
                        <% final String selectedCount = password.pwm.Validator.readStringFromRequest(request, "count");%>
                        <select name="count" style="width:auto">
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
                    <td class="key" style="border: 0">
                        Max Time
                        <br/>
                        <% final String selectedTime = password.pwm.Validator.readStringFromRequest(request, "maxTime");%>
                        <select name="maxTime" style="width: auto">
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
                    <td class="key" style="border: 0">
                        <label for="displayText">Display</label>
                        <br/>
                        <% final String displayText = password.pwm.Validator.readStringFromRequest(request, "displayText", 255, "Both");%>
                        <select id="displayText" name="displayText" style="width: auto">
                            <option value="false" <%= "false".equals(displayText) ? "selected=\"selected\"" : "" %>>Table</option>
                            <option value="true" <%= "true".equals(displayText) ? "selected=\"selected\"" : "" %>>Text</option>
                        </select>
                    </td>
                    <td class="key" style="border: 0; vertical-align: middle">
                        <input type="submit" name="submit_button" id="submit_button" value=" Search " class="btn"/>
                    </td>
                </tr>
            </table>
        </form>
        <br/>
        <%
            PwmLogLevel logLevel = PwmLogLevel.INFO;
            LocalDBLogger.EventType logType = LocalDBLogger.EventType.Both;
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
                logType = LocalDBLogger.EventType.valueOf(password.pwm.Validator.readStringFromRequest(request, "type"));
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
        
            LocalDBLogger.SearchResults searchResults = null;
            try {
                searchResults = localDBLogger.readStoredEvents(PwmSession.getPwmSession(session), logLevel, eventCount, username, text, maxTime, logType);
            } catch (Exception e) {
                out.write("<p>Unexpected error while searching: " + e.getMessage()+"</p>");
            }
        %>
        <% if (searchResults == null || searchResults.getEvents().isEmpty()) { %>
        <p style="text-align:center;">No events matched your search. Please refine your search query and try again.</p>
        <% } else { %>
        <% if (displayAsText) { %>
        <hr/>
        <pre><% for (final PwmLogEvent event : searchResults.getEvents()) { %><%= event.toLogString(true) %><%="\n"%><% } %></pre>
        <hr/>
        <% } else {%>
        <script type="text/javascript">
            var data = [];
            <%
                final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                final Gson gson = new Gson();
                timeFormat.setTimeZone(TimeZone.getTimeZone("Zulu"));
                for (final PwmLogEvent event : searchResults.getEvents()) {
                    try {
                        final Map<String, String> rowData = new LinkedHashMap<String, String>();
                        rowData.put("timestamp", timeFormat.format(event.getDate()));
                        rowData.put("level", event.getLevel().toString());
                        rowData.put("src", event.getSource());
                        rowData.put("user", event.getActor());
                        rowData.put("component",event.getTopTopic());
                        rowData.put("detail",event.getMessage());
            %>
            data.push(<%=gson.toJson(rowData)%>)
            <%
                    } catch (IllegalStateException e) { /* ignore */ }
                }
            %>
        </script>
            <div id="WaitDialogBlank">&nbsp;</div>
            <div id="dgrid">
            </div>
            <script>
                function startupPage() {
                    var headers = {
                        "timestamp":"Time",
                        "level":"Level",
                        "src":"Source Address",
                        "user":"User",
                        "component":"Component",
                        "detail":"Detail"
                    };
                    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                            function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                                var columnHeaders = headers;
        
                                // Create a new constructor by mixing in the components
                                var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);
        
                                // Now, create an instance of our custom grid which
                                // have the features we added!
                                var grid = new CustomGrid({
                                    columns: columnHeaders
                                }, "dgrid");
                                grid.set("sort","timestamp");
                                grid.renderArray(data);
                                getObject('WaitDialogBlank').style.display = 'none';
                            });
                };
            </script>
        <style scoped="scoped">
            .dgrid { height: auto; }
            .dgrid .dgrid-scroller { position: relative;  overflow: visible; }
            .dgrid-column-timestamp {width: 80px;}
            .dgrid-column-level {width: 50px;}
            .dgrid-column-src {width: 80px;}
            .dgrid-column-user {width: 70px;}
            .dgrid-column-component {width: 100px;}
        </style>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    require(["dojo/parser","dijit/form/NumberSpinner","dojo/domReady!"],function(dojoParser){
                        dojoParser.parse();
                        startupPage();
                    });
                });
            </script>
        <% } %>
        <p style="text-align:center;">Matched <%= numberFormat.format(searchResults.getEvents().size()) %> entries after
            searching <%= numberFormat.format(searchResults.getSearchedEvents()) %> log entries
            in <%= searchResults.getSearchTime().asCompactString() %>.</p>
        <% } %>
    </div>
    <div id="centerbody">
        <p>
            This page shows the debug log
            history. This history is stored in the LocalDB cache of the debug log. For a
            permanent log
            record of events, see the application server's log file.
            All times listed are in
            the <%= (java.text.DateFormat.getDateTimeInstance()).getTimeZone().getDisplayName() %>
            timezone. The LocalDB contains <%=numberFormat.format(localDBLogger.getStoredEventCount())%> events. The oldest event is from
            <%= SimpleDateFormat.getInstance().format(ContextManager.getPwmApplication(session).getLocalDBLogger().getTailDate()) %>
            .
            </p><p>
            The LocalDB is configured to capture events of level
            <b><%=ContextManager.getPwmApplication(session).getConfig().readSettingAsString(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL)%>
            </b> and higher.
        </p>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
