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

const PWM_ADMIN = {};

import {PWM_JSLibrary} from "./jslibrary.js";
import {PWM_UILibrary} from "./uilibrary.js";
import {PWM_MAIN} from "./main.js";

export {PWM_ADMIN};

let healthCheckInProgress = false;

PWM_ADMIN.initAdminNavMenu = async function() {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();

    const makeMenu = function () {

        require(["dijit/form/DropDownButton", "dijit/DropDownMenu", "dijit/Menu", "dijit/MenuItem", "dijit/PopupMenuItem", "dojo/dom", "dijit/MenuSeparator"],
            function (DropDownButton, DropDownMenu, Menu, MenuItem, PopupMenuItem, dom, MenuSeparator) {
                const pMenu = new DropDownMenu({style: "display: none;"});
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.getDisplayString('Title_LogViewer'),
                    id: 'eventLog_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/logs');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.getDisplayString('Title_TokenLookup'),
                    id: 'tokenLookup_dropitem',
                    onClick: function () {
                        PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/admin/tokens');
                    }
                }));
                pMenu.addChild(new MenuItem({
                    label: PWM_ADMIN.getDisplayString('Title_URLReference'),
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

    PWM_JSLibrary.doIfQueryHasResults("#admin-nav-menu-container",makeMenu)
};


PWM_ADMIN.initDownloadProcessReportZipForm = function() {
    PWM_JSLibrary.doQuery("#reportDownloadButton", function(node){
        PWM_MAIN.addEventHandler(node, "click", async function() {
            PWM_MAIN.showConfirmDialog({title:"Report Status",text:await PWM_ADMIN.getDisplayString('Confirm_Report_Start'),okAction:function(){
                    let url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','downloadReportZip');
                    url = PWM_MAIN.addParamToUrl(url,'recordCount',PWM_JSLibrary.getElement('recordCount').value);
                    url = PWM_MAIN.addParamToUrl(url,'recordType',PWM_JSLibrary.readValueOfSelectElement('recordType'));
                    window.location.href = url;
                }});
        })
    });
    PWM_JSLibrary.doQuery("#reportCancelButton", function(node){
        PWM_MAIN.addEventHandler(node, "click", function() {
            const url = PWM_MAIN.addParamToUrl(window.location.href, 'processAction', 'cancelDownload');
            PWM_MAIN.ajaxRequest(url, function(){
                PWM_MAIN.showDialog({title:"Report Status",text:"Download Cancelled"})
            });
        })
    });
};

PWM_ADMIN.refreshReportProcessStatus=function() {
    const url = PWM_MAIN.addParamToUrl(null, 'processAction','reportProcessStatus');
    const loadFunction = function(data) {
        if (data['data'] && data['data']['presentable']) {
            const fields = data['data']['presentable'];
            PWM_JSLibrary.getElement('statusTable').innerHTML = PWM_UILibrary.displayElementsToTableContents(fields);
            PWM_UILibrary.initElementsToTableContents(fields);
        }

        const reportInProgress = data['data']['reportInProgress'] === true;
        PWM_JSLibrary.getElement("reportDownloadButton").disabled = reportInProgress;
        PWM_JSLibrary.getElement("reportCancelButton").disabled = !reportInProgress;
        PWM_JSLibrary.getElement( "downloadReportOptionsFieldset" ).disabled = reportInProgress;

        if ( reportInProgress ) {
            PWM_MAIN.cancelCountDownTimer()
        }
    };
    const errorFunction = function (error) {
        debugger;
        console.log('error during report status update: ' + error);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction});
};

PWM_ADMIN.webSessionHeaders = async function() {
    return [
        {field:"userID",label:await PWM_ADMIN.getDisplayString('Field_Session_UserID')},
        {field:"ldapProfile",label:await PWM_ADMIN.getDisplayString('Field_Session_LdapProfile')},
        {field:"userDN",label:await PWM_ADMIN.getDisplayString('Field_Session_UserDN'),hidden:true},
        {field:"createTime",label:await PWM_ADMIN.getDisplayString('Field_Session_CreateTime')},
        {field:"lastTime",label:await PWM_ADMIN.getDisplayString('Field_Session_LastTime')},
        {field:"label",label:await PWM_ADMIN.getDisplayString('Field_Session_Label')},
        {field:"idle",label:await PWM_ADMIN.getDisplayString('Field_Session_Idle')},
        {field:"locale",label:await PWM_ADMIN.getDisplayString('Field_Session_Locale'),hidden:true},
        {field:"srcAddress",label:await PWM_ADMIN.getDisplayString('Field_Session_SrcAddress')},
        {field:"srcHost",label:await PWM_ADMIN.getDisplayString('Field_Session_SrcHost'),hidden:true},
        {field:"lastUrl",label:await PWM_ADMIN.getDisplayString('Field_Session_LastURL'),hidden:true},
        {field:"intruderAttempts",label:await PWM_ADMIN.getDisplayString('Field_Session_IntruderAttempts'),hidden:true}
    ];
};

PWM_ADMIN.initActiveSessionGrid=async function() {
    const headers = await PWM_ADMIN.webSessionHeaders();

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
                PWM_ADMIN.detailView(evt, headers, PWM_VAR['activeSessionsGrid']);
            });
        });
};

PWM_ADMIN.refreshActiveSessionGrid=function() {
    const grid = PWM_VAR['activeSessionsGrid'];
    grid.refresh();

    const maximum = PWM_JSLibrary.getElement('maxActiveSessionResults').value;
    let url = PWM_MAIN.addParamToUrl(window.location.href, "processAction", "sessionData");
    url = PWM_MAIN.addParamToUrl(url,'maximum',maximum);
    const loadFunction = function (data) {
        grid.renderArray(data['data']);
        grid.set("sort", {attribute: 'createTime', ascending: false, descending: true});
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.intruderHeaders = async function(){
    return [
        {field:"domainID",label:await PWM_ADMIN.getDisplayString('Field_Intruder_Domain')},
        {field:"subject",label:await PWM_ADMIN.getDisplayString('Field_Intruder_Subject')},
        {field:"timeStamp",label:await PWM_ADMIN.getDisplayString('Field_Intruder_Timestamp')},
        {field:"attemptCount",label:await PWM_ADMIN.getDisplayString('Field_Intruder_Count')},
        {field:"status",label:await PWM_ADMIN.getDisplayString('Field_Intruder_Status')}
    ];
};


PWM_ADMIN.initIntrudersGrid=async function() {
    const headers = await PWM_ADMIN.intruderHeaders();
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
                        PWM_ADMIN.detailView(evt, headers, grid);
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
        var maximum = PWM_JSLibrary.getElement('maxIntruderGridResults').value;
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

PWM_ADMIN.auditUserHeaders = async function() {
    return [
        {field:"domain",label:await PWM_ADMIN.getDisplayString('Field_Audit_Domain')},
        {field:"timestamp",label:await PWM_ADMIN.getDisplayString('Field_Audit_Timestamp')},
        {field:"perpetratorID",label:await PWM_ADMIN.getDisplayString('Field_Audit_PerpetratorID')},
        {field:"perpetratorDN",label:await PWM_ADMIN.getDisplayString('Field_Audit_PerpetratorDN'),hidden:true},
        {field:"perpetratorLdapProfile",label:await PWM_ADMIN.getDisplayString('Field_Audit_PerpetratorLdapProfile'),hidden:true},
        {field:"eventCode",label:await PWM_ADMIN.getDisplayString('Field_Audit_EventCode')},
        {field:"message",label:await PWM_ADMIN.getDisplayString('Field_Audit_Message'),hidden:true},
        {field:"sourceAddress",label:await PWM_ADMIN.getDisplayString('Field_Audit_SourceAddress')},
        {field:"sourceHost",label:await PWM_ADMIN.getDisplayString('Field_Audit_SourceHost'),hidden:true},
        {field:"guid",label:await PWM_ADMIN.getDisplayString('Field_Audit_GUID'),hidden:true},
        {field:"narrative",label:await PWM_ADMIN.getDisplayString('Field_Audit_Narrative')}
    ];
};

PWM_ADMIN.auditHelpdeskHeaders = async function() {
    return [
        {field:"domain",label:await PWM_ADMIN.getDisplayString('Field_Audit_Domain')},
        {field:"timestamp",label:await PWM_ADMIN.getDisplayString('Field_Audit_Timestamp')},
        {field:"perpetratorID",label:await PWM_ADMIN.getDisplayString('Field_Audit_PerpetratorID')},
        {field:"perpetratorDN",label:await PWM_ADMIN.getDisplayString('Field_Audit_PerpetratorDN'),hidden:true},
        {field:"perpetratorLdapProfile",label:await PWM_ADMIN.getDisplayString('Field_Audit_PerpetratorLdapProfile'),hidden:true},
        {field:"eventCode",label:await PWM_ADMIN.getDisplayString('Field_Audit_EventCode')},
        {field:"message",label:await PWM_ADMIN.getDisplayString('Field_Audit_Message'),hidden:true},
        {field:"targetID",label:await PWM_ADMIN.getDisplayString('Field_Audit_TargetID')},
        {field:"targetDN",label:await PWM_ADMIN.getDisplayString('Field_Audit_TargetDN')},
        {field:"targetLdapProfile",label:await PWM_ADMIN.getDisplayString('Field_Audit_TargetLdapProfile')},
        {field:"sourceAddress",label:await PWM_ADMIN.getDisplayString('Field_Audit_SourceAddress')},
        {field:"sourceHost",label:await PWM_ADMIN.getDisplayString('Field_Audit_SourceHost'),hidden:true},
        {field:"guid",label:await PWM_ADMIN.getDisplayString('Field_Audit_GUID'),hidden:true},
        {field:"narrative",label:await PWM_ADMIN.getDisplayString('Field_Audit_Narrative'),hidden:true}
    ];
};

PWM_ADMIN.auditSystemHeaders = async function() {
    return [
        {field:"timestamp",label:await PWM_ADMIN.getDisplayString('Field_Audit_Timestamp')},
        {field:"eventCode",label:await PWM_ADMIN.getDisplayString('Field_Audit_EventCode')},
        {field:"message",label:await PWM_ADMIN.getDisplayString('Field_Audit_Message')},
        {field:"instance",label:await PWM_ADMIN.getDisplayString('Field_Audit_Instance'),hidden:true},
        {field:"guid",label:await PWM_ADMIN.getDisplayString('Field_Audit_GUID'),hidden:true},
        {field:"narrative",label:await PWM_ADMIN.getDisplayString('Field_Audit_Narrative'),hidden:true}
    ];
};

PWM_ADMIN.logHeaders = async function() {
    return [
        {field:"d",label:await PWM_ADMIN.getDisplayString('Field_Logs_Timestamp')},
        {field:"l",label:await PWM_ADMIN.getDisplayString('Field_Logs_Level')},
        {field:"s",label:await PWM_ADMIN.getDisplayString('Field_Logs_Source'),hidden:true},
        {field:"b",label:await PWM_ADMIN.getDisplayString('Field_Logs_Label')},
        {field:"a",label:await PWM_ADMIN.getDisplayString('Field_Logs_User'),hidden:true},
        {field:"t",label:await PWM_ADMIN.getDisplayString('Field_Logs_Component'),hidden:true},
        {field:"m",label:await PWM_ADMIN.getDisplayString('Field_Logs_Detail')},
        {field:"e",label:await PWM_ADMIN.getDisplayString('Field_Logs_Error'),hidden:true}
    ];
};

PWM_ADMIN.initLogGrid=async function() {
    const logHeaders = await PWM_ADMIN.logHeaders();
    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            // Create a new constructor by mixing in the components
            const CustomGrid = declare([Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry]);

            // Now, create an instance of our custom userGrid
            PWM_VAR['logViewerGrid'] = new CustomGrid({columns: logHeaders}, "logViewerGrid");
            PWM_VAR['logViewerGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, logHeaders, PWM_VAR['logViewerGrid']);
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
            PWM_JSLibrary.getElement('username').value = settings['username'];
            PWM_JSLibrary.getElement('text').value = settings['text'];
            PWM_JSLibrary.getElement('count').value = settings['count'];
            PWM_JSLibrary.getElement('maxTime').value = settings['maxTime'];
            PWM_JSLibrary.setValueOfSelectElement('type', settings['type']);
            PWM_JSLibrary.setValueOfSelectElement('level', settings['level']);
            PWM_JSLibrary.setValueOfSelectElement('displayType', settings['displayType']);
            if (PWM_JSLibrary.getElement('form-downloadLog')) {
                PWM_JSLibrary.setValueOfSelectElement('downloadType', settings['downloadType']);
                PWM_JSLibrary.setValueOfSelectElement('compressionType', settings['compressionType']);
            }
        }
    };
    loadSettings();
};

PWM_ADMIN.readLogFormData = function() {
    const settings = {};
    settings['username'] = PWM_JSLibrary.getElement('username').value;
    settings['text'] = PWM_JSLibrary.getElement('text').value;
    settings['count'] = PWM_JSLibrary.getElement('count').value;
    settings['maxTime'] = PWM_JSLibrary.getElement('maxTime').value;
    settings['type'] = PWM_JSLibrary.readValueOfSelectElement('type');
    settings['level'] = PWM_JSLibrary.readValueOfSelectElement('level');
    settings['displayType'] = PWM_JSLibrary.readValueOfSelectElement('displayType');
    if (PWM_JSLibrary.getElement('form-downloadLog')) {
        settings['downloadType'] = PWM_JSLibrary.readValueOfSelectElement('downloadType');
        settings['compressionType'] = PWM_JSLibrary.readValueOfSelectElement('compressionType');
    }
    return settings;
};

PWM_ADMIN.refreshLogData = async function() {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();

    PWM_JSLibrary.getElement('button-search').disabled = true;
    const logSettings = PWM_ADMIN.readLogFormData();

    const processFunction = function (data) {
        console.time('someFunction');

        const records = data['data']['records'];
        if (PWM_JSLibrary.isEmpty(records)) {
            PWM_JSLibrary.removeCssClass('div-noResultsMessage', 'nodisplay');
            PWM_JSLibrary.addCssClass('wrapper-logViewerGrid', 'nodisplay');
            PWM_JSLibrary.addCssClass('wrapper-lineViewer', 'nodisplay');

        } else {
            if (data['data']['display'] === 'grid') {
                PWM_JSLibrary.addCssClass('div-noResultsMessage', 'nodisplay');
                PWM_JSLibrary.removeCssClass('wrapper-logViewerGrid', 'nodisplay');
                PWM_JSLibrary.addCssClass('wrapper-lineViewer', 'nodisplay');
                const grid = PWM_VAR['logViewerGrid'];
                grid.refresh();
                grid.renderArray(records);
                grid.set("timestamp", {attribute: 'createTime', ascending: false, descending: true});
            } else {
                PWM_JSLibrary.addCssClass('div-noResultsMessage', 'nodisplay');
                PWM_JSLibrary.addCssClass('wrapper-logViewerGrid', 'nodisplay');
                PWM_JSLibrary.removeCssClass('wrapper-lineViewer', 'nodisplay');
                let textOutput = '';

                for (let iterator in records) {
                    (function (record) {
                        textOutput += records[record];
                        textOutput += "\n";
                    }(iterator));
                }
                PWM_JSLibrary.getElement('lineViewer').textContent = textOutput;
            }
        }
        console.timeEnd('someFunction');

        PWM_JSLibrary.getElement('button-search').disabled = false;
        PWM_MAIN.closeWaitDialog();
    };

    const url = await PWM_MAIN.addParamToUrl(PWM_GLOBAL['url-context'] + '/private/admin', 'processAction', 'readLogData');
    const options = {};
    options.content = logSettings;

    PWM_MAIN.showWaitDialog({loadFunction:function(){
            PWM_MAIN.ajaxRequest(url,processFunction,options);
        }
    });
};


PWM_ADMIN.initAuditGrid=async function() {
    const userHeaders = await PWM_ADMIN.auditUserHeaders();
    const systemHeaders = await PWM_ADMIN.auditSystemHeaders();
    const helpdeskHeaders = await PWM_ADMIN.auditHelpdeskHeaders();

    require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
        function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
            // Create a new constructor by mixing in the components
            const CustomGrid = declare([Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry]);

            // Now, create an instance of our custom userGrid
            PWM_VAR['auditUserGrid'] = new CustomGrid({columns: userHeaders}, "auditUserGrid");
            PWM_VAR['auditSystemGrid'] = new CustomGrid({columns: systemHeaders}, "auditSystemGrid");
            PWM_VAR['auditHelpdeskGrid'] = new CustomGrid({columns: helpdeskHeaders}, "auditHelpdeskGrid");

            PWM_ADMIN.refreshAuditGridData(undefined,'USER');
            PWM_ADMIN.refreshAuditGridData(undefined,'HELPDESK');
            PWM_ADMIN.refreshAuditGridData(undefined,'SYSTEM');

            PWM_VAR['auditUserGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, userHeaders, PWM_VAR['auditUserGrid']);
            });
            PWM_VAR['auditSystemGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, systemHeaders, PWM_VAR['auditSystemGrid']);
            });
            PWM_VAR['auditHelpdeskGrid'].on(".dgrid-row:click", function(evt){
                PWM_ADMIN.detailView(evt, helpdeskHeaders, PWM_VAR['auditHelpdeskGrid']);
            });
        });
};

PWM_ADMIN.refreshAuditGridData=async function(maximum,type) {
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

    let url = await PWM_MAIN.addParamToUrl(window.location.href, "processAction", "auditData");
    url = await PWM_MAIN.addParamToUrl(url,'maximum',maximum);
    url = await PWM_MAIN.addParamToUrl(url,'type',type);
    const loadFunction = function (data) {
        grid.renderArray(data['data']['records']);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
};

PWM_ADMIN.showStatChart = async function(statName,days,divName,options) {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();

    options = options === undefined ? {} : options;
    const doRefresh = options['refreshTime']
        ? function () {
            setTimeout(function () {
                PWM_ADMIN.showStatChart(statName, days, divName, options);
            }, options['refreshTime']);
        }
        : function () {
        };
    let statsGetUrl = await PWM_MAIN.addParamToUrl(PWM_GLOBAL['url-context'] + '/public/api', "processAction", "statistics");
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
                        if (PWM_JSLibrary.getElement(loopEpsID) !== null) {
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
                            if (PWM_JSLibrary.getElement(loopFieldEpsID) !== null) {
                                PWM_JSLibrary.getElement(loopFieldEpsID).innerHTML = loopEpmValue;
                            }
                            if (PWM_JSLibrary.getElement(loopEpsID) !== null) {
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
                }
                if (divName !== null && PWM_JSLibrary.getElement(divName)) { // stats chart
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

PWM_ADMIN.showAppHealth = async function(parentDivID, options, refreshNow) {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();

    const inputOpts = options || PWM_GLOBAL['showPwmHealthOptions'] || {};
    let refreshUrl = inputOpts['sourceUrl'] || PWM_GLOBAL['url-context'] + "/public/api?processAction=health";
    const showRefresh = inputOpts['showRefresh'];
    const showTimestamp = inputOpts['showTimestamp'];
    const refreshTime = inputOpts['refreshTime'] || 60 * 1000;
    const finishFunction = inputOpts['finishFunction'];

    console.log('starting showPwmHealth: refreshTime=' + refreshTime);
    const parentDiv = PWM_JSLibrary.getElement(parentDivID);
    if (PWM_GLOBAL['inhibitHealthUpdate'] === true) {
        try { parentDiv.innerHTML = ''; } catch (e) { console.log('unable to update health div' + e) };
        return;
    }

    if (healthCheckInProgress) {
        return;
    }

    healthCheckInProgress = "true";

    if (refreshNow) {
        parentDiv.innerHTML = '<div class="WaitDialogBlank" style="margin-top: 20px; margin-bottom: 20px"/>';
        refreshUrl = PWM_MAIN.addParamToUrl(refreshUrl, 'refreshImmediate', 'true');
    }

    let overallHealth;

    const loadFunction = function (data) {
        if (data['error']) {
            PWM_MAIN.showErrorDialog(data);
        } else {
            overallHealth = data['data']['overall'];
            const htmlBody = PWM_ADMIN.makeHealthHtml(data['data'], {showTimestamp:true, showRefresh:true});
            parentDiv.innerHTML = htmlBody;
            PWM_MAIN.initTimestampElement(PWM_JSLibrary.getElement('healthCheckTimestamp'));

            PWM_MAIN.addEventHandler('button-refreshHealth', 'click', function () {
                PWM_ADMIN.showAppHealth(parentDivID, options, true);
            });

            healthCheckInProgress = false;

            if (refreshTime > 0) {
                setTimeout(function () {
                    PWM_ADMIN.showAppHealth(parentDivID, options);
                }, refreshTime);
            }
            if (showTimestamp) {
                PWM_MAIN.initTimestampElement(PWM_JSLibrary.getElement('healthCheckTimestamp'));
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
        healthCheckInProgress = false;
        overallHealth = 'WARN';
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
};

PWM_ADMIN.makeHealthHtml = function(healthData, options) {
    options = options || {};
    const healthRecords = healthData['records'];
    let htmlBody = '<div>';
    htmlBody += '<div class="healthTable-wrapper"><table id="healthTable">';

    const domainIds = new Set();
    PWM_JSLibrary.forEachInArray(healthRecords,function(loopRecord) {
        domainIds.add(loopRecord.domainID);
    });

    PWM_JSLibrary.forEachInArray(Array.from(domainIds),function(domainId){
        if ( domainIds.size > 1 )
        {
            htmlBody += `<tr><td colspan="3" class="title">${domainId}</td></tr>`
        }
        PWM_JSLibrary.forEachInArray(healthRecords,function(loopRecord){
            if ( domainId === loopRecord.domainID) {
                htmlBody += '<tr>'
                    + `<td class="category">${loopRecord.topic}</td>`
                    + `<td class="health-${loopRecord.status}">${loopRecord.status}</td>`
                    + `<td class="detail">${loopRecord.detail}</td>`
                    + '</tr>';
            }
        });
    });

    htmlBody += '</table></div>';

    if (options.showTimestamp || options.showRefresh) {
        htmlBody += '<div class="healthTable-footer">';
        if (options.showTimestamp && healthData.timestamp) {
            htmlBody += `Last Updated <span id="healthCheckTimestamp" class="timestamp">${healthData.timestamp}</span>`;
        }
        if (options.showRefresh) {
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
                PWM_JSLibrary.getElement('button-refreshPwNotifyStatus').disabled = true;
                PWM_ADMIN.loadPwNotifyStatus();
                PWM_ADMIN.loadPwNotifyLog();
                setTimeout(function () {
                    PWM_MAIN.closeWaitDialog();
                    PWM_JSLibrary.getElement('button-refreshPwNotifyStatus').disabled = false;
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

        PWM_JSLibrary.getElement('table-pwNotifyStatus').innerHTML = htmlData;

        for (var item in statusData) {
            (function (key) {
                const item = statusData[key];
                if (item['type'] === 'timestamp') {
                    PWM_MAIN.initTimestampElement(PWM_JSLibrary.getElement('pwNotifyStatusRow-' + key));
                }
            })(item);
        }

        PWM_JSLibrary.getElement('button-executePwNotifyJob').disabled = !data['data']['enableStartButton'];
    };
    const url = PWM_MAIN.addParamToUrl(window.location.href, 'processAction', 'readPwNotifyStatus');
    PWM_MAIN.ajaxRequest(url, processData);

};

PWM_ADMIN.loadPwNotifyLog = function () {
    const processData = function (data) {
        const debugData = data['data'];
        if (debugData && debugData.length > 0) {
            PWM_JSLibrary.getElement('div-pwNotifyDebugLog').innerHTML = '';
            PWM_JSLibrary.getElement('div-pwNotifyDebugLog').appendChild(document.createTextNode(debugData));
        } else {
            PWM_JSLibrary.getElement('div-pwNotifyDebugLog').innerHTML = '<span class="footnote">Job has not been run on this server since startup.</span>';
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
                postExecuteFunctions.push(function() {
                    PWM_MAIN.initTimestampElement(PWM_JSLibrary.getElement(id));
                });
            }
            text += '</span></td></tr>';
            postExecuteFunctions.push(function(){
                PWM_JSLibrary.getElement(id).appendChild(document.createTextNode(value));
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

PWM_ADMIN.getDisplayString=async function(key, options) {
    options = options || {};
    options['bundle'] = 'Admin';
    return await PWM_MAIN.getDisplayString(key,options);
};

PWM_ADMIN.initActivityPage=function() {
    PWM_ADMIN.initAuditGrid();
    PWM_ADMIN.initActiveSessionGrid();
    PWM_ADMIN.initIntrudersGrid();

    PWM_MAIN.addEventHandler('button-refreshAuditUser','click',function(){
        PWM_ADMIN.refreshAuditGridData(PWM_JSLibrary.getElement('maxAuditUserResults').value,'USER');
    });
    PWM_MAIN.addEventHandler('button-refreshHelpdeskUser','click',function(){
        PWM_ADMIN.refreshAuditGridData(PWM_JSLibrary.getElement('maxAuditHelpdeskResults').value,'HELPDESK');
    });
    PWM_MAIN.addEventHandler('button-refreshSystemAudit','click',function(){
        PWM_ADMIN.refreshAuditGridData(PWM_JSLibrary.getElement('maxAuditSystemResults').value,'SYSTEM');
    });
}