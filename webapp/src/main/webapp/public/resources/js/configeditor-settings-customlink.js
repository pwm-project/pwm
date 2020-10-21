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

// -------------------------- Custom link handler ------------------------------------

var CustomLinkHandler = {};
CustomLinkHandler.newRowValue = {
    name:'',
    labels:{'':''},
    description:{'':''}
};

CustomLinkHandler.init = function(keyName) {
    console.log('CustomLinkHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        CustomLinkHandler.redraw(keyName);
    });
};

CustomLinkHandler.redraw = function(keyName) {
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
        CustomLinkHandler.drawRow(parentDiv, keyName, i, resultValue[i]);
    }

    var buttonRow = document.createElement("tr");
    buttonRow.setAttribute("colspan","5");
    buttonRow.innerHTML = '<td><button class="btn" id="button-' + keyName + '-addRow"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Item</button></td>';

    parentDivElement.appendChild(buttonRow);

    PWM_MAIN.addEventHandler('button-' + keyName + '-addRow','click',function(){
        CustomLinkHandler.addRow(keyName);
    });

};

CustomLinkHandler.drawRow = function(parentDiv, settingKey, iteration, value) {
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

        htmlRow += '<td class="noborder" style="min-width:90px;"><button id="' + inputID + 'optionsButton"><span class="btn-icon pwm-icon pwm-icon-sliders"/> Options</button></td>';

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
            CustomLinkHandler.move(settingKey, true, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "-moveDown", 'click', function () {
            CustomLinkHandler.move(settingKey, false, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "-deleteRowButton", 'click', function () {
            CustomLinkHandler.removeRow(settingKey, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "label", 'click, keypress', function () {
            CustomLinkHandler.showLabelDialog(settingKey, iteration);
        });
        PWM_MAIN.addEventHandler("icon-editLabel-" + inputID, 'click, keypress', function () {
            CustomLinkHandler.showLabelDialog(settingKey, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "optionsButton", 'click', function () {
            CustomLinkHandler.showOptionsDialog(settingKey, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "name", 'input', function () {
            PWM_VAR['clientSettingCache'][settingKey][iteration]['name'] = PWM_MAIN.getObject(inputID + "name").value;
            CustomLinkHandler.write(settingKey);
        });
        PWM_MAIN.addEventHandler(inputID + "type", 'click', function () {
            PWM_VAR['clientSettingCache'][settingKey][iteration]['type'] = PWM_MAIN.getObject(inputID + "type").value;
            CustomLinkHandler.write(settingKey);
        });
};

CustomLinkHandler.write = function(settingKey, finishFunction) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

CustomLinkHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            var currentValues = PWM_VAR['clientSettingCache'][keyName];
            currentValues.splice(iteration,1);
            CustomLinkHandler.write(keyName,function(){
                CustomLinkHandler.init(keyName);
            });
        }
    });
};

CustomLinkHandler.move = function(settingKey, moveUp, iteration) {
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    if (moveUp) {
        CustomLinkHandler.arrayMoveUtil(currentValues, iteration, iteration - 1);
    } else {
        CustomLinkHandler.arrayMoveUtil(currentValues, iteration, iteration + 1);
    }
    CustomLinkHandler.write(settingKey);
    CustomLinkHandler.redraw(settingKey);
};

CustomLinkHandler.arrayMoveUtil = function(arr, fromIndex, toIndex) {
    var element = arr[fromIndex];
    arr.splice(fromIndex, 1);
    arr.splice(toIndex, 0, element);
};


CustomLinkHandler.addRow = function(keyName) {
    UILibrary.stringEditorDialog({
        title:PWM_SETTINGS['settings'][keyName]['label'] + ' - New Custom Link Key Name',
        instructions: 'Acceptable characters, a-z,A-Z,0-9',
        regex:'^[a-zA-Z][a-zA-Z0-9-]*$',
        placeholder:'KeyName',
        completeFunction:function(value){
            for (var i in PWM_VAR['clientSettingCache'][keyName]) {
                if (PWM_VAR['clientSettingCache'][keyName][i]['name'] === value) {
                    alert('key name already exists');
                    return;
                }
            }
            var currentSize = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][keyName]);
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1] = CustomLinkHandler.newRowValue;
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1].name = value;
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1].labels = {'':value};
            CustomLinkHandler.write(keyName,function(){
                CustomLinkHandler.init(keyName);
            });
        }
    });
};

CustomLinkHandler.showOptionsDialog = function(keyName, iteration) {
    var type = PWM_VAR['clientSettingCache'][keyName][iteration]['type'];
    var settings = PWM_SETTINGS['settings'][keyName];
    var options = 'options' in PWM_SETTINGS['settings'][keyName] ? PWM_SETTINGS['settings'][keyName]['options'] : {};

    var inputID = 'value_' + keyName + '_' + iteration + '_';
    var bodyText = '<div style="max-height: 500px; overflow-x: auto"><table class="noborder">';

    bodyText += '<tr>';
    var descriptionValue = PWM_VAR['clientSettingCache'][keyName][iteration]['description'][''];
    bodyText += '<td id="' + inputID + '-label-description" class="key" >Description</td><td>';
    bodyText += '<div class="noWrapTextBox" id="' + inputID + 'DescriptionValue"><span class="btn-icon pwm-icon pwm-icon-edit"></span><span>' + descriptionValue + '...</span></div>';
    bodyText += '</td>';

    bodyText += '</tr><tr>';

    var customLinkUrl = PWM_VAR['clientSettingCache'][keyName][iteration]['customLinkUrl'];
    bodyText += '<td id="' + inputID + '-Site-url" class="key" >Link URL</td><td>' +
        '<input placeholder="https://example.com" style="width: 350px;" type="url" class="key" id="' + inputID + 'SiteURL' + '" value="'+ customLinkUrl +'"/></td>';
    bodyText += '</tr><tr>';

    var checkedValue = PWM_VAR['clientSettingCache'][keyName][iteration]['customLinkNewWindow'];
    bodyText += '<td class="key" title="' + PWM_CONFIG.showString('Tooltip_Form_ShowInNewWindow') + '">Open link in new window</td><td><input type="checkbox" id="' + inputID + 'newWindow' + '" ';
    if(checkedValue) {
        bodyText += 'checked'
    }
    bodyText += '/></td>';

    bodyText += '</tr><tr>';

    bodyText += '</table></div>';

    var initDialogWidgets = function () {

        PWM_MAIN.addEventHandler(inputID + 'DescriptionValue', 'change', function () {
            CustomLinkHandler.showDescriptionDialog(keyName, iteration);
        });

        PWM_MAIN.addEventHandler(inputID + 'DescriptionValue', 'click', function () {
            CustomLinkHandler.showDescriptionDialog(keyName, iteration);
        });

        PWM_MAIN.addEventHandler(inputID + 'SiteURL', 'change', function () {
            PWM_VAR['clientSettingCache'][keyName][iteration]['customLinkUrl'] = this.value;
            CustomLinkHandler.write(keyName)
        });

        PWM_MAIN.addEventHandler(inputID + 'newWindow', 'click', function () {
            PWM_VAR['clientSettingCache'][keyName][iteration]['customLinkNewWindow'] = PWM_MAIN.getObject(inputID + 'newWindow').checked;
            CustomLinkHandler.write(keyName)
        });
    };

    PWM_MAIN.showDialog({
        title: PWM_SETTINGS['settings'][keyName]['label'] + ' - ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
        text: bodyText,
        allowMove: true,
        loadFunction: initDialogWidgets,
        okAction: function () {
            CustomLinkHandler.redraw(keyName);
        }
    });
};

CustomLinkHandler.showLabelDialog = function(keyName, iteration) {
    var finishAction = function(){ CustomLinkHandler.redraw(keyName); };
    var title = 'Label for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'];
    CustomLinkHandler.multiLocaleStringDialog(keyName, iteration, 'labels', finishAction, title);
};

CustomLinkHandler.multiLocaleStringDialog = function(keyName, iteration, settingType, finishAction, titleText) {
    require(["dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "label_";
        var bodyText = '<table class="noborder" id="' + inputID + 'table">';
        bodyText += '<tr>';
        for (var localeName in PWM_VAR['clientSettingCache'][keyName][iteration][settingType]) {
            var value = PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName];
            var localeID = inputID + localeName;
            bodyText += '<td>' + localeName + '</td>';
            bodyText += '<td><input style="width:420px" class="configStringInput" type="text" value="' + value + '" id="' + localeID + '-input"></input></td>';
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
                        var localeID = inputID + localeName;
                        PWM_MAIN.addEventHandler(localeID + '-input', 'input', function () {
                            var inputElement = PWM_MAIN.getObject(localeID + '-input');
                            var value = inputElement.value;
                            PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName] = value;
                            CustomLinkHandler.write(keyName);
                        });
                        PWM_MAIN.addEventHandler(localeID + '-removeLocaleButton', 'click', function () {
                            delete PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName];
                            CustomLinkHandler.write(keyName);
                            CustomLinkHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                        });
                    }(iter));
                }
                UILibrary.addAddLocaleButtonRow(inputID + 'table', inputID, function(localeName){
                    if (localeName in PWM_VAR['clientSettingCache'][keyName][iteration][settingType]) {
                        alert('Locale is already present');
                    } else {
                        PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName] = '';
                        CustomLinkHandler.write(keyName);
                        CustomLinkHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                    }
                }, Object.keys(PWM_VAR['clientSettingCache'][keyName][iteration][settingType]));
            }
        });
    });
};


CustomLinkHandler.showDescriptionDialog = function(keyName, iteration) {
    var finishAction = function(){ CustomLinkHandler.showOptionsDialog(keyName, iteration); };
    var title = 'Description for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'];
    CustomLinkHandler.multiLocaleStringDialog(keyName, iteration, 'description', finishAction, title);
};

