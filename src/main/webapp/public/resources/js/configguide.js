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

var PWM_GUIDE = PWM_GUIDE || {};
var PWM_MAIN = PWM_MAIN || {};
var PWM_GLOBAL = PWM_GLOBAL || {};

PWM_GUIDE.selectTemplate = function(template) {
    PWM_MAIN.showWaitDialog({title:'Loading...',loadFunction:function() {
        var url = "config-guide?processAction=selectTemplate&template=" + template;
        PWM_MAIN.showDialog(url,function(result){
            if (!result['error']) {
                PWM_MAIN.getObject('button_next').disabled = template == "NOTSELECTED";
                PWM_MAIN.closeWaitDialog();
            } else {
                PWM_MAIN.showError(result['errorDetail']);
            }

        },{method:'GET'});
    }});
};

PWM_GUIDE.updateForm = function() {
    require(["dojo","dijit/registry","dojo/dom-form"],function(dojo,registry,domForm){
        var formJson = dojo.formToJson('configForm');
        dojo.xhrPost({
            url: "config-guide?processAction=updateForm&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
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
};

PWM_GUIDE.gotoStep = function(step) {
    PWM_MAIN.showWaitDialog({loadFunction:function(){
        //preload in case of server restart
        PWM_MAIN.preloadAll(function(){
            var url =  "config-guide?processAction=gotoStep&step=" + step;
            var loadFunction = function(result) {
                if (result['error']) {
                    PWM_MAIN.showErrorDialog(result);
                    return;
                } else if (result['data']) {
                    if (result['data']['serverRestart']) {
                        PWM_CONFIG.waitForRestart();
                        return;
                    }
                }
                PWM_MAIN.goto('config-guide');
            };
            PWM_MAIN.ajaxRequest(url,loadFunction);
        });
    }});
};

PWM_GUIDE.setUseConfiguredCerts = function(value) {
    var url = "config-guide?processAction=useConfiguredCerts&value=" + value;
    var loadFunction = function(result) {
        if (result['error']) {
            PWM_MAIN.showError(result['errorDetail']);
        }
    };
    PWM_MAIN.ajaxRequest(url,loadFunction);
};

PWM_GUIDE.extendSchema = function() {
    PWM_MAIN.showConfirmDialog({text:"Are you sure you want to extend the LDAP schema?",okAction:function(){
        PWM_MAIN.showWaitDialog({loadFunction:function() {
            var url = "config-guide?processAction=extendSchema";
            var loadFunction = function(result) {
                if (result['error']) {
                    PWM_MAIN.showError(result['errorDetail']);
                } else {
                    var output = '<pre>' + result['data'] + '</pre>';
                    PWM_MAIN.showDialog({title:"Results",text:output,okAction:function(){
                        window.location.reload();
                    }});
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction);
        }});
    }});
};