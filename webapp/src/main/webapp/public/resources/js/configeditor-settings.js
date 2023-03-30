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

var PWM_CFGEDIT = PWM_CFGEDIT || {};
var PWM_CONFIG = PWM_CONFIG || {};
var PWM_MAIN = PWM_MAIN || {};
var PWM_VAR = PWM_VAR || {};
var PWM_SETTINGS = PWM_SETTINGS || {};

PWM_VAR['clientSettingCache'] = { };

// -------------------------- locale table handler ------------------------------------
var LocalizedStringValueHandler = {};

PWM_VAR['LocalizedStringValueHandler-settingData'] = {};
LocalizedStringValueHandler.init = function(settingKey, settingData) {
    console.log('LocalizedStringValueHandler init for ' + settingKey);

    if (settingData) {
        PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey] = settingData;
    } else {
        PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey] = PWM_SETTINGS['settings'][settingKey];
    }

    let parentDiv = 'table_setting_' + settingKey;
    PWM_MAIN.getObject(parentDiv).innerHTML = '<table id="tableTop_' + settingKey + '" style="border-width:0">';
    parentDiv = PWM_MAIN.getObject('tableTop_' + settingKey);

    PWM_VAR['clientSettingCache'][settingKey + "_parentDiv"] = parentDiv;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(settingKey, function(resultValue) {
        PWM_VAR['clientSettingCache'][settingKey] = resultValue;
        LocalizedStringValueHandler.draw(settingKey);
    });
};

LocalizedStringValueHandler.draw = function(settingKey) {
    const parentDiv = PWM_VAR['clientSettingCache'][settingKey + "_parentDiv"];
    const settingData = PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey];

    const resultValue = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    if (PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        parentDiv.innerHTML = '<button class="btn" id="button-' + settingKey + '-addValue"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value</button>';
        PWM_MAIN.addEventHandler('button-' + settingKey + '-addValue','click',function(){
            UILibrary.stringEditorDialog({
                title:'Add Value',
                textarea:('LOCALIZED_TEXT_AREA' === settingData['syntax']),
                regex:'pattern' in settingData ? settingData['pattern'] : '.+',
                placeholder:settingData['placeholder'],
                value:'',
                completeFunction:function(value){
                    LocalizedStringValueHandler.writeLocaleSetting(settingKey,'',value);
                }
            });
        })
    } else {
        for (const localeKey in resultValue) {
            LocalizedStringValueHandler.drawRow(parentDiv, settingKey, localeKey, resultValue[localeKey])
        }
        UILibrary.addAddLocaleButtonRow(parentDiv, settingKey, function(localeKey) {
            LocalizedStringValueHandler.addLocaleSetting(settingKey, localeKey);
        }, Object.keys(resultValue));
    }

    PWM_VAR['clientSettingCache'][settingKey] = resultValue;
};

LocalizedStringValueHandler.drawRow = function(parentDiv, settingKey, localeString, value) {
    const settingData = PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey];
    const inputID = 'value-' + settingKey + '-' + localeString;

    const newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    let tableHtml = '<td style="border-width:0; width: 15px">';
    if (localeString !== null && localeString.length > 0) {
        tableHtml += localeString;
    }
    tableHtml += '</td>';

    tableHtml += '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></ta>';

    tableHtml += '<td id="panel-' + inputID + '">';
    tableHtml += '<div id="value-' + inputID + '" class="configStringPanel"></div>';
    tableHtml += '</td>';

    const defaultLocale = (localeString === null || localeString.length < 1);
    const required = settingData['required'];
    const hasNonDefaultValues = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1 ;

    if (!defaultLocale || !required && !hasNonDefaultValues) {
        tableHtml += '<div style="width: 10px; height: 10px;" class="delete-row-icon action-icon pwm-icon pwm-icon-times"'
            + 'id="button-' + settingKey + '-' + localeString + '-deleteRow"></div>';
    }

    newTableRow.innerHTML = tableHtml;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    PWM_MAIN.addEventHandler("button-" + settingKey + '-' + localeString + "-deleteRow","click",function(){
        LocalizedStringValueHandler.removeLocaleSetting(settingKey, localeString);
    });
    UILibrary.addTextValueToElement('value-' + inputID, (value !== null && value.length > 0) ? value : ' ');

    const editFunction = function() {
        UILibrary.stringEditorDialog({
            title:'Edit Value',
            textarea:('LOCALIZED_TEXT_AREA' === settingData['syntax']),
            regex:'pattern' in settingData ? settingData['pattern'] : '.+',
            placeholder:settingData['placeholder'],
            value:value,
            completeFunction:function(value){
                LocalizedStringValueHandler.writeLocaleSetting(settingKey,localeString,value);
            }
        });
    };

    PWM_MAIN.addEventHandler("panel-" + inputID,'click',function(){ editFunction(); });
    PWM_MAIN.addEventHandler("button-" + inputID,'click',function(){ editFunction(); });
};

LocalizedStringValueHandler.writeLocaleSetting = function(settingKey, locale, value) {
    const existingValues = PWM_VAR['clientSettingCache'][settingKey];
    existingValues[locale] = value;
    PWM_CFGEDIT.writeSetting(settingKey, existingValues);
    LocalizedStringValueHandler.draw(settingKey);
};

LocalizedStringValueHandler.removeLocaleSetting = function(settingKey, locale) {
    const existingValues = PWM_VAR['clientSettingCache'][settingKey];
    delete existingValues[locale];
    PWM_CFGEDIT.writeSetting(settingKey, existingValues);
    LocalizedStringValueHandler.draw(settingKey);
};

LocalizedStringValueHandler.addLocaleSetting = function(settingKey, localeKey) {
    const existingValues = PWM_VAR['clientSettingCache'][settingKey];
    const settingData = PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey];
    if (localeKey in existingValues) {
        PWM_MAIN.showErrorDialog('Locale ' + localeKey + ' is already present.');
    } else {
        UILibrary.stringEditorDialog({
            title:'Add Value - ' + localeKey,
            textarea:('LOCALIZED_TEXT_AREA' === settingData['syntax']),
            regex:'pattern' in settingData ? settingData['pattern'] : '.+',
            placeholder:settingData['placeholder'],
            value:'',
            completeFunction:function(value){
                LocalizedStringValueHandler.writeLocaleSetting(settingKey,localeKey,value);
            }
        });
    }
};


// -------------------------- multi locale table handler ------------------------------------

const MultiLocaleTableHandler = {};

MultiLocaleTableHandler.initMultiLocaleTable = function(keyName) {
    console.log('MultiLocaleTableHandler init for ' + keyName);
    const parentDiv = 'table_setting_' + keyName;

    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        MultiLocaleTableHandler.draw(keyName);
    });
};

MultiLocaleTableHandler.draw = function(keyName) {
    const parentDiv = 'table_setting_' + keyName;
    const regExPattern = PWM_SETTINGS['settings'][keyName]['pattern'];

    const resultValue = PWM_VAR['clientSettingCache'][keyName];
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    for (const localeName in resultValue) {
        const localeTableRow = document.createElement("tr");
        localeTableRow.setAttribute("style", "border-width: 0;");

        const localeTdName = document.createElement("td");
        localeTdName.setAttribute("style", "border-width: 0; width:15px");
        localeTdName.innerHTML = localeName;
        localeTableRow.appendChild(localeTdName);

        const localeTdContent = document.createElement("td");
        localeTdContent.setAttribute("style", "border-width: 0; width: 525px");
        localeTableRow.appendChild(localeTdContent);

        const localeTableElement = document.createElement("table");
        localeTableElement.setAttribute("style", "border-width: 0px; width:525px; margin:0");
        localeTdContent.appendChild(localeTableElement);

        const multiValues = resultValue[localeName];

        for (const iteration in multiValues) {

            const valueTableRow = document.createElement("tr");

            const valueTd1 = document.createElement("td");
            valueTd1.setAttribute("style", "border-width: 0;");

            const inputID = "value-" + keyName + "-" + localeName + "-" + iteration;

            const inputElement = document.createElement("input");
            inputElement.setAttribute("id", inputID);
            inputElement.setAttribute("value", multiValues[iteration]);
            inputElement.setAttribute("onchange", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',this.value,'" + regExPattern + "')");
            inputElement.setAttribute("style", "width: 480px; padding: 5px;");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
            valueTd1.appendChild(inputElement);
            valueTableRow.appendChild(valueTd1);
            localeTableElement.appendChild(valueTableRow);

            // add remove button
            const imgElement = document.createElement("div");
            imgElement.setAttribute("style", "width: 10px; height: 10px;");
            imgElement.setAttribute("class", "delete-row-icon action-icon pwm-icon pwm-icon-times");
            imgElement.setAttribute("id", inputID + "-remove");
            valueTd1.appendChild(imgElement);
        }

        { // add row button for this locale group
            const newTableRow = document.createElement("tr");
            newTableRow.setAttribute("style", "border-width: 0");
            newTableRow.setAttribute("colspan", "5");

            const newTableData = document.createElement("td");
            newTableData.setAttribute("style", "border-width: 0;");

            const addItemButton = document.createElement("button");
            addItemButton.setAttribute("type", "button");
            addItemButton.setAttribute("onclick", "PWM_VAR['clientSettingCache']['" + keyName + "']['" + localeName + "'].push('');MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "',null,null,null,'" + regExPattern + "')");
            addItemButton.innerHTML = "Add Value";
            newTableData.appendChild(addItemButton);

            newTableRow.appendChild(newTableData);
            localeTableElement.appendChild(newTableRow);
        }

        if (localeName !== '') { // add remove locale x
            const imgElement2 = document.createElement("div");
            imgElement2.setAttribute("id", "div-" + keyName + "-" + localeName + "-remove");
            imgElement2.setAttribute("class", "delete-row-icon action-icon pwm-icon pwm-icon-times");
            const tdElement = document.createElement("td");
            tdElement.setAttribute("style", "border-width: 0; text-align: left; vertical-align: top;width 10px");

            localeTableRow.appendChild(tdElement);
            tdElement.appendChild(imgElement2);
        }

        const parentDivElement = PWM_MAIN.getObject(parentDiv);
        parentDivElement.appendChild(localeTableRow);

        { // add a spacer row
            const spacerTableRow = document.createElement("tr");
            spacerTableRow.setAttribute("style", "border-width: 0");
            parentDivElement.appendChild(spacerTableRow);

            const spacerTableData = document.createElement("td");
            spacerTableData.setAttribute("style", "border-width: 0");
            spacerTableData.innerHTML = "&nbsp;";
            spacerTableRow.appendChild(spacerTableData);
        }
    }

    const addLocaleFunction = function(value) {
        MultiLocaleTableHandler.writeMultiLocaleSetting(keyName, value, 0, '', regExPattern);
    };

    UILibrary.addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction, Object.keys(resultValue));
    PWM_VAR['clientSettingCache'][keyName] = resultValue;

    for (const localeName in resultValue) {
        const multiValues = resultValue[localeName];
        for (const iteration in multiValues) {
            const inputID = "value-" + keyName + "-" + localeName + "-" + iteration;
            {
                const removeID = inputID + "-remove";
                PWM_MAIN.addEventHandler(removeID, 'click', function () {
                    MultiLocaleTableHandler.writeMultiLocaleSetting(keyName, localeName, iteration, null, regExPattern);
                });
            }
            {
                const removeID = "div-" + keyName + "-" + localeName + "-remove";
                PWM_MAIN.addEventHandler(removeID, 'click', function () {
                    MultiLocaleTableHandler.writeMultiLocaleSetting(keyName, localeName, null, null, regExPattern);
                });
            }
        }
    }
};

MultiLocaleTableHandler.writeMultiLocaleSetting = function(settingKey, locale, iteration, value) {
    if (locale !== null) {
        if (PWM_VAR['clientSettingCache'][settingKey][locale] === null) {
            PWM_VAR['clientSettingCache'][settingKey][locale] = [ "" ];
        }

        if (iteration === null) {
            delete PWM_VAR['clientSettingCache'][settingKey][locale];
        } else {
            if (value === null) {
                PWM_VAR['clientSettingCache'][settingKey][locale].splice(iteration,1);
            } else {
                PWM_VAR['clientSettingCache'][settingKey][locale][iteration] = value;
            }
        }
    }

    PWM_CFGEDIT.writeSetting(settingKey, PWM_VAR['clientSettingCache'][settingKey]);
    MultiLocaleTableHandler.draw(settingKey);
};



// -------------------------- change password handler ------------------------------------

const ChangePasswordHandler = {};

ChangePasswordHandler.init = function(settingKey) {
    const parentDiv = 'table_setting_' + settingKey;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    if (parentDivElement) {
        PWM_CFGEDIT.readSetting(settingKey,function(data){
            const hasPassword = !data['isDefault'];
            let htmlBody = '';
            if (hasPassword) {
                htmlBody += '<table><tr><td>Value stored.</td></tr></table>';
                htmlBody += '<button id="button-clearPassword-' + settingKey + '" class="btn"><span class="btn-icon pwm-icon pwm-icon-times"></span>Clear Value</button>';
            } else {
                htmlBody += '<button id="button-changePassword-' + settingKey + '" class="btn"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Store Value</button>';
            }

            parentDivElement.innerHTML = htmlBody;

            PWM_MAIN.addEventHandler('button-changePassword-' + settingKey,'click',function(){
                ChangePasswordHandler.popup(settingKey,PWM_SETTINGS['settings'][settingKey]['label']);
            });

            PWM_MAIN.addEventHandler('button-clearPassword-' + settingKey,'click',function(){
                PWM_MAIN.showConfirmDialog({
                    text:'Clear password for setting ' + PWM_SETTINGS['settings'][settingKey]['label'] + '?',
                    okAction:function() {
                        PWM_CFGEDIT.resetSetting(settingKey,function(){
                            ChangePasswordHandler.init(settingKey);
                        });
                    }
                });
            });
        });
    }

};

ChangePasswordHandler.popup = function(settingKey,settingName,writeFunction) {
    if (!PWM_VAR['clientSettingCache'][settingKey]) {
        PWM_VAR['clientSettingCache'][settingKey] = {};
    }
    if (!PWM_VAR['clientSettingCache'][settingKey]['settings']) {
        PWM_VAR['clientSettingCache'][settingKey]['settings'] = {};
    }
    PWM_VAR['clientSettingCache'][settingKey]['settings']['name'] = settingName;
    if (writeFunction) {
        PWM_VAR['clientSettingCache'][settingKey]['settings']['writeFunction'] = writeFunction;
    } else {
        PWM_VAR['clientSettingCache'][settingKey]['settings']['writeFunction'] = function(passwordValue){
            ChangePasswordHandler.doChange(settingKey,passwordValue);
        }
    }
    PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = false;
    ChangePasswordHandler.clear(settingKey);
    ChangePasswordHandler.changePasswordPopup(settingKey);
};

ChangePasswordHandler.validatePasswordPopupFields = function(settingKey) {
    const password1 = PWM_MAIN.getObject('password1').value;
    const password2 = PWM_MAIN.getObject('password2').value;

    let matchStatus = "";

    const properties = settingKey === undefined || PWM_SETTINGS['settings'][settingKey] === undefined ? {} : PWM_SETTINGS['settings'][settingKey]['properties'];
    const minLength = properties && 'Minimum' in properties ? properties['Minimum'] : 1;

    PWM_MAIN.getObject('field-password-length').innerHTML = password1.length;
    PWM_MAIN.getObject('button-storePassword').disabled = true;

    if (minLength > 1 && password1.length < minLength) {
        PWM_MAIN.addCssClass('field-password-length','invalid-value');
    } else {
        PWM_MAIN.removeCssClass('field-password-length','invalid-value');
        if (password2.length > 0) {

            if (password1 === password2) {
                matchStatus = "MATCH";
                PWM_MAIN.getObject('button-storePassword').disabled = false;
            } else {
                matchStatus = "NO_MATCH";
            }
        }
    }

    ChangePasswordHandler.markConfirmationCheck(matchStatus);
};

ChangePasswordHandler.markConfirmationCheck = function(matchStatus) {
    if (matchStatus === "MATCH") {
        PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'visible';
        PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'hidden';
        PWM_MAIN.getObject("confirmCheckMark").width = '15';
        PWM_MAIN.getObject("confirmCrossMark").width = '0';
    } else if (matchStatus === "NO_MATCH") {
        PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'hidden';
        PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'visible';
        PWM_MAIN.getObject("confirmCheckMark").width = '0';
        PWM_MAIN.getObject("confirmCrossMark").width = '15';
    } else {
        PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'hidden';
        PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'hidden';
        PWM_MAIN.getObject("confirmCheckMark").width = '0';
        PWM_MAIN.getObject("confirmCrossMark").width = '0';
    }
};

ChangePasswordHandler.doChange = function(settingKey, passwordValue) {
    PWM_MAIN.showWaitDialog({loadFunction:function(){
            PWM_CFGEDIT.writeSetting(settingKey,passwordValue,function(){
                ChangePasswordHandler.clear(settingKey);
                ChangePasswordHandler.init(settingKey);
                PWM_MAIN.closeWaitDialog();
            });

        }})
};

ChangePasswordHandler.clear = function(settingKey) {
    PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'] = '';
    PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'] = '';
};

ChangePasswordHandler.generateRandom = function(settingKey) {
    const length = PWM_VAR['passwordDialog-randomLength'];
    const special = PWM_VAR['passwordDialog-special'];

    if (!PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields']) {
        PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = true;
    }

    let charMap = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    if (special) {
        charMap += '~`!@#$%^&*()_-+=;:,.[]{}';
    }
    const postData = { };
    postData.maxLength = length;
    postData.minLength = length;
    postData.chars = charMap;
    postData.noUser = true;
    PWM_MAIN.getObject('button-storePassword').disabled = true;

    const url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','randomPassword');
    const loadFunction = function(data) {
        ChangePasswordHandler.changePasswordPopup(settingKey);
        PWM_MAIN.getObject('password1').value = data['data']['password'];
        PWM_MAIN.getObject('password2').value = '';
        PWM_MAIN.getObject('button-storePassword').disabled = false;
    };

    PWM_MAIN.showWaitDialog({loadFunction:function(){
            PWM_MAIN.ajaxRequest(url,loadFunction,{content:postData});
        }});
};

ChangePasswordHandler.changePasswordPopup = function(settingKey) {
    const writeFunction = PWM_VAR['clientSettingCache'][settingKey]['settings']['writeFunction'];
    const showFields = PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'];
    const p1 = PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'];
    const p2 = PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'];
    const properties = settingKey === undefined || PWM_SETTINGS['settings'][settingKey] === undefined ? {} : PWM_SETTINGS['settings'][settingKey]['properties'];
    const minLength = properties && 'Minimum' in properties ? properties['Minimum'] : 1;
    let randomLength = 'passwordDialog-randomLength' in PWM_VAR ? PWM_VAR['passwordDialog-randomLength'] : 25;
    randomLength = randomLength < minLength ? minLength : randomLength;
    const special = 'passwordDialog-special' in PWM_VAR ? PWM_VAR['passwordDialog-special'] : false;

    let bodyText = '';
    if (minLength > 1) {
        bodyText += 'Minimum Length: ' + minLength + '</span><br/><br/>'
    }
    bodyText += '<table class="noborder">'
        + '<tr><td><span class="formFieldLabel">' + PWM_MAIN.showString('Field_NewPassword') + '</span></td></tr>'
        + '<tr><td>';

    if (showFields) {
        bodyText += '<textarea name="password1" id="password1" class="configStringInput" style="width: 400px; max-width: 400px; max-height:100px; overflow-y: auto" autocomplete="off">' + p1 + '</textarea>';
    } else {
        bodyText += '<input name="password1" id="password1" class="configStringInput" type="password" style="width: 400px;" autocomplete="off" value="' + p1 + '"></input>';
    }

    bodyText += '</td></tr>'
        + '<tr><td><span class="formFieldLabel">' + PWM_MAIN.showString('Field_ConfirmPassword') + '</span></td></tr>'
        + '<tr><td>';

    if (showFields) {
        bodyText += '<textarea name="password2" id="password2" class="configStringInput" style="width: 400px; max-width: 400px; max-height:100px; overflow-y: auto" autocomplete="off">' + p2 + '</textarea>';
    } else {
        bodyText += '<input name="password2" type="password" id="password2" class="configStringInput" style="width: 400px;" autocomplete="off" value="' + p2 + '"></input>';
    }

    bodyText += '</td>'
        + '<td><div style="margin:0;">'
        + '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/greenCheck.png">'
        + '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png">'
        + '</div></td>'
        + '</tr></table>';

    bodyText += '<br/>Length: <span id="field-password-length">-</span>';

    bodyText += '<br/><br/><div class="dialogSection" style="width: 400px"><span class="formFieldLabel">Generate Random Password </span><br/>'
        + '<label class="checkboxWrapper"><input id="input-special" type="checkbox"' + (special ? ' checked' : '') + '>Specials</input></label>'
        + '&nbsp;&nbsp;&nbsp;&nbsp;<input id="input-randomLength" type="number" min="10" max="1000" value="' + randomLength + '" style="width:45px">Length'
        + '&nbsp;&nbsp;&nbsp;&nbsp;<button id="button-generateRandom" name="button-generateRandom"><span class="pwm-icon pwm-icon-random btn-icon"></span>Generate Random</button>'
        + '</div><br/><br/>'
        + '<button name="button-storePassword" class="btn" id="button-storePassword" disabled="true"/>'
        + '<span class="pwm-icon pwm-icon-forward btn-icon"></span>Store Password</button>&nbsp;&nbsp;'
        + '<label class="checkboxWrapper"><input id="show" type="checkbox"' + (showFields ? ' checked' : '') + '>Show Passwords</input></label>'
        + '</div><br/><br/>';

    PWM_MAIN.showDialog({
        title: 'Store Password - ' + PWM_VAR['clientSettingCache'][settingKey]['settings']['name'],
        text: bodyText,
        showOk: false,
        showClose: true,
        loadFunction:function(){
            PWM_MAIN.addEventHandler('button-storePassword','click',function() {
                const passwordValue = PWM_MAIN.getObject('password1').value;
                PWM_MAIN.closeWaitDialog();
                writeFunction(passwordValue);
            });
            PWM_MAIN.addEventHandler('button-generateRandom','click',function() {
                PWM_VAR['passwordDialog-randomLength'] = PWM_MAIN.getObject('input-randomLength').value;
                PWM_VAR['passwordDialog-special'] = PWM_MAIN.getObject('input-special').checked;
                ChangePasswordHandler.generateRandom(settingKey);
            });
            PWM_MAIN.addEventHandler('password1','input',function(){
                PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'] = PWM_MAIN.getObject('password1').value;
                ChangePasswordHandler.validatePasswordPopupFields(settingKey);
                PWM_MAIN.getObject('password2').value = '';
            });
            PWM_MAIN.addEventHandler('password2','input',function(){
                PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'] = PWM_MAIN.getObject('password2').value;
                ChangePasswordHandler.validatePasswordPopupFields(settingKey);
            });
            PWM_MAIN.addEventHandler('show','change',function(){
                PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = PWM_MAIN.getObject('show').checked;
                ChangePasswordHandler.changePasswordPopup(settingKey);
            });
            PWM_MAIN.getObject('password1').focus();
            ChangePasswordHandler.validatePasswordPopupFields(settingKey);
        }
    });
};



// -------------------------- boolean handler ------------------------------------

const BooleanHandler = {};

BooleanHandler.init = function(keyName) {
    console.log('BooleanHandler init for ' + keyName);

    const parentDiv = 'table_setting_' + keyName;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    parentDivElement.innerHTML = '<label class="checkboxWrapper">'
        + '<input type="checkbox" id="value_' + keyName + '" value="false" disabled/>'
        + 'Enabled (True)</label>';

    PWM_CFGEDIT.readSetting(keyName,function(data){
        const checkElement = PWM_MAIN.getObject("value_" + keyName);
        checkElement.checked = data;
        checkElement.disabled = false;
        PWM_MAIN.addEventHandler("value_" + keyName, 'change', function(){
            PWM_CFGEDIT.writeSetting(keyName,checkElement.checked);
        });
    });
};

BooleanHandler.toggle = function(keyName,widget) {
    PWM_CFGEDIT.writeSetting(keyName,widget.checked);
};



// -------------------------- option list handler ------------------------------------

const OptionListHandler = {};
OptionListHandler.defaultItem = [];

OptionListHandler.init = function(keyName) {
    console.log('OptionListHandler init for ' + keyName);

    const parentDiv = 'table_setting_' + keyName;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    let htmlBody = '';
    const options = PWM_SETTINGS['settings'][keyName]['options'];
    for (const key in options) {
        (function (optionKey) {
            const buttonID = keyName + "_button_" + optionKey;
            htmlBody += '<label class="checkboxWrapper" style="min-width:180px;">'
                + '<input type="checkbox" id="' + buttonID + '" disabled/>'
                + options[optionKey] + '</label>';
        })(key);
    }
    parentDivElement.innerHTML = htmlBody;


    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        OptionListHandler.draw(keyName);
    });
};

OptionListHandler.draw = function(keyName) {
    const resultValue = PWM_VAR['clientSettingCache'][keyName];
    const options = PWM_SETTINGS['settings'][keyName]['options'];
    for (const key in options) {
        (function (optionKey) {
            const buttonID = keyName + "_button_" + optionKey;
            const checked = PWM_MAIN.JSLibrary.arrayContains(resultValue,optionKey)
            PWM_MAIN.getObject(buttonID).checked = checked;
            PWM_MAIN.getObject(buttonID).disabled = false;
            PWM_MAIN.addEventHandler(buttonID,'change',function(){
                OptionListHandler.toggle(keyName,optionKey);
            });
        })(key);
    }
};

OptionListHandler.toggle = function(keyName,optionKey) {
    const resultValue = PWM_VAR['clientSettingCache'][keyName];
    const checked = PWM_MAIN.JSLibrary.arrayContains(resultValue,optionKey)
    if (checked) {
        PWM_MAIN.JSLibrary.removeFromArray(resultValue, optionKey);
    } else {
        resultValue.push(optionKey);
    }
    PWM_CFGEDIT.writeSetting(keyName, resultValue);
};

// -------------------------- numeric value handler ------------------------------------

const NumericValueHandler = {};
NumericValueHandler.init = function(settingKey) {
    NumericValueHandler.impl(settingKey, 'number', 0, 100);
};

NumericValueHandler.impl = function(settingKey, type, defaultMin, defaultMax) {
    const parentDiv = 'table_setting_' + settingKey;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);
    const properties = PWM_SETTINGS['settings'][settingKey]['properties'];
    const min = 'Minimum' in properties ? parseInt(properties['Minimum']) : defaultMin;
    const max = 'Maximum' in properties ? parseInt(properties['Maximum']) : defaultMax;

    let htmlBody = '<input type="number" id="value_' + settingKey + '" class="configNumericInput" min="'+min+'" max="'+max+'"/>';
    if (type === 'number') {
        htmlBody += '<span class="configNumericLimits">' + min + ' - ' + max + '</span>';
    } else if (type === 'duration') {
        htmlBody +=  '<span class="configNumericLimits">' + PWM_MAIN.showString('Display_Seconds')  + '</span>'
        htmlBody +=  '<span style="margin-left:20px" id="display-' + settingKey + '-duration"></span>';
    }

    parentDivElement.innerHTML = htmlBody;

    PWM_CFGEDIT.readSetting(settingKey,function(data){
        PWM_MAIN.getObject('value_' + settingKey).value = data;
        UILibrary.manageNumericInput('value_' + settingKey,function(value){
            PWM_VAR['clientSettingCache'][settingKey] = value;
            PWM_CFGEDIT.writeSetting(settingKey, value);
            NumericValueHandler.updateDurationDisplay(settingKey,value);
        });

        PWM_MAIN.addEventHandler('value_' + settingKey,'mousewheel',function(e){ e.blur(); });
        NumericValueHandler.updateDurationDisplay(settingKey,data);
    });
};

NumericValueHandler.updateDurationDisplay = function(settingKey, numberValue) {
    numberValue = parseInt(numberValue);
    const displayElement = PWM_MAIN.getObject('display-' + settingKey + '-duration');
    if (displayElement) {
        displayElement.innerHTML = (numberValue && numberValue !== 0)
            ? PWM_MAIN.convertSecondsToDisplayTimeDuration(numberValue, true)
            : '';
    }
};



// -------------------------- duration value ---------------------------

const DurationValueHandler = {};
DurationValueHandler.init = function(settingKey) {
    NumericValueHandler.impl(settingKey, 'duration', -1, 365 * 24 * 60 * 60, 1 );
};

// -------------------------- numeric array value handler ------------------------------------

const NumericArrayValueHandler = {};
NumericArrayValueHandler.init = function(settingKey) {
    NumericArrayValueHandler.impl(settingKey, 'number', 0, 100);
};

NumericArrayValueHandler.impl = function(settingKey, type) {
    PWM_CFGEDIT.readSetting(settingKey,function(data){
        PWM_VAR['clientSettingCache'][settingKey] = data;
        NumericArrayValueHandler.draw(settingKey, type);
    });
};

NumericArrayValueHandler.draw = function(settingKey, type) {
    const resultValue = PWM_VAR['clientSettingCache'][settingKey];

    const parentDiv = 'table_setting_' + settingKey;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);
    const properties = PWM_SETTINGS['settings'][settingKey]['properties'];
    const min = 'Minimum' in properties ? parseInt(properties['Minimum']) : 1;
    const max = 'Maximum' in properties ? parseInt(properties['Maximum']) : 365 * 24 * 60 * 60;
    const minValues = 'Minimum_Values' in properties ? parseInt(properties['Minimum_Values']) : 1;
    const maxValues = 'Maximum_Values' in properties ? parseInt(properties['Maximum_Values']) : 10;

    let htmlBody = '<table class="noborder">';
    for (const iteration in resultValue) {
        (function(rowKey) {
            const id = settingKey+ "-" + rowKey;

            htmlBody += '<tr><td><input type="number" id="value-' + id + '" class="configNumericInput" min="'+min+'" max="'+max+'" disabled/>';
            if (type === 'number') {
                htmlBody += '<span class="configNumericLimits">' + min + ' - ' + max + '</span>';
            } else if (type === 'duration') {
                htmlBody +=  '<span class="configNumericLimits">' + PWM_MAIN.showString('Display_Seconds')  + '</span>'
                htmlBody +=  '<span style="margin-left:20px" id="display-' + id + '-duration"></span>';
            }
            htmlBody += '</td><td>';
            if ( resultValue.length > minValues ) {
                htmlBody += '<span id="button-' + id + '-delete" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></span>';
            }
            htmlBody += '</td></tr>';

        }(iteration));
    }

    htmlBody += '</table>';
    if ( resultValue.length < maxValues ) {
        htmlBody += '<br/><button class="btn" id="button-addValue-' + settingKey + '">';
        htmlBody += '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value';
        htmlBody += '</button>';
    }

    parentDivElement.innerHTML = htmlBody;

    const addListeners = function() {
        for (const iteration in resultValue) {
            (function(rowKey) {
                const id = settingKey+ "-" + rowKey;
                const readValue  = resultValue[rowKey];
                PWM_MAIN.getObject('value-' + id).value = readValue;
                PWM_MAIN.getObject('value-' + id).disabled = false;

                UILibrary.manageNumericInput('value-' + id,function(value){
                    PWM_VAR['clientSettingCache'][settingKey][rowKey] = value;
                    PWM_CFGEDIT.writeSetting(settingKey, PWM_VAR['clientSettingCache'][settingKey]);
                    NumericValueHandler.updateDurationDisplay(id, value);
                });

                PWM_MAIN.addEventHandler('value-' + settingKey,'mousewheel',function(e){ e.blur(); });
                NumericValueHandler.updateDurationDisplay(id, readValue);

                PWM_MAIN.addEventHandler('button-' + id + '-delete','click',function(){
                    PWM_MAIN.showConfirmDialog({okAction:function(){
                            PWM_VAR['clientSettingCache'][settingKey].splice(rowKey, 1);
                            PWM_CFGEDIT.writeSetting(settingKey, PWM_VAR['clientSettingCache'][settingKey],function(){
                                NumericArrayValueHandler.draw(settingKey, type);
                            });
                        }});
                });

            }(iteration));
        }

        PWM_MAIN.addEventHandler('button-addValue-' + settingKey,'click',function () {
            PWM_VAR['clientSettingCache'][settingKey].push(86400);
            PWM_CFGEDIT.writeSetting(settingKey, PWM_VAR['clientSettingCache'][settingKey],function(){
                NumericArrayValueHandler.draw(settingKey, type);
            });
        });
    };

    addListeners();
};


// -------------------------- duration array value ---------------------------

const DurationArrayValueHandler = {};
DurationArrayValueHandler.init = function(settingKey) {
    NumericArrayValueHandler.impl(settingKey, 'duration');
};


// -------------------------- string value handler ------------------------------------

const StringValueHandler = {};

StringValueHandler.init = function(settingKey) {
    const parentDiv = 'table_setting_' + settingKey;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);
    const settingData = PWM_SETTINGS['settings'][settingKey];
    const textAreaMode = 'TEXT_AREA' === settingData['syntax'];
    PWM_CFGEDIT.readSetting(settingKey,function(data) {
        const inputID = settingKey;
        let bodyHtml = '';
        const value = data;
        const cssClass = textAreaMode ? 'eulaText' : 'configStringPanel';
        if (value && value.length > 0) {
            bodyHtml += '<table style="border-width: 0">';
            bodyHtml += '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></ta>';
            bodyHtml += '<td style=""><div class="' + cssClass + '" id="panel-' + inputID + '"></div></td>';
            if (!settingData['required']) {
                bodyHtml += '<td style="border-width: 0"><span id="button-' + inputID + '-delete" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></span></td>';
            }

            bodyHtml += '</table>';
        } else {
            bodyHtml += '<button class="btn" id="button-add-' + inputID + '">';
            bodyHtml += '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value';
            bodyHtml += '</button>';
        }

        parentDivElement.innerHTML = bodyHtml;
        UILibrary.addTextValueToElement('panel-' + inputID, value);

        PWM_MAIN.addEventHandler('button-' + inputID + '-delete','click',function(){
            PWM_CFGEDIT.writeSetting(settingKey,'',function(){
                StringValueHandler.init(settingKey);
            });
        });

        const isLdapDN = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'], 'ldapDnSyntax');
        const editor = function(){
            const writeBackFunc = function(value){
                PWM_CFGEDIT.writeSetting(settingKey,value,function(){
                    StringValueHandler.init(settingKey);
                });
            };
            if (isLdapDN) {
                const ldapProfile = PWM_CFGEDIT.readCurrentProfile();
                UILibrary.editLdapDN(writeBackFunc,{currentDN: value, profile: ldapProfile});
            } else {
                UILibrary.stringEditorDialog({
                    title:'Edit Value - ' + settingData['label'],
                    textarea:(textAreaMode),
                    regex:'pattern' in settingData ? settingData['pattern'] : '.+',
                    placeholder:settingData['placeholder'],
                    value:value,
                    completeFunction:writeBackFunc
                });
            }
        };

        PWM_MAIN.addEventHandler('button-' + settingKey,'click',function(){
            editor();
        });
        PWM_MAIN.addEventHandler('button-add-' + settingKey,'click',function(){
            editor();
        });
        PWM_MAIN.addEventHandler('panel-' + settingKey,'click',function(){
            editor();
        });
    });
};


// -------------------------- text area handler ------------------------------------

const TextAreaValueHandler = {};
TextAreaValueHandler.init = function(settingKey) {
    StringValueHandler.init(settingKey);
};


// -------------------------- select value handler ------------------------------------

const SelectValueHandler = {};
SelectValueHandler.init = function(settingKey) {
    const parentDiv = 'table_setting_' + settingKey;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);
    const allowUserInput = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Select_AllowUserInput');

    let htmlBody = '<select id="setting_' + settingKey + '" disabled="true">'
        + '<option value="' + PWM_MAIN.showString('Display_PleaseWait') + '">' + PWM_MAIN.showString('Display_PleaseWait') + '</option></select>';

    if (allowUserInput) {
        htmlBody += '<button class="btn" id="button_selectOverride_' + settingKey + '">'
            + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value</button>';

    }

    parentDivElement.innerHTML = htmlBody;

    PWM_MAIN.addEventHandler('setting_' + settingKey,'change',function(){
        const settingElement = PWM_MAIN.getObject('setting_' + settingKey);
        const changeFunction = function(){
            const selectedValue = settingElement.options[settingElement.selectedIndex].value;
            PWM_CFGEDIT.writeSetting(settingKey,selectedValue);
            settingElement.setAttribute('previousValue',settingElement.selectedIndex);
        };
        if ('ModificationWarning' in PWM_SETTINGS['settings'][settingKey]['properties']) {
            PWM_MAIN.showConfirmDialog({
                text:PWM_SETTINGS['settings'][settingKey]['properties']['ModificationWarning'],
                cancelAction: function(){
                    settingElement.selectedIndex = settingElement.getAttribute('previousValue');
                    PWM_MAIN.closeWaitDialog();
                },
                okAction:changeFunction
            });
        } else {
            changeFunction();
        }
    });

    PWM_MAIN.addEventHandler('button_selectOverride_' + settingKey,'click',function() {
        const changeFunction = function(value){
            PWM_CFGEDIT.writeSetting(settingKey,value,function(){
                SelectValueHandler.init(settingKey);
            });
        };
        UILibrary.stringEditorDialog({
            title:'Set Value',
            value:'',
            completeFunction:function(value){
                changeFunction(value);
            }
        });
    });


    PWM_CFGEDIT.readSetting(settingKey, function(dataValue) {
        const settingElement = PWM_MAIN.getObject('setting_' + settingKey);

        let optionsHtml = '';
        const options = PWM_SETTINGS['settings'][settingKey]['options'];

        if (dataValue && dataValue.length > 0 && !(dataValue in options)) {
            optionsHtml += '<option value="' + dataValue + '">' + dataValue + '</option>'
        }
        for (const option in options) {
            const optionValue = options[option];
            optionsHtml += '<option value="' + option + '">' + optionValue + '</option>'
        }
        settingElement.innerHTML = optionsHtml;
        settingElement.value = dataValue;
        settingElement.disabled = false;
        settingElement.setAttribute('previousValue',settingElement.selectedIndex);
    });
};


// -------------------------- x509 setting handler ------------------------------------

const X509CertificateHandler = {};

X509CertificateHandler.init = function(keyName) {
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        X509CertificateHandler.draw(keyName);
    });
};

X509CertificateHandler.certificateToHtml = function(certificate, keyName, id) {
    let htmlBody = '';
    htmlBody += '<div style="max-width:100%; margin-bottom:8px"><table style="max-width:100%" id="table_certificate' + keyName + '-' + id + '">';
    htmlBody += '<tr><td colspan="2" class="key" style="text-align: center">Certificate ' + id + '  <a id="certTimestamp-detail-' + keyName + '-' + id + '">(detail)</a></td></tr>';
    htmlBody += '<tr><td>Subject</td><td><div class="setting_table_value">' + certificate['subject'] + '</div></td></tr>';
    htmlBody += '<tr><td>Issuer</td><td><div class="setting_table_value">' + certificate['issuer'] + '</div></td></tr>';
    htmlBody += '<tr><td>Serial</td><td><div class="setting_table_value">' + certificate['serial'] + '</div></td></tr>';
    htmlBody += '<tr><td>Issue Date</td><td id="certTimestamp-issue-' + keyName + '-' + id + '" class="setting_table_value timestamp">' + certificate['issueDate'] + '</td></tr>';
    htmlBody += '<tr><td>Expire Date</td><td id="certTimestamp-expire-' + keyName + '-' + id + '" class="setting_table_value timestamp">' + certificate['expireDate'] + '</td></tr>';
    htmlBody += '<tr><td>MD5 Hash</td><td><div class="setting_table_value">' + certificate['md5Hash'] + '</div></td></tr>';
    htmlBody += '<tr><td>SHA1 Hash</td><td><div class="setting_table_value">' + certificate['sha1Hash'] + '</div></td></tr>';
    htmlBody += '<tr><td>SHA512 Hash</td><td><div class="setting_table_value">' + certificate['sha512Hash'] + '</div></td></tr>';
    htmlBody += '</table></div>';
    return htmlBody;
};

X509CertificateHandler.certHtmlActions = function(certificate, keyName, id) {
    PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('certTimestamp-issue-' + keyName + '-' + id));
    PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('certTimestamp-expire-' + keyName + '-' + id));
    PWM_MAIN.addEventHandler('certTimestamp-detail-' + keyName + '-' + id,'click',function(){
        PWM_MAIN.showDialog({
            title: 'Detail - ' + PWM_SETTINGS['settings'][keyName]['label'] + ' - Certificate ' + id,
            text: '<pre>' + certificate['detail'] + '</pre>',
            dialogClass: 'wide',
            showClose: true,
            showOk: false
        });
    });
};

X509CertificateHandler.draw = function(keyName) {
    const parentDiv = 'table_setting_' + keyName;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    const resultValue = PWM_VAR['clientSettingCache'][keyName];

    let htmlBody = '<div>';
    for (const certCounter in resultValue) {
        (function (counter) {
            const certificate = resultValue[counter];
            htmlBody += X509CertificateHandler.certificateToHtml(certificate, keyName, counter);
        })(certCounter);
    }
    htmlBody += '</div>';

    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        htmlBody += '<button id="' + keyName + '_ClearButton" class="btn"><span class="btn-icon pwm-icon pwm-icon-times"></span>Clear</button>'
    }
    htmlBody += '<button id="' + keyName + '_AutoImportButton" class="btn"><span class="btn-icon pwm-icon pwm-icon-download"></span>Import From Server</button>'
    parentDivElement.innerHTML = htmlBody;

    for (const certCounter in resultValue) {
        (function (counter) {
            const certificate = resultValue[counter];
            X509CertificateHandler.certHtmlActions(certificate, keyName, counter)
        })(certCounter);
    }

    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        PWM_MAIN.addEventHandler(keyName + '_ClearButton','click',function(){
            handleResetClick(keyName);
        });
    }
    const importClassname = PWM_SETTINGS['settings'][keyName]['properties']['Cert_ImportHandler'];
    PWM_MAIN.addEventHandler(keyName + '_AutoImportButton','click',function(){
        PWM_CFGEDIT.executeSettingFunction(keyName,importClassname);
    });
};


// -------------------------- verification method handler ------------------------------------

const VerificationMethodHandler = {};
VerificationMethodHandler.init = function(settingKey) {
    PWM_CFGEDIT.readSetting(settingKey, function(resultValue) {
        PWM_VAR['clientSettingCache'][settingKey] = resultValue;
        VerificationMethodHandler.draw(settingKey);
    });
};

VerificationMethodHandler.draw = function(settingKey) {
    const settingOptions = PWM_SETTINGS['settings'][settingKey]['options'];
    const parentDiv = 'table_setting_' + settingKey;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    const showMinOptional = !PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Verification_HideMinimumOptional');

    let htmlBody = '<table class="">';
    for (const method in settingOptions) {
        const id = settingKey + '-' + method;
        const label = settingOptions[method];
        const title = PWM_CONFIG.showString('VerificationMethodDetail_' + method);
        htmlBody += '<tr><td title="' + title + '"><span style="cursor:pointer")">' + label + '</span></td><td><input id="input-range-' + id + '" type="range" min="0" max="2" value="0"/></td>';
        htmlBody += '<td><span id="label-' + id +'"></span></td></tr>';
    }
    htmlBody += '</table>';

    if (showMinOptional) {
        htmlBody += '<br/><label>Minimum Optional Required <input min="0" style="width:30px;" id="input-minOptional-' + settingKey + '" type="number" value="0" class="configNumericInput""></label>';
    }
    parentDivElement.innerHTML = htmlBody;
    for (const method in settingOptions) {
        const id = settingKey + '-' + method;
        PWM_MAIN.addEventHandler('input-range-' + id,'change',function(){
            VerificationMethodHandler.updateLabels(settingKey);
            VerificationMethodHandler.write(settingKey);
        });

        const enabledState = PWM_VAR['clientSettingCache'][settingKey]['methodSettings'][method]
            && PWM_VAR['clientSettingCache'][settingKey]['methodSettings'][method]['enabledState'];

        let numberValue = 0;
        if (enabledState) {
            switch (enabledState) {
                case 'disabled':
                    numberValue = 0;
                    break;
                case 'optional':
                    numberValue = 1;
                    break;
                case 'required':
                    numberValue = 2;
                    break;
                default:
                    alert('unknown value = VerificationMethodHandler.draw = ' + method);
            }
        }
        PWM_MAIN.getObject('input-range-' + id).value = numberValue;
    }
    if (showMinOptional) {
        PWM_MAIN.getObject('input-minOptional-' + settingKey).value = PWM_VAR['clientSettingCache'][settingKey]['minOptionalRequired'];
        PWM_MAIN.addEventHandler('input-minOptional-' + settingKey, 'input', function () {
            VerificationMethodHandler.updateLabels(settingKey);
            VerificationMethodHandler.write(settingKey);
        });
    }

    VerificationMethodHandler.updateLabels(settingKey);
};

VerificationMethodHandler.write = function(settingKey) {
    const showMinOptional = !PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Verification_HideMinimumOptional');

    const settingOptions = PWM_SETTINGS['settings'][settingKey]['options'];
    const values = {};
    values['minOptionalRequired'] = showMinOptional ? Number(PWM_MAIN.getObject('input-minOptional-' + settingKey).value) : 0;
    values['methodSettings'] = {};
    for (const method in settingOptions) {
        const id = settingKey + '-' + method;
        const value = Number(PWM_MAIN.getObject('input-range-' + id).value);

        let enabledState = 'disabled';
        switch (value) {
            case 0:
                enabledState = 'disabled';
                break;
            case 1:
                enabledState = 'optional';
                break;
            case 2:
                enabledState = 'required';
                break;
        }
        values['methodSettings'][method] = {};
        values['methodSettings'][method]['enabledState'] = enabledState;
    }
    PWM_CFGEDIT.writeSetting(settingKey, values);
};

VerificationMethodHandler.updateLabels = function(settingKey) {
    const settingOptions = PWM_SETTINGS['settings'][settingKey]['options'];
    let optionalCount = 0;
    for (const method in settingOptions) {
        const id = settingKey + '-' + method;
        const value = Number(PWM_MAIN.getObject('input-range-' + id).value);
        let label = '';
        switch (value) {
            case 0:
                label = 'Not Used';
                break;
            case 1:
                label = 'Optional';
                optionalCount++;
                break;
            case 2:
                label = 'Required';
                break;
            default:
                alert('unknown value = VerificationMethodHandler.updateLabels');
        }
        PWM_MAIN.getObject('label-' + id).innerHTML = label;
    }
    const showMinOptional = !PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Verification_HideMinimumOptional');
    if (showMinOptional) {
        const minOptionalInput = PWM_MAIN.getObject('input-minOptional-' + settingKey);
        minOptionalInput.max = optionalCount;
        const currentMax = Number(minOptionalInput.value);
        if (currentMax > optionalCount) {
            minOptionalInput.value = optionalCount.toString();
        }
    }
};


// -------------------------- file setting handler ------------------------------------

const FileValueHandler = {};

FileValueHandler.init = function(keyName) {
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        FileValueHandler.draw(keyName);
    });
};

FileValueHandler.draw = function(keyName) {
    const parentDiv = 'table_setting_' + keyName;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    const resultValue = PWM_VAR['clientSettingCache'][keyName];

    let htmlBody = '';

    if (PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        htmlBody = '<p>No File Present</p>';
    } else {
        for (const fileCounter in resultValue) {
            (function (counter) {
                const fileInfo = resultValue[counter];
                htmlBody += '<table style="width:100%" id="table_file' + keyName + '-' + counter + '">';
                htmlBody += '<tr><td colspan="2" class="key" style="text-align: center">File' + '</td></tr>';
                htmlBody += '<tr><td>Name</td><td class="setting_table_value">' + fileInfo['name'] + '</td></tr>';
                htmlBody += '<tr><td>Size</td><td class="setting_table_value">' + fileInfo['size'] + '</td></tr>';
                htmlBody += '<tr><td>SHA512 checksum</td><td class="setting_table_value">' + fileInfo['sha512sum'] + '</td></tr>';
                htmlBody += '</table>'
            })(fileCounter);
        }
    }

    if (PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        htmlBody += '<button class="btn" id="button-uploadFile-' + keyName + '"><span class="btn-icon pwm-icon pwm-icon-upload"></span>Upload File</button>';
    } else {
        htmlBody += '<button class="btn" id="button-removeFile-' + keyName + '"><span class="btn-icon pwm-icon pwm-icon-trash-o"></span>Remove File</button>';
    }

    parentDivElement.innerHTML = htmlBody;

    PWM_MAIN.addEventHandler('button-uploadFile-' + keyName,'click',function(){
        FileValueHandler.uploadFile(keyName);
    });

    PWM_MAIN.addEventHandler('button-removeFile-' + keyName,'click',function(){
        PWM_MAIN.showConfirmDialog({text:'Are you sure you want to remove the currently stored file?',okAction:function(){
                PWM_MAIN.showWaitDialog({loadFunction:function(){
                        PWM_CFGEDIT.resetSetting(keyName,function(){
                            FileValueHandler.init(keyName);
                            PWM_MAIN.closeWaitDialog();
                        });
                    }});
            }});
    });
};

FileValueHandler.uploadFile = function(keyName) {
    let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','uploadFile');
    url = PWM_MAIN.addParamToUrl(url, 'key',keyName);

    const options = {};
    options['url'] = url;
    options['nextFunction'] = function() {
        PWM_MAIN.showWaitDialog({loadFunction:function(){
                FileValueHandler.init(keyName);
                PWM_MAIN.closeWaitDialog();
            }});
    };
    UILibrary.uploadFileDialog(options);
};


// -------------------------- x509 setting handler ------------------------------------

const PrivateKeyHandler = {};

PrivateKeyHandler.init = function(keyName) {
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        PrivateKeyHandler.draw(keyName);
    });
};

PrivateKeyHandler.draw = function(keyName) {
    const parentDiv = 'table_setting_' + keyName;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    const resultValue = PWM_VAR['clientSettingCache'][keyName];

    let htmlBody = '<div>';

    const hasValue = resultValue !== undefined && 'key' in resultValue;

    if (hasValue) {
        const certificates = resultValue['certificates'];
        for (const certCounter in certificates) {
            (function (counter) {
                const certificate = certificates[counter];
                htmlBody += X509CertificateHandler.certificateToHtml(certificate, keyName, counter);
            })(certCounter);
        }
        htmlBody += '</div>';

        const key = resultValue['key'];
        htmlBody += '<div style="max-width:100%; margin-bottom:8px"><table style="max-width:100%">';
        htmlBody += '<tr><td colspan="2" class="key" style="text-align: center">Key</td></tr>';
        htmlBody += '<tr><td>Format</td><td><div class="setting_table_value">' + key['format'] + '</div></td></tr>';
        htmlBody += '<tr><td>Algorithm</td><td><div class="setting_table_value">' + key['algorithm'] + '</div></td></tr>';
        htmlBody += '</table></div>';
        htmlBody += '<button id="' + keyName + '_ClearButton" class="btn"><span class="btn-icon pwm-icon pwm-icon-times"></span>Remove Certificate</button>'
    } else {
        htmlBody += '<div>No Key Present</div><br/>';
    }

    if (!hasValue) {
        htmlBody += '<button id="' + keyName + '_UploadButton" class="btn"><span class="btn-icon pwm-icon pwm-icon-upload"></span>Import Private Key &amp; Certificate</button>'
    }
    parentDivElement.innerHTML = htmlBody;

    if (hasValue) {
        const certificates = resultValue['certificates'];
        for (const certCounter in certificates) {
            (function (counter) {
                const certificate = certificates[counter];
                X509CertificateHandler.certHtmlActions(certificate, keyName, counter);
            })(certCounter);
        }
    }

    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        PWM_MAIN.addEventHandler(keyName + '_ClearButton','click',function(){
            handleResetClick(keyName);
        });
    }
    PWM_MAIN.addEventHandler(keyName + '_UploadButton','click',function(){
        const options = {};
        let url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','uploadFile');
        url = PWM_MAIN.addParamToUrl(url, 'key', keyName);
        options['url'] = url;

        let text = '<form autocomplete="off"><table class="noborder">';
        text += '<tr><td class="key">File Format</td><td><select id="input-certificateUpload-format"><option value="PKCS12">PKCS12 / PFX</option><option value="JKS">Java Keystore (JKS)</option></select></td></tr>';
        text += '<tr><td class="key">Password</td><td><input type="password" class="configInput" id="input-certificateUpload-password"/></td></tr>';
        text += '<tr><td class="key">Alias</td><td><input type="text" class="configInput" id="input-certificateUpload-alias"/><br/><span class="footnote">Alias only required if file has multiple aliases</span></td></tr>';
        text += '</table></form>';
        options['text'] = text;

        const urlUpdateFunction = function(url) {
            const formatSelect = PWM_MAIN.getObject('input-certificateUpload-format');
            url = PWM_MAIN.addParamToUrl(url,'format',formatSelect.options[formatSelect.selectedIndex].value);
            url = PWM_MAIN.addParamToUrl(url,'password',PWM_MAIN.getObject('input-certificateUpload-password').value);
            url = PWM_MAIN.addParamToUrl(url,'alias',PWM_MAIN.getObject('input-certificateUpload-alias').value);
            return url;
        };
        options['title'] = 'Import Private Key & Certificate';
        options['urlUpdateFunction'] = urlUpdateFunction;
        UILibrary.uploadFileDialog(options);
    });
};


//--------- named secret handler ---
const NamedSecretHandler = {};

NamedSecretHandler.init = function(settingKey) {
    const parentDiv = 'table_setting_' + settingKey;
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    if (parentDivElement) {
        PWM_CFGEDIT.readSetting(settingKey,function(data){
            PWM_VAR['clientSettingCache'][settingKey] = data;
            let htmlBody = '';
            htmlBody += '<table>';
            let rowCounter = 0;
            for (const key in data) {
                const id = settingKey + '_' + key;
                htmlBody += '<tr>';
                htmlBody += '<td>' + key + '</td><td>Stored Value</td><td><button id="button-usage-' + id + '"><span class="btn-icon pwm-icon pwm-icon-sliders"/>Usage</button></td>';
                htmlBody += '<td style="width:10px"><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-deleteRow-' + id + '"></span></td>';
                htmlBody += '</tr>';
                rowCounter++;
            }

            if (rowCounter < 1) {
                htmlBody += '<tr><td>No values.</td></tr>';
            }

            htmlBody += '</table>';

            htmlBody += '<button id="button-addPassword-' + settingKey + '" class="btn"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value</button>';
            parentDivElement.innerHTML = htmlBody;

            PWM_MAIN.addEventHandler('button-addPassword-' + settingKey,'click',function(){
                NamedSecretHandler.addPassword(settingKey);
            });

            for (const key in data) {
                (function (loopKey) {
                    const id = settingKey + '_' + loopKey;
                    PWM_MAIN.addEventHandler('button-deleteRow-' + id,'click',function(){
                        NamedSecretHandler.deletePassword(settingKey, loopKey);
                    });
                    PWM_MAIN.addEventHandler('button-usage-' + id,'click',function(){
                        NamedSecretHandler.usagePopup(settingKey, loopKey);
                    });
                })(key);
            }
        });
    }
};

NamedSecretHandler.usagePopup = function(settingKey, key) {
    const titleText = PWM_SETTINGS['settings'][settingKey]['label'] + ' - Usage - ' + key ;
    const options = PWM_SETTINGS['settings'][settingKey]['options'];
    const currentValues = PWM_VAR['clientSettingCache'][settingKey];
    let html = '<table class="noborder">';
    for (const loopKey in options) {
        (function (optionKey) {
            html += '<tr><td>';
            const buttonID = key + "_usage_button_" + optionKey;
            html += '<label class="checkboxWrapper" style="min-width:180px;">'
                + '<input type="checkbox" id="' + buttonID + '"/>'
                + options[optionKey] + '</label>';
            html += '</td></tr>';
        })(loopKey);
    }
    html += '</table>';
    const loadFunction = function () {
        for (const loopKey in options) {
            (function (optionKey) {
                const buttonID = key + "_usage_button_" + optionKey;
                const checked = PWM_MAIN.JSLibrary.arrayContains(currentValues[key]['usage'],optionKey);
                PWM_MAIN.getObject(buttonID).checked = checked;
                PWM_MAIN.addEventHandler(buttonID,'click',function(){
                    const nowChecked = PWM_MAIN.getObject(buttonID).checked;
                    if (nowChecked) {
                        currentValues[key]['usage'].push(optionKey);
                    } else {
                        PWM_MAIN.JSLibrary.removeFromArray(currentValues[key]['usage'], optionKey);
                    }
                });
            })(loopKey);
        }
    };
    const okFunction = function() {
        const postWriteFunction = function() {
            NamedSecretHandler.init(settingKey);
        };
        PWM_CFGEDIT.writeSetting(settingKey, currentValues, postWriteFunction);
    };
    PWM_MAIN.showDialog({title:titleText,text:html,loadFunction:loadFunction,okAction:okFunction});
};

NamedSecretHandler.addPassword = function(settingKey) {
    const titleText = PWM_SETTINGS['settings'][settingKey]['label'] + ' - Name';
    const stringEditorFinishFunc = function(nameValue) {
        const currentValues = PWM_VAR['clientSettingCache'][settingKey];
        if (nameValue in currentValues) {;
            const errorTxt = '"' + nameValue + '" already exists.';
            PWM_MAIN.showErrorDialog(errorTxt);
            return;
        }
        const pwDialogOptions = {};
        pwDialogOptions['title'] = PWM_SETTINGS['settings'][settingKey]['label'] + ' - Password';
        pwDialogOptions['showRandomGenerator'] = true;
        pwDialogOptions['showValues'] = true;
        pwDialogOptions['writeFunction'] = function(pwValue) {
            currentValues[nameValue] = {};
            currentValues[nameValue]['password'] = pwValue;
            currentValues[nameValue]['usage'] = [];

            const postWriteFunction = function() {
                NamedSecretHandler.init(settingKey);
            };

            PWM_CFGEDIT.writeSetting(settingKey, currentValues, postWriteFunction);
        };
        UILibrary.passwordDialogPopup(pwDialogOptions)
    };
    const instructions = 'Please enter the name for the new password.';
    UILibrary.stringEditorDialog({
        title: titleText,
        regex: '[a-zA-Z]{2,20}',
        instructions: instructions,
        completeFunction: stringEditorFinishFunc
    });
};


NamedSecretHandler.deletePassword = function(settingKey, key) {
    PWM_MAIN.showConfirmDialog({
        text:'Delete named password <b>' + key + '</b>?',
        okAction:function() {
            const currentValues = PWM_VAR['clientSettingCache'][settingKey];
            delete currentValues[key];

            const postWriteFunction = function() {
                NamedSecretHandler.init(settingKey);
            };
            PWM_CFGEDIT.writeSetting(settingKey, currentValues, postWriteFunction);
        }
    });

};


