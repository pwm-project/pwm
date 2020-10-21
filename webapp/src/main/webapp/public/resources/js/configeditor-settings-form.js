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


var FormTableHandler = {};
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
    mimeTypes:['image/gif','image/png','image/jpeg','image/bmp','image/webp'],
    maximumSize:65000
};

FormTableHandler.init = function(keyName) {
    console.log('FormTableHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        FormTableHandler.redraw(keyName);
    });
};

FormTableHandler.redraw = function(keyName) {
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    parentDivElement.innerHTML = '<table class="noborder" style="margin-left: 0; width:auto" id="table-top-' + keyName + '"></table>';
    parentDiv = 'table-top-' + keyName;
    parentDivElement = PWM_MAIN.getObject(parentDiv);

    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        var headerRow = document.createElement("tr");
        var rowHtml = '<td>Name</td><td></td><td>Label</td>';
        headerRow.innerHTML = rowHtml;
        parentDivElement.appendChild(headerRow);
    }

    for (var i in resultValue) {
        FormTableHandler.drawRow(parentDiv, keyName, i, resultValue[i]);
    }

    var buttonRow = document.createElement("tr");
    buttonRow.setAttribute("colspan","5");
    buttonRow.innerHTML = '<td><button class="btn" id="button-' + keyName + '-addRow"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Item</button></td>';

    parentDivElement.appendChild(buttonRow);

    PWM_MAIN.addEventHandler('button-' + keyName + '-addRow','click',function(){
        FormTableHandler.addRow(keyName);
    });

};

FormTableHandler.drawRow = function(parentDiv, settingKey, iteration, value) {
    var itemCount = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]);
    var inputID = 'value_' + settingKey + '_' + iteration + "_";
    var options = PWM_SETTINGS['settings'][settingKey]['options'];
    var properties = PWM_SETTINGS['settings'][settingKey]['properties'];

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    var htmlRow = '';
    htmlRow += '<td style="background: #f6f9f8; border:1px solid #dae1e1; width:180px"><div class="noWrapTextBox" id="panel-name-' + inputID + '" ></div></td>';
    htmlRow += '<td style="width:1px" id="icon-editLabel-' + inputID + '"><span class="btn-icon pwm-icon pwm-icon-edit"></span></td>';
    htmlRow += '<td style="background: #f6f9f8; border:1px solid #dae1e1; width:170px"><div style="" class="noWrapTextBox " id="' + inputID + 'label"><span>' + value['labels'][''] + '</span></div></td>';

    var userDNtypeAllowed = options['type-userDN'] === 'show';
    if (!PWM_MAIN.JSLibrary.isEmpty(options)) {
        htmlRow += '<td style="width:15px;">';
        htmlRow += '<select id="' + inputID + 'type">';
        for (var optionItem in options) {
            //if (optionList[optionItem] !== 'userDN' || userDNtypeAllowed) {
            var optionName = options[optionItem];
            var selected = (optionName === PWM_VAR['clientSettingCache'][settingKey][iteration]['type']);
            htmlRow += '<option value="' + optionName + '"' + (selected ? " selected" : "") + '>' + optionName + '</option>';
            //}
        }
        htmlRow += '</select>';
        htmlRow += '</td>';
    }

    var hideOptions = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'], 'Form_HideOptions');
    if (!hideOptions) {
        htmlRow += '<td class="noborder" style="min-width:90px;"><button id="' + inputID + 'optionsButton"><span class="btn-icon pwm-icon pwm-icon-sliders"/> Options</button></td>';
    }

    htmlRow += '<td style="width:10px">';
    if (itemCount > 1 && iteration !== (itemCount -1)) {
        htmlRow += '<span id="' + inputID + '-moveDown" class="action-icon pwm-icon pwm-icon-chevron-down"></span>';
    }
    htmlRow += '</td>';

    htmlRow += '<td style="width:10px">';
    if (itemCount > 1 && iteration !== 0) {
        htmlRow += '<span id="' + inputID + '-moveUp" class="action-icon pwm-icon pwm-icon-chevron-up"></span>';
    }
    htmlRow += '</td>';
    htmlRow += '<td style="width:10px"><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="' + inputID + '-deleteRowButton"></span></td>';

    newTableRow.innerHTML = htmlRow;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    UILibrary.addTextValueToElement("panel-name-" + inputID,value['name']);

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
        PWM_VAR['clientSettingCache'][settingKey][iteration]['name'] = PWM_MAIN.getObject(inputID + "name").value;
        FormTableHandler.write(settingKey);
    });
    PWM_MAIN.addEventHandler(inputID + "type", 'click', function () {
        PWM_VAR['clientSettingCache'][settingKey][iteration]['type'] = PWM_MAIN.getObject(inputID + "type").value;
        FormTableHandler.write(settingKey);
    });
};

FormTableHandler.write = function(settingKey, finishFunction) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

FormTableHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            var currentValues = PWM_VAR['clientSettingCache'][keyName];
            currentValues.splice(iteration,1);
            FormTableHandler.write(keyName,function(){
                FormTableHandler.init(keyName);
            });
        }
    });
};

FormTableHandler.move = function(settingKey, moveUp, iteration) {
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    if (moveUp) {
        FormTableHandler.arrayMoveUtil(currentValues, iteration, iteration - 1);
    } else {
        FormTableHandler.arrayMoveUtil(currentValues, iteration, iteration + 1);
    }
    FormTableHandler.write(settingKey);
    FormTableHandler.redraw(settingKey);
};

FormTableHandler.arrayMoveUtil = function(arr, fromIndex, toIndex) {
    var element = arr[fromIndex];
    arr.splice(fromIndex, 1);
    arr.splice(toIndex, 0, element);
};


FormTableHandler.addRow = function(keyName) {
    UILibrary.stringEditorDialog({
        title:PWM_SETTINGS['settings'][keyName]['label'] + ' - New Form Field',
        regex:'^[a-zA-Z][a-zA-Z0-9-]*$',
        placeholder:'FieldName',
        completeFunction:function(value){
            for (var i in PWM_VAR['clientSettingCache'][keyName]) {
                if (PWM_VAR['clientSettingCache'][keyName][i]['name'] === value) {
                    alert('field already exists');
                    return;
                }
            }
            var currentSize = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][keyName]);
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1] = FormTableHandler.newRowValue;
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1].name = value;
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1].labels = {'':value};
            FormTableHandler.write(keyName,function(){
                FormTableHandler.init(keyName);
            });
        }
    });
};

FormTableHandler.showOptionsDialog = function(keyName, iteration) {
    var type = PWM_VAR['clientSettingCache'][keyName][iteration]['type'];
    var settings = PWM_SETTINGS['settings'][keyName];
    var currentValue = PWM_VAR['clientSettingCache'][keyName][iteration];
    var options = PWM_SETTINGS['settings'][keyName]['options'];


    var hideStandardOptions = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_HideStandardOptions') || type === 'photo';
    var showRequired = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowRequiredOption');
    var showUnique = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowUniqueOption') && type !== 'photo';
    var showReadOnly = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowReadOnlyOption');
    var showMultiValue = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowMultiValueOption');
    var showConfirmation = type !== 'checkbox' && type !== 'select' && type != 'photo' && !hideStandardOptions;
    var showSource = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowSource');


    var inputID = 'value_' + keyName + '_' + iteration + "_";
    var bodyText = '<div style="max-height: 500px; overflow-y: auto"><table class="noborder">';
    if (!hideStandardOptions || type === 'photo') {
        bodyText += '<tr>';
        bodyText += '<td id="' + inputID + '-label-description" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Description') + '">Description</td><td>';
        bodyText += '<div class="noWrapTextBox" id="' + inputID + 'description"><span class="btn-icon pwm-icon pwm-icon-edit"></span><span id="' + inputID + '-value"></span></div>';
        bodyText += '</td>';
    }

    bodyText += '</tr><tr>';
    if (showRequired) {
        bodyText += '<td id="' + inputID + '-label-required" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Required') + '">Required</td><td><input type="checkbox" id="' + inputID + 'required' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showConfirmation) {
        bodyText += '<td id="' + inputID + '-label-confirm" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Confirm') + '">Confirm</td><td><input type="checkbox" id="' + inputID + 'confirmationRequired' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showReadOnly) {
        bodyText += '<td id="' + inputID + '-label-readOnly" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_ReadOnly') + '">Read Only</td><td><input type="checkbox" id="' + inputID + 'readonly' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showUnique) {
        bodyText += '<td id="' + inputID + '-label-unique" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Unique') + '">Unique</td><td><input type="checkbox" id="' + inputID + 'unique' + '"/></td>';
        bodyText += '</tr><tr>';
    }
    if (showMultiValue) {
        bodyText += '<td id="' + inputID + '-label-multivalue" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_MultiValue') + '">MultiValue</td><td><input type="checkbox" id="' + inputID + 'multivalue' + '"/></td>';
        bodyText += '</tr><tr>';
    }

    if (!hideStandardOptions) {
        bodyText += '<td class="key">Minimum Length</td><td><input min="0" pattern="[0-9]{1,5}" required max="65536" style="width: 70px" type="number" id="' + inputID + 'minimumLength' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td class="key">Maximum Length</td><td><input min="0" pattern="[0-9]{1,5}" max="65536" style="width: 70px" type="number" id="' + inputID + 'maximumLength' + '"/></td>';
        bodyText += '</tr><tr>';

        { // regex
            bodyText += '<td id="' + inputID + '-label-regex" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Regex') + '">Regular Expression</td><td><input type="text" class="configStringInput" style="width:300px" id="' + inputID + 'regex' + '"/></td>';
            bodyText += '</tr><tr>';

            var regexErrorValue = currentValue['regexErrors'][''];
            bodyText += '<td id="' + inputID + '-label-regexError" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_RegexError') + '">Regular Expression<br/>Error Message</td><td>';
            bodyText += '<div class="noWrapTextBox" id="' + inputID + 'regexErrors"><span class="btn-icon pwm-icon pwm-icon-edit"></span><span>' + regexErrorValue + '...</span></div>';
            bodyText += '</td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '<td id="' + inputID + '-label-placeholder" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Placeholder') + '">Placeholder</td><td><input type="text" class="configStringInput" style="width:300px" id="' + inputID + 'placeholder' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td id="' + inputID + '-label-js" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Javascript') + '">JavaScript (Depreciated)</td><td><input type="text" class="configStringInput" style="width:300px" id="' + inputID + 'javascript' + '"/></td>';
        bodyText += '</tr><tr>';
    }

    if ('select' in options) {
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
        bodyText += '<td class="key">MimeTypes</td><td><button class="btn" id="' + inputID + 'editMimeTypesButton"><span class="btn-icon pwm-icon pwm-icon-list-ul"/> Edit</button></td>';
        bodyText += '</tr>';

        bodyText += '<td class="key">Maximum Size (bytes)</td><td><input min="0" pattern="[0-9]{1,10}" max="10000000" style="width: 90px" type="number" id="' + inputID + 'maximumSize' + '"/></td>';
        bodyText += '</tr><tr>';
    }

    bodyText += '</table></div>';

    var initFormElements = function() {
        var currentValue = PWM_VAR['clientSettingCache'][keyName][iteration];


        PWM_MAIN.addEventHandler(inputID + 'editOptionsButton', 'click', function(){
            FormTableHandler.showSelectOptionsDialog(keyName,iteration);
        });

        PWM_MAIN.addEventHandler(inputID + 'editMimeTypesButton', 'click', function(){
            FormTableHandler.showMimeTypesDialog(keyName,iteration);
        });

        PWM_MAIN.addEventHandler(inputID + 'description','click',function(){
            FormTableHandler.showDescriptionDialog(keyName, iteration);
        });

        var descriptionValue = currentValue['description'][''];
        UILibrary.addTextValueToElement(inputID + '-value', descriptionValue ? descriptionValue : "Edit");

        if (showRequired) {
            PWM_MAIN.getObject(inputID + "required").checked = currentValue['required'];
            PWM_MAIN.addEventHandler(inputID + "required", "change", function(){
                currentValue['required'] = PWM_MAIN.getObject(inputID + "required").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (PWM_MAIN.getObject(inputID + "confirmationRequired") != null) {
            PWM_MAIN.getObject(inputID + "confirmationRequired").checked = currentValue['confirmationRequired'];
            PWM_MAIN.addEventHandler(inputID + "confirmationRequired", "change", function () {
                currentValue['confirmationRequired'] = PWM_MAIN.getObject(inputID + "confirmationRequired").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (showReadOnly) {
            PWM_MAIN.getObject(inputID + "readonly").checked = currentValue['readonly'];
            PWM_MAIN.addEventHandler(inputID + "readonly", "change", function () {
                currentValue['readonly'] = PWM_MAIN.getObject(inputID + "readonly").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (showUnique) {
            PWM_MAIN.getObject(inputID + "unique").checked = currentValue['unique'];
            PWM_MAIN.addEventHandler(inputID + "unique", "change", function () {
                currentValue['unique'] = PWM_MAIN.getObject(inputID + "unique").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (showMultiValue) {
            PWM_MAIN.getObject(inputID + "multivalue").checked = currentValue['multivalue'];
            PWM_MAIN.addEventHandler(inputID + "multivalue", "change", function () {
                currentValue['multivalue'] = PWM_MAIN.getObject(inputID + "multivalue").checked;
                FormTableHandler.write(keyName)
            });
        }

        if (PWM_MAIN.getObject(inputID + "maximumSize")) {
            PWM_MAIN.getObject(inputID + "maximumSize").value = currentValue['maximumSize'];
            PWM_MAIN.addEventHandler(inputID + "maximumSize", "change", function(){
                currentValue['maximumSize'] = PWM_MAIN.getObject(inputID + "maximumSize").value;
                FormTableHandler.write(keyName)
            });
        }

        if (!hideStandardOptions) {
            PWM_MAIN.getObject(inputID + "minimumLength").value = currentValue['minimumLength'];
            PWM_MAIN.addEventHandler(inputID + "minimumLength", "change", function(){
                currentValue['minimumLength'] = PWM_MAIN.getObject(inputID + "minimumLength").value;
                FormTableHandler.write(keyName)
            });

            PWM_MAIN.getObject(inputID + "maximumLength").value = currentValue['maximumLength'];
            PWM_MAIN.addEventHandler(inputID + "maximumLength", "change", function(){
                currentValue['maximumLength'] = PWM_MAIN.getObject(inputID + "maximumLength").value;
                FormTableHandler.write(keyName)
            });

            PWM_MAIN.getObject(inputID + "regex").value = currentValue['regex'] ? currentValue['regex'] : '';
            PWM_MAIN.addEventHandler(inputID + "regex", "change", function(){
                currentValue['regex'] = PWM_MAIN.getObject(inputID + "regex").value;
                FormTableHandler.write(keyName)
            });

            PWM_MAIN.addEventHandler(inputID + 'regexErrors', 'click', function () {
                FormTableHandler.showRegexErrorsDialog(keyName, iteration);
            });

            PWM_MAIN.getObject(inputID + "placeholder").value = currentValue['placeholder'] ? currentValue['placeholder'] : '';
            PWM_MAIN.addEventHandler(inputID + "placeholder", "change", function(){
                currentValue['placeholder'] = PWM_MAIN.getObject(inputID + "placeholder").value;
                FormTableHandler.write(keyName)
            });

            PWM_MAIN.getObject(inputID + "javascript").value = currentValue['javascript'] ? currentValue['javascript'] : '';
            PWM_MAIN.addEventHandler(inputID + "javascript", "change", function(){
                currentValue['javascript'] = PWM_MAIN.getObject(inputID + "javascript").value;
                FormTableHandler.write(keyName)
            });
        }
        if (showSource) {
            var nodeID = inputID + 'source';
            PWM_MAIN.JSLibrary.setValueOfSelectElement(nodeID,currentValue['source']);
            PWM_MAIN.addEventHandler(nodeID,'change',function(){
                var newValue = PWM_MAIN.JSLibrary.readValueOfSelectElement(nodeID);
                currentValue['source'] = newValue;
                FormTableHandler.write(keyName);
            });
        }
    };

    PWM_MAIN.showDialog({
        title: PWM_SETTINGS['settings'][keyName]['label'] + ' - ' + currentValue['name'],
        text:bodyText,
        allowMove:true,
        loadFunction:initFormElements,
        okAction:function(){
            FormTableHandler.redraw(keyName);
        }
    });
};

FormTableHandler.showLabelDialog = function(keyName, iteration) {
    var finishAction = function(){ FormTableHandler.redraw(keyName); };
    var title = 'Label for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'];
    FormTableHandler.multiLocaleStringDialog(keyName, iteration, 'labels', finishAction, title);
};

FormTableHandler.multiLocaleStringDialog = function(keyName, iteration, settingType, finishAction, titleText) {
    var inputID = 'value_' + keyName + '_' + iteration + "_" + "label_";
    var bodyText = '<table class="noborder" id="' + inputID + 'table">';
    bodyText += '<tr>';
    for (var localeName in PWM_VAR['clientSettingCache'][keyName][iteration][settingType]) {
        var localeID = inputID + localeName;
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
            for (var iter in PWM_VAR['clientSettingCache'][keyName][iteration][settingType]) {
                (function(localeName) {
                    var value = PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName];
                    var localeID = inputID + localeName;
                    PWM_MAIN.getObject(localeID + '-input').value = value;
                    PWM_MAIN.addEventHandler(localeID + '-input', 'input', function () {
                        var inputElement = PWM_MAIN.getObject(localeID + '-input');
                        var value = inputElement.value;
                        PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName] = value;
                        FormTableHandler.write(keyName);
                    });
                    PWM_MAIN.addEventHandler(localeID + '-removeLocaleButton', 'click', function () {
                        delete PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName];
                        FormTableHandler.write(keyName);
                        FormTableHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                    });
                }(iter));
            }
            UILibrary.addAddLocaleButtonRow(inputID + 'table', inputID, function(localeName){
                if (localeName in PWM_VAR['clientSettingCache'][keyName][iteration][settingType]) {
                    alert('Locale is already present');
                } else {
                    PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName] = '';
                    FormTableHandler.write(keyName);
                    FormTableHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                }
            }, Object.keys(PWM_VAR['clientSettingCache'][keyName][iteration][settingType]));
        }
    });
};


FormTableHandler.showRegexErrorsDialog = function(keyName, iteration) {
    var finishAction = function(){ FormTableHandler.showOptionsDialog(keyName, iteration); };
    var title = 'Regular Expression Error Message for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'];
    FormTableHandler.multiLocaleStringDialog(keyName, iteration, 'regexErrors', finishAction, title);
};


FormTableHandler.showSelectOptionsDialog = function(keyName, iteration) {
    var inputID = 'value_' + keyName + '_' + iteration + "_" + "selectOptions_";
    var bodyText = '';
    bodyText += '<table class="noborder" id="' + inputID + 'table"">';
    bodyText += '<tr>';
    bodyText += '<td><b>Value</b></td><td><b>Display Name</b></td>';
    bodyText += '</tr><tr>';
    for (var optionName in PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions']) {
        (function(counter) {
            var value = PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions'][counter];
            var optionID = inputID + counter;
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

    var initFormFields = function() {
        for (var optionName in PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions']) {
            (function(counter) {
                var optionID = inputID + counter;
                PWM_MAIN.addEventHandler(optionID + '-removeButton','click',function(){
                    FormTableHandler.removeSelectOptionsOption(keyName,iteration,counter);
                });
            }(optionName));
        }

        PWM_MAIN.addEventHandler('addSelectOptionButton','click',function(){
            var value = PWM_MAIN.getObject('addSelectOptionName').value;
            var display = PWM_MAIN.getObject('addSelectOptionValue').value;
            FormTableHandler.addSelectOptionsOption(keyName, iteration, value, display);
        });
    };

    PWM_MAIN.showDialog({
        title: 'Select Options for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
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

    PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions'][optionName] = optionValue;
    FormTableHandler.write(keyName);
    FormTableHandler.showSelectOptionsDialog(keyName, iteration);
};

FormTableHandler.removeSelectOptionsOption = function(keyName, iteration, optionName) {
    delete PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions'][optionName];
    FormTableHandler.write(keyName);
    FormTableHandler.showSelectOptionsDialog(keyName, iteration);
};

FormTableHandler.showDescriptionDialog = function(keyName, iteration) {
    var finishAction = function(){ FormTableHandler.showOptionsDialog(keyName, iteration); };
    var title = 'Description for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'];
    FormTableHandler.multiLocaleStringDialog(keyName, iteration, 'description', finishAction, title);
};

FormTableHandler.showMimeTypesDialog = function(keyName, iteration) {
    var inputID = 'value_' + keyName + '_' + iteration + "_" + "selectOptions_";
    var bodyText = '';
    bodyText += '<table class="noborder" id="' + inputID + 'table"">';
    bodyText += '<tr>';
    bodyText += '</tr><tr>';
    for (var optionName in PWM_VAR['clientSettingCache'][keyName][iteration]['mimeTypes']) {
        (function(optionName) {
            var value = PWM_VAR['clientSettingCache'][keyName][iteration]['mimeTypes'][optionName];
            var optionID = inputID + optionName;
            bodyText += '<td><div class="noWrapTextBox">' + value + '</div></td>';
            bodyText += '<td class="noborder" style="">';
            bodyText += '<span id="' + optionID + '-removeButton" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></span>';
            bodyText += '</td>';
            bodyText += '</tr><tr>';
        }(optionName));
    }
    bodyText += '</tr></table>';
    bodyText += '<br/><br/><br/>';
    bodyText += '<input class="configStringInput" pattern=".*/.*" style="width:200px" type="text" placeholder="Value" required id="addValue"/>';
    bodyText += '<button id="addItemButton"><span class="btn-icon pwm-icon pwm-icon-plus-square"/> Add</button>';

    PWM_MAIN.showDialog({
        title: 'Mime Types - ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
        text: bodyText,
        okAction: function(){
            FormTableHandler.showOptionsDialog(keyName,iteration);
        }
    });

    for (var optionName in PWM_VAR['clientSettingCache'][keyName][iteration]['mimeTypes']) {
        (function(optionName) {
            var optionID = inputID + optionName;
            PWM_MAIN.addEventHandler(optionID + '-removeButton','click',function(){
                delete PWM_VAR['clientSettingCache'][keyName][iteration]['mimeTypes'][optionName];
                FormTableHandler.write(keyName);
                FormTableHandler.showMimeTypesDialog(keyName, iteration);
            });
        }(optionName));
    }

    PWM_MAIN.addEventHandler('addItemButton','click',function(){
        var value = PWM_MAIN.getObject('addValue').value;

        if (value === null || value.length < 1) {
            alert('Value field is required');
            return;
        }

        PWM_VAR['clientSettingCache'][keyName][iteration]['mimeTypes'].push(value);
        FormTableHandler.write(keyName);
        FormTableHandler.showMimeTypesDialog(keyName, iteration);
    });
};
