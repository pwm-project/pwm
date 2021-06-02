/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

"use strict";

var PWM_CFGEDIT = PWM_CFGEDIT || {};
var PWM_CONFIG = PWM_CONFIG || {};
var PWM_MAIN = PWM_MAIN || {};
var PWM_VAR = PWM_VAR || {};
var PWM_SETTINGS = PWM_SETTINGS || {};

PWM_VAR['outstandingOperations'] = 0;
PWM_VAR['skippedSettingCount'] = 0;

PWM_CFGEDIT.syntaxFunctionMap = {
    FORM: function (settingKey) {
        FormTableHandler.init(settingKey, {});
    },
    OPTIONLIST: function (settingKey) {
        OptionListHandler.init(settingKey);
    },
    CUSTOMLINKS: function (settingKey) {
        CustomLinkHandler.init(settingKey, {});
    },
    EMAIL: function (settingKey) {
        EmailTableHandler.init(settingKey);
    },
    ACTION: function (settingKey) {
        ActionHandler.init(settingKey);
    },
    PASSWORD: function (settingKey) {
        ChangePasswordHandler.init(settingKey);
    },
    NAMED_SECRET: function (settingKey) {
        NamedSecretHandler.init(settingKey);
    },
    NUMERIC: function (settingKey) {
        NumericValueHandler.init(settingKey);
    },
    DURATION: function (settingKey) {
        DurationValueHandler.init(settingKey);
    },
    DURATION_ARRAY: function (settingKey) {
        DurationArrayValueHandler.init(settingKey);
    },
    STRING: function (settingKey) {
        StringValueHandler.init(settingKey);
    },
    TEXT_AREA: function (settingKey) {
        TextAreaValueHandler.init(settingKey)
    },
    SELECT: function (settingKey) {
        SelectValueHandler.init(settingKey);
    },
    BOOLEAN: function (settingKey) {
        BooleanHandler.init(settingKey);
    },
    LOCALIZED_STRING_ARRAY: function (settingKey) {
        MultiLocaleTableHandler.initMultiLocaleTable(settingKey);
    },
    STRING_ARRAY: function (settingKey) {
        StringArrayValueHandler.init(settingKey);
    },
    PROFILE: function (settingKey) {
        StringArrayValueHandler.init(settingKey);
    },
    LOCALIZED_STRING: function (settingKey) {
        LocalizedStringValueHandler.init(settingKey);
    },
    LOCALIZED_TEXT_AREA: function (settingKey) {
        LocalizedStringValueHandler.init(settingKey);
    },
    CHALLENGE: function (settingKey) {
        ChallengeSettingHandler.init(settingKey);
    },
    X509CERT: function (settingKey) {
        X509CertificateHandler.init(settingKey);
    },
    PRIVATE_KEY: function (settingKey) {
        PrivateKeyHandler.init(settingKey);
    },
    FILE: function (settingKey) {
        FileValueHandler.init(settingKey);
    },
    VERIFICATION_METHOD: function (settingKey) {
        VerificationMethodHandler.init(settingKey);
    },
    REMOTE_WEB_SERVICE: function (settingKey) {
        RemoteWebServiceHandler.init(settingKey);
    },
    USER_PERMISSION: function (settingKey) {
        UserPermissionHandler.init(settingKey);
    }
};


PWM_CFGEDIT.readSetting = function(keyName, valueWriter) {
    var modifiedOnly = PWM_CFGEDIT.readNavigationFilters()['modifiedSettingsOnly'];
    var maxLevel = parseInt(PWM_CFGEDIT.readNavigationFilters()['level']);
    PWM_VAR['outstandingOperations']++;
    PWM_CFGEDIT.handleWorkingIcon();
    var url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','readSetting');
    url = PWM_MAIN.addParamToUrl(url, 'key', keyName);
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
        var showSetting = (PWM_SETTINGS['settings'][keyName] && PWM_SETTINGS['settings'][keyName]['syntax'] === 'PROFILE') ||   (!modifiedOnly || !isDefault) && (maxLevel < 0 || settingLevel  <= maxLevel );
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
            if (data['data']['modifyUser']['ldapProfile'] && data['data']['modifyUser']['ldapProfile'] !== "default") {
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
    var url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','writeSetting');
    url = PWM_MAIN.addParamToUrl(url, 'key', keyName);
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
    var url = "editor?processAction=resetSetting&key=" + keyName;
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
    var resetImageButton = PWM_MAIN.getObject('resetButton-' + keyName);
    var modifiedIcon = PWM_MAIN.getObject('modifiedNoticeIcon-' + keyName);
    var settingSyntax = '';
    try {
        settingSyntax = PWM_SETTINGS['settings'][keyName]['syntax'];
    } catch (e) { /* noop */ }  //setting keys may not be loaded

    if (PWM_SETTINGS['settings'][keyName]) {
        if (PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][keyName]['flags'],'NoDefault')) {
            isDefault = true;
        }
    }

    if (!isDefault) {
        if (resetImageButton) {
            resetImageButton.style.visibility = 'visible';
        }
        if (modifiedIcon) {
            modifiedIcon.style.display = 'inline';
        }
        try {
            document.getElementById('title_' + keyName).classList.add('modified');
            document.getElementById('titlePane_' + keyName).classList.add('modified');
        } catch (e) { /* noop */ }
    } else {
        if (resetImageButton) {
            resetImageButton.style.visibility = 'hidden';
        }
        if (modifiedIcon) {
            modifiedIcon.style.display = 'none';
        }
        try {
            document.getElementById('title_' + keyName).classList.remove('modified');
            document.getElementById('titlePane_' + keyName).classList.remove('modified');
        } catch (e) { /* noop */ }
    }
};

PWM_CFGEDIT.getSettingValueElement = function(settingKey) {
    var parentDiv = 'table_setting_' + settingKey;
    return PWM_MAIN.getObject(parentDiv);
};

PWM_CFGEDIT.clearDivElements = function(parentDiv) {
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    if (parentDivElement !== null) {
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
            var url = "editor?processAction=finishEditing";
            var loadFunction = function(data) {
                if (data['error'] === true) {
                    PWM_MAIN.showErrorDialog(data);
                } else {
                    console.log('save completed');
                    PWM_MAIN.showWaitDialog({title:'Save complete, applying configuration...',loadFunction:function(){
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
        var url = "editor?processAction=setConfigurationPassword";
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
    PWM_CFGEDIT.applyStoredSettingFilterPrefs();

    PWM_MAIN.addEventHandler('homeSettingSearch',['input','focus'],function(){PWM_CFGEDIT.processSettingSearch(PWM_MAIN.getObject('searchResults'));});
    PWM_MAIN.addEventHandler('button-navigationExpandAll','click',function(){PWM_VAR['navigationTree'].expandAll()});
    PWM_MAIN.addEventHandler('button-navigationCollapseAll','click',function(){PWM_VAR['navigationTree'].collapseAll()});

    PWM_MAIN.addEventHandler('cancelButton_icon','click',function(){PWM_CFGEDIT.cancelEditing()});
    PWM_MAIN.addEventHandler('saveButton_icon','click',function(){PWM_CFGEDIT.saveConfiguration()});
    PWM_MAIN.addEventHandler('setPassword_icon','click',function(){PWM_CFGEDIT.setConfigurationPassword()});
    PWM_MAIN.addEventHandler('referenceDoc_icon','click',function(){
        PWM_MAIN.newWindowOpen(PWM_GLOBAL['url-context'] + '/public/reference/','referencedoc');
    });
    PWM_MAIN.addEventHandler('macroDoc_icon','click',function(){ PWM_CFGEDIT.showMacroHelp(); });

    PWM_MAIN.addEventHandler('button-closeMenu','click',function(){
        PWM_CFGEDIT.closeMenuPanel();
    });
    PWM_MAIN.addEventHandler('button-openMenu','click',function(){
        PWM_CFGEDIT.openMenuPanel();
    });
    PWM_MAIN.addEventHandler('radio-setting-level','change',function(){
        PWM_CFGEDIT.handleSettingsFilterLevelRadioClick();
    });
    PWM_MAIN.addEventHandler('radio-modified-only','change',function(){
        PWM_CFGEDIT.handleModifiedSettingsRadioClick();
    });


    PWM_CONFIG.heartbeatCheck();

    PWM_CFGEDIT.loadMainPageBody();

    console.log('completed initConfigEditor');
    if (nextFunction) {
        nextFunction();
    }
};

PWM_CFGEDIT.executeSettingFunction = function (setting, name, resultHandler, extraData) {
    var jsonSendData = {};
    jsonSendData['setting'] = setting;
    jsonSendData['function'] = name;
    jsonSendData['extraData'] = extraData;

    resultHandler = resultHandler !== undefined ? resultHandler : function(data) {
        var msgBody = '<div style="max-height: 400px; overflow-y: auto">' + data['successMessage'] + '</div>';
        PWM_MAIN.showDialog({width:700,title: 'Results', text: msgBody, okAction: function () {
                PWM_CFGEDIT.loadMainPageBody();
            }});
    };

    var requestUrl = "editor?processAction=executeSettingFunction";
    if (PWM_CFGEDIT.readCurrentProfile()) {
        requestUrl = PWM_MAIN.addParamToUrl(requestUrl,'profile',PWM_CFGEDIT.readCurrentProfile());
    }

    PWM_MAIN.showWaitDialog({loadFunction:function() {
            var loadFunction = function(data) {
                if (data['error']) {
                    PWM_MAIN.showErrorDialog(data);
                } else {
                    resultHandler(data);
                }
            };
            PWM_MAIN.ajaxRequest(requestUrl, loadFunction, {content:jsonSendData});
        }});
};

PWM_CFGEDIT.showChangeLog=function(confirmText, confirmFunction) {
    var url = "editor?processAction=readChangeLog";
    var loadFunction = function(data) {
        PWM_MAIN.closeWaitDialog();
        if (data['error']) {
            PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
        } else {
            var showChangeLogDialog = function() {
                var bodyText = '<div class="changeLogViewBox">';
                bodyText += data['data']['html'];
                bodyText += '</div>';

                if (data['data']['health']) {
                    bodyText += '<br/><div>Configuration Concerns:</div>';
                    bodyText += '<div><ul>';
                    for (var i in data['data']['health']['records']) {
                        var detail = data['data']['health']['records'][i]['detail'];
                        bodyText += '<li>' + detail + '</li>';
                    }
                    bodyText += '</ul></div>';
                }

                if (confirmText !== undefined) {
                    bodyText += '<br/><div>' + confirmText + '</div>';
                }
                if (confirmFunction === undefined) {
                    PWM_MAIN.showDialog({title: "Unsaved Configuration Editor Changes", text: bodyText, dialogClass:'wide', showClose: true});
                } else {
                    PWM_MAIN.showConfirmDialog({title: "Unsaved Configuration Editor Changes", text: bodyText, dialogClass:'wide', showClose: true, okAction:confirmFunction});
                }
            };

            showChangeLogDialog()

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
        PWM_MAIN.addCssClass('indicator-noResults',"hidden");
        PWM_MAIN.addCssClass('indicator-searching',"hidden");
        PWM_MAIN.addCssClass(destinationDiv.id,"hidden");
        destinationDiv.innerHTML = '';
    };

    var readSearchTerm = function() {
        if (!PWM_MAIN.getObject('homeSettingSearch') || !PWM_MAIN.getObject('homeSettingSearch') || PWM_MAIN.getObject('homeSettingSearch').value.length < 1) {
            return null;
        }
        return PWM_MAIN.getObject('homeSettingSearch').value;
    };

    console.log('beginning search #' + iteration);
    var url = "editor?processAction=search";

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
            if (PWM_MAIN.JSLibrary.isEmpty(data['data'])) {
                PWM_MAIN.removeCssClass('indicator-noResults','hidden')
                console.log('search #' + iteration + ', 0 results, ' + elapsedTime + 'ms');
            } else {
                PWM_MAIN.addCssClass('indicator-noResults','hidden')
                PWM_MAIN.JSLibrary.forEachInObject(data['data'], function (categoryIter, category) {
                    bodyText += '<div class="panel-searchResultCategory">' + categoryIter + '</div>';
                    PWM_MAIN.JSLibrary.forEachInObject(category, function (settingIter, setting) {
                        var profileID = setting['profile'];
                        var linkID = 'link-' + setting['category'] + '-' + settingIter + (profileID ? profileID : '');
                        var settingID = "search_" + (profileID ? profileID + '_' : '') + settingIter;
                        bodyText += '<div><span id="' + linkID + '" class="panel-searchResultItem">';
                        bodyText += PWM_SETTINGS['settings'][settingIter]['label'];
                        bodyText += '</span>&nbsp;<span id="' + settingID + '_popup" class="btn-icon pwm-icon pwm-icon-info-circle"></span>';
                        if (!setting['default']) {
                            bodyText += '<span class="pwm-icon pwm-icon-pencil-square modifiedNoticeIcon" title="' + PWM_CONFIG.showString('Tooltip_ModifiedNotice') + '">&nbsp;</span>';
                        }
                        bodyText += '</div>';
                        resultCount++;
                    });
                });
                console.log('search #' + iteration + ', ' + resultCount + ' results, ' + elapsedTime + 'ms');
                PWM_MAIN.removeCssClass(destinationDiv.id, "hidden");
                destinationDiv.innerHTML = bodyText;
                PWM_MAIN.JSLibrary.forEachInObject(data['data'], function (categoryIter, category) {
                    PWM_MAIN.JSLibrary.forEachInObject(category, function (settingKey, setting) {
                        var profileID = setting['profile'];
                        var settingID = "search_" + (profileID ? profileID + '_' : '') + settingKey;
                        var value = setting['value'];
                        var toolBody = '<span style="font-weight: bold">Setting</span>';
                        toolBody += '<br/>' + PWM_SETTINGS['settings'][settingKey]['label'] + '<br/><br/>';
                        toolBody += '<span style="font-weight: bold">Description</span>';
                        toolBody += '<br/>' + PWM_SETTINGS['settings'][settingKey]['description'] + '<br/><br/>';
                        toolBody += '<span style="font-weight: bold">Value</span>';
                        toolBody += '<br/>' + value.replace('\n', '<br/>') + '<br/>';
                        PWM_MAIN.showDijitTooltip({
                            id: settingID + '_popup',
                            text: toolBody,
                            width: 500
                        });
                        var linkID = 'link-' + setting['category'] + '-' + settingKey + (profileID ? profileID : '');
                        PWM_MAIN.addEventHandler(linkID, 'click', function () {
                            resetDisplay();
                            PWM_MAIN.Preferences.writeSessionStorage('configEditor-lastSelected', {
                                type: 'category',
                                category: setting['category'],
                                setting: settingKey,
                                profile: profileID
                            });
                            PWM_CFGEDIT.gotoSetting(setting['category'], settingKey, profileID);
                        });
                    });
                });
            }
        }
    };
    var validationProps = {};
    validationProps['serviceURL'] = url;
    validationProps['readDataFunction'] = function(){
        resetDisplay();
        PWM_MAIN.removeCssClass('indicator-searching','hidden');

        var value = readSearchTerm();
        return {search:value,key:value};
    };
    validationProps['completeFunction'] = function() {
        PWM_MAIN.addCssClass('indicator-searching','hidden');
    };
    validationProps['processResultsFunction'] = loadFunction;
    PWM_MAIN.pwmFormValidator(validationProps);
};


PWM_CFGEDIT.gotoSetting = function(category,settingKey,profile) {
    console.log('going to setting... category=' + category + " settingKey=" + settingKey + " profile=" + profile);

    if (!category) {
        if (settingKey) {
            var settingInfo = PWM_SETTINGS['settings'][settingKey];
            if (settingInfo) {
                category = settingInfo['category'];
            }
        }
    }

    if (!settingKey && !category) {
        console.log('unable to gotoUrl setting: settingKey and category parameter are not specified');
        return;
    }

    if (settingKey && !(settingKey in PWM_SETTINGS['settings'])) {
        console.log('unable to gotoUrl setting: settingKey parameter "' + settingKey + '" is not valid');
        return;
    }

    if (!(category in PWM_SETTINGS['categories'])) {
        console.log('unable to gotoUrl setting: category parameter "' + category + '" is not valid');
        return;
    }

    PWM_CFGEDIT.setCurrentProfile(profile);
    PWM_CFGEDIT.displaySettingsCategory(category);

    if (PWM_SETTINGS['categories'][category]['menuLocation']) {
        var text = PWM_SETTINGS['categories'][category]['menuLocation'];
        if (PWM_SETTINGS['categories'][category]['profiles']) {
            text = text.replace('PROFILE',profile);
        }
        PWM_CFGEDIT.setBreadcrumbText(text);
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

PWM_CFGEDIT.setBreadcrumbText = function(text) {
    PWM_MAIN.getObject('currentPageDisplay').innerHTML = text;

};



PWM_CFGEDIT.cancelEditing = function() {
    var url =  "editor?processAction=readChangeLog";
    PWM_MAIN.showWaitDialog({loadFunction:function(){
            var loadFunction = function(data) {
                if (data['error']) {
                    PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
                } else {
                    if (data['data']['modified'] === true) {
                        var bodyText = '<div class="changeLogViewBox">';
                        bodyText += data['data']['html'];
                        bodyText += '</div><br/><div>';
                        bodyText += PWM_CONFIG.showString('MenuDisplay_CancelConfig');
                        bodyText += '</div>';
                        PWM_MAIN.closeWaitDialog();
                        PWM_MAIN.showConfirmDialog({dialogClass:'wide',showClose:true,allowMove:true,text:bodyText,okAction:
                                function () {
                                    PWM_MAIN.showWaitDialog({loadFunction: function () {
                                            PWM_MAIN.ajaxRequest('editor?processAction=cancelEditing',function(){
                                                PWM_MAIN.gotoUrl('manager', {addFormID: true});
                                            });
                                        }});
                                }
                        });
                    } else {
                        PWM_MAIN.gotoUrl('manager', {addFormID: true});
                    }
                }
            };
            PWM_MAIN.ajaxRequest(url, loadFunction);
        }});
};

PWM_CFGEDIT.showMacroHelp = function() {
    var processExampleFunction = function() {
        PWM_MAIN.getObject('panel-testMacroOutput').innerHTML = PWM_MAIN.showString('Display_PleaseWait');
        var sendData = {};
        sendData['input'] = PWM_MAIN.getObject('input-testMacroInput').value;
        var url = "editor?processAction=testMacro";
        var loadFunction = function(data) {
            PWM_MAIN.getObject('panel-testMacroOutput').innerHTML = data['data'];
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{content:sendData});
    };

    var loadFunction = function() {
        if (PWM_MAIN.getObject('input-testMacroInput')) {
            console.log('connected to macroHelpDiv');
            setTimeout(function(){
                PWM_MAIN.addEventHandler('input-testMacroInput','input',processExampleFunction);
                processExampleFunction();
                PWM_MAIN.getObject('input-testMacroInput').focus();
                PWM_MAIN.getObject('input-testMacroInput').setSelectionRange(-1, -1);
            },500);
        } else {
            if (attempts < 50) {
                attempts++;
                setTimeout(loadFunction,100);
            }
        }
    };

    var options = {};
    options['title'] = 'Macro Help'
    options['id'] = 'id-dialog-macroHelp'
    options['dialogClass'] = 'wide';
    options['dojoStyle'] = 'width: 750px';
    options['showClose'] = true;
    options['href'] = PWM_GLOBAL['url-resources'] + "/text/macroHelp.html"
    options['loadFunction'] = loadFunction;
    PWM_MAIN.showDialog( options );
};

PWM_CFGEDIT.showTimezoneList = function() {
    var options = {};
    options['title'] = 'Timezones'
    options['id'] = 'id-dialog-timeZoneHelp'
    options['dialogClass'] = 'wide';
    options['dojoStyle'] = 'width: 750px';
    options['showClose'] = true;
    options['href'] = PWM_GLOBAL['url-context'] + "/public/reference/timezones.jsp"
    PWM_MAIN.showDialog( options );
};

PWM_CFGEDIT.showDateTimeFormatHelp = function() {
    var options = {};
    options['title'] = 'Date & Time Formatting'
    options['id'] = 'id-dialog-dateTimePopup'
    options['dialogClass'] = 'wide';
    options['dojoStyle'] = 'width: 750px';
    options['showClose'] = true;
    options['href'] = PWM_GLOBAL['url-resources'] + "/text/datetimeFormatHelp.html"
    PWM_MAIN.showDialog( options );
};

PWM_CFGEDIT.ldapHealthCheck = function() {
    PWM_MAIN.showWaitDialog({loadFunction:function() {
            var url = "editor?processAction=ldapHealthCheck";
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
            var url =  "editor?processAction=databaseHealthCheck";
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

PWM_CFGEDIT.httpsCertificateView = function() {
    PWM_MAIN.showWaitDialog({title:'Parsing...',loadFunction:function(){
            var url =  "editor?processAction=httpsCertificateView";
            var loadFunction = function(data) {
                PWM_MAIN.closeWaitDialog();
                if (data['error']) {
                    PWM_MAIN.showErrorDialog(data);
                } else {
                    var bodyText = '<pre>' + data['data'] + '</pre>';
                    var titleText = 'HTTPS Certificate';
                    PWM_MAIN.showDialog({text:bodyText,title:titleText});
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction);
        }});
};

PWM_CFGEDIT.smsHealthCheck = function() {
    var dialogBody = '<p>' + PWM_CONFIG.showString('Warning_SmsTestData') + '</p><form id="smsCheckParametersForm"><table>';
    dialogBody += '<tr><td>To</td><td><input name="to" type="text" value="555-1212"/></td></tr>';
    dialogBody += '<tr><td>Message</td><td><input name="message" type="text" value="Test Message"/></td></tr>';
    dialogBody += '</table></form>';
    PWM_MAIN.showDialog({text:dialogBody,showCancel:true,title:'Test SMS connection',closeOnOk:false,okAction:function(){
            var formElement = PWM_MAIN.getObject("smsCheckParametersForm");
            var formData = PWM_MAIN.JSLibrary.formToValueMap(formElement);
            var url =  "editor?processAction=smsHealthCheck";
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
};

PWM_CFGEDIT.emailHealthCheck = function() {
    var dialogBody = '<p>' + PWM_CONFIG.showString('Warning_EmailTestData') + '</p><form id="emailCheckParametersForm"><table>';
    dialogBody += '<tr><td>To</td><td><input name="to" type="text" value="test@example.com"/></td></tr>';
    dialogBody += '<tr><td>From</td><td><input name="from" type="text" value="@DefaultEmailFromAddress@"/></td></tr>';
    dialogBody += '<tr><td>Subject</td><td><input name="subject" type="text" value="Test Email"/></td></tr>';
    dialogBody += '<tr><td>Body</td><td><input name="body" type="text" value="Test Email""/></td></tr>';
    dialogBody += '</table></form>';
    PWM_MAIN.showDialog({text:dialogBody,showCancel:true,title:'Test Email Connection',closeOnOk:false,okAction:function(){
            var formElement = PWM_MAIN.getObject("emailCheckParametersForm");
            var formData = PWM_MAIN.JSLibrary.formToValueMap(formElement);
            var url = "editor?processAction=emailHealthCheck";
            url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                    var loadFunction = function(data) {
                        if (data['error']) {
                            PWM_MAIN.showErrorDialog(data);
                        } else {
                            var bodyText = PWM_ADMIN.makeHealthHtml(data['data'],false,false);
                            var titleText = 'Email Send Message Status';
                            PWM_MAIN.showDialog({text:bodyText,title:titleText,showCancel:true});
                        }
                    };
                    PWM_MAIN.ajaxRequest(url,loadFunction,{content:formData});
                }});
        }});
};

PWM_CFGEDIT.selectTemplate = function(newTemplate) {
    PWM_MAIN.showConfirmDialog({
        text: PWM_CONFIG.showString('Warning_ChangeTemplate'),
        okAction: function () {
            PWM_MAIN.showWaitDialog({loadFunction: function () {
                    var url = "editor?processAction=setOption&template=" + newTemplate;
                    PWM_MAIN.ajaxRequest(url, function(){ PWM_MAIN.gotoUrl('editor'); });
                }});
        }
    });
};

PWM_CFGEDIT.loadMainPageBody = function() {

    var drawSettingsFunction = function () {
        var lastSelected = PWM_MAIN.Preferences.readSessionStorage('configEditor-lastSelected', null);
        if (lastSelected) {
            PWM_CFGEDIT.dispatchNavigationItem(lastSelected);
        } else {
            PWM_CFGEDIT.dispatchNavigationItem({id: 'TEMPLATES', type: 'category', category: 'TEMPLATES'});
        }

        require(["dojo/io-query"], function (ioQuery) {
            var uri = window.location.href;
            var queryString = uri.substring(uri.indexOf("?") + 1, uri.length);
            var queryParams = ioQuery.queryToObject(queryString);
            if (queryParams['processAction'] === 'gotoSetting') {
                PWM_CFGEDIT.gotoSetting(queryParams['category'], queryParams['settingKey'], queryParams['profile']);
                return;
            }
        });
    }

    PWM_CFGEDIT.drawNavigationMenu( drawSettingsFunction );
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

    if (category === 'LDAP_BASE') {
        htmlSettingBody += '<div style="width: 100%; text-align: center">'
            + '<button class="btn" id="button-test-LDAP_BASE"><span class="btn-icon pwm-icon pwm-icon-bolt"></span>Test LDAP Profile</button>'
            + '</div>';
    } else if (category === 'DATABASE_SETTINGS') {
        htmlSettingBody += '<div style="width: 100%; text-align: center">'
            + '<button class="btn" id="button-test-DATABASE_SETTINGS"><span class="btn-icon pwm-icon pwm-icon-bolt"></span>Test Database Connection</button>'
            + '</div>';
    } else if (category === 'SMS_GATEWAY') {
        htmlSettingBody += '<div style="width: 100%; text-align: center">'
            + '<button class="btn" id="button-test-SMS"><span class="btn-icon pwm-icon pwm-icon-bolt"></span>Test SMS Settings</button>'
            + '</div>';
    } else if (category === 'EMAIL_SERVERS') {
        htmlSettingBody += '<div style="width: 100%; text-align: center">'
            + '<button class="btn" id="button-test-EMAIL"><span class="btn-icon pwm-icon pwm-icon-bolt"></span>Test Email Settings</button>'
            + '</div>';
    }

    PWM_VAR['skippedSettingCount'] = 0;
    PWM_MAIN.JSLibrary.forEachInObject(PWM_SETTINGS['settings'],function(key,settingInfo){
        if (settingInfo['category'] === category && !settingInfo['hidden']) {
            htmlSettingBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo);
        }
    });

    htmlSettingBody += '<div class="footnote" id="panel-skippedSettingInfo">';
    settingsPanel.innerHTML = htmlSettingBody;

    PWM_MAIN.JSLibrary.forEachInObject(PWM_SETTINGS['settings'],function(key,settingInfo) {
        if (settingInfo['category'] === category && !settingInfo['hidden']) {
            PWM_CFGEDIT.initSettingDisplay(settingInfo);
        }
    });

    if (category === 'LDAP_BASE') {
        PWM_MAIN.addEventHandler('button-test-LDAP_BASE', 'click', function(){PWM_CFGEDIT.ldapHealthCheck();});
    } else if (category === 'DATABASE_SETTINGS') {
        PWM_MAIN.addEventHandler('button-test-DATABASE_SETTINGS', 'click', function(){PWM_CFGEDIT.databaseHealthCheck();});
    } else if (category === 'SMS_GATEWAY') {
        PWM_MAIN.addEventHandler('button-test-SMS', 'click', function(){PWM_CFGEDIT.smsHealthCheck();});
    } else if (category === 'EMAIL_SERVERS') {
        PWM_MAIN.addEventHandler('button-test-EMAIL', 'click', function(){PWM_CFGEDIT.emailHealthCheck();});
    } else if (category === 'HTTPS_SERVER') {
        PWM_MAIN.addEventHandler('button-test-HTTPS_SERVER', 'click', function(){PWM_CFGEDIT.httpsCertificateView();});
    }
    PWM_CFGEDIT.applyGotoSettingHandlers();
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
        + '<div class="pwm-icon pwm-icon-pencil-square modifiedNoticeIcon" title="' + PWM_CONFIG.showString('Tooltip_ModifiedNotice') + '" id="modifiedNoticeIcon-' + settingKey + '" style="display: none" ></div>';

    if (settingInfo['description']) {
        htmlBody += '<div class="pwm-icon pwm-icon-question-circle icon_button" title="' + PWM_CONFIG.showString('Tooltip_HelpButton') + '" id="helpButton-' + settingKey + '"></div>';
    }

    htmlBody += '<div style="visibility: hidden" class="pwm-icon pwm-icon-undo icon_button" title="' + PWM_CONFIG.showString('Tooltip_ResetButton') + '" id="resetButton-' + settingKey + '"></div>'
        + '</div>' // close title
        + '<div id="titlePane_' + settingKey + '" class="setting_body">';

    if (settingInfo['description']) {
        var prefs = PWM_MAIN.Preferences.readSessionStorage('helpExpanded',{});
        var expandHelp = settingKey in prefs;
        htmlBody += '<div class="pane-help" id="pane-help-' + settingKey + '" style="display:' + (expandHelp ? 'inherit' : 'none') + '">'
            + settingInfo['description'];
        if (settingInfo['placeholder']) {
            htmlBody += '<p><span style="font-weight:bold">Example: </span><code>' + settingInfo['placeholder'] + '</code></p>';
        }
        htmlBody += '</div>';
    }


    htmlBody += '<div class="pane-settingValue noborder" id="table_setting_' + settingKey + '">'
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

    var syntax = setting['syntax'];
    var syntaxFunction = PWM_CFGEDIT.syntaxFunctionMap[syntax];
    if ( syntaxFunction ) {
        syntaxFunction(settingKey);
    }
};

PWM_CFGEDIT.drawNavigationMenu = function(nextFunction) {
    console.log('drawNavigationMenu')
    PWM_MAIN.getObject('navigationTree').innerHTML = '';
    PWM_MAIN.setStyle('navigationTreeWrapper','display','none');

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
                    query: {id: 'ROOT'},
                    mayHaveChildren: function(object){
                        return object.type === 'navigation';
                    }
                });

                // Create the Tree.
                var tree = new Tree({
                    model: model,
                    persist: false,
                    getIconClass: function(/*dojo.store.Item*/ item, /*Boolean*/ opened){
                        return 'tree-noicon';
                    },
                    showRoot: false,
                    openOnClick: true,
                    id: 'navigationTree',
                    onClick: function(item){
                        PWM_MAIN.Preferences.writeSessionStorage('configEditor-lastSelected',item);
                        var path = tree.get('paths');
                        PWM_MAIN.Preferences.writeSessionStorage('configEditor-path',JSON.stringify(path));
                        PWM_CFGEDIT.dispatchNavigationItem(item);
                    }
                });

                var storedPath = PWM_MAIN.Preferences.readSessionStorage('configEditor-path');
                if (storedPath) {
                    var path = JSON.parse(storedPath);
                    tree.set('paths', path);
                }

                PWM_MAIN.getObject('navigationTree').innerHTML = '';
                tree.placeAt(PWM_MAIN.getObject('navigationTree'));
                tree.startup();
                PWM_MAIN.setStyle('navigationTreeWrapper','display','inherit');
                PWM_VAR['navigationTree'] = tree; // used for expand/collapse button events;
                console.log('completed menu tree drawing');
            }
        );
    };

    var url = 'editor?processAction=menuTreeData';
    var filterParams = PWM_CFGEDIT.readNavigationFilters();

    PWM_MAIN.ajaxRequest(url,function(data){
        var menuTreeData = data['data'];
        makeTreeFunction(menuTreeData);
        if (nextFunction) {
            nextFunction();
        }
    },{content:filterParams,preventCache:true});
};

PWM_CFGEDIT.readNavigationFilters = function() {
    var result = {};
    result['modifiedSettingsOnly'] = 'settingFilter_modifiedSettingsOnly' in PWM_VAR ? PWM_VAR['settingFilter_modifiedSettingsOnly'] : false;
    result['level'] = 'settingFilter_level' in PWM_VAR ? PWM_VAR['settingFilter_level'] : 2;
    result['text'] = 'settingFilter_text' in PWM_VAR ? PWM_VAR['settingFilter_text'] : '';
    return result;
};

PWM_CFGEDIT.dispatchNavigationItem = function(item) {
    var currentID = item['id'];
    var type = item['type'];
    //debugger;
    if  (type === 'navigation') {
        /* not used, nav tree set to auto-expand */
    } else if (type === 'category') {
        var category = item['category'];
        if (item['profile']) {
            PWM_CFGEDIT.gotoSetting(category,null,item['profile']);
        } else {
            PWM_CFGEDIT.gotoSetting(category);
        }
    } else if (type === 'displayText') {
        var keys = item['keys'];
        PWM_CFGEDIT.setBreadcrumbText('Display Text - ' + item['name']);
        PWM_CFGEDIT.drawDisplayTextPage(currentID,keys);
    } else if (type === 'profile') {
        var category = item['category'];
        PWM_CFGEDIT.gotoSetting(category,null,currentID);
    } else if (type === 'profileDefinition') {
        var profileSettingKey = item['profileSetting'];
        PWM_CFGEDIT.drawProfileEditorPage(profileSettingKey);
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
            if (PWM_VAR['outstandingOperations'] === 0) {
                PWM_MAIN.getObject('displaytext-loading-panel').style.display = 'none';
                PWM_MAIN.getObject('localetext-editor-wrapper').style.display = 'inherit';
            } else {
                setTimeout(checkForFinishFunction,100);
            }
        },100);
    };
    checkForFinishFunction();
};


PWM_CFGEDIT.initConfigSettingsDefinition=function(nextFunction) {
    var clientConfigUrl = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','settingData');
    var loadFunction = function(data) {
        if (data['error'] === true) {
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
    var helpExpandedPrefs = PWM_MAIN.Preferences.readSessionStorage('helpExpanded',{});
    var element = PWM_MAIN.getObject('pane-help-' + settingKey);
    if (element) {
        if (element.style.display === 'none') {
            element.style.display = 'inherit';
            helpExpandedPrefs[settingKey] = true;
        } else {
            element.style.display = 'none';
            delete helpExpandedPrefs[settingKey];
        }
        PWM_MAIN.Preferences.writeSessionStorage('helpExpanded',helpExpandedPrefs);
    }
};

PWM_CFGEDIT.applyStoredSettingFilterPrefs = function() {
    var level = PWM_MAIN.Preferences.readSessionStorage('settingFilter_level',2);
    PWM_MAIN.getObject('radio-setting-level-' + level).checked = true;
    PWM_VAR['settingFilter_level'] = level;

    var modified = PWM_MAIN.Preferences.readSessionStorage('settingFilter_modifiedSettingsOnly',false);
    var idSuffix = modified ? 'modified' : 'all';
    PWM_MAIN.getObject('radio-modified-only-' + idSuffix).checked = true;
    PWM_VAR['settingFilter_modifiedSettingsOnly'] = modified;
};

PWM_CFGEDIT.handleSettingsFilterLevelRadioClick = function (){
    var value = parseInt(PWM_MAIN.JSLibrary.readValueOfRadioFormInput('radio-setting-level'));
    PWM_VAR['settingFilter_level'] = value;
    PWM_MAIN.Preferences.writeSessionStorage('settingFilter_level',value);
    PWM_CFGEDIT.loadMainPageBody();
};

PWM_CFGEDIT.handleModifiedSettingsRadioClick = function (){
    var value = PWM_MAIN.JSLibrary.readValueOfRadioFormInput('radio-modified-only') === 'modified';
    PWM_VAR['settingFilter_modifiedSettingsOnly'] = value ;
    PWM_MAIN.Preferences.writeSessionStorage('settingFilter_modifiedSettingsOnly',value);
    PWM_CFGEDIT.loadMainPageBody();
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

PWM_CFGEDIT.applyGotoSettingHandlers = function() {
    PWM_MAIN.doQuery('[data-gotoSettingLink]',function(element){
        PWM_MAIN.addEventHandler(element,'click',function(){
            var linkValue = element.getAttribute('data-gotoSettingLink');
            PWM_CFGEDIT.gotoSetting(null,linkValue,null);
        })
    });
};

PWM_CFGEDIT.closeMenuPanel = function() {
    console.log('action closeHeader');
    PWM_MAIN.addCssClass('header-warning','nodisplay');
    PWM_MAIN.removeCssClass('button-openMenu','nodisplay');
};

PWM_CFGEDIT.openMenuPanel = function() {
    console.log('action openHeader');
    PWM_MAIN.removeCssClass('header-warning','nodisplay');
    PWM_MAIN.addCssClass('button-openMenu','nodisplay');
};


PWM_CFGEDIT.drawInfoPage = function(settingInfo) {
    var categoryInfo = PWM_SETTINGS['categories'][settingInfo['category']];
    var macroSupport = PWM_MAIN.JSLibrary.arrayContains(settingInfo['flags'],'MacroSupport');
    var infoPanelElement = PWM_MAIN.getObject('infoPanel');

    var text = '<div class="setting_outline">';
    text += '<div class="setting-title">' + categoryInfo['label'] + '</div>';
    text += '<div class="pane-help">' + categoryInfo['description'] + '</div>';
    text += '</div><br/>';

    text += '<div class="setting_outline">';
    text += '<div class="setting-title">' + settingInfo['label'] + '</div>';
    text += '<div class="pane-help">' + settingInfo['description'] + '</div>';
    if (macroSupport) {
        text += '<div>This setting has support for using <a>Macros</a><div>';
    }
    text += '</div>';
    infoPanelElement.innerHTML = text;
};


