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

"use strict";

var PWM_CONFIG = PWM_CONFIG || {};
var PWM_GLOBAL = PWM_GLOBAL || {};

PWM_CONFIG.lockConfiguration=function() {
    PWM_MAIN.showConfirmDialog({text:PWM_CONFIG.showString('Confirm_LockConfig'),okAction:function(){
        PWM_MAIN.showWaitDialog({loadFunction:function() {
            var url = 'ConfigManager?processAction=lockConfiguration';
            var loadFunction = function(data) {
                if (data['error'] == true) {
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
    PWM_VAR['cancelHeartbeatCheck'] = true;

    options = options === undefined ? {} : options;
    console.log("beginning request to determine application status: ");
    var loadFunction = function(data) {
        try {
            var serverStartTime = data['data']['PWM_GLOBAL']['startupTime'];
            if (serverStartTime != PWM_GLOBAL['startupTime']) {
                console.log("application appears to be restarted, redirecting to context url: ");
                var redirectUrl = 'location' in options ? options['location'] : '/';
                PWM_MAIN.goto(redirectUrl);
                return;
            }
        } catch (e) {
            console.log("can't read current server startupTime, will retry detection (current error: " + e + ")");
        }
        setTimeout(function() {
            PWM_CONFIG.waitForRestart(options)
        }, Math.random() * 3000);
    };
    var errorFunction = function(error) {
        setTimeout(function() {
            PWM_CONFIG.waitForRestart(options)
        }, 3000);
        console.log('Waiting for server restart, unable to contact server: ' + error);
    };
    var url = PWM_GLOBAL['url-restservice'] + "/app-data/client?checkForRestart=true";
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,method:'GET'});
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

PWM_CONFIG.startConfigurationEditor=function() {
    require(["dojo"],function(dojo){
        if(dojo.isIE <= 8){ // only IE8 and below
            alert('Internet Explorer 8 and below is not able to edit the configuration.  Please use a newer version of Internet Explorer or a different browser.');
            document.forms['cancelEditing'].submit();
        } else {
            PWM_MAIN.goto('/private/config/editor');
        }
    });
};


PWM_CONFIG.uploadConfigDialog=function() {
    var uploadOptions = {};
    uploadOptions['url'] = window.location.pathname + '?processAction=uploadConfig';
    uploadOptions['title'] = 'Upload Configuration';
    uploadOptions['nextFunction'] = function() {
        PWM_MAIN.showWaitDialog({title:'Save complete, restarting application...',loadFunction:function(){
            PWM_CONFIG.waitForRestart({location:'/'});
        }});
    };
    UILibrary.uploadFileDialog(uploadOptions);
};

PWM_CONFIG.uploadLocalDB=function() {
    PWM_MAIN.showConfirmDialog({
        text:PWM_CONFIG.showString('Confirm_UploadLocalDB'),
        okAction:function(){
            var uploadOptions = {};
            uploadOptions['url'] = 'localdb?processAction=importLocalDB';
            uploadOptions['title'] = 'Upload and Import LocalDB Archive';
            uploadOptions['nextFunction'] = function() {
                PWM_MAIN.showWaitDialog({title:'Save complete, restarting application...',loadFunction:function(){
                    PWM_CONFIG.waitForRestart({location:'/'});
                }});
            };
            PWM_MAIN.IdleTimeoutHandler.cancelCountDownTimer();
            UILibrary.uploadFileDialog(uploadOptions);
        }
    });
};

PWM_CONFIG.closeHeaderWarningPanel = function() {
    console.log('action closeHeader');
    PWM_MAIN.setStyle('header-warning','display','none');
    PWM_MAIN.setStyle('button-openHeader','display','inherit');
    PWM_MAIN.Preferences.writeSessionStorage('headerVisibility','hide');
};

PWM_CONFIG.openHeaderWarningPanel = function() {
    console.log('action openHeader');
    PWM_MAIN.setStyle('header-warning','display','inherit');
    PWM_MAIN.setStyle('button-openHeader','display','none');
    PWM_MAIN.Preferences.writeSessionStorage('headerVisibility','show');
};



PWM_CONFIG.showString=function (key, options) {
    options = options === undefined ? {} : options;
    options['bundle'] = 'Config';
    return PWM_MAIN.showString(key,options);

};

PWM_CONFIG.openLogViewer=function(level) {
    var windowUrl = PWM_GLOBAL['url-context'] + '/private/admin/Administration?processAction=viewLogWindow' + ((level) ? '&level=' + level : '');
    var windowName = 'logViewer';
    PWM_MAIN.newWindowOpen(windowUrl,windowName);
};

PWM_CONFIG.showHeaderHealth = function() {
    var refreshUrl = PWM_GLOBAL['url-restservice'] + "/health";
    var parentDiv = PWM_MAIN.getObject('panel-header-healthData');
    if (!parentDiv) {
        return;
    }
    var headerDiv = PWM_MAIN.getObject('header-warning');
    if (parentDiv && headerDiv) {
        var loadFunction = function(data) {
            if (data['data'] && data['data']['records']) {
                var healthRecords = data['data']['records'];
                var hasWarnTopics = false;
                for (var i = 0; i < healthRecords.length; i++) {
                    var healthData = healthRecords[i];
                    if (healthData['status'] == 'WARN') {
                        hasWarnTopics = true;
                    }
                }
                if (hasWarnTopics) {
                    PWM_MAIN.addCssClass('button-openHeader','blink');
                    PWM_MAIN.setStyle('button-openHeader','color','red');

                    parentDiv.innerHTML = '<div id="panel-healthHeaderErrors" class="header-error"><span class="fa fa-warning"></span> ' + PWM_ADMIN.showString('Header_HealthWarningsPresent') + '</div>';
                } else {
                    PWM_MAIN.removeCssClass('button-openHeader','blink');
                    PWM_MAIN.setStyle('button-openHeader','color');
                }
                setTimeout(function () {
                    PWM_CONFIG.showHeaderHealth()
                }, 60 * 1000);
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
                    PWM_MAIN.goto('localdb?processAction=exportLocalDB',{addFormID:true,hideDialog:true});
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
                    PWM_MAIN.goto('ConfigManager?processAction=downloadConfig',{addFormID:true,hideDialog:true});
                    setTimeout(function(){PWM_MAIN.closeWaitDialog()},5000);
                }
            });

        }
    });
};

PWM_CONFIG.downloadSupportBundle = function() {
    var dialogText = '';
    if (PWM_VAR['config_localDBLogLevel'] != 'TRACE') {
        dialogText += PWM_CONFIG.showString("Warning_MakeSupportZipNoTrace");
        dialogText += '<br/><br/>';
    }
    dialogText += PWM_CONFIG.showString("Warning_DownloadSupportZip");

    PWM_MAIN.showConfirmDialog({
        text:dialogText,
        okAction:function(){
            PWM_MAIN.showWaitDialog({
                loadFunction: function () {
                    PWM_MAIN.goto('ConfigManager?processAction=generateSupportZip', {
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
            if (serverStartTime != PWM_GLOBAL['startupTime']) {
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
    var url = PWM_GLOBAL['url-restservice'] + "/app-data/client?heartbeat=true";
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,method:'GET'});
};

PWM_CONFIG.initConfigHeader = function() {
    // header initialization
    if (PWM_MAIN.getObject('header_configEditorButton')) {
        PWM_MAIN.addEventHandler('header_configEditorButton', 'click', function () {
            PWM_CONFIG.startConfigurationEditor();
        });
    }
    PWM_MAIN.addEventHandler('header_openLogViewerButton', 'click', function () {
        PWM_CONFIG.openLogViewer(null)
    });
    PWM_MAIN.addEventHandler('panel-header-healthData','click',function(){
        PWM_MAIN.goto('/private/config/ConfigManager');
    });
    PWM_MAIN.addEventHandler('button-closeHeader','click',function(){
        PWM_CONFIG.closeHeaderWarningPanel();
    });
    PWM_MAIN.addEventHandler('button-openHeader','click',function(){
        PWM_CONFIG.openHeaderWarningPanel();
    });

    PWM_CONFIG.showHeaderHealth();

    if (PWM_MAIN.Preferences.readSessionStorage('headerVisibility') != 'hide') {
        PWM_CONFIG.openHeaderWarningPanel();
    }

    console.log('initConfigHeader completed');
};

PWM_CONFIG.initConfigManagerWordlistPage = function() {
    var uploadWordlist = function (type, label) {
        var uploadOptions = {};
        uploadOptions['url'] = 'wordlists?processAction=uploadWordlist&wordlist=' + type;
        uploadOptions['title'] = 'Upload ' + label;
        uploadOptions['nextFunction'] = function () {
            PWM_MAIN.showDialog({
                title: 'Finished', text: 'Upload Completed', okAction: function () {
                    PWM_MAIN.goto('wordlists');
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
                                text: data['successMessage'], okAction: function () {
                                    PWM_MAIN.goto('wordlists');
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

    setInterval(refreshWordlistInfoTables, 5000);
    refreshWordlistInfoTables();

    function refreshWordlistInfoTables() {
        var makeTableData = function (tableData, title) {
            var outputHtml = '';
            outputHtml += '<tr><td colspan="2" class="title">' + title + '</td></tr>';
            for (var iter in tableData) {
                (function (label) {
                    var value = tableData[label];
                    outputHtml += '<tr><td class="key">' + label + '</td><td class="';
                    PWM_MAIN.TimestampHandler.testIfStringIsTimestamp(value,function(){outputHtml += 'timestamp'});
                    outputHtml += '">' + value + '</td></tr>';
                })(iter);
            }
            return outputHtml;
        };
        var updateWordlistActionButtons = function (data) {
            var disabled;
            disabled = !data['WORDLIST']['completed'];
            PWM_MAIN.setStyle('MenuItem_UploadWordlist','visibility', disabled ? 'hidden' : 'visible');

            disabled = !(data['WORDLIST']['completed'] && !(data['WORDLIST']['builtIn']));
            PWM_MAIN.setStyle('MenuItem_ClearWordlist','visibility', disabled ? 'hidden' : 'visible');

            disabled = !data['SEEDLIST']['completed'];
            PWM_MAIN.setStyle('MenuItem_UploadSeedlist','visibility', disabled ? 'hidden' : 'visible');

            disabled = !(data['SEEDLIST']['completed'] && !(data['SEEDLIST']['builtIn']));
            PWM_MAIN.setStyle('MenuItem_ClearSeedlist','visibility', disabled ? 'hidden' : 'visible');
        };
        var dataHandler = function (data) {
            PWM_MAIN.getObject('table-wordlistInfo').innerHTML = makeTableData(data['data']['WORDLIST']['presentableData'], 'Wordlist');
            PWM_MAIN.getObject('table-seedlistInfo').innerHTML = makeTableData(data['data']['SEEDLIST']['presentableData'], 'Seedlist');
            PWM_MAIN.TimestampHandler.initAllElements();
            updateWordlistActionButtons(data['data']);
        };
        PWM_MAIN.ajaxRequest('wordlists?processAction=readWordlistData', dataHandler);
    }
};


PWM_CONFIG.convertListOfIdentitiesToHtml = function(data) {
    var html = '<div style="max-height: 500px; overflow-y: auto">';
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

    html += '<br/><div class="noticebar" style="margin-right: 5px; margin-left: 5px">' + data['searchOperationSummary'];
    if (data['sizeExceeded']) {
        html += ' ' + PWM_CONFIG.showString('Display_EditorLDAPSizeExceeded');
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

