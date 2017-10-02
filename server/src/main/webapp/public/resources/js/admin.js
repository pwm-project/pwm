/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

PWM_ADMIN.initAdminNavMenu = function() {
    var makeMenu = function(){

        require(["dijit/form/DropDownButton", "dijit/DropDownMenu", "dijit/Menu","dijit/MenuItem", "dijit/PopupMenuItem", "dojo/dom", "dijit/MenuSeparator"],
            function(DropDownButton, DropDownMenu, Menu, MenuItem, PopupMenuItem, dom, MenuSeparator){
                var pMenu = new DropDownMenu({ style: "display: none;"});
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_LogViewer'),
                    id: 'eventLog_dropitem',
                    onClick: function() {
                        PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/private/admin/logs');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_TokenLookup'),
                    id: 'tokenLookup_dropitem',
                    onClick: function() {
                        PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/private/admin/tokens');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_URLReference'),
                    id: 'urlReference_dropitem',
                    onClick: function() {
                        PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/private/admin/urls');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: 'User Debug',
                    id: 'userDebug_dropitem',
                    onClick: function() {
                        PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/private/admin/userdebug');
                    }
                }));
                pMenu.addChild(new MenuSeparator());
                pMenu.addChild(new MenuItem({
                    label: 'Full Page Health Status',
                    id: 'fullPageHealthStatus_dropitem',
                    onClick: function() {
                        PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/public/health.jsp');
                    }
                }));
                pMenu.addChild(new MenuSeparator());
                pMenu.addChild(new MenuItem({
                    label: '<span class="pwm-icon pwm-icon-external-link"></span> Application Reference',
                    id: 'applictionReference_dropitem',
                    onClick: function() {
                        PWM_MAIN.newWindowOpen(PWM_GLOBAL['url-context'] + '/public/reference/','referencedoc');
                    }
                }));
                if (PWM_GLOBAL['setting-displayEula'] === true) {
                    pMenu.addChild(new MenuItem({
                        label: 'View EULA',
                        id: 'viewEULA_dropitem',
                        onClick: function() {
                            PWM_MAIN.showEula(false,null);
                        }
                    }));
                }
                pMenu.addChild(new MenuSeparator());
                pMenu.addChild(new MenuItem({
                    label: 'Configuration Manager',
                    id: 'configurationManager_dropitem',
                    onClick: function() {
                        PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/private/config/manager');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: 'Configuration Editor',
                    id: 'configurationEditor_dropitem',
                    onClick: function() {
                        PWM_MAIN.goto(PWM_GLOBAL['url-context'] + '/private/config/editor');
                    }
                }));


                var dropDownButton = new DropDownButton({
                    label: "More Options",
                    name: "More Options",
                    dropDown: pMenu,
                    id: "progButton"
                });
                dom.byId("admin-nav-menu-container").appendChild(dropDownButton.domNode);
            });
    };

    PWM_MAIN.doIfQueryHasResults("#admin-nav-menu-container",makeMenu)
};

PWM_ADMIN.reportDataHeaders = function() {
    return {
        "username":PWM_ADMIN.showString("Field_Report_Username"),
        "userDN":PWM_ADMIN.showString("Field_Report_UserDN"),
        "ldapProfile":PWM_ADMIN.showString("Field_Report_LDAP_Profile"),
        "email":PWM_ADMIN.showString("Field_Report_Email"),
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
        "passwordStatusExpired":PWM_ADMIN.showString("Field_Report_PwdExpired"),
        "passwordStatusPreExpired":PWM_ADMIN.showString("Field_Report_PwdPreExpired"),
        "passwordStatusViolatesPolicy":PWM_ADMIN.showString("Field_Report_PwdViolatesPolicy"),
        "passwordStatusWarnPeriod":PWM_ADMIN.showString("Field_Report_PwdWarnPeriod"),
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
            PWM_MAIN.getObject('grid-hider-menu-check-email').click();
            PWM_MAIN.getObject('grid-hider-menu-check-userGUID').click();
            PWM_MAIN.getObject('grid-hider-menu-check-responseStorageMethod').click();
            PWM_MAIN.getObject('grid-hider-menu-check-responseFormatType').click();
            PWM_MAIN.getObject('grid-hider-menu-check-userDN').click();
            PWM_MAIN.getObject('grid-hider-menu-check-hasHelpdeskResponses').click();
            PWM_MAIN.getObject('grid-hider-menu-check-passwordStatusExpired').click();
            PWM_MAIN.getObject('grid-hider-menu-check-passwordStatusPreExpired').click();
            PWM_MAIN.getObject('grid-hider-menu-check-passwordStatusViolatesPolicy').click();
            PWM_MAIN.getObject('grid-hider-menu-check-passwordStatusWarnPeriod').click();

            PWM_VAR['reportGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.reportDataHeaders(), PWM_VAR['reportGrid']);
            });
        });
};

PWM_ADMIN.initDownloadUserReportCsvForm = function() {
    PWM_MAIN.doQuery("#downloadUserReportCsvForm", function(node){
        PWM_MAIN.addEventHandler(node, "click", function() {
            var selectedColumns = [];

            PWM_MAIN.doQuery("#grid-hider-menu input:checked",function(element){
                selectedColumns.push(element.id.replace('grid-hider-menu-check-', ''));
            });

            console.log("Selected columns: " + selectedColumns);
            downloadUserReportCsvForm.selectedColumns.value = selectedColumns;
        })
    });
};

PWM_ADMIN.refreshReportDataGrid=function() {
    if (PWM_MAIN.getObject('button-refreshReportDataGrid')) {
        PWM_MAIN.getObject('button-refreshReportDataGrid').disabled = true;
    }
    PWM_VAR['reportGrid'].refresh();
    var maximum = PWM_MAIN.getObject('maxReportDataResults').value;
    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','reportData');
    url =PWM_MAIN.addParamToUrl(url,"maximum",maximum);
    var loadFunction = function(data) {
        if (PWM_MAIN.getObject('button-refreshReportDataGrid')) {
            PWM_MAIN.getObject('button-refreshReportDataGrid').disabled = false;
        }
        if (data['error']) {
            PWM_MAIN.showErrorDialog(data);
            return;
        }

        var users = data['data']['users'];

        // "Flatten out" the nested properties, so they can be displayed in the grid
        for (var i = 0, len = users.length; i < len; i++) {
            var user = users[i];
            if (user.hasOwnProperty("passwordStatus")) {
                user["passwordStatusExpired"] = user["passwordStatus"]["expired"];
                user["passwordStatusPreExpired"] = user["passwordStatus"]["preExpired"];
                user["passwordStatusViolatesPolicy"] = user["passwordStatus"]["violatesPolicy"];
                user["passwordStatusWarnPeriod"] = user["passwordStatus"]["warnPeriod"];
            }
        }

        PWM_VAR['reportGrid'].renderArray(users);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};


PWM_ADMIN.refreshReportDataStatus=function() {
    var url = PWM_GLOBAL['url-context'] + "/private/admin";
    url = PWM_MAIN.addParamToUrl(url, 'processAction','reportStatus');
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

        var availableCommands = data['data']['availableCommands'];
        PWM_MAIN.getObject("reportStartButton").disabled = !PWM_MAIN.JSLibrary.arrayContains(availableCommands,'Start');
        PWM_MAIN.getObject("reportStopButton").disabled = !PWM_MAIN.JSLibrary.arrayContains(availableCommands,'Stop');
        PWM_MAIN.getObject("reportClearButton").disabled = !PWM_MAIN.JSLibrary.arrayContains(availableCommands,'Clear');
    };
    var errorFunction = function (error) {
        console.log('error during report status update: ' + error);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET',errorFunction:errorFunction});
};

PWM_ADMIN.refreshReportDataSummary=function() {
    var url = PWM_GLOBAL['url-context'] + "/private/admin";
    url = PWM_MAIN.addParamToUrl(url, 'processAction','reportSummary');

    var loadFunction = function(data) {
        if (data['data'] && data['data']['presentable']) {
            var htmlTable = '';
            for (var item in data['data']['presentable']) {
                var rowData = data['data']['presentable'][item];
                htmlTable += '<tr><td>' + rowData['label'] + '</td><td>' + rowData['count'] + '</td><td>' + (rowData['pct'] ? rowData['pct'] : '') + '</td></tr>';
            }
            PWM_MAIN.getObject('summaryTable').innerHTML = htmlTable;
        }
    };
    var errorFunction = function (error) {
        console.log('error during report status update: ' + error);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET',errorFunction:errorFunction});
};

PWM_ADMIN.reportAction=function(action) {
    var confirmText, actionText;
    if (!action) {
        return;
    }
    confirmText = PWM_ADMIN.showString('Confirm_Report_' + action);
    actionText = PWM_ADMIN.showString('Display_Report_Action_' + action);
    PWM_MAIN.showConfirmDialog({text:confirmText,okAction:function(){
        PWM_MAIN.showWaitDialog({title:PWM_MAIN.showString('Display_PleaseWait'),text:actionText,loadFunction:function(){
            var url = PWM_GLOBAL['url-context'] + "/private/admin";
            url = PWM_MAIN.addParamToUrl(url, 'processAction','reportCommand');
            url = PWM_MAIN.addParamToUrl(url, 'command',action);
            PWM_MAIN.ajaxRequest(url,function(){
                setTimeout(function(){
                    PWM_ADMIN.refreshReportDataStatus();
                    PWM_ADMIN.refreshReportDataSummary();
                    PWM_MAIN.closeWaitDialog();
                },7500);
            });
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
    var grid = PWM_VAR['activeSessionsGrid'];
    grid.refresh();

    var maximum = PWM_MAIN.getObject('maxActiveSessionResults').value;
    var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "sessionData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
    var loadFunction = function(data) {
        grid.renderArray(data['data']);
        grid.set("sort", { attribute : 'createTime', ascending: false, descending: true });
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
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
    var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "intruderData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
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
        "guid": PWM_ADMIN.showString('Field_Audit_GUID'),
        "narrative": PWM_ADMIN.showString('Field_Audit_Narrative')
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
        "guid": PWM_ADMIN.showString('Field_Audit_GUID'),
        "narrative": PWM_ADMIN.showString('Field_Audit_Narrative')
    };
};

PWM_ADMIN.auditSystemHeaders = function() {
    return {
        "timestamp":PWM_ADMIN.showString('Field_Audit_Timestamp'),
        "eventCode":PWM_ADMIN.showString('Field_Audit_EventCode'),
        "message":PWM_ADMIN.showString('Field_Audit_Message'),
        "instance":PWM_ADMIN.showString('Field_Audit_Instance'),
        "guid":PWM_ADMIN.showString('Field_Audit_GUID'),
        "narrative":PWM_ADMIN.showString('Field_Audit_Narrative')
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
            PWM_MAIN.getObject('auditUserGrid-hider-menu-check-narrative').click();

            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-perpetratorDN').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-perpetratorLdapProfile').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-message').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-targetDN').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-targetLdapProfile').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-sourceHost').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-guid').click();
            PWM_MAIN.getObject('auditHelpdeskGrid-hider-menu-check-narrative').click();

            PWM_MAIN.getObject('auditSystemGrid-hider-menu-check-instance').click();
            PWM_MAIN.getObject('auditSystemGrid-hider-menu-check-guid').click();
            PWM_MAIN.getObject('auditSystemGrid-hider-menu-check-narrative').click();
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
    var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "auditData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
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
            var statsGetUrl = PWM_MAIN.addParamToUrl(window.location.href,"processAction","statistics");
            statsGetUrl = PWM_MAIN.addParamToUrl(statsGetUrl, "statName", statName);
            statsGetUrl = PWM_MAIN.addParamToUrl(statsGetUrl, "days", days);

            var errorFunction = function() {
                for (var loopEpsTypeIndex = 0; loopEpsTypeIndex < epsTypes.length; loopEpsTypeIndex++) { // clear all the gauges
                    var loopEpsName = epsTypes[loopEpsTypeIndex] + '';
                    for (var loopEpsDurationsIndex = 0; loopEpsDurationsIndex < epsDurations.length; loopEpsDurationsIndex++) { // clear all the gauges
                        var loopEpsDuration = epsDurations[loopEpsDurationsIndex] + '';
                        var loopEpsID = "EPS-GAUGE-" + loopEpsName + "_" + loopEpsDuration;
                        if (PWM_MAIN.getObject(loopEpsID) !== null) {
                            if (registry.byId(loopEpsID)) {
                                registry.byId(loopEpsID).setAttribute('value','0');
                            }
                        }
                    }
                }
                doRefresh();
            };

            var loadFunction = function(data) {
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
                            if (loopEpsDuration === "HOURLY") {
                                activityCount += loopEpsValue;
                            }
                            if (PWM_MAIN.getObject(loopFieldEpsID) !== null) {
                                PWM_MAIN.getObject(loopFieldEpsID).innerHTML = loopEpmValue;
                            }
                            if (PWM_MAIN.getObject(loopEpsID) !== null) {
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
                if (divName !== null && PWM_MAIN.getObject(divName)) { // stats chart
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
            };

            PWM_MAIN.ajaxRequest(statsGetUrl, loadFunction, {errorFunction:errorFunction,method:'GET'});
        });
};

PWM_ADMIN.showAppHealth = function(parentDivID, options, refreshNow) {

    var inputOpts = options || PWM_GLOBAL['showPwmHealthOptions'] || {};
    PWM_GLOBAL['showPwmHealthOptions'] = options;
    var refreshUrl = inputOpts['sourceUrl'] || PWM_GLOBAL['url-context'] + "/public/api?processAction=health";
    var showRefresh = inputOpts['showRefresh'];
    var showTimestamp = inputOpts['showTimestamp'];
    var refreshTime = inputOpts['refreshTime'] || 60 * 1000;
    var finishFunction = inputOpts['finishFunction'];

    console.log('starting showPwmHealth: refreshTime=' + refreshTime);
    require(["dojo"],function(dojo){
        var parentDiv = dojo.byId(parentDivID);
        if (PWM_GLOBAL['inhibitHealthUpdate'] === true) {
            try { parentDiv.innerHTML = ''; } catch (e) { console.log('unable to update health div' + e) };
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
                PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('healthCheckTimestamp'));

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
            if (error !== null) {
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
    var htmlBody = '<div>';
    htmlBody += '<div class="healthTable-wrapper"><table>';
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
    htmlBody += '</table></div>';

    if (showTimestamp || showRefresh) {
        htmlBody += '<div class="healthTable-footer">';
        if (showTimestamp) {
            htmlBody += 'Last Updated <span id="healthCheckTimestamp" class="timestamp">';
            htmlBody += (healthData['timestamp']);
            htmlBody += '</span>';
        }
        if (showRefresh) {
            // htmlBody += '&nbsp;&nbsp;&nbsp;&nbsp;<span id="button-refreshHealth" class="pwm-icon btn-icon pwm-icon-refresh" title="Refresh"></span>';
        }
        htmlBody += "</div>";
    }
    htmlBody += '</div>';

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
            var id = "record-detail-" + key;
            text += '<tr><td class="key">' + label + '</td>';
            text += '<td><span id="' + id + '" style="max-height: 200px; overflow: auto; max-width: 400px" class="timestamp">';
            if (key.toLowerCase().indexOf('time') >= 0) {
                PWM_MAIN.TimestampHandler.testIfStringIsTimestamp(value,function(){
                    postExecuteFunctions.push(function() {
                        PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject(id));
                    });
                });
            }
            text += '</span></td></tr>';
            postExecuteFunctions.push(function(){
                PWM_MAIN.getObject(id).appendChild(document.createTextNode(value));
            });
        })(item);
    }
    text += '</table>';
    PWM_MAIN.showDialog({title:"Record Detail",text:text,showClose:true,allowMove:true,loadFunction:function(){
        for (var i = 0; i < postExecuteFunctions.length; i++) {
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

