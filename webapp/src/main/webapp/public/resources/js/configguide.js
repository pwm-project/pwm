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

var PWM_GUIDE = PWM_GUIDE || {};
var PWM_MAIN = PWM_MAIN || {};
var PWM_GLOBAL = PWM_GLOBAL || {};

PWM_GUIDE.selectTemplate = function(template) {
    PWM_MAIN.showWaitDialog({title:'Loading...',loadFunction:function() {
            var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','selectTemplate');
            url = PWM_MAIN.addParamToUrl(url, 'template', template);
            PWM_MAIN.showDialog(url,function(result){
                if (!result['error']) {
                    PWM_MAIN.getObject('button_next').disabled = template === "NOTSELECTED";
                    PWM_MAIN.closeWaitDialog();
                } else {
                    PWM_MAIN.showError(result['errorDetail']);
                }

            },{method:'GET'});
        }});
};

PWM_GUIDE.updateForm = function() {
    var formJson = PWM_MAIN.JSLibrary.formToValueMap('configForm');
    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','updateForm');
    var loadFunction = function() {
        PWM_MAIN.log("sent form params to server: " + formJson);
    }
    PWM_MAIN.ajaxRequest(url,loadFunction,{content:formJson});
};

PWM_GUIDE.gotoStep = function(step) {
    PWM_MAIN.showWaitDialog({loadFunction:function(){
            //preload in case of server restart
            PWM_MAIN.preloadAll(function(){
                var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','gotoStep');
                url = PWM_MAIN.addParamToUrl(url, 'step', step);
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
                    PWM_MAIN.gotoUrl('config-guide');
                };
                PWM_MAIN.ajaxRequest(url,loadFunction);
            });
        }});
};

PWM_GUIDE.setUseConfiguredCerts = function(value) {
    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','useConfiguredCerts');
    url = PWM_MAIN.addParamToUrl(url, 'value', value);
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
                    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','extendSchema');
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

PWM_GUIDE.skipGuide = function() {
    PWM_MAIN.preloadAll(function(){
        PWM_MAIN.showConfirmDialog({text:PWM_CONFIG.showString('Confirm_SkipGuide'),okAction:function() {

                var skipGuideFunction = function(password) {
                    var contents = {};
                    contents['password'] = password;
                    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','skipGuide');
                    var loadFunction = function(result) {
                        if (result['error']) {
                            PWM_MAIN.showError(result['errorDetail']);
                        } else {
                            PWM_MAIN.showWaitDialog({loadFunction:function(){
                                    PWM_CONFIG.waitForRestart();
                                }});
                        }
                    };
                    PWM_MAIN.ajaxRequest(url,loadFunction,{content:contents});
                };

                var text = 'Set Configuration Password';
                UILibrary.passwordDialogPopup({minimumLength:8, title:text, writeFunction:skipGuideFunction});
            }});
    });
};
