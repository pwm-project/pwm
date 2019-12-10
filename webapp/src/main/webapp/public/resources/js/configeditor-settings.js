/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

    var parentDiv = 'table_setting_' + settingKey;
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
    var parentDiv = PWM_VAR['clientSettingCache'][settingKey + "_parentDiv"];
    var settingData = PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey];

    var resultValue = PWM_VAR['clientSettingCache'][settingKey];
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
        for (var localeKey in resultValue) {
            LocalizedStringValueHandler.drawRow(parentDiv, settingKey, localeKey, resultValue[localeKey])
        }
        UILibrary.addAddLocaleButtonRow(parentDiv, settingKey, function(localeKey) {
            LocalizedStringValueHandler.addLocaleSetting(settingKey, localeKey);
        }, Object.keys(resultValue));
    }

    PWM_VAR['clientSettingCache'][settingKey] = resultValue;
};

LocalizedStringValueHandler.drawRow = function(parentDiv, settingKey, localeString, value) {
    var settingData = PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey];
    var inputID = 'value-' + settingKey + '-' + localeString;

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    var tableHtml = '<td style="border-width:0; width: 15px">';
    if (localeString !== null && localeString.length > 0) {
        tableHtml += localeString;
    }
    tableHtml += '</td>';

    tableHtml += '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></ta>';

    tableHtml += '<td id="panel-' + inputID + '">';
    tableHtml += '<div id="value-' + inputID + '" class="configStringPanel"></div>';
    tableHtml += '</td>';

    var defaultLocale = (localeString === null || localeString.length < 1);
    var required = settingData['required'];
    var hasNonDefaultValues = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1 ;

    if (!defaultLocale || !required && !hasNonDefaultValues) {
        tableHtml += '<div style="width: 10px; height: 10px;" class="delete-row-icon action-icon pwm-icon pwm-icon-times"'
            + 'id="button-' + settingKey + '-' + localeString + '-deleteRow"></div>';
    }

    newTableRow.innerHTML = tableHtml;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    PWM_MAIN.addEventHandler("button-" + settingKey + '-' + localeString + "-deleteRow","click",function(){
        LocalizedStringValueHandler.removeLocaleSetting(settingKey, localeString);
    });
    UILibrary.addTextValueToElement('value-' + inputID, (value !== null && value.length > 0) ? value : ' ');

    var editFunction = function() {
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
    var existingValues = PWM_VAR['clientSettingCache'][settingKey];
    existingValues[locale] = value;
    PWM_CFGEDIT.writeSetting(settingKey, existingValues);
    LocalizedStringValueHandler.draw(settingKey);
};

LocalizedStringValueHandler.removeLocaleSetting = function(settingKey, locale) {
    var existingValues = PWM_VAR['clientSettingCache'][settingKey];
    delete existingValues[locale];
    PWM_CFGEDIT.writeSetting(settingKey, existingValues);
    LocalizedStringValueHandler.draw(settingKey);
};

LocalizedStringValueHandler.addLocaleSetting = function(settingKey, localeKey) {
    var existingValues = PWM_VAR['clientSettingCache'][settingKey];
    var settingData = PWM_VAR['LocalizedStringValueHandler-settingData'][settingKey];
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




// -------------------------- string array value handler ------------------------------------

var StringArrayValueHandler = {};

StringArrayValueHandler.init = function(keyName) {
    console.log('StringArrayValueHandler init for ' + keyName);

    var parentDiv = 'table_setting_' + keyName;
    PWM_MAIN.getObject(parentDiv).innerHTML = '<div id="tableTop_' + keyName + '">';
    parentDiv = PWM_MAIN.getObject('tableTop_' + keyName);

    PWM_VAR['clientSettingCache'][keyName + "_options"] = PWM_VAR['clientSettingCache'][keyName + "_options"] || {};
    PWM_VAR['clientSettingCache'][keyName + "_options"]['parentDiv'] = parentDiv;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        StringArrayValueHandler.draw(keyName);

        var syntax = PWM_SETTINGS['settings'][keyName]['syntax'];
        if (syntax === 'PROFILE') {
            PWM_MAIN.getObject("resetButton-" + keyName).style.display = 'none';
            PWM_MAIN.getObject("helpButton-" + keyName).style.display = 'none';
            PWM_MAIN.getObject("modifiedNoticeIcon-" + keyName).style.display = 'none';
        }
    });
};


StringArrayValueHandler.draw = function(settingKey) {
    var parentDiv = PWM_VAR['clientSettingCache'][settingKey + "_options"]['parentDiv'];
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    var resultValue = PWM_VAR['clientSettingCache'][settingKey];

    var tableElement = document.createElement("table");
    tableElement.setAttribute("style", "border-width: 0;");

    var syntax = PWM_SETTINGS['settings'][settingKey]['syntax'];
    if (syntax === 'PROFILE') {
        var divDescriptionElement = document.createElement("div");
        var text = PWM_SETTINGS['settings'][settingKey]['description'];
        text += '<br/>' + PWM_CONFIG.showString('Display_ProfileNamingRules');
        divDescriptionElement.innerHTML = text;
        parentDivElement.appendChild(divDescriptionElement);

        var defaultProfileRow = document.createElement("tr");
        defaultProfileRow.setAttribute("colspan", "5");
    }

    var counter = 0;
    var itemCount = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]);
    parentDivElement.appendChild(tableElement);

    for (var i in resultValue) {
        (function(iteration) {
            StringArrayValueHandler.drawRow(settingKey, iteration, resultValue[iteration], itemCount, tableElement);
            counter++;
        })(i);
    }

    var settingProperties = PWM_SETTINGS['settings'][settingKey]['properties'];
    if (settingProperties && 'Maximum' in settingProperties && itemCount >= settingProperties['Maximum']) {
        // item count is already maxed out
    } else {
        var addItemButton = document.createElement("button");
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
    var settingInfo = PWM_SETTINGS['settings'][settingKey];
    var syntax = settingInfo['syntax'];

    var inputID = 'value-' + settingKey + '-' + iteration;

    var valueRow = document.createElement("tr");
    valueRow.setAttribute("style", "border-width: 0");
    valueRow.setAttribute("id",inputID + "_row");

    var rowHtml = '';
    if (syntax !== 'PROFILE') {
        rowHtml = '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></td>';
    }
    rowHtml += '<td style=""><div class="configStringPanel" id="' + inputID + '"></div></td>';

    if (syntax === 'PROFILE') {
        var copyButtonID = 'button-' + settingKey + '-' + iteration + '-copy';
        rowHtml += '<td class="noborder nopadding" style="width:10px" title="Copy">';
        rowHtml += '<span id="' + copyButtonID + '" class="action-icon pwm-icon pwm-icon-copy"></span>';
        rowHtml += '</td>';
    }

    var downButtonID = 'button-' + settingKey + '-' + iteration + '-moveDown';
    rowHtml += '<td class="noborder nopadding" style="width:10px" title="Move Down">';
    if (itemCount > 1 && iteration !== (itemCount -1)) {
        rowHtml += '<span id="' + downButtonID + '" class="action-icon pwm-icon pwm-icon-chevron-down"></span>';
    }
    rowHtml += '</td>';

    var upButtonID = 'button-' + settingKey + '-' + iteration + '-moveUp';
    rowHtml += '<td class="noborder nopadding" style="width:10px" title="Move Up">';
    if (itemCount > 1 && iteration !== 0) {
        rowHtml += '<span id="' + upButtonID + '" class="action-icon pwm-icon pwm-icon-chevron-up"></span>';
    }
    rowHtml += '</td>';

    var deleteButtonID = 'button-' + settingKey + '-' + iteration + '-delete';
    rowHtml += '<td class="noborder nopadding" style="width:10px" title="Delete">';

    if (itemCount > 1 || (!settingInfo['required'])) {
        rowHtml += '<span id="' + deleteButtonID + '" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></span>';
    }
    rowHtml += '</td>';


    valueRow.innerHTML = rowHtml;
    parentDivElement.appendChild(valueRow);

    UILibrary.addTextValueToElement(inputID, value);
    if (syntax !== 'PROFILE') {
        PWM_MAIN.addEventHandler(inputID,'click',function(){
            StringArrayValueHandler.valueHandler(settingKey,iteration);
        });
        PWM_MAIN.addEventHandler('button-' + inputID,'click',function(){
            StringArrayValueHandler.valueHandler(settingKey,iteration);
        });
    } else {
        PWM_MAIN.addEventHandler(copyButtonID,'click',function(){
            var editorOptions = {};
            editorOptions['title'] = 'Copy Profile - New Profile ID';
            editorOptions['regex'] = PWM_SETTINGS['settings'][settingKey]['pattern'];
            editorOptions['placeholder'] = PWM_SETTINGS['settings'][settingKey]['placeholder'];
            editorOptions['completeFunction'] = function(newValue){
                var options = {};
                options['setting'] = settingKey;
                options['sourceID'] = value;
                options['destinationID'] = newValue;
                var resultFunction = function(data){
                    if (data['error']) {
                        PWM_MAIN.showErrorDialog(data);
                    } else {
                        PWM_MAIN.gotoUrl('editor');
                    }
                };
                PWM_MAIN.showWaitDialog({loadFunction:function(){
                        PWM_MAIN.ajaxRequest("editor?processAction=copyProfile",resultFunction,{content:options});
                    }});
            };
            UILibrary.stringEditorDialog(editorOptions);
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
    var okAction = function(value) {
        if (iteration > -1) {
            PWM_VAR['clientSettingCache'][settingKey][iteration] = value;
        } else {
            PWM_VAR['clientSettingCache'][settingKey].push(value);
        }
        StringArrayValueHandler.writeSetting(settingKey)
    };

    var editorOptions = {};
    editorOptions['title'] = PWM_SETTINGS['settings'][settingKey]['label'] + " - " + (iteration > -1 ? "Edit" : "Add") + " Value";
    editorOptions['regex'] = PWM_SETTINGS['settings'][settingKey]['pattern'];
    editorOptions['placeholder'] = PWM_SETTINGS['settings'][settingKey]['placeholder'];
    editorOptions['completeFunction'] = okAction;
    editorOptions['value'] = iteration > -1 ? PWM_VAR['clientSettingCache'][settingKey][iteration] : '';

    var isLdapDN = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'ldapDNsyntax');
    if (isLdapDN) {
        UILibrary.editLdapDN(okAction,{currentDN: editorOptions['value']});
    } else {
        UILibrary.stringEditorDialog(editorOptions);
    }
};

StringArrayValueHandler.move = function(settingKey, moveUp, iteration) {
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    if (moveUp) {
        StringArrayValueHandler.arrayMoveUtil(currentValues, iteration, iteration - 1);
    } else {
        StringArrayValueHandler.arrayMoveUtil(currentValues, iteration, iteration + 1);
    }
    StringArrayValueHandler.writeSetting(settingKey)
};

StringArrayValueHandler.arrayMoveUtil = function(arr, fromIndex, toIndex) {
    var element = arr[fromIndex];
    arr.splice(fromIndex, 1);
    arr.splice(toIndex, 0, element);
};

StringArrayValueHandler.removeValue = function(settingKey, iteration) {
    var syntax = PWM_SETTINGS['settings'][settingKey]['syntax'];
    var profileName = PWM_VAR['clientSettingCache'][settingKey][iteration];
    var deleteFunction = function() {
        var currentValues = PWM_VAR['clientSettingCache'][settingKey];
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
    } else {
        deleteFunction();
    }
};

StringArrayValueHandler.writeSetting = function(settingKey, reload) {
    var syntax = PWM_SETTINGS['settings'][settingKey]['syntax'];
    var nextFunction = function() {
        if (syntax === 'PROFILE') {
            PWM_MAIN.gotoUrl('editor');
        }
        if (reload) {
            StringArrayValueHandler.init(settingKey);
        } else {
            StringArrayValueHandler.draw(settingKey);
        }
    };
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, currentValues, nextFunction);
};

// -------------------------- multi locale table handler ------------------------------------

var MultiLocaleTableHandler = {};

MultiLocaleTableHandler.initMultiLocaleTable = function(keyName) {
    console.log('MultiLocaleTableHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;

    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        MultiLocaleTableHandler.draw(keyName);
    });
};

MultiLocaleTableHandler.draw = function(keyName) {
    var parentDiv = 'table_setting_' + keyName;
    var regExPattern = PWM_SETTINGS['settings'][keyName]['pattern'];

    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    require(["dojo","dijit/registry","dojo/parser","dijit/form/Button","dijit/form/ValidationTextBox","dijit/form/Textarea","dijit/registry"],function(dojo,registry,dojoParser){
        PWM_CFGEDIT.clearDivElements(parentDiv, false);
        for (var localeName in resultValue) {
            var localeTableRow = document.createElement("tr");
            localeTableRow.setAttribute("style", "border-width: 0;");

            var localeTdName = document.createElement("td");
            localeTdName.setAttribute("style", "border-width: 0; width:15px");
            localeTdName.innerHTML = localeName;
            localeTableRow.appendChild(localeTdName);

            var localeTdContent = document.createElement("td");
            localeTdContent.setAttribute("style", "border-width: 0; width: 525px");
            localeTableRow.appendChild(localeTdContent);

            var localeTableElement = document.createElement("table");
            localeTableElement.setAttribute("style", "border-width: 0px; width:525px; margin:0");
            localeTdContent.appendChild(localeTableElement);

            var multiValues = resultValue[localeName];

            for (var iteration in multiValues) {

                var valueTableRow = document.createElement("tr");

                var valueTd1 = document.createElement("td");
                valueTd1.setAttribute("style", "border-width: 0;");

                // clear the old dijit node (if it exists)
                var inputID = "value-" + keyName + "-" + localeName + "-" + iteration;
                var oldDijitNode = registry.byId(inputID);
                if (oldDijitNode !== null) {
                    try {
                        oldDijitNode.destroy();
                    } catch (error) {
                    }
                }

                var inputElement = document.createElement("input");
                inputElement.setAttribute("id", inputID);
                inputElement.setAttribute("value", multiValues[iteration]);
                inputElement.setAttribute("onchange", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',this.value,'" + regExPattern + "')");
                inputElement.setAttribute("style", "width: 480px; padding: 5px;");
                inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
                inputElement.setAttribute("regExp", regExPattern);
                inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
                valueTd1.appendChild(inputElement);
                valueTableRow.appendChild(valueTd1);
                localeTableElement.appendChild(valueTableRow);

                // add remove button
                var imgElement = document.createElement("div");
                imgElement.setAttribute("style", "width: 10px; height: 10px;");
                imgElement.setAttribute("class", "delete-row-icon action-icon pwm-icon pwm-icon-times");
                imgElement.setAttribute("id", inputID + "-remove");
                valueTd1.appendChild(imgElement);
            }

            { // add row button for this locale group
                var newTableRow = document.createElement("tr");
                newTableRow.setAttribute("style", "border-width: 0");
                newTableRow.setAttribute("colspan", "5");

                var newTableData = document.createElement("td");
                newTableData.setAttribute("style", "border-width: 0;");

                var addItemButton = document.createElement("button");
                addItemButton.setAttribute("type", "button");
                addItemButton.setAttribute("onclick", "PWM_VAR['clientSettingCache']['" + keyName + "']['" + localeName + "'].push('');MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "',null,null,null,'" + regExPattern + "')");
                addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
                addItemButton.innerHTML = "Add Value";
                newTableData.appendChild(addItemButton);

                newTableRow.appendChild(newTableData);
                localeTableElement.appendChild(newTableRow);
            }

            if (localeName !== '') { // add remove locale x
                var imgElement2 = document.createElement("div");
                imgElement2.setAttribute("id", "div-" + keyName + "-" + localeName + "-remove");
                imgElement2.setAttribute("class", "delete-row-icon action-icon pwm-icon pwm-icon-times");
                var tdElement = document.createElement("td");
                tdElement.setAttribute("style", "border-width: 0; text-align: left; vertical-align: top;width 10px");

                localeTableRow.appendChild(tdElement);
                tdElement.appendChild(imgElement2);
            }

            var parentDivElement = PWM_MAIN.getObject(parentDiv);
            parentDivElement.appendChild(localeTableRow);

            { // add a spacer row
                var spacerTableRow = document.createElement("tr");
                spacerTableRow.setAttribute("style", "border-width: 0");
                parentDivElement.appendChild(spacerTableRow);

                var spacerTableData = document.createElement("td");
                spacerTableData.setAttribute("style", "border-width: 0");
                spacerTableData.innerHTML = "&nbsp;";
                spacerTableRow.appendChild(spacerTableData);
            }
        }

        var addLocaleFunction = function(value) {
            require(["dijit/registry"],function(registry){
                MultiLocaleTableHandler.writeMultiLocaleSetting(keyName, value, 0, '', regExPattern);
            });
        };

        UILibrary.addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction, Object.keys(resultValue));
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        dojoParser.parse(parentDiv);

        for (var localeName in resultValue) {
            var inputID = "value-" + keyName + "-" + localeName + "-" + iteration;

            var removeID = inputID + "-remove";
            PWM_MAIN.addEventHandler(removeID,'click',function(){
                MultiLocaleTableHandler.writeMultiLocaleSetting(keyName,localeName,iteration,null,regExPattern);
            });


            var removeID = "div-" + keyName + "-" + localeName + "-remove";
            PWM_MAIN.addEventHandler(removeID,'click',function(){
                MultiLocaleTableHandler.writeMultiLocaleSetting(keyName,localeName,null,null,regExPattern);
            });
        }

    });
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

var ChangePasswordHandler = {};

ChangePasswordHandler.init = function(settingKey) {
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    if (parentDivElement) {
        PWM_CFGEDIT.readSetting(settingKey,function(data){
            var hasPassword = !data['isDefault'];
            var htmlBody = '';
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
    var password1 = PWM_MAIN.getObject('password1').value;
    var password2 = PWM_MAIN.getObject('password2').value;

    var matchStatus = "";

    var properties = settingKey === undefined || PWM_SETTINGS['settings'][settingKey] === undefined ? {} : PWM_SETTINGS['settings'][settingKey]['properties'];
    var minLength = properties && 'Minimum' in properties ? properties['Minimum'] : 1;

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
    var length = PWM_VAR['passwordDialog-randomLength'];
    var special = PWM_VAR['passwordDialog-special'];

    if (!PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields']) {
        PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = true;
    }

    var charMap = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    if (special) {
        charMap += '~`!@#$%^&*()_-+=;:,.[]{}';
    }
    var postData = { };
    postData.maxLength = length;
    postData.minLength = length;
    postData.chars = charMap;
    postData.noUser = true;
    PWM_MAIN.getObject('button-storePassword').disabled = true;

    var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','randomPassword');
    var loadFunction = function(data) {
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
    var writeFunction = PWM_VAR['clientSettingCache'][settingKey]['settings']['writeFunction'];
    var showFields = PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'];
    var p1 = PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'];
    var p2 = PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'];
    var properties = settingKey === undefined || PWM_SETTINGS['settings'][settingKey] === undefined ? {} : PWM_SETTINGS['settings'][settingKey]['properties'];
    var minLength = properties && 'Minimum' in properties ? properties['Minimum'] : 1;
    var randomLength = 'passwordDialog-randomLength' in PWM_VAR ? PWM_VAR['passwordDialog-randomLength'] : 25;
    randomLength = randomLength < minLength ? minLength : randomLength;
    var special = 'passwordDialog-special' in PWM_VAR ? PWM_VAR['passwordDialog-special'] : false;

    var bodyText = '';
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
                var passwordValue = PWM_MAIN.getObject('password1').value;
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

var BooleanHandler = {};

BooleanHandler.init = function(keyName) {
    console.log('BooleanHandler init for ' + keyName);

    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    parentDivElement.innerHTML = '<label class="checkboxWrapper">'
        + '<input type="checkbox" id="value_' + keyName + '" value="false" disabled/>'
        + 'Enabled (True)</label>';

    PWM_CFGEDIT.readSetting(keyName,function(data){
        var checkElement = PWM_MAIN.getObject("value_" + keyName);
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


// -------------------------- user permission handler ------------------------------------

var UserPermissionHandler = {};
UserPermissionHandler.defaultFilterValue = {type:'ldapFilter',ldapQuery:"(objectClass=*)",ldapBase:""};
UserPermissionHandler.defaultGroupValue = {type:'ldapGroup',ldapBase:""};

UserPermissionHandler.init = function(keyName) {
    console.log('UserPermissionHandler init for ' + keyName);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        UserPermissionHandler.draw(keyName);
    });
};

UserPermissionHandler.draw = function(keyName) {
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    while (parentDivElement.firstChild) {
        parentDivElement.removeChild(parentDivElement.firstChild);
    }

    var htmlBody = '';
    for (var iteration in resultValue) {
        (function(rowKey) {
            var inputID = "value-" + keyName + "-" + rowKey;

            if (htmlBody.length > 0) {
                htmlBody += '<br/><br/><div style="clear:both; text-align:center">OR</span></div>'
            }

            htmlBody += '<div class="setting_item_value_wrapper" style="float:left; width: 570px;"><div style="width:100%; text-align:center">';

            var currentProfileValue = ('ldapProfileID' in resultValue[rowKey]) ? resultValue[rowKey]['ldapProfileID'] : "";
            htmlBody += '</div><table class="noborder">'
                + '<td style="width:200px" id="' + inputID + '_profileHeader' + '">' + PWM_CONFIG.showString('Setting_Permission_Profile') + '</td>'
                + '<td></td>'
                + '<td><input style="width: 200px;" class="configStringInput" id="' + inputID + '-profile" list="' + inputID + '-datalist" value="' +  currentProfileValue + '"/>'
                + '<datalist id="' + inputID + '-datalist"/></td>'
                + '</tr>';

            if (resultValue[rowKey]['type'] !== 'ldapGroup') {
                htmlBody += '<tr>'
                    + '<td><span id="' + inputID + '_FilterHeader' + '">' + PWM_CONFIG.showString('Setting_Permission_Filter') + '</span></td>'
                    + '<td id="icon-edit-query-' + inputID + '"><div title="Edit Value" class="btn-icon pwm-icon pwm-icon-edit"></div></td>'
                    + '<td><div style="width: 350px; padding: 5px;" class="configStringPanel noWrapTextBox border" id="' + inputID + '-query"></div></td>'
                    + '</tr>';
            }

            htmlBody += '<tr>'
                + '<td><span id="' + inputID + '_BaseHeader' + '">'
                + PWM_CONFIG.showString((resultValue[rowKey]['type'] === 'ldapGroup') ?  'Setting_Permission_Base_Group' : 'Setting_Permission_Base')
                + '</span></td>'
                + '<td id="icon-edit-base-' + inputID + '"><div title="Edit Value" class="btn-icon pwm-icon pwm-icon-edit"></div></td>'
                + '<td><div style="width: 350px; padding: 5px;" class="configStringPanel noWrapTextBox border" id="' + inputID + '-base">&nbsp;</div></td>'
                + '</tr>';


            htmlBody += '</table></div><div id="button-' + inputID + '-deleteRow" style="float:right" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></div>';
        }(iteration));
    }
    parentDivElement.innerHTML = parentDivElement.innerHTML + htmlBody;

    setTimeout(function(){
        for (var iteration in resultValue) {
            (function(rowKey) {
                var inputID = "value-" + keyName + "-" + rowKey;
                console.log('inputID-' + inputID);

                var profileSelectElement = PWM_MAIN.getObject(inputID + "-datalist");
                profileSelectElement.appendChild(new Option('all'));
                var profileIdList = PWM_SETTINGS['var']['ldapProfileIds'];
                for (var i in profileIdList) {
                    profileSelectElement.appendChild(new Option(profileIdList[i]));
                }

                PWM_MAIN.addEventHandler(inputID + '-profile','input',function(){
                    console.log('write');
                    PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapProfileID'] = this.value;
                    UserPermissionHandler.write(keyName);
                });

                if (resultValue[rowKey]['type'] !== 'ldapGroup') {
                    UILibrary.addTextValueToElement(inputID + '-query', PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapQuery']);
                    var queryEditor = function(){
                        UILibrary.stringEditorDialog({
                            value:PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapQuery'],
                            completeFunction:function(value) {
                                PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapQuery'] = value;
                                UserPermissionHandler.write(keyName,true);
                            }
                        });
                    };

                    PWM_MAIN.addEventHandler(inputID + "-query",'click',function(){
                        queryEditor();
                    });
                    PWM_MAIN.addEventHandler('icon-edit-query-' + inputID,'click',function(){
                        queryEditor();
                    });
                }

                var currentBaseValue = ('ldapBase' in resultValue[rowKey]) ? resultValue[rowKey]['ldapBase'] : "";
                var baseEditor = function(){
                    UILibrary.editLdapDN(function(value, ldapProfileID) {
                        PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapProfileID'] = ldapProfileID;
                        PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapBase'] = value;
                        UserPermissionHandler.write(keyName,true);
                    }, {currentDN: currentBaseValue});
                };
                if (currentBaseValue && currentBaseValue.length > 0) {
                    UILibrary.addTextValueToElement(inputID + '-base', currentBaseValue);
                }
                PWM_MAIN.addEventHandler(inputID + '-base','click',function(){
                    baseEditor();
                });
                PWM_MAIN.addEventHandler('icon-edit-base-' + inputID,'click',function(){
                    baseEditor();
                });


                var deleteButtonID = 'button-' + inputID + '-deleteRow';
                var hasID = PWM_MAIN.getObject(deleteButtonID) ? "YES" : "NO";
                console.log("addEventHandler row: " + deleteButtonID + " rowKey=" + rowKey + " hasID="+hasID);
                PWM_MAIN.addEventHandler(deleteButtonID,'click',function(){
                    console.log("delete row: " + inputID + " rowKey=" + rowKey + " hasID="+hasID);
                    delete PWM_VAR['clientSettingCache'][keyName][rowKey];
                    UserPermissionHandler.write(keyName,true);
                });

                PWM_MAIN.showTooltip({
                    id:inputID +'_profileHeader',
                    width: 300,
                    text:PWM_CONFIG.showString('Tooltip_Setting_Permission_Profile')
                });
                PWM_MAIN.showTooltip({
                    id:inputID +'_FilterHeader',
                    width: 300,
                    text:PWM_CONFIG.showString('Tooltip_Setting_Permission_Filter')
                });
                PWM_MAIN.showTooltip({
                    id: inputID + '_BaseHeader',
                    width: 300,
                    text: PWM_CONFIG.showString('Tooltip_Setting_Permission_Base')
                });
            }(iteration));
        }
    },10);

    var options = PWM_SETTINGS['settings'][keyName]['options'];

    var buttonRowHtml = '<button class="btn" id="button-' + keyName + '-addFilterValue">'
        + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Filter</button>';

    var hideGroup = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][keyName]['flags'], 'Permission_HideGroups');
    if (!hideGroup) {
        buttonRowHtml += '<button class="btn" id="button-' + keyName + '-addGroupValue">'
            + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Group</button>';
    }

    var hideMatch = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][keyName]['flags'], 'Permission_HideMatch');
    if (!hideMatch) {
        buttonRowHtml += '<button id="button-' + keyName + '-viewMatches" class="btn">'
            + '<span class="btn-icon pwm-icon pwm-icon-user"></span>View Matches</button>';
    }

    parentDivElement.innerHTML = parentDivElement.innerHTML + buttonRowHtml;

    PWM_MAIN.addEventHandler('button-' + keyName + '-viewMatches','click',function(){
        var dataHandler = function(data) {
            var html = PWM_CONFIG.convertListOfIdentitiesToHtml(data['data']);
            PWM_MAIN.showDialog({title:'Matches',text:html});
        };
        PWM_CFGEDIT.executeSettingFunction(keyName, 'password.pwm.config.function.UserMatchViewerFunction', dataHandler, null)
    });

    PWM_MAIN.addEventHandler('button-' + keyName + '-addFilterValue','click',function(){
        PWM_VAR['clientSettingCache'][keyName].push(PWM_MAIN.copyObject(UserPermissionHandler.defaultFilterValue));
        UserPermissionHandler.write(keyName, true);
    });

    PWM_MAIN.addEventHandler('button-' + keyName + '-addGroupValue','click',function(){
        PWM_VAR['clientSettingCache'][keyName].push(PWM_MAIN.copyObject(UserPermissionHandler.defaultGroupValue));
        UserPermissionHandler.write(keyName, true);
    });
};

UserPermissionHandler.write = function(settingKey,redraw) {
    var nextFunction = function(){
        if (redraw) {
            UserPermissionHandler.draw(settingKey);
        }
    };
    PWM_CFGEDIT.writeSetting(settingKey, PWM_VAR['clientSettingCache'][settingKey], nextFunction);
};

// -------------------------- option list handler ------------------------------------

var OptionListHandler = {};
OptionListHandler.defaultItem = [];

OptionListHandler.init = function(keyName) {
    console.log('OptionListHandler init for ' + keyName);

    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var htmlBody = '';
    var options = PWM_SETTINGS['settings'][keyName]['options'];
    for (var key in options) {
        (function (optionKey) {
            var buttonID = keyName + "_button_" + optionKey;
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
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    require(["dojo/_base/array"],function(array){
        var options = PWM_SETTINGS['settings'][keyName]['options'];
        for (var key in options) {
            (function (optionKey) {
                var buttonID = keyName + "_button_" + optionKey;
                var checked = array.indexOf(resultValue,optionKey) > -1;
                PWM_MAIN.getObject(buttonID).checked = checked;
                PWM_MAIN.getObject(buttonID).disabled = false;
                PWM_MAIN.addEventHandler(buttonID,'change',function(){
                    OptionListHandler.toggle(keyName,optionKey);
                });
            })(key);
        }
    });
};

OptionListHandler.toggle = function(keyName,optionKey) {
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    require(["dojo/_base/array"],function(array){
        var checked = array.indexOf(resultValue,optionKey) > -1;
        if (checked) {
            var index = array.indexOf(resultValue, optionKey);
            while (index > -1) {
                resultValue.splice(index, 1);
                index = array.indexOf(resultValue, optionKey);
            }
        } else {
            resultValue.push(optionKey);
        }
    });
    PWM_CFGEDIT.writeSetting(keyName, resultValue);
};

// -------------------------- numeric value handler ------------------------------------

var NumericValueHandler = {};
NumericValueHandler.init = function(settingKey) {
    NumericValueHandler.impl(settingKey, 'number', 0, 100);
};

NumericValueHandler.impl = function(settingKey, type, defaultMin, defaultMax) {
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    var properties = PWM_SETTINGS['settings'][settingKey]['properties'];
    var min = 'Minimum' in properties ? parseInt(properties['Minimum']) : defaultMin;
    var max = 'Maximum' in properties ? parseInt(properties['Maximum']) : defaultMax;

    var htmlBody = '<input type="number" id="value_' + settingKey + '" class="configNumericInput" min="'+min+'" max="'+max+'"/>';
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
    var displayElement = PWM_MAIN.getObject('display-' + settingKey + '-duration');
    if (displayElement) {
        displayElement.innerHTML = (numberValue && numberValue !== 0)
            ? PWM_MAIN.convertSecondsToDisplayTimeDuration(numberValue, true)
            : '';
    }
};



// -------------------------- duration value ---------------------------

var DurationValueHandler = {};
DurationValueHandler.init = function(settingKey) {
    NumericValueHandler.impl(settingKey, 'duration', -1, 365 * 24 * 60 * 60, 1 );
};

// -------------------------- numeric array value handler ------------------------------------

var NumericArrayValueHandler = {};
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
    var resultValue = PWM_VAR['clientSettingCache'][settingKey];

    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    var properties = PWM_SETTINGS['settings'][settingKey]['properties'];
    var min = 'Minimum' in properties ? parseInt(properties['Minimum']) : 1;
    var max = 'Maximum' in properties ? parseInt(properties['Maximum']) : 365 * 24 * 60 * 60;
    var minValues = 'Minimum_Values' in properties ? parseInt(properties['Minimum_Values']) : 1;
    var maxValues = 'Maximum_Values' in properties ? parseInt(properties['Maximum_Values']) : 10;

    var htmlBody = '<table class="noborder">';
    for (var iteration in resultValue) {
        (function(rowKey) {
            var id = settingKey+ "-" + rowKey;

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

    var addListeners = function() {
        for (var iteration in resultValue) {
            (function(rowKey) {
                var id = settingKey+ "-" + rowKey;
                var readValue  = resultValue[rowKey];
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

var DurationArrayValueHandler = {};
DurationArrayValueHandler.init = function(settingKey) {
    NumericArrayValueHandler.impl(settingKey, 'duration');
};


// -------------------------- string value handler ------------------------------------

var StringValueHandler = {};

StringValueHandler.init = function(settingKey) {
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    var settingData = PWM_SETTINGS['settings'][settingKey];
    PWM_CFGEDIT.readSetting(settingKey,function(data) {
        var inputID = settingKey;
        var bodyHtml = '';
        var value = data;
        if (value && value.length > 0) {
            bodyHtml += '<table style="border-width: 0">';
            bodyHtml += '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></ta>';
            bodyHtml += '<td style=""><div class="configStringPanel" id="panel-' + inputID + '"></div></td>';
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

        var isLdapDN = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'], 'ldapDNsyntax');
        var editor = function(){
            var writeBackFunc = function(value){
                PWM_CFGEDIT.writeSetting(settingKey,value,function(){
                    StringValueHandler.init(settingKey);
                });
            };
            if (isLdapDN) {
                UILibrary.editLdapDN(writeBackFunc,{currentDN: value});
            } else {
                UILibrary.stringEditorDialog({
                    title:'Edit Value - ' + settingData['label'],
                    textarea:('TEXT_AREA' === settingData['syntax']),
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

var TextAreaValueHandler = {};
TextAreaValueHandler.init = function(settingKey) {
    StringValueHandler.init(settingKey);
};

TextAreaValueHandler.init2 = function(settingKey) {
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    parentDivElement.innerHTML = '<textarea style="max-height:300px; overflow-y: auto" id="value_' + settingKey + '" name="setting_' + settingKey + '">&nbsp;</textarea>';

    PWM_MAIN.clearDijitWidget("value_" + settingKey);
    require(["dijit/form/Textarea"],function(Textarea){
        new Textarea({
            regExp: PWM_SETTINGS['settings'][settingKey]['pattern'],
            required: PWM_SETTINGS['settings'][settingKey]['required'],
            invalidMessage: PWM_CONFIG.showString('Warning_InvalidFormat'),
            style: "width: 550px; max-width:550px; max-height:300px; overflow:auto; white-space: nowrap",
            onChange: function() {
                PWM_CFGEDIT.writeSetting(settingKey, this.value);
            },
            placeholder: PWM_SETTINGS['settings'][settingKey]['placeholder'],
            value: PWM_MAIN.showString('Display_PleaseWait'),
            disabled: true,
            id: "value_" + settingKey
        }, "value_" + settingKey);
        PWM_CFGEDIT.readInitialTextBasedValue(settingKey);
    });
};

// -------------------------- select value handler ------------------------------------

var SelectValueHandler = {};
SelectValueHandler.init = function(settingKey) {
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    var allowUserInput = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Select_AllowUserInput');

    var htmlBody = '<select id="setting_' + settingKey + '" disabled="true">'
        + '<option value="' + PWM_MAIN.showString('Display_PleaseWait') + '">' + PWM_MAIN.showString('Display_PleaseWait') + '</option></select>';

    if (allowUserInput) {
        htmlBody += '<button class="btn" id="button_selectOverride_' + settingKey + '">'
            + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value</button>';

    }

    parentDivElement.innerHTML = htmlBody;

    PWM_MAIN.addEventHandler('setting_' + settingKey,'change',function(){
        var settingElement = PWM_MAIN.getObject('setting_' + settingKey);
        var changeFunction = function(){
            var selectedValue = settingElement.options[settingElement.selectedIndex].value;
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
        var changeFunction = function(value){
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
        var settingElement = PWM_MAIN.getObject('setting_' + settingKey);

        var optionsHtml = '';
        var options = PWM_SETTINGS['settings'][settingKey]['options'];

        if (dataValue && dataValue.length > 0 && !(dataValue in options)) {
            optionsHtml += '<option value="' + dataValue + '">' + dataValue + '</option>'
        }
        for (var option in options) {
            var optionValue = options[option];
            optionsHtml += '<option value="' + option + '">' + optionValue + '</option>'
        }
        settingElement.innerHTML = optionsHtml;
        settingElement.value = dataValue;
        settingElement.disabled = false;
        settingElement.setAttribute('previousValue',settingElement.selectedIndex);
    });
};


// -------------------------- x509 setting handler ------------------------------------

var X509CertificateHandler = {};

X509CertificateHandler.init = function(keyName) {
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        X509CertificateHandler.draw(keyName);
    });
};

X509CertificateHandler.certificateToHtml = function(certificate, keyName, id) {
    var htmlBody = '';
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
            showClose: true
        });
    });
};

X509CertificateHandler.draw = function(keyName) {
    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var resultValue = PWM_VAR['clientSettingCache'][keyName];

    var htmlBody = '<div style="max-height: 300px; overflow-y: auto">';
    for (var certCounter in resultValue) {
        (function (counter) {
            var certificate = resultValue[counter];
            htmlBody += X509CertificateHandler.certificateToHtml(certificate, keyName, counter);
        })(certCounter);
    }
    htmlBody += '</div>';

    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        htmlBody += '<button id="' + keyName + '_ClearButton" class="btn"><span class="btn-icon pwm-icon pwm-icon-times"></span>Clear</button>'
    }
    htmlBody += '<button id="' + keyName + '_AutoImportButton" class="btn"><span class="btn-icon pwm-icon pwm-icon-download"></span>Import From Server</button>'
    parentDivElement.innerHTML = htmlBody;

    for (certCounter in resultValue) {
        (function (counter) {
            var certificate = resultValue[counter];
            X509CertificateHandler.certHtmlActions(certificate, keyName, counter)
        })(certCounter);
    }

    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        PWM_MAIN.addEventHandler(keyName + '_ClearButton','click',function(){
            handleResetClick(keyName);
        });
    }
    var importClassname = PWM_SETTINGS['settings'][keyName]['properties']['Cert_ImportHandler'];
    PWM_MAIN.addEventHandler(keyName + '_AutoImportButton','click',function(){
        PWM_CFGEDIT.executeSettingFunction(keyName,importClassname);
    });
};


// -------------------------- verification method handler ------------------------------------

var VerificationMethodHandler = {};
VerificationMethodHandler.init = function(settingKey) {
    PWM_CFGEDIT.readSetting(settingKey, function(resultValue) {
        PWM_VAR['clientSettingCache'][settingKey] = resultValue;
        VerificationMethodHandler.draw(settingKey);
    });
};

VerificationMethodHandler.draw = function(settingKey) {
    var settingOptions = PWM_SETTINGS['settings'][settingKey]['options'];
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var showMinOptional = !PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Verification_HideMinimumOptional');

    var htmlBody = '<table class="">';
    for (var method in settingOptions) {
        var id = settingKey + '-' + method;
        var label = settingOptions[method];
        var title = PWM_CONFIG.showString('VerificationMethodDetail_' + method);
        htmlBody += '<tr><td title="' + title + '"><span style="cursor:pointer")">' + label + '</span></td><td><input id="input-range-' + id + '" type="range" min="0" max="2" value="0"/></td>';
        htmlBody += '<td><span id="label-' + id +'"></span></td></tr>';
    }
    htmlBody += '</table>';

    if (showMinOptional) {
        htmlBody += '<br/><label>Minimum Optional Required <input min="0" style="width:30px;" id="input-minOptional-' + settingKey + '" type="number" value="0" class="configNumericInput""></label>';
    }
    parentDivElement.innerHTML = htmlBody;
    for (var method in settingOptions) {
        var id = settingKey + '-' + method;
        PWM_MAIN.addEventHandler('input-range-' + id,'change',function(){
            VerificationMethodHandler.updateLabels(settingKey);
            VerificationMethodHandler.write(settingKey);
        });

        var enabledState = PWM_VAR['clientSettingCache'][settingKey]['methodSettings'][method]
            && PWM_VAR['clientSettingCache'][settingKey]['methodSettings'][method]['enabledState'];

        var numberValue = 0;
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
    var showMinOptional = !PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Verification_HideMinimumOptional');

    var settingOptions = PWM_SETTINGS['settings'][settingKey]['options'];
    var values = {};
    values['minOptionalRequired'] = showMinOptional ? Number(PWM_MAIN.getObject('input-minOptional-' + settingKey).value) : 0;
    values['methodSettings'] = {};
    for (var method in settingOptions) {
        var id = settingKey + '-' + method;
        var value = Number(PWM_MAIN.getObject('input-range-' + id).value);

        var enabledState = 'disabled';
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
    var settingOptions = PWM_SETTINGS['settings'][settingKey]['options'];
    var optionalCount = 0;
    for (var method in settingOptions) {
        var id = settingKey + '-' + method;
        var value = Number(PWM_MAIN.getObject('input-range-' + id).value);
        var label = '';
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
    var showMinOptional = !PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][settingKey]['flags'],'Verification_HideMinimumOptional');
    if (showMinOptional) {
        var minOptionalInput = PWM_MAIN.getObject('input-minOptional-' + settingKey);
        minOptionalInput.max = optionalCount;
        var currentMax = Number(minOptionalInput.value);
        if (currentMax > optionalCount) {
            minOptionalInput.value = optionalCount.toString();
        }
    }
};


// -------------------------- file setting handler ------------------------------------

var FileValueHandler = {};

FileValueHandler.init = function(keyName) {
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        FileValueHandler.draw(keyName);
    });
};

FileValueHandler.draw = function(keyName) {
    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var resultValue = PWM_VAR['clientSettingCache'][keyName];

    var htmlBody = '';

    if (PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        htmlBody = '<p>No File Present</p>';
    } else {
        for (var fileCounter in resultValue) {
            (function (counter) {
                var fileInfo = resultValue[counter];
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
    var options = {};
    options['url'] = "editor?processAction=uploadFile&key=" + keyName;
    options['nextFunction'] = function() {
        PWM_MAIN.showWaitDialog({loadFunction:function(){
                FileValueHandler.init(keyName);
                PWM_MAIN.closeWaitDialog();
            }});
    };
    UILibrary.uploadFileDialog(options);
};


// -------------------------- x509 setting handler ------------------------------------

var PrivateKeyHandler = {};

PrivateKeyHandler.init = function(keyName) {
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        PrivateKeyHandler.draw(keyName);
    });
};

PrivateKeyHandler.draw = function(keyName) {
    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var resultValue = PWM_VAR['clientSettingCache'][keyName];

    var htmlBody = '<div style="max-height: 300px; overflow-y: auto">';

    var hasValue = resultValue !== undefined && 'key' in resultValue;

    if (hasValue) {
        var certificates = resultValue['certificates'];
        for (var certCounter in certificates) {
            (function (counter) {
                var certificate = certificates[counter];
                htmlBody += X509CertificateHandler.certificateToHtml(certificate, keyName, counter);
            })(certCounter);
        }
        htmlBody += '</div>';

        var key = resultValue['key'];
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
        var certificates = resultValue['certificates'];
        for (var certCounter in certificates) {
            (function (counter) {
                var certificate = certificates[counter];
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
        var options = {};
        var url = PWM_MAIN.addParamToUrl(window.location.href,'processAction','uploadFile');
        url = PWM_MAIN.addParamToUrl(url, 'key', keyName);
        options['url'] = url;

        var text = '<form autocomplete="off"><table class="noborder">';
        text += '<tr><td class="key">File Format</td><td><select id="input-certificateUpload-format"><option value="PKCS12">PKCS12 / PFX</option><option value="JKS">Java Keystore (JKS)</option></select></td></tr>';
        text += '<tr><td class="key">Password</td><td><input type="password" class="configInput" id="input-certificateUpload-password"/></td></tr>';
        text += '<tr><td class="key">Alias</td><td><input type="text" class="configInput" id="input-certificateUpload-alias"/><br/><span class="footnote">Alias only required if file has multiple aliases</span></td></tr>';
        text += '</table></form>';
        options['text'] = text;

        var urlUpdateFunction = function(url) {
            var formatSelect = PWM_MAIN.getObject('input-certificateUpload-format');
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
var NamedSecretHandler = {};

NamedSecretHandler.init = function(settingKey) {
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    if (parentDivElement) {
        PWM_CFGEDIT.readSetting(settingKey,function(data){
            PWM_VAR['clientSettingCache'][settingKey] = data;
            var htmlBody = '';
            htmlBody += '<table>';
            var rowCounter = 0;
            for (var key in data) {
                var id = settingKey + '_' + key;
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

            for (var key in data) {
                (function (loopKey) {
                    var id = settingKey + '_' + loopKey;
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
    var titleText = PWM_SETTINGS['settings'][settingKey]['label'] + ' - Usage - ' + key ;
    var options = PWM_SETTINGS['settings'][settingKey]['options'];
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    var html = '<table class="noborder">';
    for (var loopKey in options) {
        (function (optionKey) {
            html += '<tr><td>';
            var buttonID = key + "_usage_button_" + optionKey;
            html += '<label class="checkboxWrapper" style="min-width:180px;">'
                + '<input type="checkbox" id="' + buttonID + '"/>'
                + options[optionKey] + '</label>';
            html += '</td></tr>';
        })(loopKey);
    }
    html += '</table>';
    var loadFunction = function () {
        for (var loopKey in options) {
            (function (optionKey) {
                var buttonID = key + "_usage_button_" + optionKey;
                var checked = PWM_MAIN.JSLibrary.arrayContains(currentValues[key]['usage'],optionKey);
                PWM_MAIN.getObject(buttonID).checked = checked;
                PWM_MAIN.addEventHandler(buttonID,'click',function(){
                    var nowChecked = PWM_MAIN.getObject(buttonID).checked;
                    if (nowChecked) {
                        currentValues[key]['usage'].push(optionKey);
                    } else {
                        PWM_MAIN.JSLibrary.removeFromArray(currentValues[key]['usage'], optionKey);
                    }
                });
            })(loopKey);
        }
    };
    var okFunction = function() {
        var postWriteFunction = function() {
            NamedSecretHandler.init(settingKey);
        };
        PWM_CFGEDIT.writeSetting(settingKey, currentValues, postWriteFunction);
    };
    PWM_MAIN.showDialog({title:titleText,text:html,loadFunction:loadFunction,okAction:okFunction});
};

NamedSecretHandler.addPassword = function(settingKey) {
    var titleText = PWM_SETTINGS['settings'][settingKey]['label'] + ' - Name';
    var stringEditorFinishFunc = function(nameValue) {
        var currentValues = PWM_VAR['clientSettingCache'][settingKey];
        if (nameValue in currentValues) {;
            var errorTxt = '"' + nameValue + '" already exists.';
            PWM_MAIN.showErrorDialog(errorTxt);
            return;
        }
        var pwDialogOptions = {};
        pwDialogOptions['title'] = PWM_SETTINGS['settings'][settingKey]['label'] + ' - Password';
        pwDialogOptions['showRandomGenerator'] = true;
        pwDialogOptions['showValues'] = true;
        pwDialogOptions['writeFunction'] = function(pwValue) {
            currentValues[nameValue] = {};
            currentValues[nameValue]['password'] = pwValue;
            currentValues[nameValue]['usage'] = [];

            var postWriteFunction = function() {
                NamedSecretHandler.init(settingKey);
            };

            PWM_CFGEDIT.writeSetting(settingKey, currentValues, postWriteFunction);
        };
        UILibrary.passwordDialogPopup(pwDialogOptions)
    };
    var instructions = 'Please enter the name for the new password.';
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
            var currentValues = PWM_VAR['clientSettingCache'][settingKey];
            delete currentValues[key];

            var postWriteFunction = function() {
                NamedSecretHandler.init(settingKey);
            };
            PWM_CFGEDIT.writeSetting(settingKey, currentValues, postWriteFunction);
        }
    });

};


