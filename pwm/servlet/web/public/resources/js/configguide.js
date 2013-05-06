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

function selectTemplate(template) {
    showWaitDialog('Loading...','',function(){
        require(["dojo"],function(dojo){
            dojo.xhrGet({
                url:"ConfigGuide?processAction=selectTemplate&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&template=" + template,
                preventCache: true,
                error: function(errorObj) {
                    showError("error starting configuration editor: " + errorObj);
                },
                load: function(result) {
                    if (!result['error']) {
                        closeWaitDialog();
                    } else {
                        showError(result['errorDetail']);
                    }
                }
            });
        });
    });
}

function updateForm() {
    require(["dojo","dijit/registry","dojo/dom-form"],function(dojo,registry,domForm){
        var formJson = dojo.formToJson('configForm');
        dojo.xhrPost({
            url: "ConfigGuide?processAction=updateForm&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            postData: formJson,
            headers: {"Accept":"application/json"},
            contentType: "application/json;charset=utf-8",
            encoding: "utf-8",
            handleAs: "json",
            dataType: "json",
            preventCache: true,
            error: function(errorObj) {
                showError("error reaching server: " + errorObj);
            },
            load: function(result) {
                console.log("sent form params to server: " + formJson);
            }
        });
    });
}

function gotoStep(step) {
    showWaitDialog();
    require(["dojo"],function(dojo){
        dojo.xhrGet({
            url: "ConfigGuide?processAction=gotoStep&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&step=" + step,
            headers: {"Accept":"application/json"},
            contentType: "application/json;charset=utf-8",
            encoding: "utf-8",
            handleAs: "json",
            dataType: "json",
            preventCache: true,
            error: function(errorObj) {
                closeWaitDialog();
                showError("error while selecting step: " + errorObj);
            },
            load: function(result) {
                if (result['data']) {
                    if (result['data']['serverRestart']) {
                        waitForRestart(new Date().getTime(),"none");
                        return;
                    }
                }
                var redirectLocation = "ConfigGuide";
                location = redirectLocation;
            }
        });
    });
}

function setUseConfiguredCerts(value) {
    showWaitDialog('Loading...','',function(){
        require(["dojo"],function(dojo){
            dojo.xhrGet({
                url:"ConfigGuide?processAction=useConfiguredCerts&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&value=" + value,
                preventCache: true,
                error: function(errorObj) {
                    showError("error starting configuration editor: " + errorObj);
                },
                load: function(result) {
                    if (!result['error']) {
                        window.location = "ConfigGuide";
                    } else {
                        showError(result['errorDetail']);
                    }
                }
            });
        });

    });
}

function uploadConfigDialog() {
    var body = '<div id="uploadFormWrapper"><form action="ConfigGuide" enctype="multipart/form-data">';
    body += '<div id="fileList"></div>';
    body += '<input name="uploadFile" type="file" label="Select File" id="uploadFile"/>';
    body += '<input type="submit" id="uploadButton" name="Upload"/>';
    body += '</form></div>';

    require(["dojo","dijit/Dialog","dojox/form/Uploader","dojox/form/uploader/FileList","dijit/form/Button","dojox/form/uploader/FileList","dojox/form/uploader/plugins/HTML5"],function(
        dojo,Dialog,Uploader,FileList,Button,FileList){
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
            url: 'ConfigGuide' + '?processAction=uploadConfig&pwmFormID=' + PWM_GLOBAL['pwmFormID'],
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
                showWaitDialog(null,null,function(){
                    setTimeout(function(){
                        location = PWM_GLOBAL['url-context'];
                    },10 * 1000);
                });
            }
        });
    });
}
