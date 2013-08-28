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
        <jsp:param name="pwm.PageName" value="Active Sessions"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <div id="grid">
        </div>
        <style scoped="scoped">
            .grid { height: auto; }
            .grid .dgrid-scroller { position: relative; max-height: 360px; overflow: auto; }
        </style>
        <div id="buttonbar">
                <input name="maxResults" id="maxResults" value="1000" data-dojo-type="dijit.form.NumberSpinner" style="width: 70px"
                       data-dojo-props="constraints:{min:10,max:10000000,pattern:'#'},smallDelta:100"/>
                Rows
                <button class="btn" type="button" onclick="refreshData()">Refresh</button>
        </div>
    </div>
    <div class="push"></div>
</div>
<script>
    var grid;
    var headers = {
        "createTime":"Create Time",
        "label":"Label",
        "idle":"Idle",
        "srcAddress":"Address",
        "locale":"Locale",
        "userID":"User ID",
        "userDN":"User DN",
        "srcHost":"Host",
        "lastUrl":"Last URL"
    };

    function initGrid() {
        require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                    var columnHeaders = headers;

                    // Create a new constructor by mixing in the components
                    var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                    // Now, create an instance of our custom grid
                    grid = new CustomGrid({
                        columns: columnHeaders
                    }, "grid");

                    // unclick superfluous fields
                    getObject('grid-hider-menu-check-label').click();
                    getObject('grid-hider-menu-check-userDN').click();
                    getObject('grid-hider-menu-check-srcHost').click();
                    getObject('grid-hider-menu-check-locale').click();
                    getObject('grid-hider-menu-check-lastUrl').click();

                    refreshData();
                });
    }

    function refreshData() {
        showWaitDialog();
        require(["dojo"],function(dojo){
            grid.refresh();
            var maximum = getObject('maxResults').value;
            var url = PWM_GLOBAL['url-restservice'] + "/app-data/session?maximum=" + maximum + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
            dojo.xhrGet({
                url: url,
                preventCache: true,
                handleAs: 'json',
                load: function(data) {
                    closeWaitDialog();
                    grid.renderArray(data['data']);
                    grid.set("sort", { attribute : 'createTime', ascending: false, descending: true });
                },
                error: function(error) {
                    closeWaitDialog();
                    alert('unable to load data: ' + error);
                }
            });
        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/form/NumberSpinner","dojo/domReady!"],function(dojoParser){
            dojoParser.parse();
            initGrid();
        });
    });
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
