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

//
// PWM Change Password JavaScript.
//

"use strict";

var PWM_CHANGEPW = PWM_CHANGEPW || {};

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.

PWM_CHANGEPW.passwordField = "password1";
PWM_CHANGEPW.passwordConfirmField = "password2";

PWM_CHANGEPW.validatePasswords = function(userDN, nextFunction)
{
    // if p1 is changing, then clear out p2.
    if (PWM_GLOBAL['previousP1'] !== PWM_MAIN.getObject(PWM_CHANGEPW.passwordField).value) {
        PWM_MAIN.getObject(PWM_CHANGEPW.passwordConfirmField ).value = "";
        PWM_GLOBAL['previousP1'] = PWM_MAIN.getObject(PWM_CHANGEPW.passwordField).value;
    }

    const validationProps = {};

    validationProps['completeFunction'] = nextFunction ? nextFunction : function () {};
    validationProps['messageWorking'] = PWM_MAIN.showString('Display_CheckingPassword');
    validationProps['serviceURL'] = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','checkPassword');
    validationProps['readDataFunction'] = function(){
        const returnObj = {};
        returnObj[PWM_CHANGEPW.passwordField ] = PWM_MAIN.getObject(PWM_CHANGEPW.passwordField ).value;
        returnObj[PWM_CHANGEPW.passwordConfirmField ] = PWM_MAIN.getObject(PWM_CHANGEPW.passwordConfirmField ).value;
        if (userDN) {
            returnObj['username'] = userDN;
        }
        return returnObj;
    };
    validationProps['processResultsFunction'] = function(data){
        if (data) {
            PWM_CHANGEPW.updateDisplay(data['data']);
        } else {
            PWM_CHANGEPW.updateDisplay(null);
        }
    };

    PWM_MAIN.pwmFormValidator(validationProps);
};


PWM_CHANGEPW.updateDisplay = function(resultInfo) {
    if (!resultInfo) {
        PWM_MAIN.showSuccess(PWM_MAIN.showString('Display_PasswordPrompt'));
        PWM_CHANGEPW.markStrength(0);
        PWM_CHANGEPW.markConfirmationCheck(null);
        return;
    }

    const message = resultInfo["message"];

    if (resultInfo["version"] !== 2) {
        PWM_MAIN.showError("[ unexpected version string from server ]");
        return;
    }

    if (resultInfo["passed"] === true) {
        if (resultInfo["match"] === "MATCH") {
            PWM_MAIN.showSuccess(message);
            PWM_MAIN.getObject("password_button").disabled = false;
        } else {
            PWM_MAIN.showInfo(message);
        }
    } else {
        PWM_MAIN.showError(message);
    }

    try {
        PWM_CHANGEPW.markConfirmationCheck(resultInfo["match"]);
    } catch (e) {
        console.log('error updating confirmation check icons: ' + e)
    }

    try {
        PWM_CHANGEPW.markStrength(resultInfo["strength"]);
    } catch (e) {
        console.log('error updating strength icon: ' + e)
    }
};

PWM_CHANGEPW.markConfirmationCheck = function(matchStatus) {
    if (PWM_MAIN.getObject("confirmCheckMark") && PWM_MAIN.getObject("confirmCrossMark")) {
        if (matchStatus === "MATCH") {
            PWM_MAIN.removeCssClass("confirmCheckMark","nodisplay")
            PWM_MAIN.addCssClass("confirmCrossMark","nodisplay")
        } else if (matchStatus === "NO_MATCH") {
            PWM_MAIN.addCssClass("confirmCheckMark","nodisplay")
            PWM_MAIN.removeCssClass("confirmCrossMark","nodisplay")
        } else {
            PWM_MAIN.addCssClass("confirmCheckMark","nodisplay")
            PWM_MAIN.addCssClass("confirmCrossMark","nodisplay")
        }
    }
};

PWM_CHANGEPW.markStrength = function(strength) { //strength meter
    const passwordStrengthProgressElement = PWM_MAIN.getObject("passwordStrengthProgress");

    if (!passwordStrengthProgressElement) {
        return;
    }

    if (PWM_MAIN.getObject(PWM_CHANGEPW.passwordField).value.length > 0) {
        PWM_MAIN.removeCssClass("strengthBox","noopacity");
    }

    let strengthDescr;

    if (strength >= 99) {
        strengthDescr = "VeryHigh";
    } else if (strength >= 75) {
        strengthDescr = "High";
    } else if (strength >= 45) {
        strengthDescr = "Medium";
    } else if (strength >= 20) {
        strengthDescr = "Low";
    } else {
        strengthDescr = "VeryLow";
    }

    let strengthLabel = PWM_MAIN.showString('Display_PasswordStrength' + strengthDescr);

    passwordStrengthProgressElement.value = strength;
    passwordStrengthProgressElement.setAttribute("data-strength", strengthDescr);

    const labelObject = PWM_MAIN.getObject("strengthLabelText");
    if (labelObject !== null) {
        labelObject.innerHTML = strengthLabel === null ? "" : strengthLabel;
    }
};


PWM_CHANGEPW.copyToPasswordFields = function(text) { // used to copy auto-generated passwords to password field
    if (text.length > 255) {
        text = text.substring(0,255);
    }
    text = PWM_MAIN.trimString(text);


    PWM_MAIN.closeWaitDialog();

    PWM_MAIN.getObject(PWM_CHANGEPW.passwordField).value = text;
    PWM_CHANGEPW.validatePasswords();
    PWM_MAIN.getObject(PWM_CHANGEPW.passwordConfirmField).focus();

    ShowHidePasswordHandler.show('password1');
};


PWM_CHANGEPW.showPasswordGuide=function() {
    PWM_MAIN.showDialog({
        showClose:true,
        title: PWM_MAIN.showString('Title_PasswordGuide'),
        text: '<div id="passwordGuideTextContent">' + PWM_GLOBAL['passwordGuideText'] + '</div>'
    });
};

PWM_CHANGEPW.handleChangePasswordSubmit=function(event) {
    console.log('intercepted change password submit');
    PWM_MAIN.cancelEvent(event);

    const nextFunction = function (data) {
        console.log('post change password submit handler');
        if (!data || data['data']['passed'] && 'MATCH' === data['data']['match']) {
            console.log('submitting password form');
            PWM_MAIN.getObject("changePasswordForm").submit();
        } else {
            PWM_MAIN.closeWaitDialog();
            const match = data['data']['match'];
            if ('MATCH' !== match) {
                PWM_MAIN.getObject(PWM_CHANGEPW.passwordConfirmField).value = '';
            }
            const okFunction = function () {
                if ('MATCH' === match || 'EMPTY' === match) {
                    PWM_MAIN.getObject(PWM_CHANGEPW.passwordField).focus();
                } else {
                    PWM_MAIN.getObject(PWM_CHANGEPW.passwordConfirmField).focus();
                }
                PWM_CHANGEPW.validatePasswords();
            };
            const title = PWM_MAIN.showString('Title_ChangePassword');
            const message = '<div>' + data['data']['message'] + '</div>';
            PWM_MAIN.showDialog({text: message, title: title, okAction: okFunction});
        }
    };

    PWM_MAIN.showWaitDialog({
        loadFunction:function(){
            PWM_MAIN.showInfo('\xa0');
            setTimeout(function(){
                PWM_CHANGEPW.validatePasswords(null, nextFunction);
            },500);
        }}
    );
};

PWM_CHANGEPW.doRandomGeneration=function(randomConfig) {
    randomConfig = randomConfig === undefined ? {} : randomConfig;
    const finishAction = 'finishAction' in randomConfig ? randomConfig['finishAction'] : function (password) {
        PWM_CHANGEPW.copyToPasswordFields(password)
    };

    const eventHandlers = [];
    let dialogBody = "";
    if (randomConfig['dialog'] && randomConfig['dialog'].length > 0) {
        dialogBody += randomConfig['dialog'];
    } else {
        dialogBody += PWM_MAIN.showString('Display_PasswordGeneration');
    }
    dialogBody += "<br/><br/>";
    dialogBody += '<table class="noborder">';

    for (let i = 0; i < 20; i++) {
        dialogBody += '<tr class="noborder">';
        for (let j = 0; j < 2; j++) {
            i = i + j;
            (function(index) {
                const elementID = "randomGen" + index;
                dialogBody += '<td class="noborder"><div class="link-randomPasswordValue"  id="' + elementID + '"></div></td>';
                eventHandlers.push(function(){
                    PWM_MAIN.addEventHandler(elementID,'click',function(){
                        const value = PWM_MAIN.getObject(elementID).innerHTML;
                        const parser = new DOMParser();
                        const dom = parser.parseFromString(value, 'text/html');
                        const domString = dom.body.textContent;
                        finishAction(domString);
                    });
                });
            })(i);
        }
        dialogBody += '</tr>';
    }
    dialogBody += "</table><br/><br/>";

    dialogBody += '<table class="noborder">';
    dialogBody += '<tr class="noborder"><td class="noborder"><button class="btn" id="moreRandomsButton" disabled="true"><span class="btn-icon pwm-icon pwm-icon-refresh"></span>' + PWM_MAIN.showString('Button_More') + '</button></td>';
    dialogBody += '<td class="noborder"><button class="btn" id="cancelRandomsButton"><span class="btn-icon pwm-icon pwm-icon-times"></span>' + PWM_MAIN.showString('Button_Cancel') + '</button></td></tr>';
    dialogBody += "</table>";

    randomConfig['dialogBody'] = dialogBody;

    eventHandlers.push(function(){
        PWM_MAIN.addEventHandler('cancelRandomsButton','click',function(){
            PWM_MAIN.closeWaitDialog('dialogPopup');
        });
        PWM_MAIN.addEventHandler('moreRandomsButton','click',function(){
            PWM_CHANGEPW.beginFetchRandoms(randomConfig);
        });
    });


    const titleString = randomConfig['title'] ? randomConfig['title'] : PWM_MAIN.showString('Title_RandomPasswords');
    PWM_MAIN.showDialog({
        title:titleString,
        dialogClass:'narrow',
        text:dialogBody,
        showOk:false,
        showClose:true,
        loadFunction:function(){
            PWM_CHANGEPW.beginFetchRandoms(randomConfig);
            for (let i = 0; i < eventHandlers.length; i++) {
                eventHandlers[i]();
            }
        }
    });
};

PWM_CHANGEPW.beginFetchRandoms=function(randomConfig) {
    PWM_MAIN.getObject('moreRandomsButton').disabled = true;
    const fetchList = new Array();
    for (let counter = 0; counter < 20; counter++) {
        fetchList[counter] = 'randomGen' + counter;
    }
    fetchList.sort(function() {return 0.5 - Math.random()});
    fetchList.sort(function() {return 0.5 - Math.random()});
    randomConfig['fetchList'] = fetchList;
    PWM_CHANGEPW.fetchRandoms(randomConfig);
};

PWM_CHANGEPW.fetchRandoms=function(randomConfig) {
    if (randomConfig['fetchList'].length < 1) {
        const moreButton = PWM_MAIN.getObject('moreRandomsButton');
        if (moreButton !== null) {
            moreButton.disabled = false;
            moreButton.focus();
        }
        return;
    }

    if (randomConfig['fetchList'].length > 0) {
        const successFunction = function (resultInfo) {
            const password = resultInfo['data']["password"];
            const elementID = randomConfig['fetchList'].pop();
            const element = PWM_MAIN.getObject(elementID);
            if (element !== null) {
                element.innerHTML = password;
            }
            PWM_CHANGEPW.fetchRandoms(randomConfig);
        };

        const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'randomPassword');
        const content = randomConfig['dataInput'] === null ? {} : randomConfig['dataInput'];

        PWM_MAIN.ajaxRequest(url,successFunction,{content:content});
    }
};

PWM_CHANGEPW.startupChangePasswordPage=function() {

    //PWM_MAIN.getObject('password2').disabled = true;
    PWM_CHANGEPW.markStrength(0);

    // add handlers for main form
    const changePasswordForm = PWM_MAIN.getObject('changePasswordForm');
    PWM_MAIN.addEventHandler(changePasswordForm,"keyup, change",function(){
        PWM_CHANGEPW.validatePasswords(null);
    });
    PWM_MAIN.addEventHandler(changePasswordForm,"submit",function(event){
        PWM_CHANGEPW.handleChangePasswordSubmit(event);
        //PWM_MAIN.handleFormSubmit(changePasswordForm, event);
        return false;
    });
    PWM_MAIN.addEventHandler(changePasswordForm,"reset",function(){
        PWM_CHANGEPW.validatePasswords(null);
        PWM_CHANGEPW.setInputFocus();
        return false;
    });

    // show the auto generate password panel
    const autoGenPasswordElement = PWM_MAIN.getObject("autogenerate-icon");
    if (autoGenPasswordElement !== null) {
        PWM_MAIN.removeCssClass(autoGenPasswordElement, "nodisplay");
        PWM_MAIN.addEventHandler(autoGenPasswordElement,'click',function(){
            PWM_CHANGEPW.doRandomGeneration();
        });
    }

    PWM_MAIN.addEventHandler('button-reset','click',function(event){
        console.log('intercepted reset button');

        const p1Value = PWM_MAIN.getObject(PWM_CHANGEPW.passwordField).value;
        const p2Value = PWM_MAIN.getObject(PWM_CHANGEPW.passwordConfirmField).value;

        const submitForm = function () {
            const resetForm = PWM_MAIN.getObject('form-reset');
            PWM_MAIN.handleFormSubmit(resetForm);
        };

        if ( p1Value.length > 0 || p2Value.length > 0 ) {
            PWM_MAIN.cancelEvent(event);
            PWM_MAIN.showConfirmDialog({
                text:PWM_MAIN.showString('Display_LeaveDirtyPasswordPage'),
                okLabel:PWM_MAIN.showString('Button_Continue'),
                okAction:function(){
                    submitForm();
                }
            });
        } else {
            submitForm();
        }
    });

    const messageElement = PWM_MAIN.getObject("message");
    if (messageElement.firstChild.nodeValue.length < 2) {
        setTimeout(function(){
            PWM_MAIN.showInfo(PWM_MAIN.showString('Display_PasswordPrompt'));
        },100);
    }

    if (PWM_GLOBAL['passwordGuideText'] && PWM_GLOBAL['passwordGuideText'].length > 0) {
        const iconElement = PWM_MAIN.getObject('password-guide-icon');
        if (iconElement) {
            PWM_MAIN.removeCssClass(iconElement,'nodisplay');
            PWM_MAIN.addEventHandler('password-guide-icon','click',function(){
                PWM_CHANGEPW.showPasswordGuide();
            });
        }
    }

    const tooltipText = PWM_MAIN.showString('Tooltip_PasswordStrength');
    if (tooltipText) {
        const strengthHelpIcon = PWM_MAIN.getObject('strength-tooltip-icon');
        if (strengthHelpIcon) {
            PWM_MAIN.addEventHandler('strength-tooltip-icon','click',function(){
                PWM_MAIN.showDialog({
                    showClose:true,
                    title: PWM_MAIN.showString('Title_PasswordGuide'),
                    text: '<div id="passwordGuideTextContent">' + tooltipText + '</div>'
                });
            })
        }
    }

    setTimeout(function(){
        PWM_CHANGEPW.setInputFocus();
    },10);
};

PWM_CHANGEPW.setInputFocus=function() {
    const currentPassword = PWM_MAIN.getObject('currentPassword');
    if (currentPassword !== null) {
        setTimeout(function() { currentPassword.focus(); },10);
    } else {
        const password1 = PWM_MAIN.getObject('password1');
        setTimeout(function() { password1.focus(); },10);
    }
};

PWM_CHANGEPW.refreshCreateStatus=function(refreshInterval) {
    const displayStringsUrl = PWM_MAIN.addParamToUrl(window.location.href, "processAction", "checkProgress");
    const completedUrl = PWM_MAIN.addPwmFormIDtoURL(PWM_MAIN.addParamToUrl(window.location.href, "processAction", "complete"));
    const loadFunction = function (data) {
        console.log('beginning html5 progress refresh');
        const html5passwordProgressBar = PWM_MAIN.getObject('html5ProgressBar');
        const percentComplete = data['data']['percentComplete'];
        html5passwordProgressBar.setAttribute("value", percentComplete );

        try {
            let tableBody = '';
            if (data['data']['messages']) {
                for (let msgItem in data['data']['messages']) {
                    (function (message) {
                        if (message['show']) {
                            tableBody += '<tr><td>' + message['label'] + '</td><td>';
                            tableBody += message['complete'] ? PWM_MAIN.showString('Value_ProgressComplete') : PWM_MAIN.showString('Value_ProgressInProgress');
                            tableBody += '</td></tr>';
                        }
                    }(data['data']['messages'][msgItem]));
                }
            }
            if (PWM_MAIN.getObject('progressMessageTable')) {
                PWM_MAIN.getObject('progressMessageTable').innerHTML = tableBody;
            }
            if (PWM_MAIN.getObject('estimatedRemainingSeconds')) {
                PWM_MAIN.getObject('estimatedRemainingSeconds').innerHTML = data['data']['estimatedRemainingSeconds'];
            }
            if (PWM_MAIN.getObject('elapsedSeconds')) {
                PWM_MAIN.getObject('elapsedSeconds').innerHTML = data['data']['elapsedSeconds'];
            }
        } catch (e) {
            console.log('unable to update progressMessageTable, error: ' + e);
        }

        if (data['data']['complete'] === true) {
            PWM_MAIN.gotoUrl(completedUrl, {delay: 1000})
        } else {
            setTimeout(function () {
                PWM_CHANGEPW.refreshCreateStatus(refreshInterval);
            }, refreshInterval);
        }
    };
    const errorFunction = function (error) {
        console.log('unable to read password change status: ' + error);
        setTimeout(function () {
            PWM_CHANGEPW.refreshCreateStatus(refreshInterval);
        }, refreshInterval);
    };
    PWM_MAIN.ajaxRequest(displayStringsUrl, loadFunction, {errorFunction:errorFunction});
};
