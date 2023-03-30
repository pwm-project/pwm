/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
    const makeMenu = function () {

        require(["dijit/form/DropDownButton", "dijit/DropDownMenu", "dijit/Menu", "dijit/MenuItem", "dijit/PopupMenuItem", "dojo/dom", "dijit/MenuSeparator"],
            function (DropDownButton, DropDownMenu, Menu, MenuItem, PopupMenuItem, dom, MenuSeparator) {
                const pMenu = new DropDownMenu({style: "display: none;"});
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_LogViewer'),
                    id: 'eventLog_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/logs');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_TokenLookup'),
                    id: 'tokenLookup_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/tokens');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.showString('Title_URLReference'),
                    id: 'urlReference_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/urls');
                    }
                }));
                pMenu.addChild(new MenuSeparator());
                pMenu.addChild(new MenuItem({
                    label: 'Full Page Health Status',
                    id: 'fullPageHealthStatus_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/public/health');
                    }
                }));
                pMenu.addChild(new MenuSeparator());
                pMenu.addChild(new MenuItem({
                    label: '<span class="pwm-icon pwm-icon-external-link"></span> Application Reference',
                    id: 'applictionReference_dropitem',
                    onClick: function () {
                        PWM_MAIN.newWindowOpen(PWM_GLOBAL['url-context'] + '/public/reference/', 'referencedoc');
                    }
                }));
                if (PWM_GLOBAL['setting-displayEula'] === true) {
                    pMenu.addChild(new MenuItem({
                        label: 'View EULA',
                        id: 'viewEULA_dropitem',
                        onClick: function () {
                            PWM_MAIN.showEula(false, null);
                        }
                    }));
                }
                pMenu.addChild(new MenuSeparator());
                pMenu.addChild(new MenuItem({
                    label: 'Configuration Manager',
                    id: 'configurationManager_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/config/manager');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: 'Configuration Editor',
                    id: 'configurationEditor_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/config/editor');
                    }
                }));


                const dropDownButton = new DropDownButton({
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


PWM_ADMIN.initDownloadProcessReportZipForm = function() {
    PWM_MAIN.doQuery("#reportDownloadButton", function(node){
        PWM_MAIN.addEventHandler(node, "click", function() {
            PWM_MAIN.showConfirmDialog({title:"Report Status",text:PWM_ADMIN.showString('Confirm_Report_Start'),okAction:function(){
                    let url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','downloadReportZip');
                    url = PWM_MAIN.addParamToUrl(url,'recordCount',PWM_MAIN.getObject('recordCount').value);
                    url = PWM_MAIN.addParamToUrl(url,'recordType',PWM_MAIN.JSLibrary.readValueOfSelectElement('recordType'));
                    window.location.href = url;
                }});
        })
    });
    PWM_MAIN.doQuery("#reportCancelButton", function(node){
        PWM_MAIN.addEventHandler(node, "click", function() {
            const url = PWM_MAIN.addParamToUrl(window.location.href, 'processAction', 'cancelDownload');
            PWM_MAIN.ajaxRequest(url, function(){
                PWM_MAIN.showDialog({title:"Report Status",text:"Download Cancelled"})
            });
        })
    });
};

PWM_ADMIN.refreshReportProcessStatus=function() {
    const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','reportProcessStatus');
    const loadFunction = function(data) {
        if (data['data'] && data['data']['presentable']) {
            const fields = data['data']['presentable'];
            PWM_MAIN.getObject('statusTable').innerHTML = UILibrary.displayElementsToTableContents(fields);
            UILibrary.initElementsToTableContents(fields);
        }

        const reportInProgress = data['data']['reportInProgress'] === true;
        PWM_MAIN.getObject("reportDownloadButton").disabled = reportInProgress;
        PWM_MAIN.getObject("reportCancelButton").disabled = !reportInProgress;
        PWM_MAIN.getObject( "downloadReportOptionsFieldset" ).disabled = reportInProgress;

        if ( reportInProgress === PWM_MAIN.IdleTimeoutHandler.countDownTimerEnabled() ) {
            if (reportInProgress) {
                PWM_MAIN.IdleTimeoutHandler.cancelCountDownTimer()
            } else {
                PWM_MAIN.IdleTimeoutHandler.resumeCountDownTimer()
            }
        }
    };
    const errorFunction = function (error) {
        console.log('error during report status update: ' + error);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction});
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
    const headers = PWM_ADMIN.webSessionHeaders();

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            const columnHeaders = headers;

            // Create a new constructor by mixing in the components
            const CustomGrid = declare([Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry]);

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
    const grid = PWM_VAR['activeSessionsGrid'];
    grid.refresh();

    const maximum = PWM_MAIN.getObject('maxActiveSessionResults').value;
    let url = PWM_MAIN.addParamToUrl(window.location.href, "processAction", "sessionData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
    const loadFunction = function (data) {
        grid.renderArray(data['data']);
        grid.set("sort", {attribute: 'createTime', ascending: false, descending: true});
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.intruderHeaders = function(){
    return [
        {field:"domainID",label:PWM_ADMIN.showString('Field_Intruder_Domain')},
        {field:"subject",label:PWM_ADMIN.showString('Field_Intruder_Subject')},
        {field:"timeStamp",label:PWM_ADMIN.showString('Field_Intruder_Timestamp')},
        {field:"attemptCount",label:PWM_ADMIN.showString('Field_Intruder_Count')},
        {field:"status",label:PWM_ADMIN.showString('Field_Intruder_Status')}
    ];
};


PWM_ADMIN.initIntrudersGrid=function() {
    PWM_VAR['intruderRecordTypes'] = ["ADDRESS","USERNAME","USER_ID","ATTRIBUTE","TOKEN_DEST"];
    const intruderGridHeaders = PWM_ADMIN.intruderHeaders();

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            // Create a new constructor by mixing in the components
            const CustomGrid = declare([Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry]);

            // Now, create an instance of our custom grid
            PWM_VAR['intruderGrid'] = {};
            for (let i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
                (function(iter){
                    const recordType = PWM_VAR['intruderRecordTypes'][iter];
                    const grid = new CustomGrid({columns: intruderGridHeaders}, recordType + "_Grid");
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
    for (let i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
        const recordType = PWM_VAR['intruderRecordTypes'][i];
        PWM_VAR['intruderGrid'][recordType].refresh();
    }
    try {
        var maximum = PWM_MAIN.getObject('maxIntruderGridResults').value;
    } catch (e) {
        maximum = 1000;
    }
    let url = PWM_MAIN.addParamToUrl(window.location.href, "processAction", "intruderData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
    const loadFunction = function (data) {
        for (let i = 0; i < PWM_VAR['intruderRecordTypes'].length; i++) {
            const recordType = PWM_VAR['intruderRecordTypes'][i];
            PWM_VAR['intruderGrid'][recordType].renderArray(data['data'][recordType]);
        }
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.auditUserHeaders = function() {
    return [
        {field:"domain",label:PWM_ADMIN.showString('Field_Audit_Domain')},
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
        {field:"domain",label:PWM_ADMIN.showString('Field_Audit_Domain')},
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
            const CustomGrid = declare([Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry]);

            // Now, create an instance of our custom userGrid
            PWM_VAR['logViewerGrid'] = new CustomGrid({columns: PWM_ADMIN.logHeaders()}, "logViewerGrid");
            PWM_VAR['logViewerGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, PWM_ADMIN.logHeaders(), PWM_VAR['logViewerGrid']);
            });
        }
    );

    const saveSettings = function () {
        const logSettings = PWM_ADMIN.readLogFormData();
        PWM_MAIN.Preferences.writeSessionStorage('logSettings', logSettings);
    };

    PWM_MAIN.addEventHandler('form-loadLog','change', saveSettings);
    PWM_MAIN.addEventHandler('form-downloadLog','change', saveSettings);

    const loadSettings = function () {
        const settings = PWM_MAIN.Preferences.readSessionStorage('logSettings');
        if (settings) {
            PWM_MAIN.getObject('username').value = settings['username'];
            PWM_MAIN.getObject('text').value = settings['text'];
            PWM_MAIN.getObject('count').value = settings['count'];
            PWM_MAIN.getObject('maxTime').value = settings['maxTime'];
            PWM_MAIN.JSLibrary.setValueOfSelectElement('type', settings['type']);
            PWM_MAIN.JSLibrary.setValueOfSelectElement('level', settings['level']);
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
    const settings = {};
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
    const logSettings = PWM_ADMIN.readLogFormData();

    const processFunction = function (data) {
        console.time('someFunction');

        const records = data['data']['records'];
        if (PWM_MAIN.JSLibrary.isEmpty(records)) {
            PWM_MAIN.removeCssClass('div-noResultsMessage', 'nodisplay');
            PWM_MAIN.addCssClass('wrapper-logViewerGrid', 'nodisplay');
            PWM_MAIN.addCssClass('wrapper-lineViewer', 'nodisplay');

        } else {
            if (data['data']['display'] === 'grid') {
                PWM_MAIN.addCssClass('div-noResultsMessage', 'nodisplay');
                PWM_MAIN.removeCssClass('wrapper-logViewerGrid', 'nodisplay');
                PWM_MAIN.addCssClass('wrapper-lineViewer', 'nodisplay');
                const grid = PWM_VAR['logViewerGrid'];
                grid.refresh();
                grid.renderArray(records);
                grid.set("timestamp", {attribute: 'createTime', ascending: false, descending: true});
            } else {
                PWM_MAIN.addCssClass('div-noResultsMessage', 'nodisplay');
                PWM_MAIN.addCssClass('wrapper-logViewerGrid', 'nodisplay');
                PWM_MAIN.removeCssClass('wrapper-lineViewer', 'nodisplay');
                let textOutput = '';

                for (let iterator in records) {
                    (function (record) {
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

    const url = PWM_MAIN.addParamToUrl(PWM_GLOBAL['url-context'] + '/private/admin', 'processAction', 'readLogData');
    const options = {};
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
            const CustomGrid = declare([Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry]);

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

    let url = PWM_MAIN.addParamToUrl(window.location.href, "processAction", "auditData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
    url = PWM_MAIN.addParamToUrl(url,'type',type);
    const loadFunction = function (data) {
        grid.renderArray(data['data']['records']);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.showStatChart = function(statName,days,divName,options) {
    options = options === undefined ? {} : options;
    const doRefresh = options['refreshTime']
        ? function () {
            setTimeout(function () {
                PWM_ADMIN.showStatChart(statName, days, divName, options);
            }, options['refreshTime']);
        }
        : function () {
        };
    let statsGetUrl = PWM_MAIN.addParamToUrl(PWM_GLOBAL['url-context'] + '/public/api', "processAction", "statistics");
    const epsTypes = PWM_GLOBAL['epsTypes'];
    const epsDurations = PWM_GLOBAL['epsDurations'];
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

            const errorFunction = function () {
                for (let loopEpsTypeIndex = 0; loopEpsTypeIndex < epsTypes.length; loopEpsTypeIndex++) { // clear all the gauges
                    const loopEpsName = epsTypes[loopEpsTypeIndex] + '';
                    for (let loopEpsDurationsIndex = 0; loopEpsDurationsIndex < epsDurations.length; loopEpsDurationsIndex++) { // clear all the gauges
                        const loopEpsDuration = epsDurations[loopEpsDurationsIndex] + '';
                        const loopEpsID = "EPS-GAUGE-" + loopEpsName + "_" + loopEpsDuration;
                        if (PWM_MAIN.getObject(loopEpsID) !== null) {
                            if (registry.byId(loopEpsID)) {
                                registry.byId(loopEpsID).setAttribute('value', '0');
                            }
                        }
                    }
                }
                doRefresh();
            };

            const loadFunction = function (data) {
                {// gauges
                    console.log('Beginning stats update process...');
                    data = data['data'];
                    let activityCount = 0;
                    for (let loopEpsIndex = 0; loopEpsIndex < epsTypes.length; loopEpsIndex++) {
                        const loopEpsName = epsTypes[loopEpsIndex] + '';
                        for (let loopEpsDurationsIndex = 0; loopEpsDurationsIndex < epsDurations.length; loopEpsDurationsIndex++) { // clear all the gauges
                            const loopEpsDuration = epsDurations[loopEpsDurationsIndex] + '';
                            const loopEpsID = "EPS-GAUGE-" + loopEpsName + "_" + loopEpsDuration;
                            const loopFieldEpsID = "FIELD_" + loopEpsName + "_" + loopEpsDuration;
                            const loopEpsValue = data['EPS'][loopEpsName + "_" + loopEpsDuration];
                            const loopEpmValue = (loopEpsValue * 60).toFixed(3);
                            const loopTop = PWM_GLOBAL['client.activityMaxEpsRate'];
                            if (loopEpsDuration === "HOURLY") {
                                activityCount += loopEpsValue;
                            }
                            if (PWM_MAIN.getObject(loopFieldEpsID) !== null) {
                                PWM_MAIN.getObject(loopFieldEpsID).innerHTML = loopEpmValue;
                            }
                            if (PWM_MAIN.getObject(loopEpsID) !== null) {
                                console.log('EpsID=' + loopEpsID + ', ' + 'Eps=' + loopEpsValue + ', ' + 'Epm=' + loopEpmValue);
                                if (registry.byId(loopEpsID)) {
                                    registry.byId(loopEpsID).setAttribute('value', loopEpmValue);
                                    registry.byId(loopEpsID).setAttribute('max', loopTop);
                                } else {
                                    const glossyCircular = new dojox.gauges.GlossyCircularGauge({
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
                    const values = [];
                    for (let key in data['nameData']) {
                        const value = data['nameData'][key];
                        values.push(parseInt(value));
                    }

                    if (PWM_GLOBAL[divName + '-stored-reference']) {
                        const existingChart = PWM_GLOBAL[divName + '-stored-reference'];
                        existingChart.destroy();
                    }
                    const c = new dojox.charting.Chart2D(divName);
                    PWM_GLOBAL[divName + '-stored-reference'] = c;
                    c.addPlot("default", {type: "Columns", gap: '2'});
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

    const inputOpts = options || PWM_GLOBAL['showPwmHealthOptions'] || {};
    PWM_GLOBAL['showPwmHealthOptions'] = options;
    let refreshUrl = inputOpts['sourceUrl'] || PWM_GLOBAL['url-context'] + "/public/api?processAction=health";
    const showRefresh = inputOpts['showRefresh'];
    const showTimestamp = inputOpts['showTimestamp'];
    const refreshTime = inputOpts['refreshTime'] || 60 * 1000;
    const finishFunction = inputOpts['finishFunction'];

    console.log('starting showPwmHealth: refreshTime=' + refreshTime);
    require(["dojo"],function(dojo){
        const parentDiv = dojo.byId(parentDivID);
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

        const loadFunction = function (data) {
            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
            } else {
                PWM_GLOBAL['pwm-health'] = data['data']['overall'];
                const htmlBody = PWM_ADMIN.makeHealthHtml(data['data'], showTimestamp, showRefresh);
                parentDiv.innerHTML = htmlBody;
                PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('healthCheckTimestamp'));

                PWM_MAIN.addEventHandler('button-refreshHealth', 'click', function () {
                    PWM_ADMIN.showAppHealth(parentDivID, options, true);
                });

                PWM_GLOBAL['healthCheckInProgress'] = false;

                if (refreshTime > 0) {
                    setTimeout(function () {
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

        const errorFunction = function (error) {
            if (error !== null) {
                console.log('error reaching server: ' + error);
            }
            let htmlBody = '<div style="text-align:center; background-color: #d20734">';
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
                setTimeout(function () {
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
    const healthRecords = healthData['records'];
    let htmlBody = '<div>';
    htmlBody += '<div class="healthTable-wrapper"><table>';
    for (let i = 0; i < healthRecords.length; i++) {
        (function(iter){
            const loopRecord = healthRecords[iter];
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
                const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'startPwNotifyJob');
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
    const processData = function (data) {
        const statusData = data['data']['statusData'];
        let htmlData = '<tr><td colspan="2" class="title">Password Expiration Notification Job Status</td></tr>';
        for (var item in statusData) {
            (function (key) {
                const item = statusData[key];
                htmlData += '<tr><td>' + item['label'] + '</td><td>';
                if (item['type'] === 'timestamp') {
                    htmlData += '<span id="pwNotifyStatusRow-' + key + '" class="timestamp">' + item['value'] + '</span>';
                } else {
                    htmlData += item['value'];
                }
                htmlData += '</td></tr>';
            })(item);
        }

        PWM_MAIN.getObject('table-pwNotifyStatus').innerHTML = htmlData;

        for (var item in statusData) {
            (function (key) {
                const item = statusData[key];
                if (item['type'] === 'timestamp') {
                    PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('pwNotifyStatusRow-' + key));
                }
            })(item);
        }

        PWM_MAIN.getObject('button-executePwNotifyJob').disabled = !data['data']['enableStartButton'];
    };
    const url = PWM_MAIN.addParamToUrl(window.location.href, 'processAction', 'readPwNotifyStatus');
    PWM_MAIN.ajaxRequest(url, processData);

};

PWM_ADMIN.loadPwNotifyLog = function () {
    const processData = function (data) {
        const debugData = data['data'];
        if (debugData && debugData.length > 0) {
            PWM_MAIN.getObject('div-pwNotifyDebugLog').innerHTML = '';
            PWM_MAIN.getObject('div-pwNotifyDebugLog').appendChild(document.createTextNode(debugData));
        } else {
            PWM_MAIN.getObject('div-pwNotifyDebugLog').innerHTML = '<span class="footnote">Job has not been run on this server since startup.</span>';
        }
    };
    const url = PWM_MAIN.addParamToUrl(window.location.href, 'processAction', 'readPwNotifyLog');
    PWM_MAIN.ajaxRequest(url, processData);

};

PWM_ADMIN.detailView = function(evt, headers, grid){
    const row = grid.row(evt);
    let text = '<table>';
    const postExecuteFunctions = [];
    for (let item in headers) {
        (function(key){
            const field = headers[key]['field'];
            const label = headers[key]['label'];
            const value = field in row.data ? row.data[field] : '';
            const id = "record-detail-" + key;
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
            for (let i = 0; i < postExecuteFunctions.length; i++) {
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

