/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

var PWM_SETTINGS = {};


function lockConfiguration() {
    showConfirmDialog(null,PWM_SETTINGS['display']['Confirm_LockConfig'],function(){
        showWaitDialog(null,null,function(){
            require(["dojo"],function(dojo){
                dojo.xhrGet({
                    url:"ConfigManager?processAction=lockConfiguration&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    sync:true,
                    dataType: "json",
                    handleAs: "json",
                    preventCache: true,
                    load: function(data) {
                        if (data['error'] == true) {
                            closeWaitDialog();
                            showDialog('Error',data['errorDetail']);
                        } else {
                            showWaitDialog();
                            showError('Waiting for server restart');
                            waitForRestart(new Date().getTime());
                        }
                    }
                });
            });
        });
    });
}


function waitForRestart(startTime) {
    require(["dojo"],function(dojo){
        var currentTime = new Date().getTime();
        dojo.xhrGet({
            url: PWM_GLOBAL['url-restservice'] + "/app-data/client/reload",
            preventCache: true,
            timeout: 10 * 1000,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                if (data['error'] == true) {
                    clearDijitWidget('waitDialogID');
                    showError(data['errorDetail']);
                    return;
                }

                var serverStartTime = data['data']['PWM_GLOBAL']['startupTime'];
                if (serverStartTime != PWM_GLOBAL['startupTime']) {
                    window.location = PWM_GLOBAL['url-context'];
                    return;
                }
                var diff = currentTime - startTime;
                //console.log('oldEpoch=' + oldEpoch + ", currentEpoch=" + epoch + ", difftime=" + diff);
                if (diff > 4 * 60 * 1000) { // timeout
                    alert('Unable to restart, please restart the java application server.');
                    showError('Server has not restarted (timeout)');
                } else {
                    showError('Waiting for server restart, server has not yet restarted (' + (diff) + ' ms)');
                    setTimeout(function() {
                        waitForRestart(startTime)
                    }, Math.random() * 1000);
                }
            },
            error: function(error) {
                setTimeout(function() {
                    waitForRestart(startTime)
                }, 1000);
                console.log('Waiting for server restart, unable to contact server: ' + error);
            }
        });
    });
}

function startNewConfigurationEditor(template) {
    showWaitDialog('Loading...','');
    require(["dojo"],function(dojo){
        dojo.xhrGet({
            url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&getTemplate=" + template,
            preventCache: true,
            error: function(errorObj) {
                showError("error starting configuration editor: " + errorObj);
            },
            load: function() {
                window.location = "ConfigManager?processAction=editMode&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + '&mode=SETTINGS';
            }
        });
    });
}

function startConfigurationEditor() {
    require(["dojo"],function(dojo){
        if(dojo.isIE <= 7){ // only IE8 and below
            alert('Internet Explorer 7 and below is not able to edit the configuration.  Please use a newer version of Internet Explorer or a different browser.');
            document.forms['cancelEditing'].submit();
        } else {
            showWaitDialog('Loading...','',function(){
                window.location = "ConfigManager?processAction=startEditing&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
            });
        }
    });
}

function readInitialTextBasedValue(key) {
    require(["dijit/registry"],function(registry){
        readSetting(key, function(dataValue) {
            getObject('value_' + key).value = dataValue;
            getObject('value_' + key).disabled = false;
            registry.byId('value_' + key).set('disabled', false);
            registry.byId('value_' + key).startup();
            try {registry.byId('value_' + key).validate(false);} catch (e) {}
            try {registry.byId('value_verify_' + key).validate(false);} catch (e) {}
        });
    });
}

function uploadConfigDialog() {
    var body = '<div id="uploadFormWrapper"><form action="ConfigGuide" enctype="multipart/form-data">';
    body += '<div id="fileList"></div>';
    body += '<input name="uploadFile" type="file" label="Select File" id="uploadFile"/>';
    body += '<input type="submit" id="uploadButton" name="Upload"/>';
    body += '</form></div>';

    var uploadUrl = window.location.pathname + '?processAction=uploadConfig&pwmFormID=' + PWM_GLOBAL['pwmFormID'];
    console.log('uploading config file to url ' + uploadUrl);

    showWaitDialog(null,null,function(){
        closeWaitDialog();
        require(["dojo","dijit/Dialog","dojox/form/Uploader","dojox/form/uploader/FileList","dijit/form/Button","dojox/form/uploader/plugins/HTML5"],function(
            dojo,Dialog,Uploader,FileList,Button){
            var idName = 'dialogPopup';
            clearDijitWidget(idName);
            var theDialog = new Dialog({
                id: idName,
                title: 'Upload Configuration',
                style: "width: 300px",
                content: body
            });
            theDialog.show();
            var fileMask = [
                ["XML File", 	"*.xml"],
                ["TXT File", 	"*.txt"]
            ];
            var uploader = new dojox.form.Uploader({
                multiple: false,
                name: "uploadFile",
                label: 'Select File',
                required:true,
                fileMask: fileMask,
                url: uploadUrl,
                isDebug: true,
                devMode: true
            },'uploadFile');
            uploader.startup();
            var uploadButton = new Button({
                label: 'Upload',
                type: 'submit'
            },"uploadButton");
            uploadButton.startup();
            new FileList({
                uploaderId: 'uploadFile'
            },"fileList")
            dojo.connect(uploader, "onComplete", function(data){
                if (data['error'] == true) {
                    showDialog('Upload Error', data['errorDetail']);
                } else {
                    closeWaitDialog();
                    clearDijitWidget(idName);
                    showWaitDialog(null,null,function(){
                        waitForRestart(new Date().getTime());
                    });
                }
            });
        });
    });
}

function initConfigPage() {
    require(["dojo"],function(dojo){
        var clientConfigUrl = PWM_GLOBAL['url-context'] + "/public/rest/app-data/client-config";
        dojo.xhrGet({
            url: clientConfigUrl,
            handleAs: 'json',
            timeout: 30 * 1000,
            headers: { "Accept": "application/json" },
            load: function(data) {
                if (data['error'] == true) {
                    alert('unable to load ' + clientConfigUrl + ', error: ' + data['errorDetail'])
                } else {
                    for (var settingKey in data['data']) {
                        PWM_SETTINGS[settingKey] = data['data'][settingKey];
                    }
                    pwmPageLoadHandler();
                }
            },
            error: function(error) {
                showError('Unable to read settings app-data from server, please reload page (' + error + ')');
                console.log('unable to read settings app-data: ' + error);
                pwmPageLoadHandler();
            }
        });
    });
}

function openLogViewer(level) {
    if (!level) {
        level = 'INFO';
    }
    var windowUrl = PWM_GLOBAL['url-context'] + '/public/CommandServlet?processAction=viewLog&level=' + level;
    var windowParams = 'status=0,toolbar=0,location=0,menubar=0,scrollbars=1,resizable=1';
    var viewLog = window.open(windowUrl,'logViewer',windowParams).focus();
}