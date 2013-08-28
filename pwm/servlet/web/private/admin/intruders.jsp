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
            <div data-dojo-type="dijit.layout.ContentPane" title="Users">
                <div id="userGrid">
                </div>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="Addresses">
                <div id="addressGrid">
                </div>
            </div>
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
    .addressGrid { height: auto; }
    .addressGrid .dgrid-scroller { position: relative; overflow: visible; }
    .userGrid { height: auto; }
    .userGrid .dgrid-scroller { position: relative; overflow: visible; }
</style>
<script>
    var userGrid;
    var addressGrid;
    var userHeaders = {"subject":"Username/DN","timestamp":"Timestamp","count":"Count","status":"Status"};
    var addressHeaders = {"subject":"Address","timestamp":"Timestamp","count":"Count","status":"Status"};

    function initGrid() {
        require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                    // Create a new constructor by mixing in the components
                    var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                    // Now, create an instance of our custom grid
                    addressGrid = new CustomGrid({ columns: addressHeaders}, "addressGrid");
                    userGrid = new CustomGrid({ columns: userHeaders}, "userGrid");

                    refreshData();
                });
    }

    function refreshData() {
        showWaitDialog();
        require(["dojo"],function(dojo){
            addressGrid.refresh();
            userGrid.refresh();
            var maximum = getObject('maxResults').value;
            var url = PWM_GLOBAL['url-restservice'] + "/app-data/intruder?maximum=" + maximum  + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
            dojo.xhrGet({
                url: url,
                preventCache: true,
                handleAs: 'json',
                load: function(data) {
                    closeWaitDialog();
                    addressGrid.renderArray(data['data']['address']);
                    userGrid.renderArray(data['data']['user']);
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
