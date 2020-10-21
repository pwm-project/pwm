/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/logs');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_TokenLookup'),
                    id: 'tokenLookup_dropitem',
                    onClick: function() {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/tokens');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_URLReference'),
                    id: 'urlReference_dropitem',
                    onClick: function() {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/urls');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: 'User Debug',
                    id: 'userDebug_dropitem',
                    onClick: function() {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/userdebug');
                    }
                }));
                pMenu.addChild(new MenuSeparator());
                pMenu.addChild(new MenuItem({
                    label: 'Full Page Health Status',
                    id: 'fullPageHealthStatus_dropitem',
                    onClick: function() {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/public/health');
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
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/config/manager');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: 'Configuration Editor',
                    id: 'configurationEditor_dropitem',
                    onClick: function() {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/config/editor');
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
    return [
        {field:"username",label:PWM_ADMIN.showString("Field_Report_Username")},
        {field:"userDN",label:PWM_ADMIN.showString("Field_Report_UserDN"),hidden:true},
        {field:"ldapProfile",label:PWM_ADMIN.showString("Field_Report_LDAP_Profile"),hidden:true},
        {field:"email",label:PWM_ADMIN.showString("Field_Report_Email"),hidden:true},
        {field:"userGUID",label:PWM_ADMIN.showString("Field_Report_UserGuid"),hidden:true},
        {field:"accountExpirationTime",label:PWM_ADMIN.showString("Field_Report_AccountExpireTime")},
        {field:"passwordExpirationTime",label:PWM_ADMIN.showString("Field_Report_PwdExpireTime")},
        {field:"passwordChangeTime",label:PWM_ADMIN.showString("Field_Report_PwdChangeTime")},
        {field:"responseSetTime",label:PWM_ADMIN.showString("Field_Report_ResponseSaveTime")},
        {field:"lastLoginTime",label:PWM_ADMIN.showString("Field_Report_LastLogin")},
        {field:"hasResponses",label:PWM_ADMIN.showString("Field_Report_HasResponses")},
        {field:"hasHelpdeskResponses",label:PWM_ADMIN.showString("Field_Report_HasHelpdeskResponses"),hidden:true},
        {field:"responseStorageMethod",label:PWM_ADMIN.showString("Field_Report_ResponseStorageMethod"),hidden:true},
        {field:"responseFormatType",label:PWM_ADMIN.showString("Field_Report_ResponseFormatType"),hidden:true},
        {field:"passwordStatusExpired",label:PWM_ADMIN.showString("Field_Report_PwdExpired"),hidden:true},
        {field:"passwordStatusPreExpired",label:PWM_ADMIN.showString("Field_Report_PwdPreExpired"),hidden:true},
        {field:"passwordStatusViolatesPolicy",label:PWM_ADMIN.showString("Field_Report_PwdViolatesPolicy"),hidden:true},
        {field:"passwordStatusWarnPeriod",label:PWM_ADMIN.showString("Field_Report_PwdWarnPeriod"),hidden:true},
        {field:"requiresPasswordUpdate",label:PWM_ADMIN.showString("Field_Report_RequiresPasswordUpdate")},
        {field:"requiresResponseUpdate",label:PWM_ADMIN.showString("Field_Report_RequiresResponseUpdate")},
        {field:"requiresProfileUpdate",label:PWM_ADMIN.showString("Field_Report_RequiresProfileUpdate")},
        {field:"cacheTimestamp",label:PWM_ADMIN.showString("Field_Report_RecordCacheTime"),hidden:true}
    ];
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
            var htmlTable = UILibrary.displayElementsToTableContents(fields);
            PWM_MAIN.getObject('statusTable').innerHTML = htmlTable;
            UILibrary.initElementsToTableContents(fields);
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
    return [
        {field:"userID",label:PWM_ADMIN.showString('Field_Session_UserID')},
        {field:"ldapProfile",label:PWM_ADMIN.showString('Field_Session_LdapProfile')},
        {field:"userDN",label:PWM_ADMIN.showString('Field_Session_UserDN'),hidden:true},
        {field:"createTime",label:PWM_ADMIN.showString('Field_Session_CreateTime')},
        {field:"lastTime",label:PWM_ADMIN.showString('Field_Session_LastTime')},
        {field:"label",label:PWM_ADMIN.showString('Field_Session_Label')},
        {field:"idle",label:PWM_ADMIN.showString('Field_Session_Idle')},
        {field:"locale",label:PWM_ADMIN.showString('Field_Session_Locale'),hidden:true},
        {field:"srcAddress",label:PWM_ADMIN.showString('Field_Session_SrcAddress')},
        {field:"srcHost",label:PWM_ADMIN.showString('Field_Session_SrcHost'),hidden:true},
        {field:"lastUrl",label:PWM_ADMIN.showString('Field_Session_LastURL'),hidden:true},
        {field:"intruderAttempts",label:PWM_ADMIN.showString('Field_Session_IntruderAttempts'),hidden:true}
    ];
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
    return [
        {field:"subject",label:PWM_ADMIN.showString('Field_Intruder_Subject')},
        {field:"timestamp",label:PWM_ADMIN.showString('Field_Intruder_Timestamp')},
        {field:"count",label:PWM_ADMIN.showString('Field_Intruder_Count')},
        {field:"status",label:PWM_ADMIN.showString('Field_Intruder_Status')}
    ];
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
    return [
        {field:"timestamp",label:PWM_ADMIN.showString('Field_Audit_Timestamp')},
        {field:"perpetratorID",label:PWM_ADMIN.showString('Field_Audit_PerpetratorID')},
        {field:"perpetratorDN",label:PWM_ADMIN.showString('Field_Audit_PerpetratorDN'),hidden:true},
        {field:"perpetratorLdapProfile",label:PWM_ADMIN.showString('Field_Audit_PerpetratorLdapProfile'),hidden:true},
        {field:"eventCode",label:PWM_ADMIN.showString('Field_Audit_EventCode')},
        {field:"message",label:PWM_ADMIN.showString('Field_Audit_Message'),hidden:true},
        {field:"sourceAddress",label:PWM_ADMIN.showString('Field_Audit_SourceAddress')},
        {field:"sourceHost",label:PWM_ADMIN.showString('Field_Audit_SourceHost'),hidden:true},
        {field:"guid",label:PWM_ADMIN.showString('Field_Audit_GUID'),hidden:true},
        {field:"narrative",label:PWM_ADMIN.showString('Field_Audit_Narrative')}
    ];
};

PWM_ADMIN.auditHelpdeskHeaders = function() {
    return [
        {field:"timestamp",label:PWM_ADMIN.showString('Field_Audit_Timestamp')},
        {field:"perpetratorID",label:PWM_ADMIN.showString('Field_Audit_PerpetratorID')},
        {field:"perpetratorDN",label:PWM_ADMIN.showString('Field_Audit_PerpetratorDN'),hidden:true},
        {field:"perpetratorLdapProfile",label:PWM_ADMIN.showString('Field_Audit_PerpetratorLdapProfile'),hidden:true},
        {field:"eventCode",label:PWM_ADMIN.showString('Field_Audit_EventCode')},
        {field:"message",label:PWM_ADMIN.showString('Field_Audit_Message'),hidden:true},
        {field:"targetID",label:PWM_ADMIN.showString('Field_Audit_TargetID')},
        {field:"targetDN",label:PWM_ADMIN.showString('Field_Audit_TargetDN')},
        {field:"targetLdapProfile",label:PWM_ADMIN.showString('Field_Audit_TargetLdapProfile')},
        {field:"sourceAddress",label:PWM_ADMIN.showString('Field_Audit_SourceAddress')},
        {field:"sourceHost",label:PWM_ADMIN.showString('Field_Audit_SourceHost'),hidden:true},
        {field:"guid",label:PWM_ADMIN.showString('Field_Audit_GUID'),hidden:true},
        {field:"narrative",label:PWM_ADMIN.showString('Field_Audit_Narrative'),hidden:true}
    ];
};

PWM_ADMIN.auditSystemHeaders = function() {
    return [
        {field:"timestamp",label:PWM_ADMIN.showString('Field_Audit_Timestamp')},
        {field:"eventCode",label:PWM_ADMIN.showString('Field_Audit_EventCode')},
        {field:"message",label:PWM_ADMIN.showString('Field_Audit_Message')},
        {field:"instance",label:PWM_ADMIN.showString('Field_Audit_Instance'),hidden:true},
        {field:"guid",label:PWM_ADMIN.showString('Field_Audit_GUID'),hidden:true},
        {field:"narrative",label:PWM_ADMIN.showString('Field_Audit_Narrative'),hidden:true}
    ];
};

PWM_ADMIN.logHeaders = function() {
    return [
        {field:"d",label:PWM_ADMIN.showString('Field_Logs_Timestamp')},
        {field:"l",label:PWM_ADMIN.showString('Field_Logs_Level')},
        {field:"s",label:PWM_ADMIN.showString('Field_Logs_Source'),hidden:true},
        {field:"b",label:PWM_ADMIN.showString('Field_Logs_Label')},
        {field:"a",label:PWM_ADMIN.showString('Field_Logs_User'),hidden:true},
        {field:"t",label:PWM_ADMIN.showString('Field_Logs_Component'),hidden:true},
        {field:"m",label:PWM_ADMIN.showString('Field_Logs_Detail')},
        {field:"e",label:PWM_ADMIN.showString('Field_Logs_Error'),hidden:true}
    ];
};

PWM_ADMIN.initLogGrid=function() {
    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            // Create a new constructor by mixing in the components
            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry ]);

            // Now, create an instance of our custom userGrid
            PWM_VAR['logViewerGrid'] = new CustomGrid({columns: PWM_ADMIN.logHeaders()}, "logViewerGrid");
            PWM_VAR['logViewerGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.logHeaders(), PWM_VAR['logViewerGrid']);
            });
        }
    );

    var saveSettings = function() {
        var logSettings = PWM_ADMIN.readLogFormData();
        PWM_MAIN.Preferences.writeSessionStorage('logSettings',logSettings);
    };

    PWM_MAIN.addEventHandler('form-loadLog','change', saveSettings);
    PWM_MAIN.addEventHandler('form-downloadLog','change', saveSettings);

    var loadSettings = function () {
        var settings = PWM_MAIN.Preferences.readSessionStorage('logSettings');
        if (settings) {
            PWM_MAIN.getObject('username').value = settings['username'];
            PWM_MAIN.getObject('text').value = settings['text'];
            PWM_MAIN.getObject('count').value = settings['count'];
            PWM_MAIN.getObject('maxTime').value = settings['maxTime'];
            PWM_MAIN.JSLibrary.setValueOfSelectElement('type',settings['type']);
            PWM_MAIN.JSLibrary.setValueOfSelectElement('level',settings['level']);
            PWM_MAIN.JSLibrary.setValueOfSelectElement('displayType', settings['displayType']);
            if (PWM_MAIN.getObject('form-downloadLog')) {
                PWM_MAIN.JSLibrary.setValueOfSelectElement('downloadType', settings['downloadType']);
                PWM_MAIN.JSLibrary.setValueOfSelectElement('compressionType', settings['compressionType']);
            }
        }
    };
    loadSettings();
};

PWM_ADMIN.readLogFormData = function() {
    var settings = {};
    settings['username'] = PWM_MAIN.getObject('username').value;
    settings['text'] = PWM_MAIN.getObject('text').value;
    settings['count'] = PWM_MAIN.getObject('count').value;
    settings['maxTime'] = PWM_MAIN.getObject('maxTime').value;
    settings['type'] = PWM_MAIN.JSLibrary.readValueOfSelectElement('type');
    settings['level'] = PWM_MAIN.JSLibrary.readValueOfSelectElement('level');
    settings['displayType'] = PWM_MAIN.JSLibrary.readValueOfSelectElement('displayType');
    if (PWM_MAIN.getObject('form-downloadLog')) {
        settings['downloadType'] = PWM_MAIN.JSLibrary.readValueOfSelectElement('downloadType');
        settings['compressionType'] = PWM_MAIN.JSLibrary.readValueOfSelectElement('compressionType');
    }
    return settings;
};

PWM_ADMIN.refreshLogData = function() {
    PWM_MAIN.getObject('button-search').disabled = true;
    var logSettings = PWM_ADMIN.readLogFormData();

    var processFunction = function(data) {
        console.time('someFunction');

        var records = data['data']['records'];
        if (PWM_MAIN.JSLibrary.isEmpty(records)) {
            PWM_MAIN.removeCssClass('div-noResultsMessage', 'hidden');
            PWM_MAIN.addCssClass('wrapper-logViewerGrid', 'hidden');
            PWM_MAIN.addCssClass('wrapper-lineViewer', 'hidden');

        } else {
            if (data['data']['display'] === 'grid') {
                PWM_MAIN.addCssClass('div-noResultsMessage', 'hidden');
                PWM_MAIN.removeCssClass('wrapper-logViewerGrid', 'hidden');
                PWM_MAIN.addCssClass('wrapper-lineViewer', 'hidden');
                var grid = PWM_VAR['logViewerGrid'];
                grid.refresh();
                grid.renderArray(records);
                grid.set("timestamp", { attribute : 'createTime', ascending: false, descending: true });
            } else {
                PWM_MAIN.addCssClass('div-noResultsMessage', 'hidden');
                PWM_MAIN.addCssClass('wrapper-logViewerGrid', 'hidden');
                PWM_MAIN.removeCssClass('wrapper-lineViewer', 'hidden');
                var textOutput = '';

                for (var iterator in records) {
                    (function(record) {
                        textOutput += records[record];
                        textOutput += "\n";
                    }(iterator));
                }
                PWM_MAIN.getObject('lineViewer').textContent = textOutput;
            }
        }
        console.timeEnd('someFunction');

        PWM_MAIN.getObject('button-search').disabled = false;
        PWM_MAIN.closeWaitDialog();
    };

    var url = PWM_MAIN.addParamToUrl(PWM_GLOBAL['url-context'] + '/private/admin',  'processAction', 'readLogData');
    var options = {};
    options.content = logSettings;

    PWM_MAIN.showWaitDialog({loadFunction:function(){
            PWM_MAIN.ajaxRequest(url,processFunction,options);
        }
    });
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

            PWM_ADMIN.refreshAuditGridData(undefined,'USER');
            PWM_ADMIN.refreshAuditGridData(undefined,'HELPDESK');
            PWM_ADMIN.refreshAuditGridData(undefined,'SYSTEM');

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

PWM_ADMIN.refreshAuditGridData=function(maximum,type) {
    switch (type) {
        case 'USER':
            var grid = PWM_VAR['auditUserGrid'];
            break;

        case 'HELPDESK':
            var grid = PWM_VAR['auditHelpdeskGrid'];
            break;

        case 'SYSTEM':
            var grid = PWM_VAR['auditSystemGrid'];
            break;
    }

    grid.refresh();

    if (!maximum) {
        maximum = 100;
    }

    var url = PWM_MAIN.addParamToUrl(window.location.href, "processAction", "auditData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
    url = PWM_MAIN.addParamToUrl(url,'type',type);
    var loadFunction = function(data) {
        grid.renderArray(data['data']['records']);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.showStatChart = function(statName,days,divName,options) {
    options = options === undefined ? {} : options;
    var doRefresh = options['refreshTime']
        ? function(){setTimeout(function(){PWM_ADMIN.showStatChart(statName,days,divName,options);},options['refreshTime']);}
        : function(){};
    var statsGetUrl = PWM_MAIN.addParamToUrl( PWM_GLOBAL['url-context'] + '/public/api',"processAction","statistics");
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

PWM_ADMIN.initPwNotifyPage = function() {
    PWM_MAIN.addEventHandler('button-executePwNotifyJob','click',function(){
        PWM_MAIN.showWaitDialog({loadFunction:function(){
                var url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','startPwNotifyJob');
                PWM_MAIN.ajaxRequest(url,function(data){
                    setTimeout(function(){
                        PWM_MAIN.showDialog({title:'Job Started',text:data['successMessage'],okAction:function(){
                                PWM_ADMIN.loadPwNotifyStatus();
                            }
                        });
                    },3000);
                });
            }
        });
    });

    PWM_MAIN.addEventHandler('button-refreshPwNotifyStatus','click',function(){
        PWM_MAIN.showWaitDialog({loadFunction:function() {
                PWM_MAIN.getObject('button-refreshPwNotifyStatus').disabled = true;
                PWM_ADMIN.loadPwNotifyStatus();
                PWM_ADMIN.loadPwNotifyLog();
                setTimeout(function () {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.getObject('button-refreshPwNotifyStatus').disabled = false;
                },500);
            }
        });
    });

    PWM_ADMIN.loadPwNotifyStatus();
    setTimeout(function(){
        PWM_ADMIN.loadPwNotifyStatus();
    },5000);

    PWM_ADMIN.loadPwNotifyLog();
};

PWM_ADMIN.loadPwNotifyStatus = function () {
    var processData = function (data) {
        var statusData = data['data']['statusData'];
        var htmlData = '<tr><td colspan="2" class="title">Password Expiration Notification Job Status</td></tr>';
        for (var item in statusData) {
            (function(key){
                var item = statusData[key];
                htmlData += '<tr><td>' + item['label'] + '</td><td>';
                if ( item['type'] === 'timestamp') {
                    htmlData += '<span id="pwNotifyStatusRow-' + key + '" class="timestamp">' + item['value'] + '</span>';
                } else {
                    htmlData += item['value'];
                }
                htmlData += '</td></tr>';
            })(item);
        }

        PWM_MAIN.getObject('table-pwNotifyStatus').innerHTML = htmlData;

        for (var item in statusData) {
            (function(key){
                var item = statusData[key];
                if ( item['type'] === 'timestamp') {
                    PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('pwNotifyStatusRow-' + key));
                }
            })(item);
        }

        PWM_MAIN.getObject('button-executePwNotifyJob').disabled = !data['data']['enableStartButton'];
    };
    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','readPwNotifyStatus');
    PWM_MAIN.ajaxRequest(url, processData);

};

PWM_ADMIN.loadPwNotifyLog = function () {
    var processData = function (data) {
        var debugData = data['data'];
        if (debugData && debugData.length > 0) {
            PWM_MAIN.getObject('div-pwNotifyDebugLog').innerHTML = '';
            PWM_MAIN.getObject('div-pwNotifyDebugLog').appendChild(document.createTextNode(debugData));
        } else {
            PWM_MAIN.getObject('div-pwNotifyDebugLog').innerHTML = '<span class="footnote">Job has not been run on this server since startup.</span>';
        }
    };
    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','readPwNotifyLog');
    PWM_MAIN.ajaxRequest(url, processData);

};

PWM_ADMIN.detailView = function(evt, headers, grid){
    var row = grid.row(evt);
    var text = '<table>';
    var postExecuteFunctions = [];
    for (var item in headers) {
        (function(key){
            var field = headers[key]['field'];
            var label = headers[key]['label'];
            var value = field in row.data ? row.data[field] : '';
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

