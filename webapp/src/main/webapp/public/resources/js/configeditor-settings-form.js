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

const FormTableHandler = {};

import {PWM_CFGEDIT} from "./configeditor.js";
import {PWM_CONFIG} from "./configmanager.js";
import {PWM_MAIN} from "./main.js";
import {PWM_JSLibrary} from "./jslibrary.js";
import {PWM_UILibrary} from "./uilibrary.js";

export {FormTableHandler};

const ClientSettingCache = {};


FormTableHandler.newRowValue = {
    name:'',
    minimumLength:0,
    maximumLength:255,
    labels:{'':''},
    regexErrors:{'':''},
    selectOptions:{},
    description:{'':''},
    type:'text',
    placeholder:'',
    javascript:'',
    regex:'',
    source:'ldap',
    maximumSize:65000
};

FormTableHandler.init = function(keyName) {
    console.log('FormTableHandler init for ' + keyName);
    const parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        ClientSettingCache[keyName] = resultValue;
        FormTableHandler.redraw(keyName);
    });
};

FormTableHandler.redraw = async function(keyName) {
    const settingData = await PWM_CFGEDIT.getConfigSettingData();
    const resultValue = ClientSettingCache[keyName];
    {
        const parentDiv = 'table_setting_' + keyName;
        const parentDivElement = PWM_JSLibrary.getElement(parentDiv);
        parentDivElement.innerHTML = '<table class="noborder" style="margin-left: 0; width:auto" id="table-top-' + keyName + '"></table>';
    }
    const parentDiv = 'table-top-' + keyName;
    const parentDivElement = PWM_JSLibrary.getElement(parentDiv);


    if (!PWM_JSLibrary.isEmpty(resultValue)) {
        const headerRow = document.createElement("tr");
        const rowHtml = '<td>Name</td><td></td><td>Label</td>';
        headerRow.innerHTML = rowHtml;
        parentDivElement.appendChild(headerRow);
    }

    PWM_JSLibrary.forEachInObject(resultValue,function (i,value){
        FormTableHandler.drawRow(parentDiv, keyName, i, value, settingData);
    });

    const buttonRowHtml = `<tr>`
        +  `<td colspan="5"><button class="btn" id="button-${keyName}-addRow"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Item</button></td>`
        + `</tr>`;
    parentDivElement.insertAdjacentHTML("beforeend", buttonRowHtml );

    PWM_MAIN.addEventHandler('button-' + keyName + '-addRow','click',function(){
        FormTableHandler.addRow(keyName);
    });

};

FormTableHandler.drawRow = function(parentDiv, settingKey, iteration, value, settingData) {
    const itemCount = PWM_JSLibrary.itemCount(ClientSettingCache[settingKey]);
    const inputID = 'value_' + settingKey + '_' + iteration + "_";
    const options = settingData['settings'][settingKey]['options'];
    const properties = settingData['settings'][settingKey]['properties'];

    const newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    let htmlRow = `<td class="setting_form_column"><div class="noWrapTextBox" id="panel-name-${inputID}"></div></td>`
     + `<td id="icon-editLabel-${inputID}"><span class="btn-icon pwm-icon pwm-icon-edit"></span></td>`
     + `<td class="setting_form_column"><div class="noWrapTextBox" id="${inputID}label"><span>${value['labels']['']}</span></div></td>`;

    const userDNtypeAllowed = options['type-userDN'] === 'show';
    if (!PWM_JSLibrary.isEmpty(options)) {
        htmlRow += '<td style="width:15px;">';
        htmlRow += '<select id="' + inputID + 'type">';
        for (const optionItem in options) {
            if (optionList[optionItem] !== 'userDN' || userDNtypeAllowed) {
            const optionName = options[optionItem];
            const selected = (optionName === ClientSettingCache[settingKey][iteration]['type']);
            htmlRow += '<option value="' + optionName + '"' + (selected ? " selected" : "") + '>' + optionName + '</option>';
            }
        }
        htmlRow += '</select>';
        htmlRow += '</td>';
    }

    const hideOptions = PWM_JSLibrary.arrayContains(settingData['settings'][settingKey]['flags'], 'Form_HideOptions');
    if (!hideOptions) {
        htmlRow += '<td class="noborder" style="min-width:90px;"><button id="' + inputID + 'optionsButton"><span class="btn-icon pwm-icon pwm-icon-sliders"/> Options</button></td>';
    }

    htmlRow += '<td style="width:10px">';
    if (itemCount > 1 && iteration != (itemCount -1)) {
        htmlRow += '<span id="' + inputID + '-moveDown" class="action-icon pwm-icon pwm-icon-chevron-down"></span>';
    }
    htmlRow += '</td>';

    htmlRow += '<td style="width:10px">';
    if (itemCount > 1 && iteration != 0) {
        htmlRow += '<span id="' + inputID + '-moveUp" class="action-icon pwm-icon pwm-icon-chevron-up"></span>';
    }
    htmlRow += '</td>';
    htmlRow += '<td style="width:10px"><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="' + inputID + '-deleteRowButton"></span></td>';

    newTableRow.innerHTML = htmlRow;
    const parentDivElement = PWM_JSLibrary.getElement(parentDiv);
    parentDivElement.appendChild(newTableRow);

    PWM_UILibrary.addTextValueToElement("panel-name-" + inputID,value['name']);

    PWM_MAIN.addEventHandler(inputID + "-moveUp", 'click', function () {
        FormTableHandler.move(settingKey, true, iteration);
    });
    PWM_MAIN.addEventHandler(inputID + "-moveDown", 'click', function () {
        FormTableHandler.move(settingKey, false, iteration);
    });
    PWM_MAIN.addEventHandler(inputID + "-deleteRowButton", 'click', function () {
        FormTableHandler.removeRow(settingKey, iteration);
    });
    PWM_MAIN.addEventHandler(inputID + "label", 'click, keypress', function () {
        FormTableHandler.showLabelDialog(settingKey, iteration);
    });
    PWM_MAIN.addEventHandler("icon-editLabel-" + inputID, 'click, keypress', function () {
        FormTableHandler.showLabelDialog(settingKey, iteration);
    });
    PWM_MAIN.addEventHandler(inputID + "optionsButton", 'click', function () {
        FormTableHandler.showOptionsDialog(settingKey, iteration);
    });
    PWM_MAIN.addEventHandler(inputID + "name", 'input', function () {
        ClientSettingCache[settingKey][iteration]['name'] = PWM_JSLibrary.getElement(inputID + "name").value;
        FormTableHandler.write(settingKey);
    });
    PWM_MAIN.addEventHandler(inputID + "type", 'click', function () {
        ClientSettingCache[settingKey][iteration]['type'] = PWM_JSLibrary.getElement(inputID + "type").value;
        FormTableHandler.write(settingKey);
    });
};

FormTableHandler.write = function(settingKey, finishFunction) {
    const cachedSetting = ClientSettingCache[settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

FormTableHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            const currentValues = ClientSettingCache[keyName];
            currentValues.splice(iteration,1);
            FormTableHandler.write(keyName,function(){
                FormTableHandler.init(keyName);
            });
        }
    });
};

FormTableHandler.move = function(settingKey, moveUp, iteration) {
    const currentValues = ClientSettingCache[settingKey];
    if (moveUp) {
        FormTableHandler.arrayMoveUtil(currentValues, iteration, iteration - 1);
    } else {
        FormTableHandler.arrayMoveUtil(currentValues, iteration, iteration + 1);
    }
    FormTableHandler.write(settingKey);
    FormTableHandler.redraw(settingKey);
};

FormTableHandler.arrayMoveUtil = function(arr, fromIndex, toIndex) {
    const element = arr[fromIndex];
    arr.splice(fromIndex, 1);
    arr.splice(toIndex, 0, element);
};


FormTableHandler.addRow = async function(keyName) {
    const settingData = await PWM_CFGEDIT.getConfigSettingData();

    PWM_UILibrary.stringEditorDialog({
        title:settingData['settings'][keyName]['label'] + ' - New Form Field',
        regex:'^[a-zA-Z][a-zA-Z0-9-]*$',
        placeholder:'FieldName',
        completeFunction:function(value){
            for (const i in ClientSettingCache[keyName]) {
                if (ClientSettingCache[keyName][i]['name'] === value) {
                    alert('field already exists');
                    return;
                }
            }
            const currentSize = PWM_JSLibrary.itemCount(ClientSettingCache[keyName]);
            ClientSettingCache[keyName][currentSize + 1] = FormTableHandler.newRowValue;
            ClientSettingCache[keyName][currentSize + 1].name = value;
            ClientSettingCache[keyName][currentSize + 1].labels = {'':value};
            FormTableHandler.write(keyName,function(){
                FormTableHandler.init(keyName);
            });
        }
    });
};

FormTableHandler.showOptionsDialog = async function(keyName, iteration) {
    const settingData = await PWM_CFGEDIT.getConfigSettingData();

    const type = ClientSettingCache[keyName][iteration]['type'];
    const settings = settingData['settings'][keyName];
    const currentValue = ClientSettingCache[keyName][iteration];
    const options = settingData['settings'][keyName]['options'];

    const hideStandardOptions = PWM_JSLibrary.arrayContains(settings['flags'],'Form_HideStandardOptions') || type === 'photo';
    const showRequired = PWM_JSLibrary.arrayContains(settings['flags'],'Form_ShowRequiredOption');
    const showUnique = PWM_JSLibrary.arrayContains(settings['flags'],'Form_ShowUniqueOption') && type !== 'photo';
    const showReadOnly = PWM_JSLibrary.arrayContains(settings['flags'],'Form_ShowReadOnlyOption');
    const showMultiValue = PWM_JSLibrary.arrayContains(settings['flags'],'Form_ShowMultiValueOption');
    const showConfirmation = type !== 'checkbox' && type !== 'select' && type != 'photo' && !hideStandardOptions;
    const showSource = PWM_JSLibrary.arrayContains(settings['flags'],'Form_ShowSource');


    const inputID = 'value_' + keyName + '_' + iteration + "_";
    let bodyText = '<div style="max-height: 500px; overflow-y: auto"><table class="noborder">';
    if (!hideStandardOptions || type === 'photo') {
        bodyText += '<tr>';
        bodyText += '<td id="' + inputID + '-label-description" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_Description') + '">Description</td><td>';
        bodyText += '<div class="noWrapTextBox" id="' + inputID + 'description"><span class="btn-icon pwm-icon pwm-icon-edit"></span><span id="' + inputID + '-value"></span></div>';
        bodyText += '</td>';
    }

    bodyText += '</tr><tr>';
    if (showRequired) {
        bodyText += '<td id="' + inputID + '-label-required" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_Required') + '">Required</td><td><input type="checkbox" id="' + inputID + 'required' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showConfirmation) {
        bodyText += '<td id="' + inputID + '-label-confirm" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_Confirm') + '">Confirm</td><td><input type="checkbox" id="' + inputID + 'confirmationRequired' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showReadOnly) {
        bodyText += '<td id="' + inputID + '-label-readOnly" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_ReadOnly') + '">Read Only</td><td><input type="checkbox" id="' + inputID + 'readonly' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showUnique) {
        bodyText += '<td id="' + inputID + '-label-unique" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_Unique') + '">Unique</td><td><input type="checkbox" id="' + inputID + 'unique' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showMultiValue) {
        bodyText += '<td id="' + inputID + '-label-multivalue" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_MultiValue') + '">MultiValue</td><td><input type="checkbox" id="' + inputID + 'multivalue' + '"/></td>';
        bodyText += '</tr><tr>';
    }

    if (!hideStandardOptions) {
        bodyText += '<td class="key">Minimum Length</td><td><input min="0" pattern="[0-9]{1,5}" required max="65536" style="width: 70px" type="number" id="' + inputID + 'minimumLength' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td class="key">Maximum Length</td><td><input min="0" pattern="[0-9]{1,5}" max="65536" style="width: 70px" type="number" id="' + inputID + 'maximumLength' + '"/></td>';
        bodyText += '</tr><tr>';

        { // regex
            bodyText += '<td id="' + inputID + '-label-regex" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_Regex') + '">Regular Expression</td><td><input type="text" class="configStringInput" style="width:300px" id="' + inputID + 'regex' + '"/></td>';
            bodyText += '</tr><tr>';

            const regexErrorValue = currentValue['regexErrors'][''];
            bodyText += '<td id="' + inputID + '-label-regexError" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_RegexError') + '">Regular Expression<br/>Error Message</td><td>';
            bodyText += '<div class="noWrapTextBox" id="' + inputID + 'regexErrors"><span class="btn-icon pwm-icon pwm-icon-edit"></span><span>' + regexErrorValue + '...</span></div>';
            bodyText += '</td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '<td id="' + inputID + '-label-placeholder" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_Placeholder') + '">Placeholder</td><td><input type="text" class="configStringInput" style="width:300px" id="' + inputID + 'placeholder' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td id="' + inputID + '-label-js" class="key" title="' + await PWM_CONFIG.getDisplayString('Tooltip_FormOptions_Javascript') + '">JavaScript (Depreciated)</td><td><input type="text" class="configStringInput" style="width:300px" id="' + inputID + 'javascript' + '"/></td>';
        bodyText += '</tr><tr>';
    }

    if (currentValue['type'] === 'select') {
        bodyText += '<td class="key">Select Options</td><td><button class="btn" id="' + inputID + 'editOptionsButton"><span class="btn-icon pwm-icon pwm-icon-list-ul"/> Edit</button></td>';
        bodyText += '</tr>';
    }

    if (showSource) {
        bodyText += '<td id="' + inputID + '-label-source" class="key">Data Source</td>'
            +  '<td><select id="' + inputID + 'source' + '">'
            + '<option value="ldap">LDAP</option>'
            + '<option value="remote">Remote REST API</option>'
            + '</select></td></tr><tr>';
    }

    if (currentValue['type'] === 'photo') {
        bodyText += '<td class="key">Maximum Size (bytes)</td><td><input min="0" pattern="[0-9]{1,10}" max="10000000" style="width: 90px" type="number" id="' + inputID + 'maximumSize' + '"/></td>';
        bodyText += '</tr><tr>';
    }

    bodyText += '</table></div>';

    const initFormElements = function() {
        const currentValue = ClientSettingCache[keyName][iteration];


        PWM_MAIN.addEventHandler(inputID + 'editOptionsButton', 'click', function(){
            FormTableHandler.showSelectOptionsDialog(keyName,iteration);
        });

        PWM_MAIN.addEventHandler(inputID + 'description','click',function(){
            FormTableHandler.showDescriptionDialog(keyName, iteration);
        });

        const descriptionValue = currentValue['description'][''];
        PWM_UILibrary.addTextValueToElement(inputID + '-value', descriptionValue ? descriptionValue : "Edit");

        if (showRequired) {
            PWM_JSLibrary.getElement(inputID + "required").checked = currentValue['required'];
            PWM_MAIN.addEventHandler(inputID + "required", "change", function(){
                currentValue['required'] = PWM_JSLibrary.getElement(inputID + "required").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (PWM_JSLibrary.getElement(inputID + "confirmationRequired") != null) {
            PWM_JSLibrary.getElement(inputID + "confirmationRequired").checked = currentValue['confirmationRequired'];
            PWM_MAIN.addEventHandler(inputID + "confirmationRequired", "change", function () {
                currentValue['confirmationRequired'] = PWM_JSLibrary.getElement(inputID + "confirmationRequired").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (showReadOnly) {
            PWM_JSLibrary.getElement(inputID + "readonly").checked = currentValue['readonly'];
            PWM_MAIN.addEventHandler(inputID + "readonly", "change", function () {
                currentValue['readonly'] = PWM_JSLibrary.getElement(inputID + "readonly").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (showUnique) {
            PWM_JSLibrary.getElement(inputID + "unique").checked = currentValue['unique'];
            PWM_MAIN.addEventHandler(inputID + "unique", "change", function () {
                currentValue['unique'] = PWM_JSLibrary.getElement(inputID + "unique").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (showMultiValue) {
            PWM_JSLibrary.getElement(inputID + "multivalue").checked = currentValue['multivalue'];
            PWM_MAIN.addEventHandler(inputID + "multivalue", "change", function () {
                currentValue['multivalue'] = PWM_JSLibrary.getElement(inputID + "multivalue").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (PWM_JSLibrary.getElement(inputID + "maximumSize")) {
            PWM_JSLibrary.getElement(inputID + "maximumSize").value = currentValue['maximumSize'];
            PWM_MAIN.addEventHandler(inputID + "maximumSize", "change", function(){
                currentValue['maximumSize'] = PWM_JSLibrary.getElement(inputID + "maximumSize").value;
                FormTableHandler.write(keyName)
            });
        }

        if (!hideStandardOptions) {
            PWM_JSLibrary.getElement(inputID + "minimumLength").value = currentValue['minimumLength'];
            PWM_MAIN.addEventHandler(inputID + "minimumLength", "change", function(){
                currentValue['minimumLength'] = PWM_JSLibrary.getElement(inputID + "minimumLength").value;
                FormTableHandler.write(keyName)
            });

            PWM_JSLibrary.getElement(inputID + "maximumLength").value = currentValue['maximumLength'];
            PWM_MAIN.addEventHandler(inputID + "maximumLength", "change", function(){
                currentValue['maximumLength'] = PWM_JSLibrary.getElement(inputID + "maximumLength").value;
                FormTableHandler.write(keyName)
            });

            PWM_JSLibrary.getElement(inputID + "regex").value = currentValue['regex'] ? currentValue['regex'] : '';
            PWM_MAIN.addEventHandler(inputID + "regex", "change", function(){
                currentValue['regex'] = PWM_JSLibrary.getElement(inputID + "regex").value;
                FormTableHandler.write(keyName)
            });

            PWM_MAIN.addEventHandler(inputID + 'regexErrors', 'click', function () {
                FormTableHandler.showRegexErrorsDialog(keyName, iteration);
            });

            PWM_JSLibrary.getElement(inputID + "placeholder").value = currentValue['placeholder'] ? currentValue['placeholder'] : '';
            PWM_MAIN.addEventHandler(inputID + "placeholder", "change", function(){
                currentValue['placeholder'] = PWM_JSLibrary.getElement(inputID + "placeholder").value;
                FormTableHandler.write(keyName)
            });

            PWM_JSLibrary.getElement(inputID + "javascript").value = currentValue['javascript'] ? currentValue['javascript'] : '';
            PWM_MAIN.addEventHandler(inputID + "javascript", "change", function(){
                currentValue['javascript'] = PWM_JSLibrary.getElement(inputID + "javascript").value;
                FormTableHandler.write(keyName)
            });
        }
        if (showSource) {
            const nodeID = inputID + 'source';
            PWM_JSLibrary.setValueOfSelectElement(nodeID,currentValue['source']);
            PWM_MAIN.addEventHandler(nodeID,'change',function(){
                const newValue = PWM_JSLibrary.readValueOfSelectElement(nodeID);
                currentValue['source'] = newValue;
                FormTableHandler.write(keyName);
            });
        }
    };

    PWM_MAIN.showDialog({
        title: settingData['settings'][keyName]['label'] + ' - ' + currentValue['name'],
        text:bodyText,
        allowMove:true,
        loadFunction:initFormElements,
        okAction:function(){
            FormTableHandler.redraw(keyName);
        }
    });
};

FormTableHandler.showLabelDialog = function(keyName, iteration) {
    const finishAction = function(){ FormTableHandler.redraw(keyName); };
    const title = 'Label for ' + ClientSettingCache[keyName][iteration]['name'];
    FormTableHandler.multiLocaleStringDialog(keyName, iteration, 'labels', finishAction, title);
};

FormTableHandler.multiLocaleStringDialog = function(keyName, iteration, settingType, finishAction, titleText) {
    const inputID = 'value_' + keyName + '_' + iteration + "_" + "label_";
    let bodyText = '<table class="noborder" id="' + inputID + 'table">';
    bodyText += '<tr>';
    for (const localeName in ClientSettingCache[keyName][iteration][settingType]) {
        const localeID = inputID + localeName;
        bodyText += '<td>' + localeName + '</td>';
        bodyText += '<td><input style="width:420px" class="configStringInput" type="text" id="' + localeID + '-input"></input></td>';
        if (localeName !== '') {
            bodyText += '<td><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="' + localeID + '-removeLocaleButton"></span></td>';
        }
        bodyText += '</tr><tr>';
    }
    bodyText += '</tr></table>';

    PWM_MAIN.showDialog({
        title: titleText,
        text: bodyText,
        okAction:function(){
            finishAction();
        },
        loadFunction:function(){
            for (const iter in ClientSettingCache[keyName][iteration][settingType]) {
                (function(localeName) {
                    const value = ClientSettingCache[keyName][iteration][settingType][localeName];
                    const localeID = inputID + localeName;
                    PWM_JSLibrary.getElement(localeID + '-input').value = value;
                    PWM_MAIN.addEventHandler(localeID + '-input', 'input', function () {
                        const inputElement = PWM_JSLibrary.getElement(localeID + '-input');
                        const value = inputElement.value;
                        ClientSettingCache[keyName][iteration][settingType][localeName] = value;
                        FormTableHandler.write(keyName);
                    });
                    PWM_MAIN.addEventHandler(localeID + '-removeLocaleButton', 'click', function () {
                        delete ClientSettingCache[keyName][iteration][settingType][localeName];
                        FormTableHandler.write(keyName);
                        FormTableHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                    });
                }(iter));
            }
            PWM_UILibrary.addAddLocaleButtonRow(inputID + 'table', inputID, function(localeName){
                if (localeName in ClientSettingCache[keyName][iteration][settingType]) {
                    alert('Locale is already present');
                } else {
                    ClientSettingCache[keyName][iteration][settingType][localeName] = '';
                    FormTableHandler.write(keyName);
                    FormTableHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                }
            }, Object.keys(ClientSettingCache[keyName][iteration][settingType]));
        }
    });
};


FormTableHandler.showRegexErrorsDialog = function(keyName, iteration) {
    const finishAction = function(){ FormTableHandler.showOptionsDialog(keyName, iteration); };
    const title = 'Regular Expression Error Message for ' + ClientSettingCache[keyName][iteration]['name'];
    FormTableHandler.multiLocaleStringDialog(keyName, iteration, 'regexErrors', finishAction, title);
};


FormTableHandler.showSelectOptionsDialog = function(keyName, iteration) {
    const inputID = 'value_' + keyName + '_' + iteration + "_" + "selectOptions_";
    let bodyText = '';
    bodyText += '<table class="noborder" id="' + inputID + 'table"">';
    bodyText += '<tr>';
    bodyText += '<td><b>Value</b></td><td><b>Display Name</b></td>';
    bodyText += '</tr><tr>';
    for (const optionName in ClientSettingCache[keyName][iteration]['selectOptions']) {
        (function(counter) {
            const value = ClientSettingCache[keyName][iteration]['selectOptions'][counter];
            const optionID = inputID + counter;
            bodyText += '<td>' + counter + '</td><td>' + value + '</td>';
            bodyText += '<td class="noborder" style="width:15px">';
            bodyText += '<span id="' + optionID + '-removeButton" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></span>';
            bodyText += '</td>';
            bodyText += '</tr><tr>';
        }(optionName));
    }
    bodyText += '</tr></table>';
    bodyText += '<br/><br/><br/>';
    bodyText += '<input class="configStringInput" style="width:200px" type="text" placeholder="Value" required id="addSelectOptionName"/>';
    bodyText += '<input class="configStringInput" style="width:200px" type="text" placeholder="Display Name" required id="addSelectOptionValue"/>';
    bodyText += '<button id="addSelectOptionButton"><span class="btn-icon pwm-icon pwm-icon-plus-square"/> Add</button>';

    const initFormFields = function() {
        for (const optionName in ClientSettingCache[keyName][iteration]['selectOptions']) {
            (function(counter) {
                const optionID = inputID + counter;
                PWM_MAIN.addEventHandler(optionID + '-removeButton','click',function(){
                    FormTableHandler.removeSelectOptionsOption(keyName,iteration,counter);
                });
            }(optionName));
        }

        PWM_MAIN.addEventHandler('addSelectOptionButton','click',function(){
            const value = PWM_JSLibrary.getElement('addSelectOptionName').value;
            const display = PWM_JSLibrary.getElement('addSelectOptionValue').value;
            FormTableHandler.addSelectOptionsOption(keyName, iteration, value, display);
        });
    };

    PWM_MAIN.showDialog({
        title: 'Select Options for ' + ClientSettingCache[keyName][iteration]['name'],
        text: bodyText,
        loadFunction: initFormFields,
        okAction: function(){
            FormTableHandler.showOptionsDialog(keyName,iteration);
        }
    });

};

FormTableHandler.addSelectOptionsOption = function(keyName, iteration, optionName, optionValue) {
    if (optionName === null || optionName.length < 1) {
        alert('Name field is required');
        return;
    }

    if (optionValue === null || optionValue.length < 1) {
        alert('Value field is required');
        return;
    }

    ClientSettingCache[keyName][iteration]['selectOptions'][optionName] = optionValue;
    FormTableHandler.write(keyName);
    FormTableHandler.showSelectOptionsDialog(keyName, iteration);
};

FormTableHandler.removeSelectOptionsOption = function(keyName, iteration, optionName) {
    delete ClientSettingCache[keyName][iteration]['selectOptions'][optionName];
    FormTableHandler.write(keyName);
    FormTableHandler.showSelectOptionsDialog(keyName, iteration);
};

FormTableHandler.showDescriptionDialog = function(keyName, iteration) {
    const finishAction = function(){ FormTableHandler.showOptionsDialog(keyName, iteration); };
    const title = 'Description for ' + ClientSettingCache[keyName][iteration]['name'];
    FormTableHandler.multiLocaleStringDialog(keyName, iteration, 'description', finishAction, title);
};
