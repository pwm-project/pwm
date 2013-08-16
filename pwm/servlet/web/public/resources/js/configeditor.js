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
                showError("Unable to communicate with server.  Please refresh page.");
                console.log("error loading " + keyName + ", reason: " + errorObj);
            },
            load: function(data) {
                console.log('read data for setting ' + keyName);
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
                showError("Unable to communicate with server.  Please refresh page.");
                console.log("error writing setting " + keyName + ", reason: " + errorObj)
            },
            load: function(data) {
                console.log('wrote data for setting ' + keyName);
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

    require(["dijit/form/Select","dijit/form/Button"],function(Select,Button){
        var availableLocales = PWM_GLOBAL['localeInfo'];

        var localeMenu = [];
        for (var localeIter in availableLocales) {
            if (localeIter != PWM_GLOBAL['defaultLocale']) {
                localeMenu.push({label: availableLocales[localeIter], value: localeIter})
            }
        }

        clearDijitWidget(keyName + '-addLocaleValue');
        new Select({
            id: keyName + '-addLocaleValue',
            options: localeMenu,
            style: 'width: 175px'
        }, keyName + '-addLocaleValue');

        clearDijitWidget(keyName + '-addLocaleButton');
        new Button({
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
        td1.setAttribute("style", "border-width: 0; width: 15px");

        if (localeString == null || localeString.length < 1) {
            td1.innerHTML = "";
        } else {
            td1.innerHTML = localeString;
        }
        newTableRow.appendChild(td1);

    }
    {
        var td2 = document.createElement("td");
        td2.setAttribute("style", "border-width: 0");
        if (syntax == 'LOCALIZED_TEXT_AREA') {
            var textAreaElement = document.createElement("textarea");
            textAreaElement.setAttribute("id", inputID);
            textAreaElement.setAttribute("value", "[Loading....]");
            textAreaElement.setAttribute("onchange", "LocaleTableHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            textAreaElement.setAttribute("style", "width: 520px;");
            textAreaElement.setAttribute("data-dojo-type", "dijit.form.Textarea");
            textAreaElement.setAttribute("value", value);
            td2.appendChild(textAreaElement);
        } else {
            var inputElement = document.createElement("input");
            inputElement.setAttribute("id", inputID);
            inputElement.setAttribute("value", "[Loading....]");
            inputElement.setAttribute("onchange", "LocaleTableHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            inputElement.setAttribute("style", "width: 520px");
            inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("value", value);
            td2.appendChild(inputElement);
        }
        newTableRow.appendChild(td2);

        if (localeString != null && localeString.length > 0) {
            var imgElement = document.createElement("img");
            imgElement.setAttribute("style", "width: 10px; height: 10px");
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
    require(["dojo/parser","dijit/form/Button","dijit/form/Textarea","dijit/form/ValidationTextBox"],function(dojoParser){
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
            inputElement.setAttribute("style", "width: 550px");
            inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
            td1.appendChild(inputElement);
            newTableRow.appendChild(td1);


            if (itemCount(clientSettingCache[settingKey]) > 1) {
                var imgElement = document.createElement("img");
                imgElement.setAttribute("style", "width: 10px; height: 10px");
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

                var imgElement = document.createElement("img");
                imgElement.setAttribute("style", "width: 10px; height: 10px");
                imgElement.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
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
                addItemButton.setAttribute("onclick", "clientSettingCache['" + keyName + "']['" + localeName + "'].push('');MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "',null,null,null,'" + regExPattern + "')");
                addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
                addItemButton.innerHTML = "Add Value";
                newTableData.appendChild(addItemButton);

                newTableRow.appendChild(newTableData);
                localeTableElement.appendChild(newTableRow);
            }


            if (localeName != '') { // add remove locale x
                var imgElement2 = document.createElement("img");
                imgElement2.setAttribute("style", "width: 12px; height: 12px;");
                imgElement2.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
                imgElement2.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "',null,null,'" + regExPattern + "')");
                var tdElement = document.createElement("td");
                tdElement.setAttribute("style", "border-width: 0; text-align: left; vertical-align: top;width 10px");

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
    if (locale != null) {
        if (clientSettingCache[settingKey][locale] == null) {
            clientSettingCache[settingKey][locale] = [ "" ];
        }

        if (iteration == null) {
            delete clientSettingCache[settingKey][locale];
        } else {
            if (value == null) {
                clientSettingCache[settingKey][locale].splice(iteration,1);
            } else {
                clientSettingCache[settingKey][locale][iteration] = value;
            }
        }
    }

    writeSetting(settingKey, clientSettingCache[settingKey]);
    var parentDiv = 'table_setting_' + settingKey;
    MultiLocaleTableHandler.draw(parentDiv, settingKey, regExPattern);
};

// -------------------------- form table handler ------------------------------------

var FormTableHandler = {};

FormTableHandler.init = function(keyName,options) {
    console.log('FormTableHandler init for ' + keyName);
    var parentDiv = 'table_setting_' + keyName;
    clientSettingCache[keyName + '_options'] = options;
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
    require(["dojo/parser","dijit/form/Button","dijit/form/Select"],function(dojoParser){
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
            typeSelect.setAttribute("data-dojo-type", "dijit.form.Select");
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
            imgElement.setAttribute("style", "width: 10px; height: 10px");
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
        if (clientSettingCache[keyName + '_options']['readonly'] == 'show') {
            bodyText += '<td style="border:0; text-align: right">Read Only</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'readonly' + '"/></td>';
            bodyText += '</tr><tr>';
        }
        if (clientSettingCache[keyName + '_options']['unique'] == 'show') {
            bodyText += '<td style="border:0; text-align: right">Unique</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'unique' + '"/></td>';
            bodyText += '</tr><tr>';
        }
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
            bodyText += '<td style="border:0; text-align: right">Select Options</td><td style="border:0;"><input class="menubutton" type="button" id="' + inputID + 'selectOptions' + '" value="Edit" onclick="FormTableHandler.showSelectOptionsDialog(\'' + keyName + '\',\'' + iteration + '\')"/></td>';
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

        if (clientSettingCache[keyName + '_options']['readonly'] == 'show') {
            clearDijitWidget(inputID + "readonly");
            new dijit.form.CheckBox({
                checked: clientSettingCache[keyName][iteration]['readonly'],
                onChange: function(){clientSettingCache[keyName][iteration]['readonly'] = this.checked;FormTableHandler.writeFormSetting(keyName)}
            },inputID + "readonly");
        }

        if (clientSettingCache[keyName + '_options']['unique'] == 'show') {
            clearDijitWidget(inputID + "unique");
            new dijit.form.CheckBox({
                checked: clientSettingCache[keyName][iteration]['unique'],
                onChange: function(){clientSettingCache[keyName][iteration]['unique'] = this.checked;FormTableHandler.writeFormSetting(keyName)}
            },inputID + "unique");
        }

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
    require(["dijit/Dialog","dijit/form/ValidationTextBox","dijit/form/Button","dijit/form/TextBox"],function(Dialog,ValidationTextBox,Button,TextBox){
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
            new TextBox({
                onChange: function(){clientSettingCache[keyName][iteration]['selectOptions'][optionName] = this.value;FormTableHandler.writeFormSetting(keyName)}
            },loopID);
        }

        clearDijitWidget("addSelectOptionName");
        new ValidationTextBox({
            placeholder: "Name",
            id: "addSelectOptionName",
            constraints: {min: 1}
        },"addSelectOptionName");

        clearDijitWidget("addSelectOptionValue");
        new ValidationTextBox({
            placeholder: "Display Value",
            id: "addSelectOptionValue",
            constraints: {min: 1}
        },"addSelectOptionValue");

        clearDijitWidget("addSelectOptionButton");
        new Button({
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

ChangePasswordHandler.init = function(settingKey,settingName,writeFunction) {
    if (!clientSettingCache[settingKey]) {
        clientSettingCache[settingKey] = {};
    }
    if (!clientSettingCache[settingKey]['settings']) {
        clientSettingCache[settingKey]['settings'] = {};
    }
    clientSettingCache[settingKey]['settings']['name'] = settingName;
    if (writeFunction) {
        clientSettingCache[settingKey]['settings']['writeFunction'] = writeFunction;
    } else {
        clientSettingCache[settingKey]['settings']['writeFunction'] = 'ChangePasswordHandler.doChange(\'' + settingKey + '\')';
    }
    clientSettingCache[settingKey]['settings']['showFields'] = false;
    ChangePasswordHandler.clear(settingKey);
    ChangePasswordHandler.changePasswordPopup(settingKey);
}

ChangePasswordHandler.validatePasswordPopupFields = function() {
    require(["dojo","dijit/registry"],function(dojo,registry){
        var password1 = registry.byId('password1').get('value');
        var password2 = registry.byId('password2').get('value');

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
    });
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
    var password1 = clientSettingCache[settingKey]['settings']['p1'];
    clearDijitWidget('dialogPopup');
    writeSetting(settingKey,password1);
    showInfo(clientSettingCache[settingKey]['settings']['name'] + ' password recorded ');
    clear(settingKey);
};

ChangePasswordHandler.clear = function(settingKey) {
    clientSettingCache[settingKey]['settings']['p1'] = '';
    clientSettingCache[settingKey]['settings']['p2'] = '';
}

ChangePasswordHandler.generateRandom = function(settingKey) {
    ChangePasswordHandler.clear(settingKey);
    if (!clientSettingCache[settingKey]['settings']['showFields']) {
        clientSettingCache[settingKey]['settings']['showFields'] = true;
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
        getObject('generateButton').disabled = true;
        getObject('generateButton').innerHTML = "Loading...";

        dojo.xhrPost({
            url:PWM_GLOBAL['url-restservice'] + "/randompassword?pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            preventCache: true,
            postData: postData,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                registry.byId('password1').set('value',data['data']['password']);
                registry.byId('password2').set('value','');
                getObject('generateButton').disabled = false;
                getObject('generateButton').innerHTML = "Random";
            },
            error: function(error) {
                getObject('generateButton').disabled = false;
                getObject('generateButton').innerHTML = "Random";
                alert('error reading random password: ' + error);
            }
        });
    });
};

ChangePasswordHandler.changePasswordPopup = function(settingKey) {
    var writeFunction = clientSettingCache[settingKey]['settings']['writeFunction'];
    require(["dojo/parser","dijit/registry","dijit/Dialog","dijit/form/Textarea","dijit/form/TextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],
        function(dojoParser,registry,Dialog,Textarea,TextBox)
        {
            var bodyText = '<div id="changePasswordDialogDiv">';
            bodyText += '<span id="message" class="message message-info">' + clientSettingCache[settingKey]['settings']['name'] + '</span><br/>';
            bodyText += '<table style="border: 0">';
            bodyText += '<tr style="border: 0"><td style="border: 0"><input name="password1" id="password1" class="inputfield" style="width: 500px; max-height: 200px; overflow: auto" autocomplete="off"></input></td>';
            bodyText += '</tr><tr style="border: 0">';
            bodyText += '<td style="border: 0" xmlns="http://www.w3.org/1999/html"><input name="password2" id="password2" class="inputfield" style="width: 500px; max-height: 200px; overflow: auto;" autocomplete="off"/></input></td>';

            bodyText += '<td style="border: 0"><div style="margin:0;">';
            bodyText += '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/greenCheck.png">';
            bodyText += '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png">';
            bodyText += '</div></td>';

            bodyText += '</tr></table>';
            bodyText += '<button name="change" class="btn" id="password_button" onclick="' + writeFunction + '" disabled="true"/>';
            bodyText += 'Store Password</button>&nbsp;&nbsp;';
            bodyText += '<button id="generateButton" name="generateButton" class="btn" onclick="ChangePasswordHandler.generateRandom(\'' + settingKey + '\')">Random</button>';
            bodyText += '&nbsp;&nbsp;<input style="width:60px" data-dojo-props="constraints: { min:1, max:102400 }" data-dojo-type="dijit/form/NumberSpinner" id="randomLength" value="32"/>Length';
            bodyText += '&nbsp;&nbsp;<input type="checkbox" id="special" data-dojo-type="dijit/form/CheckBox" value="10"/>Special';
            bodyText += '&nbsp;&nbsp;<input type="checkbox" id="show" data-dojo-type="dijit/form/CheckBox" data-dojo-props="checked:' + clientSettingCache[settingKey]['settings']['showFields'] + '" value="10"/>Show';
            bodyText += '</div>';

            clearDijitWidget('dialogPopup');
            var theDialog = new dijit.Dialog({
                id: 'dialogPopup',
                title: 'Store Password',
                style: "width: 550px",
                content: bodyText,
                hide: function(){
                    clearDijitWidget('dialogPopup');
                    ChangePasswordHandler.clear(settingKey);
                }
            });
            theDialog.show();
            registry.byId('show').set('onChange',function(){
                clientSettingCache[settingKey]['settings']['showFields'] = this.checked;
                ChangePasswordHandler.changePasswordPopup(settingKey);
            });

            dojoParser.parse(getObject('changePasswordDialogDiv'));

            var p1 = clientSettingCache[settingKey]['settings']['p1'];
            var p2 = clientSettingCache[settingKey]['settings']['p2'];
            if (clientSettingCache[settingKey]['settings']['showFields']) {
                new Textarea({
                    id: 'password1',
                    onKeyUp: function(){
                        clientSettingCache[settingKey]['settings']['p1'] = this.get('value');
                        ChangePasswordHandler.validatePasswordPopupFields();
                        registry.byId('password2').set('value','')
                    },
                    value: p1
                },'password1');
                new Textarea({
                    id: 'password2',
                    onKeyUp: function(){
                        clientSettingCache[settingKey]['settings']['p2'] = this.get('value');
                        ChangePasswordHandler.validatePasswordPopupFields();
                    },
                    value: p2
                },'password2');
            } else {
                new TextBox({
                    id: 'password1',
                    type: 'password',
                    style: 'width: 100%',
                    onKeyUp: function(){
                        clientSettingCache[settingKey]['settings']['p1'] = this.get('value');
                        ChangePasswordHandler.validatePasswordPopupFields();
                        registry.byId('password2').set('value','')
                    },
                    value: p1
                },'password1');
                new TextBox({
                    id: 'password2',
                    type: 'password',
                    style: 'width: 100%',
                    onKeyUp: function(){
                        clientSettingCache[settingKey]['settings']['p2'] = this.get('value');
                        ChangePasswordHandler.validatePasswordPopupFields();
                    },
                    value: p2
                },'password2');
            }
            getObject('password1').focus();
            ChangePasswordHandler.validatePasswordPopupFields();
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
    require(["dojo/parser","dijit/form/Button","dijit/form/Select","dijit/form/Textarea"],function(dojoParser){
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
            descriptionInput.setAttribute("onchange","clientSettingCache['" + settingKey + "'][" + iteration + "]['description'] = this.value;ActionHandler.writeFormSetting('" + settingKey + "')");
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
        imgElement.setAttribute("style", "width: 10px; height: 10px");
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
            bodyText += '<td style="border:0; text-align: right">Headers</td><td style="border:0;"><button class="menubutton" onclick="ActionHandler.showHeadersDialog(\'' + keyName + '\',\'' + iteration + '\')">Edit</button></td>';
            bodyText += '</tr><tr>';
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
        bodyText += '</tr>';
        bodyText += '</table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');ActionHandler.redraw(\'' + keyName + '\')">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Options for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 650px",
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
                onChange: function(){clientSettingCache[keyName][iteration]['body'] = this.value;ActionHandler.writeFormSetting(keyName)}
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
        for (var headerName in clientSettingCache[keyName][iteration]['headers']) {
            var value = clientSettingCache[keyName][iteration]['headers'][headerName];
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

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Http Headers for webservice ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
                ActionHandler.showOptionsDialog(keyName,iteration);
            }
        });
        theDialog.show();

        for (var headerName in clientSettingCache[keyName][iteration]['headers']) {
            var value = clientSettingCache[keyName][iteration]['headers'][headerName];
            var loopID = inputID + headerName;
            clearDijitWidget(loopID);
            new TextBox({
                onChange: function(){clientSettingCache[keyName][iteration]['headers'][headerName] = this.value;ActionHandler.writeFormSetting(keyName)}
            },loopID);
        }

        clearDijitWidget("addHeaderName");
        new ValidationTextBox({
            placeholder: "Name",
            id: "addHeaderName",
            constraints: {min: 1}
        },"addHeaderName");

        clearDijitWidget("addHeaderValue");
        new ValidationTextBox({
            placeholder: "Display Value",
            id: "addHeaderValue",
            constraints: {min: 1}
        },"addHeaderValue");

        clearDijitWidget("addHeaderButton");
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

    if (!clientSettingCache[keyName][iteration]['headers']) {
        clientSettingCache[keyName][iteration]['headers'] = {};
    }

    clientSettingCache[keyName][iteration]['headers'][headerName] = headerValue;
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.showHeadersDialog(keyName, iteration);
};

ActionHandler.removeHeader = function(keyName, iteration, headerName) {
    delete clientSettingCache[keyName][iteration]['headers'][headerName];
    ActionHandler.writeFormSetting(keyName);
    ActionHandler.showHeadersDialog(keyName, iteration);
};

// -------------------------- email table handler ------------------------------------

var EmailTableHandler = {};

EmailTableHandler.init = function(keyName) {
    console.log('EmailTableHandler init for ' + keyName);
    readSetting(keyName, function(resultValue) {
        clientSettingCache[keyName] = resultValue;
        EmailTableHandler.draw(keyName);
    });
};

EmailTableHandler.draw = function(keyName) {
    var resultValue = clientSettingCache[keyName];
    var parentDiv = 'table_setting_' + keyName;
    clearDivElements(parentDiv, true);
    require(["dojo/parser","dojo/html","dijit/form/ValidationTextBox","dijit/form/Textarea"],
        function(dojoParser,dojoHtml,ValidationTextBox,Textarea){
            clearDivElements(parentDiv, false);
            for (var localeName in resultValue) {
                EmailTableHandler.drawRow(keyName,localeName,parentDiv)
            }

            if (isEmpty(resultValue)) {
                var newTableRow = document.createElement("tr");
                newTableRow.setAttribute("style", "border-width: 0");
                newTableRow.setAttribute("colspan", "5");

                var newTableData = document.createElement("td");
                newTableData.setAttribute("style", "border-width: 0;");

                var addItemButton = document.createElement("button");
                addItemButton.setAttribute("type", "[button");
                addItemButton.setAttribute("onclick", "resetSetting('" + keyName + "');loadMainPageBody()");
                addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
                addItemButton.innerHTML = "Add Value";
                newTableData.appendChild(addItemButton);

                newTableRow.appendChild(newTableData);
                var parentDivElement = getObject(parentDiv);
                parentDivElement.appendChild(newTableRow);
            } else {
                var addLocaleFunction = function() {
                    require(["dijit/registry"],function(registry){
                        var localeValue = registry.byId(keyName + "-addLocaleValue").value;
                        if (!clientSettingCache[keyName][localeValue]) {
                            clientSettingCache[keyName][localeValue] = {};
                            EmailTableHandler.writeSetting(keyName,true);
                        }
                    });
                };
                addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction);
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
            localeTableElement.setAttribute("style", "border-width: 1px; width:515px; margin:0");
            localeTdContent.appendChild(localeTableElement);

            var idPrefix = "setting_" + localeName + "_" + keyName;
            var htmlBody = '';
            htmlBody += '<table>';
            htmlBody += '<tr style="border:0"><td style="border:0; width:30px; text-align:right">From</td>';
            htmlBody += '<td style="border:0"><input id="' + idPrefix + '_from"/></td></tr>';
            htmlBody += '<tr style="border:0"><td style="border:0; width:30px; text-align:right">Subject</td>';
            htmlBody += '<td style="border:0"><input id="' + idPrefix + '_subject"/></td></tr>';
            htmlBody += '<tr style="border:0"><td style="border:0; width:30px; text-align:right">Plain Body</td>';
            htmlBody += '<td style="border:0"><input id="' + idPrefix + '_bodyPlain"/></td></tr>';
            htmlBody += '<tr style="border:0"><td style="border:0; width:30px; text-align:right">HTML Body</td>';
            htmlBody += '<td style="border:0"><div style="border:2px solid #EAEAEA; background: white; width: 446px" onclick="EmailTableHandler.popupEditor(\'' + keyName + '\',\'' + localeName + '\')">';
            htmlBody += clientSettingCache[keyName][localeName]['bodyHtml'] ? clientSettingCache[keyName][localeName]['bodyHtml'] : "&nbsp;" ;
            htmlBody += '</div></td></tr>';
            htmlBody += "</table>"
            dojoHtml.set(localeTableElement,htmlBody);
            var parentDivElement = getObject(parentDiv);
            parentDivElement.appendChild(localeTableRow);

            clearDijitWidget(idPrefix + "_from");
            new ValidationTextBox({
                value: clientSettingCache[keyName][localeName]['from'],
                style: 'width: 450px',
                required: true,
                onChange: function(){clientSettingCache[keyName][localeName]['from'] = this.value;EmailTableHandler.writeSetting(keyName)}
            },idPrefix + "_from");

            clearDijitWidget(idPrefix + "_subject");
            new ValidationTextBox({
                value: clientSettingCache[keyName][localeName]['subject'],
                style: 'width: 450px',
                required: true,
                onChange: function(){clientSettingCache[keyName][localeName]['subject'] = this.value;EmailTableHandler.writeSetting(keyName)}
            },idPrefix + "_subject");

            clearDijitWidget(idPrefix + "_bodyPlain");
            new Textarea({
                value: clientSettingCache[keyName][localeName]['bodyPlain'],
                style: 'width: 450px',
                required: true,
                onChange: function(){clientSettingCache[keyName][localeName]['bodyPlain'] = this.value;EmailTableHandler.writeSetting(keyName)}
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

            if (localeName != '' || itemCount(clientSettingCache[keyName])){ // add remove locale x
                var imgElement2 = document.createElement("img");
                imgElement2.setAttribute("style", "width: 12px; height: 12px;");
                imgElement2.setAttribute("src", PWM_GLOBAL['url-resources'] + "/redX.png");
                imgElement2.setAttribute("onclick", "delete clientSettingCache['" + keyName + "']['" + localeName + "'];EmailTableHandler.writeSetting('" + keyName + "',true)");
                var tdElement = document.createElement("td");
                tdElement.setAttribute("style", "border-width: 0; text-align: left; vertical-align: top");

                localeTableRow.appendChild(tdElement);
                tdElement.appendChild(imgElement2);
            }
        });
}


EmailTableHandler.popupEditor = function(keyName, localeName) {
    require(["dijit/Dialog","dijit/Editor","dijit/_editor/plugins/AlwaysShowToolbar","dijit/_editor/plugins/LinkDialog","dijit/_editor/plugins/ViewSource","dijit/_editor/plugins/FontChoice","dijit/_editor/plugins/TextColor"],
        function(Dialog,Editor,AlwaysShowToolbar){
            var idValue = keyName + "_" + localeName + "_htmlEditor";
            var idValueDialog = idValue + "_Dialog";
            var bodyText = '';
            bodyText += '<div id="' + idValue + '" style="border:2px solid #EAEAEA; min-height: 200px;"></div>'
            bodyText += '<br/>'
            bodyText += '<button class="btn" onclick="EmailTableHandler.writeSetting(\'' + keyName + '\',true);clearDijitWidget(\'' + idValueDialog + '\')"> OK </button>'
            clearDijitWidget(idValue);
            clearDijitWidget(idValueDialog);

            var dialog = new Dialog({
                id: idValueDialog,
                title: "HTML Editor",
                style: "width: 650px",
                content: bodyText
            });
            dialog.show();

            new Editor({
                extraPlugins: [
                    AlwaysShowToolbar,"viewsource",
                    {name:"dijit/_editor/plugins/LinkDialog",command:"createLink",urlRegExp:".*"},
                    "fontName","foreColor"
                ],
                height: '',
                value: clientSettingCache[keyName][localeName]['bodyHtml'],
                style: 'width: 630px',
                onChange: function(){clientSettingCache[keyName][localeName]['bodyHtml'] = this.get('value')},
                onKeyUp: function(){clientSettingCache[keyName][localeName]['bodyHtml'] = this.get('value')}
            },idValue).startup();
        });
};


EmailTableHandler.writeSetting = function(settingKey, redraw) {
    var currentValues = clientSettingCache[settingKey];
    writeSetting(settingKey, currentValues);
    if (redraw) {
        EmailTableHandler.draw(settingKey);
    }
};

// -------------------------- boolean handler ------------------------------------

var BooleanHandler = {};

BooleanHandler.init = function(keyName) {
    console.log('BooleanHandler init for ' + keyName);
    var parentDiv = 'button_' + keyName;
    clearDivElements(parentDiv, true);
    require(["dijit/form/ToggleButton"],function(ToggleButton){
        new ToggleButton({
            id: parentDiv,
            iconClass:'dijitCheckBoxIcon',
            disabled: true,
            showLabel: '[Loading...]',
            onChange: function(){BooleanHandler.toggle(keyName,this);}
        },parentDiv);
        readSetting(keyName, function(resultValue) {
            require(["dijit/registry"],function(registry){
                var toggleButtonWidget = registry.byId(parentDiv);
                toggleButtonWidget.set('checked',resultValue);
                toggleButtonWidget.set('disabled',false);
                toggleButtonWidget.set('label','Enabled (True)');
            });
        });
    });
}

BooleanHandler.toggle = function(keyName,widget) {
    writeSetting(keyName,widget.checked);
}

