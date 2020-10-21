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

var PWM_GLOBAL = PWM_GLOBAL || {};
var PWM_MAIN = PWM_MAIN || {};
var PWM_VAR = PWM_VAR || {};

var PWM_UPDATE = PWM_UPDATE || {};

PWM_UPDATE.validateForm = function() {
    var validationProps = [];
    validationProps['serviceURL'] = PWM_MAIN.addParamToUrl(window.location.href,"processAction","validate");
    validationProps['readDataFunction'] = function(){
        var paramData = { };
        for (var j = 0; j < document.forms.length; j++) {
            for (var i = 0; i < document.forms[j].length; i++) {
                var current = document.forms[j].elements[i];
                paramData[current.name] = current.value;
            }
        }
        return paramData;
    };
    validationProps['processResultsFunction'] = function(data){
        data = data['data'];
        if (data["success"] === true) {
            PWM_MAIN.getObject("submitBtn").disabled = false;
            PWM_MAIN.showSuccess(data["message"]);
        } else {
            PWM_MAIN.getObject("submitBtn").disabled = true;
            PWM_MAIN.showError(data['message']);
        }
    };

    PWM_MAIN.pwmFormValidator(validationProps);
};


PWM_UPDATE.uploadPhoto=function(fieldName,options) {
    var url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'uploadPhoto');
    url = PWM_MAIN.addParamToUrl(url, 'field', fieldName);


    var uploadOptions = options === undefined ? {} : options;
    uploadOptions['url'] = url;

    uploadOptions['title'] = PWM_MAIN.showString('Title_UploadPhoto');
    uploadOptions['nextFunction'] = function () {
        PWM_MAIN.showWaitDialog({
            title: 'Upload complete...', loadFunction: function () {
                PWM_MAIN.gotoUrl(window.location.pathname);
            }
        });
    };
    UILibrary.uploadFileDialog(uploadOptions);
};
