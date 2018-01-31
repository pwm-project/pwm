/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
                        PWM_MAIN.goto('editor');
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
            PWM_MAIN.goto('editor');
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

// -------------------------- form table handler ------------------------------------

var FormTableHandler = {};
FormTableHandler.newRowValue = {
    name:'',
    minimumLength:0,
    maximumLength:255,
    labels:{'':''},
    regexErrors:{'':''},
    selectOptions:{},
    description:{'':''}
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
    require(["dojo/json"], function(JSON){
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
    var options = 'options' in PWM_SETTINGS['settings'][keyName] ? PWM_SETTINGS['settings'][keyName]['options'] : {};
    var currentValue = PWM_VAR['clientSettingCache'][keyName][iteration];

    var hideStandardOptions = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_HideStandardOptions');
    var showRequired = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowRequiredOption') && (type !== 'checkbox');
    var showUnique = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowUniqueOption');
    var showReadOnly = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowReadOnlyOption');
    var showMultiValue = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowMultiValueOption');
    var showConfirmation = type !== 'checkbox' && type !== 'select' && !hideStandardOptions;
    var showSource = PWM_MAIN.JSLibrary.arrayContains(settings['flags'],'Form_ShowSource');


    var inputID = 'value_' + keyName + '_' + iteration + "_";
    var bodyText = '<div style="max-height: 500px; overflow-y: auto"><table class="noborder">';
    if (!hideStandardOptions) {
        bodyText += '<tr>';
        var descriptionValue = currentValue['description'][''];
        bodyText += '<td id="' + inputID + '-label-description" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Description') + '">Description</td><td>';
        bodyText += '<div class="noWrapTextBox" id="' + inputID + 'description"><span class="btn-icon pwm-icon pwm-icon-edit"></span><span>' + descriptionValue + '...</span></div>';
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
        bodyText += '<td id="' + inputID + '-label-js" class="key" title="' + PWM_CONFIG.showString('Tooltip_FormOptions_Javascript') + '">JavaScript</td><td><input type="text" class="configStringInput" style="width:300px" id="' + inputID + 'javascript' + '"/></td>';
        bodyText += '</tr><tr>';
        if (currentValue['type'] === 'select') {
            bodyText += '<td class="key">Select Options</td><td><button id="' + inputID + 'editOptionsButton"><span class="btn-icon pwm-icon pwm-icon-list-ul"/> Edit</button></td>';
            bodyText += '</tr>';
        }
    }

    if (showSource) {
        bodyText += '<td id="' + inputID + '-label-source" class="key">Data Source</td>'
            +  '<td><select id="' + inputID + 'source' + '">'
            + '<option value="ldap">LDAP</option>'
            + '<option value="remote">Remote REST API</option>'
            + '</select></td></tr><tr>';
    }

    bodyText += '</table></div>';

    var initFormElements = function() {
        var currentValue = PWM_VAR['clientSettingCache'][keyName][iteration];

        PWM_MAIN.addEventHandler(inputID + 'editOptionsButton', 'click', function(){
            FormTableHandler.showSelectOptionsDialog(keyName,iteration);
        });

        PWM_MAIN.addEventHandler(inputID + 'description','click',function(){
            FormTableHandler.showDescriptionDialog(keyName, iteration);
        });

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

            PWM_MAIN.getObject(inputID + "regex").value = currentValue['regex'];
            PWM_MAIN.addEventHandler(inputID + "regex", "change", function(){
                currentValue['regex'] = PWM_MAIN.getObject(inputID + "regex").value;
                FormTableHandler.write(keyName)
            });

            PWM_MAIN.addEventHandler(inputID + 'regexErrors', 'click', function () {
                FormTableHandler.showRegexErrorsDialog(keyName, iteration);
            });

            PWM_MAIN.getObject(inputID + "placeholder").value = currentValue['placeholder'];
            PWM_MAIN.addEventHandler(inputID + "placeholder", "change", function(){
                currentValue['placeholder'] = PWM_MAIN.getObject(inputID + "placeholder").value;
                FormTableHandler.write(keyName)
            });

            PWM_MAIN.getObject(inputID + "javascript").value = currentValue['javascript'];
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
        var value = PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions'][optionName];
        var optionID = inputID + optionName;
        bodyText += '<td>' + optionName + '</td><td>' + value + '</td>';
        bodyText += '<td class="noborder" style="width:15px">';
        bodyText += '<span id="' + optionID + '-removeButton" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></span>';
        bodyText += '</td>';
        bodyText += '</tr><tr>';
    }
    bodyText += '</tr></table>';
    bodyText += '<br/><br/><br/>';
    bodyText += '<input class="configStringInput" style="width:200px" type="text" placeholder="Value" required id="addSelectOptionName"/>';
    bodyText += '<input class="configStringInput" style="width:200px" type="text" placeholder="Display Name" required id="addSelectOptionValue"/>';
    bodyText += '<button id="addSelectOptionButton"><span class="btn-icon pwm-icon pwm-icon-plus-square"/> Add</button>';

    PWM_MAIN.showDialog({
        title: 'Select Options for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
        text: bodyText,
        okAction: function(){
            FormTableHandler.showOptionsDialog(keyName,iteration);
        }
    });

    for (var optionName in PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions']) {
        var loopID = inputID + optionName;
        var optionID = inputID + optionName;
        PWM_MAIN.clearDijitWidget(loopID);
        PWM_MAIN.addEventHandler(optionID + '-removeButton','click',function(){
            FormTableHandler.removeSelectOptionsOption(keyName,iteration,optionName);
        });
    }

    PWM_MAIN.addEventHandler('addSelectOptionButton','click',function(){
        var value = PWM_MAIN.getObject('addSelectOptionName').value;
        var display = PWM_MAIN.getObject('addSelectOptionValue').value;
        FormTableHandler.addSelectOptionsOption(keyName, iteration, value, display);
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



// -------------------------- action handler ------------------------------------

var ActionHandler = {};
ActionHandler.defaultValue = {
    name:"",
    description:"",
    type:"webservice",
    method:"get",
    ldapMethod:"replace",
    url:"",
    body:"",
    headers:{},
    attributeName:"",
    attributeValue:""
};
ActionHandler.httpMethodOptions = [
    { label: "Delete", value: "delete" },
    { label: "Get", value: "get" },
    { label: "Post", value: "post" },
    { label: "Put", value: "put" },
    { label: "Patch", value: "patch" }
];
ActionHandler.ldapMethodOptions = [
    { label: "Replace (Remove all existing values)", value: "replace" },
    { label: "Add (Append new value)", value: "add" },
    { label: "Remove (Remove specified value)", value: "remove" }
];

ActionHandler.init = function(keyName) {
    console.log('ActionHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        ActionHandler.redraw(keyName);
    });
};

ActionHandler.redraw = function(keyName) {
    console.log('ActionHandler redraw for ' + keyName);
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var html = '';
    if (!PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        html += '<table class="noborder">';
        html += '<tr><td>Name</td><td></td><td>Description</td></tr>';

        for (var i in resultValue) {
            html += ActionHandler.drawRow(keyName, i, resultValue[i]);
        }

        html += '</table>';
    }

    html += '<br/><button class="btn" id="button-' + keyName + '-addValue"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Action</button>';
    parentDivElement.innerHTML = html;

    for (var i in resultValue) {
        html += ActionHandler.addRowHandlers(keyName, i, resultValue[i]);
    }

    PWM_MAIN.addEventHandler('button-' + keyName + '-addValue','click',function(){
        ActionHandler.addRow(keyName);
    });
};

ActionHandler.drawRow = function(settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";
    var optionList = PWM_GLOBAL['actionTypeOptions'];

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    var htmlRow = '<tr>';
    htmlRow += '<td class="border">';
    htmlRow += '<div class="noWrapTextBox" style="width:160px" id="display-' + inputID + '-name" ></div>';
    htmlRow += '<td style="width:1px" id="icon-editDescription-' + inputID + '"><span class="btn-icon pwm-icon pwm-icon-edit"></span></td>';
    htmlRow += '</td><td class="border" style="width:160px" >';
    htmlRow += '<div class="noWrapTextBox" id="display-' + inputID + '-description"></div>';
    htmlRow += '</td><td>';
    htmlRow += '<select id="select-' + inputID + '-type">';
    for (var optionItem in optionList) {
        var selected = optionList[optionItem] === PWM_VAR['clientSettingCache'][settingKey][iteration]['type'];
        htmlRow += '<option value="' + optionList[optionItem] + '"' + (selected ? ' selected' : '') + '>' + optionList[optionItem] + '</option>';
    }
    htmlRow += '</select>';
    htmlRow += '</td><td>';
    htmlRow += '<button id="button-' + inputID + '-options"><span class="btn-icon pwm-icon pwm-icon-sliders"/> Options</button>';
    htmlRow += '</td>';
    htmlRow += '<td><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-' + inputID + '-deleteRow"></span></td>';
    htmlRow += '</tr>';
    return htmlRow;
};

ActionHandler.addRowHandlers = function(settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";
    UILibrary.addTextValueToElement('display-' + inputID + '-name',value['name']);
    UILibrary.addTextValueToElement('display-' + inputID + '-description',value['description']);
    PWM_MAIN.addEventHandler('button-' + inputID + '-options','click',function(){
        ActionHandler.showOptionsDialog(settingKey, iteration);
    });
    var descriptionEditFunction = function() {
        UILibrary.stringEditorDialog({
            value: value['description'],
            textarea: true,
            completeFunction: function (newValue) {
                PWM_VAR['clientSettingCache'][settingKey][iteration]['description'] = newValue;
                ActionHandler.write(settingKey,function(){
                    ActionHandler.init(settingKey);
                });
            }
        });
    };

    PWM_MAIN.addEventHandler('icon-editDescription-' + inputID,'click',function(){
        descriptionEditFunction();
    });
    PWM_MAIN.addEventHandler('display-' + inputID + '-description','click',function(){
        descriptionEditFunction();
    });

    PWM_MAIN.addEventHandler('select-' + inputID + '-type','change',function(){
        PWM_VAR['clientSettingCache'][settingKey][iteration]['type'] = PWM_MAIN.getObject('select-' + inputID + '-type').value;
        ActionHandler.write(settingKey);
    });
    PWM_MAIN.addEventHandler('button-' + inputID + '-deleteRow','click',function(){
        ActionHandler.removeRow(settingKey, iteration);
    });
};

ActionHandler.write = function(settingKey, finishFunction) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

ActionHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            delete PWM_VAR['clientSettingCache'][keyName][iteration];
            console.log("removed iteration " + iteration + " from " + keyName + ", cached keyValue=" + PWM_VAR['clientSettingCache'][keyName]);
            ActionHandler.write(keyName,function(){
                ActionHandler.init(keyName);
            });
        }
    })
};

ActionHandler.addRow = function(keyName) {
    UILibrary.stringEditorDialog({
        title:'New Action',
        regex:'^[0-9a-zA-Z]+$',
        instructions: 'Please enter a descriptive name for the action.',
        placeholder:'Name',
        completeFunction:function(value){
            var currentSize = PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][keyName]);
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1] = ActionHandler.defaultValue;
            PWM_VAR['clientSettingCache'][keyName][currentSize + 1].name = value;
            ActionHandler.write(keyName,function(){
                ActionHandler.init(keyName);
            });

        }
    });
};


ActionHandler.showOptionsDialog = function(keyName, iteration) {
    require(["dojo/store/Memory","dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/Select","dijit/form/ValidationTextBox"],function(Memory){
        var inputID = 'value_' + keyName + '_' + iteration + "_";
        var value = PWM_VAR['clientSettingCache'][keyName][iteration];
        var titleText = 'title';
        var bodyText = '<table class="noborder">';
        if (value['type'] === 'webservice') {
            titleText = 'Web Service options for ' + value['name'];
            bodyText += '<tr>';
            bodyText += '<td class="key">HTTP Method</td><td class="noborder" ><select id="select-' + inputID + '-method">';

            for (var optionItem in ActionHandler.httpMethodOptions) {
                var label = ActionHandler.httpMethodOptions[optionItem]['label'];
                var optionValue = ActionHandler.httpMethodOptions[optionItem]['value'];
                var selected = optionValue === value['method'];
                bodyText += '<option value="' + optionValue + '"' + (selected ? ' selected' : '') + '>' + label + '</option>';
            }
            bodyText += '</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td class="key">HTTP Headers</td><td><button id="button-' + inputID + '-headers"><span class="btn-icon pwm-icon pwm-icon-list-ul"/> Edit</button></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td class="key">URL</td><td><input type="text" class="configstringinput" style="width:400px" placeholder="http://www.example.com/service"  id="input-' + inputID + '-url' + '" value="' + value['url'] + '"/></td>';
            bodyText += '</tr>';
            if (value['method'] !== 'get') {
                bodyText += '<tr><td class="key">Body</td><td><textarea style="max-width:400px; height:100px; max-height:100px" class="configStringInput" id="input-' + inputID + '-body' + '"/>' + value['body'] + '</textarea></td></tr>';
            }
            if (!PWM_MAIN.JSLibrary.isEmpty(value['certificateInfos'])) {
                bodyText += '<tr><td class="key">Certificates</td><td><a id="button-' + inputID + '-certDetail">View Certificates</a></td>';
                bodyText += '</tr>';
            } else {
                bodyText += '<tr><td class="key">Certificates</td><td>None</td>';
                bodyText += '</tr>';
            }
            bodyText += '';
        } else if (value['type'] === 'ldap') {
            titleText = 'LDAP options for ' + value['name'];
            bodyText += '<tr>';
            bodyText += '<td class="key">Attribute Name</td><td><input style="width:300px" class="configStringInput" type="text" id="input-' + inputID + '-attributeName' + '" value="' + value['attributeName'] + '"/></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td class="key">Attribute Value</td><td><input style="width:300px" class="configStringInput" type="text" id="input-' + inputID + '-attributeValue' + '" value="' + value['attributeValue'] + '"/></td>';
            bodyText += '</tr>';
            bodyText += '<tr>';
            bodyText += '<td class="key">Operation Type</td><td class="noborder"><select id="select-' + inputID + '-ldapMethod' + '">';

            for (var optionItem in ActionHandler.ldapMethodOptions) {
                var label = ActionHandler.ldapMethodOptions[optionItem]['label'];
                var optionValue = ActionHandler.ldapMethodOptions[optionItem]['value'];
                var selected = optionValue === value['ldapMethod'];
                bodyText += '<option value="' + optionValue + '"' + (selected ? ' selected' : '') + '>' + label + '</option>';
            }
            bodyText += '</td></tr>';
        }
        bodyText += '</table>';

        if (value['type'] === 'webservice') {
            if (!PWM_MAIN.JSLibrary.isEmpty(value['certificateInfos'])) {
                bodyText += '<button class="btn" id="button-' + inputID + '-clearCertificates"><span class="btn-icon pwm-icon pwm-icon-trash"></span>Clear Certificates</button>'
            } else {
                bodyText += '<button class="btn" id="button-' + inputID + '-importCertificates"><span class="btn-icon pwm-icon pwm-icon-download"></span>Import From Server</button>'
            }
        }

        PWM_MAIN.showDialog({
            title: titleText,
            text: bodyText,
            loadFunction: function(){
                PWM_MAIN.addEventHandler('button-' + inputID + '-headers','click',function(){
                    ActionHandler.showHeadersDialog(keyName,iteration);
                });
                if (value['type'] === 'webservice') {
                    PWM_MAIN.addEventHandler('select-' + inputID + '-method','change',function(){
                        var methodValue = PWM_MAIN.getObject('select-' + inputID + '-method').value;
                        if (methodValue === 'get') {
                            value['body'] = '';
                        }
                        value['method'] = methodValue;
                        ActionHandler.write(keyName, function(){ ActionHandler.showOptionsDialog(keyName,iteration)});
                    });
                    PWM_MAIN.addEventHandler('input-' + inputID + '-url','input',function(){
                        value['url'] = PWM_MAIN.getObject('input-' + inputID + '-url').value;
                        ActionHandler.write(keyName);
                    });
                    PWM_MAIN.addEventHandler('input-' + inputID + '-body','input',function(){
                        value['body'] = PWM_MAIN.getObject('input-' + inputID + '-body').value;
                        ActionHandler.write(keyName);
                    });
                    if (!PWM_MAIN.JSLibrary.isEmpty(value['certificateInfos'])) {
                        PWM_MAIN.addEventHandler('button-' + inputID + '-certDetail','click',function(){
                            var bodyText = '';
                            for (var i in value['certificateInfos']) {
                                var certificate = value['certificateInfos'][i];
                                bodyText += X509CertificateHandler.certificateToHtml(certificate,keyName,i);
                            }
                            var cancelFunction = function(){ ActionHandler.showOptionsDialog(keyName,iteration); };
                            var loadFunction = function(){
                                for (var i in value['certificateInfos']) {
                                    var certificate = value['certificateInfos'][i];
                                    X509CertificateHandler.certHtmlActions(certificate,keyName,i);
                                }
                            };
                            PWM_MAIN.showDialog({
                                title:'Certificate Detail',
                                dialogClass: 'wide',
                                text:bodyText,
                                okAction:cancelFunction,
                                loadFunction:loadFunction
                            });
                        });
                        PWM_MAIN.addEventHandler('button-' + inputID + '-clearCertificates','click',function() {
                            PWM_MAIN.showConfirmDialog({okAction:function(){
                                delete value['certificates'];
                                delete value['certificateInfos'];
                                ActionHandler.write(keyName, function(){ ActionHandler.showOptionsDialog(keyName,iteration)});
                            },cancelAction:function(){
                                ActionHandler.showOptionsDialog(keyName,iteration);
                            }});
                        });
                    } else {
                        PWM_MAIN.addEventHandler('button-' + inputID + '-importCertificates','click',function() {
                            var dataHandler = function(data) {
                                var msgBody = '<div style="max-height: 400px; overflow-y: auto">' + data['successMessage'] + '</div>';
                                PWM_MAIN.showDialog({width:700,title: 'Results', text: msgBody, okAction: function () {
                                    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
                                        PWM_VAR['clientSettingCache'][keyName] = resultValue;
                                        ActionHandler.showOptionsDialog(keyName, iteration);
                                    });
                                }});
                            };
                            PWM_CFGEDIT.executeSettingFunction(keyName, 'password.pwm.config.function.ActionCertImportFunction', dataHandler, value['name'])
                        });
                    }

                } else if (value['type'] === 'ldap') {
                    PWM_MAIN.addEventHandler('input-' + inputID + '-attributeName','input',function(){
                        value['attributeName'] = PWM_MAIN.getObject('input-' + inputID + '-attributeName').value;
                        ActionHandler.write(keyName);
                    });
                    PWM_MAIN.addEventHandler('input-' + inputID + '-attributeValue','input',function(){
                        value['attributeValue'] = PWM_MAIN.getObject('input-' + inputID + '-attributeValue').value;
                        ActionHandler.write(keyName);
                    });
                    PWM_MAIN.addEventHandler('select-' + inputID + '-ldapMethod','change',function(){
                        value['ldapMethod'] = PWM_MAIN.getObject('select-' + inputID + '-ldapMethod').value;
                        ActionHandler.write(keyName);
                    });
                }
            }
        });
    });
};

ActionHandler.showHeadersDialog = function(keyName, iteration) {
    var settingValue = PWM_VAR['clientSettingCache'][keyName][iteration];
    require(["dijit/Dialog","dijit/form/ValidationTextBox","dijit/form/Button","dijit/form/TextBox"],function(Dialog,ValidationTextBox,Button,TextBox){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "headers_";

        var bodyText = '';
        bodyText += '<table class="noborder">';
        bodyText += '<tr><td><b>Name</b></td><td><b>Value</b></td></tr>';
        for (var iter in settingValue['headers']) {
            (function(headerName) {
                var value = settingValue['headers'][headerName];
                var optionID = inputID + headerName;
                bodyText += '<tr><td class="border">' + headerName + '</td><td class="border">' + value + '</td>';
                bodyText += '<td style="width:15px;"><span class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-' + optionID + '-deleteRow"></span></td>';
                bodyText += '</tr>';
            }(iter));
        }
        bodyText += '</table>';

        PWM_MAIN.showDialog({
            title: 'HTTP Headers for webservice ' + settingValue['name'],
            text: bodyText,
            buttonHtml:'<button id="button-' + inputID + '-addHeader" class="btn"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Header</button>',
            okAction: function() {
                ActionHandler.showOptionsDialog(keyName,iteration);
            },
            loadFunction: function() {
                for (var iter in settingValue['headers']) {
                    (function(headerName) {
                        var headerID = inputID + headerName;
                        PWM_MAIN.addEventHandler('button-' + headerID + '-deleteRow', 'click', function () {
                            delete settingValue['headers'][headerName];
                            ActionHandler.write(keyName);
                            ActionHandler.showHeadersDialog(keyName, iteration);
                        });
                    }(iter));
                }
                PWM_MAIN.addEventHandler('button-' + inputID + '-addHeader','click',function(){
                    ActionHandler.addHeader(keyName, iteration);
                });
            }
        });
    });
};

ActionHandler.addHeader = function(keyName, iteration) {
    var body = '<table class="noborder">';
    body += '<tr><td>Name</td><td><input class="configStringInput" id="newHeaderName" style="width:300px"/></td></tr>';
    body += '<tr><td>Value</td><td><input class="configStringInput" id="newHeaderValue" style="width:300px"/></td></tr>';
    body += '</table>';

    var updateFunction = function(){
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        PWM_VAR['newHeaderName'] = PWM_MAIN.getObject('newHeaderName').value;
        PWM_VAR['newHeaderValue'] = PWM_MAIN.getObject('newHeaderValue').value;
        if (PWM_VAR['newHeaderName'].length > 0 && PWM_VAR['newHeaderValue'].length > 0) {
            PWM_MAIN.getObject('dialog_ok_button').disabled = false;
        }
    };

    PWM_MAIN.showConfirmDialog({
        title:'New Header',
        text:body,
        showClose:true,
        loadFunction:function(){
            PWM_MAIN.addEventHandler('newHeaderName','input',function(){
                updateFunction();
            });
            PWM_MAIN.addEventHandler('newHeaderValue','input',function(){
                updateFunction();
            });
            updateFunction();
        },okAction:function(){
            var headers = PWM_VAR['clientSettingCache'][keyName][iteration]['headers'];
            headers[PWM_VAR['newHeaderName']] = PWM_VAR['newHeaderValue'];
            ActionHandler.write(keyName);
            ActionHandler.showHeadersDialog(keyName, iteration);
        },cancelAction:function(){
            ActionHandler.showHeadersDialog(keyName, iteration);
        }
    });

};


// -------------------------- email table handler ------------------------------------

var EmailTableHandler = {};
EmailTableHandler.defaultValue = {
    to:"@User:Email@",
    from:"@DefaultEmailFromAddress@",
    subject:"Subject",
    bodyPlain:"Body",
    bodyHtml:"Body"
};

EmailTableHandler.init = function(keyName) {
    console.log('EmailTableHandler init for ' + keyName);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        EmailTableHandler.draw(keyName);
    });
};

EmailTableHandler.draw = function(settingKey) {
    var resultValue = PWM_VAR['clientSettingCache'][settingKey];
    var parentDiv = 'table_setting_' + settingKey;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.clearDivElements(parentDiv, false);

    var htmlBody = '';
    for (var localeName in resultValue) {
        htmlBody += EmailTableHandler.drawRowHtml(settingKey,localeName)
    }
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.innerHTML = htmlBody;

    for (var localeName in resultValue) {
        EmailTableHandler.instrumentRow(settingKey,localeName)
    }

    if (PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        var htmlBody = '<button class="btn" id="button-addValue-' + settingKey + '">';
        htmlBody += '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value';
        htmlBody += '</button>';

        var parentDivElement = PWM_MAIN.getObject(parentDiv);
        parentDivElement.innerHTML = htmlBody;

        PWM_MAIN.addEventHandler('button-addValue-' + settingKey,'click',function(){
            PWM_CFGEDIT.resetSetting(settingKey,function(){PWM_CFGEDIT.loadMainPageBody()});
        });

    } else {
        var addLocaleFunction = function(localeValue) {
            if (!PWM_VAR['clientSettingCache'][settingKey][localeValue]) {
                PWM_VAR['clientSettingCache'][settingKey][localeValue] = EmailTableHandler.defaultValue;
                EmailTableHandler.writeSetting(settingKey,true);
            }
        };
        UILibrary.addAddLocaleButtonRow(parentDiv, settingKey, addLocaleFunction, Object.keys(PWM_VAR['clientSettingCache'][settingKey]));
    }
};

EmailTableHandler.drawRowHtml = function(settingKey, localeName) {
    var localeLabel = localeName === '' ? 'Default Locale' : PWM_GLOBAL['localeInfo'][localeName] + " (" + localeName + ")";
    var idPrefix = "setting-" + localeName + "-" + settingKey;
    var htmlBody = '';
    htmlBody += '<table class="noborder" style=""><tr ><td class="noborder" style="max-width: 440px">';
    htmlBody += '<table>';
    if (PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1) {
        htmlBody += '<tr><td colspan="5" class="title" style="font-size:100%; font-weight:normal">' + localeLabel + '</td></tr>';
    }
    var outputFunction = function (labelText, typeText) {
        htmlBody += '<tr><td style="text-align:right; border-width:0;">' + labelText + '</td>';
        htmlBody += '<td id="button-' + typeText + '-' + idPrefix + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></ta>';
        htmlBody += '<td style=""><div class="configStringPanel" id="panel-' + typeText + '-' + idPrefix + '"></div></td>';
        htmlBody += '</tr>';
    };
    outputFunction('To', 'to');
    outputFunction('From', 'from');
    outputFunction('Subject', 'subject');
    outputFunction('Plain Body', 'bodyPlain');
    outputFunction('HTML Body', 'bodyHtml');

    htmlBody += '</table></td><td class="noborder" style="width:20px; vertical-align:top">';
    if (localeName !== '' || PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) < 2) { // add remove locale x
        htmlBody += '<div id="button-deleteRow-' + idPrefix + '" style="vertical-align:top" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></div>';
    }
    htmlBody += '</td></tr></table><br/>';
    return htmlBody;
};


EmailTableHandler.instrumentRow = function(settingKey, localeName) {
    var settingData = PWM_SETTINGS['settings'][settingKey];
    var idPrefix = "setting-" + localeName + "-" + settingKey;

    var editor = function(drawTextArea, type, instructions){
        UILibrary.stringEditorDialog({
            title:'Edit Value - ' + settingData['label'],
            instructions: instructions,
            textarea:drawTextArea,
            value:PWM_VAR['clientSettingCache'][settingKey][localeName][type],
            completeFunction:function(value){
                PWM_VAR['clientSettingCache'][settingKey][localeName][type] = value;
                PWM_CFGEDIT.writeSetting(settingKey,PWM_VAR['clientSettingCache'][settingKey],function(){
                    EmailTableHandler.init(settingKey);
                });
            }
        });
    };

    UILibrary.addTextValueToElement('panel-to-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['to']);
    PWM_MAIN.addEventHandler('button-to-' + idPrefix,'click',function(){ editor(false,'to',PWM_CONFIG.showString('Instructions_Edit_Email')); });
    PWM_MAIN.addEventHandler('panel-to-' + idPrefix,'click',function(){ editor(false,'to',PWM_CONFIG.showString('Instructions_Edit_Email')); });

    UILibrary.addTextValueToElement('panel-from-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['from']);
    PWM_MAIN.addEventHandler('button-from-' + idPrefix,'click',function(){ editor(false,'from'); });
    PWM_MAIN.addEventHandler('panel-from-' + idPrefix,'click',function(){ editor(false,'from'); });

    UILibrary.addTextValueToElement('panel-subject-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['subject']);
    PWM_MAIN.addEventHandler('button-subject-' + idPrefix,'click',function(){ editor(false,'subject'); });
    PWM_MAIN.addEventHandler('panel-subject-' + idPrefix,'click',function(){ editor(false,'subject'); });

    UILibrary.addTextValueToElement('panel-bodyPlain-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['bodyPlain']);
    PWM_MAIN.addEventHandler('button-bodyPlain-' + idPrefix,'click',function(){ editor(true,'bodyPlain'); });
    PWM_MAIN.addEventHandler('panel-bodyPlain-' + idPrefix,'click',function(){ editor(true,'bodyPlain'); });

    UILibrary.addTextValueToElement('panel-bodyHtml-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['bodyHtml']);
    PWM_MAIN.addEventHandler('button-bodyHtml-' + idPrefix,'click',function(){ EmailTableHandler.htmlBodyEditor(settingKey,localeName); });
    PWM_MAIN.addEventHandler('panel-bodyHtml-' + idPrefix,'click',function(){ EmailTableHandler.htmlBodyEditor(settingKey,localeName); });

    PWM_MAIN.addEventHandler("button-deleteRow-" + idPrefix,"click",function(){
        PWM_MAIN.showConfirmDialog({okAction:function(){
            delete PWM_VAR['clientSettingCache'][settingKey][localeName];
            EmailTableHandler.writeSetting(settingKey,true);
        }});
    });
};


EmailTableHandler.htmlBodyEditor = function(keyName, localeName) {
    // Grab the scope from the angular controller we created on the div element with ID: centerbody-config
    var $scope = angular.element(document.getElementById("centerbody-config")).scope();
    var idValue = keyName + "_" + localeName + "_htmlEditor";
    var toolbarButtons =
        "[" +
        "['h1','h2','h3','h4','h5','h6','p','pre','quote']," +
        "['bold','italics','underline','strikeThrough','ul','ol','undo','redo','clear']," +
        "['justifyLeft','justifyCenter','justifyRight','justifyFull','indent','outdent']," +
        "['html','insertImage','insertLink','insertVideo']" +
        "]";

    PWM_MAIN.showDialog({
        title: "HTML Editor",
        text: '<div id="' + idValue + '" text-angular ng-model="htmlText" ta-toolbar="' + toolbarButtons + '" class="html-editor"></div>',
        showClose:true,
        showCancel:true,
        dialogClass: 'wide',
        loadFunction: function(){
            // Put the existing value into the scope, and tell the controller to process the element with ID: idValue
            $scope.htmlText =  PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'];
            $scope.$broadcast("content-added", idValue);
        },
        okAction:function(){
            PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'] = $scope.htmlText;
            EmailTableHandler.writeSetting(keyName,true);
        }
    });
};


EmailTableHandler.writeSetting = function(settingKey, redraw) {
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, currentValues, function(){
        if (redraw) {
            EmailTableHandler.init(settingKey);
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
    var pattern = PWM_SETTINGS['settings'][settingKey]['pattern'];
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
    NumericValueHandler.impl(settingKey, 'duration', -1, 365 * 24 * 60 * 60 );
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
                htmlBody += '<tr><td>MD5 checksum</td><td class="setting_table_value">' + fileInfo['md5sum'] + '</td></tr>';
                htmlBody += '<tr><td>SHA1 checksum</td><td class="setting_table_value">' + fileInfo['sha1sum'] + '</td></tr>';
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
                var id = settingKey + '_' + key;
                PWM_MAIN.addEventHandler('button-deleteRow-' + id,'click',function(){
                    NamedSecretHandler.deletePassword(settingKey, key);
                });
                PWM_MAIN.addEventHandler('button-usage-' + id,'click',function(){
                    NamedSecretHandler.usagePopup(settingKey, key);
                });
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


