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

function selectTemplate(template) {
    PWM_MAIN.showWaitDialog('Loading...','',function(){
        require(["dojo"],function(dojo){
            dojo.xhrGet({
                url:"ConfigGuide?processAction=selectTemplate&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&template=" + template,
                preventCache: true,
                error: function(errorObj) {
                    PWM_MAIN.showError("error starting configuration editor: " + errorObj);
                },
                load: function(result) {
                    if (!result['error']) {
                        PWM_MAIN.getObject('button_next').disabled = template == "NOTSELECTED";
                        PWM_MAIN.closeWaitDialog();
                    } else {
                        PWM_MAIN.showError(result['errorDetail']);
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
                PWM_MAIN.showError("error reaching server: " + errorObj);
            },
            load: function(result) {
                console.log("sent form params to server: " + formJson);
            }
        });
    });
}

function gotoStep(step) {
    PWM_MAIN.showWaitDialog();
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
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.showError("error while selecting step: " + errorObj);
            },
            load: function(result) {
                if (result['data']) {
                    if (result['data']['serverRestart']) {
                        PWM_CONFIG.waitForRestart(new Date().getTime(),"none");
                        return;
                    }
                }
                var redirectLocation = "ConfigGuide";
                window.location = redirectLocation;
            }
        });
    });
}

function setUseConfiguredCerts(value) {
    PWM_MAIN.showWaitDialog('Loading...','',function(){
        require(["dojo"],function(dojo){
            dojo.xhrGet({
                url:"ConfigGuide?processAction=useConfiguredCerts&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&value=" + value,
                preventCache: true,
                error: function(errorObj) {
                    PWM_MAIN.showError("error starting configuration editor: " + errorObj);
                },
                load: function(result) {
                    if (!result['error']) {
                        window.location = "ConfigGuide";
                    } else {
                        PWM_MAIN.showError(result['errorDetail']);
                    }
                }
            });
        });

    });
}
