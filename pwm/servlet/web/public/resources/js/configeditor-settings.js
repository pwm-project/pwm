/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
    if (PWM_MAIN.isEmpty(resultValue)) {
        parentDiv.innerHTML = '<button class="btn" id="button-' + settingKey + '-addValue"><span class="btn-icon fa fa-plus-square"></span>Add Value</button>';
        PWM_MAIN.addEventHandler('button-' + settingKey + '-addValue','click',function(){
            UILibrary.stringEditorDialog({
                title:'Add Value',
                textarea:('LOCALIZED_TEXT_AREA' == settingData['syntax']),
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
    if (localeString != null && localeString.length > 0) {
        tableHtml += localeString;
    }
    tableHtml += '</td>';

    tableHtml += '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="fa fa-edit"/></ta>';

    tableHtml += '<td id="panel-' + inputID + '">';
    tableHtml += '<div id="value-' + inputID + '" class="configStringPanel"></div>';
    tableHtml += '</td>';

    var defaultLocale = (localeString == null || localeString.length < 1);
    var required = settingData['required'];
    var hasNonDefaultValues = PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1 ;

    if (!defaultLocale || !required && !hasNonDefaultValues) {
        tableHtml += '<div style="width: 10px; height: 10px;" class="delete-row-icon action-icon fa fa-times"'
        + 'id="button-' + settingKey + '-' + localeString + '-deleteRow"></div>';
    }

    newTableRow.innerHTML = tableHtml;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    PWM_MAIN.addEventHandler("button-" + settingKey + '-' + localeString + "-deleteRow","click",function(){
        LocalizedStringValueHandler.removeLocaleSetting(settingKey, localeString);
    });
    UILibrary.addTextValueToElement('value-' + inputID, (value != null && value.length > 0) ? value : ' ');

    var editFunction = function() {
        UILibrary.stringEditorDialog({
            title:'Edit Value',
            textarea:('LOCALIZED_TEXT_AREA' == settingData['syntax']),
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
            textarea:('LOCALIZED_TEXT_AREA' == settingData['syntax']),
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
        if (syntax == 'PROFILE') {
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
    if (syntax == 'PROFILE') {
        var divDescriptionElement = document.createElement("div");
        divDescriptionElement.innerHTML = PWM_SETTINGS['settings'][settingKey]['description'];
        parentDivElement.appendChild(divDescriptionElement);

        var defaultProfileRow = document.createElement("tr");
        defaultProfileRow.setAttribute("colspan", "5");
    }

    var counter = 0;
    var itemCount = PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][settingKey]);
    parentDivElement.appendChild(tableElement);

    for (var i in resultValue) {
        (function(iteration) {
            StringArrayValueHandler.drawRow(settingKey, iteration, resultValue[iteration], itemCount, tableElement);
            counter++;
        })(i);
    }

    var addItemButton = document.createElement("button");
    addItemButton.setAttribute("type", "button");
    addItemButton.setAttribute("class","btn");
    addItemButton.setAttribute("id","button-" + settingKey + "-addItem");
    addItemButton.innerHTML = '<span class="btn-icon fa fa-plus-square"></span>' + (syntax == 'PROFILE' ? "Add Profile" : "Add Value");
    parentDivElement.appendChild(addItemButton);

    PWM_MAIN.addEventHandler('button-' + settingKey + '-addItem','click',function(){
        StringArrayValueHandler.valueHandler(settingKey,-1);
    });
};

StringArrayValueHandler.drawRow = function(settingKey, iteration, value, itemCount, parentDivElement) {
    var settingInfo = PWM_SETTINGS['settings'][settingKey];
    var syntax = settingInfo['syntax'];

    var inputID = 'value-' + settingKey + '-' + iteration;

    var valueRow = document.createElement("tr");
    valueRow.setAttribute("style", "border-width: 0");
    valueRow.setAttribute("id",inputID + "_row");

    var rowHtml = '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="fa fa-edit"/></ta>';
    rowHtml += '<td style=""><div class="configStringPanel" id="' + inputID + '"></div></td>';

    var downButtonID = 'button-' + settingKey + '-' + iteration + '-moveDown';
    rowHtml += '<td style="border:0">';
    if (itemCount > 1 && iteration != (itemCount -1)) {
        rowHtml += '<span id="' + downButtonID + '" class="action-icon fa fa-chevron-down"></span>';
    }
    rowHtml += '</td>';

    var upButtonID = 'button-' + settingKey + '-' + iteration + '-moveUp';
    rowHtml += '<td style="border:0">';
    if (itemCount > 1 && iteration != 0) {
        rowHtml += '<span id="' + upButtonID + '" class="action-icon fa fa-chevron-up"></span>';
    }
    rowHtml += '</td>';

    var deleteButtonID = 'button-' + settingKey + '-' + iteration + '-delete';
    rowHtml += '<td style="border:0">';

    if (itemCount > 1 || (!settingInfo['required'] && (syntax != 'PROFILE'))) {
        rowHtml += '<span id="' + deleteButtonID + '" class="delete-row-icon action-icon fa fa-times"></span>';
    }
    rowHtml += '</td>';

    valueRow.innerHTML = rowHtml;
    parentDivElement.appendChild(valueRow);

    UILibrary.addTextValueToElement(inputID, value);
    if (syntax != 'PROFILE') {
        PWM_MAIN.addEventHandler(inputID,'click',function(){
            StringArrayValueHandler.valueHandler(settingKey,iteration);
        });
        PWM_MAIN.addEventHandler('button-' + inputID,'click',function(){
            StringArrayValueHandler.valueHandler(settingKey,iteration);
        });
    }

    if (itemCount > 1 && iteration != (itemCount -1)) {
        PWM_MAIN.addEventHandler(downButtonID,'click',function(){StringArrayValueHandler.move(settingKey,false,iteration)});
    }

    if (itemCount > 1 && iteration != 0) {
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
    UILibrary.stringEditorDialog(editorOptions);
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
    var deleteFunction = function() {
        var currentValues = PWM_VAR['clientSettingCache'][settingKey];
        currentValues.splice(iteration,1);
        StringArrayValueHandler.writeSetting(settingKey,false);
    };
    if (syntax == 'PROFILE') {
        PWM_MAIN.showConfirmDialog({
            text:'Are you sure you want to remove the profile?',
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
        if (syntax == 'PROFILE') {
            PWM_CFGEDIT.drawNavigationMenu();
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
            localeTableElement.setAttribute("style", "border-width: 2px; width:525px; margin:0");
            localeTdContent.appendChild(localeTableElement);

            var multiValues = resultValue[localeName];

            for (var iteration in multiValues) {

                var valueTableRow = document.createElement("tr");

                var valueTd1 = document.createElement("td");
                valueTd1.setAttribute("style", "border-width: 0;");

                // clear the old dijit node (if it exists)
                var inputID = "value-" + keyName + "-" + localeName + "-" + iteration;
                var oldDijitNode = registry.byId(inputID);
                if (oldDijitNode != null) {
                    try {
                        oldDijitNode.destroy();
                    } catch (error) {
                    }
                }

                var inputElement = document.createElement("input");
                inputElement.setAttribute("id", inputID);
                inputElement.setAttribute("value", multiValues[iteration]);
                inputElement.setAttribute("onchange", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',this.value,'" + regExPattern + "')");
                inputElement.setAttribute("style", "width: 490px");
                inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
                inputElement.setAttribute("regExp", regExPattern);
                inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
                valueTd1.appendChild(inputElement);
                valueTableRow.appendChild(valueTd1);
                localeTableElement.appendChild(valueTableRow);

                // add remove button
                var imgElement = document.createElement("div");
                imgElement.setAttribute("style", "width: 10px; height: 10px;");
                imgElement.setAttribute("class", "delete-row-icon action-icon fa fa-times");

                imgElement.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',null,'" + regExPattern + "')");
                valueTd1.appendChild(imgElement);
            }

            { // add row button for this locale group
                var newTableRow = document.createElement("tr");
                newTableRow.setAttribute("style", "border-width: 0");
                newTableRow.setAttribute("colspan", "5");

                var newTableData = document.createElement("td");
                newTableData.setAttribute("style", "border-width: 0;");

                var addItemButton = document.createElement("button");
                addItemButton.setAttribute("type", "[button");
                addItemButton.setAttribute("onclick", "PWM_VAR['clientSettingCache']['" + keyName + "']['" + localeName + "'].push('');MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "',null,null,null,'" + regExPattern + "')");
                addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
                addItemButton.innerHTML = "Add Value";
                newTableData.appendChild(addItemButton);

                newTableRow.appendChild(newTableData);
                localeTableElement.appendChild(newTableRow);
            }


            if (localeName != '') { // add remove locale x
                var imgElement2 = document.createElement("div");
                imgElement2.setAttribute("class", "delete-row-icon action-icon fa fa-times");
                imgElement2.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "',null,null,'" + regExPattern + "')");
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
    });
};

MultiLocaleTableHandler.writeMultiLocaleSetting = function(settingKey, locale, iteration, value) {
    if (locale != null) {
        if (PWM_VAR['clientSettingCache'][settingKey][locale] == null) {
            PWM_VAR['clientSettingCache'][settingKey][locale] = [ "" ];
        }

        if (iteration == null) {
            delete PWM_VAR['clientSettingCache'][settingKey][locale];
        } else {
            if (value == null) {
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
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    if (!PWM_MAIN.isEmpty(resultValue)) {
        var headerRow = document.createElement("tr");
        var rowHtml = '<td>Name</td><td>Label</td>';
        headerRow.innerHTML = rowHtml;
        parentDivElement.appendChild(headerRow);
    }

    for (var i in resultValue) {
        FormTableHandler.drawRow(parentDiv, keyName, i, resultValue[i]);
    }

    var buttonRow = document.createElement("tr");
    buttonRow.setAttribute("colspan","5");
    buttonRow.innerHTML = '<td><button class="btn" id="button-' + keyName + '-addRow"><span class="btn-icon fa fa-plus-square"></span>Add Form Item</button></td>';

    parentDivElement.appendChild(buttonRow);

    PWM_MAIN.addEventHandler('button-' + keyName + '-addRow','click',function(){
        FormTableHandler.addRow(keyName);
    });

};

FormTableHandler.drawRow = function(parentDiv, settingKey, iteration, value) {
    require(["dojo/json"], function(JSON){
        var itemCount = PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][settingKey]);
        var inputID = 'value_' + settingKey + '_' + iteration + "_";
        var options = PWM_SETTINGS['settings'][settingKey]['options'];

        var newTableRow = document.createElement("tr");
        newTableRow.setAttribute("style", "border-width: 0");

        var htmlRow = '';
        htmlRow += '<td><input style="width:180px" class="configStringInput" id="' + inputID + 'name" value="' + value['name'] + '"/></td>';
        htmlRow += '<td style="width:170px"><div class="noWrapTextBox" id="' + inputID + 'label"><span class="btn-icon fa fa-edit"></span><span>' + value['labels'][''] + '...</span></div></td>';

        htmlRow += '<td>';
        var userDNtypeAllowed = options['type-userDN'] == 'show';
        var optionList = PWM_GLOBAL['formTypeOptions'];
        if ('types' in options) {
            optionList = JSON.parse(options['types']);
        }
        if (!PWM_MAIN.isEmpty(optionList)) {
            htmlRow += '<select id="' + inputID + 'type">';
            for (var optionItem in optionList) {
                if (optionList[optionItem] != 'userDN' || userDNtypeAllowed) {
                    var optionName = optionList[optionItem];
                    var selected = (optionName == PWM_VAR['clientSettingCache'][settingKey][iteration]['type']);
                    htmlRow += '<option value="' + optionName + '"' + (selected ? " selected" : "") + '>' + optionName + '</option>';
                }
            }
            htmlRow += '</select>';
        }
        htmlRow += '</td>';

        var hideOptions = PWM_SETTINGS['settings'][settingKey]['options']['hideOptions'] == 'true';
        if (!hideOptions) {
            htmlRow += '<td><button id="' + inputID + 'optionsButton"><span class="btn-icon fa fa-sliders"/> Options</button></td>';
        }

        htmlRow += '<td>';
        if (itemCount > 1 && iteration != (itemCount -1)) {
            htmlRow += '<span id="' + inputID + '-moveDown" class="action-icon fa fa-chevron-down"></span>';
        }
        htmlRow += '</td>';

        htmlRow += '<td>';
        if (itemCount > 1 && iteration != 0) {
            htmlRow += '<span id="' + inputID + '-moveUp" class="action-icon fa fa-chevron-up"></span>';
        }
        htmlRow += '</td>';
        htmlRow += '<td><span class="delete-row-icon action-icon fa fa-times" id="' + inputID + '-deleteRowButton"></span></td>';

        newTableRow.innerHTML = htmlRow;
        var parentDivElement = PWM_MAIN.getObject(parentDiv);
        parentDivElement.appendChild(newTableRow);

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
        PWM_MAIN.addEventHandler(inputID + "optionsButton", 'click', function () {
            FormTableHandler.showOptionsDialog(settingKey, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "name", 'input', function () {
            PWM_VAR['clientSettingCache'][settingKey][iteration]['name'] = PWM_MAIN.getObject(inputID + "name").value;
            FormTableHandler.writeFormSetting(settingKey);
        });
        PWM_MAIN.addEventHandler(inputID + "type", 'click', function () {
            PWM_VAR['clientSettingCache'][settingKey][iteration]['type'] = PWM_MAIN.getObject(inputID + "type").value;
            FormTableHandler.writeFormSetting(settingKey);
        });
    });
};

FormTableHandler.writeFormSetting = function(settingKey, finishFunction) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

FormTableHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            var currentValues = PWM_VAR['clientSettingCache'][keyName];
            currentValues.splice(iteration,1);
            FormTableHandler.writeFormSetting(keyName,function(){
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
    FormTableHandler.writeFormSetting(settingKey);
    FormTableHandler.redraw(settingKey);
};

FormTableHandler.arrayMoveUtil = function(arr, fromIndex, toIndex) {
    var element = arr[fromIndex];
    arr.splice(fromIndex, 1);
    arr.splice(toIndex, 0, element);
};


FormTableHandler.addRow = function(keyName) {
    var body='Name <input class="configStringInput" id="newFormFieldName" style="width:300px"/>';
    PWM_MAIN.showConfirmDialog({title:'New Form Field',text:body,showClose:true,loadFunction:function(){
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        PWM_MAIN.addEventHandler('newFormFieldName','input',function(){
            PWM_VAR['newFormFieldName'] = PWM_MAIN.getObject('newFormFieldName').value;
            if (PWM_VAR['newFormFieldName'] && PWM_VAR['newFormFieldName'].length > 1) {
                PWM_MAIN.getObject('dialog_ok_button').disabled = false;
            }
        });
    },okAction:function(){
        var currentSize = PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][keyName]);
        PWM_VAR['clientSettingCache'][keyName][currentSize + 1] = FormTableHandler.newRowValue;
        PWM_VAR['clientSettingCache'][keyName][currentSize + 1].name = PWM_VAR['newFormFieldName'];
        PWM_VAR['clientSettingCache'][keyName][currentSize + 1].labels = {'':PWM_VAR['newFormFieldName']};
        FormTableHandler.writeFormSetting(keyName,function(){
            FormTableHandler.init(keyName);
        });
    }});
};

FormTableHandler.showOptionsDialog = function(keyName, iteration) {
    var options = 'options' in PWM_SETTINGS['settings'][keyName] ? PWM_SETTINGS['settings'][keyName]['options'] : {};
    var showUnique = options['unique'] == 'show';
    require(["dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/NumberSpinner"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_";
        var bodyText = '<div style="max-height: 500px; overflow-y: auto"><table class="noborder">';
        bodyText += '<tr>';
        var descriptionValue = PWM_VAR['clientSettingCache'][keyName][iteration]['description'][''];
        bodyText += '<td id="' + inputID + '-label-description" class="key">Description</td><td>';
        bodyText += '<div class="noWrapTextBox" id="' + inputID + 'description"><span class="btn-icon fa fa-edit"></span><span>' + descriptionValue + '...</span></div>';
        bodyText += '</td>';

        bodyText += '</tr><tr>';
        if (options['required'] != 'hide') {
            bodyText += '<td id="' + inputID + '-label-required" class="key">Required</td><td><input type="checkbox" id="' + inputID + 'required' + '"/></td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '<td id="' + inputID + '-label-confirm" class="key">Confirm</td><td><input type="checkbox" id="' + inputID + 'confirmationRequired' + '"/></td>';
        bodyText += '</tr><tr>';
        if (options['readonly'] == 'show') {
            bodyText += '<td id="' + inputID + '-label-readOnly" class="key">Read Only</td><td><input type="checkbox" id="' + inputID + 'readonly' + '"/></td>';
            bodyText += '</tr><tr>';
        }
        if (showUnique) {
            bodyText += '<td id="' + inputID + '-label-unique" class="key">Unique</td><td><input type="checkbox" id="' + inputID + 'unique' + '"/></td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '<td class="key">Minimum Length</td><td><input type="number" id="' + inputID + 'minimumLength' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td class="key">Maximum Length</td><td><input type="number" id="' + inputID + 'maximumLength' + '"/></td>';
        bodyText += '</tr><tr>';

        { // regex
            bodyText += '<td id="' + inputID + '-label-regex" class="key">Regular Expression</td><td><input type="text" class="configStringInput" id="' + inputID + 'regex' + '"/></td>';
            bodyText += '</tr><tr>';

            var regexErrorValue = PWM_VAR['clientSettingCache'][keyName][iteration]['regexErrors'][''];
            bodyText += '<td id="' + inputID + '-label-regexError" class="key">Regular Expression<br/>Error Message</td><td>';
            bodyText += '<div class="noWrapTextBox" id="' + inputID + 'regexErrors"><span class="btn-icon fa fa-edit"></span><span>' + regexErrorValue + '...</span></div>';
            bodyText += '</td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '<td id="' + inputID + '-label-placeholder" class="key">Placeholder</td><td><input type="text" id="' + inputID + 'placeholder' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td id="' + inputID + '-label-js" class="key">JavaScript</td><td><input type="text" id="' + inputID + 'javascript' + '"/></td>';
        bodyText += '</tr><tr>';
        if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'select') {
            bodyText += '<td class="key">Select Options</td><td><button id="' + inputID + 'editOptionsButton"><span class="btn-icon fa fa-list-ul"/> Edit</button></td>';
            bodyText += '</tr>';
        }
        bodyText += '</table></div>';

        var initDialogWidgets = function() {
            PWM_MAIN.showTooltip({
                id: inputID + '-label-description',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_Description')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-required',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_Required')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-confirm',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_Confirm')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-readOnly',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_ReadOnly')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-unique',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_Unique')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-regex',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_Regex')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-regexError',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_RegexError')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-placeholder',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_Placeholder')
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-js',
                text: PWM_CONFIG.showString('Tooltip_FormOptions_Javascript')
            });

            PWM_MAIN.addEventHandler(inputID + 'editOptionsButton', 'click', function(){
                FormTableHandler.showSelectOptionsDialog(keyName,iteration);
            });

            PWM_MAIN.addEventHandler(inputID + 'description','click',function(){
                FormTableHandler.showDescriptionDialog(keyName, iteration);
            });

            PWM_MAIN.clearDijitWidget(inputID + "required");
            new dijit.form.CheckBox({
                checked: PWM_VAR['clientSettingCache'][keyName][iteration]['required'],
                onChange: function () {
                    PWM_VAR['clientSettingCache'][keyName][iteration]['required'] = this.checked;
                    FormTableHandler.writeFormSetting(keyName)
                }
            }, inputID + "required");

            PWM_MAIN.clearDijitWidget(inputID + "confirmationRequired");
            new dijit.form.CheckBox({
                checked: PWM_VAR['clientSettingCache'][keyName][iteration]['confirmationRequired'],
                onChange: function () {
                    PWM_VAR['clientSettingCache'][keyName][iteration]['confirmationRequired'] = this.checked;
                    FormTableHandler.writeFormSetting(keyName)
                }
            }, inputID + "confirmationRequired");

            if (PWM_SETTINGS['settings'][keyName]['options']['readonly'] == 'show') {
                PWM_MAIN.clearDijitWidget(inputID + "readonly");
                new dijit.form.CheckBox({
                    checked: PWM_VAR['clientSettingCache'][keyName][iteration]['readonly'],
                    onChange: function () {
                        PWM_VAR['clientSettingCache'][keyName][iteration]['readonly'] = this.checked;
                        FormTableHandler.writeFormSetting(keyName)
                    }
                }, inputID + "readonly");
            }

            if (showUnique) {
                PWM_MAIN.clearDijitWidget(inputID + "unique");
                new dijit.form.CheckBox({
                    checked: PWM_VAR['clientSettingCache'][keyName][iteration]['unique'],
                    onChange: function () {
                        PWM_VAR['clientSettingCache'][keyName][iteration]['unique'] = this.checked;
                        FormTableHandler.writeFormSetting(keyName)
                    }
                }, inputID + "unique");
            }

            if (PWM_SETTINGS['settings'][keyName]['options']['unique'] == 'show') {
                PWM_MAIN.clearDijitWidget(inputID + "unique");
                new dijit.form.CheckBox({
                    checked: PWM_VAR['clientSettingCache'][keyName][iteration]['unique'],
                    onChange: function () {
                        PWM_VAR['clientSettingCache'][keyName][iteration]['unique'] = this.checked;
                        FormTableHandler.writeFormSetting(keyName)
                    }
                }, inputID + "unique");
            }

            PWM_MAIN.clearDijitWidget(inputID + "minimumLength");
            new dijit.form.NumberSpinner({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['minimumLength'],
                onChange: function () {
                    PWM_VAR['clientSettingCache'][keyName][iteration]['minimumLength'] = this.value;
                    FormTableHandler.writeFormSetting(keyName)
                },
                constraints: {min: 0, max: 5000},
                style: "width: 70px"
            }, inputID + "minimumLength");

            PWM_MAIN.clearDijitWidget(inputID + "maximumLength");
            new dijit.form.NumberSpinner({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['maximumLength'],
                onChange: function () {
                    PWM_VAR['clientSettingCache'][keyName][iteration]['maximumLength'] = this.value;
                    FormTableHandler.writeFormSetting(keyName)
                },
                constraints: {min: 0, max: 5000},
                style: "width: 70px"
            }, inputID + "maximumLength");

            PWM_MAIN.clearDijitWidget(inputID + "regex");
            new dijit.form.Textarea({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['regex'],
                onChange: function () {
                    PWM_VAR['clientSettingCache'][keyName][iteration]['regex'] = this.value;
                    FormTableHandler.writeFormSetting(keyName)
                }
            }, inputID + "regex");


            PWM_MAIN.addEventHandler(inputID + 'regexErrors','click',function(){
                FormTableHandler.showRegexErrorsDialog(keyName, iteration);
            });

            PWM_MAIN.clearDijitWidget(inputID + "placeholder");
            new dijit.form.Textarea({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['placeholder'],
                onChange: function () {
                    PWM_VAR['clientSettingCache'][keyName][iteration]['placeholder'] = this.value;
                    FormTableHandler.writeFormSetting(keyName)
                }
            }, inputID + "placeholder");

            PWM_MAIN.clearDijitWidget(inputID + "javascript");
            new dijit.form.Textarea({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['javascript'],
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['javascript'] = this.value;FormTableHandler.writeFormSetting(keyName)}
            },inputID + "javascript");
        };

        PWM_MAIN.showDialog({
            title: PWM_SETTINGS['settings'][keyName]['label'] + ' - ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
            text:bodyText,
            allowMove:true,
            loadFunction:initDialogWidgets,
            okAction:function(){
                FormTableHandler.redraw(keyName);
            }
        });
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
            if (localeName != '') {
                bodyText += '<td><span class="delete-row-icon action-icon fa fa-times" id="' + localeID + '-removeLocaleButton"></span></td>';
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
                            FormTableHandler.writeFormSetting(keyName);
                        });
                        PWM_MAIN.addEventHandler(localeID + '-removeLocaleButton', 'click', function () {
                            delete PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName];
                            FormTableHandler.writeFormSetting(keyName);
                            FormTableHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                        });
                    }(iter));
                }
                UILibrary.addAddLocaleButtonRow(inputID + 'table', inputID, function(localeName){
                    if (localeName in PWM_VAR['clientSettingCache'][keyName][iteration][settingType]) {
                        alert('Locale is already present');
                    } else {
                        PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName] = '';
                        FormTableHandler.writeFormSetting(keyName);
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
        bodyText += '<td style="border:0; width:15px">';
        bodyText += '<span id="' + optionID + '-removeButton" class="delete-row-icon action-icon fa fa-times"></span>';
        bodyText += '</td>';
        bodyText += '</tr><tr>';
    }
    bodyText += '</tr></table>';
    bodyText += '<br/><br/><br/>';
    bodyText += '<input class="configStringInput" style="width:200px" type="text" placeholder="Value" required id="addSelectOptionName"/>';
    bodyText += '<input class="configStringInput" style="width:200px" type="text" placeholder="Display Name" required id="addSelectOptionValue"/>';
    bodyText += '<button id="addSelectOptionButton"><span class="btn-icon fa fa-plus-square"/> Add</button>';

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
    if (optionName == null || optionName.length < 1) {
        alert('Name field is required');
        return;
    }

    if (optionValue == null || optionValue.length < 1) {
        alert('Value field is required');
        return;
    }

    PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions'][optionName] = optionValue;
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showSelectOptionsDialog(keyName, iteration);
};

FormTableHandler.removeSelectOptionsOption = function(keyName, iteration, optionName) {
    delete PWM_VAR['clientSettingCache'][keyName][iteration]['selectOptions'][optionName];
    FormTableHandler.writeFormSetting(keyName);
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
                htmlBody += '<button id="button-clearPassword-' + settingKey + '" class="btn"><span class="btn-icon fa fa-times"></span>Clear Value</button>';
            } else {
                htmlBody += '<button id="button-changePassword-' + settingKey + '" class="btn"><span class="btn-icon fa fa-plus-square"></span>Store Value</button>';
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

ChangePasswordHandler.validatePasswordPopupFields = function() {
    var password1 = PWM_MAIN.getObject('password1').value;
    var password2 = PWM_MAIN.getObject('password2').value;

    var matchStatus = "";

    PWM_MAIN.getObject('button-storePassword').disabled = true;
    if (password2.length > 0) {
        if (password1 == password2) {
            matchStatus = "MATCH";
            PWM_MAIN.getObject('button-storePassword').disabled = false;
        } else {
            matchStatus = "NO_MATCH";
        }
    }

    ChangePasswordHandler.markConfirmationCheck(matchStatus);
};

ChangePasswordHandler.markConfirmationCheck = function(matchStatus) {
    if (matchStatus == "MATCH") {
        PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'visible';
        PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'hidden';
        PWM_MAIN.getObject("confirmCheckMark").width = '15';
        PWM_MAIN.getObject("confirmCrossMark").width = '0';
    } else if (matchStatus == "NO_MATCH") {
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

    var url = PWM_GLOBAL['url-restservice'] + "/randompassword";
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
    var length = 'passwordDialog-randomLength' in PWM_VAR ? PWM_VAR['passwordDialog-randomLength'] : 25;
    var special = 'passwordDialog-special' in PWM_VAR ? PWM_VAR['passwordDialog-special'] : false;

    var bodyText = '<table class="noborder">'
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
    + '</tr></table>'
    + '<br/><br/><div class="dialogSection" style="width: 400px"><span class="formFieldLabel">Generate Random Password </span><br/>'
    + '<label class="checkboxWrapper"><input id="input-special" type="checkbox"' + (special ? ' checked' : '') + '>Specials</input></label>'
    + '&nbsp;&nbsp;&nbsp;&nbsp;<input id="input-randomLength" type="number" min="10" max="1000" value="' + length + '" style="width:45px">Length'
    + '&nbsp;&nbsp;&nbsp;&nbsp;<button id="button-generateRandom" name="button-generateRandom"><span class="fa fa-random btn-icon"></span>Generate Random</button>'
    + '</div><br/><br/>'
    + '<button name="button-storePassword" class="btn" id="button-storePassword" disabled="true"/>'
    + '<span class="fa fa-forward btn-icon"></span>Store Password</button>&nbsp;&nbsp;'
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
                ChangePasswordHandler.validatePasswordPopupFields();
                PWM_MAIN.getObject('password2').value = '';
            });
            PWM_MAIN.addEventHandler('password2','input',function(){
                PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'] = PWM_MAIN.getObject('password2').value;
                ChangePasswordHandler.validatePasswordPopupFields();
            });
            PWM_MAIN.addEventHandler('show','change',function(){
                PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = PWM_MAIN.getObject('show').checked;
                ChangePasswordHandler.changePasswordPopup(settingKey);
            });
            PWM_MAIN.getObject('password1').focus();
            ChangePasswordHandler.validatePasswordPopupFields();
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
    { label: "Put", value: "put" }
];
ActionHandler.ldapMethodOptions = [
    { label: "Replace", value: "replace" },
    { label: "Add", value: "add" },
    { label: "Remove", value: "remove" }
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
    console.log('ActionHandler redraw for ' + keyName)
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    if (!PWM_MAIN.isEmpty(resultValue)) {
        var headerRow = document.createElement("tr");
        headerRow.setAttribute("style", "border-width: 0");

        var header1 = document.createElement("td");
        header1.setAttribute("style", "border-width: 0;");
        header1.innerHTML = "Name";
        headerRow.appendChild(header1);

        var header2 = document.createElement("td");
        header2.setAttribute("style", "border-width: 0;");
        header2.innerHTML = "Description";
        headerRow.appendChild(header2);

        parentDivElement.appendChild(headerRow);
    }

    for (var i in resultValue) {
        ActionHandler.drawRow(parentDiv, keyName, i, resultValue[i]);
    }

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    newTableRow.setAttribute("colspan", "5");

    var newTableData = document.createElement("td");
    newTableData.setAttribute("style", "border-width: 0");

    var addItemButton = document.createElement("button");
    addItemButton.setAttribute("type", "button");
    addItemButton.setAttribute("class", "btn");
    addItemButton.setAttribute("id", "button-" + keyName + "-addValue");
    addItemButton.innerHTML = '<span class="btn-icon fa fa-plus-square"></span>Add Action';
    newTableData.appendChild(addItemButton);

    newTableRow.appendChild(newTableData);
    parentDivElement.appendChild(newTableRow);

    require(["dojo/parser","dijit/form/Button","dijit/form/Select","dijit/form/Textarea"],function(dojoParser){
        dojoParser.parse(parentDiv);
    });

    PWM_MAIN.addEventHandler('button-' + keyName + '-addValue','click',function(){
        ActionHandler.addRow(keyName);
    });
};

ActionHandler.drawRow = function(parentDiv, settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";
    var optionList = PWM_GLOBAL['actionTypeOptions'];

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    var htmlRow = '';
    htmlRow += '<td>';
    htmlRow += '<input id="input-' + inputID + '-name" class="configStringInput" style="width:180px" value="' + value['name'] + '"/>';
    htmlRow += '</td><td>';
    htmlRow += '<input id="input-' + inputID + '-description" class="configStringInput" style="width:180px" value="' + value['description'] + '"/>';
    htmlRow += '</td><td>';
    htmlRow += '<select id="select-' + inputID + '-type">';
    for (var optionItem in optionList) {
        var selected = optionList[optionItem] == PWM_VAR['clientSettingCache'][settingKey][iteration]['type'];
        htmlRow += '<option value="' + optionList[optionItem] + '"' + (selected ? ' selected' : '') + '>' + optionList[optionItem] + '</option>';
    }
    htmlRow += '</td><td>';
    htmlRow += '<button id="button-' + inputID + '-options"><span class="btn-icon fa fa-sliders"/> Options</button>';
    htmlRow += '</td>';
    htmlRow += '<td><span class="delete-row-icon action-icon fa fa-times" id="button-' + inputID + '-deleteRow"></span></td>';


    newTableRow.innerHTML = htmlRow;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    PWM_MAIN.addEventHandler('button-' + inputID + '-options','click',function(){
        ActionHandler.showOptionsDialog(settingKey, iteration);
    });
    PWM_MAIN.addEventHandler('input-' + inputID + '-name','input',function(){
        PWM_VAR['clientSettingCache'][settingKey][iteration]['name'] = PWM_MAIN.getObject('input-' + inputID + '-name').value;
        ActionHandler.writeFormSetting(settingKey);
    });
    PWM_MAIN.addEventHandler('input-' + inputID + '-description','input',function(){
        PWM_VAR['clientSettingCache'][settingKey][iteration]['description'] = PWM_MAIN.getObject('input-' + inputID + '-description').value;
        ActionHandler.writeFormSetting(settingKey);
    });
    PWM_MAIN.addEventHandler('select-' + inputID + '-type','change',function(){
        PWM_VAR['clientSettingCache'][settingKey][iteration]['type'] = PWM_MAIN.getObject('select-' + inputID + '-type').value;
        ActionHandler.writeFormSetting(settingKey);
    });
    PWM_MAIN.addEventHandler('button-' + inputID + '-deleteRow','click',function(){
        ActionHandler.removeRow(settingKey, iteration);
    });
};

ActionHandler.writeFormSetting = function(settingKey, finishFunction) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting, finishFunction);
};

ActionHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            delete PWM_VAR['clientSettingCache'][keyName][iteration];
            console.log("removed iteration " + iteration + " from " + keyName + ", cached keyValue=" + PWM_VAR['clientSettingCache'][keyName]);
            ActionHandler.writeFormSetting(keyName,function(){
                ActionHandler.init(keyName);
            });
        }
    })
};

ActionHandler.addRow = function(keyName) {
    var body='Name <input class="configStringInput" id="newActionName" style="width:300px"/>';
    PWM_MAIN.showConfirmDialog({title:'New Action',text:body,showClose:true,loadFunction:function(){
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        PWM_MAIN.addEventHandler('newActionName','input',function(){
            PWM_VAR['newActionName'] = PWM_MAIN.getObject('newActionName').value;
            if (PWM_VAR['newActionName'] && PWM_VAR['newActionName'].length > 1) {
                PWM_MAIN.getObject('dialog_ok_button').disabled = false;
            }
        });
    },okAction:function(){
        var currentSize = PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][keyName]);
        PWM_VAR['clientSettingCache'][keyName][currentSize + 1] = ActionHandler.defaultValue;
        PWM_VAR['clientSettingCache'][keyName][currentSize + 1].name = PWM_VAR['newActionName'];
        ActionHandler.writeFormSetting(keyName,function(){
            ActionHandler.init(keyName);
        });

    }});
};


ActionHandler.showOptionsDialog = function(keyName, iteration) {
    require(["dojo/store/Memory","dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/Select","dijit/form/ValidationTextBox"],function(Memory){
        var inputID = 'value_' + keyName + '_' + iteration + "_";
        var value = PWM_VAR['clientSettingCache'][keyName][iteration];
        var titleText = 'title';
        var bodyText = '<table class="noborder">';
        if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'webservice') {
            titleText = 'Web Service options for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'];
            bodyText += '<tr>';
            bodyText += '<td class="key">HTTP Method</td><td style="border:0;"><select id="select-' + inputID + '-method">';

            for (var optionItem in ActionHandler.httpMethodOptions) {
                var label = ActionHandler.httpMethodOptions[optionItem]['label'];
                var optionValue = ActionHandler.httpMethodOptions[optionItem]['value'];
                var selected = optionValue == PWM_VAR['clientSettingCache'][keyName][iteration]['method'];
                bodyText += '<option value="' + optionValue + '"' + (selected ? ' selected' : '') + '>' + label + '</option>';
            }
            bodyText += '</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td class="key">HTTP Headers</td><td><button id="button-' + inputID + '-headers"><span class="btn-icon fa fa-list-ul"/> Edit</button></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td class="key">URL</td><td><input type="text" class="configstringinput" style="width:400px" placeholder="http://www.example.com/service"  id="input-' + inputID + '-url' + '" value="' + value['url'] + '"/></td>';
            bodyText += '</tr>';
            if (PWM_VAR['clientSettingCache'][keyName][iteration]['method'] != 'get') {
                bodyText += '<tr><td class="key">Body</td><td><textarea style="max-width:400px; height:100px; max-height:100px" class="configStringInput" id="input-' + inputID + '-body' + '"/>' + value['body'] + '</textarea></td></tr>';
            }
            bodyText += '';
        } else if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'ldap') {
            titleText = 'LDAP options for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'];
            bodyText += '<tr>';
            bodyText += '<td class="key">Attribute Name</td><td><input style="width:300px" class="configStringInput" type="text" id="input-' + inputID + '-attributeName' + '" value="' + value['attributeName'] + '"/></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td class="key">Attribute Value</td><td><input style="width:300px" class="configStringInput" type="text" id="input-' + inputID + '-attributeValue' + '" value="' + value['attributeValue'] + '"/></td>';
            bodyText += '</tr>';
            bodyText += '<tr>';
            bodyText += '<td class="key">Operation Type</td><td style="border:0;"><select id="select-' + inputID + '-ldapMethod' + '">';

            for (var optionItem in ActionHandler.ldapMethodOptions) {
                var label = ActionHandler.ldapMethodOptions[optionItem]['label'];
                var optionValue = ActionHandler.ldapMethodOptions[optionItem]['value'];
                var selected = optionValue == PWM_VAR['clientSettingCache'][keyName][iteration]['ldapMethod'];
                bodyText += '<option value="' + optionValue + '"' + (selected ? ' selected' : '') + '>' + label + '</option>';
            }
            bodyText += '</td></tr>';
        }
        bodyText += '</table>';


        PWM_MAIN.showDialog({
            title: titleText,
            text: bodyText,
            loadFunction: function(){
                PWM_MAIN.addEventHandler('button-' + inputID + '-headers','click',function(){
                    ActionHandler.showHeadersDialog(keyName,iteration);
                });
                if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'webservice') {
                    PWM_MAIN.addEventHandler('select-' + inputID + '-method','change',function(){
                        var value = PWM_MAIN.getObject('select-' + inputID + '-method').value;
                        if (value == 'get') {
                            PWM_VAR['clientSettingCache'][keyName][iteration]['body'] = '';
                        }
                        PWM_VAR['clientSettingCache'][keyName][iteration]['method'] = value;
                        ActionHandler.writeFormSetting(keyName, function(){ ActionHandler.showOptionsDialog(keyName,iteration)});
                    });
                    PWM_MAIN.addEventHandler('input-' + inputID + '-url','input',function(){
                        PWM_VAR['clientSettingCache'][keyName][iteration]['url'] = PWM_MAIN.getObject('input-' + inputID + '-url').value;
                        ActionHandler.writeFormSetting(keyName);
                    });
                    PWM_MAIN.addEventHandler('input-' + inputID + '-body','input',function(){
                        PWM_VAR['clientSettingCache'][keyName][iteration]['body'] = PWM_MAIN.getObject('input-' + inputID + '-body').value;
                        ActionHandler.writeFormSetting(keyName);
                    });
                } else if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'ldap') {
                    PWM_MAIN.addEventHandler('input-' + inputID + '-attributeName','input',function(){
                        PWM_VAR['clientSettingCache'][keyName][iteration]['attributeName'] = PWM_MAIN.getObject('input-' + inputID + '-attributeName').value;
                        ActionHandler.writeFormSetting(keyName);
                    });
                    PWM_MAIN.addEventHandler('input-' + inputID + '-attributeValue','input',function(){
                        PWM_VAR['clientSettingCache'][keyName][iteration]['attributeValue'] = PWM_MAIN.getObject('input-' + inputID + '-attributeValue').value;
                        ActionHandler.writeFormSetting(keyName);
                    });
                    PWM_MAIN.addEventHandler('select-' + inputID + '-ldapMethod','change',function(){
                        PWM_VAR['clientSettingCache'][keyName][iteration]['ldapMethod'] = PWM_MAIN.getObject('select-' + inputID + '-ldapMethod').value;
                        ActionHandler.writeFormSetting(keyName);
                    });
                }
            }
        });
    });
};

ActionHandler.showHeadersDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/ValidationTextBox","dijit/form/Button","dijit/form/TextBox"],function(Dialog,ValidationTextBox,Button,TextBox){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "headers_";

        var bodyText = '';
        bodyText += '<table class="noborder">';
        bodyText += '<tr>';
        bodyText += '<td><b>Name</b></td><td><b>Value</b></td>';
        bodyText += '</tr><tr>';
        for (var headerName in PWM_VAR['clientSettingCache'][keyName][iteration]['headers']) {
            var value = PWM_VAR['clientSettingCache'][keyName][iteration]['headers'][headerName];
            var optionID = inputID + headerName;
            bodyText += '<td>' + headerName + '</td><td>' + value + '</td>';
            bodyText += '<td><span class="delete-row-icon action-icon fa fa-times" id="button-' + optionID + '-deleteRow"></span></td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<button id="button-' + inputID + '-addHeader" class="btn"><span class="btn-icon fa fa-plus-square"></span>Add Header</button>';

        PWM_MAIN.showDialog({
            title: 'Http Headers for webservice ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
            text: bodyText,
            okAction: function() {
                ActionHandler.showOptionsDialog(keyName,iteration);
            },
            loadFunction: function() {
                for (var headerName in PWM_VAR['clientSettingCache'][keyName][iteration]['headers']) {
                    var headerID = inputID + headerName;
                    PWM_MAIN.addEventHandler('button-' + headerID + '-deleteRow','click',function(){
                        delete PWM_VAR['clientSettingCache'][keyName][iteration]['headers'][headerName];
                        ActionHandler.writeFormSetting(keyName);
                        ActionHandler.showHeadersDialog(keyName, iteration);
                    });
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
        if (PWM_VAR['newHeaderName'].length > 1 && PWM_VAR['newHeaderValue'].length > 1) {
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
        },okAction:function(){
            var headers = PWM_VAR['clientSettingCache'][keyName][iteration]['headers'];
            headers[PWM_VAR['newHeaderName']] = PWM_VAR['newHeaderValue'];
            ActionHandler.writeFormSetting(keyName);
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

    if (PWM_MAIN.isEmpty(resultValue)) {
        var htmlBody = '<button class="btn" id="button-addValue-' + settingKey + '">';
        htmlBody += '<span class="btn-icon fa fa-plus-square"></span>Add Value';
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
    var localeLabel = localeName == '' ? 'Default Locale' : PWM_GLOBAL['localeInfo'][localeName] + " (" + localeName + ")";
    var idPrefix = "setting-" + localeName + "-" + settingKey;
    var htmlBody = '';
    htmlBody += '<table style="border:0"><tr ><td style="border:0">';
    htmlBody += '<table>';
    if (PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1) {
        htmlBody += '<tr><td colspan="5" class="title" style="font-size:100%; font-weight:normal">' + localeLabel + '</td></tr>';
    }
    var outputFunction = function (labelText, typeText) {
        htmlBody += '<tr><td style="text-align:right; border-width:0;">' + labelText + '</td>';
        htmlBody += '<td id="button-' + typeText + '-' + idPrefix + '" style="border-width:0; width: 15px"><span class="fa fa-edit"/></ta>';
        htmlBody += '<td style=""><div class="configStringPanel" id="panel-' + typeText + '-' + idPrefix + '"></div></td>';
        htmlBody += '</tr>';
    };
    outputFunction('To', 'to');
    outputFunction('From', 'from');
    outputFunction('Subject', 'subject');
    outputFunction('Plain Body', 'bodyPlain');
    outputFunction('HTML Body', 'bodyHtml');

    htmlBody += '</table></td><td style="width:20px; border:0; vertical-align:top">';
    if (localeName != '' || PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][settingKey]) < 2) { // add remove locale x
        htmlBody += '<div id="button-deleteRow-' + idPrefix + '" style="vertical-align:top" class="delete-row-icon action-icon fa fa-times"></div>';
    }
    htmlBody += '</td></tr></table><br/>';
    return htmlBody;
};


EmailTableHandler.instrumentRow = function(settingKey, localeName) {
    var settingData = PWM_SETTINGS['settings'][settingKey];
    var idPrefix = "setting-" + localeName + "-" + settingKey;

    var editor = function(drawTextArea, type){
        UILibrary.stringEditorDialog({
            title:'Edit Value - ' + settingData['label'],
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
    PWM_MAIN.addEventHandler('button-to-' + idPrefix,'click',function(){ editor(false,'to'); });
    PWM_MAIN.addEventHandler('panel-to-' + idPrefix,'click',function(){ editor(false,'to'); });

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
    require(["dijit/Editor","dijit/_editor/plugins/AlwaysShowToolbar","dijit/_editor/plugins/LinkDialog","dijit/_editor/plugins/ViewSource","dijit/_editor/plugins/FontChoice","dijit/_editor/plugins/TextColor"],
        function(Editor,AlwaysShowToolbar){
            var idValue = keyName + "_" + localeName + "_htmlEditor";
            var bodyText = '';
            bodyText += '<div id="' + idValue + '" style="border:2px solid #EAEAEA; height:300px"></div>';
            PWM_MAIN.showDialog({
                title: "HTML Editor",
                text: bodyText,
                showClose:true,
                showCancel:true,
                dialogClass: 'wide',
                loadFunction:function(){
                    PWM_MAIN.clearDijitWidget(idValue);
                    new Editor({
                        extraPlugins: [
                            AlwaysShowToolbar,"viewsource",
                            {name:"dijit/_editor/plugins/LinkDialog",command:"createLink",urlRegExp:".*"},
                            "fontName","foreColor"
                        ],
                        height: '300px',
                        value: PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'],
                        style: '',
                        onChange: function(){PWM_VAR['temp-dialogInputValue'] = this.get('value')},
                        onKeyUp: function(){PWM_VAR['temp-dialogInputValue'] = this.get('value')}
                    },idValue).startup();
                },
                okAction:function(){
                    PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'] = PWM_VAR['temp-dialogInputValue'];
                    EmailTableHandler.writeSetting(keyName,true);
                }
            });
        }
    );
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

// -------------------------- challenge handler ------------------------------------

var ChallengeSettingHandler = {};
ChallengeSettingHandler.defaultItem = {text:'Question',minLength:4,maxLength:200,adminDefined:true,enforceWordlist:true,maxQuestionCharsInAnswer:3};

ChallengeSettingHandler.init = function(settingKey) {
    var parentDiv = "table_setting_" + settingKey;
    console.log('ChallengeSettingHandler init for ' + settingKey);
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(settingKey, function(resultValue) {
        PWM_VAR['clientSettingCache'][settingKey] = resultValue;
        if (PWM_MAIN.isEmpty(resultValue)) {
            var htmlBody = '<button class="btn" id="button-addValue-' + settingKey + '">';
            htmlBody += '<span class="btn-icon fa fa-plus-square"></span>Add Value';
            htmlBody += '</button>';

            var parentDivElement = PWM_MAIN.getObject(parentDiv);
            parentDivElement.innerHTML = htmlBody;

            PWM_MAIN.addEventHandler('button-addValue-' + settingKey,'click',function(){
                PWM_VAR['clientSettingCache'][settingKey] = {};
                PWM_VAR['clientSettingCache'][settingKey][''] = [];
                PWM_VAR['clientSettingCache'][settingKey][''].push(ChallengeSettingHandler.defaultItem);
                ChallengeSettingHandler.write(settingKey,function(){
                    ChallengeSettingHandler.init(settingKey);
                });
            });
        } else {
            ChallengeSettingHandler.draw(settingKey);
        }
    });
};

ChallengeSettingHandler.draw = function(settingKey) {
    var parentDiv = "table_setting_" + settingKey;
    var resultValue = PWM_VAR['clientSettingCache'][settingKey];
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    var bodyText = '';
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    for (var localeName in resultValue) {
        (function(localeKey) {
            var multiValues = resultValue[localeKey];
            var rowCount = PWM_MAIN.itemCount(multiValues);
            var editJsText = 'ChallengeSettingHandler.editLocale(\'' + settingKey + '\',\'' + localeKey + '\')';

            bodyText += '<table class="noborder"><tr><td>';
            bodyText += '<table style="cursor: pointer; table-layout: fixed">';
            var localeLabel = localeName == '' ? 'Default Locale' : PWM_GLOBAL['localeInfo'][localeName] + " (" + localeName + ")";
            if (PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1) {
                bodyText += '<tr><td class="title" style="font-size:100%; font-weight:normal">' + localeLabel + '</td></tr>';
            }

            bodyText += '<tr>';
            bodyText += '<td style="width:100%" onclick="' + editJsText + '"> ';
            if (rowCount > 0) {
                for (var iteration in multiValues) {
                    var id = 'panel-value-' + settingKey + '-' + localeKey + '-' + iteration;
                    bodyText += '<div style="text-overflow:ellipsis; white-space:nowrap; overflow:hidden" id="' + id + '">text</div>';
                }
            } else {
                bodyText += '[No Questions]';
            }
            bodyText += '</td></tr>';

            bodyText += '</table></td><td style="width:20px; border:0; vertical-align:top">';
            if (localeName != '' || PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][settingKey]) < 2) { // add remove locale x
                bodyText += '<div id="button-deleteRow-' + settingKey + '-' + localeKey + '" style="vertical-align:top" class="delete-row-icon action-icon fa fa-times"></div>';
            }
            bodyText += '</td></tr></table><br/>';
        }(localeName));
    }
    parentDivElement.innerHTML = bodyText;

    var addLocaleFunction = function(localeValue) {
        if (localeValue in PWM_VAR['clientSettingCache'][settingKey]) {
            PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Error'),text:'Locale <i>' + localeValue + '</i> is already present.'});
        } else {
            PWM_VAR['clientSettingCache'][settingKey][localeValue] = [];
            PWM_VAR['clientSettingCache'][settingKey][localeValue][0] = ChallengeSettingHandler.defaultItem;
            ChallengeSettingHandler.write(settingKey, function(){
                ChallengeSettingHandler.init(settingKey);
            });
        }
    };
    var tableElement = document.createElement("div");
    parentDivElement.appendChild(tableElement);

    UILibrary.addAddLocaleButtonRow(tableElement, settingKey, addLocaleFunction, Object.keys(resultValue));

    for (var localeName in resultValue) {
        (function(localeKey) {
            var multiValues = resultValue[localeKey];
            var rowCount = PWM_MAIN.itemCount(multiValues);
            if (rowCount > 0) {
                for (var iteration in multiValues) {
                    (function (rowKey) {
                        var id = 'panel-value-' + settingKey + '-' + localeKey + '-' + iteration;
                        var questionText = multiValues[rowKey]['text'];
                        var adminDefined = multiValues[rowKey]['adminDefined'];
                        var output = (adminDefined ? questionText : '[User Defined]');
                        UILibrary.addTextValueToElement(id,output);
                    }(iteration));
                }
            }

            PWM_MAIN.addEventHandler('button-deleteRow-' + settingKey + '-' + localeKey,'click',function(){
                ChallengeSettingHandler.deleteLocale(settingKey, localeKey)
            });
        }(localeName));
    }

};

ChallengeSettingHandler.editLocale = function(keyName, localeKey) {
    var localeDisplay = localeKey == "" ? "Default" : localeKey;
    var dialogBody = '<div id="challengeLocaleDialogDiv" style="max-height:500px; overflow-x: auto">';

    var localeName = localeKey;

    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    require(["dojo","dijit/registry","dojo/parser","dijit/form/Button","dijit/form/ValidationTextBox","dijit/form/Textarea","dijit/form/NumberSpinner","dijit/form/ToggleButton"],
        function(dojo,registry,dojoParser){


            var multiValues = resultValue[localeName];

            for (var iteration in multiValues) {
                (function(rowKey) {
                    dialogBody += '<table style="border:0">';
                    dialogBody += '<tr><td>';
                    dialogBody += '<table class="noborder" style="margin:0"><tr>';
                    dialogBody += '<td colspan="200" style="border-width: 0;">';

                    var inputID = "value-" + keyName + "-" + localeName + "-" + rowKey;
                    PWM_MAIN.clearDijitWidget(inputID);

                    dialogBody += '<input class="configStringInput" id="' + inputID + '" style="width: 700px" required="required" disabled value="Loading"/>';

                    dialogBody += '</td>';
                    dialogBody += '</tr>';

                    dialogBody += '<tr style="padding-bottom: 15px; border:0"><td style="padding-bottom: 15px; border:0">';

                    dialogBody += '<label class="checkboxWrapper"><input type="checkbox" id="value-adminDefined-' + inputID + '" disabled/>Admin Defined</label>';

                    dialogBody += '</td><td style="padding-bottom: 15px; border:0">';
                    dialogBody += '<input style="width: 50px" data-dojo-type="dijit/form/NumberSpinner" value="' +multiValues[rowKey]['minLength'] + '" data-dojo-props="constraints:{min:0,max:255,places:0}""';
                    dialogBody += ' onchange="PWM_VAR[\'clientSettingCache\'][\'' + keyName + '\'][\'' + localeKey + '\'][\'' + rowKey + '\'][\'minLength\'] = this.value"/><br/>Min Length';

                    dialogBody += '</td><td style="padding-bottom: 15px; border:0">';
                    dialogBody += '<input style="width: 50px" data-dojo-type="dijit/form/NumberSpinner" value="' +multiValues[rowKey]['maxLength'] + '" data-dojo-props="constraints:{min:0,max:255,places:0}""';
                    dialogBody += ' onchange="PWM_VAR[\'clientSettingCache\'][\'' + keyName + '\'][\'' + localeKey + '\'][\'' + rowKey + '\'][\'maxLength\'] = this.value"/><br/>Max Length';

                    dialogBody += '</td><td style="padding-bottom: 15px; border:0">';
                    dialogBody += '<input style="width: 50px" data-dojo-type="dijit/form/NumberSpinner" value="' +multiValues[rowKey]['maxQuestionCharsInAnswer'] + '" data-dojo-props="constraints:{min:0,max:255,places:0}""';
                    dialogBody += ' onchange="PWM_VAR[\'clientSettingCache\'][\'' + keyName + '\'][\'' + localeKey + '\'][\'' + rowKey + '\'][\'maxQuestionCharsInAnswer\'] = this.value"/><br/> Max Question Chars';

                    dialogBody += '</td><td style="padding-bottom: 15px; border:0">';
                    dialogBody += '<label class="checkboxWrapper"><input type="checkbox" id="value-wordlist-' + inputID + '" disabled/>Apply Wordlist</label>';

                    dialogBody += '</td></tr>';
                    dialogBody += '</table></td><td style="border:0; vertical-align: top">';
                    if (PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][keyName][localeKey]) > 1) { // add remove locale x

                        dialogBody += '<div class="delete-row-icon action-icon fa fa-times" id="button-deleteRow-' + inputID + '"/>';
                    }

                    dialogBody += '</td></tr></table>';
                    dialogBody += '<br/>';

                }(iteration));
            }


            dialogBody += '</div>';
            dialogBody += '<br/><br/><button type="button" data-dojo-type="dijit/form/Button"';
            dialogBody += ' onclick="ChallengeSettingHandler.addRow(\'' + keyName + '\',\'' + localeKey + '\')"';
            dialogBody += '><span class="btn-icon fa fa-plus-square"></span>Add Value</button>';

            var dialogTitle = PWM_SETTINGS['settings'][keyName]['label'] + ' - ' + localeDisplay;
            PWM_MAIN.showDialog({title:dialogTitle,text:dialogBody,showClose:true,dialogClass:'wide',loadFunction:function(){
                dojoParser.parse(PWM_MAIN.getObject('challengeLocaleDialogDiv'));
                for (var iteration in multiValues) {
                    (function(rowKey) {
                        var inputID = "value-" + keyName + "-" + localeName + "-" + rowKey;

                        // question text
                        var processQuestion = function() {
                            var isAdminDefined = multiValues[rowKey]['adminDefined'];
                            PWM_MAIN.getObject(inputID).value = isAdminDefined ? multiValues[rowKey]['text'] : '[User Defined]';
                            PWM_MAIN.getObject(inputID).disabled = !isAdminDefined;
                        };
                        processQuestion();
                        PWM_MAIN.addEventHandler(inputID, 'input', function () {
                            //if (!multiValues[rowKey]['adminDefined']) {
                                PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['text'] = PWM_MAIN.getObject(inputID).value;
                            //}
                        });

                        // admin defined checkbox
                        PWM_MAIN.getObject('value-adminDefined-' + inputID).disabled = false;
                        PWM_MAIN.getObject('value-adminDefined-' + inputID).checked = multiValues[rowKey]['adminDefined'];
                        PWM_MAIN.addEventHandler('value-adminDefined-' + inputID,'change',function(){
                            var checked = PWM_MAIN.getObject('value-adminDefined-' + inputID).checked;
                            multiValues[rowKey]['adminDefined'] = checked;
                            processQuestion();
                        });

                        // wordlist checkbox
                        PWM_MAIN.getObject('value-wordlist-' + inputID).disabled = false;
                        PWM_MAIN.getObject('value-wordlist-' + inputID).checked = multiValues[rowKey]['enforceWordlist'];
                        PWM_MAIN.addEventHandler('value-wordlist-' + inputID,'change',function(){
                            var checked = PWM_MAIN.getObject('value-wordlist-' + inputID).checked;
                            multiValues[rowKey]['enforceWordlist'] = checked;
                        });

                        // delete row
                        PWM_MAIN.addEventHandler('button-deleteRow-' + inputID, 'click', function () {
                            ChallengeSettingHandler.deleteRow(keyName, localeKey, rowKey);
                        });

                    }(iteration));
                }

            },okAction:function(){
                ChallengeSettingHandler.write(keyName);
                ChallengeSettingHandler.draw(keyName);
            }});
        }
    );

};

ChallengeSettingHandler.deleteLocale = function(keyName,localeKey) {
    PWM_MAIN.showConfirmDialog({
        text: 'Are you sure you want to remove all the questions for the <i>' + localeKey + '</i> locale?',
        okAction:function(){
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                delete PWM_VAR['clientSettingCache'][keyName][localeKey];
                PWM_CFGEDIT.writeSetting(keyName, PWM_VAR['clientSettingCache'][keyName],function(){
                    PWM_MAIN.closeWaitDialog();
                    ChallengeSettingHandler.init(keyName);
                });
            }});
        }
    });
};

ChallengeSettingHandler.toggleAdminDefinedRow = function(toggleElement,inputID,keyName,localeKey,rowKey) {
    require(["dojo","dijit/registry"],function(dojo,registry){
        var currentSetting = toggleElement.checked;
        PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['adminDefined'] = currentSetting;
        var inputElement = registry.byId(inputID);
        if (currentSetting) {
            inputElement.set('disabled',false);
            inputElement.set('value','Question');
        } else {
            inputElement.set('disabled',true);
            inputElement.set('value','[User Defined]');
            PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['text'] = '';
        }
    });
};

ChallengeSettingHandler.deleteRow = function(keyName, localeKey, rowName) {
    delete PWM_VAR['clientSettingCache'][keyName][localeKey][rowName];
    ChallengeSettingHandler.editLocale(keyName, localeKey);
};

ChallengeSettingHandler.addRow = function(keyName, localeKey) {
    PWM_VAR['clientSettingCache'][keyName][localeKey].push(ChallengeSettingHandler.defaultItem);
    ChallengeSettingHandler.write(keyName);
    ChallengeSettingHandler.editLocale(keyName, localeKey);
};

ChallengeSettingHandler.write = function(keyName, nextFunction) {
    PWM_CFGEDIT.writeSetting(keyName, PWM_VAR['clientSettingCache'][keyName], nextFunction);
};

// -------------------------- user permission handler ------------------------------------

var UserPermissionHandler = {};
UserPermissionHandler.defaultFilterValue = {type:'ldapFilter',ldapQuery:"(objectClass=*)",ldapBase:""};
UserPermissionHandler.defaultGroupValue = {type:'ldapGroup',ldapBase:"cn=exampleGroup,ou=container,o=organization"};

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
            htmlBody += '<div class="setting_item_value_wrapper" style="float:left; width: 570px;"><div style="width:100%; text-align:center">';
            if (resultValue[rowKey]['type'] == 'ldapGroup') {
                htmlBody += 'LDAP Group';
            } else {
                htmlBody += 'LDAP Filter';
            }

            var currentProfileValue = ('ldapProfileID' in resultValue[rowKey]) ? resultValue[rowKey]['ldapProfileID'] : "";
            htmlBody += '</div><table class="noborder">'
            + '<td style="width:200px" id="' + inputID + '_profileHeader' + '">' + PWM_CONFIG.showString('Setting_Permission_Profile') + '</td>'
            + '<td><input style="width: 200px;" class="configStringInput" id="' + inputID + '-profile" list="' + inputID + '-datalist" value="' +  currentProfileValue + '"/>'
            + '<datalist id="' + inputID + '-datalist"/></td>'
            + '</tr>';

            if (resultValue[rowKey]['type'] != 'ldapGroup') {
                var currentQueryValue = ('ldapQuery' in resultValue[rowKey]) ? resultValue[rowKey]['ldapQuery'] : "";
                htmlBody += '<tr>'
                + '<td><span id="' + inputID + '_FilterHeader' + '">' + PWM_CONFIG.showString('Setting_Permission_Filter') + '</span></td>'
                + '<td><input style="width: 420px;" class="configStringInput" id="' + inputID + '-query" value="' + currentQueryValue + '"></input></td>'
                + '</tr>';
            }

            var currentBaseValue = ('ldapBase' in resultValue[rowKey]) ? resultValue[rowKey]['ldapBase'] : "";
            htmlBody += '<tr>'
            + '<td><span id="' + inputID + '_BaseHeader' + '">'
            + PWM_CONFIG.showString((resultValue[rowKey]['type'] == 'ldapGroup') ?  'Setting_Permission_Base_Group' : 'Setting_Permission_Base')
            + '</span></td>'

            + '<td><input style="width: 420px;" class="configStringInput" id="' + inputID + '-base" value="' + currentBaseValue + '"></input></td>'
            + '</td>'
            + '</tr>';


            htmlBody += '</table></div><div id="button-' + inputID + '-deleteRow" style="float:right" class="delete-row-icon action-icon fa fa-times"></div>';
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
                for (var i in PWM_VAR['ldapProfileIds']) {
                    profileSelectElement.appendChild(new Option(PWM_VAR['ldapProfileIds'][i]));
                }

                PWM_MAIN.addEventHandler(inputID + '-profile','input',function(){
                    console.log('write');
                    PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapProfileID'] = this.value;
                    UserPermissionHandler.write(keyName);
                });

                if (resultValue[rowKey]['type'] != 'ldapGroup') {
                    var queryInput = PWM_MAIN.getObject(inputID + "-query");
                    queryInput.disabled = false;
                    queryInput.required = true;

                    PWM_MAIN.addEventHandler(inputID + "-query",'input',function(){
                        PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapQuery'] = this.value;
                        UserPermissionHandler.write(keyName);
                    });
                }

                var queryInput = PWM_MAIN.getObject(inputID + "-base");
                queryInput.disabled = false;
                queryInput.required = true;

                PWM_MAIN.addEventHandler(inputID + "-base",'input',function(){
                    PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapBase'] = this.value;
                    UserPermissionHandler.write(keyName);
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
        + '<span class="btn-icon fa fa-plus-square"></span>Add Filter</button>';

    var hideGroup = 'hideGroups' in options && options['hideGroups'] == "true";
    if (!hideGroup) {
        buttonRowHtml += '<button class="btn" id="button-' + keyName + '-addGroupValue">'
        + '<span class="btn-icon fa fa-plus-square"></span>Add Group</button>';
    }

    var hideMatch = 'hideMatch' in options && options['hideMatch'] == "true";
    if (!hideMatch) {
        buttonRowHtml += '<button id="button-' + keyName + '-viewMatches" class="btn">'
        + '<span class="btn-icon fa fa-eye"></span>View Matches</button>';
    }

    parentDivElement.innerHTML = parentDivElement.innerHTML + buttonRowHtml;

    PWM_MAIN.addEventHandler('button-' + keyName + '-viewMatches','click',function(){
        PWM_CFGEDIT.executeSettingFunction(keyName,'password.pwm.config.function.UserMatchViewerFunction')
    });

    PWM_MAIN.addEventHandler('button-' + keyName + '-addFilterValue','click',function(){
        PWM_VAR['clientSettingCache'][keyName].push(UserPermissionHandler.defaultFilterValue);
        UserPermissionHandler.write(keyName, true);
    });

    PWM_MAIN.addEventHandler('button-' + keyName + '-addGroupValue','click',function(){
        PWM_VAR['clientSettingCache'][keyName].push(UserPermissionHandler.defaultGroupValue);
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
    var options = PWM_SETTINGS['settings'][settingKey]['options'];
    var pattern = PWM_SETTINGS['settings'][settingKey]['pattern'];
    var min = 'min' in options ? parseInt(options['min']) : defaultMin;
    var max = 'max' in options ? parseInt(options['max']) : defaultMax;

    var htmlBody = '<input type="number" id="value_' + settingKey + '" class="configNumericInput" min="'+min+'" max="'+max+'"/>';
    if (type == 'number') {
        htmlBody += '<span class="configNumericLimits">' + min + ' - ' + max + '</span>';
    } else if (type == 'duration') {
        htmlBody +=  '<span class="configNumericLimits">' + PWM_MAIN.showString('Display_Seconds')  + '</span>'
        htmlBody +=  '<span style="margin-left:10px" id="display-' + settingKey + '-duration"></span>';
    }

    parentDivElement.innerHTML = htmlBody;

    PWM_CFGEDIT.readSetting(settingKey,function(data){
        PWM_MAIN.getObject('value_' + settingKey).value = data;
        PWM_VAR['clientSettingCache'][settingKey] = data;
        PWM_MAIN.addEventHandler('value_' + settingKey,'input',function(){
            var value = PWM_MAIN.getObject('value_' + settingKey).value;
            var valid = value.match(/[0-9]+/);
            if (valid) {
                console.log('valid');
                PWM_VAR['clientSettingCache'][settingKey] = data;
                PWM_CFGEDIT.writeSetting(settingKey, value);
                NumericValueHandler.updateDurationDisplay(settingKey,value);
            } else {
                console.log('invalid');
                PWM_MAIN.getObject('value_' + settingKey).value = PWM_VAR['clientSettingCache'][settingKey];
            }
        });

        PWM_MAIN.addEventHandler('value_' + settingKey,'mousewheel',function(e){ e.blur(); });
        NumericValueHandler.updateDurationDisplay(settingKey,data);
    });
};

NumericValueHandler.updateDurationDisplay = function(settingKey, numberValue) {
    var displayElement = PWM_MAIN.getObject('display-' + settingKey + '-duration');
    if (displayElement) {
        displayElement.innerHTML = (numberValue && numberValue != 0)
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
            bodyHtml += '<td id="button-' + inputID + '" style="border-width:0; width: 15px"><span class="fa fa-edit"/></ta>';
            bodyHtml += '<td style=""><div class="configStringPanel" id="panel-' + inputID + '"></div></td>';
            if (!settingData['required']) {
                bodyHtml += '<td style="border-width: 0"><span id="button-' + inputID + '-delete" class="delete-row-icon action-icon fa fa-times"></span></td>';
            }

            bodyHtml += '</table>';
        } else {
            bodyHtml += '<button class="btn" id="button-add-' + inputID + '">';
            bodyHtml += '<span class="btn-icon fa fa-plus-square"></span>Add Value';
            bodyHtml += '</button>';
        }

        parentDivElement.innerHTML = bodyHtml;
        UILibrary.addTextValueToElement('panel-' + inputID, value);

        PWM_MAIN.addEventHandler('button-' + inputID + '-delete','click',function(){
            PWM_CFGEDIT.writeSetting(settingKey,'',function(){
                StringValueHandler.init(settingKey);
            });
        });

        var editor = function(){
            UILibrary.stringEditorDialog({
                title:'Edit Value - ' + settingData['label'],
                textarea:('TEXT_AREA' == settingData['syntax']),
                regex:'pattern' in settingData ? settingData['pattern'] : '.+',
                placeholder:settingData['placeholder'],
                value:value,
                completeFunction:function(value){
                    PWM_CFGEDIT.writeSetting(settingKey,value,function(){
                        StringValueHandler.init(settingKey);
                    });
                }
            });
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

    var htmlBody = '<select id="setting_' + settingKey + '" disabled="true">'
        + '<option value="' + PWM_MAIN.showString('Display_PleaseWait') + '">' + PWM_MAIN.showString('Display_PleaseWait') + '</option></select>';
    parentDivElement.innerHTML = htmlBody;

    PWM_MAIN.clearDijitWidget("value_" + settingKey);
    PWM_MAIN.clearDijitWidget("setting_" + settingKey);

    PWM_MAIN.addEventHandler('setting_' + settingKey,'change',function(){
        var settingElement = PWM_MAIN.getObject('setting_' + settingKey);
        var selectedValue = settingElement.options[settingElement.selectedIndex].value;
        PWM_CFGEDIT.writeSetting(settingKey,selectedValue)
    });
    PWM_CFGEDIT.readSetting(settingKey, function(dataValue) {
        var settingElement = PWM_MAIN.getObject('setting_' + settingKey);

        var optionsHtml = '';
        var options = PWM_SETTINGS['settings'][settingKey]['options'];
        for (var option in options) {
            var optionValue = options[option];
            optionsHtml += '<option value="' + option + '">' + optionValue + '</option>'
        }
        settingElement.innerHTML = optionsHtml;
        settingElement.value = dataValue;
        settingElement.disabled = false;
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

X509CertificateHandler.draw = function(keyName) {
    var parentDiv = 'table_setting_' + keyName;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var resultValue = PWM_VAR['clientSettingCache'][keyName];

    var htmlBody = '<div style="max-height: 300px; overflow-y: auto">';
    for (var certCounter in resultValue) {
        (function (counter) {
            var certificate = resultValue[counter];
            htmlBody += '<div style="max-width:100%; margin-bottom:8px"><table style="max-width:100%" id="table_certificate' + keyName + '-' + counter + '">';
            htmlBody += '<tr><td colspan="2" class="key" style="text-align: center">Certificate ' + counter + '  <a id="certTimestamp-detail-' + keyName + '-' + counter + '">(detail)</a></td></tr>';
            htmlBody += '<tr><td>Subject</td><td><div class="setting_table_value">' + certificate['subject'] + '</div></td></tr>';
            htmlBody += '<tr><td>Issuer</td><td><div class="setting_table_value">' + certificate['issuer'] + '</div></td></tr>';
            htmlBody += '<tr><td>Serial</td><td><div class="setting_table_value">' + certificate['serial'] + '</div></td></tr>';
            htmlBody += '<tr><td>Issue Date</td><td id="certTimestamp-issue-' + keyName + '-' + counter + '" class="setting_table_value timestamp">' + certificate['issueDate'] + '</td></tr>';
            htmlBody += '<tr><td>Expire Date</td><td id="certTimestamp-expire-' + keyName + '-' + counter + '" class="setting_table_value timestamp">' + certificate['expireDate'] + '</td></tr>';
            htmlBody += '<tr><td>MD5 Hash</td><td><div class="setting_table_value">' + certificate['md5Hash'] + '</div></td></tr>';
            htmlBody += '<tr><td>SHA1 Hash</td><td><div class="setting_table_value">' + certificate['sha1Hash'] + '</div></td></tr>';
            htmlBody += '<tr><td>SHA512 Hash</td><td><div class="setting_table_value">' + certificate['sha512Hash'] + '</div></td></tr>';
            htmlBody += '</table></div>'
        })(certCounter);
    }
    htmlBody += '</div>';

    if (!PWM_MAIN.isEmpty(resultValue)) {
        htmlBody += '<button id="' + keyName + '_ClearButton" class="btn"><span class="btn-icon fa fa-times"></span>Clear</button>'
    } else {
        htmlBody += 'No certificates stored.<br/><br/>'
    }
    htmlBody += '<button id="' + keyName + '_AutoImportButton" class="btn"><span class="btn-icon fa fa-download"></span>Import From Server</button>'
    parentDivElement.innerHTML = htmlBody;

    for (certCounter in resultValue) {
        (function (counter) {
            PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('certTimestamp-issue-' + keyName + '-' + counter));
            PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('certTimestamp-expire-' + keyName + '-' + counter));
            PWM_MAIN.addEventHandler('certTimestamp-detail-' + keyName + '-' + counter,'click',function(){
                PWM_MAIN.showDialog({
                    title: 'Detail - ' + PWM_SETTINGS['settings'][keyName]['label'] + ' - Certificate ' + counter,
                    text: '<pre>' + resultValue[counter]['detail'] + '</pre>',
                    dialogClass: 'wide',
                    showClose: true
                });
            });
        })(certCounter);
    }

    if (!PWM_MAIN.isEmpty(resultValue)) {
        PWM_MAIN.addEventHandler(keyName + '_ClearButton','click',function(){
            handleResetClick(keyName);
        });
    }
    PWM_MAIN.addEventHandler(keyName + '_AutoImportButton','click',function(){
        switch (keyName) {
            case 'ldap.serverCerts':
                PWM_CFGEDIT.executeSettingFunction(keyName,'password.pwm.config.function.LdapCertImportFunction');
                break;
            case 'audit.syslog.certificates':
                PWM_CFGEDIT.executeSettingFunction(keyName,'password.pwm.config.function.SyslogCertImportFunction');
                break;

            default:
                alert('unhandled cert-import request for key=' + keyName);
        }
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
    var parentDiv = 'table_setting_' + settingKey;
    var parentDivElement = PWM_MAIN.getObject(parentDiv);

    var htmlBody = '<table class="">';
    for (var method in PWM_SETTINGS['verificationMethods']) {
        var id = settingKey + '-' + method;
        var label = PWM_SETTINGS['verificationMethods'][method];
        htmlBody += '<tr><td>' + label + '</td><td><input id="input-range-' + id + '" type="range" min="0" max="2" value="0"/></td>';
        htmlBody += '<td><span id="label-' + id +'"></span></td></tr>';
    }
    htmlBody += '</table>';
    htmlBody += '<br/><label>Minimum Optional Required <input style="width:30px;" id="input-minOptional-' + settingKey + '" type="number" value="0" class="configNumericInput""></label>';
    parentDivElement.innerHTML = htmlBody;
    for (var method in PWM_SETTINGS['verificationMethods']) {
        var id = settingKey + '-' + method;
        PWM_MAIN.addEventHandler('input-range-' + id,'change',function(){
            VerificationMethodHandler.updateLabels(settingKey);
            VerificationMethodHandler.write(settingKey);
        });
        var enabledState = PWM_VAR['clientSettingCache'][settingKey]['methodSettings'][method]['enabledState'];
        var numberValue = 0;
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
                alert('unknown value = VerificationMethodHandler.draw');
        }
        PWM_MAIN.getObject('input-range-' + id).value = numberValue;
    }
    PWM_MAIN.getObject('input-minOptional-' + settingKey).value = PWM_VAR['clientSettingCache'][settingKey]['minOptionalRequired'];
    PWM_MAIN.addEventHandler('input-minOptional-' + settingKey,'input',function(){
        VerificationMethodHandler.updateLabels(settingKey);
        VerificationMethodHandler.write(settingKey);
    });

    VerificationMethodHandler.updateLabels(settingKey);
};

VerificationMethodHandler.write = function(settingKey) {
    var values = {};
    values['minOptionalRequired'] = Number(PWM_MAIN.getObject('input-minOptional-' + settingKey).value);
    values['methodSettings'] = {};
    for (var method in PWM_SETTINGS['verificationMethods']) {
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
    var optionalCount = 0;
    for (var method in PWM_SETTINGS['verificationMethods']) {
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
    var minOptionalInput = PWM_MAIN.getObject('input-minOptional-' + settingKey);
    minOptionalInput.max = optionalCount;
    var currentMax = Number(minOptionalInput.value);
    if (currentMax > optionalCount) {
        minOptionalInput.value = optionalCount.toString();
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

    if (PWM_MAIN.isEmpty(resultValue)) {
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

    if (PWM_MAIN.isEmpty(resultValue)) {
        htmlBody += '<button class="btn" id="button-uploadFile-' + keyName + '"><span class="btn-icon fa fa-upload"></span>Upload File</button>';
    } else {
        htmlBody += '<button class="btn" id="button-removeFile-' + keyName + '"><span class="btn-icon fa fa-trash-o"></span>Remove File</button>';
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
    options['url'] = "ConfigEditor?processAction=uploadFile&key=" + keyName;
    options['nextFunction'] = function() {
        PWM_MAIN.showWaitDialog({loadFunction:function(){
            FileValueHandler.init(keyName);
            PWM_MAIN.closeWaitDialog();
        }});
    };
    PWM_CONFIG.uploadFile(options);
};

// -------------------------- common elements handler ------------------------------------


var UILibrary = {};
UILibrary.stringEditorDialog = function(options){
    options = options === undefined ? {} : options;
    var title = 'title' in options ? options['title'] : 'Edit Value';
    var completeFunction = 'completeFunction' in options ? options['completeFunction'] : function() {alert('no string editor dialog complete function')};
    var regexString = 'regex' in options && options['regex'] ? options['regex'] : '.+';
    var initialValue = 'value' in options ? options['value'] : '';
    var placeholder = 'placeholder' in options ? options['placeholder'] : '';
    var textarea = 'textarea' in options ? options['textarea'] : false;

    var regexObject = new RegExp(regexString);
    var text = '';
    text += '<div style="visibility: hidden;" id="panel-valueWarning"><span class="fa fa-warning message-error"></span>&nbsp;' + PWM_CONFIG.showString('Warning_ValueIncorrectFormat') + '</div>';
    text += '<br/>';

    if (textarea) {
        text += '<textarea style="max-width: 480px; width: 480px; height:300px; max-height:300px; overflow-y: auto" class="configStringInput" autofocus required id="addValueDialog_input"></textarea>';
    } else {
        text += '<input style="width: 480px" class="configStringInput" autofocus required id="addValueDialog_input"/>';
    }

    var inputFunction = function() {
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        PWM_MAIN.getObject('panel-valueWarning').style.visibility = 'hidden';

        var value = PWM_MAIN.getObject('addValueDialog_input').value;
        if (value.length > 0) {
            var passedValidation = regexObject  != null && regexObject.test(value);
            if (passedValidation) {
                PWM_MAIN.getObject('dialog_ok_button').disabled = false;
                PWM_VAR['temp-dialogInputValue'] = PWM_MAIN.getObject('addValueDialog_input').value;
            } else {
                PWM_MAIN.getObject('panel-valueWarning').style.visibility = 'visible';
            }
        }
    };

    var okFunction = function() {
        var value = PWM_VAR['temp-dialogInputValue'];
        completeFunction(value);
    };

    PWM_MAIN.showDialog({
        title:title,
        text:text,
        okAction:okFunction,
        showCancel:true,
        showClose: true,
        allowMove: true,
        loadFunction:function(){
            PWM_MAIN.getObject('addValueDialog_input').value = initialValue;
            if (regexString && regexString.length > 1) {
                PWM_MAIN.getObject('addValueDialog_input').setAttribute('pattern',regexString);
            }
            if (placeholder && placeholder.length > 1) {
                PWM_MAIN.getObject('addValueDialog_input').setAttribute('placeholder',placeholder);
            }
            inputFunction();
            PWM_MAIN.addEventHandler('addValueDialog_input','input',function(){
                inputFunction();
            });
        }
    });
};

UILibrary.addTextValueToElement = function(elementID, input) {
    var element = PWM_MAIN.getObject(elementID);
    if (element) {
        element.innerHTML = '';
        element.appendChild(document.createTextNode(input));
    }
};

UILibrary.addAddLocaleButtonRow = function(parentDiv, keyName, addFunction, existingLocales) {
    var availableLocales = PWM_GLOBAL['localeInfo'];

    var tableRowElement = document.createElement('tr');
    tableRowElement.setAttribute("style","border-width: 0");

    var bodyHtml = '';
    bodyHtml += '<td style="border-width: 0" colspan="5">';
    bodyHtml += '<select id="' + keyName + '-addLocaleValue">';

    var localesAdded = 0;
    for (var localeIter in availableLocales) {
        if (localeIter != PWM_GLOBAL['defaultLocale']) {
            if (!existingLocales || (existingLocales.indexOf(localeIter) == -1)) {
                localesAdded++;
                var labelText = availableLocales[localeIter] + " (" + localeIter + ")";
                bodyHtml += '<option value="' + localeIter + '">' + labelText + '</option>';
            }
        }
    }
    bodyHtml += '</select>';

    bodyHtml += '<button type="button" class="btn" id="' + keyName + '-addLocaleButton"><span class="btn-icon fa fa-plus-square"></span>Add Locale</button>'

    bodyHtml += '</td>';
    if (localesAdded == 0) {
        bodyHtml = '<td style="border-width: 0" colspan="5"><span class="footnote">All locales present</span></td>';
    }
    tableRowElement.innerHTML = bodyHtml;
    PWM_MAIN.getObject(parentDiv).appendChild(tableRowElement);

    PWM_MAIN.addEventHandler(keyName + '-addLocaleButton','click',function(){
        var value = PWM_MAIN.getObject(keyName + "-addLocaleValue").value;
        addFunction(value);
    });
};
