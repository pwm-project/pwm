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
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.util.IntruderManager" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<style scoped="scoped">
    .dgrid { height: auto; }
    .dgrid .dgrid-scroller { position: relative; overflow: visible; }
</style>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Intruders"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <%
            final List<Map<String,Object>> userGridData = new ArrayList<Map<String, Object>>();
            IntruderManager.RecordIterator<IntruderManager.IntruderRecord> userIterator;
            for (userIterator = pwmApplicationHeader.getIntruderManager().userRecordIterator(); userIterator.hasNext() && userGridData.size() < PwmConstants.INTRUDER_TABLE_SIZE_VIEW_MAX; ) {
                final IntruderManager.IntruderRecord intruderRecord = userIterator.next();
                if (intruderRecord != null) {
                    final Map<String,Object> rowData = new HashMap<String,Object>();
                    rowData.put("subject",intruderRecord.getSubject());
                    rowData.put("timestamp",dateFormat.format(intruderRecord.getTimeStamp()));
                    rowData.put("count",String.valueOf(intruderRecord.getAttemptCount()));
                    try {
                        pwmApplicationHeader.getIntruderManager().check(intruderRecord.getSubject(), intruderRecord.getSubject(), "");
                        rowData.put("status","watching");
                    } catch (PwmException e) {
                        rowData.put("status","locked");
                    }
                    userGridData.add(rowData);
                }
            }
            userIterator.close();
            final List<Map<String,Object>> addressGridData = new ArrayList<Map<String, Object>>();
            IntruderManager.RecordIterator<IntruderManager.IntruderRecord> addressIterator;
            for (addressIterator = pwmApplicationHeader.getIntruderManager().addressRecordIterator(); addressIterator.hasNext() && addressGridData.size() < PwmConstants.INTRUDER_TABLE_SIZE_VIEW_MAX; ) {
                final IntruderManager.IntruderRecord intruderRecord = addressIterator.next();
                if (intruderRecord != null) {
                    final Map<String,Object> rowData = new HashMap<String,Object>();
                    rowData.put("subject",intruderRecord.getSubject());
                    rowData.put("timestamp",dateFormat.format(intruderRecord.getTimeStamp()));
                    rowData.put("count",String.valueOf(intruderRecord.getAttemptCount()));
                    try {
                        pwmApplicationHeader.getIntruderManager().check(null,null,intruderRecord.getSubject());
                        rowData.put("status","watching");
                    } catch (PwmException e) {
                        rowData.put("status","locked");
                    }
                    addressGridData.add(rowData);
                }
            }
            addressIterator.close();
        %>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
            <div data-dojo-type="dijit.layout.ContentPane" title="Users">
                <% if (userGridData.isEmpty()) { %>
                No users are currently in the user lock table.
                <% } else { %>
                <div id="userGrid">
                </div>
                <script type="text/javascript">
                    function initUserGrid() {
                        var headers = {"subject":"Username/DN","timestamp":"Timestamp","count":"Count","status":"Status"};

                        require(["dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                                function(declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                                    var userData = <%=new Gson().toJson(userGridData)%>;

                                    // Create a new constructor by mixing in the components
                                    var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                                    // Now, create an instance of our custom grid which
                                    // have the features we added!
                                    var grid = new CustomGrid({
                                        columns: headers
                                    }, "userGrid");
                                    grid.renderArray(userData);
                                    grid.set("subject","userID");
                                });
                    }
                </script>
                <% } %>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="Addresses">
                <% if (addressGridData.isEmpty()) { %>
                No addresses are currently in the address lock table.
                <% } else { %>
                <div id="addressGrid">
                </div>
                <script type="text/javascript">
                    function initAddressGrid() {
                        var headers = {"subject":"Address","timestamp":"Timestamp","count":"Count","status":"Status"};

                        require(["dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                                function(declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                                    var addressData = <%=new Gson().toJson(addressGridData)%>;

                                    // Create a new constructor by mixing in the components
                                    var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                                    // Now, create an instance of our custom grid which
                                    // have the features we added!
                                    var grid = new CustomGrid({
                                        columns: headers
                                    }, "addressGrid");
                                    grid.renderArray(addressData);
                                    grid.set("subject","userID");
                                });
                    }
                </script>
                <% } %>
            </div>
        </div>
    </div>
</div>
<script type="text/javascript">
    function startupPage() {
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog"],function(dojoParser){
            dojoParser.parse();
        });
    }
    PWM_GLOBAL['startupFunctions'].push(function(){
        startupPage();
        if (initUserGrid) initUserGrid();
        if (initAddressGrid) initAddressGrid();
    });
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
