<%@ page import="password.pwm.util.intruder.RecordType" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.i18n.Config" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Intruders"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
            <% for (RecordType recordType : RecordType.values()) { %>
            <% String titleName = LocaleHelper.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(), "IntruderRecordType_" + recordType.toString(), pwmApplicationHeader.getConfig(),Config.class); %>
            <div data-dojo-type="dijit.layout.ContentPane" title="<%=titleName%>">
                <div id="<%=recordType%>_Grid">
                </div>
            </div>
            <% } %>
        </div>
        <br/>
        <div id="buttonbar">
            <input name="maxResults" id="maxResults" value="1000" data-dojo-type="dijit.form.NumberSpinner" style="width: 70px"
                   data-dojo-props="constraints:{min:10,max:10000000,pattern:'#'},smallDelta:100"/>
            Rows
            <button class="btn" type="button" onclick="refreshData()">Refresh</button>
        </div>

    </div>
    <div class="push"></div>
</div>
<style scoped="scoped">
    <% for (RecordType recordType : RecordType.values()) { %>
    .<%=recordType%>_Grid { height: auto; }
    .<%=recordType%>_Grid .dgrid-scroller { position: relative; overflow: visible; }
    <% } %>
</style>
<script>
    <% for (RecordType recordType : RecordType.values()) { %>
    var <%=recordType%>_Grid;
    var <%=recordType%>_Headers = {"subject":"Subject","timestamp":"Timestamp","count":"Count","status":"Status"};
    <% } %>

    function initGrid() {
        require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                    // Create a new constructor by mixing in the components
                    var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                    // Now, create an instance of our custom grid
                    <% for (RecordType recordType : RecordType.values()) { %>
                    <%=recordType%>_Grid = new CustomGrid({ columns: <%=recordType%>_Headers}, "<%=recordType%>_Grid");
                    <% } %>

                    refreshData();
                });
    }

    function refreshData() {
        showWaitDialog();
        require(["dojo"],function(dojo){
            <% for (RecordType recordType : RecordType.values()) { %>
            <%=recordType%>_Grid.refresh();
            <% } %>
            var maximum = getObject('maxResults').value;
            var url = PWM_GLOBAL['url-restservice'] + "/app-data/intruder?maximum=" + maximum  + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
            dojo.xhrGet({
                url: url,
                preventCache: true,
                handleAs: 'json',
                load: function(data) {
                    closeWaitDialog();
                    <% for (RecordType recordType : RecordType.values()) { %>
                    <%=recordType%>_Grid.renderArray(data['data']['<%=recordType%>']);
                    <% } %>
                },
                error: function(error) {
                    closeWaitDialog();
                    alert('unable to load data: ' + error);
                }
            });
        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog","dijit/form/NumberSpinner"],function(dojoParser){
            dojoParser.parse();
            initGrid();
        });
    });
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
