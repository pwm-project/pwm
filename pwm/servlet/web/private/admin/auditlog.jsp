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
        <jsp:param name="pwm.PageName" value="Audit Log"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
            <div data-dojo-type="dijit.layout.ContentPane" title="User">
                <div id="userGrid">
                </div>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="System">
                <div id="systemGrid">
                </div>
            </div>
        </div>
        <br/>
        <div id="buttonbar">
            <input name="maxResults" id="maxResults" value="1000" data-dojo-type="dijit.form.NumberSpinner" style="width: 70px"
                   data-dojo-props="constraints:{min:10,max:10000000,pattern:'#'},smallDelta:100"/>
            Rows
            <button class="btn" type="button" onclick="refreshData()">Refresh</button>
            <br/>
            <form action="<%=request.getContextPath()%><pwm:url url="/private/CommandServlet"/>" method="GET">
                <button type="submit" class="btn">Download as CSV</button>
                <input type="hidden" name="processAction" value="outputAuditLogCsv"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
    <style>
        .userGrid { height: auto; }
        .systemGrid { height: auto; }
    </style>
</div>
<script type="text/javascript">
    var userGrid;
    var userHeaders = {
        "timestamp":"Time",
        "perpetratorID":"Perpetrator ID",
        "perpetratorDN":"Perpetrator DN",
        "eventCode":"Event",
        "message":"Message",
        "targetID":"Target ID",
        "targetDN":"Target DN",
        "sourceAddress":"Source Address",
        "sourceHost":"Source Host"
    };
    var systemGrid;
    var systemHeaders = {
        "timestamp":"Time",
        "eventCode":"Event",
        "message":"Message",
        "instance":"Instance"
    };

    function initGrid() {
        require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                    // Create a new constructor by mixing in the components
                    var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                    // Now, create an instance of our custom userGrid
                    userGrid = new CustomGrid({columns: userHeaders}, "userGrid");
                    systemGrid = new CustomGrid({columns: systemHeaders}, "systemGrid");

                    // unclick superfluous fields
                    getObject('userGrid-hider-menu-check-perpetratorDN').click();
                    getObject('userGrid-hider-menu-check-message').click();
                    getObject('userGrid-hider-menu-check-targetDN').click();
                    getObject('userGrid-hider-menu-check-sourceHost').click();

                    refreshData();
                });
    }

    function refreshData() {
        showWaitDialog();
        require(["dojo"],function(dojo){
            userGrid.refresh();
            systemGrid.refresh();
            var maximum = getObject('maxResults').value;
            var url = PWM_GLOBAL['url-restservice'] + "/app-data/audit?maximum=" + maximum;
            dojo.xhrGet({
                url: url,
                preventCache: true,
                headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
                handleAs: 'json',
                load: function(data) {
                    closeWaitDialog();
                    userGrid.renderArray(data['data']['user']);
                    userGrid.set("sort", { attribute : 'timestamp', ascending: false, descending: true });
                    systemGrid.renderArray(data['data']['system']);
                    systemGrid.set("sort", { attribute : 'timestamp', ascending: false, descending: true });
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
