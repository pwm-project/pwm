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

var StringArrayValueHandler = {};

StringArrayValueHandler.init = function(keyName) {
    console.log('StringArrayValueHandler init for ' + keyName);

    {
        const parentDiv = 'table_setting_' + keyName;
        PWM_MAIN.getObject(parentDiv).innerHTML = '<div id="tableTop_' + keyName + '">';
    }
    const parentDiv = PWM_MAIN.getObject('tableTop_' + keyName);

    PWM_VAR['clientSettingCache'][keyName + "_options"] = PWM_VAR['clientSettingCache'][keyName + "_options"] || {};
    PWM_VAR['clientSettingCache'][keyName + "_options"]['parentDiv'] = parentDiv;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        StringArrayValueHandler.draw(keyName);

        const syntax = PWM_SETTINGS['settings'][keyName]['syntax'];
        if (syntax === 'PROFILE') {
            PWM_MAIN.getObject("resetButton-" + keyName).style.display = 'none';
            PWM_MAIN.getObject("helpButton-" + keyName).style.display = 'none';
            PWM_MAIN.getObject("modifiedNoticeIcon-" + keyName).style.display = 'none';
        }
    });
};


StringArrayValueHandler.draw = function(settingKey) {
    const parentDiv = PWM_VAR['clientSettingCache'][settingKey + "_options"]['parentDiv'];
    const parentDivElement = PWM_MAIN.getObject(parentDiv);

    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    const resultValue = PWM_VAR['clientSettingCache'][settingKey];

    const tableElement = document.createElement("table");
    tableElement.setAttribute("style", "border-width: 0;");

    const syntax = PWM_SETTINGS['settings'][settingKey]['syntax'];
    if (syntax === 'PROFILE') {
        const divDescriptionElement = document.createElement("div");
        let text = PWM_SETTINGS['settings'][settingKey]['description'];
        text += '<br/>' + PWM_CONFIG.showString('Display_ProfileNamingRules');
        divDescriptionElement.innerHTML = text;
        parentDivElement.appendChild(divDescriptionElement);

        const defaultProfileRow = document.createElement("tr");
        defaultProfileRow.setAttribute("colspan", "5");
    }

    let counter = 0;
    const itemCount = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]);
    parentDivElement.appendChild(tableElement);

    for (const i in resultValue) {
        (function(iteration) {
            StringArrayValueHandler.drawRow(settingKey, iteration, resultValue[iteration], itemCount, tableElement);
            counter++;
        })(i);
    }

    const settingProperties = PWM_SETTINGS['settings'][settingKey]['properties'];
    if (settingProperties && 'Maximum' in settingProperties && itemCount >= settingProperties['Maximum']) {
        // item count is already maxed out
    } else {
        const addItemButton = document.createElement("button");
        addItemButton.setAttribute("type", "button");
        addItemButton.setAttribute("class", "btn");
        addItemButton.setAttribute("id", "button-" + settingKey + "-addItem");
        addItemButton.innerHTML = '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>' + (syntax === 'PROFILE' ? "Add Profile" : "Add Value");
        parentDivElement.appendChild(addItemButton);

        PWM_MAIN.addEventHandler('button-' + settingKey + '-addItem', 'click', function () {
            StringArrayValueHandler.valueHandler(settingKey, -1);
        });
    }
};

StringArrayValueHandler.drawRow = function(settingKey, iteration, value, itemCount, parentDivElement) {
    const settingInfo = PWM_SETTINGS['settings'][settingKey];
    const settingProperties = PWM_SETTINGS['settings'][settingKey]['properties'];
    const syntax = settingInfo['syntax'];

    const inputID = 'value-' + settingKey + '-' + iteration;

    const valueRow = document.createElement("tr");
    valueRow.setAttribute("style", "border-width: 0");
    valueRow.setAttribute("id",inputID + "_row");

    let rowHtml = '';
    if (syntax !== 'PROFILE') {
        rowHtml = '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></td>';
    }
    rowHtml += '<td style=""><div class="configStringPanel" id="' + inputID + '"></div></td>';

    const copyButtonID = 'button-' + settingKey + '-' + iteration + '-copy';
    if (syntax === 'PROFILE') {
        rowHtml += '<td class="noborder nopadding" style="width:10px" title="Copy">';
        rowHtml += '<span id="' + copyButtonID + '" class="action-icon pwm-icon pwm-icon-copy"></span>';
        rowHtml += '</td>';
    } else if (syntax === 'DOMAIN') {
        rowHtml += '<td class="noborder nopadding" style="width:10px" title="Copy">';
        rowHtml += '<span id="' + copyButtonID + '" class="action-icon pwm-icon pwm-icon-copy"></span>';
        rowHtml += '</td>';
    }

    const showMoveButtons = syntax !== 'DOMAIN';
    const downButtonID = 'button-' + settingKey + '-' + iteration + '-moveDown';
    const upButtonID = 'button-' + settingKey + '-' + iteration + '-moveUp';
    if ( showMoveButtons ) {
        rowHtml += '<td class="noborder nopadding" style="width:10px" title="Move Down">';
        if (itemCount > 1 && iteration !== (itemCount - 1)) {
            rowHtml += '<span id="' + downButtonID + '" class="action-icon pwm-icon pwm-icon-chevron-down"></span>';
        }
        rowHtml += '</td>';

        rowHtml += '<td class="noborder nopadding" style="width:10px" title="Move Up">';
        if (itemCount > 1 && iteration !== 0) {
            rowHtml += '<span id="' + upButtonID + '" class="action-icon pwm-icon pwm-icon-chevron-up"></span>';
        }
        rowHtml += '</td>';
    }

    const minValuesRequired = settingProperties['Minimum'] ? settingProperties['Minimum'] : settingInfo['required'] ? 1 : 0;
    const showDeleteButtons = itemCount > minValuesRequired;
    const deleteButtonID = 'button-' + settingKey + '-' + iteration + '-delete';
    if (showDeleteButtons) {
        rowHtml += '<td class="noborder nopadding" style="width:10px" title="Delete">';
        rowHtml += '<span id="' + deleteButtonID + '" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></span>';
        rowHtml += '</td>';
    }

    valueRow.innerHTML = rowHtml;
    parentDivElement.appendChild(valueRow);

    let allowEditValue = true;
    if (syntax === 'PROFILE' || syntax === 'DOMAIN') {
        allowEditValue = false;
        PWM_MAIN.addEventHandler(copyButtonID, 'click', function () {
            const editorOptions = {};
            editorOptions['title'] = syntax === 'PROFILE' ? 'Copy Profile' : 'Copy Domain';
            editorOptions['instructions'] = syntax === 'PROFILE' ? 'Copy profile and all profile settings from existing "' + value + '" profile to a new profile.' :
                'Copy domain and all domain settings from existing "' + value + '" domain to a new domain.'
            editorOptions['regex'] = PWM_SETTINGS['settings'][settingKey]['pattern'];
            editorOptions['placeholder'] = PWM_SETTINGS['settings'][settingKey]['placeholder'];
            editorOptions['completeFunction'] = function (newValue) {
                const options = {};
                options['setting'] = settingKey;
                options['sourceID'] = value;
                options['destinationID'] = newValue;
                const resultFunction = function (data) {
                    if (data['error']) {
                        PWM_MAIN.showErrorDialog(data);
                    } else {
                        PWM_MAIN.gotoUrl('editor');
                    }
                };

                const actionName = syntax === 'PROFILE' ? 'copyProfile' : 'copyDomain';
                PWM_MAIN.showWaitDialog({
                    loadFunction: function () {
                        const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction',actionName);
                        PWM_MAIN.ajaxRequest(url, resultFunction, {content: options});
                    }
                });
            };
            UILibrary.stringEditorDialog(editorOptions);
        });
    }

    UILibrary.addTextValueToElement(inputID, value);

    if (allowEditValue) {
        PWM_MAIN.addEventHandler(inputID, 'click', function () {
            StringArrayValueHandler.valueHandler(settingKey, iteration);
        });
        PWM_MAIN.addEventHandler('button-' + inputID, 'click', function () {
            StringArrayValueHandler.valueHandler(settingKey, iteration);
        });
    }

    if (itemCount > 1 && iteration !== (itemCount -1)) {
        PWM_MAIN.addEventHandler(downButtonID,'click',function(){StringArrayValueHandler.move(settingKey,false,iteration)});
    }

    if (itemCount > 1 && iteration !== 0) {
        PWM_MAIN.addEventHandler(upButtonID,'click',function(){StringArrayValueHandler.move(settingKey,true,iteration)});
    }

    if (itemCount > 1 || !PWM_SETTINGS['settings'][settingKey]['required']) {
        PWM_MAIN.addEventHandler(deleteButtonID,'click',function(){StringArrayValueHandler.removeValue(settingKey,iteration)});
    }
};

StringArrayValueHandler.valueHandler = function(settingKey, iteration) {
    const okAction = function(value) {
        if (iteration > -1) {
            PWM_VAR['clientSettingCache'][settingKey][iteration] = value;
        } else {
            PWM_VAR['clientSettingCache'][settingKey].push(value);
        }
        StringArrayValueHandler.writeSetting(settingKey)
    };

    const editorOptions = {};
    editorOptions['title'] = PWM_SETTINGS['settings'][settingKey]['label'] + " - " + (iteration > -1 ? "Edit" : "Add") + " Value";
    editorOptions['regex'] = PWM_SETTINGS['settings'][settingKey]['pattern'];
    editorOptions['placeholder'] = PWM_SETTINGS['settings'][settingKey]['placeholder'];
    editorOptions['completeFunction'] = okAction;
    editorOptions['value'] = iteration > -1 ? PWM_VAR['clientSettingCache'][settingKey][iteration] : '';

    const isLdapDN = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'ldapDnSyntax');
    if (isLdapDN) {
        UILibrary.editLdapDN(okAction,{currentDN: editorOptions['value']});
    } else {
        UILibrary.stringEditorDialog(editorOptions);
    }
};

StringArrayValueHandler.move = function(settingKey, moveUp, iteration) {
    const currentValues = PWM_VAR['clientSettingCache'][settingKey];
    if (moveUp) {
        StringArrayValueHandler.arrayMoveUtil(currentValues, iteration, iteration - 1);
    } else {
        StringArrayValueHandler.arrayMoveUtil(currentValues, iteration, iteration + 1);
    }
    StringArrayValueHandler.writeSetting(settingKey)
};

StringArrayValueHandler.arrayMoveUtil = function(arr, fromIndex, toIndex) {
    const element = arr[fromIndex];
    arr.splice(fromIndex, 1);
    arr.splice(toIndex, 0, element);
};

StringArrayValueHandler.removeValue = function(settingKey, iteration) {
    const syntax = PWM_SETTINGS['settings'][settingKey]['syntax'];
    const profileName = PWM_VAR['clientSettingCache'][settingKey][iteration];
    const deleteFunction = function() {
        const currentValues = PWM_VAR['clientSettingCache'][settingKey];
        currentValues.splice(iteration,1);
        StringArrayValueHandler.writeSetting(settingKey,false);
    };
    if (syntax === 'PROFILE') {
        PWM_MAIN.showConfirmDialog({
            text:PWM_CONFIG.showString('Confirm_RemoveProfile',{value1:profileName}),
            okAction:function(){
                deleteFunction();
            }
        });
    } else if (syntax === 'DOMAIN') {
        PWM_MAIN.showConfirmDialog({
            text:PWM_CONFIG.showString('Confirm_RemoveDomain',{value1:profileName}),
            okAction:function(){
                deleteFunction();
            }
        });
    } else {
        deleteFunction();
    }
};

StringArrayValueHandler.writeSetting = function(settingKey, reload) {
    const syntax = PWM_SETTINGS['settings'][settingKey]['syntax'];
    const nextFunction = function() {
        if (syntax === 'PROFILE') {
            PWM_MAIN.gotoUrl('editor');
        }
        if (reload) {
            StringArrayValueHandler.init(settingKey);
        } else {
            StringArrayValueHandler.draw(settingKey);
        }
    };
    const currentValues = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, currentValues, nextFunction);
};