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

"use strict";

import {PWM_JSLibrary} from "./jslibrary.js";
import {PWM_MAIN} from "./main.js";
import {PWM_CHANGEPW} from "./changepassword.js";

const PWM_HELPDESK = {};

export {PWM_HELPDESK}

const PREF_KEY_VERIFICATION_STATE = 'verificationState';

let cachedClientData = null;
let cardMode = false;

async function readHelpdeskClientData() {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();
    const urlBase = PWM_GLOBAL['url-context'] + '/private/helpdesk';
    if ( !cachedClientData ) {
        cachedClientData = new Promise((resolve,reject) => {
            const url = PWM_MAIN.addParamToUrl(urlBase,'processAction','helpdeskClientData');
            PWM_MAIN.ajaxRequest(url,function(data){
                if (data['error']) {
                    PWM_MAIN.showErrorDialog(data);
                    reject(null);
                }
                resolve(Object.freeze(data['data']));
            },{method:"GET"});
        });
    }
    return cachedClientData;
}

function readVerificationState() {
    return PWM_MAIN.Preferences.readSessionStorage(PREF_KEY_VERIFICATION_STATE);
}

function writeVerificationState(verificationState) {
    PWM_MAIN.Preferences.writeSessionStorage(PREF_KEY_VERIFICATION_STATE, verificationState);
}

function handleSearchModeToggle() {
    const toggleElement = PWM_JSLibrary.getElement('helpdesk-search-result-mode')
    const checked = toggleElement.checked;
    cardMode = !checked;
    console.log('checked=' + checked);
    PWM_MAIN.Preferences.writeSessionStorage('helpdesk_field_cardmode', cardMode)
    PWM_HELPDESK.processHelpdeskSearch();
}

PWM_HELPDESK.processHelpdeskSearch = function() {
    const gridDiv = PWM_JSLibrary.getElement('helpdesk-searchResultsGrid');
    gridDiv.innerHTML = '';
    const validationProps = {};
    validationProps['serviceURL'] = PWM_MAIN.addParamToUrl(null,"processAction", "search");
    validationProps['showMessage'] = false;
    validationProps['usernameField'] = PWM_JSLibrary.getElement('username').value;
    validationProps['readDataFunction'] = function(){
        PWM_JSLibrary.removeCssClass('searchIndicator','hidden')
        return { username:PWM_JSLibrary.getElement('username').value }
    };
    validationProps['completeFunction'] = function() {
        PWM_JSLibrary.addCssClass('searchIndicator','hidden')
    };
    validationProps['processResultsFunction'] = function(data) {
        processSearchResultData(data);
    };
    PWM_MAIN.pwmFormValidator(validationProps);
    PWM_JSLibrary.addCssClass('maxResultsIndicator','nodisplay');
};

function showRecentVerifications() {
    const handleVerificationResult = async function (data) {
        if (data['error']) {
            PWM_MAIN.showErrorDialog(data);
        } else {
            const records = data['data']['records'];
            let html = '';
            if (PWM_JSLibrary.isEmpty(records)) {
                html += await PWM_MAIN.getDisplayString('Display_SearchResultsNone');
            } else {
                html += '<table>';
                html += '<tr><td class="title">' + await PWM_MAIN.getDisplayString('Field_LdapProfile') + '</td><td class="title">'
                    + await PWM_MAIN.getDisplayString('Field_Username') + '</td><td class="title">'
                    + await PWM_MAIN.getDisplayString('Field_DateTime') + '</td><td class="title">'
                    + await PWM_MAIN.getDisplayString('Field_Method') + '</td>';
                for (let i in records) {
                    const record = records[i];
                    html += `<tr>`;
                    html += `<td>${record['profile']}</td>`;
                    html += `<td>${record['username']}</td>`;
                    html += `<td id="verification-record-timestamp-${i}" class="timestamp">${record['timestamp']}</td>`;
                    html += `<td>${record['method']}</td>`;
                    html += '</tr>';
                }
            }

            html += '</table>';
            PWM_MAIN.showDialog({
                'title': await PWM_MAIN.getDisplayString('Title_RecentVerifications'), 'text': html, loadFunction: function () {
                    for (let i in records) {
                        const id = 'verification-record-timestamp-' + i;
                        PWM_MAIN.initTimestampElement(PWM_JSLibrary.getElement(id));
                    }
                }
            });
        }
    };

    const loadVerificationsFunction = function () {
        const url = PWM_MAIN.addParamToUrl(null, "processAction", "showVerifications");
        const content = { [PREF_KEY_VERIFICATION_STATE]:readVerificationState() };
        PWM_MAIN.ajaxRequest(url, handleVerificationResult, {content: content,addPwmFormID:true});
    };


    PWM_MAIN.showWaitDialog({loadFunction:loadVerificationsFunction});
}


async function processSearchResultData(data) {
    if (data === null) {
        PWM_MAIN.showErrorDialog(await PWM_MAIN.getDisplayString('Display_HelpdeskNoData'));
    } else if (data['error']) {
        PWM_MAIN.showErrorDialog(data);
    } else {
        const gridDiv = PWM_JSLibrary.getElement('helpdesk-searchResultsGrid');
        const gridData = data['data']['searchResults'];

        if (PWM_JSLibrary.isEmpty(gridData)) {
            gridDiv.innerHTML = '<div>No results</div>';
        } else {

            const sizeExceeded = data['data']['sizeExceeded'];

            const resultProcessor = cardMode
                ? new SearchResultToCardProcessor()
                : new SearchResultToGridProcessor();

            gridDiv.innerHTML = await resultProcessor.makeHtmlData(gridData);

            await resultProcessor.htmlPostProcessor(gridData);

            if (sizeExceeded) {
                PWM_JSLibrary.removeCssClass('maxResultsIndicator', 'hidden');
                PWM_MAIN.showTooltip({
                    id: 'maxResultsIndicator',
                    position: 'below',
                    text: await PWM_MAIN.getDisplayString('Display_SearchResultsExceeded')
                })
            } else {
                PWM_JSLibrary.addCssClass('maxResultsIndicator', 'hidden');
                PWM_MAIN.showTooltip({
                    id: 'maxResultsIndicator',
                    position: 'below',
                    text: await PWM_MAIN.getDisplayString('Display_SearchResultsNone')
                })
            }
        }
    }
}

async function validateCode(options) {
    options = options === undefined ? {} : options;

    const userKey = options['userKey'];
    const processAction = options['processAction'];
    const dialogText = options['dialogText'];
    const extraPayload = options['extraPayload'];
    const showInputField = 'showInputField' in options ? options['showInputField '] : true;

    const disp_ButtonVerify = await PWM_MAIN.getDisplayString('Button_Verify');

    const validateOtpCodeFunction = function () {
        const typedCode = PWM_JSLibrary.getElement('code')
            ? PWM_JSLibrary.getElement('code').value
            : '';

        const content = extraPayload === undefined ? {} : extraPayload();

        content['userKey'] = userKey;
        content['code'] = typedCode;
        content[PREF_KEY_VERIFICATION_STATE] = readVerificationState();
        const url = PWM_MAIN.addParamToUrl(null, "processAction", processAction);
        const loadFunction = function (data) {
            PWM_JSLibrary.addCssClass('icon-working','hidden');

            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
                return;
            }

            const verificationState = data['data'][PREF_KEY_VERIFICATION_STATE];
            writeVerificationState(verificationState);

            const passed = data['data']['passed'];
            if (passed) {
                gotoDetailView(userKey);
            } else {
                const errorMsg = data['data']['message'];
                PWM_MAIN.showDialog({text:errorMsg,okAction:function(){
                        gotoDetailView(userKey);
                    }});

            }
        };
        PWM_MAIN.showWaitDialog({loadFunction(){
                PWM_MAIN.ajaxRequest(url, loadFunction, {content: content});
            }});
    };

    let text = '<form id="validateCodeForm"><div>' + dialogText + '</div><br/><div>';
    if (showInputField) {
        text += '<td><input id="code" name="code"/></td>';
    }

    text += '<button type="submit" class="btn" id="button-checkCode"><span class="btn-icon pwm-icon pwm-icon-check"></span>'
        + disp_ButtonVerify + '</button>'
        + '</div></form>';

    const successFunction = function () {
        gotoDetailView(userKey);
    };

    PWM_MAIN.showDialog({
        showClose:true,
        showOk:false,
        allowMove:true,
        title:await PWM_MAIN.getDisplayString('Title_ValidateCode'),
        text:text,
        loadFunction:function(){
            PWM_MAIN.addEventHandler('validateCodeForm','submit',function(event){
                validateOtpCodeFunction(event);
                PWM_JSLibrary.cancelEvent(event);
                console.log('caught submit');
                return false;
            });

        },
        okAction:successFunction
    });
}

async function handleOptionalVerificationButton(userKey) {
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();
    const urlBase = PWM_GLOBAL['url-context'] + '/private/helpdesk/detail';
    PWM_MAIN.showWaitDialog({loadFunction:function(){
            const checkVerificationOptions = {
                [PREF_KEY_VERIFICATION_STATE]:readVerificationState(),
                userKey:userKey
            };
            const verificationUrl = PWM_MAIN.addParamToUrl(urlBase,'processAction','checkOptionalVerification');
            const verificationResultFunction = function(data){
                const verificationResult = data['data']
                processVerificationResponse(userKey,verificationResult,'optional');
            };
            PWM_MAIN.ajaxRequest(verificationUrl,verificationResultFunction,{content:checkVerificationOptions});
        }});

}

async function processVerificationResponse(userKey,verificationResponse,mode) {
    const methods = verificationResponse.verificationOptions.verificationMethods[mode.toString()]

    const disp_TitleVerifySend = await PWM_MAIN.getDisplayString('Long_Title_VerificationSend');
    const disp_ButtonEmail =  await PWM_MAIN.getDisplayString('Button_Email');
    const disp_ButtonSms = await PWM_MAIN.getDisplayString('Button_SMS');
    const disp_ButtonAttributes = await PWM_MAIN.getDisplayString('Button_Attributes');
    const disp_ButtonOtp = await PWM_MAIN.getDisplayString('Button_OTP');

    const sendTokenAction = function (id) {
        const sendContent = {
            userKey:userKey,
            id:id
        };
        PWM_MAIN.showWaitDialog({
            loadFunction: function () {
                const url = PWM_MAIN.addParamToUrl(null, "processAction", "sendVerificationToken");
                const loadFunction = async function (data) {
                    if (!data['error']) {
                        const text = '<table><tr><td>' + await PWM_MAIN.getDisplayString('Display_TokenDestination') + '</td><td>'
                            + data['data']['destination'] + '</td></tr></table>';
                        const returnExtraData = function () {
                            return data['data'];
                        };
                        validateCode({userKey: userKey, processAction: 'verifyVerificationToken', dialogText: text, extraPayload: returnExtraData});
                    } else {
                        PWM_MAIN.showErrorDialog(data);
                    }
                };
                PWM_MAIN.ajaxRequest(url, loadFunction, {content: sendContent});
            }
        });
    };

    let dialogText = `<div class="verificationOptionsWrapper">`;
    if (mode === 'required') {
        dialogText += `<div class="verificationOptionsTitle">${disp_TitleVerifySend}</div>`;
    }
    if (PWM_JSLibrary.arrayContains(methods,'ATTRIBUTES')) {
        dialogText += '<div class="verificationOptionChoice"><button class="btn" type="button" name="attributesChoiceButton" id="attributesChoiceButton">'
            + `<span class="btn-icon pwm-icon pwm-icon-database "></span>${disp_ButtonAttributes}</button></div>`;
    }
    if (PWM_JSLibrary.arrayContains(methods,'TOKEN')) {
        PWM_JSLibrary.forEachInArray(verificationResponse['verificationOptions']['tokenDestinations'],function(tokenDestination){
            const buttonLabel = tokenDestination['type'] === 'email' ? disp_ButtonEmail : disp_ButtonSms;
            const buttonIcon = tokenDestination['type'] === 'email' ? 'pwm-icon-envelope-o' : 'pwm-icon-qrcode';
            const buttonId = 'token-button-' + tokenDestination['id'];
            dialogText += `<div class="verificationOptionChoice"><button class="btn" type="button" name="${buttonId}" id="${buttonId}">`
                + `<span class="btn-icon pwm-icon ${buttonIcon}"></span>${buttonLabel}</button>`
                + `<span class="tokenDestination">${tokenDestination['display']}</span></div>`;
        });
    }
    if (PWM_JSLibrary.arrayContains(methods,'OTP')) {
        dialogText += '<div class="verificationOptionChoice"><button class="btn" type="button" name="otpChoiceButton" id="otpChoiceButton">'
            + `<span class="btn-icon pwm-icon pwm-icon-qrcode"></span>${disp_ButtonOtp}</button></div>`;
    }
    dialogText += '</div>';

    const dialogLoadFunction = function () {
        PWM_MAIN.addEventHandler('attributesChoiceButton', 'click', function () {
            validateAttributes(userKey)
        });
        PWM_JSLibrary.forEachInArray(verificationResponse['verificationOptions']['tokenDestinations'],function(tokenDestination){
            const buttonId = 'token-button-' + tokenDestination['id'];
            PWM_MAIN.addEventHandler(buttonId, 'click', function () {
                sendTokenAction(tokenDestination['id'])
            });
        });
        PWM_MAIN.addEventHandler('otpChoiceButton', 'click', function () {
            PWM_HELPDESK.validateOtpCode(userKey)
        });
    };
    PWM_MAIN.showDialog({
        title:await PWM_MAIN.getDisplayString('Title_VerificationSend'),
        text:dialogText,
        showOk:false,
        showCancel:false,
        showClose: true,
        okAction:function(){
            sendTokenAction();
        },
        loadFunction:dialogLoadFunction
    });
}

async function validateAttributes(userKey) {
    const helpdeskClientData = await readHelpdeskClientData();
    const formItems = helpdeskClientData['verificationForm'];
    let bodyText = '';
    bodyText += '<div><table>';

    PWM_JSLibrary.forEachInArray(formItems,function(formItem){
        const name = formItem['name'];
        const label = formItem['label'];
        bodyText += `<tr><td>${label}</td><td><input id="input-${name}"/></td></tr>`;
    })
    bodyText += '</table></div>';

    const extraData = function () {
        const formData = {};
        PWM_JSLibrary.forEachInArray(formItems,function(formItem){
            const name = formItem['name'];
            formData[name] = PWM_JSLibrary.getElement('input-' + name).value;
        });

        return {attributeData:formData};
    };

    const options = {
        userKey: userKey,
        processAction: 'validateAttributes',
        dialogText: bodyText,
        showInputField: false,
        extraPayload: extraData

    };

    await validateCode(options);
}


async function gotoDetailView(userKey){
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();
    console.log('navigating to detail view');
    const urlBase = PWM_GLOBAL['url-context'] + '/private/helpdesk';
    PWM_MAIN.showWaitDialog({loadFunction:function(){
            const checkVerificationOptions = {
                [PREF_KEY_VERIFICATION_STATE]:readVerificationState(),
                userKey:userKey
            };
            const verificationUrl = PWM_MAIN.addParamToUrl(urlBase,'processAction','checkVerification');
            const verificationResultFunction = function(data){
                const verificationResult = data['data']
                if ( verificationResult['passed']) {
                    const viewDetailParams = {
                        [PREF_KEY_VERIFICATION_STATE]:readVerificationState(),
                        userKey:userKey
                    };
                    PWM_MAIN.submitPostAction(urlBase + "/detail",'detail',viewDetailParams)
                }
                else {
                    processVerificationResponse(userKey,verificationResult,'required');
                }
            };
            PWM_MAIN.ajaxRequest(verificationUrl,verificationResultFunction,{content:checkVerificationOptions});
        }});
}

async function gotoSearchView(){
    const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();
    console.log('navigating to search view');
    const url  = PWM_GLOBAL['url-context'] + '/private/helpdesk';
    PWM_MAIN.gotoUrl(url);
}

class SearchResultToCardProcessor {
    async htmlPostProcessor(gridData) {
        PWM_JSLibrary.forEachInArray(gridData, function (rowData) {
            const userKey = rowData['userKey'];
            SearchResultToCardProcessor.postProcessHtmlCard(userKey);
        });
    }

    static async postProcessHtmlCard( userKey ) {
        const PWM_GLOBAL = await PWM_MAIN.getPwmGlobal();
        const clientData = await readHelpdeskClientData();
        const photoEnabled = clientData['enablePhoto'];
        const contentId = 'person-tile-content-' + userKey;
        const contentElement = PWM_JSLibrary.getElement(contentId);
        const baseUrl = PWM_GLOBAL['url-context'] + '/private/helpdesk';
        const url = PWM_MAIN.addParamsToUrl(baseUrl,{"processAction":"card","userKey":userKey});

        PWM_MAIN.addEventHandler('tile-'+userKey,'click',function (event){
            PWM_JSLibrary.cancelEvent(event);
            gotoDetailView(userKey);
        })

        PWM_MAIN.ajaxRequest(url,function(data){
            const cardDisplayLines = data['data']['cardDisplayLines'];
            let counter = 0;
            PWM_JSLibrary.forEachInArray( cardDisplayLines, function(displayLine){
                const lineId = contentId + '-line-' + counter++;
                const lineDiv = document.createElement('div');
                lineDiv.setAttribute('class','person-tile-content-line');
                lineDiv.setAttribute( 'id',lineId);
                lineDiv.innerText = displayLine;
                contentElement.append(lineDiv);
            } )
            if (photoEnabled) {
                const photoElement = PWM_JSLibrary.getElement('person-tile-avatar-' + userKey);
                if ( photoElement ) {
                    const photoUrl = PWM_MAIN.addParamsToUrl( baseUrl, {'processAction':'photo','userKey':userKey} );
                    photoElement.style.backgroundImage = `url('${photoUrl}')`;
                }
            }
        });
    }

    async makeHtmlData(gridData) {
        const clientData = await readHelpdeskClientData();
        const photoEnabled = clientData['enablePhoto'];
        let htmlData = '<div class="person-tile-grid-wrapper"><div class="person-tile-grid">';
        PWM_JSLibrary.forEachInArray(gridData, function (rowData) {
            const userKey = rowData['userKey'];
            htmlData += SearchResultToCardProcessor.makeHtmlCard(userKey, photoEnabled);
        });
        htmlData += '</div></div>';
        return htmlData;
    }

    static makeHtmlCard(userKey, photoEnabled) {
        const tileId = 'tile-' + userKey;
        let htmlData = `<div id="${tileId}" class="person-tile">`;
        if (photoEnabled) {
            htmlData += `<div class="person-tile-avatar" id="person-tile-avatar-${userKey}"></div>`;
        }
        const contentId = 'person-tile-content-' + userKey;
        htmlData += `<div id="${contentId}" class="person-tile-content"></div>`
        htmlData += `</div>`;
        return htmlData;
    }
}

class SearchResultToGridProcessor {
    async htmlPostProcessor(gridData) {
        const clientData = await readHelpdeskClientData();

        PWM_JSLibrary.forEachInArray(gridData, function (rowData) {
            const userKey = rowData['userKey'];
            const rowId = 'row-' + userKey;
            PWM_JSLibrary.forEachInObject(clientData['searchColumns'], function (key, value) {
                const valueId = 'value-' + userKey + '-' + key;
                const valueElement = PWM_JSLibrary.getElement(valueId);
                valueElement.innerText = rowData[key];
            });
            PWM_MAIN.addEventHandler(rowId, 'click', function (event) {
                PWM_JSLibrary.cancelEvent(event);
                gotoDetailView(userKey);
            })
        });

        this.makeTableSortable();
    };

    makeTableSortable() {
        const getCellValue = (tr, idx) => tr.children[idx].innerText || tr.children[idx].textContent;

        const comparer = (idx, asc) => (a, b) => ((v1, v2) =>
                v1 !== '' && v2 !== '' && !isNaN(v1) && !isNaN(v2) ? v1 - v2 : v1.toString().localeCompare(v2)
        )(getCellValue(asc ? a : b, idx), getCellValue(asc ? b : a, idx));

        // do the work...
        document.querySelectorAll('th').forEach(th => th.addEventListener('click', (() => {
            const table = th.closest('table');
            Array.from(table.querySelectorAll('tr:nth-child(n+2)'))
                .sort(comparer(Array.from(th.parentNode.children).indexOf(th), this.asc = !this.asc))
                .forEach(tr => table.appendChild(tr) );
        })));
    }

    async makeHtmlData(gridData) {
        const clientData = await readHelpdeskClientData();
        let htmlRows = '<table class="person-table">';
        htmlRows += await SearchResultToGridProcessor.#makeTableHeaderHtml(gridData);
        PWM_JSLibrary.forEachInArray(gridData, function (rowData) {
            const userKey = rowData['userKey'];
            const rowId = 'row-' + userKey;
            htmlRows += `<tr id="${rowId}" class="person-table-row">`;
            PWM_JSLibrary.forEachInObject(clientData['searchColumns'], function (key, value) {
                const valueId = 'value-' + userKey + '-' + key;
                htmlRows += `<td id="${valueId}" class="person-table-data"></td>`;
            });
            htmlRows += '</tr>';
        });
        htmlRows += '</table>';
        return htmlRows;
    }

    static async #makeTableHeaderHtml() {
        const clientData = await readHelpdeskClientData();
        let htmlRow = "<tr>";
        PWM_JSLibrary.forEachInObject(clientData['searchColumns'], function (key, value) {
            htmlRow += `<th id="tableHeaderColumn-${key}" class="person-table-header">${value}</th>`;
        });
        htmlRow += "</tr>";
        return htmlRow;
    }
}

PWM_HELPDESK.initHelpdeskSearchPage = function() {
    PWM_MAIN.addEventHandler('helpdesk-search-result-mode','click',handleSearchModeToggle)
    PWM_MAIN.addEventHandler('button-show-current-verifications','click',showRecentVerifications);
    PWM_MAIN.addEventHandler('username', "keyup, input", function(){
        PWM_HELPDESK.processHelpdeskSearch();
        try {
            const helpdeskFieldUsername = PWM_JSLibrary.getElement('username').value;
            PWM_MAIN.Preferences.writeSessionStorage("helpdesk_field_username", helpdeskFieldUsername);
        } catch (e) {
            console.log('error writing username field from sessionStorage: ' + e);
        }
    });

    if (PWM_JSLibrary.getElement('username')) {
        try {
            const helpdeskFieldUsername = PWM_MAIN.Preferences.readSessionStorage("helpdesk_field_username","");
            PWM_JSLibrary.getElement('username').value = helpdeskFieldUsername;
        } catch (e) {
            console.log('error reading username field from sessionStorage: ' + e);
        }

        if (PWM_JSLibrary.getElement('username').value && PWM_JSLibrary.getElement('username').value.length > 0) {
            PWM_HELPDESK.processHelpdeskSearch();
        }
    }

    cardMode = PWM_MAIN.Preferences.readSessionStorage('helpdesk_field_cardmode',true);
    const toggleElement = PWM_JSLibrary.getElement('helpdesk-search-result-mode')
    if ( toggleElement ) {
        toggleElement.checked = !cardMode;
    }
};



async function helpdeskPasswordChangeAction(userKey) {
    const clientData = await readHelpdeskClientData();

    const randomChosenFunction = function(password){
        PWM_MAIN.showWaitDialog({loadFunction:function(){
                const url = PWM_MAIN.addParamToUrl(null,'processAction','setPassword');
                const content = {username:userKey,[PREF_KEY_VERIFICATION_STATE]:readVerificationState(),password:password};
                const loadFunction = async function(data){
                    const message = `<div>${data.successMessage}<div><br/><div class="field-password" id="showPassword"></div>`;
                    PWM_MAIN.showDialog({title:await PWM_MAIN.getDisplayString('Title_Success'),text:message,loadFunction(){
                            PWM_JSLibrary.getElement('showPassword').innerText = password;
                        }})
                }
                PWM_MAIN.ajaxRequest(url,loadFunction,{content:content})
            }});
    };

    const submitPwChangeDialog = async function() {
        const display_titleSuccess = await PWM_MAIN.getDisplayString('Title_Success');
        const display_checkingPw = await PWM_MAIN.getDisplayString('Display_CheckingPassword');
        PWM_MAIN.showInfo(display_checkingPw);
        PWM_CHANGEPW.validatePasswords(userKey,function(data){
            const validationData = data['data'];
            if (validationData['passed'] && validationData['match'] === 'MATCH') {
                const pw = PWM_JSLibrary.getElement(PWM_CHANGEPW.passwordField).value;
                PWM_MAIN.showWaitDialog({
                    loadFunction: function () {
                        const url = PWM_MAIN.addParamToUrl(null, 'processAction', 'setPassword');
                        const options = {username: userKey, password: pw, [PREF_KEY_VERIFICATION_STATE]:readVerificationState()};
                        PWM_MAIN.ajaxRequest(url, function (data) {
                            PWM_MAIN.showDialog({title: display_titleSuccess, text: data['successMessage']});
                        }, {content: options});
                    }
                })
            } else {
                PWM_MAIN.showError(validationData['message']);
            }
        },{[PREF_KEY_VERIFICATION_STATE]:readVerificationState()});
    }

    const randomConfig = {dataInput:{username:userKey},finishAction:randomChosenFunction};

    if (clientData['pwUiMode'] === 'autogen') {
        PWM_CHANGEPW.doRandomGeneration(randomConfig);
        return;
    }

    let buttonHtml = `<button type="submit" name="password_button" class="btn" id="password_button" disabled>`
        + `<span class="btn-icon pwm-icon pwm-icon-forward">&nbsp;</span>`
        + await PWM_MAIN.getDisplayString("Button_ChangePassword")
        + '</button>';

    if (clientData['pwUiMode'] === 'both') {
        buttonHtml += `<button type="button" name="button_random" class="btn" id="button_random">`
            + `<span class="btn-icon pwm-icon pwm-icon-retweet">&nbsp;</span>`
            + await PWM_MAIN.getDisplayString("Display_Random")
            + '</button>';
    }

    const dialogText = PWM_JSLibrary.getElement('template-helpdeskDialog').innerHTML;
    const dialogTitle = await PWM_MAIN.getDisplayString('Title_ChangePassword');
    PWM_MAIN.showDialog({text:dialogText,title:dialogTitle,showOk:false,showClose:true,buttonHtml:buttonHtml,loadFunction:async function(){
            PWM_MAIN.addEventHandler('button_random','click',function(){
                PWM_CHANGEPW.doRandomGeneration(randomConfig);
            })
            const changePasswordForm = PWM_JSLibrary.getElement('helpdeskChangePwDialog-form');
            PWM_MAIN.addEventHandler(changePasswordForm,"keyup, change",function(){
                PWM_CHANGEPW.validatePasswords(userKey,function(data){
                    const validationData = data['data'];
                    const pwOkay = validationData['passed'] && validationData['match'] === 'MATCH';
                    PWM_JSLibrary.getElement('password_button').disabled = !pwOkay;
                },{[PREF_KEY_VERIFICATION_STATE]:readVerificationState()});
            });
            PWM_MAIN.addEventHandler('password_button',"click",function(){
                submitPwChangeDialog();
            });
            PWM_MAIN.addEventHandler(changePasswordForm,"submit",function(event){
                PWM_JSLibrary.cancelEvent(event);
                submitPwChangeDialog();
                return false;
            });
            const display_pwPrompt = await PWM_MAIN.getDisplayString('Display_PasswordPrompt');
            PWM_MAIN.showInfo(display_pwPrompt);
        }
    });
}

async function handleGenericButtonAction(userKey,processAction,actionName) {
    const display_titleSuccess = await PWM_MAIN.getDisplayString('Title_Success');

    PWM_MAIN.showConfirmDialog({okAction:function(){
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                    const url = PWM_MAIN.addParamToUrl(null, 'processAction',processAction);
                    const options = {userKey:userKey,[PREF_KEY_VERIFICATION_STATE]:readVerificationState()};
                    if (actionName) {
                        options['actionName'] = actionName;
                    }
                    PWM_MAIN.ajaxRequest(url,function(data){
                        PWM_MAIN.showDialog({title:display_titleSuccess,text:data['successMessage'],okAction:function(){
                                gotoDetailView(userKey);
                            }});
                    },{content:options})
                }});
        }})
}

PWM_HELPDESK.initHelpdeskDetailPage = async function(userKey) {

    PWM_MAIN.addEventHandler('button-back','click',function(){
        gotoSearchView();
    });
    PWM_MAIN.addEventHandler('button-refresh','click',function(){
        gotoDetailView(userKey);
    });
    PWM_MAIN.addEventHandler('button-changePassword','click',function(){
        helpdeskPasswordChangeAction(userKey);
    });
    PWM_MAIN.addEventHandler('button-unlock','click',function(){
        handleGenericButtonAction(userKey,'unlockIntruder');
    });
    PWM_MAIN.addEventHandler('button-clearResponses','click',function(){
        handleGenericButtonAction(userKey,'clearResponses');
    });
    PWM_MAIN.addEventHandler('button-clearOtp','click',function(){
        handleGenericButtonAction(userKey,'clearOtp');
    });
    PWM_MAIN.addEventHandler('button-verification','click',function(){
        handleOptionalVerificationButton(userKey);
    });

    const clientData = await readHelpdeskClientData();
    PWM_JSLibrary.forEachInObject(clientData['actions'],function(key,value){
        PWM_MAIN.addEventHandler('button-action-' + key,'click',function(){
            handleGenericButtonAction(userKey,'executeAction',key);
        });
    })

    const cardViewElement = PWM_JSLibrary.getElement('detail-card-view');
    if (cardViewElement) {
        const clientData = await readHelpdeskClientData();
        const photoEnabled = clientData['enablePhoto'];
        cardViewElement.innerHTML = SearchResultToCardProcessor.makeHtmlCard(userKey,photoEnabled);
        SearchResultToCardProcessor.postProcessHtmlCard(userKey);
    }
}

PWM_HELPDESK.validateOtpCode = async function(userKey) {
    const dialogText = await PWM_MAIN.getDisplayString('Display_HelpdeskOtpValidation');
    validateCode({userKey:userKey, processAction:'validateOtpCode', dialogText:dialogText});
};
