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
var availableLocales = new Array();
var menuItems = new Array();
var selectedCategory = "";

function readSetting(keyName, valueWriter) {
    require(["dojo"],function(dojo){
        dojo.xhrGet({
            url:"ConfigManager?processAction=readSetting&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&key=" + keyName,
            contentType: "application/json;charset=utf-8",
            dataType: "json",
            handleAs: "json",
            error: function(errorObj) {
                showError("error loading " + keyName + ", reason: " + errorObj)
            },
            load: function(data) {
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
    for (var localeIter in availableLocales) {
        var optionElement = document.createElement("option");
        optionElement.innerHTML = localeIter;
        selectElement.appendChild(optionElement);
    }
    td1.appendChild(selectElement);

    var addButton = document.createElement("button");
    addButton.setAttribute('id', keyName + '-addLocaleButton');
    addButton.setAttribute("type", "button");
    addButton.innerHTML = 'Add Locale';
    td1.appendChild(addButton);

    newTableRow.appendChild(td1);
    var parentDivElement = getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    require(["dijit/form/ComboBox","dijit/form/Button"],function(){
        clearDigitWidget(keyName + '-addLocaleValue');
        new dijit.form.ComboBox({
            id: keyName + '-addLocaleValue'
        }, keyName + '-addLocaleValue');

        clearDigitWidget(keyName + '-addLocaleButton');
        new dijit.form.Button({
            id: keyName + '-addLocaleButton',
            onClick: addFunction
        }, keyName + '-addLocaleButton');

        return newTableRow;
    });
}

// -------------------------- locale table handler ------------------------------------

function initLocaleTable(parentDiv, keyName, regExPattern, syntax) {
    clearDivElements(parentDiv, true);
    readSetting(keyName, function(resultValue) {
        clearDivElements(parentDiv, false);
        for (var i in resultValue) {
            addLocaleTableRow(parentDiv, keyName, i, resultValue[i], regExPattern, syntax)
        }
        addAddLocaleButtonRow(parentDiv, keyName, function() {
            addLocaleSetting(keyName, parentDiv, regExPattern, syntax);
        });

        clientSettingCache[keyName] = resultValue;
        require(["dojo","dijit/form/Button","dijit/form/TextArea"],function(dojo){
            dojo.parser.parse(parentDiv);
        });
    });
}

function addLocaleTableRow(parentDiv, settingKey, localeString, value, regExPattern, syntax) {
    var inputID = 'value-' + settingKey + '-' + localeString;

    // clear the old dijit node (if it exists)
    clearDigitWidget(inputID);

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    {
        var td1 = document.createElement("td");
        td1.setAttribute("style", "border-width: 0;");

        if (localeString == null || localeString.length < 1) {
            td1.innerHTML = "Default";
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
            textAreaElement.setAttribute("onchange", "writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            textAreaElement.setAttribute("style", "width: 500px;");
            textAreaElement.setAttribute("dojoType", "dijit.form.Textarea");
            textAreaElement.setAttribute("value", value);
            td2.appendChild(textAreaElement);
        } else {
            var inputElement = document.createElement("input");
            inputElement.setAttribute("id", inputID);
            inputElement.setAttribute("value", "[Loading....]");
            inputElement.setAttribute("onchange", "writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            inputElement.setAttribute("style", "width: 500px");
            inputElement.setAttribute("dojoType", "dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("value", value);
            td2.appendChild(inputElement);
        }
        newTableRow.appendChild(td2);

        if (localeString != null && localeString.length > 0) {
            var imgElement = document.createElement("img");
            imgElement.setAttribute("style", "width: 15px; height: 15px");
            imgElement.setAttribute("src", "../resources/redX.png");
            imgElement.setAttribute("onclick", "removeLocaleSetting('" + settingKey + "','" + localeString + "','" + parentDiv + "','" + regExPattern + "')");
            td2.appendChild(imgElement);
        }
    }
    var parentDivElement = getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
}

function writeLocaleSetting(settingKey, locale, value) {
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
}

function removeLocaleSetting(keyName, locale, parentDiv, regExPattern, syntax) {
    writeLocaleSetting(keyName, locale, null);
    clearDivElements(parentDiv, true);
    initLocaleTable(parentDiv, keyName, regExPattern, syntax);
}

function addLocaleSetting(keyName, parentDiv, regExPattern, syntax) {
    require(["dijit"],function(dijit){
        var inputValue = dijit.byId(keyName + '-addLocaleValue').value;
        try {
            var existingElementForLocale = getObject('value-' + keyName + '-' + inputValue);
            if (existingElementForLocale == null) {
                writeLocaleSetting(keyName, inputValue, '');
                clearDivElements(parentDiv, true);
                initLocaleTable(parentDiv, keyName, regExPattern, syntax);
            }
        } finally {
        }
    });
}

// -------------------------- multivalue table handler ------------------------------------

function initMultiTable(parentDiv, keyName, regExPattern) {
    clearDivElements(parentDiv, true);

    readSetting(keyName, function(resultValue) {
        clearDivElements(parentDiv, false);
        var counter = 0;
        for (var i in resultValue) {
            addMultiValueRow(parentDiv, keyName, i, resultValue[i], regExPattern);
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
            addItemButton.setAttribute("onclick", "addMultiSetting('" + keyName + "','" + parentDiv + "','" + regExPattern + "');");
            addItemButton.setAttribute("dojoType", "dijit.form.Button");
            addItemButton.innerHTML = "Add Value";
            newTableData.appendChild(addItemButton);

            newTableRow.appendChild(newTableData);
            var parentDivElement = getObject(parentDiv);
            parentDivElement.appendChild(newTableRow);
        }
        clientSettingCache[keyName] = counter;
        require(["dojo","dijit/form/Button","dijit/form/TextArea"],function(dojo){
            dojo.parser.parse(parentDiv);
        });
    });
}

function addMultiValueRow(parentDiv, settingKey, iteration, value, regExPattern) {
    require(["dijit"],function(dijit){
        var inputID = 'value-' + settingKey + '-' + iteration;

        // clear the old dijit node (if it exists)

        var oldDijitNode = dijit.byId(inputID);
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
            inputElement.setAttribute("onchange", "writeMultiSetting('" + settingKey + "','" + iteration + "',this.value)");
            inputElement.setAttribute("style", "width: 450px");
            inputElement.setAttribute("dojoType", "dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp", regExPattern);
            inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
            td1.appendChild(inputElement);
            newTableRow.appendChild(td1);


            if (iteration != 0) {
                var imgElement = document.createElement("img");
                imgElement.setAttribute("style", "width: 15px; height: 15px");
                imgElement.setAttribute("src", "../resources/redX.png");
                imgElement.setAttribute("onclick", "removeMultiSetting('" + settingKey + "','" + iteration + "','" + regExPattern + "')");
                td1.appendChild(imgElement);
            }
        }
        var parentDivElement = getObject(parentDiv);
        parentDivElement.appendChild(newTableRow);
    });
}

function writeMultiSetting(settingKey, iteration, value) {
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
}

function removeMultiSetting(keyName, iteration, regExPattern) {
    var parentDiv = 'table_setting_' + keyName;
    writeMultiSetting(keyName, iteration, null);
    clearDivElements(parentDiv, true);
    initMultiTable(parentDiv, keyName, regExPattern);
}

function addMultiSetting(keyName, parentDiv, regExPattern) {
    var size = clientSettingCache[keyName];
    writeMultiSetting(keyName, size + 1, "");
    clearDivElements(parentDiv, true);
    initMultiTable(parentDiv, keyName, regExPattern)
}

// -------------------------- multi locale table handler ------------------------------------

function initMultiLocaleTable(parentDiv, keyName, regExPattern) {
    require(["dojo","dijit","dijit/form/Button","dijit/form/TextArea"],function(dojo,dijit){
        clearDivElements(parentDiv, true);
        readSetting(keyName, function(resultValue) {
            clearDivElements(parentDiv, false);
            for (var localeName in resultValue) {
                var localeTableRow = document.createElement("tr");
                localeTableRow.setAttribute("style", "border-width: 0");

                var localeTdName = document.createElement("td");
                localeTdName.setAttribute("style", "border-width: 0; text-align: right; vertical-align: top;");
                localeTdName.innerHTML = localeName == "" ? "Default" : localeName;
                localeTableRow.appendChild(localeTdName);

                var localeTdContent = document.createElement("td");
                localeTdContent.setAttribute("style", "border-width: 0;");
                localeTableRow.appendChild(localeTdContent);

                var localeTableElement = document.createElement("table");
                localeTableElement.setAttribute("style", "border-width: 1");
                localeTdContent.appendChild(localeTableElement);

                var multiValues = resultValue[localeName];

                for (var iteration in multiValues) {

                    var valueTableRow = document.createElement("tr");

                    var valueTd1 = document.createElement("td");
                    valueTd1.setAttribute("style", "border-width: 0;");

                    // clear the old dijit node (if it exists)
                    var inputID = "value-" + keyName + "-" + localeName + "-" + iteration;
                    var oldDijitNode = dijit.byId(inputID);
                    if (oldDijitNode != null) {
                        try {
                            oldDijitNode.destroy();
                        } catch (error) {
                        }
                    }

                    var inputElement = document.createElement("input");
                    inputElement.setAttribute("id", inputID);
                    inputElement.setAttribute("value", multiValues[iteration]);
                    inputElement.setAttribute("onchange", "writeMultiLocaleSetting('" + keyName + "')");
                    inputElement.setAttribute("style", "width: 450px");
                    inputElement.setAttribute("dojoType", "dijit.form.ValidationTextBox");
                    inputElement.setAttribute("regExp", regExPattern);
                    inputElement.setAttribute("invalidMessage", "The value does not have the correct format.");
                    valueTd1.appendChild(inputElement);
                    valueTableRow.appendChild(valueTd1);
                    localeTableElement.appendChild(valueTableRow);

                    if (iteration != 0) { // add the remove value button
                        var imgElement = document.createElement("img");
                        imgElement.setAttribute("style", "width: 15px; height: 15px");
                        imgElement.setAttribute("src", "../resources/redX.png");
                        imgElement.setAttribute("onclick", "writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',null);initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
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
                    addItemButton.setAttribute("onclick", "writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + (multiValues.size + 1) + "','');initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
                    addItemButton.setAttribute("dojoType", "dijit.form.Button");
                    addItemButton.innerHTML = "Add Value";
                    newTableData.appendChild(addItemButton);

                    newTableRow.appendChild(newTableData);
                    localeTableElement.appendChild(newTableRow);
                }


                if (localeName != '') { // add remove locale x
                    var imgElement2 = document.createElement("img");
                    imgElement2.setAttribute("style", "width: 15px; height: 15px");
                    imgElement2.setAttribute("src", "../resources/redX.png");
                    imgElement2.setAttribute("onclick", "writeMultiLocaleSetting('" + keyName + "','" + localeName + "',null,null);initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
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
                require(["dijit"],function(dijit){
                    writeMultiLocaleSetting(keyName, dijit.byId(keyName + "-addLocaleValue").value, 0, '');
                    initMultiLocaleTable(parentDiv, keyName, regExPattern);
                });
            };

            addAddLocaleButtonRow(parentDiv, keyName, addLocaleFunction);
            clientSettingCache[keyName] = resultValue;
            dojo.parser.parse(parentDiv);
        });
    });
}

function writeMultiLocaleSetting(settingKey, locale, iteration, value) {
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
}

function saveConfiguration() {
    require(["dojo"],function(dojo){
        showWaitDialog('Saving Configuration...', null);
        dojo.xhrGet({
            url:"ConfigManager?processAction=getOptions",
            sync: true,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=finishEditing&pwmFormID=" + PWM_GLOBAL['pwmFormID']
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
            sync: false,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                dojo.xhrGet({
                    url:"ConfigManager?processAction=lockConfiguration&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    sync:true
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
            sync: true,
            dataType: "json",
            handleAs: "json",
            load: function(data) {
                var error = data != null ? data['error'] : null;
                if (error) {
                    clearDigitWidget('waitDialogID');
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

function setCookie(c_name,value,exdays)
{
    var exdate=new Date();
    exdate.setDate(exdate.getDate() + exdays);
    var c_value=escape(value) + ((exdays==null) ? "" : "; expires="+exdate.toUTCString());
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