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

"use strict";

var PWM_CONFIG = PWM_CONFIG || {};
var PWM_GLOBAL = PWM_GLOBAL || {};

PWM_CONFIG.lockConfiguration=function() {
    PWM_MAIN.showConfirmDialog({text:PWM_CONFIG.showString('Confirm_LockConfig'),okAction:function(){
            PWM_MAIN.showWaitDialog({loadFunction:function() {
                    var url = 'ConfigManager?processAction=lockConfiguration';
                    var loadFunction = function(data) {
                        if (data['error'] === true) {
                            PWM_MAIN.closeWaitDialog();
                            PWM_MAIN.showDialog({
                                title: PWM_MAIN.showString('Title_Error'),
                                text: data['errorDetail']
                            });
                        } else {
                            PWM_CONFIG.waitForRestart();
                        }
                    };
                    PWM_MAIN.ajaxRequest(url,loadFunction);
                }});
        }});
};

PWM_CONFIG.waitForRestart=function(options) {
    var pingCycleTimeMs = 1000;
    var maxWaitTimeMs = 120 * 1000;

    PWM_VAR['cancelHeartbeatCheck'] = true;

    var restartFunction = function() {
        var redirectUrl = 'location' in options ? options['location'] : '/';
        console.log("application appears to be restarted, redirecting to context url: " + redirectUrl);
        PWM_MAIN.gotoUrl(redirectUrl);
    };

    options = options === undefined ? {} : options;
    if (!('failbackStartTime' in options)) {
        options['failbackStartTime'] = Date.now();
    } else {
        var elapsedMs = Date.now() - options['failbackStartTime'];
        if (elapsedMs > maxWaitTimeMs) {
            restartFunction();
            return;
        }
    }

    var originalRuntimeNonce = PWM_GLOBAL['runtimeNonce'];

    console.log("beginning request to determine application status: ");
    var loadFunction = function(data) {
        try {
            if (data['error']) {
                console.log('data error reading /ping endpoint: ' + JSON.stringify(data));
            } else {
                var currentNonce = data['data']['runtimeNonce'];
                console.log("comparing declared nonce=" + originalRuntimeNonce + " and xhr read nonce=" + currentNonce);
                if (currentNonce !== originalRuntimeNonce) {
                    console.log("change detected, restarting page");
                    restartFunction();
                    return;
                } else {
                    console.log("no change detected");
                }
            }
        } catch (e) {
            console.log("can't read current server nonce, will retry detection (current error: " + e + ")");
        }
        setTimeout(function() {
            PWM_CONFIG.waitForRestart(options)
        }, pingCycleTimeMs);
    };
    var errorFunction = function(error) {
        setTimeout(function() {
            PWM_CONFIG.waitForRestart(options)
        }, pingCycleTimeMs);
        console.log('Waiting for server restart, unable to contact server: ' + error);
    };
    var url = PWM_GLOBAL['url-context'] + "/public/api?processAction=ping";
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,method:'GET',preventCache:true});
};

PWM_CONFIG.startNewConfigurationEditor=function(template) {
    PWM_MAIN.showWaitDialog({title:'Loading...',loadFunction:function(){
            require(["dojo"],function(dojo){
                dojo.xhrGet({
                    url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&getTemplate=" + template,
                    preventCache: true,
                    error: function(errorObj) {
                        PWM_MAIN.showError("error starting configuration editor: " + errorObj);
                    },
                    load: function() {
                        window.location = "ConfigManager?processAction=editMode&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + '&mode=SETTINGS';
                    }
                });
            });
        }});
};

PWM_CONFIG.uploadConfigDialog=function() {
    PWM_MAIN.preloadAll(function() {
        var uploadOptions = {};
        uploadOptions['url'] = window.location.pathname + '?processAction=uploadConfig';
        uploadOptions['title'] = 'Upload Configuration';
        uploadOptions['nextFunction'] = function () {
            PWM_MAIN.showWaitDialog({
                title: 'Save complete, restarting application...', loadFunction: function () {
                    PWM_CONFIG.waitForRestart({location: '/'});
                }
            });
        };
        UILibrary.uploadFileDialog(uploadOptions);
    });
};

PWM_CONFIG.uploadLocalDB=function() {
    PWM_MAIN.preloadAll(function() {
        PWM_MAIN.showConfirmDialog({
            text: PWM_CONFIG.showString('Confirm_UploadLocalDB'),
            okAction: function () {
                var uploadOptions = {};
                uploadOptions['url'] = 'localdb?processAction=importLocalDB';
                uploadOptions['title'] = 'Upload and Import LocalDB Archive';
                uploadOptions['nextFunction'] = function () {
                    PWM_MAIN.showWaitDialog({
                        title: 'Save complete, restarting application...', loadFunction: function () {
                            PWM_CONFIG.waitForRestart({location: '/'});
                        }
                    });
                };
                PWM_MAIN.IdleTimeoutHandler.cancelCountDownTimer();
                UILibrary.uploadFileDialog(uploadOptions);
            }
        });
    });
};

PWM_CONFIG.closeHeaderWarningPanel = function() {
    console.log('action closeHeader');

    PWM_MAIN.addCssClass('header-warning','nodisplay');
    PWM_MAIN.addCssClass('header-warning-backdrop','nodisplay');
    //PWM_MAIN.removeCssClass('button-openHeader','nodisplay');
};

PWM_CONFIG.openHeaderWarningPanel = function() {
    console.log('action openHeader');

    require(['dojo/dom','dijit/place'], function(dom, place) {
        PWM_MAIN.removeCssClass('header-warning-backdrop','nodisplay');
        PWM_MAIN.removeCssClass('header-warning','nodisplay');
        //PWM_MAIN.addCssClass('button-openHeader','nodisplay');
        place.around(PWM_MAIN.getObject("header-warning"), PWM_MAIN.getObject("header-username-caret"), ["below-alt"], false);

        PWM_MAIN.addEventHandler("header-warning-backdrop", "click", function(event) {
            PWM_CONFIG.closeHeaderWarningPanel();
        });
    });
};

PWM_CONFIG.showString=function (key, options) {
    options = options === undefined ? {} : options;
    options['bundle'] = 'Config';
    return PWM_MAIN.showString(key,options);

};

PWM_CONFIG.showHeaderHealth = function() {
    var refreshUrl = PWM_GLOBAL['url-context'] + "/public/api?processAction=health";
    var parentDiv = PWM_MAIN.getObject('panel-header-healthData');
    if (!parentDiv) {
        return;
    }
    var headerDiv = PWM_MAIN.getObject('header-warning');
    if (parentDiv && headerDiv) {
        var loadFunction = function(data) {
            if (data['data'] && data['data']['overall']) {
                var hasWarnTopics = data['data']['overall'] === 'WARN';
                if (hasWarnTopics) {
                    PWM_MAIN.removeCssClass('header-menu-alert','display-none');
                    PWM_MAIN.removeCssClass('panel-header-healthData','display-none');
                } else {
                    PWM_MAIN.addCssClass('header-menu-alert','display-none');
                    PWM_MAIN.addCssClass('panel-header-healthData','display-none');
                }
                setTimeout(function () {
                    PWM_CONFIG.showHeaderHealth()
                }, 30 * 1000);
            }
        };
        var errorFunction = function(error) {
            console.log('unable to read header health status: ' + error);
        };
        PWM_MAIN.ajaxRequest(refreshUrl, loadFunction,{errorFunction:errorFunction,method:'GET'});
    }
};

PWM_CONFIG.downloadLocalDB = function () {
    PWM_MAIN.showConfirmDialog({
        text:PWM_CONFIG.showString("Warning_DownloadLocal"),
        okAction:function(){
            PWM_MAIN.showWaitDialog({
                loadFunction:function(){
                    PWM_MAIN.gotoUrl('localdb?processAction=exportLocalDB',{addFormID:true,hideDialog:true});
                    setTimeout(function(){PWM_MAIN.closeWaitDialog()},5000);
                }
            });

        }
    });
};

PWM_CONFIG.downloadConfig = function () {
    PWM_MAIN.showConfirmDialog({
        text:PWM_CONFIG.showString("Warning_DownloadConfiguration"),
        okAction:function(){
            PWM_MAIN.showWaitDialog({
                loadFunction:function(){
                    PWM_MAIN.gotoUrl('ConfigManager?processAction=downloadConfig',{addFormID:true,hideDialog:true});
                    setTimeout(function(){PWM_MAIN.closeWaitDialog()},5000);
                }
            });

        }
    });
};

PWM_CONFIG.downloadSupportBundle = function() {
    var dialogText = '';
    if (PWM_VAR['config_localDBLogLevel'] !== 'TRACE') {
        dialogText += PWM_CONFIG.showString("Warning_MakeSupportZipNoTrace");
        dialogText += '<br/><br/>';
    }
    dialogText += PWM_CONFIG.showString("Warning_DownloadSupportZip");

    PWM_MAIN.showConfirmDialog({
        text:dialogText,
        okAction:function(){
            PWM_MAIN.showWaitDialog({
                loadFunction: function () {
                    PWM_MAIN.gotoUrl('ConfigManager?processAction=generateSupportZip', {
                        addFormID: true,
                        hideDialog: true
                    });
                    setTimeout(function () {
                        PWM_MAIN.closeWaitDialog()
                    }, 5000);
                }
            });
        }
    });
};



PWM_CONFIG.heartbeatCheck = function() {
    var heartbeatFrequency = 10 * 1000;
    if (PWM_VAR['cancelHeartbeatCheck']) {
        console.log('heartbeat check cancelled');
        return;
    }
    if (typeof document['hidden'] !== "undefined" && document['hidden']) {
        console.log('skipping heartbeat check because page is not currently visible');
        setTimeout(PWM_CONFIG.heartbeatCheck,heartbeatFrequency);
        return;
    }

    require(["dojo","dijit/Dialog"],function() {
        /* make sure dialog js is loaded, server may not be available to load lazy */
    });

    console.log('beginning config-editor heartbeat check');
    var handleErrorFunction = function(message) {
        console.log('config-editor heartbeat failed');
        PWM_MAIN.showErrorDialog('There has been a problem communicating with the application server, please refresh your browser to continue.<br/><br/>' + message,{
            showOk:false
        });
    };
    var loadFunction = function(data) {
        try {
            var serverStartTime = data['data']['PWM_GLOBAL']['startupTime'];
            if (serverStartTime !== PWM_GLOBAL['startupTime']) {
                var message = "Application appears to have be restarted.";
                handleErrorFunction(message);
            } else {
                setTimeout(PWM_CONFIG.heartbeatCheck,heartbeatFrequency);
            }
        } catch (e) {
            handleErrorFunction('Error reading server status.');
        }
    };
    var errorFunction = function(e) {
        handleErrorFunction('I/O error communicating with server.');
    };
    var url = PWM_GLOBAL['url-context'] + "/public/api?processAction=clientData&heartbeat=true";
    url = PWM_MAIN.addParamToUrl(url,'pageUrl',window.location.href);
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,method:'GET'});
};

PWM_CONFIG.initConfigHeader = function() {
    PWM_MAIN.addEventHandler('panel-header-healthData','click',function(){
        PWM_MAIN.gotoUrl('/private/config/manager');
    });
    PWM_MAIN.addEventHandler('button-closeHeader','click',function(){
        PWM_CONFIG.closeHeaderWarningPanel();
    });
    PWM_MAIN.addEventHandler('button-openHeader','click',function(){
        PWM_CONFIG.openHeaderWarningPanel();
    });
    PWM_MAIN.addEventHandler('header-menu','click',function(){
        PWM_CONFIG.openHeaderWarningPanel();
    });

    var newElement = document.createElement('div');
    newElement.id =  "header-warning-backdrop";
    newElement.setAttribute('class','nodisplay');
    document.body.appendChild(newElement);

    /*
    require(["dojo/dom-construct", "dojo/_base/window", "dojo/dom", "dijit/place"], function(domConstruct, win, dom, place){
        PWM_MAIN.addEventHandler(window, "resize", function () {
            place.around(dom.byId("header-warning"), dom.byId("header-username-caret"), ["below-alt"], false);
        });
    });
     */

    PWM_CONFIG.showHeaderHealth();

    console.log('initConfigHeader completed');
};

PWM_CONFIG.initConfigManagerWordlistPage = function() {
    var uploadWordlist = function (type, label) {
        var uploadOptions = {};
        uploadOptions['url'] = 'wordlists?processAction=uploadWordlist&wordlist=' + type;
        uploadOptions['title'] = 'Upload ' + label;
        uploadOptions['text'] = PWM_CONFIG.showString('Display_UploadWordlist');
        uploadOptions['nextFunction'] = function () {
            PWM_MAIN.showDialog({
                title: 'Finished', text: 'Upload Completed', okAction: function () {
                    PWM_MAIN.gotoUrl('wordlists');
                }
            });
        };
        PWM_MAIN.IdleTimeoutHandler.cancelCountDownTimer();
        UILibrary.uploadFileDialog(uploadOptions);
    };
    var clearWordlist = function (type, label) {
        PWM_MAIN.showConfirmDialog({
            okAction: function () {
                PWM_MAIN.showWaitDialog({
                    loadFunction: function () {
                        var url = 'wordlists?processAction=clearWordlist&wordlist=' + type;
                        var loadFunction = function (data) {
                            PWM_MAIN.showDialog({
                                title: PWM_MAIN.showString('Title_Success'),
                                text: data['successMessage'], okAction: function () {
                                    PWM_MAIN.showWaitDialog({
                                        loadFunction: function(){
                                            PWM_MAIN.gotoUrl('wordlists');
                                        }
                                    });
                                }
                            });
                        };
                        PWM_MAIN.ajaxRequest(url, loadFunction);
                    }
                });
            }
        });
    };
    PWM_MAIN.addEventHandler('MenuItem_UploadWordlist', 'click', function () {
        uploadWordlist('WORDLIST', 'Wordlist');
    });
    PWM_MAIN.addEventHandler('MenuItem_UploadSeedlist', 'click', function () {
        uploadWordlist('SEEDLIST', 'Seedlist');
    });
    PWM_MAIN.addEventHandler('MenuItem_ClearWordlist', 'click', function () {
        clearWordlist('WORDLIST', 'Wordlist');
    });
    PWM_MAIN.addEventHandler('MenuItem_ClearSeedlist', 'click', function () {
        clearWordlist('SEEDLIST', 'Seedlist');
    });

    function refreshWordlistInfoTables() {
        var makeTableData = function (tableData, title) {
            var outputHtml = '';
            outputHtml += '<tr><td colspan="2" class="title">' + title + '</td></tr>';
            outputHtml += UILibrary.displayElementsToTableContents(tableData);
            return outputHtml;
        };
        var updateWordlistActionButtons = function (data) {
            var disabled;
            disabled = !data['WORDLIST']['allowUpload'];
            PWM_MAIN.setStyle('MenuItem_UploadWordlist','visibility', disabled ? 'hidden' : 'visible');

            disabled = !data['WORDLIST']['allowClear'];
            PWM_MAIN.setStyle('MenuItem_ClearWordlist','visibility', disabled ? 'hidden' : 'visible');

            disabled = !data['SEEDLIST']['allowUpload'];
            PWM_MAIN.setStyle('MenuItem_UploadSeedlist','visibility', disabled ? 'hidden' : 'visible');

            disabled = !data['SEEDLIST']['allowClear'];
            PWM_MAIN.setStyle('MenuItem_ClearSeedlist','visibility', disabled ? 'hidden' : 'visible');
        };
        var dataHandler = function (data) {
            var wordlistData = data['data']['WORDLIST']['presentableData'];
            PWM_MAIN.getObject('table-wordlistInfo').innerHTML = makeTableData(
                wordlistData,
                PWM_CONFIG.showString('Label_Wordlist')
            );
            UILibrary.initElementsToTableContents(wordlistData);
            var seedlistData = data['data']['SEEDLIST']['presentableData'];
            PWM_MAIN.getObject('table-seedlistInfo').innerHTML = makeTableData(
                seedlistData,
                PWM_CONFIG.showString('Label_Seedlist')
            );
            UILibrary.initElementsToTableContents(seedlistData);
            updateWordlistActionButtons(data['data']);
        };
        var errorHandler = function(data) {
            console.log('error during info refresh: ' + data);
        };
        PWM_MAIN.ajaxRequest('wordlists?processAction=readWordlistData', dataHandler,{errorFunction:errorHandler});
    }

    setInterval(refreshWordlistInfoTables, 5000);
    refreshWordlistInfoTables();
};


PWM_CONFIG.convertListOfIdentitiesToHtml = function(data) {
    var html = '<div class="panel-large overflow-y">';
    var users = data['users'];
    if (users && !PWM_MAIN.JSLibrary.isEmpty(users)) {
        html += '<table style="">';
        html += '<thead><tr><td class="title" style="width: 75px">' + PWM_MAIN.showString('Field_LdapProfile') + '</td>';
        html += '<td class="title" style="max-width: 375px">'+ PWM_MAIN.showString('Field_UserDN') + '</td></tr></thead>';
        html += '<tbody>';
        for (var iter in users) {
            var userIdentity = users[iter];
            html += '<tr ><td style="width: 75px">' + userIdentity['ldapProfile'] + '</td><td title="' + userIdentity['userDN'] + '">';
            html += '<div style="max-width: 375px; white-space: nowrap; overflow:hidden; text-overflow: ellipsis;">' + userIdentity['userDN'] + '</div></td></tr>';
        }
        html += '</tbody></table>';
    } else {
        html += PWM_MAIN.showString('Display_SearchResultsNone');
    }
    html += '</div>';

    html += '<br/><div class="footnote"><p>' + data['searchOperationSummary'] + '</p>';
    if (data['sizeExceeded']) {
        html += '<p>' + PWM_CONFIG.showString('Display_EditorLDAPSizeExceeded') + '</p>';
    }
    html += '</div>';
    return html;
};

PWM_CONFIG.configClosedWarning = function() {
    PWM_MAIN.showDialog({
        title:PWM_MAIN.showString('Title_Error'),
        text:"This operation is not available when the configuration is restricted."
    });
};
