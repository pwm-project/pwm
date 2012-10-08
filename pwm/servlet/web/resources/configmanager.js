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
            url:"ConfigManager?processAction=readSetting&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&key=" + keyName,
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
            sync: true,
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
                localeMenu.push({name: localeIter + ": " + availableLocales[localeIter], id: localeIter})
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
    require(["dojo/parser","dijit/form/Button","dijit/form/Textarea","dijit/form/ValidationTextBox"],function(dojoParser){
        console.log('LocaleTableHandler init for ' + keyName);
        clearDivElements(parentDiv, true);
        readSetting(keyName, function(resultValue) {
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
        td1.setAttribute("style", "border-width: 0; max-width:10px");

        if (localeString == null || localeString.length < 1) {
            td1.innerHTML = "";
        } else {
            td1.innerHTML = localeString;
        }
        newTableRow.appendChild(td1);

    }
    {
        var td2 = document.createElement("td");
        td2.setAttribute("width", "100%");
        td2.setAttribute("style", "border-width: 0;");
        if (syntax == 'LOCALIZED_TEXT_AREA') {
            var textAreaElement = document.createElement("textarea");
            textAreaElement.setAttribute("id", inputID);
            textAreaElement.setAttribute("value", "[Loading....]");
            textAreaElement.setAttribute("onchange", "LocaleTableHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            textAreaElement.setAttribute("style", "width: 500px;");
            textAreaElement.setAttribute("data-dojo-type", "dijit.form.Textarea");
            textAreaElement.setAttribute("value", value);
            td2.appendChild(textAreaElement);
        } else {
            var inputElement = document.createElement("input");
            inputElement.setAttribute("id", inputID);
            inputElement.setAttribute("value", "[Loading....]");
            inputElement.setAttribute("onchange", "LocaleTableHandler.writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            inputElement.setAttribute("style", "width: 500px");
            inputElement.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("value", value);
            td2.appendChild(inputElement);
        }
        newTableRow.appendChild(td2);

        if (localeString != null && localeString.length > 0) {
            var imgElement = document.createElement("img");
            imgElement.setAttribute("style", "width: 15px; height: 15px");
            imgElement.setAttribute("src", "../resources/redX.png");
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
    LocaleTableHandler.initLocaleTable(parentDiv, keyName, regExPattern, syntax);
};

LocaleTableHandler.addLocaleSetting = function(keyName, parentDiv, regExPattern, syntax) {
    require(["dijit/registry"],function(registry){
        var inputValue = registry.byId(keyName + '-addLocaleValue').value;
        try {
            var existingElementForLocale = getObject('value-' + keyName + '-' + inputValue);
            if (existingElementForLocale == null) {
                LocaleTableHandler.writeLocaleSetting(keyName, inputValue, '');
                LocaleTableHandler.initLocaleTable(parentDiv, keyName, regExPattern, syntax);
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
        clearDivElements(parentDiv, false);
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
        clientSettingCache[keyName] = counter;
        require(["dojo/parser","dijit/form/Button","dijit/form/Textarea"],function(dojoParser){
            dojoParser.parse(parentDiv);
        });
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
                imgElement.setAttribute("src", "../resources/redX.png");
                imgElement.setAttribute("onclick", "MultiTableHandler.removeMultiSetting('" + settingKey + "','" + iteration + "','" + regExPattern + "')");
                td1.appendChild(imgElement);
            }
        }
        var parentDivElement = getObject(parentDiv);
        parentDivElement.appendChild(newTableRow);
    });
};

MultiTableHandler.writeFormSetting = function(settingKey, iteration, value) {
    var size = clientSettingCache[settingKey];
    var currentValues = { };
    for (var i = 0; i < size; i++) {
        var inputID = 'value-' + settingKey + '-' + i;
        currentValues[i] = getObject(inputID).value;
    }
    if (value == null) {
        delete currentValues[iteration];
    } else {
        currentValues[iteration] = value;
    }
    writeSetting(settingKey, currentValues);
};

MultiTableHandler.removeMultiSetting = function(keyName, iteration, regExPattern) {
    var parentDiv = 'table_setting_' + keyName;
    MultiTableHandler.writeFormSetting(keyName, iteration, null);
    MultiTableHandler.initMultiTable(parentDiv, keyName, regExPattern);
};

MultiTableHandler.addMultiSetting = function(keyName, parentDiv, regExPattern) {
    var size = clientSettingCache[keyName];
    MultiTableHandler.writeFormSetting(keyName, size + 1, "");
    MultiTableHandler.initMultiTable(parentDiv, keyName, regExPattern)
};

// -------------------------- multi locale table handler ------------------------------------

var MultiLocaleTableHandler = {};

MultiLocaleTableHandler.initMultiLocaleTable = function(parentDiv, keyName, regExPattern) {
    console.log('MultiLocaleTableHandler init for ' + keyName);
    require(["dojo","dijit/registry","dojo/parser","dijit/form/Button","dijit/form/ValidationTextBox","dijit/form/Textarea","dijit/registry"],function(dojo,registry,dojoParser){
        clearDivElements(parentDiv, true);
        readSetting(keyName, function(resultValue) {
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
                    inputElement.setAttribute("onchange", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "')");
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
                        imgElement.setAttribute("src", "../resources/redX.png");
                        imgElement.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',null);MultiLocaleTableHandler.initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
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
                    addItemButton.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + (multiValues.size + 1) + "','');MultiLocaleTableHandler.initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
                    addItemButton.setAttribute("data-dojo-type", "dijit.form.Button");
                    addItemButton.innerHTML = "Add Value";
                    newTableData.appendChild(addItemButton);

                    newTableRow.appendChild(newTableData);
                    localeTableElement.appendChild(newTableRow);
                }


                if (localeName != '') { // add remove locale x
                    var imgElement2 = document.createElement("img");
                    imgElement2.setAttribute("style", "width: 15px; height: 15px;");
                    imgElement2.setAttribute("src", "../resources/redX.png");
                    imgElement2.setAttribute("onclick", "MultiLocaleTableHandler.writeMultiLocaleSetting('" + keyName + "','" + localeName + "',null,null);MultiLocaleTableHandler.initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
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
                    MultiLocaleTableHandler.writeMultiLocaleSetting(keyName, registry.byId(keyName + "-addLocaleValue").value, 0, '');
                    MultiLocaleTableHandler.initMultiLocaleTable(parentDiv, keyName, regExPattern);
                });
            };

            addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction);
            clientSettingCache[keyName] = resultValue;
            dojoParser.parse(parentDiv);
        });
    });
};

MultiLocaleTableHandler.writeMultiLocaleSetting = function(settingKey, locale, iteration, value) {
    var results = clientSettingCache[settingKey];
    var currentValues = { };
    for (var loopLocale in results) {
        var iterValues = results[loopLocale];
        var loopValues = { };
        for (var loopIteration in iterValues) {
            var inputID = "value-" + settingKey + "-" + loopLocale + "-" + loopIteration;
            loopValues[loopIteration] = getObject(inputID).value;
        }
        currentValues[loopLocale] = loopValues;
    }

    if (locale != null) {
        if (currentValues[locale] == null) {
            currentValues[locale] = { "0":"" };
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
};

// -------------------------- form table handler ------------------------------------

var FormTableHandler = {};

FormTableHandler.init = function(parentDiv, keyName) {
    console.log('FormTableHandler init for ' + keyName);
    clearDivElements(parentDiv, true);
    readSetting(keyName, function(resultValue) {
        clientSettingCache[keyName] = resultValue;
        FormTableHandler.redraw(parentDiv, keyName);
    });
};

FormTableHandler.redraw = function(parentDiv, keyName) {
    var resultValue = clientSettingCache[keyName];
    clearDivElements(parentDiv, false);
    var parentDivElement = getObject(parentDiv);

    {
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
        FormTableHandler.addFormRow(parentDiv, keyName, i, resultValue[i]);
    }

    {
        var newTableRow = document.createElement("tr");
        newTableRow.setAttribute("style", "border-width: 0");
        newTableRow.setAttribute("colspan", "5");

        var newTableData = document.createElement("td");
        newTableData.setAttribute("style", "border-width: 0;");

        var addItemButton = document.createElement("button");
        addItemButton.setAttribute("type", "[button");
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

FormTableHandler.addFormRow = function(parentDiv, settingKey, iteration, value) {
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
            labelInput.setAttribute("value", value['label']);
            labelInput.setAttribute("readonly", "true");
            labelInput.setAttribute("onclick","FormTableHandler.showLabelDialog('" + settingKey + "'," + iteration + ")");
            labelInput.setAttribute("data-dojo-type", "dijit.form.ValidationTextBox");
            td2.appendChild(labelInput);
            newTableRow.appendChild(td2);
        }

        {
            var td3 = document.createElement("td");
            td3.setAttribute("style", "border-width: 0");
            var optionList = ["text","email","number","password","random","readonly","tel","hidden"];
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

        if (iteration != 0) {
            var tdFinal = document.createElement("td");
            tdFinal.setAttribute("style", "border-width: 0");

            var imgElement = document.createElement("img");
            imgElement.setAttribute("style", "width: 15px; height: 15px");
            imgElement.setAttribute("src", "../resources/redX.png");
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
    var parentDiv = 'table_setting_' + keyName;
    delete clientSettingCache[keyName][iteration];
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.redraw(parentDiv, keyName);
};

FormTableHandler.addMultiSetting = function(keyName, parentDiv) {
    var currentSize = 0;
    for (var loopVar in clientSettingCache[keyName]) {
        currentSize++;
    }
    clientSettingCache[keyName][currentSize + 1] = {};
    clientSettingCache[keyName][currentSize + 1]['name'] = '';
    clientSettingCache[keyName][currentSize + 1]['label'] = '';
    clientSettingCache[keyName][currentSize + 1]['minimumLength'] = '0';
    clientSettingCache[keyName][currentSize + 1]['maximumLength'] = '255';
    FormTableHandler.writeFormSetting(keyName);
    FormTableHandler.redraw(parentDiv, keyName)
};

FormTableHandler.showOptionsDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/NumberSpinner"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_";
        var bodyText = '<table style="border:0">';
        bodyText += '<tr>';
        bodyText += '<td style="border:0; text-align: right">Required</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'required' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Confirm</td><td style="border:0;"><input type="checkbox" id="' + inputID + 'confirmationRequired' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Minimum Length</td><td style="border:0;"><input type="number" id="' + inputID + 'minimumLength' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Maximum Length</td><td style="border:0;"><input type="number" id="' + inputID + 'maximumLength' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Regular Expression</td><td style="border:0;"><input type="text" id="' + inputID + 'regex' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">Placeholder</td><td style="border:0;"><input type="text" id="' + inputID + 'placeholder' + '"/></td>';
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Options for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
            }
        });
        theDialog.show();

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

        clearDijitWidget(inputID + "placeholder");
        new dijit.form.Textarea({
            value: clientSettingCache[keyName][iteration]['placeholder'],
            onChange: function(){clientSettingCache[keyName][iteration]['placeholder'] = this.value;FormTableHandler.writeFormSetting(keyName)}
        },inputID + "placeholder");
    });
};

FormTableHandler.showLabelDialog = function(keyName, iteration) {
    require(["dijit/Dialog","dijit/form/Textarea","dijit/form/CheckBox","dijit/form/NumberSpinner"],function(){
        var inputID = 'value_' + keyName + '_' + iteration + "_" + "label_";
        var bodyText = '<table style="border:0">';
        bodyText += '<tr>';
        bodyText += '<td style="border:0; text-align: right">Default</td><td style="border:0;"><input type="text" id="' + inputID + '' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '<td style="border:0; text-align: right">fr</td><td style="border:0;"><input type="text" id="' + inputID + 'fr' + '"/></td>';
        bodyText += '</tr><tr>';
        bodyText += '</tr></table>';
        bodyText += '<br/>';
        bodyText += '<button class="btn" onclick="clearDijitWidget(\'dialogPopup\');">OK</button>';

        clearDijitWidget('dialogPopup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: 'Labels for ' + clientSettingCache[keyName][iteration]['name'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('dialogPopup');
            }
        });
        theDialog.show();

    });
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

ChangePasswordHandler.changePasswordPopup = function(settingName,settingKey) {
    require(["dijit/Dialog","dijit/form/Textarea"],function(){
        var bodyText = '<span id="message" class="message message-info">' + settingName + '</span><br/>';
        bodyText += '<table style="border: 0">';
        bodyText += '<tr style="border: 0"><td style="border: 0"><textarea data-dojo-type="dijit.form.Textarea" name="password1" id="password1" class="inputfield" style="width: 400px" autocomplete="off" onkeyup="ChangePasswordHandler.validatePasswordPopupFields();getObject(\'password2\').value = \'\'"></textarea></td>';
        bodyText += '</tr><tr style="border: 0">';
        bodyText += '<td style="border: 0" xmlns="http://www.w3.org/1999/html"><textarea data-dojo-type="dijit.form.Textarea" name="password2" id="password2" class="inputfield" style="width: 400px" autocomplete="off" onkeyup="ChangePasswordHandler.validatePasswordPopupFields()"/></textarea></td>';

        bodyText += '<td style="border: 0"><div style="margin:0;">';
        bodyText += '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="../resources/greenCheck.png">';
        bodyText += '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="../resources/redX.png">';
        bodyText += '</div></td>';

        bodyText += '</tr></table>';
        bodyText += '<button name="change" class="btn" id="password_button" onclick="ChangePasswordHandler.doChange(' + "\'"+ settingKey + "\'" + ')" disabled="true"/>';
        bodyText += 'Set Password</button>';

        clearDijitWidget('changepassword-popup');
        var theDialog = new dijit.Dialog({
            id: 'changepassword-popup',
            title: 'Set Password',
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                clearDijitWidget('changepassword-popup');
            }
        });
        theDialog.show();

        require(["dojo/parser","dijit/form/Textarea"],function(dojoParser){
            dojoParser.parse();
        });

        setTimeout(function(){ getObject('password1').focus();},500);
    });
};




function saveConfiguration() {
    require(["dojo"],function(dojo){
        showWaitDialog('Saving Configuration...', null);
        dojo.xhrGet({
            url:"ConfigManager?processAction=getOptions",
            preventCache: true,
            sync: true,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=finishEditing&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    preventCache: true
                });
                var oldEpoch = data != null ? data['configEpoch'] : null;
                var currentTime = new Date().getTime();
                showError('Waiting for server restart');
                setTimeout(function() {
                    waitForRestart(currentTime, oldEpoch);
                }, 2 * 1000);
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
                showError('Waiting for server restart, unable to contact server: ' + error);
                setTimeout(function() {
                    waitForRestart(startTime, oldEpoch)
                }, 1000);
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
            url:"ConfigManager?processAction=setOption&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&template=" + template,
            preventCache: true,
            sync: true,
            error: function(errorObj) {
                showError("error starting configuration editor: " + errorObj)
            },
            load: function(data) {
                document.forms['editMode'].submit();
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

