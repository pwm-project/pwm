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
<%@ page import="password.pwm.event.AuditRecord" %>
<%@ page import="java.util.*" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Audit Log"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <%
            final Gson gson = new Gson();
            final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            timeFormat.setTimeZone(TimeZone.getTimeZone("Zulu"));
            final int maxResults = password.pwm.Validator.readIntegerFromRequest(request, "maxResults", 1000);
            boolean maxResultsExceeded = false;
            final List<Map<String,String>> gridData = new ArrayList<Map<String, String>>();
            for (Iterator<AuditRecord> iterator = pwmApplicationHeader.getAuditManager().readLocalDB(); iterator.hasNext(); ) {
                final AuditRecord loopRecord = iterator.next();
                try {
                    final Map<String, String> rowData = new HashMap<String, String>();
                    rowData.put("timestamp", timeFormat.format(loopRecord.getTimestamp()));
                    rowData.put("perpID", loopRecord.getPerpetratorID());
                    //rowData.put("perpDN", loopRecord.getPerpetratorDN());
                    rowData.put("event", loopRecord.getEventCode().toString());
                    //rowData.put("message",loopRecord.getMessage());
                    rowData.put("targetID",loopRecord.getTargetID());
                    //rowData.put("targetDN",loopRecord.getTargetDN());
                    gridData.add(rowData);
                } catch (IllegalStateException e) { /* ignore */ }
                if (gridData.size() >= maxResults) {
                    maxResultsExceeded = true;
                    break;
                }
            }
        %>
        <div id="grid">
        </div>
        <script>
            function startupPage() {
                var headers = {
                    "timestamp":"Time",
                    "perpID":"Perpetrator ID",
                    //"perpDN":"Perpetrator DN",
                    "event":"Event",
                    //"message":"Message",
                    "targetID":"Target ID"
                    //"targetDN":"Target DN"
                };
                require(["dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                        function(declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                            var data = <%=gson.toJson(gridData)%>;
                            var columnHeaders = headers;

                            // Create a new constructor by mixing in the components
                            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                            // Now, create an instance of our custom grid which
                            // have the features we added!
                            var grid = new CustomGrid({
                                columns: columnHeaders
                            }, "grid");
                            grid.set("sort","timestamp");
                            grid.renderArray(data);
                        });
            };
        </script>
        <style scoped="scoped">
            .dgrid { height: auto; }
            .dgrid .dgrid-scroller { position: relative; max-height: 360px; overflow: auto; }
        </style>
        <% if (maxResultsExceeded) { %>
        <div style="width:100%; text-align: center">
            <pwm:Display key="Display_SearchResultsExceeded"/>
        </div>
        <% } %>
        <div id="buttonbar">
            <input id="maxResults" value="<%=maxResults%>" data-dojo-type="dijit.form.NumberSpinner" style="width: 60px"
                   data-dojo-props="constraints:{min:10,max:10000000}"/>

            <button class="btn" onclick="refresh()">Refresh</button>
        </div>
    </div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/form/NumberSpinner","dojo/domReady!"],function(dojoParser){
            dojoParser.parse();
            startupPage();
        });
    });
    function refresh() {
        require(["dijit/registry"],function(registry){
            showWaitDialog(null,null,function(){
                var maxResults = registry.byId('maxResults').get('value');
                window.location = 'auditlog.jsp?maxResults=' + maxResults;
            });
        });
    }
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
