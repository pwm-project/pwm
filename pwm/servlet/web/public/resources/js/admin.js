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
        "username":"Username",
        "userDN":"User DN",
        "ldapProfile":"Ldap Profile",
        "userGUID":"GUID",
        "passwordExpirationTime":"Password Expiration Time",
        "passwordChangeTime":"Password Change Time",
        "responseSetTime":"Response Set Time",
        "lastLoginTime":"Last LDAP Login Time",
        "hasResponses":"Has Responses",
        "responseStorageMethod":"Response Storage Method",
        "requiresPasswordUpdate":"Requires Password Update",
        "requiresResponseUpdate":"Requires Response Update",
        "requiresProfileUpdate":"Requires Profile Update",
        "cacheTimestamp":"Record Cache Time"
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

    require(["dojo"],function(dojo){
        var url = PWM_GLOBAL['url-restservice'] + "/report/summary";
        dojo.xhrGet({
            url: url,
            preventCache: true,
            headers: {"X-RestClientKey":PWM_GLOBAL['restClientKey']},
            handleAs: 'json',
            load: function(data) {
                if (data['data'] && data['data']['raw']) {
                    var fields = data['data']['raw'];
                    var htmlTable = '';
                    var makeRow = function(key,value) {
                        return '<tr><td>' + key + '</td><td>' + value + '</tr>';
                    };
                    var pctTotal = function(value) {
                        return fields['totalUsers'] == 0 ? 0 : ((value / fields['totalUsers']) * 100).toFixed(2);
                    }
                    htmlTable += makeRow("Total Users",fields['totalUsers']);
                    htmlTable += makeRow("Users that have stored responses",fields['hasResponses'] + '  (' + pctTotal(fields['hasResponses']) + '%)');
                    for (var type in fields['responseStorage']) {
                        htmlTable += makeRow("Responses stored in " + type,fields['responseStorage'][type] + '  (' + pctTotal(fields['responseStorage'][type]) + '%)');
                    }
                    htmlTable += makeRow("Users that have an expiration time",fields['hasExpirationTime'] + '  (' + pctTotal(fields['hasResponses']) + '%)');
                    htmlTable += makeRow("Users that have changed password",fields['hasChangePwTime'] + '  (' + pctTotal(fields['hasChangePwTime']) + '%)');
                    htmlTable += makeRow("Users that have logged in",fields['hasLoginTime'] + '  (' + pctTotal(fields['hasLoginTime']) + '%)');
                    htmlTable += makeRow("Users with expired password",fields['pwExpired'] + '  (' + pctTotal(fields['pwExpired']) + '%)');
                    htmlTable += makeRow("Users with pre-expired password",fields['pwPreExpired'] + '  (' + pctTotal(fields['pwPreExpired']) + '%)');
                    htmlTable += makeRow("Users with expired password within warn period",fields['pwWarnPeriod'] + '  (' + pctTotal(fields['pwWarnPeriod']) + '%)');
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

