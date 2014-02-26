/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

var PWM_ADMIN = PWM_ADMIN || {};
var PWM_STRINGS = PWM_STRINGS || {};

PWM_ADMIN.initAdminOtherMenu=function() {
    require(["dijit/form/DropDownButton", "dijit/DropDownMenu", "dijit/Menu","dijit/MenuItem", "dijit/PopupMenuItem", "dojo/dom", "dijit/MenuSeparator"],
        function(DropDownButton, DropDownMenu, Menu, MenuItem, PopupMenuItem, dom, MenuSeparator){
            var pMenu = new DropDownMenu({ style: "display: none;"});

            pMenu.addChild(new MenuItem({
                label: 'Configuration Manager',
                onClick: function() {
                    PWM_MAIN.goto('/private/config/ConfigManager');
                }
            }));
            pMenu.addChild(new MenuItem({
                label: 'Configuration Editor',
                onClick: function() {
                    PWM_MAIN.goto('/private/config/ConfigEditor');
                }
            }));
            pMenu.addChild(new MenuSeparator());

            pMenu.addChild(new MenuItem({
                label: 'Event Log',
                onClick: function() {
                    PWM_MAIN.goto('/private/admin/eventlog.jsp');
                }
            }));
            pMenu.addChild(new MenuItem({
                label: 'Token Lookup',
                onClick: function() {
                    PWM_MAIN.goto('/private/admin/tokenlookup.jsp');
                }
            }));

            pMenu.addChild(new MenuSeparator());
            pMenu.addChild(new MenuItem({
                label: 'REST Services Reference',
                onClick: function() {
                    PWM_MAIN.goto('/public/rest.jsp');
                }
            }));
            pMenu.addChild(new MenuItem({
                label: 'Software License Reference',
                onClick: function() {
                    PWM_MAIN.goto('/public/license.jsp');
                }
            }));
            pMenu.addChild(new MenuItem({
                label: 'Error Code Reference',
                onClick: function() {
                    PWM_MAIN.goto('/private/admin/error-reference.jsp');
                }
            }));
            if (PWM_GLOBAL['setting-displayEula'] == true) {
                pMenu.addChild(new MenuItem({
                    label: 'View EULA',
                    onClick: function() {
                        PWM_MAIN.showEula(false,null);
                    }
                }));
            }

            pMenu.addChild(new MenuSeparator());
            pMenu.addChild(new MenuItem({
                label: 'Main Menu',
                onClick: function() {
                    PWM_MAIN.goto('/');
                }
            }));

            var dropDownButton = new DropDownButton({
                label: "More Options",
                name: "More Options",
                dropDown: pMenu,
                id: "progButton"
            });
            dom.byId("dropDownButtonContainer").appendChild(dropDownButton.domNode);
        });
}

PWM_ADMIN.initReportDataGrid=function() {
    var headers = {
        "username":PWM_ADMIN.showString("Field_Report_Username"),
        "userDN":PWM_ADMIN.showString("Field_Report_UserDN"),
        "ldapProfile":PWM_ADMIN.showString("Field_Report_LDAP_Profile"),
        "userGUID":PWM_ADMIN.showString("Field_Report_UserGuid"),
        "passwordExpirationTime":PWM_ADMIN.showString("Field_Report_PwdExpireTime"),
        "passwordChangeTime":PWM_ADMIN.showString("Field_Report_PwdChangeTime"),
        "responseSetTime":PWM_ADMIN.showString("Field_Report_ResponseSaveTime"),
        "lastLoginTime":PWM_ADMIN.showString("Field_Report_LastLogin"),
        "hasResponses":PWM_ADMIN.showString("Field_Report_HasResponses"),
        "responseStorageMethod":PWM_ADMIN.showString("Field_Report_ResponseStorageMethod"),
        "requiresPasswordUpdate":PWM_ADMIN.showString("Field_Report_RequiresPasswordUpdate"),
        "requiresResponseUpdate":PWM_ADMIN.showString("Field_Report_RequiresResponseUpdate"),
        "requiresProfileUpdate":PWM_ADMIN.showString("Field_Report_RequiresProfileUpdate"),
        "cacheTimestamp":PWM_ADMIN.showString("Field_Report_RecordCacheTime")
    };

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
            var columnHeaders = headers;

            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

            // Now, create an instance of our custom grid
            var grid = new CustomGrid({
                columns: columnHeaders
            }, "grid");
            PWM_VAR['reportGrid'] = grid;
            // unclick superfluous fields
            PWM_MAIN.getObject('grid-hider-menu-check-cacheTimestamp').click();
            PWM_MAIN.getObject('grid-hider-menu-check-ldapProfile').click();
            PWM_MAIN.getObject('grid-hider-menu-check-userGUID').click();
            PWM_MAIN.getObject('grid-hider-menu-check-responseStorageMethod').click();
            PWM_MAIN.getObject('grid-hider-menu-check-userDN').click();
            PWM_ADMIN.refreshReportDataGrid();
        });
}

PWM_ADMIN.refreshReportDataGrid=function() {
    require(["dojo"],function(dojo){
        PWM_VAR['reportGrid'].refresh();
        var maximum = PWM_MAIN.getObject('maxReportDataResults').value;
        var url = PWM_GLOBAL['url-restservice'] + "/report?maximum=" + maximum;
        dojo.xhrGet({
            url: url,
            preventCache: true,
            headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
            handleAs: 'json',
            load: function(data) {
                PWM_MAIN.closeWaitDialog();
                PWM_VAR['reportGrid'].renderArray(data['data']['users']);
            },
            error: function(error) {
                PWM_MAIN.closeWaitDialog();
                alert('unable to load data: ' + error);
            }
        });
    });
}


PWM_ADMIN.refreshReportDataStatus=function(refreshTime) {
    var doRefresh = refreshTime
        ? function(){setTimeout(function(){PWM_ADMIN.refreshReportDataStatus(refreshTime);},refreshTime);}
        : function(){};

    require(["dojo"],function(dojo){
        var url = PWM_GLOBAL['url-restservice'] + "/report/status";
        dojo.xhrGet({
            url: url,
            preventCache: true,
            headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
            handleAs: 'json',
            load: function(data) {
                if (data['data'] && data['data']['presentable']) {
                    var fields = data['data']['presentable'];
                    var htmlTable = '';
                    for (var field in fields) {
                        htmlTable += '<tr><td>' + field + '</td><td>' + fields[field] + '</tr>';
                    }
                    PWM_MAIN.getObject('statusTable').innerHTML = htmlTable;
                }
                if (data['data']['raw']['inprogress']) {
                    PWM_MAIN.getObject("reportStartButton").disabled = true;
                    PWM_MAIN.getObject("reportStopButton").disabled = false;
                    PWM_MAIN.getObject("reportClearButton").disabled = true;
                } else {
                    PWM_MAIN.getObject("reportStartButton").disabled = false;
                    PWM_MAIN.getObject("reportStopButton").disabled = true;
                    PWM_MAIN.getObject("reportClearButton").disabled = false;
                }
                if (refreshTime) {
                }
                doRefresh();
            },
            error: function(error) {
                var errorMsg = 'unable to load data: ' + error;
                PWM_MAIN.getObject('statusTable').innerHTML = '<tr><td>' + errorMsg + '</td></tr>';
                doRefresh();
            }
        });
    });
}

PWM_ADMIN.refreshReportDataSummary=function(refreshTime) {
    var doRefresh = refreshTime
        ? function(){setTimeout(function(){PWM_ADMIN.refreshReportDataSummary(refreshTime);},refreshTime);}
        : function(){};


    require(["dojo","dojo/number"],function(dojo,number){

        var url = PWM_GLOBAL['url-restservice'] + "/report/summary";
        dojo.xhrGet({
            url: url,
            preventCache: true,
            headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
            handleAs: 'json',
            load: function(data) {
                var pctTotal = function(value) {
                    return fields['totalUsers'] == 0 ? 0 : ((value / fields['totalUsers']) * 100).toFixed(2);
                }
                var makeRow = function(key,value,pct) {
                    return '<tr><td>' + key + '</td><td>' + number.format(value) + '</td><td>' + (pct ? pctTotal(pct) + '%': '') + '</td></tr>';
                };
                if (data['data'] && data['data']['raw']) {
                    var fields = data['data']['raw'];
                    var htmlTable = '';
                    htmlTable += makeRow("Total Users",fields['totalUsers']);
                    htmlTable += makeRow("Users that have stored responses",fields['hasResponses'],fields['hasResponses']);
                    for (var type in fields['responseStorage']) {
                        htmlTable += makeRow("Responses stored in " + type,fields['responseStorage'][type],fields['responseStorage'][type]);
                    }

                    htmlTable += makeRow("Users that have a response set time",fields['hasResponseSetTime'],fields['hasResponseSetTime']);
                    htmlTable += makeRow("Users that have a response set time in previous 3 days", fields['responseSetPrevious_3'], fields['responseSetPrevious_3']);
                    htmlTable += makeRow("Users that have a response set time in previous 7 days", fields['responseSetPrevious_7'], fields['responseSetPrevious_7']);
                    htmlTable += makeRow("Users that have a response set time in previous 14 days",fields['responseSetPrevious_14'],fields['responseSetPrevious_14']);
                    htmlTable += makeRow("Users that have a response set time in previous 30 days",fields['responseSetPrevious_30'],fields['responseSetPrevious_30']);
                    htmlTable += makeRow("Users that have a response set time in previous 60 days",fields['responseSetPrevious_60'],fields['responseSetPrevious_60']);
                    htmlTable += makeRow("Users that have a response set time in previous 90 days",fields['responseSetPrevious_90'],fields['responseSetPrevious_90']);

                    htmlTable += makeRow("Users that have an expiration time set",fields['hasExpirationTime'],fields['hasExpirationTime']);
                    htmlTable += makeRow("Users that have an expiration time in previous 3 days",fields['expirePrevious_3'],fields['expirePrevious_3']);
                    htmlTable += makeRow("Users that have an expiration time in previous 7 days",fields['expirePrevious_7'],fields['expirePrevious_7']);
                    htmlTable += makeRow("Users that have an expiration time in previous 14 days",fields['expirePrevious_14'],fields['expirePrevious_14']);
                    htmlTable += makeRow("Users that have an expiration time in previous 30 days",fields['expirePrevious_30'],fields['expirePrevious_30']);
                    htmlTable += makeRow("Users that have an expiration time in previous 60 days",fields['expirePrevious_60'],fields['expirePrevious_60']);
                    htmlTable += makeRow("Users that have an expiration time in previous 90 days",fields['expirePrevious_90'],fields['expirePrevious_90']);
                    htmlTable += makeRow("Users that have an expiration time in next 3 days",fields['expireNext_3'],fields['expireNext_3']);
                    htmlTable += makeRow("Users that have an expiration time in next 7 days",fields['expireNext_7'],fields['expireNext_7']);
                    htmlTable += makeRow("Users that have an expiration time in next 14 days",fields['expireNext_14'],fields['expireNext_14']);
                    htmlTable += makeRow("Users that have an expiration time in next 30 days",fields['expireNext_30'],fields['expireNext_30']);
                    htmlTable += makeRow("Users that have an expiration time in next 60 days",fields['expireNext_60'],fields['expireNext_60']);
                    htmlTable += makeRow("Users that have an expiration time in next 90 days",fields['expireNext_90'],fields['expireNext_90']);

                    htmlTable += makeRow("Users that have changed password",fields['hasChangePwTime'],fields['hasChangePwTime']);
                    htmlTable += makeRow("Users that have changed password in previous 3 days", fields['changePrevious_3'],fields['changePrevious_3']);
                    htmlTable += makeRow("Users that have changed password in previous 7 days", fields['changePrevious_7'],fields['changePrevious_7']);
                    htmlTable += makeRow("Users that have changed password in previous 14 days",fields['changePrevious_14'],fields['changePrevious_14']);
                    htmlTable += makeRow("Users that have changed password in previous 30 days",fields['changePrevious_30'],fields['changePrevious_30']);
                    htmlTable += makeRow("Users that have changed password in previous 60 days",fields['changePrevious_60'],fields['changePrevious_60']);
                    htmlTable += makeRow("Users that have changed password in previous 90 days",fields['changePrevious_90'],fields['changePrevious_90']);

                    htmlTable += makeRow("Users that have logged in",fields['hasLoginTime'],fields['hasLoginTime']);
                    htmlTable += makeRow("Users with expired password",fields['pwExpired'],fields['pwExpired']);
                    htmlTable += makeRow("Users with pre-expired password",fields['pwPreExpired'],fields['pwPreExpired']);
                    htmlTable += makeRow("Users with expired password within warn period",fields['pwWarnPeriod'],fields['pwWarnPeriod']);
                    /*
                     for (var field in fields) {
                     htmlTable += '<tr><td>' + field + '</td><td>' + fields[field] + '</tr>';
                     }
                     */
                    PWM_MAIN.getObject('summaryTable').innerHTML = htmlTable;
                }
                doRefresh();
            },
            error: function(error) {
                var errorMsg = 'unable to load data: ' + error;
                PWM_MAIN.getObject('summaryTable').innerHTML = errorMsg;
                doRefresh();
            }
        });
    });
}

PWM_ADMIN.reportAction=function(action) {
    var confirmText, successText;
    if (!action) {
        return;
    }
    if (action=='start') {
        confirmText = 'Start the report engine?  This may take a long while to run.';
        successText = 'Report engine has been started.';
    } else if (action=='stop') {
        confirmText = 'Stop the report engine?  You can restart the report engine at any time.';
        successText= 'Report engine has been stopped.';
    } else if (action=='clear') {
        confirmText = 'Clear the cached report data?  This will clear all cached report records and summary.';
        successText = 'Cached report data cleared';
    }
    PWM_MAIN.showConfirmDialog(null,confirmText,function(){
        PWM_MAIN.showWaitDialog(null,null,function(){
            setTimeout(function(){
                require(["dojo"],function(dojo){
                    var url = 'analysis.jsp?reportAction=' + action;
                    dojo.xhrGet({
                        url: url,
                        preventCache: true,
                        load: function() {
                            PWM_MAIN.closeWaitDialog();
                            PWM_MAIN.showDialog("Success",successText,function(){
                                PWM_ADMIN.refreshReportDataStatus();
                                PWM_ADMIN.refreshReportDataSummary();
                            });
                        },
                        error: function(error) {
                            PWM_MAIN.closeWaitDialog();
                            alert('unable to send report action: ' + error);
                        }
                    });
                });
            },3000);
        });
    },function(){});
}

PWM_ADMIN.initActiveSessionGrid=function() {
    var headers = {
        "userID":"User ID",
        "userDN":"User DN",
        "createTime":"Create Time",
        "lastTime":"Last Time",
        "label":"Label",
        "idle":"Idle",
        "srcAddress":"Address",
        "locale":"Locale",
        "srcHost":"Host",
        "lastUrl":"Last URL"
    };

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
            var columnHeaders = headers;

            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

            // Now, create an instance of our custom grid
            PWM_VAR['activeSessionsGrid'] = new CustomGrid({
                columns: columnHeaders
            }, "activeSessionGrid");

            // unclick superfluous fields
            PWM_MAIN.getObject('activeSessionGrid-hider-menu-check-label').click();
            PWM_MAIN.getObject('activeSessionGrid-hider-menu-check-userDN').click();
            PWM_MAIN.getObject('activeSessionGrid-hider-menu-check-srcHost').click();
            PWM_MAIN.getObject('activeSessionGrid-hider-menu-check-locale').click();
            PWM_MAIN.getObject('activeSessionGrid-hider-menu-check-lastUrl').click();

            PWM_ADMIN.refreshActiveSessionGrid();
        });
}

PWM_ADMIN.refreshActiveSessionGrid=function() {
    require(["dojo"],function(dojo){
        var grid = PWM_VAR['activeSessionsGrid'];
        grid.refresh();
        var maximum = PWM_MAIN.getObject('maxActiveSessionResults').value;
        var url = PWM_GLOBAL['url-restservice'] + "/app-data/session?maximum=" + maximum;
        dojo.xhrGet({
            url: url,
            preventCache: true,
            headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
            handleAs: 'json',
            load: function(data) {
                grid.renderArray(data['data']);
                grid.set("sort", { attribute : 'createTime', ascending: false, descending: true });
            },
            error: function(error) {
                alert('unable to load data: ' + error);
            }
        });
    });
}


PWM_ADMIN.initIntrudersGrid=function() {
    PWM_VAR['intruderRecordTypes'] = ["ADDRESS","USERNAME","USER_ID","ATTRIBUTE","TOKEN_DEST"];
    var intruderGridHeaders = {"subject":"Subject","timestamp":"Timestamp","count":"Count","status":"Status"};
    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

            // Now, create an instance of our custom grid
            PWM_VAR['intruderGrid'] = {};
            for (var i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
                var recordType = PWM_VAR['intruderRecordTypes'][i];
                PWM_VAR['intruderGrid'][recordType] = new CustomGrid({ columns: intruderGridHeaders}, recordType + "_Grid");
            }

            PWM_ADMIN.refreshIntruderGrid();
        });
};

PWM_ADMIN.refreshIntruderGrid=function() {
    require(["dojo"],function(dojo){
        for (var i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
            var recordType = PWM_VAR['intruderRecordTypes'][i];
            PWM_VAR['intruderGrid'][recordType].refresh();
        }
        var maximum = PWM_MAIN.getObject('maxIntruderGridResults').value;
        var url = PWM_GLOBAL['url-restservice'] + "/app-data/intruder?maximum=" + maximum;
        dojo.xhrGet({
            url: url,
            preventCache: true,
            headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
            handleAs: 'json',
            load: function(data) {
                for (var i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
                    var recordType = PWM_VAR['intruderRecordTypes'][i];
                    PWM_VAR['intruderGrid'][recordType].renderArray(data['data'][recordType]);
                }
            },
            error: function(error) {
                PWM_MAIN.closeWaitDialog();
                alert('unable to load data: ' + error);
            }
        });
    });
};

PWM_ADMIN.initAuditGrid=function() {
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
    var systemHeaders = {
        "timestamp":"Time",
        "eventCode":"Event",
        "message":"Message",
        "instance":"Instance"
    };
    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

            // Now, create an instance of our custom userGrid
            PWM_VAR['auditUserGrid'] = new CustomGrid({columns: userHeaders}, "auditUserGrid");
            PWM_VAR['auditSystemGrid'] = new CustomGrid({columns: systemHeaders}, "auditSystemGrid");

            // unclick superfluous fields
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-perpetratorDN').click();
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-message').click();
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-targetDN').click();
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-sourceHost').click();

            PWM_ADMIN.refreshAuditGridData();
        });
}

PWM_ADMIN.refreshAuditGridData=function() {
    require(["dojo"],function(dojo){
        PWM_VAR['auditUserGrid'].refresh();
        PWM_VAR['auditSystemGrid'].refresh();
        var maximum = PWM_MAIN.getObject('maxAuditGridResults').value;
        var url = PWM_GLOBAL['url-restservice'] + "/app-data/audit?maximum=" + maximum;
        dojo.xhrGet({
            url: url,
            preventCache: true,
            headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
            handleAs: 'json',
            load: function(data) {
                PWM_VAR['auditUserGrid'].renderArray(data['data']['user']);
                PWM_VAR['auditUserGrid'].set("sort", { attribute : 'timestamp', ascending: false, descending: true });
                PWM_VAR['auditSystemGrid'].renderArray(data['data']['system']);
                PWM_VAR['auditSystemGrid'].set("sort", { attribute : 'timestamp', ascending: false, descending: true });
            },
            error: function(error) {
                alert('unable to load data: ' + error);
            }
        });
    });
}

PWM_ADMIN.showStatChart = function(statName,days,divName,options) {
    var options = options || {};
    var doRefresh = options['refreshTime']
        ? function(){setTimeout(function(){PWM_ADMIN.showStatChart(statName,days,divName,options);},options['refreshTime']);}
        : function(){};
    var epsTypes = PWM_GLOBAL['epsTypes'];
    var epsDurations = PWM_GLOBAL['epsDurations'];
    require(["dojo",
        "dijit",
        "dijit/registry",
        "dojox/charting/Chart2D",
        "dojox/charting/axis2d/Default",
        "dojox/charting/plot2d/Default",
        "dojox/charting/themes/Wetland",
        "dijit/form/Button",
        "dojox/gauges/GlossyCircularGauge",
        "dojo/domReady!"],
        function(dojo,dijit,registry){
            var statsGetUrl = PWM_GLOBAL['url-restservice'] + "/statistics";
            statsGetUrl += "?statName=" + statName;
            statsGetUrl += "&days=" + days;

            dojo.xhrGet({
                url: statsGetUrl,
                handleAs: "json",
                headers: {"Accept":"application/json","X-RestClientKey":PWM_GLOBAL['restClientKey']},
                timeout: 15 * 1000,
                preventCache: true,
                error: function(data) {
                    for (var loopEpsTypeIndex = 0; loopEpsTypeIndex < epsTypes.length; loopEpsTypeIndex++) { // clear all the gauges
                        var loopEpsName = epsTypes[loopEpsTypeIndex] + '';
                        for (var loopEpsDurationsIndex = 0; loopEpsDurationsIndex < epsDurations.length; loopEpsDurationsIndex++) { // clear all the gauges
                            var loopEpsDuration = epsDurations[loopEpsDurationsIndex] + '';
                            var loopEpsID = "EPS-GAUGE-" + loopEpsName + "_" + loopEpsDuration;
                            if (PWM_MAIN.getObject(loopEpsID) != null) {
                                if (registry.byId(loopEpsID)) {
                                    registry.byId(loopEpsID).setAttribute('value',0);
                                }
                            }
                        }
                    }
                    doRefresh();
                },
                load: function(data) {
                    {// gauges
                        console.log('Beginning stats update process...');
                        data = data['data'];
                        var activityCount = 0;
                        for (var loopEpsIndex = 0; loopEpsIndex < epsTypes.length; loopEpsIndex++) {
                            var loopEpsName = epsTypes[loopEpsIndex] + '';
                            for (var loopEpsDurationsIndex = 0; loopEpsDurationsIndex < epsDurations.length; loopEpsDurationsIndex++) { // clear all the gauges
                                var loopEpsDuration = epsDurations[loopEpsDurationsIndex] + '';
                                var loopEpsID = "EPS-GAUGE-" + loopEpsName + "_" + loopEpsDuration;
                                var loopFieldEpsID = "FIELD_" + loopEpsName + "_" + loopEpsDuration;
                                var loopEpsValue = data['EPS'][loopEpsName + "_" + loopEpsDuration];
                                var loopEpmValue = (loopEpsValue * 60).toFixed(3);
                                var loopTop = PWM_GLOBAL['client.activityMaxEpsRate'];
                                if (loopEpsDuration == "HOURLY") {
                                    activityCount += loopEpsValue;
                                }
                                if (PWM_MAIN.getObject(loopFieldEpsID) != null) {
                                    PWM_MAIN.getObject(loopFieldEpsID).innerHTML = loopEpmValue;
                                }
                                if (PWM_MAIN.getObject(loopEpsID) != null) {
                                    console.log('EpsID=' + loopEpsID + ', ' + 'Eps=' + loopEpsValue + ', ' + 'Epm=' + loopEpmValue);
                                    if (registry.byId(loopEpsID)) {
                                        registry.byId(loopEpsID).setAttribute('value',loopEpmValue);
                                        registry.byId(loopEpsID).setAttribute('max',loopTop);
                                    } else {
                                        var glossyCircular = new dojox.gauges.GlossyCircularGauge({
                                            background: [255, 255, 255, 0],
                                            noChange: true,
                                            value: loopEpmValue,
                                            max: loopTop,
                                            needleColor: '#FFDC8B',
                                            majorTicksInterval: Math.abs(loopTop / 10),
                                            minorTicksInterval: Math.abs(loopTop / 10),
                                            id: loopEpsID,
                                            width: 200,
                                            height: 150
                                        }, dojo.byId(loopEpsID));
                                        glossyCircular.startup();
                                    }
                                }
                            }
                        }
                        PWM_GLOBAL['epsActivityCount'] = activityCount;
                    }
                    if (divName != null && PWM_MAIN.getObject(divName)) { // stats chart
                        var values = [];
                        for(var key in data['nameData']) {
                            var value = data['nameData'][key];
                            values.push(parseInt(value));
                        }

                        if (PWM_GLOBAL[divName + '-stored-reference']) {
                            var existingChart = PWM_GLOBAL[divName + '-stored-reference'];
                            existingChart.destroy();
                        }
                        var c = new dojox.charting.Chart2D(divName);
                        PWM_GLOBAL[divName + '-stored-reference'] = c;
                        c.addPlot("default", {type: "Columns", gap:'2'});
                        c.addAxis("x", {});
                        c.addAxis("y", {vertical: true});
                        c.setTheme(dojox.charting.themes.Wetland);
                        c.addSeries("Series 1", values);
                        c.render();
                    }
                    doRefresh();
                }
            });
        });
};

PWM_ADMIN.showAppHealth = function(parentDivID, options, refreshNow) {
    var inputOpts = options || PWM_GLOBAL['showPwmHealthOptions'] || {};
    PWM_GLOBAL['showPwmHealthOptions'] = options;
    var refreshUrl = inputOpts['sourceUrl'] || PWM_GLOBAL['url-restservice'] + "/health";
    var showRefresh = inputOpts['showRefresh'];
    var showTimestamp = inputOpts['showTimestamp'];
    var refreshTime = inputOpts['refreshTime'] || 10 * 1000;
    var finishFunction = inputOpts['finishFunction'];

    {
        refreshUrl += refreshUrl.indexOf('?') == -1 ? '?' : '&';
        refreshUrl += "pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    }

    console.log('starting showPwmHealth: refreshTime=' + refreshTime);
    require(["dojo","dojo/date/stamp"],function(dojo,stamp){
        var parentDiv = dojo.byId(parentDivID);

        if (PWM_GLOBAL['healthCheckInProgress']) {
            return;
        }


        PWM_GLOBAL['healthCheckInProgress'] = "true";

        if (refreshNow) {
            parentDiv.innerHTML = '<div id="WaitDialogBlank" style="margin-top: 20px; margin-bottom: 20px"/>';
            refreshUrl += refreshUrl.indexOf('?') > 0 ? '&' : '?';
            refreshUrl += "&refreshImmediate=true";
        }

        dojo.xhrGet({
            url: refreshUrl,
            handleAs: "json",
            headers: { "Accept":"application/json","X-RestClientKey":PWM_GLOBAL['restClientKey'] },
            timeout: 60 * 1000,
            preventCache: true,
            load: function(data) {
                if (data['error']) {

                } else {
                    PWM_GLOBAL['pwm-health'] = data['data']['overall'];
                    var healthRecords = data['data']['records'];
                    var htmlBody = '<table width="100%" style="width=100%; border=0">';
                    for (var i = 0; i < healthRecords.length; i++) {
                        var healthData = healthRecords[i];
                        htmlBody += '<tr><td class="key" style="width:1px; white-space:nowrap;"">';
                        htmlBody += healthData['topic'];
                        htmlBody += '</td><td class="health-' + healthData['status'] + '">';
                        htmlBody += healthData['status'];
                        htmlBody += "</td><td>";
                        htmlBody += healthData['detail'];
                        htmlBody += "</td></tr>";
                    }
                    if (showTimestamp || showRefresh) {
                        htmlBody += '<tr><td colspan="3" style="text-align:center;">';
                        if (showTimestamp) {
                            htmlBody += (data['data']['timestamp'] + '&nbsp;&nbsp;&nbsp;&nbsp;');
                        }
                        if (showRefresh) {
                            htmlBody += '<a title="refresh" href="#"; onclick="PWM_ADMIN.showAppHealth(\'' + parentDivID + '\',PWM_GLOBAL[\'showPwmHealthOptions\'],true)">';
                            htmlBody += '<span class="fa fa-refresh"></span>';
                            htmlBody += '</a>';
                        }
                        htmlBody += "</td></tr>";
                    }

                    htmlBody += '</table>';
                    parentDiv.innerHTML = htmlBody;
                    PWM_GLOBAL['healthCheckInProgress'] = false;
                    if (refreshTime > 0) {
                        setTimeout(function() {
                            PWM_ADMIN.showAppHealth(parentDivID, options);
                        }, refreshTime);
                    }
                    if (finishFunction) {
                        finishFunction();
                    }
                }
            },
            error: function(error) {
                if (error != null) {
                    console.log('error reaching server: ' + error);
                }
                var htmlBody = '<div style="text-align:center; background-color: #d20734">';
                htmlBody += '<br/><span style="font-weight: bold;">unable to load health data from server</span></br>';
                htmlBody += '<br/>' + new Date().toLocaleString() + '&nbsp;&nbsp;&nbsp;';
                if (showRefresh) {
                    htmlBody += '<a href="#" onclick="PWM_ADMIN.showAppHealth(\'' + parentDivID + '\',null,true)">retry</a><br/><br/>';
                }
                htmlBody += '</div>';
                parentDiv.innerHTML = htmlBody;
                PWM_GLOBAL['healthCheckInProgress'] = false;
                PWM_GLOBAL['pwm-health'] = 'WARN';
                if (refreshTime > 0) {
                    setTimeout(function() {
                        PWM_ADMIN.showAppHealth(parentDivID, options);
                    }, refreshTime);
                }
                if (finishFunction) {
                    finishFunction();
                }
            }
        });
    });
};

PWM_ADMIN.initAdminPage=function(nextFunction) {
    require(["dojo"],function(dojo){
        var clientConfigUrl = PWM_GLOBAL['url-context'] + "/public/rest/app-data/strings/Admin";
        dojo.xhrGet({
            url: clientConfigUrl,
            handleAs: 'json',
            timeout: 30 * 1000,
            headers: { "Accept": "application/json" },
            load: function(data) {
                if (data['error'] == true) {
                    alert('unable to load ' + clientConfigUrl + ', error: ' + data['errorDetail'])
                } else {
                    PWM_STRINGS['bundle-admin'] = {};
                    for (var settingKey in data['data']) {
                        PWM_STRINGS['bundle-admin'][settingKey] = data['data'][settingKey];
                    }
                }
                if (nextFunction) {
                    nextFunction();
                }
            },
            error: function(error) {
                PWM_MAIN.showError('Unable to read display data from server, please reload page (' + error + ')');
                console.log('unable to read settings app-data: ' + error);
            }
        });
    });
};

PWM_ADMIN.showString=function (key) {
    if (PWM_STRINGS['bundle-admin'][key]) {
        return PWM_STRINGS['bundle-admin'][key];
    } else {
        return "UNDEFINED STRING-" + key;
    }
};
