/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
var PWM_MAIN = PWM_MAIN || {};
var PWM_GLOBAL = PWM_GLOBAL || {};

PWM_ADMIN.initAdminOtherMenu=function() {
    require(["dijit/form/DropDownButton", "dijit/DropDownMenu", "dijit/Menu","dijit/MenuItem", "dijit/PopupMenuItem", "dojo/dom", "dijit/MenuSeparator"],
        function(DropDownButton, DropDownMenu, Menu, MenuItem, PopupMenuItem, dom, MenuSeparator){
            var pMenu = new DropDownMenu({ style: "display: none;"});
            pMenu.addChild(new MenuItem({
                label: 'Event Log',
                onClick: function() {
                    PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('form-viewLog'));
                }
            }));
            pMenu.addChild(new MenuItem({
                label: 'Token Lookup',
                onClick: function() {
                    PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('form-tokenLookup'));
                }
            }));

            pMenu.addChild(new MenuItem({
                label: 'URL Reference',
                onClick: function() {
                    PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('form-urlReference'));
                }
            }));
            pMenu.addChild(new MenuSeparator());
            pMenu.addChild(new MenuItem({
                label: 'Full Page Health Status',
                onClick: function() {
                    PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/public/health.jsp');
                }
            }));
            pMenu.addChild(new MenuSeparator());
            pMenu.addChild(new MenuItem({
                label: '<span class="fa fa-external-link"></span> Software License Reference',
                onClick: function() {
                    PWM_MAIN.newWindowOpen(PWM_GLOBAL['url-context'] + '/public/reference/license.jsp','license');
                }
            }));
            pMenu.addChild(new MenuItem({
                label: '<span class="fa fa-external-link"></span> Application Reference',
                onClick: function() {
                    PWM_MAIN.newWindowOpen(PWM_GLOBAL['url-context'] + '/public/reference/referencedoc.jsp','referencedoc');
                }
            }));
            pMenu.addChild(new MenuItem({
                label: '<span class="fa fa-external-link"></span> REST Web Services Reference',
                onClick: function() {
                    PWM_MAIN.newWindowOpen(PWM_GLOBAL['url-context'] + '/public/reference/rest.jsp','restreference');
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
                label: 'Configuration Manager',
                onClick: function() {
                    PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/private/config/ConfigManager');
                }
            }));
            pMenu.addChild(new MenuItem({
                label: 'Configuration Editor',
                onClick: function() {
                    PWM_CONFIG.startConfigurationEditor();
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
};

PWM_ADMIN.reportDataHeaders = function() {
    return {
        "username":PWM_ADMIN.showString("Field_Report_Username"),
        "userDN":PWM_ADMIN.showString("Field_Report_UserDN"),
        "ldapProfile":PWM_ADMIN.showString("Field_Report_LDAP_Profile"),
        "userGUID":PWM_ADMIN.showString("Field_Report_UserGuid"),
        "accountExpirationTime":PWM_ADMIN.showString("Field_Report_AccountExpireTime"),
        "passwordExpirationTime":PWM_ADMIN.showString("Field_Report_PwdExpireTime"),
        "passwordChangeTime":PWM_ADMIN.showString("Field_Report_PwdChangeTime"),
        "responseSetTime":PWM_ADMIN.showString("Field_Report_ResponseSaveTime"),
        "lastLoginTime":PWM_ADMIN.showString("Field_Report_LastLogin"),
        "hasResponses":PWM_ADMIN.showString("Field_Report_HasResponses"),
        "hasHelpdeskResponses":PWM_ADMIN.showString("Field_Report_HasHelpdeskResponses"),
        "responseStorageMethod":PWM_ADMIN.showString("Field_Report_ResponseStorageMethod"),
        "responseFormatType":PWM_ADMIN.showString("Field_Report_ResponseFormatType"),
        "requiresPasswordUpdate":PWM_ADMIN.showString("Field_Report_RequiresPasswordUpdate"),
        "requiresResponseUpdate":PWM_ADMIN.showString("Field_Report_RequiresResponseUpdate"),
        "requiresProfileUpdate":PWM_ADMIN.showString("Field_Report_RequiresProfileUpdate"),
        "cacheTimestamp":PWM_ADMIN.showString("Field_Report_RecordCacheTime")
    };
};

PWM_ADMIN.initReportDataGrid=function() {
    var headers = PWM_ADMIN.reportDataHeaders();

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            var columnHeaders = headers;

            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry ]);

            // Now, create an instance of our custom grid
            PWM_VAR['reportGrid'] = new CustomGrid({columns: columnHeaders}, "grid");

            // unclick superfluous fields
            PWM_MAIN.getObject('grid-hider-menu-check-cacheTimestamp').click();
            PWM_MAIN.getObject('grid-hider-menu-check-ldapProfile').click();
            PWM_MAIN.getObject('grid-hider-menu-check-userGUID').click();
            PWM_MAIN.getObject('grid-hider-menu-check-responseStorageMethod').click();
            PWM_MAIN.getObject('grid-hider-menu-check-responseFormatType').click();
            PWM_MAIN.getObject('grid-hider-menu-check-userDN').click();
            PWM_MAIN.getObject('grid-hider-menu-check-hasHelpdeskResponses').click();
            PWM_ADMIN.refreshReportDataGrid();

            PWM_VAR['reportGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.reportDataHeaders(), PWM_VAR['reportGrid']);
            });
        });
};

PWM_ADMIN.refreshReportDataGrid=function() {
    require(["dojo"],function(dojo){
        PWM_VAR['reportGrid'].refresh();
        var maximum = PWM_MAIN.getObject('maxReportDataResults').value;
        var url = PWM_GLOBAL['url-restservice'] + "/report?maximum=" + maximum;
        var loadFunction = function(data) {
            PWM_MAIN.closeWaitDialog();
            PWM_VAR['reportGrid'].renderArray(data['data']['users']);
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
    });
};


PWM_ADMIN.refreshReportDataStatus=function(refreshTime) {
    var doRefresh = refreshTime
        ? function(){setTimeout(function(){PWM_ADMIN.refreshReportDataStatus(refreshTime);},refreshTime);}
        : function(){};

    require(["dojo"],function(dojo){
        var url = PWM_GLOBAL['url-restservice'] + "/report/status";
        var loadFunction = function(data) {
            if (data['data'] && data['data']['presentable']) {
                var fields = data['data']['presentable'];
                var htmlTable = '';
                for (var field in fields) {
                    htmlTable += '<tr><td>' + field + '</td><td id="report_status_' + field + '">' + fields[field] + '</tr>';
                }
                PWM_MAIN.getObject('statusTable').innerHTML = htmlTable;
                for (var field in fields) {(function(field){
                    PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject("report_status_" + field));
                    console.log('called + ' + field);
                }(field)); }
            }
            if (data['data']['raw']['inProgress']) {
                PWM_MAIN.getObject("reportStartButton").disabled = true;
                PWM_MAIN.getObject("reportStopButton").disabled = false;
                PWM_MAIN.getObject("reportClearButton").disabled = true;
            } else {
                PWM_MAIN.getObject("reportStartButton").disabled = false;
                PWM_MAIN.getObject("reportStopButton").disabled = true;
                PWM_MAIN.getObject("reportClearButton").disabled = false;
            }
            doRefresh();
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
    });
};

PWM_ADMIN.refreshReportDataSummary=function(refreshTime) {
    var doRefresh = refreshTime
        ? function(){setTimeout(function(){PWM_ADMIN.refreshReportDataSummary(refreshTime);},refreshTime);}
        : function(){};


    require(["dojo","dojo/number"],function(dojo,number){

        var url = PWM_GLOBAL['url-restservice'] + "/report/summary";
        var loadFunction = function(data) {
            if (data['data'] && data['data']['presentable']) {
                var htmlTable = '';
                for (var item in data['data']['presentable']) {
                    var rowData = data['data']['presentable'][item];
                    htmlTable += '<tr><td>' + rowData['label'] + '</td><td>' + rowData['count'] + '</td><td>' + (rowData['pct'] ? rowData['pct'] : '') + '</td></tr>';
                }
                PWM_MAIN.getObject('summaryTable').innerHTML = htmlTable;
            }
            doRefresh();
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
    });
};

PWM_ADMIN.reportAction=function(action) {
    var confirmText, successText;
    if (!action) {
        return;
    }
    if (action=='start') {
        confirmText = PWM_ADMIN.showString('Confirm_Report_Start');
        successText = PWM_ADMIN.showString('Display_Start_Report_Success');
    } else if (action=='stop') {
        confirmText = PWM_ADMIN.showString('Confirm_Report_Stop');
        successText= PWM_ADMIN.showString('Display_Stop_Report_Success');
    } else if (action=='clear') {
        confirmText = PWM_ADMIN.showString('Confirm_Report_Clear');
        successText = PWM_ADMIN.showString('Display_Clear_Report_Success');
    }
    PWM_MAIN.showConfirmDialog({text:confirmText,okAction:function(){
        PWM_MAIN.showWaitDialog({loadFunction:function(){
            setTimeout(function(){
                var url = PWM_GLOBAL['url-restservice'] + "/command/report/" + action;
                var loadFunction = function(data) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Success'),text:successText,nextAction:function(){
                        PWM_ADMIN.refreshReportDataStatus();
                        PWM_ADMIN.refreshReportDataSummary();
                    }});
                };
                PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
            },3000);
        }});
    }});
};

PWM_ADMIN.webSessionHeaders = function() {
    return {
        "userID":PWM_ADMIN.showString('Field_Session_UserID'),
        "ldapProfile":PWM_ADMIN.showString('Field_Session_LdapProfile'),
        "userDN":PWM_ADMIN.showString('Field_Session_UserDN'),
        "createTime":PWM_ADMIN.showString('Field_Session_CreateTime'),
        "lastTime":PWM_ADMIN.showString('Field_Session_LastTime'),
        "label":PWM_ADMIN.showString('Field_Session_Label'),
        "idle":PWM_ADMIN.showString('Field_Session_Idle'),
        "locale":PWM_ADMIN.showString('Field_Session_Locale'),
        "srcAddress":PWM_ADMIN.showString('Field_Session_SrcAddress'),
        "srcHost":PWM_ADMIN.showString('Field_Session_SrcHost'),
        "lastUrl":PWM_ADMIN.showString('Field_Session_LastURL'),
        "intruderAttempts":PWM_ADMIN.showString('Field_Session_IntruderAttempts')
    };
};

PWM_ADMIN.initActiveSessionGrid=function() {
    var headers = PWM_ADMIN.webSessionHeaders();

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            var columnHeaders = headers;

            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry ]);

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
            PWM_MAIN.getObject('activeSessionGrid-hider-menu-check-intruderAttempts').click();

            PWM_ADMIN.refreshActiveSessionGrid();

            PWM_VAR['activeSessionsGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.webSessionHeaders(), PWM_VAR['activeSessionsGrid']);
            });
        });
};

PWM_ADMIN.refreshActiveSessionGrid=function() {
    require(["dojo"],function(dojo){
        var grid = PWM_VAR['activeSessionsGrid'];
        grid.refresh();

        var maximum = PWM_MAIN.getObject('maxActiveSessionResults').value;
        var url = PWM_GLOBAL['url-restservice'] + "/app-data/session?maximum=" + maximum;
        var loadFunction = function(data) {
            grid.renderArray(data['data']);
            grid.set("sort", { attribute : 'createTime', ascending: false, descending: true });
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
    });
};

PWM_ADMIN.intruderHeaders = function(){
    return {
        "subject":PWM_ADMIN.showString('Field_Intruder_Subject'),
        "timestamp":PWM_ADMIN.showString('Field_Intruder_Timestamp'),
        "count":PWM_ADMIN.showString('Field_Intruder_Count'),
        "status":PWM_ADMIN.showString('Field_Intruder_Status')
    };
};


PWM_ADMIN.initIntrudersGrid=function() {
    PWM_VAR['intruderRecordTypes'] = ["ADDRESS","USERNAME","USER_ID","ATTRIBUTE","TOKEN_DEST"];
    var intruderGridHeaders = PWM_ADMIN.intruderHeaders();

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry ]);

            // Now, create an instance of our custom grid
            PWM_VAR['intruderGrid'] = {};
            for (var i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
                (function(iter){
                    var recordType = PWM_VAR['intruderRecordTypes'][iter];
                    var grid = new CustomGrid({ columns: intruderGridHeaders}, recordType + "_Grid");
                    PWM_VAR['intruderGrid'][recordType] = grid;

                    grid.on(".dgrid-row:click", function(evt){
                        PWM_ADMIN.detailView(evt, PWM_ADMIN.intruderHeaders(), grid);
                    });
                })(i)
            }

            PWM_ADMIN.refreshIntruderGrid();

        });
};

PWM_ADMIN.refreshIntruderGrid=function() {
    for (var i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
        var recordType = PWM_VAR['intruderRecordTypes'][i];
        PWM_VAR['intruderGrid'][recordType].refresh();
    }
    try {
        var maximum = PWM_MAIN.getObject('maxIntruderGridResults').value;
    } catch (e) {
        maximum = 1000;
    }
    var url = PWM_GLOBAL['url-restservice'] + "/app-data/intruder?maximum=" + maximum;
    var loadFunction = function(data) {
        for (var i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
            var recordType = PWM_VAR['intruderRecordTypes'][i];
            PWM_VAR['intruderGrid'][recordType].renderArray(data['data'][recordType]);
        }
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.auditUserHeaders = function() {
    return {
        "timestamp": PWM_ADMIN.showString('Field_Audit_Timestamp'),
        "perpetratorID": PWM_ADMIN.showString('Field_Audit_PerpetratorID'),
        "perpetratorDN": PWM_ADMIN.showString('Field_Audit_PerpetratorDN'),
        "perpetratorLdapProfile": PWM_ADMIN.showString('Field_Audit_PerpetratorLdapProfile'),
        "eventCode": PWM_ADMIN.showString('Field_Audit_EventCode'),
        "message": PWM_ADMIN.showString('Field_Audit_Message'),
        "sourceAddress": PWM_ADMIN.showString('Field_Audit_SourceAddress'),
        "sourceHost": PWM_ADMIN.showString('Field_Audit_SourceHost'),
        "guid": PWM_ADMIN.showString('Field_Audit_GUID')
    };
};

PWM_ADMIN.auditHelpdeskHeaders = function() {
    return {
        "timestamp": PWM_ADMIN.showString('Field_Audit_Timestamp'),
        "perpetratorID": PWM_ADMIN.showString('Field_Audit_PerpetratorID'),
        "perpetratorDN": PWM_ADMIN.showString('Field_Audit_PerpetratorDN'),
        "perpetratorLdapProfile": PWM_ADMIN.showString('Field_Audit_PerpetratorLdapProfile'),
        "eventCode": PWM_ADMIN.showString('Field_Audit_EventCode'),
        "message": PWM_ADMIN.showString('Field_Audit_Message'),
        "targetID": PWM_ADMIN.showString('Field_Audit_TargetID'),
        "targetDN": PWM_ADMIN.showString('Field_Audit_TargetDN'),
        "targetLdapProfile": PWM_ADMIN.showString('Field_Audit_TargetLdapProfile'),
        "sourceAddress": PWM_ADMIN.showString('Field_Audit_SourceAddress'),
        "sourceHost": PWM_ADMIN.showString('Field_Audit_SourceHost'),
        "guid": PWM_ADMIN.showString('Field_Audit_GUID')
    };
};

PWM_ADMIN.auditSystemHeaders = function() {
    return {
        "timestamp":PWM_ADMIN.showString('Field_Audit_Timestamp'),
        "eventCode":PWM_ADMIN.showString('Field_Audit_EventCode'),
        "message":PWM_ADMIN.showString('Field_Audit_Message'),
        "instance":PWM_ADMIN.showString('Field_Audit_Instance'),
        "guid":PWM_ADMIN.showString('Field_Audit_GUID')
    };
};

PWM_ADMIN.initAuditGrid=function() {
    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry ]);

            // Now, create an instance of our custom userGrid
            PWM_VAR['auditUserGrid'] = new CustomGrid({columns: PWM_ADMIN.auditUserHeaders()}, "auditUserGrid");
            PWM_VAR['auditSystemGrid'] = new CustomGrid({columns: PWM_ADMIN.auditSystemHeaders()}, "auditSystemGrid");
            PWM_VAR['auditHelpdeskGrid'] = new CustomGrid({columns: PWM_ADMIN.auditHelpdeskHeaders()}, "auditHelpdeskGrid");

            // unclick superfluous fields
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-perpetratorDN').click();
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-perpetratorLdapProfile').click();
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-message').click();
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-sourceHost').click();
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-guid').click();

            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-perpetratorDN').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-perpetratorLdapProfile').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-message').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-targetDN').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-targetLdapProfile').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-sourceHost').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-guid').click();

            PWM_MAIN.getObject('auditSystemGrid-hider-menu-check-instance').click();
            PWM_MAIN.getObject('auditSystemGrid-hider-menu-check-guid').click();
            PWM_ADMIN.refreshAuditGridData();

            PWM_VAR['auditUserGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.auditUserHeaders(), PWM_VAR['auditUserGrid']);
            });
            PWM_VAR['auditSystemGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.auditSystemHeaders(), PWM_VAR['auditSystemGrid']);
            });
            PWM_VAR['auditHelpdeskGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.auditHelpdeskHeaders(), PWM_VAR['auditHelpdeskGrid']);
            });
        });
};

PWM_ADMIN.refreshAuditGridData=function(maximum) {
    PWM_VAR['auditUserGrid'].refresh();
    PWM_VAR['auditHelpdeskGrid'].refresh();
    PWM_VAR['auditSystemGrid'].refresh();
    if (!maximum) {
        maximum = 1000;
    }
    var url = PWM_GLOBAL['url-restservice'] + "/app-data/audit?maximum=" + maximum;
    var loadFunction = function(data) {
        PWM_VAR['auditUserGrid'].renderArray(data['data']['user']);
        PWM_VAR['auditUserGrid'].set("sort", { attribute : 'timestamp', ascending: false, descending: true });
        PWM_VAR['auditUserGrid'].resize();

        PWM_VAR['auditHelpdeskGrid'].renderArray(data['data']['helpdesk']);
        PWM_VAR['auditHelpdeskGrid'].set("sort", { attribute : 'timestamp', ascending: false, descending: true });
        PWM_VAR['auditHelpdeskGrid'].resize();

        PWM_VAR['auditSystemGrid'].renderArray(data['data']['system']);
        PWM_VAR['auditSystemGrid'].set("sort", { attribute : 'timestamp', ascending: false, descending: true });
        PWM_VAR['auditSystemGrid'].resize();

    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.showStatChart = function(statName,days,divName,options) {
    options = options === undefined ? {} : options;
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
                                    registry.byId(loopEpsID).setAttribute('value','0');
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

    console.log('starting showPwmHealth: refreshTime=' + refreshTime);
    require(["dojo"],function(dojo){
        var parentDiv = dojo.byId(parentDivID);
        if (PWM_GLOBAL['inhibitHealthUpdate'] == true) {
            parentDiv.innerHTML = '';
            return;
        }

        if (PWM_GLOBAL['healthCheckInProgress']) {
            return;
        }

        PWM_GLOBAL['healthCheckInProgress'] = "true";

        if (refreshNow) {
            parentDiv.innerHTML = '<div class="WaitDialogBlank" style="margin-top: 20px; margin-bottom: 20px"/>';
            refreshUrl = PWM_MAIN.addParamToUrl(refreshUrl, 'refreshImmediate', 'true');
        }

        var loadFunction = function(data) {
            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
            } else {
                PWM_GLOBAL['pwm-health'] = data['data']['overall'];
                var htmlBody = PWM_ADMIN.makeHealthHtml(data['data'], showTimestamp, showRefresh);
                parentDiv.innerHTML = htmlBody;

                PWM_MAIN.addEventHandler('button-refreshHealth','click',function(){
                    PWM_ADMIN.showAppHealth(parentDivID, options, true);
                });

                PWM_GLOBAL['healthCheckInProgress'] = false;

                if (refreshTime > 0) {
                    setTimeout(function() {
                        PWM_ADMIN.showAppHealth(parentDivID, options);
                    }, refreshTime);
                }
                if (showTimestamp) {
                    PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('healthCheckTimestamp'));
                }
                if (finishFunction) {
                    finishFunction();
                }
            }
        };

        var errorFunction = function(error) {
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
        };

        PWM_MAIN.ajaxRequest(refreshUrl,loadFunction,{errorFunction:errorFunction,method:'GET'});
    });
};

PWM_ADMIN.makeHealthHtml = function(healthData, showTimestamp, showRefresh) {
    var healthRecords = healthData['records'];
    var htmlBody = '<table width="100%" style="width=100%; border=0">';
    for (var i = 0; i < healthRecords.length; i++) {
        (function(iter){
            var loopRecord = healthRecords[iter];
            htmlBody += '<tr><td class="key" style="width:1px; white-space:nowrap;"">';
            htmlBody += loopRecord['topic'];
            htmlBody += '</td><td class="health-' + loopRecord['status'] + '">';
            htmlBody += loopRecord['status'];
            htmlBody += '</td><td><div style="max-height: 200px; overflow: auto">';
            htmlBody += loopRecord['detail'];
            htmlBody += '</div></td></tr>';
        })(i)
    }
    if (showTimestamp || showRefresh) {
        htmlBody += '<tr><td colspan="3" style="text-align:center;">';
        if (showTimestamp) {
            htmlBody += '<span id="healthCheckTimestamp" class="timestamp">';
            htmlBody += (healthData['timestamp']);
            htmlBody += '</span>&nbsp;&nbsp;&nbsp;&nbsp;';
        }
        if (showRefresh) {
            htmlBody += '<span id="button-refreshHealth" class="fa btn-icon fa-refresh"></span>';
        }
        htmlBody += "</td></tr>";
    }

    htmlBody += '</table>';
    return htmlBody;
};

PWM_ADMIN.detailView = function(evt, headers, grid){
    var row = grid.row(evt);
    var text = '<table>';
    var postExecuteFunctions = [];
    for (var item in headers) {
        (function(key){
            var value = key in row.data ? row.data[key] : '';
            var label = headers[key];
            text += '<tr><td class="key">' + label + '</td>';
            text += '<td>';
            if (key.toLowerCase().indexOf('time') >= 0) {
                text += '<span class="timestamp" id="field-detail-' + key + '">' + value + '</span>';
                PWM_MAIN.TimestampHandler.testIfStringIsTimestamp(value,function(){
                    postExecuteFunctions.push(function() {
                        PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('field-detail-' + key));
                    });
                });
            } else if (key == 'message') {
                var out = value.replace('\n', '<br/>');
                text += '<div style="max-height: 200px; overflow: auto; max-width: 400px">' + out + '</div>'
            } else {
                text += value;
            }
            text += '</td></tr>';
        })(item);
    }
    text += '</table>';
    PWM_MAIN.showDialog({title:"Record Detail",text:text,showClose:true,allowMove:true,loadFunction:function(){
        for (i = 0; i < postExecuteFunctions.length; i++) {
            postExecuteFunctions[i]();
        }

    }});
};

PWM_ADMIN.showString=function (key, options) {
    options = options || {};
    options['bundle'] = 'Admin';
    return PWM_MAIN.showString(key,options);
};

PWM_ADMIN.initAdminPage=function(nextFunction) {
    if (nextFunction) nextFunction();
};

