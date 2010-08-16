/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

dojo.require("dojo.parser");
dojo.require("dijit.layout.ContentPane");
dojo.require("dijit.layout.TabContainer");
dojo.require("dijit.form.Button");
dojo.require("dijit.form.NumberTextBox");
dojo.require("dijit.form.ValidationTextBox");
dojo.require("dijit.form.Textarea");
dojo.require("dijit.form.ComboBox");
dojo.require("dijit.Dialog");
dojo.require("dijit.TitlePane");


var clientSettingCache = { };

function showError(errorMsg)
{
    getObject("error_msg").firstChild.nodeValue = errorMsg;
    getObject("error_msg").className = "msg-error";
    getObject("error_msg").style.visibility = 'visible';
    window.scrollTo(getObject("error_msg").offsetLeft, getObject("error_msg").offsetTop)
}

function readSetting(keyName, valueWriter) {
    dojo.xhrGet({
        url:"ConfigManager?processAction=readSetting&pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&key=" + keyName,
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        error: function(errorObj) {
            showError("error loading " + keyName + ", reason: " + errorObj)
        },
        load: function(data){
            var resultValue = data.value;
            valueWriter(resultValue);
        }
    });
}

function writeSetting(keyName, valueData) {
    var jsonData = { key:keyName, value:valueData };
    var jsonString = dojo.toJson(jsonData);
    dojo.xhrPost({
        url: "ConfigManager?processAction=writeSetting&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
        postData: jsonString,
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        sync: true,
        error: function(errorObj) {
            showError("error writing setting " + keyName + ", reason: " + errorObj)
        }
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
    if (parentDivElement.hasChildNodes()) {
        while ( parentDivElement.childNodes.length >= 1 ) {
            var firstChild = parentDivElement.firstChild
            parentDivElement.removeChild( firstChild );
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

// -------------------------- locale table handler ------------------------------------

function initLocaleTable(parentDiv, keyName, regExPattern, syntax) {
    clearDivElements(parentDiv,true);
    readSetting(keyName, function(resultValue) {
        clearDivElements(parentDiv,false)    ;
        for (var i in resultValue) {
            addLocaleTableRow(parentDiv, keyName, i, resultValue[i], regExPattern, syntax)
        }
        clientSettingCache[keyName] = resultValue;
        dojo.parser.parse(parentDiv);
    });
}

function addLocaleTableRow(parentDiv, settingKey, localeString, value, regExPattern, syntax) {
    var inputID = 'value-' + settingKey + '-' + localeString;

    // clear the old dijit node (if it exists)
    var oldDijitNode = dijit.byId(inputID);
    if (oldDijitNode != null) { try { oldDijitNode.destroy(); } catch (error) { } }

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    {
        var td1 = document.createElement("td");
        td1.setAttribute("style", "border-width: 0");

        if (localeString == null || localeString.length < 1) {
            td1.innerHTML = " Default";
        } else {
            td1.innerHTML = localeString;
        }
        newTableRow.appendChild(td1);

    }
    {
        var td2 = document.createElement("td");
        td2.setAttribute("width","100%");
        td2.setAttribute("style", "border-width: 0;");
        if (syntax == 'LOCALIZED_TEXT_AREA') {
            var textAreaElement = document.createElement("textarea");
            textAreaElement.setAttribute("id",inputID);
            textAreaElement.setAttribute("value","[Loading....]");
            textAreaElement.setAttribute("onchange","writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            textAreaElement.setAttribute("style","width: 500px;");
            textAreaElement.setAttribute("dojoType","dijit.form.Textarea");
            textAreaElement.setAttribute("value",value);
            td2.appendChild(textAreaElement);
        } else {
            var inputElement = document.createElement("input");
            inputElement.setAttribute("id",inputID);
            inputElement.setAttribute("value","[Loading....]");
            inputElement.setAttribute("onchange","writeLocaleSetting('" + settingKey + "','" + localeString + "',this.value)");
            inputElement.setAttribute("style","width: 500px");
            inputElement.setAttribute("dojoType","dijit.form.ValidationTextBox");
            inputElement.setAttribute("regExp",regExPattern);
            inputElement.setAttribute("value",value);
            td2.appendChild(inputElement);
        }
        newTableRow.appendChild(td2);

        if (localeString != null && localeString.length > 0) {
            var imgElement = document.createElement("img");
            imgElement.setAttribute("style","width: 15px; height: 15px");
            imgElement.setAttribute("src","../resources/redX.png");
            imgElement.setAttribute("onclick","removeLocaleSetting('" + settingKey + "','" + localeString + "','" + parentDiv + "','" + regExPattern + "')");
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
        var loopValue = getObject(inputID).value;
        currentValues[i] = loopValue;
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
    initLocaleTable(parentDiv, keyName, regExPattern,syntax);
}

function addLocaleSetting(keyName, parentDiv, regExPattern, syntax) {
    var inputValue = getObject(keyName + '-addLocaleValue').value;
    try {
        var existingElementForLocale = getObject('value-' + keyName + '-' + inputValue);
        if (existingElementForLocale == null) {
            writeLocaleSetting(keyName, inputValue, '');
            clearDivElements(parentDiv,true);
            initLocaleTable(parentDiv, keyName, regExPattern,syntax);
        }
    } finally {}
}

// -------------------------- multivalue table handler ------------------------------------

function initMultiTable(parentDiv, keyName, regExPattern) {
    clearDivElements(parentDiv,true);

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
            newTableRow.setAttribute("colspan","5");

            var newTableData = document.createElement("td");
            newTableData.setAttribute("style", "border-width: 0;");

            var addItemButton = document.createElement("button");
            addItemButton.setAttribute("type","[button");
            addItemButton.setAttribute("onclick","addMultiSetting('" + keyName + "','" + parentDiv + "','" + regExPattern + "');");
            addItemButton.setAttribute("dojoType","dijit.form.Button");
            addItemButton.innerHTML = "Add Value";

            newTableData.appendChild(addItemButton);
            newTableRow.appendChild(newTableData);
            var parentDivElement = getObject(parentDiv);
            parentDivElement.appendChild(newTableRow);
        }
        clientSettingCache[keyName] = counter;
        dojo.parser.parse(parentDiv);
    });
}

function addMultiValueRow(parentDiv, settingKey, iteration, value, regExPattern) {
    var inputID = 'value-' + settingKey + '-' + iteration;

    // clear the old dijit node (if it exists)
    var oldDijitNode = dijit.byId(inputID);
    if (oldDijitNode != null) { try { oldDijitNode.destroy(); } catch (error) { } }

    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    {
        var td1 = document.createElement("td");
        td1.setAttribute("width","100%");
        td1.setAttribute("style", "border-width: 0;");


        var inputElement = document.createElement("input");
        inputElement.setAttribute("id",inputID);
        inputElement.setAttribute("value",value);
        inputElement.setAttribute("onchange","writeMultiSetting('" + settingKey + "','" + iteration +"',this.value)");
        inputElement.setAttribute("style","width: 500px");
        inputElement.setAttribute("dojoType","dijit.form.ValidationTextBox");
        inputElement.setAttribute("regExp",regExPattern);
        inputElement.setAttribute("invalidMessage","The value does not have the correct format.");
        td1.appendChild(inputElement);
        newTableRow.appendChild(td1);


        if (iteration != 0) {
            var imgElement = document.createElement("img");
            imgElement.setAttribute("style","width: 15px; height: 15px");
            imgElement.setAttribute("src","../resources/redX.png");
            imgElement.setAttribute("onclick","removeMultiSetting('" + settingKey + "','" + iteration + "','" + regExPattern + "')");
            td1.appendChild(imgElement);
        }
    }
    var parentDivElement = getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
}

function writeMultiSetting(settingKey, iteration, value) {
    var size = clientSettingCache[settingKey];
    var currentValues = { };
    for (var i = 0; i < size; i++) {
        var inputID = 'value-' + settingKey + '-' + i;
        var loopValue = getObject(inputID).value;
        currentValues[i] = loopValue;
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
    clearDivElements(parentDiv,true);
    readSetting(keyName, function(resultValue) {
        clearDivElements(parentDiv,false);
        for (var localeName in resultValue) {
            var localeTableRow = document.createElement("tr");
            localeTableRow.setAttribute("style", "border-width: 0");

            var localeTdName = document.createElement("td");
            localeTdName.setAttribute("style", "border-width: 0; text-align: right; vertical-align: top");
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
                if (oldDijitNode != null) { try { oldDijitNode.destroy(); } catch (error) { } }

                var inputElement = document.createElement("input");
                inputElement.setAttribute("id",inputID);
                inputElement.setAttribute("value", multiValues[iteration]);
                inputElement.setAttribute("onchange","writeMultiLocaleSetting('" + keyName + "')");
                inputElement.setAttribute("style","width: 550px");
                inputElement.setAttribute("dojoType","dijit.form.ValidationTextBox");
                inputElement.setAttribute("regExp",regExPattern);
                inputElement.setAttribute("invalidMessage","The value does not have the correct format.");
                valueTd1.appendChild(inputElement);
                valueTableRow.appendChild(valueTd1);
                localeTableElement.appendChild(valueTableRow);

                if (iteration != 0) { // add the remove value button
                    var imgElement = document.createElement("img");
                    imgElement.setAttribute("style","width: 15px; height: 15px");
                    imgElement.setAttribute("src","../resources/redX.png");
                    imgElement.setAttribute("onclick","writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + iteration + "',null);initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
                    valueTd1.appendChild(imgElement);
                }
            }

            { // add row button
                var newTableRow = document.createElement("tr");
                newTableRow.setAttribute("style", "border-width: 0");
                newTableRow.setAttribute("colspan","5");

                var newTableData = document.createElement("td");
                newTableData.setAttribute("style", "border-width: 0;");

                var addItemButton = document.createElement("button");
                addItemButton.setAttribute("type","[button");
                addItemButton.setAttribute("onclick","writeMultiLocaleSetting('" + keyName + "','" + localeName + "','" + (multiValues.size + 1) + "','');initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
                addItemButton.setAttribute("dojoType","dijit.form.Button");
                addItemButton.innerHTML = "Add Value";

                newTableData.appendChild(addItemButton);
                newTableRow.appendChild(newTableData);
                localeTableElement.appendChild(newTableRow);
            }


            if (localeName != '') { // add remove locale x
                var imgElement = document.createElement("img");
                imgElement.setAttribute("style","width: 15px; height: 15px");
                imgElement.setAttribute("src","../resources/redX.png");
                imgElement.setAttribute("onclick","writeMultiLocaleSetting('" + keyName + "','" + localeName + "',null,null);initMultiLocaleTable('" + parentDiv + "','" + keyName + "','" + regExPattern + "')");
                tdElement = document.createElement("td");
                tdElement.setAttribute("style", "border-width: 0; text-align: left; vertical-align: top");

                localeTableRow.appendChild(tdElement);
                tdElement.appendChild(imgElement);
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
        clientSettingCache[keyName] = resultValue;
        dojo.parser.parse(parentDiv);
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
            var loopValue = getObject(inputID).value;
            loopValues[loopIteration] = loopValue;
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

function showWaitDialog(message) {
    var theDialog = new dijit.Dialog({
        title: "Please Wait",
        style: "width: 300px",
        content: message,
        closable: false
    });
    theDialog.show()        ;
}
