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

PWM_VAR['outstandingOperations'] = 0;
PWM_VAR['skippedSettingCount'] = 0;


PWM_CFGEDIT.readSetting = function(keyName, valueWriter) {
    var modifiedOnly = PWM_CFGEDIT.readNavigationFilters()['modifiedSettingsOnly'];
    var maxLevel = parseInt(PWM_CFGEDIT.readNavigationFilters()['level']);
    PWM_VAR['outstandingOperations']++;
    PWM_CFGEDIT.handleWorkingIcon();
    var url = "ConfigEditor?processAction=readSetting&key=" + keyName;
    if (PWM_CFGEDIT.readCurrentProfile()) {
        url = PWM_MAIN.addParamToUrl(url, 'profile', PWM_CFGEDIT.readCurrentProfile());
    }
    var loadFunction = function(data) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        console.log('read data for setting ' + keyName);
        var resultValue = data['data']['value'];
        var isDefault = data['data']['isDefault'];
        var settingLevel = 0;
        if (PWM_SETTINGS['settings'][keyName] && PWM_SETTINGS['settings'][keyName]['level']) {
            settingLevel = PWM_SETTINGS['settings'][keyName]['level'];
        }
        var showSetting = (PWM_SETTINGS['settings'][keyName] && PWM_SETTINGS['settings'][keyName]['syntax'] == 'PROFILE') ||   (!modifiedOnly || !isDefault) && (maxLevel < 0 || settingLevel  <= maxLevel );
        if (showSetting) {
            valueWriter(resultValue);
            PWM_MAIN.setStyle('outline_' + keyName,'display','inherit');
            PWM_CFGEDIT.updateSettingDisplay(keyName, isDefault);
            PWM_CFGEDIT.updateLastModifiedInfo(keyName, data);
        } else {
            PWM_MAIN.setStyle('outline_' + keyName,'display','none');
            PWM_VAR['skippedSettingCount']++;
            if (PWM_VAR['skippedSettingCount'] > 0 && PWM_MAIN.getObject('panel-skippedSettingInfo')) {
                PWM_MAIN.getObject('panel-skippedSettingInfo').innerHTML = "" + PWM_VAR['skippedSettingCount'] + " items are not shown due to filter settings."
            }
        }
    };
    var errorFunction = function(error) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Error'),text:"Unable to communicate with server.  Please refresh page."});
        console.log("error loading " + keyName + ", reason: " + error);
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction});
};

PWM_CFGEDIT.updateLastModifiedInfo = function(keyName, data) {
    if (PWM_MAIN.getObject('panel-' + keyName + '-modifyTime')) {
        if (data['data']['modifyTime']) {
            PWM_MAIN.getObject('panel-' + keyName + '-modifyTime').innerHTML = 'Last Modified '
            + '<span id="panel-' + keyName + '-modifyTimestamp">' + data['data']['modifyTime'] + '</span>';
            PWM_MAIN.TimestampHandler.initElement(PWM_MAIN.getObject('panel-' + keyName + '-modifyTimestamp'));
        } else {
            PWM_MAIN.getObject('panel-' + keyName + '-modifyTime').innerHTML = '';
        }
    }
    if (PWM_MAIN.getObject('panel-' + keyName + '-modifyUser')) {
        if (data['data']['modifyUser']) {
            var output = 'Modified by ' + data['data']['modifyUser']['userDN'];
            if (data['data']['modifyUser']['ldapProfile'] && data['data']['modifyUser']['ldapProfile'] != "default") {
                output += ' [' + data['data']['modifyUser']['ldapProfile'] + ']';
            }
            PWM_MAIN.getObject('panel-' + keyName + '-modifyUser').innerHTML = output;
        } else {
            PWM_MAIN.getObject('panel-' + keyName + '-modifyUser').innerHTML = '';
        }
    }
};

PWM_CFGEDIT.writeSetting = function(keyName, valueData, nextAction) {
    PWM_VAR['outstandingOperations']++;
    PWM_CFGEDIT.handleWorkingIcon();
    var url = "ConfigEditor?processAction=writeSetting&key=" + keyName;
    if (PWM_CFGEDIT.readCurrentProfile()) {
        url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
    }
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

PWM_CFGEDIT.resetSetting=function(keyName, nextAction) {
    var url = "ConfigEditor?processAction=resetSetting&key=" + keyName;
    if (PWM_CFGEDIT.readCurrentProfile()) {
        url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
    }
    var loadFunction = function() {
        console.log('reset data for ' + keyName);
        if (nextAction !== undefined) {
            nextAction();
        }
    };
    PWM_MAIN.ajaxRequest(url,loadFunction);
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

PWM_CFGEDIT.getSettingValueElement = function(settingKey) {
    var parentDiv = 'table_setting_' + settingKey;
    return PWM_MAIN.getObject(parentDiv);
};

PWM_CFGEDIT.clearDivElements = function(parentDiv) {
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    if (parentDivElement != null) {
        if (parentDivElement.hasChildNodes()) {
            while (parentDivElement.childNodes.length >= 1) {
                var firstChild = parentDivElement.firstChild;
                parentDivElement.removeChild(firstChild);
            }
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
    addItemButton.setAttribute("class", "btn");
    addItemButton.onclick = addFunction;
    addItemButton.innerHTML = "Add Value";
    newTableData.appendChild(addItemButton);

    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
    newTableRow.appendChild(newTableData);
};

PWM_CFGEDIT.readInitialTextBasedValue = function(key) {
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
};

PWM_CFGEDIT.saveConfiguration = function() {
    PWM_VAR['cancelHeartbeatCheck'] = true;
    PWM_MAIN.preloadAll(function(){
        var confirmText = PWM_CONFIG.showString('MenuDisplay_SaveConfig');
        var confirmFunction = function(){
            var url = "ConfigEditor?processAction=finishEditing";
            var loadFunction = function(data) {
                if (data['error'] == true) {
                    PWM_MAIN.showErrorDialog(data);
                } else {
                    console.log('save completed');
                    PWM_MAIN.showWaitDialog({title:'Save complete, restarting application...',loadFunction:function(){
                        PWM_CONFIG.waitForRestart({location:'/'});
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

    var writeFunction = function(passwordValue) {
        PWM_CFGEDIT.setConfigurationPassword(passwordValue);
    };
    ChangePasswordHandler.popup('configPw','Configuration Password',writeFunction);
};


function handleResetClick(settingKey) {
    var label = PWM_SETTINGS['settings'][settingKey] ? PWM_SETTINGS['settings'][settingKey]['label'] : ' ';
    var dialogText = PWM_CONFIG.showString('Warning_ResetSetting',{value1:label});
    var titleText = 'Reset ' + label ? label : '';

    PWM_MAIN.showConfirmDialog({title:titleText,text:dialogText,okAction:function(){
        PWM_CFGEDIT.resetSetting(settingKey,function(){
            PWM_CFGEDIT.loadMainPageBody();
        });
    }});
}

PWM_CFGEDIT.initConfigEditor = function(nextFunction) {
    PWM_MAIN.addEventHandler('homeSettingSearch','input',function(){PWM_CFGEDIT.processSettingSearch(PWM_MAIN.getObject('searchResults'));});
    PWM_MAIN.addEventHandler('button-navigationExpandAll','click',function(){PWM_VAR['navigationTree'].expandAll()});
    PWM_MAIN.addEventHandler('button-navigationCollapseAll','click',function(){PWM_VAR['navigationTree'].collapseAll()});

    PWM_MAIN.addEventHandler('cancelButton_icon','click',function(){PWM_CFGEDIT.cancelEditing()});
    PWM_MAIN.addEventHandler('saveButton_icon','click',function(){PWM_CFGEDIT.saveConfiguration()});
    PWM_MAIN.addEventHandler('setPassword_icon','click',function(){PWM_CFGEDIT.setConfigurationPassword()});
    PWM_MAIN.addEventHandler('referenceDoc_icon','click',function(){
        PWM_MAIN.newWindowOpen(PWM_GLOBAL['url-context'] + '/public/reference/referencedoc.jsp#settings','referencedoc');
    });
    PWM_MAIN.addEventHandler('macroDoc_icon','click',function(){ PWM_CFGEDIT.showMacroHelp(); });
    PWM_MAIN.addEventHandler('settingFilter_icon','click',function(){ PWM_CFGEDIT.showSettingFilter(); });

    PWM_CONFIG.heartbeatCheck();

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

    var requestUrl = "ConfigEditor?processAction=executeSettingFunction&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    if (PWM_CFGEDIT.readCurrentProfile()) {
        requestUrl = PWM_MAIN.addParamToUrl(requestUrl,'profile',PWM_CFGEDIT.readCurrentProfile());
    }

    PWM_MAIN.showWaitDialog({loadFunction:function() {
        require(["dojo", "dojo/json"], function (dojo, json) {
            dojo.xhrPost({
                url: requestUrl,
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
                        PWM_MAIN.showDialog({width:700,title: 'Results', text: msgBody, okAction: function () {
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
            bodyText += data['data']['html'];
            bodyText += '</div>';
            if (confirmText != undefined) {
                bodyText += '<br/><div>' + confirmText + '</div>';
            }
            if (confirmFunction == undefined) {
                PWM_MAIN.showDialog({title: "Unsaved Configuration Editor Changes", text: bodyText, dialogClass:'wide', showClose: true});
            } else {
                PWM_MAIN.showConfirmDialog({title: "Unsaved Configuration Editor Changes", text: bodyText, dialogClass:'wide', showClose: true, okAction:confirmFunction});
            }
        }
    };
    PWM_MAIN.showWaitDialog({loadFunction: function () {
        PWM_MAIN.ajaxRequest(url, loadFunction);
    }});
};

PWM_CFGEDIT.processSettingSearch = function(destinationDiv) {
    var iteration = 'settingSearchIteration' in PWM_VAR ? PWM_VAR['settingSearchIteration'] + 1 : 0;
    var startTime = new Date().getTime();
    PWM_VAR['settingSearchIteration'] = iteration;

    var resetDisplay = function() {
        PWM_MAIN.getObject('indicator-noResults').style.display = 'none';
        PWM_MAIN.getObject('indicator-searching').style.display = 'none';
        destinationDiv.style.visibility = 'hidden';
        destinationDiv.innerHTML = '';
    };

    var readSearchTerm = function() {
        if (!PWM_MAIN.getObject('homeSettingSearch') || !PWM_MAIN.getObject('homeSettingSearch') || PWM_MAIN.getObject('homeSettingSearch').value.length < 1) {
            return null;
        }
        return PWM_MAIN.getObject('homeSettingSearch').value;
    };

    console.log('beginning search #' + iteration);
    var url = "ConfigEditor?processAction=search";

    var loadFunction = function(data) {
        resetDisplay();

        if (!readSearchTerm()) {
            resetDisplay();
            return;
        }

        if (!data) {
            console.log('search #' + iteration + ", no data returned");
            return;
        }

        if (data['error']) {
            console.log('search #' + iteration + ", error returned: " + data);
            PWM_MAIN.showErrorDialog(data);
        } else {
            var bodyText = '';
            var resultCount = 0;
            var elapsedTime = (new Date().getTime()) - startTime;
            if (PWM_MAIN.isEmpty(data['data'])) {
                PWM_MAIN.getObject('indicator-noResults').style.display = 'inline';
                console.log('search #' + iteration + ', 0 results, ' + elapsedTime + 'ms');
            } else {
                for (var categoryIter in data['data']) {
                    var category = data['data'][categoryIter];
                    bodyText += '<div class="panel-searchResultCategory">' + categoryIter + '</div>';
                    for (var settingIter in category) {
                        var setting = category[settingIter];
                        var profileID = setting['profile'];
                        var linkID = 'link-' + setting['category'] + '-' + settingIter + (profileID ? profileID : '');
                        var settingID = "search_" + (profileID ? profileID + '_' : '') + settingIter;
                        bodyText += '<div><span id="' + linkID + '" class="panel-searchResultItem">';
                        bodyText += PWM_SETTINGS['settings'][settingIter]['label'];
                        bodyText += '</span>&nbsp;<span id="' + settingID + '_popup" class="btn-icon fa fa-info-circle"></span>';
                        if (!setting['default']) {
                            bodyText += '<span class="fa fa-star modifiedNoticeIcon" title="Setting has been modified">&nbsp;</span>';
                        }
                        bodyText += '</div>';
                        resultCount++;
                    }
                }
                console.log('search #' + iteration + ', ' + resultCount + ' results, ' + elapsedTime + 'ms');
                destinationDiv.style.visibility = 'visible';
                destinationDiv.innerHTML = bodyText;
                for (var categoryIter in data['data']) {
                    var category = data['data'][categoryIter];
                    for (var iter in category) {
                        (function (settingKey) {
                            var setting = category[settingKey];
                            var profileID = setting['profile'];
                            var settingID = "search_" + (profileID ? profileID + '_' : '') + settingKey;
                            var value = setting['value'];
                            var toolBody = '<span style="font-weight: bold">Setting</span>';
                            toolBody += '<br/>' + PWM_SETTINGS['settings'][settingKey]['label'] + '<br/><br/>';
                            toolBody += '<span style="font-weight: bold">Description</span>';
                            toolBody += '<br/>' + PWM_SETTINGS['settings'][settingKey]['description'] + '<br/><br/>';
                            toolBody += '<span style="font-weight: bold">Value</span>';
                            toolBody += '<br/>' + value.replace('\n', '<br/>') + '<br/>';
                            PWM_MAIN.showTooltip({
                                id: settingID + '_popup',
                                text: toolBody,
                                width: 500
                            });
                            var linkID = 'link-' + setting['category'] + '-' + settingKey + (profileID ? profileID : '');
                            PWM_MAIN.addEventHandler(linkID ,'click',function(){
                                resetDisplay();
                                PWM_CFGEDIT.gotoSetting(setting['category'],settingKey,profileID);
                            });
                        }(iter));
                    }
                }
            }
        }
    };
    var validationProps = {};
    validationProps['serviceURL'] = url;
    validationProps['readDataFunction'] = function(){
        resetDisplay();
        PWM_MAIN.getObject('indicator-searching').style.display = 'inline';

        var value = readSearchTerm();
        return {search:value,key:value};
    };
    validationProps['completeFunction'] = function() {
        PWM_MAIN.getObject('indicator-searching').style.display = 'none';
    };
    validationProps['processResultsFunction'] = loadFunction;
    PWM_MAIN.pwmFormValidator(validationProps);
};


PWM_CFGEDIT.gotoSetting = function(category,settingKey,profile) {
    console.log('going to setting... category=' + category + " settingKey=" + settingKey + " profile=" + profile);

    if (!category || (!(category in PWM_SETTINGS['categories']))) {
        console.log('can\'t process request to display settings category: ' + category );
        return;
    }

    PWM_CFGEDIT.setCurrentProfile(profile);
    PWM_CFGEDIT.displaySettingsCategory(category);

    if (PWM_SETTINGS['categories'][category]['label']) {
        PWM_MAIN.getObject('currentPageDisplay').innerHTML = ' - ' + PWM_SETTINGS['categories'][category]['label'];
    }

    var item = {};
    item['id'] = category;
    item['type'] = 'category';

    if (settingKey) {
        setTimeout(function(){
            var settingElement = PWM_CFGEDIT.getSettingValueElement(settingKey);
            console.log('navigating and highlighting setting ' + settingKey);
            //location.href = "#setting-" + settingKey;
            settingElement.scrollIntoView(true);
            if (settingElement.getBoundingClientRect().top < 100) {
                window.scrollBy(0, -100);
            }
            PWM_MAIN.flashDomElement('red','title_' + settingKey, 5000);
        },1000);
    }
};


PWM_CFGEDIT.cancelEditing = function() {
    var url =  "ConfigEditor?processAction=readChangeLog";
    PWM_MAIN.showWaitDialog({loadFunction:function(){
        var loadFunction = function(data) {
            if (data['error']) {
                PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
            } else {
                if (data['data']['modified'] == true) {
                    var bodyText = '<div class="changeLogViewBox">';
                    bodyText += data['data']['html'];
                    bodyText += '</div><br/><div>';
                    bodyText += PWM_CONFIG.showString('MenuDisplay_CancelConfig');
                    bodyText += '</div>';
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showConfirmDialog({dialogClass:'wide',showClose:true,allowMove:true,text:bodyText,okAction:
                        function () {
                            PWM_MAIN.showWaitDialog({loadFunction: function () {
                                PWM_MAIN.ajaxRequest('ConfigEditor?processAction=cancelEditing',function(){
                                    PWM_MAIN.goto('ConfigManager', {addFormID: true});
                                });
                            }});
                        }
                    });
                } else {
                    PWM_MAIN.goto('ConfigManager', {addFormID: true});
                }
            }
        };
        PWM_MAIN.ajaxRequest(url, loadFunction);
    }});
};

PWM_CFGEDIT.showMacroHelp = function() {
    require(["dijit/Dialog"],function(Dialog) {
        var idName = 'macroPopup';
        PWM_MAIN.clearDijitWidget(idName);
        var theDialog = new Dialog({
            id: idName,
            title: 'Macro Help',
            style: "width: 750px",
            href: PWM_GLOBAL['url-resources'] + "/text/macroHelp.html"
        });
        var attempts = 0;
        // iframe takes indeterminate amount of time to load, so just retry till it apperas
        var loadFunction = function() {
            if (PWM_MAIN.getObject('input-testMacroInput')) {
                console.log('connected to macroHelpDiv');
                setTimeout(function(){
                    PWM_MAIN.getObject('input-testMacroInput').focus();
                },500);
                PWM_MAIN.addEventHandler('button-testMacro','click',function(){
                    PWM_MAIN.getObject('panel-testMacroOutput').innerHTML = PWM_MAIN.showString('Display_PleaseWait');
                    var sendData = {};
                    sendData['input'] = PWM_MAIN.getObject('input-testMacroInput').value;
                    var url = "ConfigEditor?processAction=testMacro";
                    var loadFunction = function(data) {
                        PWM_MAIN.getObject('panel-testMacroOutput').innerHTML = data['data'];
                    };
                    PWM_MAIN.ajaxRequest(url,loadFunction,{content:sendData});
                });
            } else {
                if (attempts < 50) {
                    attempts++;
                    setTimeout(loadFunction,100);
                }
            }
        };
        theDialog.show();
        loadFunction();
    });
};

PWM_CFGEDIT.showTimezoneList = function() {
    require(["dijit/Dialog"],function(Dialog) {
        var idName = 'timezonePopup';
        PWM_MAIN.clearDijitWidget(idName);
        var theDialog = new Dialog({
            id: idName,
            title: 'Timezones',
            style: "width: 750px",
            href: PWM_GLOBAL['url-context'] + "/public/reference/timezones.jsp"
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
        var url = "ConfigEditor?processAction=ldapHealthCheck";
        url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
        var loadFunction = function(data) {
            PWM_MAIN.closeWaitDialog();
            if (data['error']) {
                PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
            } else {
                var bodyText = PWM_ADMIN.makeHealthHtml(data['data'],false,false);
                var profileName = PWM_CFGEDIT.readCurrentProfile();
                var titleText = PWM_MAIN.showString('Field_LdapProfile') + ": " + profileName;
                PWM_MAIN.showDialog({text:bodyText,title:titleText});
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction);
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
                PWM_MAIN.showDialog({text:bodyText,title:titleText});
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction);
    }});
};

PWM_CFGEDIT.smsHealthCheck = function() {
    require(["dojo/dom-form"], function(domForm){
        var dialogBody = '<p>' + PWM_CONFIG.showString('Warning_SmsTestData') + '</p><form id="smsCheckParametersForm"><table>';
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
                        PWM_MAIN.showDialog({text:bodyText,title:titleText,showCancel:true});
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

    PWM_CFGEDIT.drawNavigationMenu();
    var storedPreferences = PWM_MAIN.readLocalStorage();
    if (storedPreferences['lastSelected']) {
        PWM_CFGEDIT.dispatchNavigationItem(storedPreferences['lastSelected']);
    } else {
        PWM_CFGEDIT.drawHomePage();
    }
};

PWM_CFGEDIT.displaySettingsCategory = function(category) {
    var settingsPanel = PWM_MAIN.getObject('settingsPanel');
    settingsPanel.innerHTML = '';
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

    PWM_VAR['skippedSettingCount'] = 0;
    for (var loopSetting in PWM_SETTINGS['settings']) {
        (function(settingKey) {
            var settingInfo = PWM_SETTINGS['settings'][settingKey];
            if (settingInfo['category'] == category && !settingInfo['hidden']) {
                htmlSettingBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo);
            }
        })(loopSetting);
    }
    htmlSettingBody += '<div class="footnote" id="panel-skippedSettingInfo">';

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
    settingsPanel.innerHTML = '';
    var settingInfo = PWM_SETTINGS['settings'][settingKey];
    console.log('drawing profile-editor for setting-' + settingKey);

    settingsPanel.innerHTML = PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo);
    PWM_CFGEDIT.initSettingDisplay(settingInfo);
};

PWM_CFGEDIT.drawHtmlOutlineForSetting = function(settingInfo, options) {
    options = options === undefined ? {} : options;
    var settingKey = settingInfo['key'];
    var settingLabel = settingInfo['label'];
    var htmlBody = '<div id="outline_' + settingKey + '" class="setting_outline" style="display:none">'
        + '<div class="setting_title" id="title_' + settingKey + '">'
        + '<a id="setting-' + settingKey + '" class="text">' + settingLabel + '</a>'
        + '<div class="fa fa-pencil-square modifiedNoticeIcon" title="' + PWM_CONFIG.showString('Tooltip_ModifiedNotice') + '" id="modifiedNoticeIcon-' + settingKey + '" style="display: none" ></div>';

    if (settingInfo['description']) {
        htmlBody += '<div class="fa fa-question-circle icon_button" title="' + PWM_CONFIG.showString('Tooltip_HelpButton') + '" id="helpButton-' + settingKey + '"></div>';
    }

    htmlBody += '<div style="visibility: hidden" class="fa fa-undo icon_button" title="' + PWM_CONFIG.showString('Tooltip_ResetButton') + '" id="resetButton-' + settingKey + '"></div>'
    + '</div>' // close title
    + '<div id="titlePane_' + settingKey + '" class="setting_body">';

    if (settingInfo['description']) {
        var prefs = PWM_MAIN.readLocalStorage();
        var expandHelp = 'helpExpanded' in prefs && settingKey in prefs['helpExpanded'];
        htmlBody += '<div class="pane-help" id="pane-help-' + settingKey + '" style="display:' + (expandHelp ? 'inherit' : 'none') + '">'
        + settingInfo['description'] + '</div>';
    }

    htmlBody += '<div class="pane-settingValue" id="table_setting_' + settingKey + '" style="border:0 none">'
    + '</div>' // close setting;
    + '</div>' // close body
    + '<div class="footnote" style="width:100%"><span id="panel-' + settingKey + '-modifyTime"></span></div>'
    + '<div class="footnote" style="width:100%"><span id="panel-' + settingKey + '-modifyUser"></span></div>'
    + '</div>';  // close outline

    return htmlBody;
};

PWM_CFGEDIT.initSettingDisplay = function(setting, options) {
    var settingKey = setting['key'];
    options = options === undefined ? {} : options;

    PWM_MAIN.addEventHandler('helpButton-' + settingKey, 'click', function () {
        PWM_CFGEDIT.displaySettingHelp(settingKey);
    });
    PWM_MAIN.addEventHandler('setting-' + settingKey, 'click', function () {
        PWM_CFGEDIT.displaySettingHelp(settingKey);
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
            LocalizedStringValueHandler.init(settingKey);
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

        case 'VERIFICATION_METHOD':
            VerificationMethodHandler.init(settingKey);
            break;

        case 'NONE':
            break;

        default:
            alert('unknown setting syntax type: ' + setting['syntax']);

    }
};

PWM_CFGEDIT.drawNavigationMenu = function() {
    PWM_MAIN.getObject('navigationTree').innerHTML = '';
    PWM_MAIN.setStyle('navigationTreeWrapper','display','none');

    var detectFirstDisplay = function() {
        var ca = document.cookie.split(';');
        for(var i=0; i<ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0)==' ') c = c.substring(1);
            if (c.indexOf('navigationTreeSaveStateCookie') != -1) {
                return false;
            }
        }
        return true;
    };

    var makeTreeFunction = function(menuTreeData) {
        require(["dojo/_base/window", "dojo/store/Memory", "dijit/tree/ObjectStoreModel", "dijit/Tree","dijit","dojo/domReady!"],
            function(win, Memory, ObjectStoreModel, Tree)
            {
                PWM_MAIN.clearDijitWidget('navigationTree');
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

                var virginNavTree = detectFirstDisplay();

                // Create the Tree.
                var tree = new Tree({
                    model: model,
                    persist: true,
                    getIconClass: function(/*dojo.store.Item*/ item, /*Boolean*/ opened){
                        return 'tree-noicon';
                    },
                    showRoot: false,
                    openOnClick: true,
                    id: 'navigationTree',
                    onClick: function(item){
                        var storedPreferences = PWM_MAIN.readLocalStorage();
                        storedPreferences['lastSelected'] = item;
                        PWM_MAIN.writeLocalStorage(storedPreferences);
                        PWM_CFGEDIT.dispatchNavigationItem(item);
                    }
                });

                if (virginNavTree) {
                    console.log('first time nav menu loaded');
                    tree.expandAll();
                    setTimeout(function(){
                        tree.collapseAll();
                    },1000);
                } else {
                    console.log('detected previous nav menu cookie');
                }

                PWM_MAIN.getObject('navigationTree').innerHTML = '';
                tree.placeAt(PWM_MAIN.getObject('navigationTree'));
                tree.startup();
                PWM_VAR['navigationTree'] = tree; // used for expand/collapse button events;
                PWM_MAIN.setStyle('navigationTreeWrapper','display','inherit');
            }
        );
    };

    var url = 'ConfigEditor?processAction=menuTreeData';
    url = PWM_MAIN.addParamToUrl(url,'modifiedSettingsOnly',PWM_CFGEDIT.readNavigationFilters()['modifiedSettingsOnly']);
    url = PWM_MAIN.addParamToUrl(url,'level',PWM_CFGEDIT.readNavigationFilters()['level']);

    PWM_MAIN.ajaxRequest(url,function(data){
        var menuTreeData = data['data'];
        makeTreeFunction(menuTreeData);
    },{method:'GET'});
};

PWM_CFGEDIT.readNavigationFilters = function() {
    var result = {};
    result['modifiedSettingsOnly'] = 'settingFilter_modifiedSettingsOnly' in PWM_VAR ? PWM_VAR['settingFilter_modifiedSettingsOnly'] : false;
    result['level'] = 'settingFilter_level' in PWM_VAR ? PWM_VAR['settingFilter_level'] : 2;
    return result;
};

PWM_CFGEDIT.dispatchNavigationItem = function(item) {
    var currentID = item['id'];
    var type = item['type'];
    if (currentID == 'HOME') {
        PWM_CFGEDIT.drawHomePage();
    } else if (type == 'navigation') {
        /* not used, nav tree set to auto-expand */
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

    if (item['name']) {
        PWM_MAIN.getObject('currentPageDisplay').innerHTML = ' - ' + item['name'];
    }
};

PWM_CFGEDIT.drawDisplayTextPage = function(settingKey, keys) {
    var settingsPanel = PWM_MAIN.getObject('settingsPanel');
    var remainingLoads = keys.length;
    settingsPanel.innerHTML = '<div id="displaytext-loading-panel" style="width:100%; text-align: center">'
    + PWM_MAIN.showString('Display_PleaseWait') + '&nbsp;<span id="remainingCount"></div>';
    console.log('drawing displaytext-editor for setting-' + settingKey);
    var htmlBody = '<div id="localetext-editor-wrapper" style="display:none">';
    for (var key in keys) {
        var displayKey = 'localeBundle-' + settingKey + '-' + keys[key];
        var settingInfo = {};
        settingInfo['key'] = displayKey;
        settingInfo['label'] = keys[key];
        htmlBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo,{showHelp:false});
    }
    settingsPanel.innerHTML = settingsPanel.innerHTML + htmlBody;
    
    var initSetting = function(keyCounter) {
        if (PWM_VAR['outstandingOperations'] > 5) {
            setTimeout(function () { initSetting(keyCounter); }, 50);
            return;
        }
        var displayKey = 'localeBundle-' + settingKey + '-' + keys[keyCounter];
        var settingInfo = {};
        settingInfo['key'] = displayKey;
        settingInfo['label'] = keys[keyCounter];
        settingInfo['syntax'] = 'NONE';
        PWM_CFGEDIT.initSettingDisplay(settingInfo);
        LocalizedStringValueHandler.init(displayKey,{required:true});
        remainingLoads--;
        PWM_MAIN.getObject('remainingCount').innerHTML = remainingLoads > 0 ? remainingLoads : '';
    };
    
    var delay = 5;
    for (var key in keys) {
        (function(keyCounter) {
            setTimeout(function(){
                initSetting(keyCounter);
            },delay);
            delay = delay + 5;
        })(key);
    }
    var checkForFinishFunction = function() {
        console.log('checking for finish function...');
        setTimeout(function(){
            if (PWM_VAR['outstandingOperations'] == 0) {
                PWM_MAIN.getObject('displaytext-loading-panel').style.display = 'none';
                PWM_MAIN.getObject('localetext-editor-wrapper').style.display = 'inherit';
            } else {
                setTimeout(checkForFinishFunction,100);
            }
        },100);
    };
    checkForFinishFunction();
};

PWM_CFGEDIT.drawHomePage = function() {
    var htmlBody = '';

    var settingsPanel = PWM_MAIN.getObject('settingsPanel');
    settingsPanel.innerHTML = PWM_MAIN.showString('Display_PleaseWait');

    var templateSettingBody = '';
    templateSettingBody += '<div><select id="select-template">';
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
    templateSelectSetting['description'] = PWM_CONFIG.showString('Display_AboutTemplates');
    htmlBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(templateSelectSetting);

    var notesSettings = {};
    notesSettings['key'] = 'configurationNotes';
    notesSettings['label'] = 'Configuration Notes';
    htmlBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(notesSettings);

    settingsPanel.innerHTML = htmlBody;

    PWM_MAIN.getObject('table_setting_templateSelect').innerHTML = templateSettingBody;
    PWM_MAIN.getObject('table_setting_configurationNotes').innerHTML = notesSettingBody;


    PWM_MAIN.addEventHandler('select-template','change',function(){
        PWM_CFGEDIT.selectTemplate(PWM_MAIN.getObject('select-template').options[PWM_MAIN.getObject('select-template').selectedIndex].value)
    });
    PWM_MAIN.addEventHandler('configurationNotesTextarea','input',function(){
        var value = PWM_MAIN.getObject('configurationNotesTextarea').value;
        PWM_VAR['configurationNotes'] = value;
        var url = "ConfigEditor?processAction=setOption&updateNotesText=true";
        PWM_MAIN.ajaxRequest(url,function(){console.log('saved config notes')},{content:value});
    });

    PWM_MAIN.setStyle('outline_' + templateSelectSetting['key'],'display','inherit');
    PWM_MAIN.setStyle('outline_' + notesSettings['key'],'display','inherit');

};


PWM_CFGEDIT.initConfigSettingsDefinition=function(nextFunction) {
    var clientConfigUrl = PWM_GLOBAL['url-context'] + "/private/config/ConfigEditor?processAction=settingData&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
    var loadFunction = function(data) {
        if (data['error'] == true) {
            console.error('unable to load ' + clientConfigUrl + ', error: ' + data['errorDetail'])
        } else {
            for (var settingKey in data['data']) {
                PWM_SETTINGS[settingKey] = data['data'][settingKey];
            }
        }
        console.log('loaded client-configsettings data');
        if (nextFunction) nextFunction();
    };
    var errorFunction = function(error) {
        var errorMsg = 'unable to read config settings app-data: ' + error;
        console.log(errorMsg);
        if (!PWM_VAR['initError']) PWM_VAR['initError'] = errorMsg;
        if (nextFunction) nextFunction();
    };
    PWM_MAIN.ajaxRequest(clientConfigUrl, loadFunction, {method:'GET',errorFunction:errorFunction});
};

PWM_CFGEDIT.displaySettingHelp = function(settingKey) {
    console.log('toggle help for ' + settingKey);
    var prefs = PWM_MAIN.readLocalStorage();
    prefs['helpExpanded'] = 'helpExpanded' in prefs ? prefs['helpExpanded'] : {};
    var element = PWM_MAIN.getObject('pane-help-' + settingKey);
    if (element) {
        if (element.style.display == 'none') {
            element.style.display = 'inherit';
            prefs['helpExpanded'][settingKey] = true;
        } else {
            element.style.display = 'none';
            delete prefs['helpExpanded'][settingKey];
        }
        PWM_MAIN.writeLocalStorage(prefs);
    }
};

PWM_CFGEDIT.showSettingFilter = function() {
    var currentValues = PWM_CFGEDIT.readNavigationFilters();

    var dialogBody = '<div><table class="" style="table-layout: fixed">';
    dialogBody += '<tr><td>Setting Level</td><td><label>';
    dialogBody += '<input type="range" min="0" max="2" name="input-settingLevel" id="input-settingLevel" value="' + currentValues['level'] + '" style="width:100px"/>';
    dialogBody += '<span id="panel-settingLevelDescription"></span></label></td></tr>';
    dialogBody += '<tr><td>Modified</td><td>';
    dialogBody += '<input type="radio" name="input-modifiedSettingsOnly" id="input-modifiedSettingsOnly-all" ' + (!currentValues['modifiedSettingsOnly'] ? 'checked' : '') + '>All';
    dialogBody += '<input type="radio" name="input-modifiedSettingsOnly" id="input-modifiedSettingsOnly-modified" ' + (currentValues['modifiedSettingsOnly'] ? 'checked' : '') + '>Modified';
    dialogBody += '</td></tr>';
    dialogBody += '</table></div>';
    var updateSettingLevelDescription = function() {
        var value = parseInt(PWM_MAIN.getObject('input-settingLevel').value);
        var descriptionText = PWM_CONFIG.showString('Display_SettingFilter_Level_' + value);
        PWM_MAIN.getObject('panel-settingLevelDescription').innerHTML = descriptionText;
    };
    var updateIcon = function() {
        var isDefault = PWM_VAR['settingFilter_modifiedSettingsOnly'] == false && PWM_VAR['settingFilter_level'] == 2;
        if (isDefault) {
            PWM_MAIN.removeCssClass('settingFilter_icon', "modified");
        } else {
            PWM_MAIN.addCssClass('settingFilter_icon', "modified");
        }
    };

    var updateVars = function() {
        PWM_VAR['settingFilter_modifiedSettingsOnly'] = PWM_MAIN.getObject('input-modifiedSettingsOnly-modified').checked;
        PWM_VAR['settingFilter_level'] = parseInt(PWM_MAIN.getObject('input-settingLevel').value);
        updateSettingLevelDescription();

    };
    PWM_MAIN.showDialog({title:'Setting Filters',text:dialogBody,loadFunction:function(){
        PWM_MAIN.addEventHandler('input-modifiedSettingsOnly-all','change',function(){
            updateVars();
        });
        PWM_MAIN.addEventHandler('input-modifiedSettingsOnly-modified','change',function(){
            updateVars();
        });
        PWM_MAIN.addEventHandler('input-settingLevel','change',function(){
            updateVars();
        });
        updateSettingLevelDescription();
    },okAction:function(){
        updateIcon();
        PWM_CFGEDIT.loadMainPageBody();
    }});
};


PWM_CFGEDIT.readCurrentProfile = function() {
    return PWM_VAR['currentProfile'];
};

PWM_CFGEDIT.setCurrentProfile = function(profile) {
    if (profile) {
        PWM_VAR['currentProfile'] = profile;
    } else {
        delete PWM_VAR['currentProfile'];
    }
};