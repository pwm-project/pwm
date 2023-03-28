/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

var PWM_NEWUSER = PWM_NEWUSER || {};

// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
PWM_NEWUSER.validateNewUserForm=function() {
    const validationProps = [];
    validationProps['messageWorking'] = PWM_MAIN.showString('Display_CheckingData');
    validationProps['serviceURL'] = PWM_MAIN.addParamToUrl(window.location.href,"processAction","validate");
    validationProps['readDataFunction'] = function(){
        return PWM_NEWUSER.makeFormData();
    };
    validationProps['processResultsFunction'] = function(data){
        PWM_NEWUSER.updateDisplay(data);
    };

    PWM_MAIN.pwmFormValidator(validationProps);
};

PWM_NEWUSER.makeFormData=function() {
    const paramData = {};

    const newUserForm = PWM_MAIN.getObject('newUserForm');

    for (let i = 0; i < newUserForm.elements.length; i++ ) {
        const loopElement = newUserForm.elements[i];
        paramData[loopElement.name] = loopElement.value;
    }

    return paramData;
};

PWM_NEWUSER.updateDisplay=function(data) {
    PWM_CHANGEPW.markConfirmationCheck(null);
    PWM_CHANGEPW.markStrength(null);

    if (data['error']) {
        PWM_MAIN.showError(data['errorMessage']);
        PWM_MAIN.getObject("submitBtn").disabled = true;
    } else {
        const resultInfo = data['data'];
        const message = resultInfo["message"];

        if (resultInfo["passed"] === true) {
            if (resultInfo["match"] === "MATCH") {
                PWM_MAIN.getObject("submitBtn").disabled = false;
                PWM_MAIN.showSuccess(message);
            } else {
                PWM_MAIN.getObject("submitBtn").disabled = true;
                PWM_MAIN.showInfo(message);
            }
        } else {
            PWM_MAIN.getObject("submitBtn").disabled = true;
            PWM_MAIN.showError(message);
        }

        PWM_CHANGEPW.markConfirmationCheck(resultInfo["match"]);
        PWM_CHANGEPW.markStrength(resultInfo["strength"]);
    }
};


PWM_NEWUSER.refreshCreateStatus=function(refreshInterval) {
    require(["dojo","dijit/registry"],function(dojo,registry){
        const checkStatusUrl = PWM_MAIN.addParamToUrl(window.location.pathname, "processAction", "checkProgress");
        const completedUrl = PWM_MAIN.addParamToUrl(window.location.pathname, "processAction", "complete");
        const loadFunction = function (data) {
            const supportsProgress = (document.createElement('progress').max !== undefined);
            if (supportsProgress) {
                console.log('beginning html5 progress refresh');
                const html5passwordProgressBar = PWM_MAIN.getObject('html5ProgressBar');
                dojo.setAttr(html5passwordProgressBar, "value", data['data']['percentComplete']);
            } else {
                console.log('beginning dojo progress refresh');
                const progressBar = registry.byId('passwordProgressBar');
                progressBar.set("value", data['data']['percentComplete']);
            }

            if (data['data']['complete'] === true) {
                PWM_MAIN.gotoUrl(completedUrl, {delay: 1000})
            } else {
                setTimeout(function () {
                    PWM_NEWUSER.refreshCreateStatus(refreshInterval);
                }, refreshInterval);
            }
        };
        PWM_MAIN.ajaxRequest(checkStatusUrl, loadFunction, {method:'GET'});
    });
};
