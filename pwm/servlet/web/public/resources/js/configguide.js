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
                        getObject('button_next').disabled = template == "NOTSELECTED";
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
