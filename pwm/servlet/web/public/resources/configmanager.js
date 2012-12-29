/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

var clientSettingCache = { };

function readSetting(keyName, valueWriter) {
    require(["dojo"],function(dojo){
        dojo.xhrGet({
            url:"ConfigManager?processAction=readSetting&key=" + keyName + "&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            contentType: "application/json;charset=utf-8",
            preventCache: true,
            dataType: "json",
            handleAs: "json",
            error: function(errorObj) {
                showError("error loading " + keyName + ", reason: " + errorObj)
            },
            load: function(data) {
                console.log('read data for ' + keyName);
                var resultValue = data.value;
                valueWriter(resultValue);
                var isDefault = data['isDefault'];
                var resetImageButton = getObject('resetButton-' + keyName);
                if (!isDefault) {
                    resetImageButton.style.visibility = 'visible';
                } else {
                    resetImageButton.style.visibility = 'hidden';
                }
            }
        });
    });
}

function writeSetting(keyName, valueData) {
    require(["dojo"],function(dojo){
        var jsonString = dojo.toJson(valueData);
        dojo.xhrPost({
            url: "ConfigManager?processAction=writeSetting&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&key=" + keyName,
            postData: jsonString,
            contentType: "application/json;charset=utf-8",
            encoding: "utf-8",
            dataType: "json",
            handleAs: "json",
            preventCache: true,
            error: function(errorObj) {
                showError("error writing setting " + keyName + ", reason: " + errorObj)
            },
            load: function(data) {
                console.log('wrote data for ' + keyName);
                var isDefault = data['isDefault'];
                var resetImageButton = getObject('resetButton-' + keyName);
                if (!isDefault) {
                    resetImageButton.style.visibility = 'visible';
                } else {
                    resetImageButton.style.visibility = 'hidden';
                }
            }
        });
    });
}

function resetSetting(keyName) {
    require(["dojo"],function(dojo){
        var jsonData = { key:keyName };
        var jsonString = dojo.toJson(jsonData);
        dojo.xhrPost({
            url: "ConfigManager?processAction=resetSetting&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            postData: jsonString,
            contentType: "application/json;charset=utf-8",
            dataType: "json",
            handleAs: "json",
            sync: true,
            error: function(errorObj) {
                showError("error resetting setting " + keyName + ", reason: " + errorObj)
            },
            load: function() {
                console.log('reset data for ' + keyName);
            }
        });
    });
}

function toggleBooleanSetting(keyName) {
    var valueElement = getObject('value_' + keyName);
    var buttonElement = getObject('button_' + keyName);
    var innerValue = valueElement.value;
    if (innerValue == 'true') {
        valueElement.value = 'false';
        buttonElement.innerHTML = '\u00A0\u00A0\u00A0False\u00A0\u00A0\u00A0';
    } else {
        valueElement.value = 'true';
        buttonElement.innerHTML = '\u00A0\u00A0\u00A0True\u00A0\u00A0\u00A0';
    }
}

function clearDivElements(parentDiv, showLoading) {
    var parentDivElement = getObject(parentDiv);
    if (parentDivElement != null) {
        if (parentDivElement.hasChildNodes()) {
            while (parentDivElement.childNodes.length >= 1) {
                var firstChild = parentDivElement.firstChild;
                parentDivElement.removeChild(firstChild);
            }
        }
        if (showLoading) {
            var newTableRow = document.createElement("tr");
            newTableRow.setAttribute("style", "border-width: 0");
            parentDivElement.appendChild(newTableRow);


            var newTableData = document.createElement("td");
            newTableData.setAttribute("style", "border-width: 0");
            newTableData.innerHTML = "[Loading...]";
            newTableRow.appendChild(newTableData);
        }
    }
}

function addAddLocaleButtonRow(parentDiv, keyName, addFunction) {
    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");

    var td1 = document.createElement("td");
    td1.setAttribute("style", "border-width: 0");
    td1.setAttribute("colspan", "5");

    var selectElement = document.createElement("select");
    selectElement.setAttribute('id', keyName + '-addLocaleValue');
    td1.appendChild(selectElement);

    var addButton = document.createElement("button");
    addButton.setAttribute('id', keyName + '-addLocaleButton');
    addButton.setAttribute("type", "button");
    addButton.innerHTML = 'Add Locale';
    td1.appendChild(addButton);

    newTableRow.appendChild(td1);
    var parentDivElement = getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    require(["dojo/store/Memory","dijit/form/FilteringSelect","dijit/form/Button"],function(Memory){
        var availableLocales = PWM_GLOBAL['localeInfo'];

        var localeMenu = [];
        for (var localeIter in availableLocales) {
            if (localeIter != PWM_GLOBAL['defaultLocale']) {
                localeMenu.push({name: availableLocales[localeIter], id: localeIter})
            }
        }

        clearDijitWidget(keyName + '-addLocaleValue');
        new dijit.form.FilteringSelect({
            id: keyName + '-addLocaleValue',
            store: new Memory({data: localeMenu})
        }, keyName + '-addLocaleValue');

        clearDijitWidget(keyName + '-addLocaleButton');
        new dijit.form.Button({
            id: keyName + '-addLocaleButton',
            onClick: addFunction
        }, keyName + '-addLocaleButton');

        return newTableRow;
    });
}

// -------------------------- locale table handler ------------------------------------
var LocaleTableHandler = {};

LocaleTableHandler.initLocaleTable = function(parentDiv, keyName, regExPattern, syntax) {
    console.log('LocaleTableHandler init for ' + keyName);
    clientSettingCache[keyName + "_regExPattern"] = regExPattern;
    clientSettingCache[keyName + "_syntax"] = syntax;
    clientSettingCache[keyName + "_parentDiv"] = parentDiv;
    clearDivElements(parentDiv, true);
    readSetting(keyName, function(resultValue) {
        clientSettingCache[keyName] = resultValue;
        LocaleTableHandler.draw(keyName);
    });
};

LocaleTableHandler.draw = function(keyName) {
    var parentDiv = clientSettingCache[keyName + "_parentDiv"];
    var regExPattern = clientSettingCache[keyName + "_regExPattern"];
    var syntax = clientSettingCache[keyName + "_syntax"];

    require(["dojo/parser","dijit/form/Button","dijit/form/Textarea","dijit/form/ValidationTextBox"],function(dojoParser){
        var resultValue = clientSettingCache[keyName];
        clearDivElements(parentDiv, false);
        for (var i in resultValue) {
            LocaleTableHandler.addLocaleTableRow(parentDiv, keyName, i, resultValue[i], regExPattern, syntax)
        }
        addAddLocaleButtonRow(parentDiv, keyName, function() {
            LocaleTableHandler.addLocaleSetting(keyName, parentDiv, regExPattern, syntax);
        });

        clientSettingCache[keyName] = resultValue;
        dojoParser.parse(parentDiv);
    });
};

LocaleTableHandler.addLocaleTableRow = function(parentDiv, settingKey, localeString, value, regExPattern, syntax) {
    var inputID = 'value-' + settingKey + '-' + localeString;

    // clear the old dijit node (if it exists)
    clearDijitWidget(inputID);

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    {
        var td1 = document.createElement("td");
        td1.setAttribute("style", "border-width: 0");

        if (localeString == null || localeString.length < 1) {
            td1.innerHTML = "";
        } else {
            td1.innerHTML = PWM_GLOBAL['localeDisplayNames'][localeString];
        }
        newTableRow.appendChild(td1);

    }
    {
        var td2 = document.createElement("td");
        //td2.setAttribute("width", "100%");
        td2.setAttribute("style", "border-width: 0");
        if (syntax == 'LOCALIZED_TEXT_AREA') {
            var textAreaElement = document.createElement("textarea");
            textAreaElement.setAttribute("id", inputID);
            textAreaElement.setAttribute("value", "[Loading....]");
            textAreaElement.setAttribute("onchange", "LocaleTableHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            textAreaElement.setAttribute("style", "width: 470px;");
            textAreaElement.setAttribute("data-dojo-type", "dijit.form.Textarea");
            textAreaElement.setAttribute("value", value);
            td2.appendChild(textAreaElement);
        } else {
            var inputElement = document.createElement("input");
            inputElement.setAttribute("id", inputID);
            inputElement.setAttribute("value", "[Loading....]");
            inputElement.setAttribute("onchange", "LocaleTableHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            inputElement.setAttribute("style", "width: 470px");
            inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("value", value);
            td2.appendChild(inputElement);
        }
        newTableRow.appendChild(td2);

        if (localeString != null && localeString.length > 0) {
            var imgElement = document.createElement("img");
            imgElement.setAttribute("style", "width: 15px; height: 15px");
            imgElement.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
            imgElement.setAttribute("onclick", "LocaleTableHandler.removeLocaleSetting('" + settingKey + "','" + localeString + "','" + parentDiv + "','" + regExPattern + "','" + syntax + "')");
            td2.appendChild(imgElement);
        }
    }
    var parentDivElement = getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
};

LocaleTableHandler.writeLocaleSetting = function(settingKey, locale, value) {
    var existingValues = clientSettingCache[settingKey];
    var currentValues = { };
    for (var i in existingValues) {
        var inputID = 'value-' + settingKey + '-' + i;
        currentValues[i] = getObject(inputID).value;
    }
    if (value == null) {
        delete currentValues[locale];
    } else {
        currentValues[locale] = value;
    }
    writeSetting(settingKey, currentValues);
    clientSettingCache[settingKey] = currentValues;
};

LocaleTableHandler.removeLocaleSetting = function(keyName, locale, parentDiv, regExPattern, syntax) {
    LocaleTableHandler.writeLocaleSetting(keyName, locale, null);
    LocaleTableHandler.draw(keyName);
};

LocaleTableHandler.addLocaleSetting = function(keyName, parentDiv, regExPattern, syntax) {
    require(["dijit/registry"],function(registry){
        var inputValue = registry.byId(keyName + '-addLocaleValue').value;
        try {
            var existingElementForLocale = getObject('value-' + keyName + '-' + inputValue);
            if (existingElementForLocale == null) {
                LocaleTableHandler.writeLocaleSetting(keyName, inputValue, '');
                LocaleTableHandler.draw(keyName);
            }
        } finally {
        }
    });
};

// -------------------------- multivalue table handler ------------------------------------

var MultiTableHandler = {};

MultiTableHandler.initMultiTable = function(parentDiv, keyName, regExPattern) {
    console.log('MultiTableHandler init for ' + keyName);
    clearDivElements(parentDiv, true);
    readSetting(keyName, function(resultValue) {
        clientSettingCache[keyName] = resultValue;
        MultiTableHandler.draw(parentDiv, keyName, regExPattern);
    });
};


MultiTableHandler.draw = function(parentDiv, keyName, regExPattern) {
    clearDivElements(parentDiv, false);
    var resultValue = clientSettingCache[keyName];
    var counter = 0;
    for (var i in resultValue) {
        MultiTableHandler.addMultiValueRow(parentDiv, keyName, i, resultValue[i], regExPattern);
        counter++;
    }
    {
        var newTableRow = document.createElement("tr");
        newTableRow.setAttribute("style", "border-width: 0");
        newTableRow.setAttribute("colspan", "5");

        var newTableData = document.createElement("td");
        newTableData.setAttribute("style", "border-width: 0;");

        var addItemButton = document.createElement("button");
        addItemButton.setAttribute("type", "[button");
        addItemButton.setAttribute("onclick", "MultiTableHandler.addMultiSetting('" + keyName + "','" + parentDiv + "','" + regExPattern + "');");
        addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
        addItemButton.innerHTML = "Add Value";
        newTableData.appendChild(addItemButton);

        newTableRow.appendChild(newTableData);
        var parentDivElement = getObject(parentDiv);
        parentDivElement.appendChild(newTableRow);
    }
    require(["dojo/parser","dijit/form/Button","dijit/form/Textarea"],function(dojoParser){
        dojoParser.parse(parentDiv);
    });
};

MultiTableHandler.addMultiValueRow = function(parentDiv, settingKey, iteration, value, regExPattern) {
    require(["dijit/registry"],function(registry){
        var inputID = 'value-' + settingKey + '-' + iteration;

        // clear the old dijit node (if it exists)
        var oldDijitNode = registry.byId(inputID);
        if (oldDijitNode != null) {
            try {
                oldDijitNode.destroy();
            } catch (error) {
            }
        }

        var newTableRow = document.createElement("tr");
        newTableRow.setAttribute("style", "border-width: 0");
        {
            var td1 = document.createElement("td");
            td1.setAttribute("width", "100%");
            td1.setAttribute("style", "border-width: 0;");


            var inputElement = document.createElement("input");
            inputElement.setAttribute("id", inputID);
            inputElement.setAttribute("value", value);
            inputElement.setAttribute("onchange", "MultiTableHandler.writeMultiSetting('" + settingKey + "','" + iteration + "',this.value)");
            inputElement.setAttribute("style", "width: 450px");
            inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
            td1.appendChild(inputElement);
            newTableRow.appendChild(td1);


            if (iteration != 0) {
                var imgElement = document.createElement("img");
                imgElement.setAttribute("style", "width: 15px; height: 15px");
                imgElement.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
                imgElement.setAttribute("onclick", "MultiTableHandler.removeMultiSetting('" + settingKey + "','" + iteration + "','" + regExPattern + "')");
                td1.appendChild(imgElement);
            }
        }
        var parentDivElement = getObject(parentDiv);
        parentDivElement.appendChild(newTableRow);
    });
};

MultiTableHandler.writeMultiSetting = function(settingKey, iteration, value) {
    var currentValues = clientSettingCache[settingKey];
    if (value == null) {
        delete currentValues[iteration];
    } else {
        currentValues[iteration] = value;
    }
    writeSetting(settingKey, currentValues);
};

MultiTableHandler.removeMultiSetting = function(keyName, iteration, regExPattern) {
    var parentDiv = 'table_setting_' + keyName;
    MultiTableHandler.writeMultiSetting(keyName, iteration, null);
    MultiTableHandler.draw(parentDiv, keyName, regExPattern);
};

MultiTableHandler.addMultiSetting = function(keyName, parentDiv, regExPattern) {
    clientSettingCache[keyName].push("");
    writeSetting(keyName, clientSettingCache[keyName]);
    MultiTableHandler.draw(parentDiv, keyName, regExPattern)
};

// -------------------------- multi locale table handler ------------------------------------

var MultiLocaleTableHandler = {};

MultiLocaleTableHandler.initMultiLocaleTable = function(parentDiv, keyName, regExPattern) {
    console.log('MultiLocaleTableHandler init for ' + keyName);
    clearDivElements(parentDiv, true);
    readSetting(keyName, function(resultValue) {
        clientSettingCache[keyName] = resultValue;
        MultiLocaleTableHandler.draw(parentDiv, keyName, regExPattern);
    });
};

MultiLocaleTableHandler.draw = function(parentDiv, keyName, regExPattern) {
    var resultValue = clientSettingCache[keyName];
    require(["dojo","dijit/registry","dojo/parser","dijit/form/Button","dijit/form/ValidationTextBox","dijit/form/Textarea","dijit/registry"],function(dojo,registry,dojoParser){
        clearDivElements(parentDiv, false);
        for (var localeName in resultValue) {
            var localeTableRow = document.createElement("tr");
            localeTableRow.setAttribute("style", "border-width: 0;");

            var localeTdName = document.createElement("td");
            localeTdName.setAttribute("style", "border-width: 0; width:15px");
            localeTdName.innerHTML = localeName;
            localeTableRow.appendChild(localeTdName);

            var localeTdContent = document.createElement("td");
            localeTdContent.setAttribute("style", "border-width: 0; width: 495px");
            localeTableRow.appendChild(localeTdContent);

            var localeTableElement = document.createElement("table");
            localeTableElement.setAttribute("style", "border-width: 1px; width:490px; margin:0");
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
                inputElement.setAttribute("style", "width: 450px");
                inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
                inputElement.setAttribute("regExp", regExPattern);
                inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
                valueTd1.appendChild(inputElement);
                valueTableRow.appendChild(valueTd1);
                localeTableElement.appendChild(valueTableRow);

                if (iteration != 0) { // add the remove value button
                    var imgElement = document.createElement("img");
                    imgElement.setAttribute("style", "width: 15px; height: 15px");
                    imgElement.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
                    imgElement.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',null,'" + regExPattern + "')");
                    valueTd1.appendChild(imgElement);
                }
            }

            { // add row button for this locale group
                var newTableRow = document.createElement("tr");
                newTableRow.setAttribute("style", "border-width: 0");
                newTableRow.setAttribute("colspan", "5");

                var newTableData = document.createElement("td");
                newTableData.setAttribute("style", "border-width: 0;");

                var addItemButton = document.createElement("button");
                addItemButton.setAttribute("type", "[button");
                addItemButton.setAttribute("onclick", "clientSettingCache['" + keyName + "']['" + localeName + "'].push('');MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "',null,null,null,'" + regExPattern + "')");
                addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
                addItemButton.innerHTML = "Add Value";
                newTableData.appendChild(addItemButton);

                newTableRow.appendChild(newTableData);
                localeTableElement.appendChild(newTableRow);
            }


            if (localeName != '') { // add remove locale x
                var imgElement2 = document.createElement("img");
                imgElement2.setAttribute("style", "width: 15px; height: 15px;");
                imgElement2.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
                imgElement2.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "',null,null,'" + regExPattern + "')");
                var tdElement = document.createElement("td");
                tdElement.setAttribute("style", "border-width: 0; text-align: left; vertical-align: top");

                localeTableRow.appendChild(tdElement);
                tdElement.appendChild(imgElement2);
            }

            var parentDivElement = getObject(parentDiv);
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

        addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction);
        clientSettingCache[keyName] = resultValue;
        dojoParser.parse(parentDiv);
    });
};

MultiLocaleTableHandler.writeMultiLocaleSetting = function(settingKey, locale, iteration, value, regExPattern) {
    var currentValues = clientSettingCache[settingKey];
    if (locale != null) {
        if (currentValues[locale] == null) {
            currentValues[locale] = [ "" ];
        }

        if (iteration == null) {
            delete currentValues[locale];
        } else {
            var internalValues = currentValues[locale];
            if (value == null) {
                delete internalValues[iteration];
            } else {
                internalValues[iteration] = value;
            }
        }
    }

    writeSetting(settingKey, currentValues);
    var parentDiv = 'table_setting_' + settingKey;
    MultiLocaleTableHandler.draw(parentDiv, settingKey, regExPattern);
};

// -------------------------- form table handler ------------------------------------

var FormTableHandler = {};

FormTableHandler.init = function(keyName) {
    console.log('FormTableHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;
    clearDivElements(parentDiv, true);
    readSetting(keyName, function(resultValue) {
        clientSettingCache[keyName] = resultValue;
        FormTableHandler.redraw(keyName);
    });
};

FormTableHandler.redraw = function(keyName) {
    var resultValue = clientSettingCache[keyName];
    var parentDiv = 'table_setting_' + keyName;
    clearDivElements(parentDiv, false);
    var parentDivElement = getObject(parentDiv);

    if (!isEmpty(resultValue)) {
        var headerRow = document.createElement("tr");
        headerRow.setAttribute("style", "border-width: 0");

        var header1 = document.createElement("td");
        header1.setAttribute("style", "border-width: 0;");
        header1.innerHTML = "Name";
        headerRow.appendChild(header1);

        var header2 = document.createElement("td");
        header2.setAttribute("style", "border-width: 0;");
        header2.innerHTML = "Label";
        headerRow.appendChild(header2);

        parentDivElement.appendChild(headerRow);
    }

    for (var i in resultValue) {
        FormTableHandler.drawRow(parentDiv, keyName, i, resultValue[i]);
    }

    {
        var newTableRow = document.createElement("tr");
        newTableRow.setAttribute("style", "border-width: 0");
        newTableRow.setAttribute("colspan", "5");

        var newTableData = document.createElement("td");
        newTableData.setAttribute("style", "border-width: 0;");

        var addItemButton = document.createElement("button");
        addItemButton.setAttribute("type", "button");
        addItemButton.setAttribute("onclick", "FormTableHandler.addMultiSetting('" + keyName + "','" + parentDiv + "');");
        addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
        addItemButton.innerHTML = "Add Value";
        newTableData.appendChild(addItemButton);

        newTableRow.appendChild(newTableData);
        parentDivElement.appendChild(newTableRow);
    }
    require(["dojo/parser","dijit/form/Button","dijit/form/FilteringSelect"],function(dojoParser){
        dojoParser.parse(parentDiv);
    });
};

FormTableHandler.drawRow = function(parentDiv, settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";

    // clear the old dijit node (if it exists)
    clearDijitWidget(inputID + "name");
    clearDijitWidget(inputID + "label");
    clearDijitWidget(inputID + "type");
    clearDijitWidget(inputID + "optionsButton");

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    {
        {
            var td1 = document.createElement("td");
            td1.setAttribute("style", "border-width: 0");
            var nameInput = document.createElement("input");
            nameInput.setAttribute("id", inputID + "name");
            nameInput.setAttribute("value", value['name']);
            nameInput.setAttribute("onchange","clientSettingCache['" + settingKey + "'][" + iteration + "]['name'] = this.value;FormTableHandler.writeFormSetting('" + settingKey + "')");
            nameInput.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            nameInput.setAttribute("data-dojo-props", "required: true");

            td1.appendChild(nameInput);
            newTableRow.appendChild(td1);
        }

        {
            var td2 = document.createElement("td");
            td2.setAttribute("style", "border-width: 0");
            var labelInput = document.createElement("input");
            labelInput.setAttribute("id", inputID + "label");
            labelInput.setAttribute("value", value['labels']['']);
            labelInput.setAttribute("readonly", "true");
            labelInput.setAttribute("onclick","FormTableHandler.showLabelDialog('" + settingKey + "'," + iteration + ")");
            labelInput.setAttribute("onkeypress","FormTableHandler.showLabelDialog('" + settingKey + "'," + iteration + ")");
            labelInput.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            td2.appendChild(labelInput);
            newTableRow.appendChild(td2);
        }

        {
            var td3 = document.createElement("td");
            td3.setAttribute("style", "border-width: 0");
            var optionList = PWM_GLOBAL['formTypeOptions'];
            var typeSelect = document.createElement("select");
            typeSelect.setAttribute("data-dojo-type", "dijit.form.FilteringSelect");
            typeSelect.setAttribute("id", inputID + "type");
            typeSelect.setAttribute("style","width: 80px");
            typeSelect.setAttribute("onchange","clientSettingCache['" + settingKey + "'][" + iteration + "]['type'] = this.value;FormTableHandler.writeFormSetting('" + settingKey + "')");
            for (var optionItem in optionList) {
                var optionElement = document.createElement("option");
                optionElement.value = optionList[optionItem];
                optionElement.innerHTML = optionList[optionItem];
                if (optionList[optionItem] == clientSettingCache[settingKey][iteration]['type']) {
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
            labelButton.setAttribute("onclick","FormTableHandler.showOptionsDialog('" + settingKey + "'," + iteration + ")");
            labelButton.innerHTML = "Options";
            td4.appendChild(labelButton);
            newTableRow.appendChild(td4);
        }

        {
            var tdFinal = document.createElement("td");
            tdFinal.setAttribute("style", "border-width: 0");

            var imgElement = document.createElement("img");
            imgElement.setAttribute("style", "width: 15px; height: 15px");
            imgElement.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
            imgElement.setAttribute("onclick", "FormTableHandler.removeMultiSetting('" + settingKey + "','" + iteration + "')");
            tdFinal.appendChild(imgElement);
            newTableRow.appendChild(tdFinal);
        }
    }
    var parentDivElement = getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
};

FormTableHandler.writeFormSetting = function(settingKey) {
    var cachedSetting = clientSettingCache[settingKey];
    writeSetting(settingKey, cachedSetting);
};

FormTableHandler.removeMultiSetting = function(keyName, iteration) {
    delete clientSettingCache[keyName][iteration];
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.redraw(keyName);
};

FormTableHandler.addMultiSetting = function(keyName) {
    var currentSize = 0;
    for (var loopVar in clientSettingCache[keyName]) {
        currentSize++;
    }
    clientSettingCache[keyName][currentSize + 1] = {};
    clientSettingCache[keyName][currentSize + 1]['name'] = '';
    clientSettingCache[keyName][currentSize + 1]['minimumLength'] = '0';
    clientSettingCache[keyName][currentSize + 1]['maximumLength'] = '255';
    clientSettingCache[keyName][currentSize + 1]['labels'] = {};
    clientSettingCache[keyName][currentSize + 1]['labels'][''] = '';
    clientSettingCache[keyName][currentSize + 1]['regexErrors'] = {};
    clientSettingCache[keyName][currentSize + 1]['regexErrors'][''] = '';
    clientSettingCache[keyName][currentSize + 1]['selectOptions'] = {};
    clientSettingCache[keyName][currentSize + 1]['selectOptions'][''] = '';
    clientSettingCache[keyName][currentSize + 1]['description'] = {};
    clientSettingCache[keyName][currentSize + 1]['description'][''] = '';
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.redraw(keyName)
};

FormTableHandler.showOptionsDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/NumberSpinner"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_";
        var bodyText = '<table style="border:0">';
        bodyText += '<tr>';
        bodyText += '<td style="border:0; text-align: right">Description</td><td style="border:0;"><input type="text" id="' + inputID + 'description' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Required</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'required' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Confirm</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'confirmationRequired' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Read Only</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'readonly' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Minimum Length</td><td style="border:0;"><input type="number" id="' + inputID + 'minimumLength' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Maximum Length</td><td style="border:0;"><input type="number" id="' + inputID + 'maximumLength' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Regular Expression</td><td style="border:0;"><input type="text" id="' + inputID + 'regex' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Regular Expression<br/>Error Message</td><td style="border:0;"><input type="text" id="' + inputID + 'regexErrors' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Placeholder</td><td style="border:0;"><input type="text" id="' + inputID + 'placeholder' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">JavaScript</td><td style="border:0;"><input type="text" id="' + inputID + 'javascript' + '"/></td>';
        bodyText += '</tr><tr>';
        if (clientSettingCache[keyName][iteration]['type'] == 'select') {
            bodyText += '<td style="border:0; text-align: right">Select Options</td><td style="border:0;"><input class="btn" type="button" id="' + inputID + 'selectOptions' + '" value="Edit Options" onclick="FormTableHandler.showSelectOptionsDialog(\'' + keyName + '\',\'' + iteration + '\')"/></td>';
            bodyText += '</tr>';
        }
        bodyText += '</table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');FormTableHandler.redraw(\'' + keyName + '\')">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Options for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
                FormTableHandler.redraw(keyName);
            }
        });
        theDialog.show();

        clearDijitWidget(inputID + "description");
        new dijit.form.Textarea({
            value: clientSettingCache[keyName][iteration]['description'][''],
            readonly: true,
            onClick: function(){FormTableHandler.showDescriptionDialog(keyName,iteration);},
            onKeyPress: function(){FormTableHandler.showDescriptionDialog(keyName,iteration);}
        },inputID + "description");

        clearDijitWidget(inputID + "required");
        new dijit.form.CheckBox({
            checked: clientSettingCache[keyName][iteration]['required'],
            onChange: function(){clientSettingCache[keyName][iteration]['required'] = this.checked;FormTableHandler.writeFormSetting(keyName)}
        },inputID + "required");

        clearDijitWidget(inputID + "confirmationRequired");
        new dijit.form.CheckBox({
            checked: clientSettingCache[keyName][iteration]['confirmationRequired'],
            onChange: function(){clientSettingCache[keyName][iteration]['confirmationRequired'] = this.checked;FormTableHandler.writeFormSetting(keyName)}
        },inputID + "confirmationRequired");

        clearDijitWidget(inputID + "readonly");
        new dijit.form.CheckBox({
            checked: clientSettingCache[keyName][iteration]['readonly'],
            onChange: function(){clientSettingCache[keyName][iteration]['readonly'] = this.checked;FormTableHandler.writeFormSetting(keyName)}
        },inputID + "readonly");

        clearDijitWidget(inputID + "minimumLength");
        new dijit.form.NumberSpinner({
            value: clientSettingCache[keyName][iteration]['minimumLength'],
            onChange: function(){clientSettingCache[keyName][iteration]['minimumLength'] = this.value;FormTableHandler.writeFormSetting(keyName)},
            constraints: { min:0, max:5000 },
            style: "width: 70px"
        },inputID + "minimumLength");

        clearDijitWidget(inputID + "maximumLength");
        new dijit.form.NumberSpinner({
            value: clientSettingCache[keyName][iteration]['maximumLength'],
            onChange: function(){clientSettingCache[keyName][iteration]['maximumLength'] = this.value;FormTableHandler.writeFormSetting(keyName)},
            constraints: { min:0, max:5000 },
            style: "width: 70px"
        },inputID + "maximumLength");

        clearDijitWidget(inputID + "regex");
        new dijit.form.Textarea({
            value: clientSettingCache[keyName][iteration]['regex'],
            onChange: function(){clientSettingCache[keyName][iteration]['regex'] = this.value;FormTableHandler.writeFormSetting(keyName)}
        },inputID + "regex");

        clearDijitWidget(inputID + "regexErrors");
        new dijit.form.Textarea({
            value: clientSettingCache[keyName][iteration]['regexErrors'][''],
            readonly: true,
            onClick: function(){FormTableHandler.showRegexErrorsDialog(keyName,iteration);},
            onKeyPress: function(){FormTableHandler.showRegexErrorsDialog(keyName,iteration);}
        },inputID + "regexErrors");

        clearDijitWidget(inputID + "placeholder");
        new dijit.form.Textarea({
            value: clientSettingCache[keyName][iteration]['placeholder'],
            onChange: function(){clientSettingCache[keyName][iteration]['placeholder'] = this.value;FormTableHandler.writeFormSetting(keyName)}
        },inputID + "placeholder");

        clearDijitWidget(inputID + "javascript");
        new dijit.form.Textarea({
            value: clientSettingCache[keyName][iteration]['javascript'],
            onChange: function(){clientSettingCache[keyName][iteration]['javascript'] = this.value;FormTableHandler.writeFormSetting(keyName)}
        },inputID + "javascript");
    });
};

FormTableHandler.showLabelDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "label_";
        var bodyText = '<table style="border:0" id="' + inputID + 'table">';
        bodyText += '<tr>';
        for (var localeName in clientSettingCache[keyName][iteration]['labels']) {
            var value = clientSettingCache[keyName][iteration]['labels'][localeName];
            var localeID = inputID + localeName;
            bodyText += '<td style="border:0; text-align: right">' + localeName + '</td><td style="border:0;"><input type="text" value="' + value + '" id="' + localeID + '' + '"/></td>';
            if (localeName != '') {
                bodyText += '<td style="border:0">';
                bodyText += '<img id="' + localeID + '-removeButton' + '" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png"';
                bodyText += ' onclick="FormTableHandler.removeLocaleLabel(\'' + keyName + '\',' + iteration + ',\'' + localeName + '\')" />';
                bodyText += '</td>';
            }
            bodyText += '</tr><tr>';
        }
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');FormTableHandler.redraw(\'' + keyName + '\')">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Label for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
                FormTableHandler.redraw(keyName);
            }
        });
        theDialog.show();

        for (var localeName in clientSettingCache[keyName][iteration]['labels']) {
            var value = clientSettingCache[keyName][iteration]['labels'][localeName];
            var loopID = inputID + localeName;
            clearDijitWidget(loopID);
            new dijit.form.Textarea({
                onChange: function(){clientSettingCache[keyName][iteration]['labels'][localeName] = this.value;FormTableHandler.writeFormSetting(keyName)}
            },loopID);

        }

        var addLocaleFunction = function() {
            require(["dijit/registry"],function(registry){
                FormTableHandler.addLocaleLabel(keyName, iteration, registry.byId(inputID + "-addLocaleValue").value);
            });
        };

        addAddLocaleButtonRow(inputID + 'table', inputID, addLocaleFunction);
    });
};

FormTableHandler.addLocaleLabel = function(keyName, iteration, localeName) {
    clientSettingCache[keyName][iteration]['labels'][localeName] = '';
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showLabelDialog(keyName, iteration)
};

FormTableHandler.removeLocaleLabel = function(keyName, iteration, localeName) {
    delete clientSettingCache[keyName][iteration]['labels'][localeName];
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showLabelDialog(keyName, iteration)
};

FormTableHandler.showRegexErrorsDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/Textarea"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "regexErrors_";

        var bodyText = '';
        bodyText += '<p>Error Message to show when the regular expression constraint is violated.</p>';
        bodyText += '<table style="border:0" id="' + inputID + 'table">';
        bodyText += '<tr>';
        for (var localeName in clientSettingCache[keyName][iteration]['regexErrors']) {
            var value = clientSettingCache[keyName][iteration]['regexErrors'][localeName];
            var localeID = inputID + localeName;
            bodyText += '<td style="border:0; text-align: right">' + localeName + '</td><td style="border:0;"><input type="text" value="' + value + '" id="' + localeID + '' + '"/></td>';
            if (localeName != '') {
                bodyText += '<td style="border:0">';
                bodyText += '<img id="' + localeID + '-removeButton' + '" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png"';
                bodyText += ' onclick="FormTableHandler.removeRegexErrorLocale(\'' + keyName + '\',' + iteration + ',\'' + localeName + '\')" />';
                bodyText += '</td>';
            }
            bodyText += '</tr><tr>';
        }
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');FormTableHandler.showOptionsDialog(\'' + keyName + '\',\'' + iteration + '\')">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Regular Expression Error Message for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
                FormTableHandler.showOptionsDialog(keyName,iteration);
            }
        });
        theDialog.show();

        for (var localeName in clientSettingCache[keyName][iteration]['regexErrors']) {
            var value = clientSettingCache[keyName][iteration]['regexErrors'][localeName];
            var loopID = inputID + localeName;
            clearDijitWidget(loopID);
            new dijit.form.Textarea({
                onChange: function(){clientSettingCache[keyName][iteration]['regexErrors'][localeName] = this.value;FormTableHandler.writeFormSetting(keyName)}
            },loopID);

        }

        var addLocaleFunction = function() {
            require(["dijit/registry"],function(registry){
                FormTableHandler.addRegexErrorLocale(keyName, iteration, registry.byId(inputID + "-addLocaleValue").value);
            });
        };

        addAddLocaleButtonRow(inputID + 'table', inputID, addLocaleFunction);
    });
};

FormTableHandler.addRegexErrorLocale = function(keyName, iteration, localeName) {
    clientSettingCache[keyName][iteration]['regexErrors'][localeName] = '';
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showRegexErrorsDialog(keyName, iteration);
};

FormTableHandler.removeRegexErrorLocale = function(keyName, iteration, localeName) {
    delete clientSettingCache[keyName][iteration]['regexErrors'][localeName];
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showRegexErrorsDialog(keyName, iteration);
};

FormTableHandler.showSelectOptionsDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/ValidationTextBox","dijit/form/Button"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "selectOptions_";

        var bodyText = '';
        bodyText += '<table style="border:0" id="' + inputID + 'table">';
        bodyText += '<tr>';
        bodyText += '<td style="border:0"><b>Name</b></td><td style="border:0"><b>Display Value</b></td>';
        bodyText += '</tr><tr>';
        for (var optionName in clientSettingCache[keyName][iteration]['selectOptions']) {
            var value = clientSettingCache[keyName][iteration]['selectOptions'][optionName];
            var optionID = inputID + optionName;
            bodyText += '<td style="border:1px">' + optionName + '</td><td style="border:1px">' + value + '</td>';
            bodyText += '<td style="border:0">';
            bodyText += '<img id="' + optionID + '-removeButton' + '" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png"';
            bodyText += ' onclick="FormTableHandler.removeSelectOptionsOption(\'' + keyName + '\',' + iteration + ',\'' + optionName + '\')" />';
            bodyText += '</td>';
            bodyText += '</tr><tr>';
        }
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<br/>';
        bodyText += '<input type="text" id="addSelectOptionName"/>';
        bodyText += '<input type="text" id="addSelectOptionValue"/>';
        bodyText += '<input type="button" id="addSelectOptionButton" value="Add"/>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="FormTableHandler.showOptionsDialog(\'' + keyName + '\',\'' + iteration + '\')">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Select Options for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
                FormTableHandler.showOptionsDialog(keyName,iteration);
            }
        });
        theDialog.show();

        for (var optionName in clientSettingCache[keyName][iteration]['selectOptions']) {
            var value = clientSettingCache[keyName][iteration]['selectOptions'][optionName];
            var loopID = inputID + optionName;
            clearDijitWidget(loopID);
            new dijit.form.TextBox({
                onChange: function(){clientSettingCache[keyName][iteration]['selectOptions'][optionName] = this.value;FormTableHandler.writeFormSetting(keyName)}
            },loopID);
        }

        clearDijitWidget("addSelectOptionName");
        new dijit.form.ValidationTextBox({
            placeholder: "Name",
            id: "addSelectOptionName",
            constraints: {min: 1}
        },"addSelectOptionName");

        clearDijitWidget("addSelectOptionValue");
        new dijit.form.ValidationTextBox({
            placeholder: "Display Value",
            id: "addSelectOptionValue",
            constraints: {min: 1}
        },"addSelectOptionValue");

        clearDijitWidget("addSelectOptionButton");
        new dijit.form.Button({
            label: "Add",
            onClick: function() {
                require(["dijit/registry"],function(registry){
                    var name = registry.byId('addSelectOptionName').value;
                    var value = registry.byId('addSelectOptionValue').value;
                    FormTableHandler.addSelectOptionsOption(keyName, iteration, name, value);
                });
            }
        },"addSelectOptionButton");
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

    clientSettingCache[keyName][iteration]['selectOptions'][optionName] = optionValue;
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showSelectOptionsDialog(keyName, iteration);
};

FormTableHandler.removeSelectOptionsOption = function(keyName, iteration, optionName) {
    delete clientSettingCache[keyName][iteration]['selectOptions'][optionName];
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showSelectOptionsDialog(keyName, iteration);
};

FormTableHandler.showDescriptionDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/Textarea"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "description_";

        var bodyText = '';
        bodyText += '<table style="border:0" id="' + inputID + 'table">';
        bodyText += '<tr>';
        for (var localeName in clientSettingCache[keyName][iteration]['description']) {
            var value = clientSettingCache[keyName][iteration]['description'][localeName];
            var localeID = inputID + localeName;
            bodyText += '<td style="border:0; text-align: right">' + localeName + '</td><td style="border:0;"><input type="text" value="' + value + '" id="' + localeID + '' + '"/></td>';
            if (localeName != '') {
                bodyText += '<td style="border:0">';
                bodyText += '<img id="' + localeID + '-removeButton' + '" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png"';
                bodyText += ' onclick="FormTableHandler.removeDescriptionLocale(\'' + keyName + '\',' + iteration + ',\'' + localeName + '\')" />';
                bodyText += '</td>';
            }
            bodyText += '</tr><tr>';
        }
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');FormTableHandler.showOptionsDialog(\'' + keyName + '\',\'' + iteration + '\')">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Description for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
                FormTableHandler.showOptionsDialog(keyName,iteration);
            }
        });
        theDialog.show();

        for (var localeName in clientSettingCache[keyName][iteration]['description']) {
            var value = clientSettingCache[keyName][iteration]['description'][localeName];
            var loopID = inputID + localeName;
            clearDijitWidget(loopID);
            new dijit.form.Textarea({
                onChange: function(){clientSettingCache[keyName][iteration]['description'][localeName] = this.value;FormTableHandler.writeFormSetting(keyName)}
            },loopID);
        }

        var addLocaleFunction = function() {
            require(["dijit/registry"],function(registry){
                FormTableHandler.addDescriptionLocale(keyName, iteration, registry.byId(inputID + "-addLocaleValue").value);
            });
        };

        addAddLocaleButtonRow(inputID + 'table', inputID, addLocaleFunction);
    });
};

FormTableHandler.addDescriptionLocale = function(keyName, iteration, localeName) {
    clientSettingCache[keyName][iteration]['description'][localeName] = '';
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showDescriptionDialog(keyName, iteration);
};

FormTableHandler.removeDescriptionLocale = function(keyName, iteration, localeName) {
    delete clientSettingCache[keyName][iteration]['description'][localeName];
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.showDescriptionDialog(keyName, iteration);
};

// -------------------------- change password handler ------------------------------------

var ChangePasswordHandler = {};

ChangePasswordHandler.validatePasswordPopupFields = function() {
    var password1 = getObject('password1').value;
    var password2 = getObject('password2').value;

    var matchStatus = "";

    getObject('password_button').disabled = true;
    if (password2.length > 0) {
        if (password1 == password2) {
            matchStatus = "MATCH";
            getObject('password_button').disabled = false;
        } else {
            matchStatus = "NO_MATCH";
        }
    }

    ChangePasswordHandler.markConfirmationCheck(matchStatus);
};

ChangePasswordHandler.markConfirmationCheck = function(matchStatus) {
    if (matchStatus == "MATCH") {
        getObject("confirmCheckMark").style.visibility = 'visible';
        getObject("confirmCrossMark").style.visibility = 'hidden';
        getObject("confirmCheckMark").width = '15';
        getObject("confirmCrossMark").width = '0';
    } else if (matchStatus == "NO_MATCH") {
        getObject("confirmCheckMark").style.visibility = 'hidden';
        getObject("confirmCrossMark").style.visibility = 'visible';
        getObject("confirmCheckMark").width = '0';
        getObject("confirmCrossMark").width = '15';
    } else {
        getObject("confirmCheckMark").style.visibility = 'hidden';
        getObject("confirmCrossMark").style.visibility = 'hidden';
        getObject("confirmCheckMark").width = '0';
        getObject("confirmCrossMark").width = '0';
    }
};

ChangePasswordHandler.doChange = function(settingKey) {
    var password1 = getObject('password1').value;
    clearDijitWidget('changepassword-popup');
    writeSetting(settingKey,password1);
};

ChangePasswordHandler.generateRandom = function() {
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
        getObject('generateButton').disabled = true;
        getObject('generateButton').innerHTML = "Loading...";

        dojo.xhrPost({
            url:PWM_GLOBAL['url-restservice'] + "/randompassword?pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            preventCache: true,
            postData: postData,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                registry.byId('password1').set('value',data['password']);
                registry.byId('password2').set('value','');
                getObject('generateButton').disabled = false;
                getObject('generateButton').innerHTML = "Generate Random";
            },
            error: function(error) {
                getObject('generateButton').disabled = false;
                getObject('generateButton').innerHTML = "Generate Random";
                alert('error reading random password: ' + error);
            }
        });
    });
};

ChangePasswordHandler.changePasswordPopup = function(settingName,settingKey) {
    require(["dijit/Dialog","dijit/form/Textarea"],function(){
        var bodyText = '<span id="message" class="message message-info">' + settingName + '</span><br/>';
        bodyText += '<table style="border: 0">';
        bodyText += '<tr style="border: 0"><td style="border: 0"><textarea data-dojo-type="dijit.form.Textarea" name="password1" id="password1" class="inputfield" style="width: 500px; max-height: 200px; overflow: auto" autocomplete="off" onkeyup="ChangePasswordHandler.validatePasswordPopupFields();getObject(\'password2\').value = \'\'"></textarea></td>';
        bodyText += '</tr><tr style="border: 0">';
        bodyText += '<td style="border: 0" xmlns="http://www.w3.org/1999/html"><textarea data-dojo-type="dijit.form.Textarea" name="password2" id="password2" class="inputfield" style="width: 500px; max-height: 200px; overflow: auto;" autocomplete="off" onkeyup="ChangePasswordHandler.validatePasswordPopupFields()"/></textarea></td>';

        bodyText += '<td style="border: 0"><div style="margin:0;">';
        bodyText += '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/greenCheck.png">';
        bodyText += '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png">';
        bodyText += '</div></td>';

        bodyText += '</tr></table>';
        bodyText += '<button name="change" class="btn" id="password_button" onclick="ChangePasswordHandler.doChange(' + "\'"+ settingKey + "\'" + ')" disabled="true"/>';
        bodyText += 'Set Password</button>&nbsp;&nbsp;&nbsp;&nbsp;';
        bodyText += '<button id="generateButton" name="generateButton" class="btn" onclick="ChangePasswordHandler.generateRandom()">Generate Random</button>';
        bodyText += '&nbsp;&nbsp;<input style="width:60px" data-dojo-props="constraints: { min:1, max:102400 }" data-dojo-type="dijit/form/NumberSpinner" id="randomLength" value="1024"/>Length';
        bodyText += '&nbsp;&nbsp;<input type="checkbox" id="special" data-dojo-type="dijit.form.CheckBox" value="10"/>Special';

        clearDijitWidget('changepassword-popup');
        var theDialog = new dijit.Dialog({
            id: 'changepassword-popup',
            title: 'Set Password',
            style: "width: 550px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('changepassword-popup');
            }
        });
        theDialog.show();

        require(["dojo/parser","dijit/form/Textarea","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });

        setTimeout(function(){ getObject('password1').focus();},500);
    });
};

// -------------------------- action handler ------------------------------------

var ActionHandler = {};

ActionHandler.init = function(keyName) {
    console.log('ActionHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;
    clearDivElements(parentDiv, true);
    readSetting(keyName, function(resultValue) {
        clientSettingCache[keyName] = resultValue;
        ActionHandler.redraw(keyName);
    });
};

ActionHandler.redraw = function(keyName) {
    console.log('ActionHandler redraw for ' + keyName)
    var resultValue = clientSettingCache[keyName];
    var parentDiv = 'table_setting_' + keyName;
    clearDivElements(parentDiv, false);
    var parentDivElement = getObject(parentDiv);

    if (!isEmpty(resultValue)) {
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

    {
        var newTableRow = document.createElement("tr");
        newTableRow.setAttribute("style", "border-width: 0");
        newTableRow.setAttribute("colspan", "5");

        var newTableData = document.createElement("td");
        newTableData.setAttribute("style", "border-width: 0; width: 50px");

        var addItemButton = document.createElement("button");
        addItemButton.setAttribute("type", "button");
        addItemButton.setAttribute("onclick", "ActionHandler.addMultiSetting('" + keyName + "','" + parentDiv + "');");
        addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
        addItemButton.innerHTML = "Add Value";
        newTableData.appendChild(addItemButton);

        newTableRow.appendChild(newTableData);
        parentDivElement.appendChild(newTableRow);
    }
    require(["dojo/parser","dijit/form/Button","dijit/form/FilteringSelect","dijit/form/Textarea"],function(dojoParser){
        dojoParser.parse(parentDiv);
    });
};

ActionHandler.drawRow = function(parentDiv, settingKey, iteration, value) {
    var inputID = 'value_' + settingKey + '_' + iteration + "_";

    // clear the old dijit node (if it exists)
    clearDijitWidget(inputID + "name");
    clearDijitWidget(inputID + "description");
    clearDijitWidget(inputID + "type");
    clearDijitWidget(inputID + "optionsButton");

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    {
        {
            var td1 = document.createElement("td");
            td1.setAttribute("style", "border-width: 0; width:50px");
            var nameInput = document.createElement("input");
            nameInput.setAttribute("id", inputID + "name");
            nameInput.setAttribute("value", value['name']);
            nameInput.setAttribute("onchange","clientSettingCache['" + settingKey + "'][" + iteration + "]['name'] = this.value;ActionHandler.writeFormSetting('" + settingKey + "')");
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
            descriptionInput.setAttribute("readonly", "readonly");
            descriptionInput.setAttribute("onclick","ActionHandler.showOptionsDialog('" + settingKey + "'," + iteration + ")");
            descriptionInput.setAttribute("onkeypress","ActionHandler.showOptionsDialog('" + settingKey + "'," + iteration + ")");
            descriptionInput.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            td2.appendChild(descriptionInput);
            newTableRow.appendChild(td2);
        }

        {
            var td3 = document.createElement("td");
            td3.setAttribute("style", "border-width: 0");
            var optionList = PWM_GLOBAL['actionTypeOptions'];
            var typeSelect = document.createElement("select");
            typeSelect.setAttribute("data-dojo-type", "dijit.form.FilteringSelect");
            typeSelect.setAttribute("id", inputID + "type");
            typeSelect.setAttribute("style","width: 90px");
            typeSelect.setAttribute("onchange","clientSettingCache['" + settingKey + "'][" + iteration + "]['type'] = this.value;ActionHandler.writeFormSetting('" + settingKey + "')");
            for (var optionItem in optionList) {
                var optionElement = document.createElement("option");
                optionElement.value = optionList[optionItem];
                optionElement.innerHTML = optionList[optionItem];
                if (optionList[optionItem] == clientSettingCache[settingKey][iteration]['type']) {
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
        imgElement.setAttribute("style", "width: 15px; height: 15px");
        imgElement.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
        imgElement.setAttribute("onclick", "ActionHandler.removeMultiSetting('" + settingKey + "','" + iteration + "')");
        tdFinal.appendChild(imgElement);
        newTableRow.appendChild(tdFinal);
    }
    var parentDivElement = getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
};

ActionHandler.writeFormSetting = function(settingKey) {
    var cachedSetting = clientSettingCache[settingKey];
    writeSetting(settingKey, cachedSetting);
};

ActionHandler.removeMultiSetting = function(keyName, iteration) {
    delete clientSettingCache[keyName][iteration];
    console.log("removed iteration " + iteration + " from " + keyName + ", cached keyValue=" + clientSettingCache[keyName]);
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.redraw(keyName);
};

ActionHandler.addMultiSetting = function(keyName) {
    var currentSize = 0;
    for (var loopVar in clientSettingCache[keyName]) {
        currentSize++;
    }
    clientSettingCache[keyName][currentSize + 1] = {};
    clientSettingCache[keyName][currentSize + 1]['name'] = '';
    clientSettingCache[keyName][currentSize + 1]['description'] = '';
    clientSettingCache[keyName][currentSize + 1]['type'] = 'webservice';
    clientSettingCache[keyName][currentSize + 1]['method'] = 'get';
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.redraw(keyName)
};

ActionHandler.showOptionsDialog = function(keyName, iteration) {
    require(["dojo/store/Memory","dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/Select","dijit/form/ValidationTextBox"],function(Memory){
        var inputID = 'value_' + keyName + '_' + iteration + "_";
        var bodyText = '<table style="border:0">';
        if (clientSettingCache[keyName][iteration]['type'] == 'webservice') {
            bodyText += '<tr>';
            bodyText += '<td style="border:0; text-align: center" colspan="2">Web Service</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">&nbsp;</td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">Method</td><td style="border:0;"><select id="' + inputID + 'method' + '"/></td>';
            bodyText += '</tr><tr>';
            //bodyText += '<td style="border:0; text-align: right">Client Side</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'clientSide' + '"/></td>';
            //bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">URL</td><td style="border:0;"><input type="text" id="' + inputID + 'url' + '"/></td>';
            bodyText += '</tr><tr>';
            bodyText += '<td style="border:0; text-align: right">Body</td><td style="border:0;"><input type="text" id="' + inputID + 'body' + '"/></td>';
            bodyText += '</tr>';
        } else if (clientSettingCache[keyName][iteration]['type'] == 'ldap') {
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
        bodyText += '<td style="border:0; text-align: right">Description</td><td style="border:0;"><input type="text" id="' + inputID + 'description' + '"/></td>';
        bodyText += '</tr>';
        bodyText += '</table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');ActionHandler.redraw(\'' + keyName + '\')">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Options for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
                ActionHandler.redraw(keyName);
            }
        });
        theDialog.show();

        if (clientSettingCache[keyName][iteration]['type'] == 'webservice') {
            clearDijitWidget(inputID + "method");
            new dijit.form.Select({
                value: clientSettingCache[keyName][iteration]['method'],
                options: [
                    { label: "Delete", value: "delete" },
                    { label: "Get", value: "get" },
                    { label: "Post", value: "post" },
                    { label: "Put", value: "put" }
                ],
                style: "width: 80px",
                onChange: function(){clientSettingCache[keyName][iteration]['method'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "method");

            //clearDijitWidget(inputID + "clientSide");
            //new dijit.form.CheckBox({
            //    checked: clientSettingCache[keyName][iteration]['clientSide'],
            //    onChange: function(){clientSettingCache[keyName][iteration]['clientSide'] = this.checked;ActionHandler.writeFormSetting(keyName)}
            //},inputID + "clientSide");

            clearDijitWidget(inputID + "url");
            new dijit.form.Textarea({
                value: clientSettingCache[keyName][iteration]['url'],
                required: true,
                onChange: function(){clientSettingCache[keyName][iteration]['url'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "url");

            clearDijitWidget(inputID + "body");
            new dijit.form.Textarea({
                value: clientSettingCache[keyName][iteration]['body'],
                onChange: function(){clientSettingCache[keyName][iteration]['body'] = this.checked;ActionHandler.writeFormSetting(keyName)}
            },inputID + "body");

        } else if (clientSettingCache[keyName][iteration]['type'] == 'ldap') {
            clearDijitWidget(inputID + "attributeName");
            new dijit.form.ValidationTextBox({
                value: clientSettingCache[keyName][iteration]['attributeName'],
                required: true,
                onChange: function(){clientSettingCache[keyName][iteration]['attributeName'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "attributeName");

            clearDijitWidget(inputID + "attributeValue");
            new dijit.form.Textarea({
                value: clientSettingCache[keyName][iteration]['attributeValue'],
                required: true,
                onChange: function(){clientSettingCache[keyName][iteration]['attributeValue'] = this.value;ActionHandler.writeFormSetting(keyName)}
            },inputID + "attributeValue");
        }
        clearDijitWidget(inputID + "description");
        new dijit.form.Textarea({
            value: clientSettingCache[keyName][iteration]['description'],
            onChange: function(){clientSettingCache[keyName][iteration]['description'] = this.value;ActionHandler.writeFormSetting(keyName)}
        },inputID + "description");
    });
};








function saveConfiguration(waitForReload) {
    require(["dojo"],function(dojo){
        showWaitDialog('Saving Configuration...', null);
        dojo.xhrGet({
            url:"ConfigManager?processAction=getOptions",
            preventCache: true,
            sync: true,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                var oldEpoch = data != null ? data['configEpoch'] : null;
                dojo.xhrGet({
                    url:"ConfigManager?processAction=finishEditing&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    preventCache: true,
                    dataType: "json",
                    handleAs: "json",
                    load: function(data){
                        if (data['error'] == 'true') {
                            closeWaitDialog();
                            showError(data['errorMessage']);
                        } else {
                            if (waitForReload) {
                                var currentTime = new Date().getTime();
                                showError('Waiting for server restart');
                                setTimeout(function() {
                                    waitForRestart(currentTime, oldEpoch);
                                }, 2 * 1000);
                            } else {
                                window.location.reload();
                            }
                        }
                    }
                });
            },
            error: function(error) {
                alert(error);
                window.location = "ConfigManager?unable_to_read_current_epoch"; //refresh page
            }
        });
    });
}

function finalizeConfiguration() {
    require(["dojo"],function(dojo){
        showWaitDialog('Finalizing Configuration...', null);

        dojo.xhrGet({
            url:"ConfigManager?processAction=getOptions",
            preventCache: true,
            sync: false,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=lockConfiguration&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    sync:true,
                    preventCache: true
                });
                var oldEpoch = data['configEpoch'];
                var currentTime = new Date().getTime();
                showError('Waiting for server restart');
                setTimeout(function() {
                    waitForRestart(currentTime, oldEpoch);
                }, 1000);
            },
            error: function(error) {
                alert(error);
                window.location = "ConfigManager?unable_to_read_current_epoch"; //refresh page
            }
        });
    });
}


function waitForRestart(startTime, oldEpoch) {
    require(["dojo"],function(dojo){
        var currentTime = new Date().getTime();
        dojo.xhrGet({
            url:"ConfigManager?processAction=getOptions",
            preventCache: true,
            sync: true,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                var error = data != null ? data['error'] : null;
                if (error) {
                    clearDijitWidget('waitDialogID');
                    showError(data['errorDetail']);
                    return;
                }

                if (data != null) {
                    var epoch = data['configEpoch'];
                    if (epoch != oldEpoch) {
                        window.location = "ConfigManager"; //refresh page
                        return;
                    }
                }

                if (currentTime - startTime > 4 * 60 * 1000) { // timeout
                    alert('Configuration save successful.   Unable to restart PWM, please restart the java application server.');
                    showError('PWM Server has not restarted (timeout)');
                } else {
                    showError('Waiting for server restart, server has not yet restarted');
                    setTimeout(function() {
                        waitForRestart(startTime, oldEpoch)
                    }, 3000);
                }
            },
            error: function(error) {
                setTimeout(function() {
                    waitForRestart(startTime, oldEpoch)
                }, 1000);
                console.log('Waiting for server restart, unable to contact server: ' + error);
            }
        });
    });
}

function handleResetClick(settingKey) {
    var confirmLabel = 'Are you sure you want to reset this setting to the default value?';
    if (confirm(confirmLabel)) {
        resetSetting(settingKey);
        window.location = window.location;
    }
}

function startNewConfigurationEditor(template) {
    require(["dojo"],function(dojo){
        showWaitDialog('Loading...','');
        dojo.xhrGet({
            url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&getTemplate=" + template,
            preventCache: true,
            error: function(errorObj) {
                showError("error starting configuration editor: " + errorObj);
            },
            load: function() {
                window.location = "ConfigManager?processAction=editMode&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + '&mode=SETTINGS';
            }
        });
    });
}

function setCookie(c_name,value,exseconds)
{
    var exdate=new Date();
    exdate.setTime(exdate.getTime + (exseconds * 1000));
    var c_value=escape(value) + ((exseconds==null) ? "" : "; expires="+exdate.toUTCString());
    document.cookie=c_name+"=" + c_value;
}

function getCookie(c_name)
{
    var i,x,y,ARRcookies=document.cookie.split(";");
    for (i=0;i<ARRcookies.length;i++)
    {
        x=ARRcookies[i].substr(0,ARRcookies[i].indexOf("="));
        y=ARRcookies[i].substr(ARRcookies[i].indexOf("=")+1);
        x=x.replace(/^\s+|\s+$/g,"");
        if (x==c_name)
        {
            return unescape(y);
        }
    }
}

function readInitialTextBasedValue(key) {
    require(["dijit/registry"],function(registry){
        readSetting(key, function(dataValue) {
            getObject('value_' + key).value = dataValue;
            getObject('value_' + key).disabled = false;
            registry.byId('value_' + key).set('disabled', false);
            registry.byId('value_' + key).startup();
            try {registry.byId('value_' + key).validate(false);} catch (e) {}
            try {registry.byId('value_verify_' + key).validate(false);} catch (e) {}
        });
    });
}

function writeConfigurationNotes() {
    require(["dojo","dijit/Dialog"],function(dojo){
        var value = getObject('configNotesDialog').value;
        showWaitDialog();
        dojo.xhrPost({
            url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&updateNotesText=true",
            postData: dojo.toJson(value),
            contentType: "application/json;charset=utf-8",
            dataType: "json",
            handleAs: "text",
            load: function(data){
                closeWaitDialog();
                buildMenuBar();
            },
            error: function(errorObj) {
                closeWaitDialog();
                showError("error saving notes text: " + errorObj);
                buildMenuBar();
            }
        });
    });
}

function showConfigurationNotes() {
    require(["dojo","dijit/form/Textarea","dijit/Dialog","dojo/_base/connect"],function(dojo){

        setCookie("seen-notes","true", 60 * 60);
        var idName = 'configNotesDialog';
        var bodyText = '<textarea cols="40" rows="10" style="width: 575px; height: 300px; resize:none" onchange="writeConfigurationNotes()" disabled="true" id="' + idName + '">';
        bodyText += 'Loading...';
        bodyText += '</textarea>';
        bodyText += '<button onclick="writeConfigurationNotes()" class="btn">OK</button>';

        closeWaitDialog();
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Configuration Notes',
            style: "width: 600px;",
            content: bodyText
        });
        theDialog.show();

        dojo.xhrGet({
            url:"ConfigManager?processAction=getOptions&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            dataType: "json",
            handleAs: "json",
            error: function(errorObj) {
                closeWaitDialog();
                showError("error reading notes text: " + errorObj)
            },
            load: function(data){
                var value = data['notesText'];
                getObject(idName).value = value;
                getObject(idName).disabled = false;
            }
        });
    });
}

function isEmpty(o) {
    for (var key in o) if (o.hasOwnProperty(key)) return false;
    return true;
}

