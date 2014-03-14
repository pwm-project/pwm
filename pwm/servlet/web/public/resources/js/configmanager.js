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

"use strict";

var PWM_SETTINGS = PWM_SETTINGS || {};
var PWM_CONFIG = PWM_CONFIG || {};

PWM_CONFIG.lockConfiguration=function() {
    PWM_MAIN.showConfirmDialog(null,PWM_SETTINGS['display']['Confirm_LockConfig'],function(){
        PWM_MAIN.showWaitDialog(null,null,function(){
            require(["dojo"],function(dojo){
                dojo.xhrGet({
                    url:"ConfigManager?processAction=lockConfiguration&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    sync:true,
                    dataType: "json",
                    handleAs: "json",
                    preventCache: true,
                    load: function(data) {
                        if (data['error'] == true) {
                            PWM_MAIN.closeWaitDialog();
                            PWM_MAIN.showDialog('Error',data['errorDetail']);
                        } else {
                            PWM_MAIN.showWaitDialog();
                            PWM_MAIN.showError('Waiting for server restart');
                            PWM_CONFIG.waitForRestart(new Date().getTime());
                        }
                    }
                });
            });
        });
    });
};

PWM_CONFIG.waitForRestart=function(startTime) {
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
                    PWM_MAIN.clearDijitWidget('waitDialogID');
                    PWM_MAIN.showError(data['errorDetail']);
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
                    PWM_MAIN.showError('Server has not restarted (timeout)');
                } else {
                    PWM_MAIN.showError('Waiting for server restart, server has not yet restarted (' + (diff) + ' ms)');
                    setTimeout(function() {
                        PWM_CONFIG.waitForRestart(startTime)
                    }, Math.random() * 1000);
                }
            },
            error: function(error) {
                setTimeout(function() {
                    PWM_CONFIG.waitForRestart(startTime)
                }, 1000);
                console.log('Waiting for server restart, unable to contact server: ' + error);
            }
        });
    });
};

PWM_CONFIG.startNewConfigurationEditor=function(template) {
    PWM_MAIN.showWaitDialog('Loading...','');
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
}

PWM_CONFIG.startConfigurationEditor=function() {
    require(["dojo"],function(dojo){
        if(dojo.isIE <= 8){ // only IE8 and below
            alert('Internet Explorer 8 and below is not able to edit the configuration.  Please use a newer version of Internet Explorer or a different browser.');
            document.forms['cancelEditing'].submit();
        } else {
            PWM_MAIN.goto('/private/config/ConfigEditor');
        }
    });
};


PWM_CONFIG.uploadConfigDialog=function() {
    var body = '<div id="uploadFormWrapper"><form action="ConfigGuide" enctype="multipart/form-data">';
    body += '<div id="fileList"></div>';
    body += '<input name="uploadFile" type="file" label="Select File" id="uploadFile"/>';
    body += '<input type="submit" id="uploadButton" name="Upload"/>';
    body += '</form></div>';

    var uploadUrl = window.location.pathname + '?processAction=uploadConfig&pwmFormID=' + PWM_GLOBAL['pwmFormID'];


    require(["dojo","dijit/Dialog","dojox/form/Uploader","dojox/form/uploader/FileList","dijit/form/Button","dojox/form/uploader/plugins/HTML5"],function(
        dojo,Dialog,Uploader,FileList,Button){

        if(dojo.isIE <= 9){ // IE9 and below no workie
            var dialogText = PWM_CONFIG.showString("Warning_UploadIE9");
            var headerText = PWM_MAIN.showString("Title_Error");
            PWM_MAIN.showDialog(headerText,dialogText);
            return;
        }

        PWM_MAIN.showWaitDialog(null,null,function(){
            console.log('uploading config file to url ' + uploadUrl);
            PWM_MAIN.closeWaitDialog();
            var idName = 'dialogPopup';
            PWM_MAIN.clearDijitWidget(idName);
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
                    PWM_MAIN.showDialog('Upload Error', data['errorDetail']);
                } else {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.clearDijitWidget(idName);
                    PWM_MAIN.showWaitDialog(null,null,function(){
                        PWM_CONFIG.waitForRestart(new Date().getTime());
                    });
                }
            });
        });
    });
};

PWM_CONFIG.initConfigPage=function(nextFunction) {
    require(["dojo"],function(dojo){
        var clientConfigUrl = PWM_GLOBAL['url-context'] + "/public/rest/app-data/client-config";
        dojo.xhrGet({
            url: clientConfigUrl,
            handleAs: 'json',
            timeout: 30 * 1000,
            headers: {"Accept":"application/json","X-RestClientKey":PWM_GLOBAL['restClientKey']},
            load: function(data) {
                if (data['error'] == true) {
                    alert('unable to load ' + clientConfigUrl + ', error: ' + data['errorDetail'])
                } else {
                    for (var settingKey in data['data']) {
                        PWM_SETTINGS[settingKey] = data['data'][settingKey];
                    }
                }
                if (nextFunction) {
                    nextFunction();
                }
            },
            error: function(error) {
                PWM_MAIN.showError('Unable to read settings app-data from server, please reload page (' + error + ')');
                console.log('unable to read settings app-data: ' + error);
            }
        });
    });
};

PWM_CONFIG.showString=function (key) {
    if (PWM_SETTINGS['display'] && PWM_SETTINGS['display'][key]) {
        return PWM_SETTINGS['display'][key];
    } else {
        return "UNDEFINED STRING-" + key;
    }
};

PWM_CONFIG.openLogViewer=function(level) {
    var windowUrl = PWM_GLOBAL['url-context'] + '/public/CommandServlet?processAction=viewLog' + ((level) ? '&level=' + level : '');
    var windowParams = 'status=0,toolbar=0,location=0,menubar=0,scrollbars=1,resizable=1';
    var viewLog = window.open(windowUrl,'logViewer',windowParams).focus();
};

