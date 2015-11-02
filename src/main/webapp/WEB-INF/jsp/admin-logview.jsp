<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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

<%@ page import="password.pwm.util.JsonUtil" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.logging.LocalDBLogger" %>
<%@ page import="password.pwm.util.logging.PwmLogEvent" %>
<%@ page import="password.pwm.util.logging.PwmLogLevel" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request,response); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(pwmRequest.getLocale()); %>
<% final LocalDBLogger localDBLogger = pwmRequest.getPwmApplication().getLocalDBLogger(); %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Log Viewer"/>
    </jsp:include>
    <div id="centerbody" style="width: 96%; margin-left: 2%; margin-right: 2%; background: white">
        <%@ include file="fragment/admin-nav.jsp" %>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded"
              name="searchForm" id="searchForm" class="pwm-form">
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <table style="">
                <tr style="width:0">
                    <td class="key" style="border:0">
                        <label for="level">Level</label>
                        <br/>
                        <%--
                        <% final String selectedLevel = pwmRequest.readParameterAsString("level", "INFO");%>
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
                        --%>
                        <% final String selectedLevel = pwmRequest.readParameterAsString("level", "INFO");%>
                        <% final PwmLogLevel configuredLevel = pwmRequest.getConfig().readSettingAsEnum(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL,PwmLogLevel.class); %>
                        <select name="level" style="width: auto;" id="select-level">
                            <% for (final PwmLogLevel level : PwmLogLevel.values()) { %>
                            <% boolean optionSelected = level.toString().equals(selectedLevel); %>
                            <% boolean disabled = level.compareTo(configuredLevel) < 0; %>
                            <option value="<%=level%>" <%=optionSelected ?" selected": ""%><%=disabled ? " disabled" : ""%>  ><%=level%></option>
                            <% } %>
                        </select>

                    </td>
                    <td class="key" style="border: 0">
                        <label for="type">Type</label>
                        <br/>
                        <% final String selectedType = pwmRequest.readParameterAsString("type", "Both");%>
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
                               value="<%=pwmRequest.readParameterAsString("username")%>"/>
                    </td>
                    <td class="key" style="border: 0">
                        Containing text
                        <br/>
                        <input name="text" type="text"
                               value="<%=pwmRequest.readParameterAsString("text")%>"/>
                    </td>
                    <td class="key" style="border: 0">
                        Max Count
                        <br/>
                        <% final String selectedCount = pwmRequest.readParameterAsString("count");%>
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
                        <% final String selectedTime = pwmRequest.readParameterAsString("maxTime");%>
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
                        <% final String displayText = pwmRequest.readParameterAsString("displayText", "Both");%>
                        <select id="displayText" name="displayText" style="width: auto">
                            <option value="false" <%= "false".equals(displayText) ? "selected=\"selected\"" : "" %>>Table</option>
                            <option value="true" <%= "true".equals(displayText) ? "selected=\"selected\"" : "" %>>Text</option>
                        </select>
                    </td>
                    <td class="key" style="border: 0; vertical-align: middle">
                        <button type="submit" name="submit_button" id="submit_button" class="btn">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-search"></span></pwm:if>
                            Search
                        </button>
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
            final String username = pwmRequest.readParameterAsString("username");
            final String text = pwmRequest.readParameterAsString("text");
            final boolean displayAsText = Boolean.parseBoolean(displayText);
            try {
                logLevel = PwmLogLevel.valueOf(PwmRequest.forRequest(request, response).readParameterAsString("level"));
            } catch (Exception e) {
            }
            try {
                logType = LocalDBLogger.EventType.valueOf(PwmRequest.forRequest(request, response).readParameterAsString("type"));
            } catch (Exception e) {
            }
            try {
                eventCount = Integer.parseInt(PwmRequest.forRequest(request, response).readParameterAsString("count"));
            } catch (Exception e) {
            }
            try {
                maxTime = Long.parseLong(PwmRequest.forRequest(request, response).readParameterAsString("maxTime"));
            } catch (Exception e) {
            }

            LocalDBLogger.SearchResults searchResults = null;
            try {
                final LocalDBLogger.SearchParameters searchParameters = new LocalDBLogger.SearchParameters(logLevel, eventCount, username, text, maxTime, logType);
                searchResults = localDBLogger.readStoredEvents(searchParameters);
            } catch (Exception e) {
                out.write("<p>Unexpected error while searching: " + e.getMessage()+"</p>");
                e.printStackTrace();
            }
        %>
        <% if (!searchResults.hasNext()) { %>
        <p style="text-align:center;">No events matched your search. Please refine your search query and try again.</p>
        <% } else { %>
        <% if (displayAsText) { %>
        <hr/>
        <pre><% while (searchResults.hasNext()) { final PwmLogEvent event = searchResults.next(); %><%=StringUtil.escapeHtml(event.toLogString()) %><%="\n"%><% } %></pre>
        <hr/>
        <% } else {%>
        <pwm:script>
            <script type="text/javascript">
                var data = [];
                <%
                    while (searchResults.hasNext()) {
                        final PwmLogEvent event = searchResults.next();
                        try {
                            final Map<String, Object> rowData = new LinkedHashMap<String, Object>();
                            rowData.put("timestamp", event.getDate());
                            rowData.put("level", event.getLevel().toString());
                            rowData.put("src", event.getSource());
                            rowData.put("user", event.getActor());
                            rowData.put("component",event.getTopTopic());
                            rowData.put("detail",event.getMessage());
                %>
                data.push(<%=JsonUtil.serializeMap(rowData)%>);
                <%
                        } catch (IllegalStateException e) { /* ignore */ }
                    }
                %>
            </script>
        </pwm:script>
        <div class="WaitDialogBlank" id="WaitDialogBlank">&nbsp;</div>
        <div id="dgrid">
        </div>
        <pwm:script>
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
                                PWM_MAIN.getObject('WaitDialogBlank').style.display = 'none';
                            });
                };
            </script>
        </pwm:script>
        <style nonce="<pwm:value name="cspNonce"/>" scoped="scoped">
            .dgrid { height: auto; }
            .dgrid .dgrid-scroller { position: relative;  overflow: visible; }
            .dgrid-column-timestamp {width: 80px;}
            .dgrid-column-level {width: 50px;}
            .dgrid-column-src {width: 80px;}
            .dgrid-column-user {width: 70px;}
            .dgrid-column-component {width: 100px;}
        </style>
        <pwm:script>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    require(["dojo/parser","dijit/form/NumberSpinner","dojo/domReady!"],function(dojoParser){
                        dojoParser.parse();
                        startupPage();
                    });
                });
            </script>
        </pwm:script>
        <% } %>
        <p style="text-align:center;">Matched <%= numberFormat.format(searchResults.getReturnedEvents()) %> entries after
            searching <%= numberFormat.format(searchResults.getReturnedEvents()) %> log entries
            in <%= searchResults.getSearchTime().asCompactString() %>.</p>
        <% } %>
        <div class="footnote">
        <p>
            This page shows the debug log
            history. This history is stored in the LocalDB cache of the debug log. For a
            permanent log
            record of events, see the application server's log file.  The LocalDB contains <%=numberFormat.format(localDBLogger.getStoredEventCount())%> events. The oldest event is from
            <span class="timestamp"><%= PwmConstants.DEFAULT_DATETIME_FORMAT.format(ContextManager.getPwmApplication(session).getLocalDBLogger().getTailDate()) %></span>.
        </p><p>
        The LocalDB is configured to capture events of level
        <b><%=ContextManager.getPwmApplication(session).getConfig().readSettingAsString(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL)%>
        </b> and higher.
        </div>
    </p>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
