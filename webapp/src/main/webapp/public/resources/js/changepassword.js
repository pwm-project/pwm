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

import {PWM_JSLibrary} from "./jslibrary.js";
import {PWM_MAIN} from './main.js';

const PWM_CHANGEPW = {};

export { PWM_CHANGEPW };

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.

PWM_CHANGEPW.passwordField = "password1";
PWM_CHANGEPW.passwordConfirmField = "password2";

const RANDOM_PW_WAIT_HTML = '&nbsp;';

let previousP1 = null;

PWM_CHANGEPW.validatePasswords = async function(userDN, nextFunction, extraRequestData)
{
    // if p1 is changing, then clear out p2.
    if (previousP1 !== PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField).value) {
        PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordConfirmField ).value = "";
        previousP1 = PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField).value;
    }

    const validationProps = {};

    validationProps['completeFunction'] = nextFunction ? nextFunction : function () {};
    validationProps['messageWorking'] = await PWM_MAIN.getDisplayString('Display_CheckingPassword');
    validationProps['serviceURL'] = await PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','checkPassword');
    validationProps['readDataFunction'] = function(){
        const returnObj = {};
        returnObj[PWM_CHANGEPW.passwordField ] = PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField ).value;
        returnObj[PWM_CHANGEPW.passwordConfirmField ] = PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordConfirmField ).value;
        if (userDN) {
            returnObj['username'] = userDN;
        }
        if (extraRequestData) {
            return Object.assign(returnObj, extraRequestData);
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


PWM_CHANGEPW.updateDisplay = async function(resultInfo) {
    if (!resultInfo) {
        const displayPrompt = await PWM_MAIN.getDisplayString('Display_PasswordPrompt');
        PWM_MAIN.showSuccess(displayPrompt);
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
            PWM_JSLibrary.getElement("password_button").disabled = false;
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
    if (PWM_JSLibrary.getElement("confirmCheckMark") && PWM_JSLibrary.getElement("confirmCrossMark")) {
        if (matchStatus === "MATCH") {
            PWM_JSLibrary.removeCssClass("confirmCheckMark","nodisplay")
            PWM_JSLibrary.addCssClass("confirmCrossMark","nodisplay")
        } else if (matchStatus === "NO_MATCH") {
            PWM_JSLibrary.addCssClass("confirmCheckMark","nodisplay")
            PWM_JSLibrary.removeCssClass("confirmCrossMark","nodisplay")
        } else {
            PWM_JSLibrary.addCssClass("confirmCheckMark","nodisplay")
            PWM_JSLibrary.addCssClass("confirmCrossMark","nodisplay")
        }
    }
};

PWM_CHANGEPW.markStrength = async function(strength) { //strength meter
    const passwordStrengthProgressElement = PWM_JSLibrary.getElement("passwordStrengthProgress");

    if (!passwordStrengthProgressElement) {
        return;
    }

    if (PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField).value.length > 0) {
        PWM_JSLibrary.removeCssClass("strengthBox","noopacity");
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

    let strengthLabel = await PWM_MAIN.getDisplayString('Display_PasswordStrength' + strengthDescr);

    passwordStrengthProgressElement.value = strength;
    passwordStrengthProgressElement.setAttribute("data-strength", strengthDescr);

    const labelObject = PWM_JSLibrary.getElement("strengthLabelText");
    if (labelObject !== null) {
        labelObject.innerHTML = strengthLabel === null ? "" : strengthLabel;
    }
};


PWM_CHANGEPW.copyToPasswordFields = async function(text) { // used to copy auto-generated passwords to password field
    if (text.length > 255) {
        text = text.substring(0,255);
    }
    text = PWM_MAIN.trimString(text);


    PWM_MAIN.closeWaitDialog();

    PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField).value = text;
    await PWM_CHANGEPW.validatePasswords();
    PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordConfirmField).focus();

    ShowHidePasswordHandler.show('password1');
};


PWM_CHANGEPW.showPasswordGuide=async function() {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();
    PWM_MAIN.showDialog({
        showClose:true,
        title: await PWM_MAIN.getDisplayString('Title_PasswordGuide'),
        text: '<div id="passwordGuideTextContent">' + PWM_GLOBAL['passwordGuideText'] + '</div>'
    });
};

PWM_CHANGEPW.handleChangePasswordSubmit=async function(event,userDn) {
    const title = await PWM_MAIN.getDisplayString('Title_ChangePassword');
    console.log('intercepted change password submit');
    PWM_JSLibrary.cancelEvent(event);

    const nextFunction = function (data) {
        console.log('post change password submit handler');
        if (!data || data['data']['passed'] && 'MATCH' === data['data']['match']) {
            console.log('submitting password form');
            PWM_JSLibrary.getElement("changePasswordForm").submit();
        } else {
            PWM_MAIN.closeWaitDialog();
            const match = data['data']['match'];
            if ('MATCH' !== match) {
                PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordConfirmField).value = '';
            }
            const okFunction = function () {
                if ('MATCH' === match || 'EMPTY' === match) {
                    PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField).focus();
                } else {
                    PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordConfirmField).focus();
                }
                PWM_CHANGEPW.validatePasswords();
            };
            const message = '<div>' + data['data']['message'] + '</div>';
            PWM_MAIN.showDialog({text: message, title: title, okAction: okFunction});
        }
    };

    PWM_MAIN.showWaitDialog({
        loadFunction:function(){
            PWM_MAIN.showInfo('\xa0');
            setTimeout(function(){
                PWM_CHANGEPW.validatePasswords(userDn, nextFunction);
            },500);
        }}
    );
};

PWM_CHANGEPW.doRandomGeneration=async function(randomConfig) {
    randomConfig = randomConfig === undefined ? {} : randomConfig;
    const finishAction = 'finishAction' in randomConfig ? randomConfig['finishAction'] : function (password) {
        PWM_CHANGEPW.copyToPasswordFields(password)
    };

    const displayPwGen = await PWM_MAIN.getDisplayString('Display_PasswordGeneration');
    const displayButtonMore = await PWM_MAIN.getDisplayString('Button_More');
    const displayButtonCancel = await PWM_MAIN.getDisplayString('Button_Cancel');

    const eventHandlers = [];
    let dialogBody = "";
    if (randomConfig['dialog'] && randomConfig['dialog'].length > 0) {
        dialogBody += randomConfig['dialog'];
    } else {
        dialogBody += displayPwGen;
    }
    dialogBody += "<br/><br/>";
    dialogBody += '<table class="noborder">';

    for (let i = 0; i < 10; i++) {
        (function(index) {
            const elementID = "randomGen" + index;
            dialogBody += '<tr class="noborder">';
            dialogBody += '<td class="noborder"><div class="link-randomPasswordValue"  id="' + elementID + '">'
                + RANDOM_PW_WAIT_HTML;
            eventHandlers.push(function(){
                PWM_MAIN.addEventHandler(elementID,'click',function(){
                    const value = PWM_JSLibrary.getElement(elementID).innerHTML;
                    const parser = new DOMParser();
                    const dom = parser.parseFromString(value, 'text/html');
                    const domString = dom.body.textContent;
                    finishAction(domString);
                });
            });
            dialogBody += '</tr>';
        })(i);
    }
    dialogBody += "</table><br/><br/>";

    dialogBody += '<table class="noborder">';
    dialogBody += '<tr class="noborder"><td class="noborder"><button class="btn" id="moreRandomsButton" disabled="true"><span class="btn-icon pwm-icon pwm-icon-refresh"></span>' + displayButtonMore + '</button></td>';
    dialogBody += '<td class="noborder"><button class="btn" id="cancelRandomsButton"><span class="btn-icon pwm-icon pwm-icon-times"></span>' + displayButtonCancel + '</button></td></tr>';
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


    const titleString = randomConfig['title'] ? randomConfig['title'] : await PWM_MAIN.getDisplayString('Title_RandomPasswords');

    PWM_MAIN.showDialog({
        title:titleString,
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
    PWM_JSLibrary.getElement('moreRandomsButton').disabled = true;
    const fetchList = new Array();
    for (let counter = 0; counter < 10; counter++) {
        fetchList[counter] = 'randomGen' + counter;
    }
    fetchList.sort(function() {return 0.5 - Math.random()});
    fetchList.sort(function() {return 0.5 - Math.random()});
    randomConfig['fetchList'] = fetchList;
    PWM_CHANGEPW.fetchRandoms(randomConfig);
};

PWM_CHANGEPW.fetchRandoms=async function(randomConfig) {

    if (randomConfig['fetchList'].length > 0) {
        const elementID = randomConfig['fetchList'].pop();
        const element = PWM_JSLibrary.getElement(elementID);
        if (element !== null) {
            element.innerHTML = RANDOM_PW_WAIT_HTML;
        }

        PWM_CHANGEPW.fetchRandoms(randomConfig);

        const successFunction = function (resultInfo) {
            const password = resultInfo['data']["password"];
            if (element !== null) {
                element.innerText = password;
            }

            if (randomConfig['fetchList'].length < 1) {
                const moreButton = PWM_JSLibrary.getElement('moreRandomsButton');
                if (moreButton !== null) {
                    moreButton.disabled = false;
                    moreButton.focus();
                }
            }
        };

        const url = PWM_MAIN.addParamToUrl(null, 'processAction', 'randomPassword');
        const content = randomConfig['dataInput'] === null ? {} : randomConfig['dataInput'];
        PWM_MAIN.ajaxRequest(url,successFunction,{content:content});
    }
};

PWM_CHANGEPW.startupChangePasswordPage=async function() {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();

    //PWM_JSLibrary.getElement('password2').disabled = true;
    PWM_CHANGEPW.markStrength(0);

    // add handlers for main form
    const changePasswordForm = PWM_JSLibrary.getElement('changePasswordForm');
    PWM_MAIN.addEventHandler(changePasswordForm,"keyup, change",function(){
        PWM_CHANGEPW.validatePasswords(null);
    });
    PWM_MAIN.addEventHandler(changePasswordForm,"submit",function(event){
        PWM_CHANGEPW.handleChangePasswordSubmit(event);
        return false;
    });
    PWM_MAIN.addEventHandler(changePasswordForm,"reset",function(){
        PWM_CHANGEPW.validatePasswords(null);
        PWM_CHANGEPW.setInputFocus();
        return false;
    });

    // show the auto generate password panel
    const autoGenPasswordElement = PWM_JSLibrary.getElement("autogenerate-icon");
    if (autoGenPasswordElement !== null) {
        PWM_JSLibrary.removeCssClass(autoGenPasswordElement, "nodisplay");
        PWM_MAIN.addEventHandler(autoGenPasswordElement,'click',function(){
            PWM_CHANGEPW.doRandomGeneration();
        });
    }

    PWM_MAIN.addEventHandler('button-reset','click',async function(event){
        console.log('intercepted reset button');

        const p1Value = PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField).value;
        const p2Value = PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordConfirmField).value;

        const submitForm = function () {
            const resetForm = PWM_JSLibrary.getElement('form-reset');
            PWM_MAIN.handleFormSubmit(resetForm);
        };

        const confirmDialogOptions = {
            text:await PWM_MAIN.getDisplayString('Display_LeaveDirtyPasswordPage'),
            okLabel:await PWM_MAIN.getDisplayString('Button_Continue'),
            okAction:function(){
                submitForm();
            }
        }

        if ( p1Value.length > 0 || p2Value.length > 0 ) {
            PWM_JSLibrary.cancelEvent(event);
            PWM_MAIN.showConfirmDialog(confirmDialogOptions);
        } else {
            submitForm();
        }
    });

    const messageElement = PWM_JSLibrary.getElement("message");
    if (messageElement.firstChild.nodeValue.length < 2) {
        setTimeout(async function(){
            PWM_MAIN.showInfo(await PWM_MAIN.getDisplayString('Display_PasswordPrompt'));
        },100);
    }

    if (PWM_GLOBAL['passwordGuideText'] && PWM_GLOBAL['passwordGuideText'].length > 0) {
        const iconElement = PWM_JSLibrary.getElement('password-guide-icon');
        if (iconElement) {
            PWM_JSLibrary.removeCssClass(iconElement,'nodisplay');
            PWM_MAIN.addEventHandler('password-guide-icon','click',function(){
                PWM_CHANGEPW.showPasswordGuide();
            });
        }
    }

    const tooltipText = await PWM_MAIN.getDisplayString('Tooltip_PasswordStrength');
    if (tooltipText) {
        const strengthHelpIcon = PWM_JSLibrary.getElement('strength-tooltip-icon');
        if (strengthHelpIcon) {
            PWM_MAIN.addEventHandler('strength-tooltip-icon','click',async function(){
                PWM_MAIN.showDialog({
                    showClose:true,
                    title: await PWM_MAIN.getDisplayString('Title_PasswordGuide'),
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
    const currentPassword = PWM_JSLibrary.getElement('currentPassword');
    if (currentPassword !== null) {
        setTimeout(function() { currentPassword.focus(); },10);
    } else {
        const password1 = PWM_JSLibrary.getElement('password1');
        setTimeout(function() { password1.focus(); },10);
    }
};

PWM_CHANGEPW.refreshCreateStatus=async function(refreshInterval) {
    const displayStringsUrl = PWM_MAIN.addParamToUrl(window.location.href, "processAction", "checkProgress");
    const completedUrl = await PWM_MAIN.addPwmFormIDtoURL(PWM_MAIN.addParamToUrl(window.location.href, "processAction", "complete"));

        const displayProgComplete = await PWM_MAIN.getDisplayString('Value_ProgressComplete')
        const displayInProg = await PWM_MAIN.getDisplayString('Value_ProgressInProgress');


    const loadFunction = function (data) {
        console.log('beginning html5 progress refresh');
        const html5passwordProgressBar = PWM_JSLibrary.getElement('html5ProgressBar');
        const percentComplete = data['data']['percentComplete'];
        html5passwordProgressBar.setAttribute("value", percentComplete );

        try {
            let tableBody = '';
            if (data['data']['messages']) {
                for (let msgItem in data['data']['messages']) {
                    (function (message) {
                        if (message['show']) {
                            tableBody += '<tr><td>' + message['label'] + '</td><td>';
                            tableBody += message['complete'] ? displayProgComplete : displayInProg
                            tableBody += '</td></tr>';
                        }
                    }(data['data']['messages'][msgItem]));
                }
            }
            if (PWM_JSLibrary.getElement('progressMessageTable')) {
                PWM_JSLibrary.getElement('progressMessageTable').innerHTML = tableBody;
            }
            if (PWM_JSLibrary.getElement('estimatedRemainingSeconds')) {
                PWM_JSLibrary.getElement('estimatedRemainingSeconds').innerHTML = data['data']['estimatedRemainingSeconds'];
            }
            if (PWM_JSLibrary.getElement('elapsedSeconds')) {
                PWM_JSLibrary.getElement('elapsedSeconds').innerHTML = data['data']['elapsedSeconds'];
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

