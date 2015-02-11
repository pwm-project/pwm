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

LocalizedStringValueHandler.init = function(keyName, regExPattern, syntax) {
    console.log('LocalizedStringValueHandler init for ' + keyName);

    var parentDiv = 'table_setting_' + keyName;
    PWM_MAIN.getObject(parentDiv).innerHTML = '<table id="tableTop_' + keyName + '" style="border-width:0">';
    parentDiv = PWM_MAIN.getObject('tableTop_' + keyName);

    PWM_VAR['clientSettingCache'][keyName + "_regExPattern"] = regExPattern;
    PWM_VAR['clientSettingCache'][keyName + "_syntax"] = syntax;
    PWM_VAR['clientSettingCache'][keyName + "_parentDiv"] = parentDiv;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        LocalizedStringValueHandler.draw(keyName);
    });
};

LocalizedStringValueHandler.draw = function(keyName) {
    var parentDiv = PWM_VAR['clientSettingCache'][keyName + "_parentDiv"];
    var regExPattern = PWM_VAR['clientSettingCache'][keyName + "_regExPattern"];
    var syntax = PWM_VAR['clientSettingCache'][keyName + "_syntax"];

    require(["dojo/parser","dijit/form/Button","dijit/form/Textarea","dijit/form/ValidationTextBox"],function(dojoParser){
        var resultValue = PWM_VAR['clientSettingCache'][keyName];
        PWM_CFGEDIT.clearDivElements(parentDiv, false);
        for (var i in resultValue) {
            LocalizedStringValueHandler.addLocaleTableRow(parentDiv, keyName, i, resultValue[i], regExPattern, syntax)
        }
        PWM_CFGEDIT.addAddLocaleButtonRow(parentDiv, keyName, function(localeKey) {
            LocalizedStringValueHandler.addLocaleSetting(keyName, localeKey);
        });

        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        dojoParser.parse(parentDiv);
    });
};

LocalizedStringValueHandler.addLocaleTableRow = function(parentDiv, settingKey, localeString, value, regExPattern, syntax) {
    var inputID = 'value-' + settingKey + '-' + localeString;

    // clear the old dijit node (if it exists)
    PWM_MAIN.clearDijitWidget(inputID);

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");


    var td1 = document.createElement("td");
    td1.setAttribute("style", "border-width: 0; width: 15px");

    if (localeString == null || localeString.length < 1) {
        td1.innerHTML = "";
    } else {
        td1.innerHTML = localeString;
    }
    newTableRow.appendChild(td1);


    var td2 = document.createElement("td");
    td2.setAttribute("style", "border-width: 0");
    if (syntax == 'LOCALIZED_TEXT_AREA') {
        var textAreaElement = document.createElement("textarea");
        textAreaElement.setAttribute("id", inputID);
        textAreaElement.setAttribute("value", PWM_MAIN.showString('Display_PleaseWait'));
        textAreaElement.setAttribute("onchange", "LocalizedStringValueHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
        textAreaElement.setAttribute("style", "width: 510px; max-width:510px; max-height: 300px; overflow: auto; white-space: nowrap");
        textAreaElement.setAttribute("data-dojo-type", "dijit.form.Textarea");
        textAreaElement.setAttribute("value", value);
        td2.appendChild(textAreaElement);
    } else {
        var inputElement = document.createElement("input");
        inputElement.setAttribute("id", inputID);
        inputElement.setAttribute("value", PWM_MAIN.showString('Display_PleaseWait'));
        inputElement.setAttribute("onchange", "LocalizedStringValueHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
        inputElement.setAttribute("style", "width: 510px;");
        inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
        inputElement.setAttribute("regExp", regExPattern);
        inputElement.setAttribute("value", value);
        td2.appendChild(inputElement);
    }
    newTableRow.appendChild(td2);

    if (localeString != null && localeString.length > 0) {
        var imgElement = document.createElement("div");
        imgElement.setAttribute("style", "width: 10px; height: 10px;");
        imgElement.setAttribute("class", "delete-row-icon action-icon fa fa-times");
        imgElement.setAttribute("id", "button-" + settingKey + '-' + localeString + "-deleteRow");
        imgElement.setAttribute("onclick", "LocalizedStringValueHandler.removeLocaleSetting('" + settingKey + "','" + localeString + "','" + parentDiv + "','" + regExPattern + "','" + syntax + "')");
        td2.appendChild(imgElement);
    }

    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    PWM_MAIN.addEventHandler("button-" + settingKey + '-' + localeString + "-deleteRow","click",function(){
        LocalizedStringValueHandler.removeLocaleSetting(settingKey, localeString);
    });
};

LocalizedStringValueHandler.writeLocaleSetting = function(settingKey, locale, value) {
    var existingValues = PWM_VAR['clientSettingCache'][settingKey];
    var currentValues = { };
    for (var i in existingValues) {
        var inputID = 'value-' + settingKey + '-' + i;
        currentValues[i] = PWM_MAIN.getObject(inputID).value;
    }
    if (value == null) {
        delete currentValues[locale];
    } else {
        currentValues[locale] = value;
    }
    PWM_CFGEDIT.writeSetting(settingKey, currentValues);
    PWM_VAR['clientSettingCache'][settingKey] = currentValues;
};

LocalizedStringValueHandler.removeLocaleSetting = function(keyName, locale, parentDiv, regExPattern, syntax) {
    LocalizedStringValueHandler.writeLocaleSetting(keyName, locale, null);
    LocalizedStringValueHandler.draw(keyName);
};

LocalizedStringValueHandler.addLocaleSetting = function(keyName, inputValue) {
    try {
        var existingElementForLocale = PWM_MAIN.getObject('value-' + keyName + '-' + inputValue);
        if (existingElementForLocale == null) {
            PWM_VAR['clientSettingCache'][keyName][inputValue] = [];
            PWM_CFGEDIT.writeSetting(keyName, PWM_VAR['clientSettingCache'][keyName]);
            //LocalizedStringValueHandler.writeLocaleSetting(keyName, inputValue, '');
            LocalizedStringValueHandler.draw(keyName);
        }
    } finally {
    }
};




// -------------------------- string array value handler ------------------------------------

var StringArrayValueHandler = {};

StringArrayValueHandler.init = function(keyName) {
    console.log('StringArrayValueHandler init for ' + keyName);

    var parentDiv = 'table_setting_' + keyName;
    PWM_MAIN.getObject(parentDiv).innerHTML = '<table id="tableTop_' + keyName + '" style="border-width:0">';
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
    for (var i in resultValue) {
        (function(iteration) {
            StringArrayValueHandler.drawRow(settingKey, iteration, resultValue[iteration], itemCount, tableElement);
            counter++;
        })(i);
    }
    parentDivElement.appendChild(tableElement);

    var addItemButton = document.createElement("button");
    addItemButton.setAttribute("type", "button");
    addItemButton.setAttribute("class","btn");
    addItemButton.setAttribute("id","button-" + settingKey + "-addItem");
    addItemButton.innerHTML = '<span class="btn-icon fa fa-plus-square"></span>' + (syntax == 'PROFILE' ? "Add Profile" : "Add Value");
    parentDivElement.appendChild(addItemButton);

    require(["dojo/parser","dijit/form/Button","dijit/form/ValidationTextBox"],function(dojoParser){
        dojoParser.parse(parentDiv);
        PWM_MAIN.addEventHandler('button-' + settingKey + '-addItem','click',function(){
            StringArrayValueHandler.valueHandler(settingKey,-1);
        });
    });
};

StringArrayValueHandler.drawRow = function(settingKey, iteration, value, itemCount, parentDivElement) {
    var settingInfo = PWM_SETTINGS['settings'][settingKey];
    var syntax = settingInfo['syntax'];

    var inputID = 'value-' + settingKey + '-' + iteration;

    // clear the old dijit node (if it exists)
    PWM_MAIN.clearDijitWidget(inputID);

    var valueRow = document.createElement("tr");
    valueRow.setAttribute("style", "border-width: 0");
    valueRow.setAttribute("id",inputID + "_row");

    var rowHtml = '<td style=""><div style="width:500px; overflow:hidden; text-overflow: ellipsis" id="' + inputID + '">' + value + '</div></td>';

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

    setTimeout(function(){
        if (syntax != 'PROFILE') {
            PWM_MAIN.addEventHandler(inputID,'click',function(){
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
    },100);
};

StringArrayValueHandler.valueHandler = function(settingKey, iteration) {
    var text = '';
    //text += '<div>' + PWM_SETTINGS['settings'][settingKey]['description'] + '</div><hr/>';
    text += '<input style="width: 500px" required="required" id="addValueDialog_input"/>';

    var changeFunction = function() {
        PWM_VAR['addDialog_value'] = this.value;
        PWM_MAIN.getObject('dialog_ok_button').disabled = !this.validate();
    };

    var loadFunction = function() {
        var value = iteration > -1 ? PWM_VAR['clientSettingCache'][settingKey][iteration] : '';
        PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        require(["dijit/form/ValidationTextBox"],function(ValidationTextBox) {
            new ValidationTextBox({
                id:"addValueDialog_input",
                regExp: PWM_SETTINGS['settings'][settingKey]['pattern'],
                style: 'width: 500px',
                required: true,
                invalidMessage: 'The value does not have the correct format',
                value: value,
                onChange: changeFunction,
                onKeyUp: changeFunction
            },"addValueDialog_input");
        });
    };

    var okAction = function() {
        var value = PWM_VAR['addDialog_value'];
        if (iteration > -1) {
            PWM_VAR['clientSettingCache'][settingKey][iteration] = value;
        } else {
            PWM_VAR['clientSettingCache'][settingKey].push(value);
        }
        StringArrayValueHandler.writeSetting(settingKey)
    };

    PWM_MAIN.showDialog({
        title:PWM_SETTINGS['settings'][settingKey]['label'] + " - " + (iteration > -1 ? "Edit" : "Add") + " Value",
        text:text,
        loadFunction:loadFunction,
        okAction:okAction,
        showCancel:true,
        showClose: true,
        allowMove: true
    });
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

        var addLocaleFunction = function() {
            require(["dijit/registry"],function(registry){
                MultiLocaleTableHandler.writeMultiLocaleSetting(keyName, registry.byId(keyName + "-addLocaleValue").value, 0, '', regExPattern);
            });
        };

        PWM_CFGEDIT.addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction);
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

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    newTableRow.setAttribute("colspan", "5");

    var newTableData = document.createElement("td");
    newTableData.setAttribute("style", "border-width: 0;");

    var addItemButton = document.createElement("button");
    addItemButton.setAttribute("type", "button");
    addItemButton.setAttribute("class", "btn");
    addItemButton.setAttribute("onclick", "FormTableHandler.addMultiSetting('" + keyName + "','" + parentDiv + "');");
    addItemButton.innerHTML = '<span class="btn-icon fa fa-plus-square"></span>Add Form Item';
    newTableData.appendChild(addItemButton);

    newTableRow.appendChild(newTableData);
    parentDivElement.appendChild(newTableRow);

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

        htmlRow += '<td><span class="delete-row-icon action-icon fa fa-times" id="' + inputID + 'deleteRowButton"></span></td>';

        newTableRow.innerHTML = htmlRow;

        var parentDivElement = PWM_MAIN.getObject(parentDiv);
        parentDivElement.appendChild(newTableRow);

        PWM_MAIN.addEventHandler(inputID + "-moveUp", 'click', function () {
            FormTableHandler.move(settingKey, true, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "-moveDown", 'click', function () {
            FormTableHandler.move(settingKey, false, iteration);
        });
        PWM_MAIN.addEventHandler(inputID + "deleteRowButton", 'click', function () {
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

FormTableHandler.writeFormSetting = function(settingKey) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting);
};

FormTableHandler.removeRow = function(keyName, iteration) {
    PWM_MAIN.showConfirmDialog({
        text:'Are you sure you wish to delete this item?',
        okAction:function(){
            var currentValues = PWM_VAR['clientSettingCache'][keyName];
            currentValues.splice(iteration,1);
            FormTableHandler.writeFormSetting(keyName);
            FormTableHandler.redraw(keyName);
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

FormTableHandler.newRowValue = {
    name:'',
    minimumLength:0,
    maximumLength:255,
    labels:{'':''},
    regexErrors:{'':''},
    selectOptions:{},
    description:{'':''}
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
        var newRowValue = FormTableHandler.newRowValue;
        newRowValue['name'] = PWM_VAR['newFormFieldName'];
        PWM_VAR['clientSettingCache'][keyName].push(newRowValue);
        FormTableHandler.writeFormSetting(keyName);
        FormTableHandler.redraw(keyName)
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
                text: 'Detailed description of this form item, including any special instructions.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-required',
                text: 'Marks the field as required.  The user must supply a value before being able to complete the form.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-confirm',
                text: 'Adds a duplicate field to the form and requires the value be the same for the original and confirmation field.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-readOnly',
                text: 'Make the field unmodifiable.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-unique',
                text: 'Indicate that the field value must be unique in the directory before proceeding.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-regex',
                text: 'Apply a <i>regular expression</i> pattern to the value.  The value must match the pattern before the form is completed.  This pattern can be used to constrain the permitted syntax of the value.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-regexError',
                text: 'Error message to show when the regular expression pattern is not matched.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-placeholder',
                text: 'Placeholder text to display in the form field with the field is not populated with a value.'
            });
            PWM_MAIN.showTooltip({
                id: inputID + '-label-js',
                text: 'Javascript to be added to the browser.'
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
            bodyText += '<td><input style="width:445px" class="configStringInput" type="text" value="' + value + '" id="' + localeID + '-input"></input></td>';
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
                        PWM_MAIN.addEventHandler(localeID + 'removeLocaleButton', 'click', function () {
                            delete PWM_VAR['clientSettingCache'][keyName][iteration]['labels'][localeName];
                            FormTableHandler.writeFormSetting(keyName);
                            FormTableHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                        });
                    }(iter));
                }
                PWM_CFGEDIT.addAddLocaleButtonRow(inputID + 'table', inputID, function(localeName){
                    PWM_VAR['clientSettingCache'][keyName][iteration][settingType][localeName] = '';
                    FormTableHandler.writeFormSetting(keyName);
                    FormTableHandler.multiLocaleStringDialog(keyName, iteration, settingType, finishAction, titleText);
                });
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
        PWM_VAR['clientSettingCache'][settingKey]['settings']['writeFunction'] = 'ChangePasswordHandler.doChange(\'' + settingKey + '\')';
    }
    PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = false;
    ChangePasswordHandler.clear(settingKey);
    ChangePasswordHandler.changePasswordPopup(settingKey);
};

ChangePasswordHandler.validatePasswordPopupFields = function() {
    require(["dojo","dijit/registry"],function(dojo,registry){
        var password1 = registry.byId('password1').get('value');
        var password2 = registry.byId('password2').get('value');

        var matchStatus = "";

        PWM_MAIN.getObject('password_button').disabled = true;
        if (password2.length > 0) {
            if (password1 == password2) {
                matchStatus = "MATCH";
                PWM_MAIN.getObject('password_button').disabled = false;
            } else {
                matchStatus = "NO_MATCH";
            }
        }

        ChangePasswordHandler.markConfirmationCheck(matchStatus);
    });
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

ChangePasswordHandler.doChange = function(settingKey) {
    var password1 = PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'];
    PWM_MAIN.clearDijitWidget('dialogPopup');
    PWM_CFGEDIT.writeSetting(settingKey,password1,function(){
        ChangePasswordHandler.clear(settingKey);
        ChangePasswordHandler.init(settingKey);
    });
};

ChangePasswordHandler.clear = function(settingKey) {
    PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'] = '';
    PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'] = '';
};

ChangePasswordHandler.generateRandom = function(settingKey) {
    ChangePasswordHandler.clear(settingKey);
    if (!PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields']) {
        PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = true;
        ChangePasswordHandler.changePasswordPopup(settingKey);
    }
    require(["dojo","dijit/registry"],function(dojo,registry){
        var charMap = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
        if (registry.byId('special').checked) {
            charMap += '~`!@#$%^&*()_-+=;:,.[]{}';
        }
        var length = registry.byId('randomLength').value;
        var postData = { };
        postData.maxLength = length;
        postData.minLength = length;
        postData.chars = charMap;
        postData.noUser = true;
        PWM_MAIN.getObject('generateButton').disabled = true;

        dojo.xhrPost({
            url:PWM_GLOBAL['url-restservice'] + "/randompassword",
            preventCache: true,
            headers: {"Accept":"application/json","X-RestClientKey":PWM_GLOBAL['restClientKey']},
            postData: postData,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                registry.byId('password1').set('value',data['data']['password']);
                registry.byId('password2').set('value','');
                PWM_MAIN.getObject('generateButton').disabled = false;
            },
            error: function(error) {
                PWM_MAIN.getObject('generateButton').disabled = false;
                alert('error reading random password: ' + error);
            }
        });
    });
};

ChangePasswordHandler.changePasswordPopup = function(settingKey) {
    var writeFunction = PWM_VAR['clientSettingCache'][settingKey]['settings']['writeFunction'];
    require(["dojo/parser","dijit/registry","dijit/Dialog","dijit/form/Textarea","dijit/form/TextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],
        function(dojoParser,registry,Dialog,Textarea,TextBox)
        {
            /*
             var bodyText = '<div id="changePasswordDialogDiv">'
             + '<span id="message" class="message message-info">' + PWM_VAR['clientSettingCache'][settingKey]['settings']['name'] + '</span><br/>'
             */
            var bodyText = '<table style="border: 0">'
                + '<tr style="border: 0"><td style="border: 0">' + PWM_MAIN.showString('Field_NewPassword') + '</td></tr>'
                + '<tr style="border: 0"><td style="border: 0">'
                + '<input name="password1" id="password1" class="inputfield" style="width: 500px; max-height: 200px; overflow: auto" autocomplete="off">' + '</input>'
                + '</td></tr><tr style="border:0"><td style="border:0">&nbsp;</td></tr>'
                + '<tr style="border: 0"><td style="border: 0">' + PWM_MAIN.showString('Field_ConfirmPassword') + '</span></td></tr>'
                + '<tr style="border: 0">'
                + '<td style="border: 0" xmlns="http://www.w3.org/1999/html"><input name="password2" id="password2" class="inputfield" style="width: 500px; max-height: 200px; overflow: auto;" autocomplete="off"/></input></td>'

                + '<td style="border: 0"><div style="margin:0;">'
                + '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/greenCheck.png">'
                + '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png">'
                + '</div></td>'

                + '</tr></table>'
                + '<button name="change" class="btn" id="password_button" onclick="' + writeFunction + '" disabled="true"/>'
                + '<span class="fa fa-forward btn-icon"></span>Store Password</button>&nbsp;&nbsp;'
                + '<button id="generateButton" name="generateButton" class="btn" onclick="ChangePasswordHandler.generateRandom(\'' + settingKey + '\')"><span class="fa fa-random btn-icon"></span>Random</button>'
                + '&nbsp;&nbsp;<input style="width:60px" data-dojo-props="constraints: { min:1, max:102400 }" data-dojo-type="dijit/form/NumberSpinner" id="randomLength" value="32"/>Length'
                + '&nbsp;&nbsp;<input type="checkbox" id="special" data-dojo-type="dijit/form/CheckBox" value="10"/>Special'
                + '&nbsp;&nbsp;<input type="checkbox" id="show" data-dojo-type="dijit/form/CheckBox" data-dojo-props="checked:' + PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] + '" value="10"/>Show'
                + '</div>';

            PWM_MAIN.showDialog({
                title: 'Store Password - ' + PWM_VAR['clientSettingCache'][settingKey]['settings']['name'],
                text: bodyText,
                showOk: false,
                showClose: true
            });

            registry.byId('show').set('onChange',function(){
                PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields'] = this.checked;
                ChangePasswordHandler.changePasswordPopup(settingKey);
            });

            dojoParser.parse(PWM_MAIN.getObject('changePasswordDialogDiv'));

            var p1 = PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'];
            var p2 = PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'];
            var p1Options = {
                id: 'password1',
                style: 'width: 100%',
                type: 'password',
                onKeyUp: function(){
                    PWM_VAR['clientSettingCache'][settingKey]['settings']['p1'] = this.get('value');
                    ChangePasswordHandler.validatePasswordPopupFields();
                    registry.byId('password2').set('value','')
                },
                value: p1
            };
            var p2Options = {
                id: 'password2',
                style: 'width: 100%',
                type: 'password',
                onKeyUp: function(){
                    PWM_VAR['clientSettingCache'][settingKey]['settings']['p2'] = this.get('value');
                    ChangePasswordHandler.validatePasswordPopupFields();
                },
                value: p2
            };
            if (PWM_VAR['clientSettingCache'][settingKey]['settings']['showFields']) {
                new Textarea(p1Options,'password1');
                new Textarea(p2Options,'password2');
            } else {
                new TextBox(p1Options,'password1');
                new TextBox(p2Options,'password2');
            }
            PWM_MAIN.getObject('password1').focus();
            ChangePasswordHandler.validatePasswordPopupFields();
        });
};



// -------------------------- action handler ------------------------------------

var ActionHandler = {};

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
    addItemButton.setAttribute("onclick", "ActionHandler.addMultiSetting('" + keyName + "','" + parentDiv + "');");
    addItemButton.setAttribute("class", "btn");
    addItemButton.innerHTML = '<span class="btn-icon fa fa-plus-square"></span>Add Value';
    newTableData.appendChild(addItemButton);

    newTableRow.appendChild(newTableData);
    parentDivElement.appendChild(newTableRow);

    require(["dojo/parser","dijit/form/Button","dijit/form/Select","dijit/form/Textarea"],function(dojoParser){
        dojoParser.parse(parentDiv);
    });
};

ActionHandler.drawRow = function(parentDiv, settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";

    // clear the old dijit node (if it exists)
    PWM_MAIN.clearDijitWidget(inputID + "name");
    PWM_MAIN.clearDijitWidget(inputID + "description");
    PWM_MAIN.clearDijitWidget(inputID + "type");
    PWM_MAIN.clearDijitWidget(inputID + "optionsButton");

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    {
        {
            var td1 = document.createElement("td");
            td1.setAttribute("style", "border-width: 0; width:50px");
            var nameInput = document.createElement("input");
            nameInput.setAttribute("id", inputID + "name");
            nameInput.setAttribute("value", value['name']);
            nameInput.setAttribute("onchange","PWM_VAR['clientSettingCache']['" + settingKey + "'][" + iteration + "]['name'] = this.value;ActionHandler.writeFormSetting('" + settingKey + "')");
            nameInput.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            nameInput.setAttribute("data-dojo-props", "required: true");

            td1.appendChild(nameInput);
            newTableRow.appendChild(td1);
        }

        {
            var td2 = document.createElement("td");
            td2.setAttribute("style", "border-width: 0");
            var descriptionInput = document.createElement("input");
            descriptionInput.setAttribute("id", inputID + "description");
            descriptionInput.setAttribute("value", value['description']);
            descriptionInput.setAttribute("onchange","PWM_VAR['clientSettingCache']['" + settingKey + "'][" + iteration + "]['description'] = this.value;ActionHandler.writeFormSetting('" + settingKey + "')");
            descriptionInput.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            td2.appendChild(descriptionInput);
            newTableRow.appendChild(td2);
        }

        {
            var td3 = document.createElement("td");
            td3.setAttribute("style", "border-width: 0");
            var optionList = PWM_GLOBAL['actionTypeOptions'];
            var typeSelect = document.createElement("select");
            typeSelect.setAttribute("data-dojo-type", "dijit.form.Select");
            typeSelect.setAttribute("id", inputID + "type");
            typeSelect.setAttribute("style","width: 90px");
            typeSelect.setAttribute("onchange","PWM_VAR['clientSettingCache']['" + settingKey + "'][" + iteration + "]['type'] = this.value;ActionHandler.writeFormSetting('" + settingKey + "')");
            for (var optionItem in optionList) {
                var optionElement = document.createElement("option");
                optionElement.value = optionList[optionItem];
                optionElement.innerHTML = optionList[optionItem];
                if (optionList[optionItem] == PWM_VAR['clientSettingCache'][settingKey][iteration]['type']) {
                    optionElement.setAttribute("selected","true");
                }
                typeSelect.appendChild(optionElement);
            }

            td3.appendChild(typeSelect);
            newTableRow.appendChild(td3);
        }

        {
            var td4 = document.createElement("td");
            td4.setAttribute("style", "border-width: 0");
            var labelButton = document.createElement("button");
            labelButton.setAttribute("id", inputID + "optionsButton");
            labelButton.setAttribute("data-dojo-type", "dijit.form.Button");
            labelButton.setAttribute("onclick","ActionHandler.showOptionsDialog('" + settingKey + "'," + iteration + ")");
            labelButton.innerHTML = "Options";
            td4.appendChild(labelButton);
            newTableRow.appendChild(td4);
        }

        var tdFinal = document.createElement("td");
        tdFinal.setAttribute("style", "border-width: 0");

        var imgElement = document.createElement("img");
        imgElement.setAttribute("style", "width: 10px; height: 10px");
        imgElement.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
        imgElement.setAttribute("onclick", "ActionHandler.removeMultiSetting('" + settingKey + "','" + iteration + "')");
        tdFinal.appendChild(imgElement);
        newTableRow.appendChild(tdFinal);
    }
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
};

ActionHandler.writeFormSetting = function(settingKey) {
    var cachedSetting = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, cachedSetting);
};

ActionHandler.removeRow = function(keyName, iteration) {
    delete PWM_VAR['clientSettingCache'][keyName][iteration];
    console.log("removed iteration " + iteration + " from " + keyName + ", cached keyValue=" + PWM_VAR['clientSettingCache'][keyName]);
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.redraw(keyName);
};

ActionHandler.addRow = function(keyName) {
    var currentSize = 0;
    for (var loopVar in PWM_VAR['clientSettingCache'][keyName]) {
        currentSize++;
    }
    PWM_VAR['clientSettingCache'][keyName][currentSize + 1] = {};
    PWM_VAR['clientSettingCache'][keyName][currentSize + 1]['name'] = '';
    PWM_VAR['clientSettingCache'][keyName][currentSize + 1]['description'] = '';
    PWM_VAR['clientSettingCache'][keyName][currentSize + 1]['type'] = 'webservice';
    PWM_VAR['clientSettingCache'][keyName][currentSize + 1]['method'] = 'get';
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.redraw(keyName)
};

ActionHandler.showOptionsDialog = function(keyName, iteration) {
    require(["dojo/store/Memory","dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/Select","dijit/form/ValidationTextBox"],function(Memory){
        var inputID = 'value_' + keyName + '_' + iteration + "_";
        var bodyText = '<table style="border:0">';
        if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'webservice') {
            bodyText += '<tr>';
            bodyText += '<td style="border:0; text-align: center" colspan="2">Web Service</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">&nbsp;</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">Method</td><td style="border:0;"><select id="' + inputID + 'method' + '"/></td>';
            bodyText += '</tr><tr>';
            //bodyText += '<td style="border:0; text-align: right">Client Side</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'clientSide' + '"/></td>';
            //bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">Headers</td><td style="border:0;"><button class="menubutton" onclick="ActionHandler.showHeadersDialog(\'' + keyName + '\',\'' + iteration + '\')">Edit</button></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">URL</td><td style="border:0;"><input type="text" id="' + inputID + 'url' + '"/></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">Body</td><td style="border:0;"><input type="text" id="' + inputID + 'body' + '"/></td>';
            bodyText += '</tr>';
        } else if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'ldap') {
            bodyText += '<tr>';
            bodyText += '<td style="border:0; text-align: center" colspan="2">LDAP Value Write</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">&nbsp;</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">Attribute Name</td><td style="border:0;"><input type="text" id="' + inputID + 'attributeName' + '"/></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">Attribute Value</td><td style="border:0;"><input type="text" id="' + inputID + 'attributeValue' + '"/></td>';
            bodyText += '</tr>';
        }
        bodyText += '<tr>';
        bodyText += '<td style="border:0; text-align: right">&nbsp;</td>';
        bodyText += '</tr><tr>';
        bodyText += '</tr>';
        bodyText += '</table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="PWM_MAIN.clearDijitWidget(\'dialogPopup\');ActionHandler.redraw(\'' + keyName + '\')">OK</button>';

        PWM_MAIN.clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Options for ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
            style: "width: 650px",
            content: bodyText,
            hide: function(){
                PWM_MAIN.clearDijitWidget('dialogPopup');
                ActionHandler.redraw(keyName);
            }
        });
        theDialog.show();

        if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'webservice') {
            PWM_MAIN.clearDijitWidget(inputID + "method");
            new dijit.form.Select({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['method'],
                options: [
                    { label: "Delete", value: "delete" },
                    { label: "Get", value: "get" },
                    { label: "Post", value: "post" },
                    { label: "Put", value: "put" }
                ],
                style: "width: 80px",
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['method'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "method");

            //PWM_MAIN.clearDijitWidget(inputID + "clientSide");
            //new dijit.form.CheckBox({
            //    checked: PWM_VAR['clientSettingCache'][keyName][iteration]['clientSide'],
            //    onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['clientSide'] = this.checked;ActionHandler.writeFormSetting(keyName)}
            //},inputID + "clientSide");

            PWM_MAIN.clearDijitWidget(inputID + "url");
            new dijit.form.Textarea({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['url'],
                required: true,
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['url'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "url");

            PWM_MAIN.clearDijitWidget(inputID + "body");
            new dijit.form.Textarea({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['body'],
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['body'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "body");

        } else if (PWM_VAR['clientSettingCache'][keyName][iteration]['type'] == 'ldap') {
            PWM_MAIN.clearDijitWidget(inputID + "attributeName");
            new dijit.form.ValidationTextBox({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['attributeName'],
                required: true,
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['attributeName'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "attributeName");

            PWM_MAIN.clearDijitWidget(inputID + "attributeValue");
            new dijit.form.Textarea({
                value: PWM_VAR['clientSettingCache'][keyName][iteration]['attributeValue'],
                required: true,
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['attributeValue'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "attributeValue");
        }
    });
};

ActionHandler.showHeadersDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/ValidationTextBox","dijit/form/Button","dijit/form/TextBox"],function(Dialog,ValidationTextBox,Button,TextBox){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "headers_";

        var bodyText = '';
        bodyText += '<table style="border:0" id="' + inputID + 'table">';
        bodyText += '<tr>';
        bodyText += '<td style="border:0"><b>Name</b></td><td style="border:0"><b>Value</b></td>';
        bodyText += '</tr><tr>';
        for (var headerName in PWM_VAR['clientSettingCache'][keyName][iteration]['headers']) {
            var value = PWM_VAR['clientSettingCache'][keyName][iteration]['headers'][headerName];
            var optionID = inputID + headerName;
            bodyText += '<td style="border:1px">' + headerName + '</td><td style="border:1px">' + value + '</td>';
            bodyText += '<td style="border:0">';
            bodyText += '<img id="' + optionID + '-removeButton' + '" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png"';
            bodyText += ' onclick="ActionHandler.removeHeader(\'' + keyName + '\',' + iteration + ',\'' + headerName + '\')" />';
            bodyText += '</td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<br/>';
        bodyText += '<input type="text" id="addHeaderName"/>';
        bodyText += '<input type="text" id="addHeaderValue"/>';
        bodyText += '<input type="button" id="addHeaderButton" value="Add"/>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="ActionHandler.showOptionsDialog(\'' + keyName + '\',\'' + iteration + '\')">OK</button>';

        PWM_MAIN.clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Http Headers for webservice ' + PWM_VAR['clientSettingCache'][keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                PWM_MAIN.clearDijitWidget('dialogPopup');
                ActionHandler.showOptionsDialog(keyName,iteration);
            }
        });
        theDialog.show();

        for (var headerName in PWM_VAR['clientSettingCache'][keyName][iteration]['headers']) {
            var value = PWM_VAR['clientSettingCache'][keyName][iteration]['headers'][headerName];
            var loopID = inputID + headerName;
            PWM_MAIN.clearDijitWidget(loopID);
            new TextBox({
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][iteration]['headers'][headerName] = this.value;ActionHandler.writeFormSetting(keyName)}
            },loopID);
        }

        PWM_MAIN.clearDijitWidget("addHeaderName");
        new ValidationTextBox({
            placeholder: "Name",
            id: "addHeaderName",
            constraints: {min: 1}
        },"addHeaderName");

        PWM_MAIN.clearDijitWidget("addHeaderValue");
        new ValidationTextBox({
            placeholder: "Display Value",
            id: "addHeaderValue",
            constraints: {min: 1}
        },"addHeaderValue");

        PWM_MAIN.clearDijitWidget("addHeaderButton");
        new Button({
            label: "Add",
            onClick: function() {
                require(["dijit/registry"],function(registry){
                    var name = registry.byId('addHeaderName').value;
                    var value = registry.byId('addHeaderValue').value;
                    ActionHandler.addHeader(keyName, iteration, name, value);
                });
            }
        },"addHeaderButton");
    });
};

ActionHandler.addHeader = function(keyName, iteration, headerName, headerValue) {
    if (headerName == null || headerName.length < 1) {
        alert('Name field is required');
        return;
    }

    if (headerValue == null || headerValue.length < 1) {
        alert('Value field is required');
        return;
    }

    if (!PWM_VAR['clientSettingCache'][keyName][iteration]['headers']) {
        PWM_VAR['clientSettingCache'][keyName][iteration]['headers'] = {};
    }

    PWM_VAR['clientSettingCache'][keyName][iteration]['headers'][headerName] = headerValue;
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.showHeadersDialog(keyName, iteration);
};

ActionHandler.removeHeader = function(keyName, iteration, headerName) {
    delete PWM_VAR['clientSettingCache'][keyName][iteration]['headers'][headerName];
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.showHeadersDialog(keyName, iteration);
};

// -------------------------- email table handler ------------------------------------

var EmailTableHandler = {};

EmailTableHandler.init = function(keyName) {
    console.log('EmailTableHandler init for ' + keyName);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        EmailTableHandler.draw(keyName);
    });
};

EmailTableHandler.draw = function(keyName) {
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    var parentDiv = 'table_setting_' + keyName;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    require(["dojo/parser","dojo/html","dijit/form/ValidationTextBox","dijit/form/Textarea"],
        function(dojoParser,dojoHtml,ValidationTextBox,Textarea){
            PWM_CFGEDIT.clearDivElements(parentDiv, false);
            for (var localeName in resultValue) {
                EmailTableHandler.drawRow(keyName,localeName,parentDiv)
            }

            if (PWM_MAIN.isEmpty(resultValue)) {
                var newTableRow = document.createElement("tr");
                newTableRow.setAttribute("style", "border-width: 0");
                newTableRow.setAttribute("colspan", "5");

                var newTableData = document.createElement("td");
                newTableData.setAttribute("style", "border-width: 0;");

                var addItemButton = document.createElement("button");
                addItemButton.setAttribute("type", "[button");
                addItemButton.setAttribute("onclick", "PWM_CFGEDIT.resetSetting('" + keyName + "',function(){PWM_CFGEDIT.loadMainPageBody()});");
                addItemButton.setAttribute("class", "btn");
                addItemButton.innerHTML = '<span class="btn-icon fa fa-plus-square"></span>Add Value';
                newTableData.appendChild(addItemButton);

                newTableRow.appendChild(newTableData);
                var parentDivElement = PWM_MAIN.getObject(parentDiv);
                parentDivElement.appendChild(newTableRow);
            } else {
                var addLocaleFunction = function(localeValue) {
                    if (!PWM_VAR['clientSettingCache'][keyName][localeValue]) {
                        PWM_VAR['clientSettingCache'][keyName][localeValue] = {};
                        EmailTableHandler.writeSetting(keyName,true);
                    }
                };
                PWM_CFGEDIT.addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction);
            }
            dojoParser.parse(parentDiv);
        });
};

EmailTableHandler.drawRow = function(keyName, localeName, parentDiv) {
    require(["dojo/parser","dojo/html","dijit/form/ValidationTextBox","dijit/form/Textarea"],
        function(dojoParser,dojoHtml,ValidationTextBox,Textarea){
            var localeTableRow = document.createElement("tr");
            localeTableRow.setAttribute("style", "border-width: 0;");

            var localeTdName = document.createElement("td");
            localeTdName.setAttribute("style", "border-width: 0; width:15px");
            localeTdName.innerHTML = localeName;
            localeTableRow.appendChild(localeTdName);

            var localeTdContent = document.createElement("td");
            localeTdContent.setAttribute("style", "border-width: 0; width: 520px");
            localeTableRow.appendChild(localeTdContent);

            var localeTableElement = document.createElement("table");
            localeTableElement.setAttribute("style", "border-width: 0px; width:515px; margin:0");
            localeTdContent.appendChild(localeTableElement);

            var idPrefix = "setting_" + localeName + "_" + keyName;
            var htmlBody = '';
            htmlBody += '<table class="noborder">';
            htmlBody += '<tr><td style="width:30px; text-align:right">To</td>';
            htmlBody += '<td><input id="' + idPrefix + '_to"/></td></tr>';
            htmlBody += '<tr><td style="width:30px; text-align:right">From</td>';
            htmlBody += '<td><input id="' + idPrefix + '_from"/></td></tr>';
            htmlBody += '<tr><td style="width:30px; text-align:right">Subject</td>';
            htmlBody += '<td><input id="' + idPrefix + '_subject"/></td></tr>';
            htmlBody += '<tr><td style="width:30px; text-align:right">Plain Body</td>';
            htmlBody += '<td><input id="' + idPrefix + '_bodyPlain"/></td></tr>';
            htmlBody += '<tr><td style="width:30px; text-align:right">HTML Body</td>';
            htmlBody += '<td><div style="border:2px solid #EAEAEA; background: white; width: 446px" onclick="EmailTableHandler.popupEditor(\'' + keyName + '\',\'' + localeName + '\')">';
            htmlBody += PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'] ? PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'] : "&nbsp;" ;
            htmlBody += '</div></td></tr>';
            htmlBody += "</table>"
            dojoHtml.set(localeTableElement,htmlBody);
            var parentDivElement = PWM_MAIN.getObject(parentDiv);
            parentDivElement.appendChild(localeTableRow);

            PWM_MAIN.clearDijitWidget(idPrefix + "_to");
            new ValidationTextBox({
                value: PWM_VAR['clientSettingCache'][keyName][localeName]['to'],
                style: 'width: 450px',
                required: true,
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][localeName]['to'] = this.value;EmailTableHandler.writeSetting(keyName)}
            },idPrefix + "_to");

            PWM_MAIN.clearDijitWidget(idPrefix + "_from");
            new ValidationTextBox({
                value: PWM_VAR['clientSettingCache'][keyName][localeName]['from'],
                style: 'width: 450px',
                required: true,
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][localeName]['from'] = this.value;EmailTableHandler.writeSetting(keyName)}
            },idPrefix + "_from");

            PWM_MAIN.clearDijitWidget(idPrefix + "_subject");
            new ValidationTextBox({
                value: PWM_VAR['clientSettingCache'][keyName][localeName]['subject'],
                style: 'width: 450px',
                required: true,
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][localeName]['subject'] = this.value;EmailTableHandler.writeSetting(keyName)}
            },idPrefix + "_subject");

            PWM_MAIN.clearDijitWidget(idPrefix + "_bodyPlain");
            new Textarea({
                value: PWM_VAR['clientSettingCache'][keyName][localeName]['bodyPlain'],
                style: 'width: 450px',
                required: true,
                onChange: function(){PWM_VAR['clientSettingCache'][keyName][localeName]['bodyPlain'] = this.value;EmailTableHandler.writeSetting(keyName)}
            },idPrefix + "_bodyPlain");

            { // add a spacer row
                var spacerTableRow = document.createElement("tr");
                spacerTableRow.setAttribute("style", "border-width: 0");
                parentDivElement.appendChild(spacerTableRow);

                var spacerTableData = document.createElement("td");
                spacerTableData.setAttribute("style", "border-width: 0");
                spacerTableData.innerHTML = "&nbsp;";
                spacerTableRow.appendChild(spacerTableData);
            }

            if (localeName != '' || PWM_MAIN.itemCount(PWM_VAR['clientSettingCache'][keyName])){ // add remove locale x
                var imgElement2 = document.createElement("div");
                imgElement2.setAttribute("style", "width: 10px; height: 10px;");
                imgElement2.setAttribute("class", "delete-row-icon action-icon fa fa-times");
                imgElement2.setAttribute("id", "button-" + keyName + "-" + localeName + "-deleteRow");
                //imgElement2.setAttribute("onclick", "delete PWM_VAR['clientSettingCache']['" + keyName + "']['" + localeName + "'];EmailTableHandler.writeSetting('" + keyName + "',true)");
                var tdElement = document.createElement("td");
                tdElement.setAttribute("style", "border-width: 0; text-align: left; vertical-align: top");

                localeTableRow.appendChild(tdElement);
                tdElement.appendChild(imgElement2);
            }

            PWM_MAIN.addEventHandler("button-" + keyName + "-" + localeName + "-deleteRow","click",function(){
                delete PWM_VAR['clientSettingCache'][keyName][localeName];
                EmailTableHandler.writeSetting(keyName,true);
            });
        });
};


EmailTableHandler.popupEditor = function(keyName, localeName) {
    require(["dijit/Editor","dijit/_editor/plugins/AlwaysShowToolbar","dijit/_editor/plugins/LinkDialog","dijit/_editor/plugins/ViewSource","dijit/_editor/plugins/FontChoice","dijit/_editor/plugins/TextColor"],
        function(Editor,AlwaysShowToolbar){
            var idValue = keyName + "_" + localeName + "_htmlEditor";
            var idValueDialog = idValue + "_Dialog";
            var bodyText = '';
            bodyText += '<div id="' + idValue + '" style="border:2px solid #EAEAEA; min-height: 200px;"></div>';
            PWM_MAIN.showDialog({
                title: "HTML Editor",
                text: bodyText,
                dialogClass: 'wide',
                loadFunction:function(){
                    PWM_MAIN.clearDijitWidget(idValue);
                    new Editor({
                        extraPlugins: [
                            AlwaysShowToolbar,"viewsource",
                            {name:"dijit/_editor/plugins/LinkDialog",command:"createLink",urlRegExp:".*"},
                            "fontName","foreColor"
                        ],
                        height: '',
                        value: PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'],
                        style: 'width: 100%',
                        onChange: function(){PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'] = this.get('value')},
                        onKeyUp: function(){PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'] = this.get('value')}
                    },idValue).startup();
                },
                okAction:function(){
                    EmailTableHandler.writeSetting(keyName,true);
                }
            });
        });
};


EmailTableHandler.writeSetting = function(settingKey, redraw) {
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, currentValues);
    if (redraw) {
        EmailTableHandler.draw(settingKey);
    }
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
ChallengeSettingHandler.defaultItem = {text:'Question',minLength:4,maxLength:200,adminDefined:true};

ChallengeSettingHandler.init = function(keyName) {
    var parentDiv = "table_setting_" + keyName;
    console.log('ChallengeSettingHandler init for ' + keyName);
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        ChallengeSettingHandler.draw(keyName);
    });
};

ChallengeSettingHandler.draw = function(keyName) {
    var parentDiv = "table_setting_" + keyName;
    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    var bodyText = '';
    bodyText += '<table style="cursor: pointer; table-layout: fixed" class="noborder">';
    bodyText += '<col style="width:60px"/>';
    bodyText += '<col style="width:100%"/>';
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    for (var localeName in resultValue) {
        (function(localeKey) {
            var isDefaultLocale = localeKey == "";
            var multiValues = resultValue[localeKey];
            var rowCount = PWM_MAIN.itemCount(multiValues);
            var editJsText = 'ChallengeSettingHandler.editLocale(\'' + keyName + '\',\'' + localeKey + '\')';

            bodyText += '<tr><td style="" onclick="' + editJsText + '">';
            bodyText += isDefaultLocale ? "Default" : localeKey;
            bodyText += '</td>';

            bodyText += '<td onclick="' + editJsText + '"> ';
            bodyText += '<div style="text-overflow:ellipsis; white-space:nowrap; overflow:hidden">';
            if (rowCount > 0) {
                for (var iteration in multiValues) {
                    (function (rowKey) {
                        var questionText = multiValues[rowKey]['text'];
                        var adminDefined = multiValues[rowKey]['adminDefined'];
                        bodyText += '<div>' + (adminDefined ? questionText : '[User Defined]') + '</div>';
                    }(iteration));
                }
            } else {
                bodyText += '[No Questions]';
            }
            bodyText += '</div>';
            //bodyText += '</td><td style="border:0; vertical-align: top">';
            //if (!isDefaultLocale) {
            //    bodyText += '<span id="button-' + keyName + '-' + localeKey + '-deleteRow" style="top:0" class="delete-row-icon action-icon fa fa-times"/>';
            //}
            bodyText += '</td></tr>';
            bodyText += '<tr><td>&nbsp;</td></tr>';

            parentDivElement.innerHTML = bodyText;

            PWM_MAIN.addEventHandler('button-' + keyName + '-' + localeKey + '-deleteRow','click',function(){
                ChallengeSettingHandler.deleteLocale(keyName)
            });
        }(localeName));
    }
    bodyText += '</table>';

    var addLocaleFunction = function(localeValue) {
        if (localeValue in PWM_VAR['clientSettingCache'][keyName]) {
            PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Error'),text:'Locale <i>' + localeValue + '</i> is already present.'});
        } else {
            PWM_VAR['clientSettingCache'][keyName][localeValue] = [];
            PWM_VAR['clientSettingCache'][keyName][localeValue][0] = ChallengeSettingHandler.defaultItem;
            ChallengeSettingHandler.write(keyName);
            ChallengeSettingHandler.editLocale(keyName,localeValue);
        }
    };
    var tableElement = document.createElement("table");
    parentDivElement.appendChild(tableElement);
    PWM_CFGEDIT.addAddLocaleButtonRow(tableElement, keyName, addLocaleFunction);
};

ChallengeSettingHandler.editLocale = function(keyName, localeKey) {
    var localeDisplay = localeKey == "" ? "Default" : localeKey;
    var dialogBody = '<div id="challengeLocaleDialogDiv" style="max-height:500px; overflow-x: auto">';

    var localeName = localeKey;

    var resultValue = PWM_VAR['clientSettingCache'][keyName];
    require(["dojo","dijit/registry","dojo/parser","dijit/form/Button","dijit/form/ValidationTextBox","dijit/form/Textarea","dijit/form/NumberSpinner","dijit/form/ToggleButton"],
        function(dojo,registry,dojoParser){
            var multiValues = resultValue[localeName];

            dialogBody += '<table class="noborder">';

            for (var iteration in multiValues) {
                (function(rowKey) {
                    var isAdminDefined = multiValues[rowKey]['adminDefined'];
                    var questionText = multiValues[rowKey]['text'];

                    dialogBody += '<tr>';
                    dialogBody += '<td colspan="200" style="border-width: 0;">';

                    var inputID = "value-" + keyName + "-" + localeName + "-" + rowKey;
                    PWM_MAIN.clearDijitWidget(inputID);

                    dialogBody += '<textarea id="' + inputID + '" style="width: 700px" required="required"';
                    dialogBody += ' data-dojo-type="dijit/form/Textarea' + '"';
                    dialogBody += ' onchange="PWM_VAR[\'clientSettingCache\'][\'' + keyName + '\'][\'' + localeKey + '\'][\'' + rowKey + '\'][\'text\'] = this.value"';
                    if (!isAdminDefined) {
                        dialogBody += ' disabled="disabled"';
                        dialogBody += ' value="[User Defined]"';
                    } else {
                        dialogBody += ' value="' + questionText + '"';
                    }
                    dialogBody += '></textarea>';

                    dialogBody += '<div class="delete-row-icon action-icon fa fa-times"';
                    dialogBody += ' onclick="ChallengeSettingHandler.deleteRow(\'' + keyName + '\',\'' + localeKey + '\',\'' + rowKey + '\')");';
                    dialogBody += '/>';

                    dialogBody += '</td>';
                    dialogBody += '</tr>';

                    dialogBody += '<tr style="padding-bottom: 15px; border:0"><td style="padding-bottom: 15px; border:0">';
                    dialogBody += '<button data-dojo-type="dijit/form/ToggleButton" data-dojo-props="iconClass:\'dijitCheckBoxIcon\',showLabel:true,label:\'Admin Defined\',checked:' + isAdminDefined + '"';
                    dialogBody += ' onchange="ChallengeSettingHandler.toggleAdminDefinedRow(this,\'' + inputID + '\',\'' + keyName + '\',\'' + localeKey + '\',\'' + rowKey + '\')"';
                    dialogBody += '></button>';

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
                    var enforceWordlist = multiValues[rowKey]['enforceWordlist'];
                    dialogBody += '<button data-dojo-type="dijit/form/ToggleButton" data-dojo-props="iconClass:\'dijitCheckBoxIcon\',showLabel:true,label:\'Enforce Wordlist\',checked:' + enforceWordlist + '"';
                    dialogBody += ' onchange="PWM_VAR[\'clientSettingCache\'][\'' + keyName + '\'][\'' + localeKey + '\'][\'' + rowKey + '\'][\'enforceWordlist\'] = this.checked"';
                    dialogBody += '></button>';

                    /*
                     dialogBody += '</td><td style="padding-bottom: 15px; border:0">';
                     dialogBody += '<input style="width: 50px" data-dojo-type="dijit/form/NumberSpinner" value="' +multiValues[rowKey]['points'] + '" data-dojo-props="constraints:{min:0,max:255,places:0}""';
                     dialogBody += ' onchange="PWM_VAR['clientSettingCache'][\'' + keyName + '\'][\'' + localeKey + '\'][\'' + rowKey + '\'][\'points\'] = this.value"/><br/>Points';
                     */
                    dialogBody += '</td></tr>';
                }(iteration));
            }

            dialogBody += '</table></div>';
            dialogBody += '<br/><br/><button type="button" data-dojo-type="dijit/form/Button"';
            dialogBody += ' onclick="ChallengeSettingHandler.addRow(\'' + keyName + '\',\'' + localeKey + '\')"';
            dialogBody += '><span class="btn-icon fa fa-plus-square"></span>Add Value</button>';

            if (localeKey != "") {
                dialogBody += '<button type="button" data-dojo-type="dijit/form/Button"';
                dialogBody += ' onclick="ChallengeSettingHandler.deleteLocale(\'' + keyName + '\',\'' + localeKey + '\')"';
                dialogBody += '><span class="btn-icon fa fa-times"></span>Delete Locale ' + localeDisplay + '</button>';
            }

            var dialogTitle = PWM_SETTINGS['settings'][keyName]['label'] + ' - ' + localeDisplay;
            PWM_MAIN.showDialog({title:dialogTitle,text:dialogBody,showClose:true,dialogClass:'wide',loadFunction:function(){
                dojoParser.parse(PWM_MAIN.getObject('challengeLocaleDialogDiv'));
            },okAction:function(){
                ChallengeSettingHandler.write(keyName );
                ChallengeSettingHandler.draw(keyName);
            }});
        }
    );

};

ChallengeSettingHandler.deleteLocale = function(keyName,localeKey) {
    PWM_MAIN.showConfirmDialog({
        text: 'Are you sure you want to remove all the questions for the <i>' + localeKey + '</i> locale?',
        okAction:function(){
            PWM_MAIN.showWaitDialog();
            delete PWM_VAR['clientSettingCache'][keyName][localeKey];
            PWM_CFGEDIT.writeSetting(keyName, PWM_VAR['clientSettingCache'][keyName],function(){
                PWM_MAIN.closeWaitDialog();
                ChallengeSettingHandler.init(keyName);
            });
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

ChallengeSettingHandler.write = function(keyName) {
    PWM_CFGEDIT.writeSetting(keyName, PWM_VAR['clientSettingCache'][keyName]);
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

    parentDivElement.innerHTML = '<input class="configStringInput" id="value_' + settingKey + '" name="setting_' + settingKey + '" disabled/>'
    + '&nbsp;<span id="icon-' + settingKey + '-warning" style="visibility:hidden; color:orange" class="btn-icon fa fa-warning"></span>';
    PWM_CFGEDIT.readSetting(settingKey,function(data){
        var inputElement = PWM_MAIN.getObject("value_" + settingKey);
        inputElement.value = data;
        if (PWM_SETTINGS['settings'][settingKey]['required']) {
            inputElement.required = true;
        }
        if (PWM_SETTINGS['settings'][settingKey]['pattern']) {
            inputElement.pattern = PWM_SETTINGS['settings'][settingKey]['pattern'];
            inputElement.title = PWM_CONFIG.showString('Warning_InvalidFormat');
            var validateValue = function() {
                var re = new RegExp(PWM_SETTINGS['settings'][settingKey]['pattern']);
                var matches = re.test(inputElement.value);
                PWM_MAIN.getObject('icon-' + settingKey + '-warning').style.visibility = matches ?  'hidden' : 'visible';
            };
            PWM_MAIN.addEventHandler("value_" + settingKey,"keyup",function(){
                validateValue();
            });
            validateValue();
            PWM_MAIN.showTooltip({id:'icon-'+settingKey+'-warning',text:PWM_CONFIG.showString('Warning_InvalidFormat')});

        }
        if (PWM_SETTINGS['settings'][settingKey]['placeholder']) {
            inputElement.placeholder = PWM_SETTINGS['settings'][settingKey]['placeholder'];
        }
        inputElement.disabled = false;
        PWM_MAIN.addEventHandler("value_" + settingKey,"input,change",function(){
            PWM_CFGEDIT.writeSetting(settingKey,inputElement.value);
        });
    });
};

// -------------------------- text area handler ------------------------------------

var TextAreaValueHandler = {};
TextAreaValueHandler.init = function(settingKey) {
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
