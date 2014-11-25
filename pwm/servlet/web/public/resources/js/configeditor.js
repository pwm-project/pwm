/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

PWM_VAR['outstandingOperations'] = 0;
PWM_VAR['preferences'] = { };

PWM_CFGEDIT.readSetting = function(keyName, valueWriter) {
    PWM_VAR['outstandingOperations']++;
    PWM_CFGEDIT.handleWorkingIcon();
    var url = "ConfigEditor?processAction=readSetting&key=" + keyName;
    var loadFunction = function(data) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        console.log('read data for setting ' + keyName);
        var resultValue = data['data']['value'];
        valueWriter(resultValue);
        var isDefault = data['data']['isDefault'];
        PWM_CFGEDIT.updateSettingDisplay(keyName, isDefault)
    };
    var errorFunction = function(error) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Error'),text:"Unable to communicate with server.  Please refresh page."});
        console.log("error loading " + keyName + ", reason: " + error);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction});
};

PWM_CFGEDIT.writeSetting = function(keyName, valueData, nextAction) {
    PWM_VAR['outstandingOperations']++;
    PWM_CFGEDIT.handleWorkingIcon();
    var url = "ConfigEditor?processAction=writeSetting&key=" + keyName;
    var loadFunction = function(data) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        console.log('wrote data for setting ' + keyName);
        var isDefault = data['data']['isDefault'];
        PWM_CFGEDIT.updateSettingDisplay(keyName, isDefault);
        if (data['errorMessage']) {
            PWM_MAIN.showError(data['data']['errorMessage']);
        } else {
            PWM_MAIN.clearError();
        }
        if (nextAction !== undefined) {
            nextAction();
        }
    };
    var errorFunction = function(error) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Error'),text:"Unable to communicate with server.  Please refresh page."});
        console.log("error writing setting " + keyName + ", reason: " + error)
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,content:valueData});
};

PWM_CFGEDIT.resetSetting=function(keyName) {
    require(["dojo"],function(dojo){
        var jsonData = { key:keyName };
        var jsonString = dojo.toJson(jsonData);
        dojo.xhrPost({
            url: "ConfigEditor?processAction=resetSetting&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
            postData: jsonString,
            contentType: "application/json;charset=utf-8",
            dataType: "json",
            handleAs: "json",
            sync: true,
            error: function(errorObj) {
                PWM_MAIN.showError("error resetting setting " + keyName + ", reason: " + errorObj)
            },
            load: function() {
                console.log('reset data for ' + keyName);
            }
        });
    });
};


PWM_CFGEDIT.handleWorkingIcon = function() {
    var iconElement = PWM_MAIN.getObject('working_icon');
    if (iconElement) {
        if (PWM_VAR['outstandingOperations'] > 0) {
            iconElement.style.visibility = 'visible';
        } else {
            iconElement.style.visibility = 'hidden';
        }
    }
};


PWM_CFGEDIT.updateSettingDisplay = function(keyName, isDefault) {
    require(["dojo"],function(dojo){
        var resetImageButton = PWM_MAIN.getObject('resetButton-' + keyName);
        var modifiedIcon = PWM_MAIN.getObject('modifiedNoticeIcon-' + keyName);
        var settingSyntax = '';
        try {
            settingSyntax = PWM_SETTINGS['settings'][keyName]['syntax'];
        } catch (e) { /* noop */ }  //setting keys may not be loaded

        if (!isDefault) {
            resetImageButton.style.visibility = 'visible';
            modifiedIcon.style.display = 'inline';
            try {
                dojo.addClass('title_' + keyName,"modified");
                dojo.addClass('titlePane_' + keyName,"modified");
            } catch (e) { /* noop */ }
        } else {
            resetImageButton.style.visibility = 'hidden';
            modifiedIcon.style.display = 'none';
            try {
                dojo.removeClass('title_' + keyName,"modified");
                dojo.removeClass('titlePane_' + keyName,"modified");
            } catch (e) { /* noop */ }
        }
    });
};

PWM_CFGEDIT.clearDivElements = function(parentDiv, showLoading) {
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
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
            newTableData.innerHTML = PWM_MAIN.showString('Display_PleaseWait');
            newTableRow.appendChild(newTableData);
        }
    }
};

PWM_CFGEDIT.addValueButtonRow = function(parentDiv, keyName, addFunction) {
    var buttonId = keyName + '-addValueButton';
    var newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    newTableRow.setAttribute("colspan", "5");

    var newTableData = document.createElement("td");
    newTableData.setAttribute("style", "border-width: 0;");

    var addItemButton = document.createElement("button");
    addItemButton.setAttribute("type", "button");
    addItemButton.setAttribute("id", buttonId);
    addItemButton.setAttribute("class", "btn")
    addItemButton.onclick = addFunction;
    addItemButton.innerHTML = "Add Value";
    newTableData.appendChild(addItemButton);

    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
    newTableRow.appendChild(newTableData);

    /*
    PWM_MAIN.clearDijitWidget(keyName + '-addValueButton');
    require(["dijit/form/Select","dijit/form/Button"],function(Select,Button) {
        new Button({
            id: buttonId,
            onClick: addFunction
        }, buttonId);
    });
    */
};

PWM_CFGEDIT.addAddLocaleButtonRow = function(parentDiv, keyName, addFunction) {
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
    addButton.setAttribute("class", "btn");
    addButton.innerHTML = '<span class="btn-icon fa fa-plus-square"></span>Add Locale';
    td1.appendChild(addButton);

    newTableRow.appendChild(td1);
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);

    require(["dijit/form/Select"],function(Select){
        var availableLocales = PWM_GLOBAL['localeInfo'];

        var localeMenu = [];
        for (var localeIter in availableLocales) {
            if (localeIter != PWM_GLOBAL['defaultLocale']) {
                var labelText = availableLocales[localeIter] + " (" + localeIter + ")";
                localeMenu.push({label: labelText, value: localeIter})
            }
        }

        PWM_MAIN.clearDijitWidget(keyName + '-addLocaleValue');
        new Select({
            id: keyName + '-addLocaleValue',
            options: localeMenu,
            style: 'width: 175px'
        }, keyName + '-addLocaleValue');

        PWM_MAIN.addEventHandler(keyName + '-addLocaleButton','click',function(){
            addFunction();
        });

        return newTableRow;
    });
};




function readInitialTextBasedValue(key) {
    require(["dijit/registry"],function(registry){
        PWM_CFGEDIT.readSetting(key, function(dataValue) {
            PWM_MAIN.getObject('value_' + key).value = dataValue;
            PWM_MAIN.getObject('value_' + key).disabled = false;
            registry.byId('value_' + key).set('disabled', false);
            registry.byId('value_' + key).startup();
            try {registry.byId('value_' + key).validate(false);} catch (e) {}
            try {registry.byId('value_verify_' + key).validate(false);} catch (e) {}
        });
    });
}

PWM_CFGEDIT.saveConfiguration = function() {
    PWM_MAIN.preloadAll(function(){
        var confirmText = PWM_CONFIG.showString('MenuDisplay_SaveConfig');
        var confirmFunction = function(){
            var url = "ConfigEditor?processAction=finishEditing";
            var loadFunction = function(data) {
                if (data['error'] == true) {
                    PWM_MAIN.showDialog({
                        title: PWM_MAIN.showString('Title_Error'),
                        text: data['errorDetail']
                    })
                } else {
                    console.log('save completed');
                    PWM_MAIN.showWaitDialog({title:'Save complete, restarting application...',loadFunction:function(){
                        PWM_CONFIG.waitForRestart({location:'/private/config/ConfigManager'});
                    }});
                }
            };
            PWM_MAIN.showWaitDialog({title:'Saving...',loadFunction:function(){
                PWM_MAIN.ajaxRequest(url,loadFunction);
            }});
        };
        PWM_CFGEDIT.showChangeLog(confirmText,confirmFunction);
    });
};


PWM_CFGEDIT.readConfigEditorCookie = function(nextFunction) {
    require(['dojo/json','dojo/cookie'], function(json,dojoCookie){
        try {
            PWM_VAR['preferences'] = json.parse(dojoCookie("ConfigEditor_preferences"));
        } catch (e) {
            console.log("error reading PWM_VAR['preferences'] cookie: " + e);
        }
        if (nextFunction) {
            nextFunction();
        }
    });
};

PWM_CFGEDIT.setConfigEditorCookie = function() {
    require(['dojo/json','dojo/cookie'], function(json,dojoCookie){
        var cookieString = json.stringify(PWM_VAR['preferences']);
        dojoCookie("ConfigEditor_preferences", cookieString, {expires: 5}); // 5 days
    });
};

PWM_CFGEDIT.setConfigurationPassword = function(password) {
    if (password) {
        var url = "ConfigEditor?processAction=setConfigurationPassword";
        var loadFunction = function(data) {
            if (data['error']) {
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Error'), text: data['errorMessage']});
            } else {
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Success'), text: data['successMessage']});
            }
        };
        var errorFunction = function(errorObj) {
            PWM_MAIN.closeWaitDialog();
            PWM_MAIN.showDialog ({title:PWM_MAIN.showString('Title_Error'),text:"error saving configuration password: " + errorObj});
        };
        PWM_MAIN.clearDijitWidget('dialogPopup');
        PWM_MAIN.showWaitDialog({loadFunction:function(){
            PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,content:{password:password}});
        }});
        return;
    }

    var writeFunction = 'PWM_CFGEDIT.setConfigurationPassword(PWM_MAIN.getObject(\'password1\').value)';
    ChangePasswordHandler.popup('configPw','Configuration Password',writeFunction);
};

PWM_CFGEDIT.toggleHelpDisplay=function(key, options) {
    console.log('begin toggle help display for key ' + key);
    PWM_VAR['toggleHelpDisplay'] = PWM_VAR['toggleHelpDisplay'] || {};
    var helpDiv = PWM_MAIN.getObject('helpDiv_' + key);
    var titleId = 'title_' + key;
    var show;
    if (options && options['force']) {
        if (options['force'] == 'show') {
            show = true;
        } else if (options['force'] == 'hide') {
            show = false;
        }
    } else {
        show = PWM_VAR['toggleHelpDisplay'][key] == 'hidden';
    }
    if (helpDiv) {
        if (show) {
            PWM_VAR['toggleHelpDisplay'][key] = 'visible';
            require(["dijit/registry","dojo/fx"],function(registry,fx){
                var node = registry.byId('title_' + key);
                if (node) {
                    node.destroy();
                }
                fx.wipeIn({node:helpDiv}).play();
            });
        } else {
            PWM_VAR['toggleHelpDisplay'][key] = 'hidden';
            var helpText = PWM_SETTINGS['settings'][key]['description'];
            PWM_MAIN.showTooltip({
                id: [titleId],
                position: ['above','below'],
                text: helpText,
                width: 520
            });
            require(["dojo/fx"],function(fx){
                fx.wipeOut({node:helpDiv}).play();
            });
        }
    }
};

function handleResetClick(settingKey) {
    var label = PWM_SETTINGS['settings'][settingKey] ? PWM_SETTINGS['settings'][settingKey]['label'] : ' ';
    var dialogText = PWM_CONFIG.showString('Warning_ResetSetting',{value1:label});
    var titleText = 'Reset ' + label ? label : '';

    PWM_MAIN.showConfirmDialog({title:titleText,text:dialogText,okAction:function(){
        PWM_CFGEDIT.resetSetting(settingKey);
        PWM_CFGEDIT.loadMainPageBody();
    }});
}

PWM_CFGEDIT.initConfigEditor = function(nextFunction) {
    PWM_CFGEDIT.readConfigEditorCookie();

    PWM_MAIN.addEventHandler('homeSettingSearch','input',function(){PWM_CFGEDIT.handleSearchFieldInput();});
    PWM_MAIN.addEventHandler('button-navigationExpandAll','click',function(){PWM_VAR['navigationTree'].expandAll()});
    PWM_MAIN.addEventHandler('button-navigationCollapseAll','click',function(){PWM_VAR['navigationTree'].collapseAll()});

    PWM_MAIN.showTooltip({id:'cancelButton_icon',text:'Cancel Changes and return to Configuration Manager',position:'below'});
    PWM_MAIN.showTooltip({id:'saveButton_icon',text:'Save',position:'below'});
    PWM_MAIN.showTooltip({id:'setPassword_icon',text:'Set Configuration Password',position:'below'});
    PWM_MAIN.showTooltip({id:'referenceDoc_icon',text:'Open Reference Documentation',position:'below'});
    PWM_MAIN.showTooltip({id:'macroDoc_icon',text:'Macro Help',position:'below'});
    PWM_MAIN.showTooltip({id:'settingSearchIcon',text:'Search settings, help text and setting values',position:'above'});
    PWM_MAIN.showTooltip({id:'noSearchResultsIndicator',text:'No search results',position:'above'});

    PWM_MAIN.addEventHandler('cancelButton_icon','click',function(){PWM_CFGEDIT.cancelEditing()});
    PWM_MAIN.addEventHandler('saveButton_icon','click',function(){PWM_CFGEDIT.saveConfiguration()});
    PWM_MAIN.addEventHandler('setPassword_icon','click',function(){PWM_CFGEDIT.setConfigurationPassword()});
    PWM_MAIN.addEventHandler('referenceDoc_icon','click',function(){
        window.open(PWM_GLOBAL['url-context'] + '/public/referencedoc.jsp','_blank', 'toolbar=0,location=0,menubar=0');
    });
    PWM_MAIN.addEventHandler('macroDoc_icon','click',function(){
        PWM_CFGEDIT.showMacroHelp();
    });

    require(["dojo","dijit/Dialog"],function() {
        setTimeout(PWM_CONFIG.heartbeatCheck,5000);
    });

    PWM_CFGEDIT.loadMainPageBody();

    console.log('completed initConfigEditor');
    if (nextFunction) {
        nextFunction();
    }
};

PWM_CFGEDIT.executeSettingFunction = function(setting, name) {
    var jsonSendData = {};
    jsonSendData['setting'] = setting;
    jsonSendData['function'] = name;

    PWM_MAIN.showWaitDialog({loadFunction:function() {
        require(["dojo", "dojo/json"], function (dojo, json) {
            dojo.xhrPost({
                url: "ConfigEditor?processAction=executeSettingFunction&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                postData: json.stringify(jsonSendData),
                headers: {"Accept": "application/json"},
                contentType: "application/json;charset=utf-8",
                encoding: "utf-8",
                handleAs: "json",
                dataType: "json",
                preventCache: true,
                load: function (data) {
                    PWM_MAIN.closeWaitDialog();
                    if (data['error']) {
                        var errorBody = '<div style="max-width: 400px">' + data['errorMessage'] + '<br/><br/>' + data['errorDetail'] + '</div>';
                        PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: errorBody, okAction: function () {
                            PWM_CFGEDIT.loadMainPageBody();
                        }});
                    } else {
                        var msgBody = '<div style="max-height: 400px; overflow-y: auto">' + data['successMessage'] + '</div>';
                        PWM_MAIN.showDialog({width:700,title: PWM_MAIN.showString("Title_Success"), text: msgBody, okAction: function () {
                            PWM_CFGEDIT.loadMainPageBody();
                        }});
                    }
                },
                error: function (errorObj) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showError("error executing function: " + errorObj);
                }
            });
        });
    }});
};

PWM_CFGEDIT.showChangeLog=function(confirmText, confirmFunction) {
    var url = "ConfigEditor?processAction=readChangeLog";
    var loadFunction = function(data) {
        PWM_MAIN.closeWaitDialog();
        if (data['error']) {
            PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
        } else {
            var bodyText = '<div class="changeLogViewBox">';
            bodyText += data['data'];
            bodyText += '</div>';
            if (confirmText != undefined) {
                bodyText += '<br/><div>' + confirmText + '</div>';
            }
            if (confirmFunction == undefined) {
                PWM_MAIN.showDialog({title: "Unsaved Configuration Editor Changes", text: bodyText, width: 650, showClose: true});
            } else {
                PWM_MAIN.showConfirmDialog({title: "Unsaved Configuration Editor Changes", text: bodyText, width: 650, showClose: true, okAction:confirmFunction});
            }
        }
    };
    PWM_MAIN.showWaitDialog({loadFunction: function () {
        PWM_MAIN.ajaxRequest(url, loadFunction);
    }});
};


PWM_CFGEDIT.processSettingSearch = function(searchValue, destinationDiv) {
    var url = "ConfigEditor?processAction=search";
    var data = {search:searchValue};

    destinationDiv.style.visibility = 'hidden';
    PWM_MAIN.getObject('noSearchResultsIndicator').style.display = 'none';

    var loadFunction = function(data) {
        if (!data) {
            return;
        }
        if (data['error']) {
            PWM_MAIN.showErrorDialog(data);
        } else {
            var bodyText = '';
            var resultCount = 0;
            if (PWM_MAIN.isEmpty(data['data'])) {
                if (searchValue) {
                    PWM_MAIN.getObject('noSearchResultsIndicator').style.display = 'inline';
                }
            } else {
                for (var categoryIter in data['data']) {
                    var category = data['data'][categoryIter];
                    bodyText += '<span style="font-weight: bold">' + categoryIter + '</span><br/>';
                    for (var settingIter in category) {
                        var setting = category[settingIter];
                        var profileID = setting['profile'];
                        var functionText;
                        if (profileID) {
                            functionText = 'PWM_CFGEDIT.gotoSetting(\'' + setting['category'] + '\',\'' + settingIter + '\',\'' + profileID + '\')';
                        } else {
                            functionText = 'PWM_CFGEDIT.gotoSetting(\'' + setting['category'] + '\',\'' + settingIter + '\')';
                        }

                        bodyText += '<span>&nbsp;&nbsp;</span>';
                        var settingID = "search_" + (profileID ? profileID + '_' : '') + settingIter;
                        bodyText += '<span id="' + settingID + '" style="text-indent: 1.5em; margin-left 10px; cursor: pointer; text-decoration: underline" onclick="' + functionText + '">';
                        bodyText += PWM_SETTINGS['settings'][settingIter]['label'];
                        bodyText += '</span>&nbsp;<span id="' + settingID + '_popup" class="btn-icon fa fa-info-circle"></span>';
                        if (!setting['default']) {
                            bodyText += '<span class="fa fa-star" title="Setting has been modified" style="color:#d20734">&nbsp;</span>';
                        }
                        bodyText += '<br/>';
                        resultCount++;
                    }
                }
                destinationDiv.style.visibility = 'visible';
                destinationDiv.innerHTML = bodyText;
                PWM_MAIN.flashDomElement('#000000', destinationDiv.id, 300);
                (function () {
                    for (var categoryIter in data['data']) {
                        var category = data['data'][categoryIter];
                        for (var settingIter in category) {
                            var setting = category[settingIter];
                            var profileID = setting['profile'];
                            var settingID = "search_" + (profileID ? profileID + '_' : '') + settingIter;
                            var value = setting['value'];
                            var toolBody = '<span style="font-weight: bold">Setting</span>';
                            toolBody += '<br/>' + PWM_SETTINGS['settings'][settingIter]['label'] + '<br/><br/>';
                            toolBody += '<span style="font-weight: bold">Description</span>';
                            toolBody += '<br/>' + PWM_SETTINGS['settings'][settingIter]['description'] + '<br/><br/>';
                            toolBody += '<span style="font-weight: bold">Value</span>';
                            toolBody += '<br/>' + value.replace('\n', '<br/>') + '<br/>';
                            PWM_MAIN.showTooltip({
                                id: settingID + '_popup',
                                text: toolBody,
                                width: 500
                            });
                        }
                    }
                }());
            }
        }
    };
    var validationProps = {};
    validationProps['serviceURL'] = url;
    validationProps['readDataFunction'] = function(){
        PWM_MAIN.getObject('searchIndicator').style.display = 'inline';
        return data;
    };
    validationProps['completeFunction'] = function() {
        PWM_MAIN.getObject('searchIndicator').style.display = 'none';
    };
    validationProps['processResultsFunction'] = loadFunction;
    PWM_MAIN.pwmFormValidator(validationProps);
    //PWM_MAIN.ajaxRequest(url,loadFunction,{content:data})
};


PWM_CFGEDIT.gotoSetting = function(category,settingKey,profile) {
    console.log('going to setting...');
    PWM_VAR['preferences']['category'] = category;
    PWM_VAR['preferences']['setting'] = settingKey ? settingKey : '';
    if (profile) {
        PWM_VAR['preferences']['profile'] = profile;
    } else {
        PWM_VAR['preferences']['profile'] = '';
    }
    PWM_CFGEDIT.setConfigEditorCookie();
    PWM_CFGEDIT.displaySettingsCategory(category);

    PWM_MAIN.getObject('homeSettingSearch').value = '';
    PWM_CFGEDIT.handleSearchFieldInput();

    if (settingKey) {
        setTimeout(function(){
            location.href = "#setting-"+settingKey;
            PWM_MAIN.flashDomElement('red','outline_' + settingKey,5000);
        },1000);
    }
};

PWM_CFGEDIT.toggleAdvancedSettingsDisplay = function(options) {
    require(["dojo","dojo/fx","dojo/on"], function(dojo,fx,on) {
        var advSetElement = PWM_MAIN.getObject('advancedSettings');
        if (PWM_VAR['advancedSettingsAreVisible']) {
            console.log("hiding advanced settings");
            try { PWM_MAIN.getObject('showAdvancedSettingsButton').style.display='block'; } catch(e) {
                console.log("can't show 'showAdvancedSettingsButton', error: " + e);
            }
            try { PWM_MAIN.getObject('hideAdvancedSettingsButton').style.display='none'; } catch(e) {
                console.log("can't hide 'hideAdvancedSettingsButton', error: " + e);
            }
            fx.wipeOut({node:advSetElement }).play();
            PWM_VAR['preferences']['level'] = 1;
        } else {
            console.log("showing advanced settings");
            try { PWM_MAIN.getObject('showAdvancedSettingsButton').style.display='none';  } catch(e) {
                console.log("can't hide 'showAdvancedSettingsButton', error: " + e);
            }
            try { PWM_MAIN.getObject('hideAdvancedSettingsButton').style.display='block';  } catch(e) {
                console.log("can't show 'hideAdvancedSettingsButton', error: " + e);
            }
            var animChain = fx.wipeIn({node:advSetElement });
            if (options && options['autoScroll']) {
                on(animChain, "End", function () {
                    console.log('scrolling to object id="hideAdvancedSettingsButton"');
                    window.scrollBy(0, 99999);
                    PWM_MAIN.getObject('hideAdvancedSettingsButton').scrollIntoView(true);
                    window.scrollBy(0, -100);
                });
            };
            animChain.play();
            PWM_VAR['preferences']['level'] = 2;
        }
        PWM_CFGEDIT.setConfigEditorCookie();
        PWM_VAR['advancedSettingsAreVisible'] = !PWM_VAR['advancedSettingsAreVisible'];
    });
};

PWM_CFGEDIT.cancelEditing = function() {
    var url =  "ConfigEditor?processAction=readChangeLog";
    var loadFunction = function(data) {
        PWM_MAIN.closeWaitDialog();
        if (data['error']) {
            PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
        } else {
            var bodyText = '<div class="changeLogViewBox">';
            bodyText += data['data'];
            bodyText += '</div><br/><div>';
            bodyText += PWM_CONFIG.showString('MenuDisplay_CancelConfig');
            bodyText += '</div>';
            PWM_MAIN.showConfirmDialog({width:650,text:bodyText,okAction:
                function () {
                    PWM_MAIN.showWaitDialog({loadFunction: function () {
                        PWM_MAIN.ajaxRequest('ConfigEditor?processAction=cancelEditing',function(){
                            PWM_MAIN.goto('ConfigManager', {addFormID: true});
                        });
                    }});
                }
            });
        }
    };
    PWM_MAIN.ajaxRequest(url, loadFunction);
};

PWM_CFGEDIT.showMacroHelp = function() {
    require(["dijit/Dialog"],function(Dialog) {
        var idName = 'dialogPopup';
        PWM_MAIN.clearDijitWidget(idName);
        var theDialog = new Dialog({
            id: idName,
            title: 'Macro Help',
            style: "width: 650px",
            href: PWM_GLOBAL['url-resources'] + "/text/macroHelp.html"
        });
        theDialog.show();
    });
};

PWM_CFGEDIT.showDateTimeFormatHelp = function() {
    require(["dijit/Dialog"],function(Dialog) {
        var idName = 'dateTimePopup';
        PWM_MAIN.clearDijitWidget(idName);
        var theDialog = new Dialog({
            id: idName,
            title: 'Macro Help',
            style: "width: 700px",
            href: PWM_GLOBAL['url-resources'] + "/text/datetimeFormatHelp.html"
        });
        theDialog.show();
    });
};

PWM_CFGEDIT.ldapHealthCheck = function() {
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        require(["dojo", "dojo/json"], function (dojo, json) {
            dojo.xhrPost({
                url: "ConfigEditor?processAction=ldapHealthCheck&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                headers: {"Accept": "application/json"},
                contentType: "application/json;charset=utf-8",
                encoding: "utf-8",
                handleAs: "json",
                dataType: "json",
                preventCache: true,
                load: function (data) {
                    PWM_MAIN.closeWaitDialog();
                    if (data['error']) {
                        PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
                    } else {
                        var bodyText = PWM_ADMIN.makeHealthHtml(data['data'],false,false);
                        var profileName = PWM_VAR['preferences']['profile'] && PWM_VAR['preferences']['profile'].length > 0 ? PWM_VAR['preferences']['profile'] : "Default";
                        var titleText = PWM_MAIN.showString('Field_LdapProfile') + ": " + profileName;
                        PWM_MAIN.showDialog({width:550,text:bodyText,title:titleText});
                    }
                },
                error: function (errorObj) {
                    PWM_MAIN.closeWaitDialog();
                    alert("error executing function: " + errorObj);
                }
            });
        });
    }});
};

PWM_CFGEDIT.databaseHealthCheck = function() {
    PWM_MAIN.showWaitDialog({title:'Checking database connection...',loadFunction:function(){
        var url =  "ConfigEditor?processAction=databaseHealthCheck";
        var loadFunction = function(data) {
            PWM_MAIN.closeWaitDialog();
            if (data['error']) {
                PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
            } else {
                var bodyText = PWM_ADMIN.makeHealthHtml(data['data'],false,false);
                var titleText = 'Database Connection Status';
                PWM_MAIN.showDialog({width:550,text:bodyText,title:titleText});
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction);
    }});
};

PWM_CFGEDIT.smsHealthCheck = function() {
    require(["dojo/dom-form"], function(domForm){
        var dialogBody = '<form id="smsCheckParametersForm"><table>';
        dialogBody += '<tr><td>To</td><td><input name="to" type="text" value="555-1212"/></td></tr>';
        dialogBody += '<tr><td>Message</td><td><input name="message" type="text" value="Test Message"/></td></tr>';
        dialogBody += '</table></form>';
        PWM_MAIN.showDialog({text:dialogBody,showCancel:true,title:'Test SMS connection',closeOnOk:false,okAction:function(){
            var formElement = PWM_MAIN.getObject("smsCheckParametersForm");
            var formData = domForm.toObject(formElement);
            var url =  "ConfigEditor?processAction=smsHealthCheck";
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                var loadFunction = function(data) {
                    if (data['error']) {
                        PWM_MAIN.showErrorDialog(data);
                    } else {
                        var bodyText = PWM_ADMIN.makeHealthHtml(data['data'],false,false);
                        var titleText = 'SMS Send Message Status';
                        PWM_MAIN.showDialog({width:550,text:bodyText,title:titleText,showCancel:true});
                    }

                };
                PWM_MAIN.ajaxRequest(url,loadFunction,{content:formData});
            }});
        }});
    });
};

PWM_CFGEDIT.selectTemplate = function(newTemplate) {
    PWM_MAIN.showConfirmDialog({
        text: PWM_CONFIG.showString('Warning_ChangeTemplate'),
        okAction: function () {
            PWM_MAIN.showWaitDialog({loadFunction: function () {
                var url = "ConfigEditor?processAction=setOption&template=" + newTemplate;
                var loadFunction = function (data) {
                    PWM_CFGEDIT.goto('ConfigEditor');
                };
                PWM_MAIN.ajaxRequest(url, loadFunction);
            }});
        }
    });
};

PWM_CFGEDIT.loadMainPageBody = function() {
    var storedPreferences = PWM_CFGEDIT.readLocalStorage();
    if (storedPreferences['lastSelected']) {
        PWM_CFGEDIT.dispatchNavigationItem(storedPreferences['lastSelected']);
    } else {
        PWM_CFGEDIT.drawHomePage();
    }

    PWM_CFGEDIT.drawNavigationMenu();
};

PWM_CFGEDIT.displaySettingsCategory = function(category) {
    var settingsPanel = PWM_MAIN.getObject('settingsPanel');
    settingsPanel.innerHTML = PWM_MAIN.showString('Display_PleaseWait');
    console.log('loadingSettingsCategory: ' + category);

    if (!category) {
        settingsPanel.innerHTML = '';
        console.log('no selected category');
        return;
    }
    var htmlSettingBody = '';

    if (category == 'LDAP_PROFILE') {
        htmlSettingBody += '<div style="width: 100%; text-align: center">'
            + '<button class="btn" id="button-test-LDAP_PROFILE"><span class="btn-icon fa fa-bolt"></span>Test LDAP Profile</button>'
            + '</div>';
    } else if (category == 'DATABASE') {
        htmlSettingBody += '<div style="width: 100%; text-align: center">'
            + '<button class="btn" id="button-test-DATABASE"><span class="btn-icon fa fa-bolt"></span>Test Database Settings</button>'
            + '</div>';
    } else if (category == 'SMS_GATEWAY') {
        htmlSettingBody += '<div style="width: 100%; text-align: center">'
            + '<button class="btn" id="button-test-SMS"><span class="btn-icon fa fa-bolt"></span>Test SMS Settings</button>'
            + '</div>';
    }

    for (var loopSetting in PWM_SETTINGS['settings']) {
        (function(settingKey) {
            var settingInfo = PWM_SETTINGS['settings'][settingKey];
            if (settingInfo['category'] == category && !settingInfo['hidden']) {
                htmlSettingBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo);
            }
        })(loopSetting);
    }
    settingsPanel.innerHTML = htmlSettingBody;
    for (var loopSetting in PWM_SETTINGS['settings']) {
        (function(settingKey) {
            var settingInfo = PWM_SETTINGS['settings'][settingKey];
            if (settingInfo['category'] == category && !settingInfo['hidden']) {
                PWM_CFGEDIT.initSettingDisplay(settingInfo);
            }
        })(loopSetting);
    }
    if (category == 'LDAP_PROFILE') {
        PWM_MAIN.addEventHandler('button-test-LDAP_PROFILE', 'click', function(){PWM_CFGEDIT.ldapHealthCheck();});
    } else if (category == 'DATABASE') {
        PWM_MAIN.addEventHandler('button-test-DATABASE', 'click', function(){PWM_CFGEDIT.databaseHealthCheck();});
    } else if (category == 'SMS_GATEWAY') {
        PWM_MAIN.addEventHandler('button-test-SMS', 'click', function(){PWM_CFGEDIT.smsHealthCheck();});
    }
};

PWM_CFGEDIT.drawProfileEditorPage = function(settingKey) {
    var settingsPanel = PWM_MAIN.getObject('settingsPanel');
    settingsPanel.innerHTML = PWM_MAIN.showString('Display_PleaseWait');
    var settingInfo = PWM_SETTINGS['settings'][settingKey];
    console.log('drawing profile-editor for setting-' + settingKey);

    settingsPanel.innerHTML = PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo);
    PWM_CFGEDIT.initSettingDisplay(settingInfo);
};

PWM_CFGEDIT.drawHtmlOutlineForSetting = function(settingInfo) {
    var settingKey = settingInfo['key'];
    var settingLabel = settingInfo['label'];
    return '<div id="outline_' + settingKey + '" class="setting_outline">'
        + '<div class="setting_title" id="title_' + settingKey + '">'
        + '<div class="fa fa-star modifiedNoticeIcon" title="Setting has been modified" id="modifiedNoticeIcon-' + settingKey + '" style="display: none" ></div>'
        + '<a id="setting-' + settingKey + '" class="text">' + settingLabel + '</a>'
        + '<div class="fa fa-question-circle icon_button" title="Help" id="helpButton-' + settingKey + '"></div>'
        + '<div style="visibility: hidden" class="fa fa-undo icon_button" title="Reset" id="resetButton-' + settingKey + '"></div>'
        + '</div>' // close title
        + '<div id="titlePane_' + settingKey + '" class="setting_body">'
        + '<div id="table_setting_' + settingKey + '" style="border:0 none">'
        + '</div>' // close setting;
        + '</div>' // close body;
        + '</div>'  // close outline
        ;
};

PWM_CFGEDIT.initSettingDisplay = function(setting) {
    var settingKey = setting['key'];
    //console.log('initializing handler for ' + settingKey);

    PWM_MAIN.showTooltip({
        id: "modifiedNoticeIcon-" + settingKey,
        text: 'Setting has been modified from the default value'
    });
    PWM_MAIN.showTooltip({
        id: "resetButton-" + settingKey,
        text: PWM_CONFIG.showString('Tooltip_ResetButton')
    });
    PWM_MAIN.showTooltip({
        id: "helpButton-" + settingKey,
        text: PWM_CONFIG.showString('Tooltip_HelpButton')
    });
    PWM_MAIN.addEventHandler('helpButton-' + settingKey, 'click', function () {
        PWM_MAIN.showDialog({
            text: setting['description'],
            title: setting['label'],
            allowMove: true,
            showClose: true
        });
    });
    PWM_MAIN.addEventHandler('resetButton-' + settingKey, 'click', function () {
        handleResetClick(settingKey);
    });

    switch (setting['syntax']) {
        case 'FORM':
            FormTableHandler.init(settingKey,{});
            break;

        case 'OPTIONLIST':
            OptionListHandler.init(settingKey);
            break;

        case 'EMAIL':
            EmailTableHandler.init(settingKey);
            break;

        case 'ACTION':
            ActionHandler.init(settingKey);
            break;

        case 'PASSWORD':
            ChangePasswordHandler.init(settingKey);
            break;

        case 'NUMERIC':
            NumericValueHandler.init(settingKey);
            break;

        case 'DURATION':
            DurationValueHandler.init(settingKey);
            break;

        case 'STRING':
            StringValueHandler.init(settingKey);
            break;

        case 'TEXT_AREA':
            TextAreaValueHandler.init(settingKey);
            break;

        case 'SELECT':
            SelectValueHandler.init(settingKey);
            break;

        case 'BOOLEAN':
            BooleanHandler.init(settingKey);
            break;

        case 'LOCALIZED_STRING_ARRAY':
            MultiLocaleTableHandler.initMultiLocaleTable(settingKey);
            break;

        case 'STRING_ARRAY':
        case 'PROFILE':
            StringArrayValueHandler.init(settingKey);
            break;

        case 'LOCALIZED_STRING':
        case 'LOCALIZED_TEXT_AREA':
            LocalizedStringValueHandler.init(settingKey, '', setting['syntax']);
            break;

        case 'USER_PERMISSION':
            UserPermissionHandler.init(settingKey);
            break;

        case 'CHALLENGE':
            ChallengeSettingHandler.init(settingKey);
            break;

        case 'X509CERT':
            X509CertificateHandler.init(settingKey);
            break;

        case 'FILE':
            FileValueHandler.init(settingKey);
            break;

        default:
            alert('unknown setting syntax type: ' + setting['syntax']);

    }
};

PWM_CFGEDIT.drawNavigationMenu = function() {
    var makeTreeFunction = function(menuTreeData) {
        PWM_MAIN.clearDijitWidget('navigationTree')
        require(["dojo/_base/window", "dojo/store/Memory", "dijit/tree/ObjectStoreModel", "dijit/Tree","dijit","dojo/domReady!"],
            function(win, Memory, ObjectStoreModel, Tree)
            {
                // Create test store, adding the getChildren() method required by ObjectStoreModel
                var myStore = new Memory({
                    data: menuTreeData,
                    getChildren: function(object){
                        return this.query({parent: object.id});
                    }
                });

                // Create the model
                var model = new ObjectStoreModel({
                    store: myStore,
                    query: {id: 'ROOT'}
                });

                // Create the Tree.
                var tree = new Tree({
                    model: model,
                    persist: true,
                    getIconClass: function(/*dojo.store.Item*/ item, /*Boolean*/ opened){
                        return 'tree-noicon';
                    },
                    showRoot: false,
                    //openOnClick: true,
                    id: 'navigationTree',
                    onClick: function(item){
                        PWM_CFGEDIT.dispatchNavigationItem(item);
                    }
                });

                tree.placeAt(PWM_MAIN.getObject('navigationTree'));
                tree.startup();
                PWM_VAR['navigationTree'] = tree; // used for expand/collapse button events;
            }
        );
    };

    PWM_MAIN.ajaxRequest('ConfigEditor?processAction=menuTreeData',function(data){
        var menuTreeData = data['data'];
        makeTreeFunction(menuTreeData);
    },{method:'GET'});
};

PWM_CFGEDIT.dispatchNavigationItem = function(item) {
    var currentID = item['id'];
    var type = item['type'];
    if (currentID == 'HOME') {
        PWM_CFGEDIT.drawHomePage();
    } else if (type == 'category') {
        PWM_CFGEDIT.gotoSetting(currentID);
    } else if (type == 'displayText') {
        var keys = item['keys'];
        PWM_CFGEDIT.drawDisplayTextPage(currentID,keys);
    } else if (type == 'profile') {
        var category = item['category'];
        PWM_CFGEDIT.gotoSetting(category,null,currentID);
    } else if (type == 'profile-definition') {
        var profileSettingKey = item['profile-setting'];
        PWM_CFGEDIT.drawProfileEditorPage(profileSettingKey);
    }

    var storedPreferences = PWM_CFGEDIT.readLocalStorage();
    storedPreferences['lastSelected'] = item;
    PWM_CFGEDIT.readLocalStorage(storedPreferences);

    PWM_MAIN.getObject('currentPageDisplay').innerHTML = ' - ' + item['name'];
};

PWM_CFGEDIT.drawDisplayTextPage = function(settingKey, keys) {
    var settingsPanel = PWM_MAIN.getObject('settingsPanel');
    var remainingLoads = keys.length;
    settingsPanel.innerHTML = '<div id="displaytext-loading-panel" style="width:100%; text-align: center">'
        + PWM_MAIN.showString('Display_PleaseWait') + '<span id="remainingCount"></div>';
    console.log('drawing displaytext-editor for setting-' + settingKey);
    var htmlBody = '<div id="localetext-editor-wrapper" style="display:none">';
    for (var key in keys) {
        var displayKey = 'localeBundle-' + settingKey + '-' + keys[key];
        var settingInfo = {};
        settingInfo['key'] = displayKey;
        settingInfo['label'] = keys[key];
        htmlBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo);
    }
    settingsPanel.innerHTML = settingsPanel.innerHTML + htmlBody;
    var delayTimeout = 0;
    for (var key in keys) {
        delayTimeout += 20;
        (function(keyCounter) {
            setTimeout(function(){
                var displayKey = 'localeBundle-' + settingKey + '-' + keys[keyCounter];
                var settingInfo = {};
                settingInfo['key'] = displayKey;
                settingInfo['label'] = keys[keyCounter];
                settingInfo['syntax'] = 'LOCALIZED_STRING';
                PWM_CFGEDIT.initSettingDisplay(settingInfo);
                remainingLoads--;
                PWM_MAIN.getObject('remainingCount').innerHTML = remainingLoads;
            },delayTimeout);
        })(key);
    }
    var checkForFinishFunction = function() {
        console.log('checking for finish function...');
        setTimeout(function(){
            if (PWM_VAR['outstandingOperations'] == 0) {
                PWM_MAIN.getObject('displaytext-loading-panel').style.display = 'none';
                PWM_MAIN.getObject('localetext-editor-wrapper').style.display = 'inherit';
            } else {
                console.log('nope!');
                setTimeout(checkForFinishFunction,1000);
            }
        },1000);
    };
    checkForFinishFunction();
};

PWM_CFGEDIT.handleSearchFieldInput = function() {
    var searchTerm = PWM_MAIN.getObject('homeSettingSearch').value;
    PWM_CFGEDIT.processSettingSearch(
        searchTerm,
        PWM_MAIN.getObject('searchResults')
    );
};

PWM_CFGEDIT.drawHomePage = function() {
    var settingsPanel = PWM_MAIN.getObject('settingsPanel');
    settingsPanel.innerHTML = PWM_MAIN.showString('Display_PleaseWait');

    var templateSettingBody = '';
    templateSettingBody += '<div><select onchange="PWM_CFGEDIT.selectTemplate(this.options[this.selectedIndex].value)">';
    for (var template in PWM_SETTINGS['templates']) {
        var templateInfo = PWM_SETTINGS['templates'][template];
        templateSettingBody += '<option value="' + templateInfo['key'] + '"';
        if (PWM_VAR['currentTemplate'] == templateInfo['key']) {
            templateSettingBody += ' selected="selected"';
        }
        templateSettingBody += '>' + templateInfo['description'] + '</option>';
    }
    templateSettingBody += '</select></div>';

    var notesSettingBody = '';
    notesSettingBody += '<div><textarea id="configurationNotesTextarea">' + PWM_VAR['configurationNotes'] + '</textarea></div>';

    var templateSelectSetting = {};
    templateSelectSetting['key'] = 'templateSelect';
    templateSelectSetting['label'] = 'Configuration Template';
    var htmlBody = PWM_CFGEDIT.drawHtmlOutlineForSetting(templateSelectSetting);

    var notesSettings = {};
    notesSettings['key'] = 'configurationNotes';
    notesSettings['label'] = 'Configuration Notes';
    htmlBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(notesSettings);

    settingsPanel.innerHTML = htmlBody;

    PWM_MAIN.getObject('table_setting_templateSelect').innerHTML = templateSettingBody;
    PWM_MAIN.getObject('table_setting_configurationNotes').innerHTML = notesSettingBody;

    PWM_MAIN.addEventHandler('helpButton-templateSelect','click',function(){
        PWM_MAIN.showDialog({text:PWM_CONFIG.showString('Display_AboutTemplates')});
    });
    PWM_MAIN.addEventHandler('configurationNotesTextarea','input',function(){
        var value = PWM_MAIN.getObject('configurationNotesTextarea').value;
        PWM_VAR['configurationNotes'] = value;
        var url = "ConfigEditor?processAction=setOption&updateNotesText=true";
        PWM_MAIN.ajaxRequest(url,function(){console.log('saved config notes')},{content:value});
    });
};

PWM_CFGEDIT.readLocalStorage = function(dataUpdate) {
    if(typeof(Storage) !== "undefined") {
        if (dataUpdate) {
            localStorage.setItem("ConfigEditor_Storage",JSON.stringify(dataUpdate));
        }
        var storedStr = localStorage.getItem("ConfigEditor_Storage");
        if (storedStr) {
            try {
                return JSON.parse(storedStr);
            } catch (e) {
                console.error('Error decoding existing local storage value: ' + e);
            }
        }
        return {};
    } else {
        console.log("browser doesn't support local storage");
    }
};

