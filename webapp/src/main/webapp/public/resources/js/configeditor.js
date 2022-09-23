/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
    DOMAIN: function (settingKey) {
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
    const modifiedOnly = PWM_CFGEDIT.readNavigationFilters()['modifiedSettingsOnly'];
    const maxLevel = parseInt(PWM_CFGEDIT.readNavigationFilters()['level']);
    PWM_VAR['outstandingOperations']++;
    PWM_CFGEDIT.handleWorkingIcon();
    let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','readSetting');
    url = PWM_MAIN.addParamToUrl(url, 'key', keyName);
    if (PWM_CFGEDIT.readCurrentProfile()) {
        url = PWM_MAIN.addParamToUrl(url, 'profile', PWM_CFGEDIT.readCurrentProfile());
    }
    const loadFunction = function(data) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        console.log('read data for setting ' + keyName);
        const resultValue = data['data']['value'];
        const isDefault = data['data']['isDefault'];
        const settingLevel = (PWM_SETTINGS['settings'][keyName] && PWM_SETTINGS['settings'][keyName]['level'])
            ?  PWM_SETTINGS['settings'][keyName]['level']
            : 0;

        const showSetting = (PWM_SETTINGS['settings'][keyName] && PWM_SETTINGS['settings'][keyName]['syntax'] === 'PROFILE') ||   (!modifiedOnly || !isDefault) && (maxLevel < 0 || settingLevel  <= maxLevel );
        if (showSetting) {
            valueWriter(resultValue);
            PWM_MAIN.removeCssClass('outline_' + keyName,'nodisplay');
            PWM_CFGEDIT.updateSettingDisplay(keyName, isDefault);
            PWM_CFGEDIT.updateLastModifiedInfo(keyName, data);
        } else {
            PWM_MAIN.addCssClass('outline_' + keyName,'nodisplay');
            PWM_VAR['skippedSettingCount']++;
            if (PWM_VAR['skippedSettingCount'] > 0 && PWM_MAIN.getObject('panel-skippedSettingInfo')) {
                PWM_MAIN.getObject('panel-skippedSettingInfo').innerHTML = "" + PWM_VAR['skippedSettingCount'] + " items are not shown due to filter settings."
            }
        }
    };
    const errorFunction = function(error) {
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
            let output = 'Modified by ' + data['data']['modifyUser']['userDN'];
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
    let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','writeSetting');
    url = PWM_MAIN.addParamToUrl(url, 'key', keyName);
    if (PWM_CFGEDIT.readCurrentProfile()) {
        url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
    }
    const loadFunction = function(data) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        console.log('wrote data for setting ' + keyName);
        const isDefault = data['data']['isDefault'];
        PWM_CFGEDIT.updateSettingDisplay(keyName, isDefault);
        if (data['errorMessage']) {
            PWM_MAIN.showError(data['data']['errorMessage']);
        } else {
            PWM_MAIN.clearError();
        }
        const restartEditor = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][keyName]['flags'],'ReloadEditorOnModify');
        if ( restartEditor )
        {
            PWM_MAIN.gotoUrl(window.location.href);
            return;
        }
        if (nextAction !== undefined) {
            nextAction();
        }
    };
    const errorFunction = function(error) {
        PWM_VAR['outstandingOperations']--;
        PWM_CFGEDIT.handleWorkingIcon();
        PWM_MAIN.showErrorDialog(error);
        console.log("error writing setting " + keyName + ", reason: " + error)
    };
    PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,content:valueData});
};

PWM_CFGEDIT.resetSetting=function(keyName, nextAction) {
    let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'resetSetting');
    url = PWM_MAIN.addParamToUrl(url, 'key', keyName);
    if (PWM_CFGEDIT.readCurrentProfile()) {
        url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
    }
    const loadFunction = function() {
        console.log('reset data for ' + keyName);
        if (nextAction !== undefined) {
            nextAction();
        }
    };
    PWM_MAIN.ajaxRequest(url,loadFunction);
};


PWM_CFGEDIT.handleWorkingIcon = function() {
    const iconElement = PWM_MAIN.getObject('working_icon');
    if (iconElement) {
        if (PWM_VAR['outstandingOperations'] > 0) {
            iconElement.style.visibility = 'visible';
        } else {
            iconElement.style.visibility = 'hidden';
        }
    }
};


PWM_CFGEDIT.updateSettingDisplay = function(keyName, isDefault) {
    const resetImageButton = PWM_MAIN.getObject('resetButton-' + keyName);
    const modifiedIcon = PWM_MAIN.getObject('modifiedNoticeIcon-' + keyName);
    let settingSyntax = '';
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
    const parentDiv = 'table_setting_' + settingKey;
    return PWM_MAIN.getObject(parentDiv);
};

PWM_CFGEDIT.clearDivElements = function(parentDiv) {
    const parentDivElement = PWM_MAIN.getObject(parentDiv);
    if (parentDivElement !== null) {
        if (parentDivElement.hasChildNodes()) {
            while (parentDivElement.childNodes.length >= 1) {
                const firstChild = parentDivElement.firstChild;
                parentDivElement.removeChild(firstChild);
            }
        }
    }
};

PWM_CFGEDIT.addValueButtonRow = function(parentDiv, keyName, addFunction) {
    const buttonId = keyName + '-addValueButton';
    const newTableRow = document.createElement("tr");
    newTableRow.setAttribute("style", "border-width: 0");
    newTableRow.setAttribute("colspan", "5");

    const newTableData = document.createElement("td");
    newTableData.setAttribute("style", "border-width: 0;");

    const addItemButton = document.createElement("button");
    addItemButton.setAttribute("type", "button");
    addItemButton.setAttribute("id", buttonId);
    addItemButton.setAttribute("class", "btn");
    addItemButton.onclick = addFunction;
    addItemButton.innerHTML = "Add Value";
    newTableData.appendChild(addItemButton);

    const parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.appendChild(newTableRow);
    newTableRow.appendChild(newTableData);
};

PWM_CFGEDIT.saveConfiguration = function() {
    PWM_VAR['cancelHeartbeatCheck'] = true;
    PWM_MAIN.preloadAll(function(){
        const confirmFunction = function(){
            const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'finishEditing');
            const saveFunction = function(data) {
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
                    PWM_MAIN.ajaxRequest(url,saveFunction);
                }});
        };
        PWM_CFGEDIT.showChangeLog(confirmFunction);
    });
};

PWM_CFGEDIT.showChangeLog=function(nextFunction) {
    const jasonFunction = function() {
        PWM_CFGEDIT.showHealthWarnings(nextFunction);
    }
    const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'readChangeLog');
    const loadFunction = function(data) {
        PWM_MAIN.closeWaitDialog();
        const bodyText = PWM_CFGEDIT.makeChangeLogHtml(data['data']);
        const titleText = 'Changes';
        const okLabel = PWM_MAIN.showString('Button_Continue');
        PWM_MAIN.showConfirmDialog({title: titleText, okLabel:okLabel, text: bodyText, dialogClass:'wide', showClose: true, okAction:jasonFunction});
    };
    PWM_MAIN.showWaitDialog({loadFunction: function () {
            PWM_MAIN.ajaxRequest(url, loadFunction);
        }
    });
};

PWM_CFGEDIT.showHealthWarnings=function( nextFunction ) {
    const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'readWarnings');
    const loadFunction = function(data) {
        PWM_MAIN.closeWaitDialog();
        let bodyText = PWM_CFGEDIT.makeConfigWarningsHtml(data['data'])
        bodyText += '<br/><div>' + PWM_CONFIG.showString('MenuDisplay_SaveConfig') + '</div>';
        const titleText = 'Configuration Concerns';
        PWM_MAIN.showConfirmDialog({title: titleText, text: bodyText, dialogClass:'wide', showClose: true, okAction:nextFunction});
    };
    PWM_MAIN.showWaitDialog({loadFunction: function () {
            PWM_MAIN.ajaxRequest(url, loadFunction);
        }
    });
};

PWM_CFGEDIT.setConfigurationPassword = function(password) {
    if (password) {
        const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'setConfigurationPassword');
        const loadFunction = function(data) {
            if (data['error']) {
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Error'), text: data['errorMessage']});
            } else {
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Success'), text: data['successMessage']});
            }
        };
        const errorFunction = function(errorObj) {
            PWM_MAIN.closeWaitDialog();
            PWM_MAIN.showDialog ({title:PWM_MAIN.showString('Title_Error'),text:"error saving configuration password: " + errorObj});
        };
        PWM_MAIN.clearDijitWidget('dialogPopup');
        PWM_MAIN.showWaitDialog({loadFunction:function(){
                PWM_MAIN.ajaxRequest(url,loadFunction,{errorFunction:errorFunction,content:{password:password}});
            }});
        return;
    }

    const writeFunction = function(passwordValue) {
        PWM_CFGEDIT.setConfigurationPassword(passwordValue);
    };
    ChangePasswordHandler.popup('configPw','Configuration Password',writeFunction);
};


function handleResetClick(settingKey) {
    const label = PWM_SETTINGS['settings'][settingKey] ? PWM_SETTINGS['settings'][settingKey]['label'] : ' ';
    const dialogText = PWM_CONFIG.showString('Warning_ResetSetting',{value1:label});
    const titleText = 'Reset ' + label ? label : '';

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

    PWM_CFGEDIT.initDomainMenu();

    PWM_CONFIG.heartbeatCheck();

    PWM_CFGEDIT.loadMainPageBody();

    console.log('completed initConfigEditor');
    if (nextFunction) {
        nextFunction();
    }
};

PWM_CFGEDIT.initDomainMenu = function() {
    const domainList = PWM_VAR['domainIds'];
    if ( domainList && domainList.length > 1 )
    {
        const domainMenuElement = PWM_MAIN.getObject('domainMenu');
        let html = '<select id="domainMenuSelect">';

        const systemSelected = PWM_VAR['selectedDomainId'] === 'system';
        html += '<option value="system"' + ( systemSelected ? ' selected' : '') + '>System</option>';

        PWM_MAIN.JSLibrary.forEachInArray( domainList, function(domainId){
            const selected = PWM_VAR['selectedDomainId'] === domainId;
            html += '<option ' + ( selected ? 'selected ' : '') + 'value="' + domainId + '">Domain: ' + domainId + "</option>";
        } )
        html += '</select>';
        domainMenuElement.innerHTML = html;
        PWM_MAIN.addEventHandler('domainMenuSelect','change',function(){
            const selectedDomain = PWM_MAIN.JSLibrary.readValueOfSelectElement('domainMenuSelect');
            PWM_MAIN.Preferences.writeSessionStorage('configEditor-lastSelected', null);
            PWM_MAIN.gotoUrl(PWM_GLOBAL['url-context'] + '/private/config/editor/' + selectedDomain );
        });
    }
}

PWM_CFGEDIT.executeSettingFunction = function (setting, name, resultHandler, extraData) {
    const jsonSendData = {};
    jsonSendData['setting'] = setting;
    jsonSendData['function'] = name;
    jsonSendData['extraData'] = extraData;

    resultHandler = resultHandler !== undefined ? resultHandler : function(data) {
        const msgBody = '<div style="max-height: 400px; overflow-y: auto">' + data['successMessage'] + '</div>';
        PWM_MAIN.showDialog({width:700,title: 'Results', text: msgBody, okAction: function () {
                PWM_CFGEDIT.loadMainPageBody();
            }});
    };

    let requestUrl = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'executeSettingFunction');
    if (PWM_CFGEDIT.readCurrentProfile()) {
        requestUrl = PWM_MAIN.addParamToUrl(requestUrl,'profile',PWM_CFGEDIT.readCurrentProfile());
    }

    PWM_MAIN.showWaitDialog({loadFunction:function() {
            const loadFunction = function(data) {
                if (data['error']) {
                    PWM_MAIN.showErrorDialog(data);
                } else {
                    resultHandler(data,extraData);
                }
            };
            PWM_MAIN.ajaxRequest(requestUrl, loadFunction, {content:jsonSendData});
        }});
};

PWM_CFGEDIT.processSettingSearch = function(destinationDiv) {
    const iteration = 'settingSearchIteration' in PWM_VAR ? PWM_VAR['settingSearchIteration'] + 1 : 0;
    const startTime = new Date().getTime();
    PWM_VAR['settingSearchIteration'] = iteration;

    const resetDisplay = function() {
        PWM_MAIN.addCssClass('indicator-noResults',"hidden");
        PWM_MAIN.addCssClass('indicator-searching',"hidden");
        PWM_MAIN.addCssClass(destinationDiv.id,"hidden");
        destinationDiv.innerHTML = '';
    };

    const readSearchTerm = function() {
        if (!PWM_MAIN.getObject('homeSettingSearch') || !PWM_MAIN.getObject('homeSettingSearch') || PWM_MAIN.getObject('homeSettingSearch').value.length < 1) {
            return null;
        }
        return PWM_MAIN.getObject('homeSettingSearch').value;
    };

    console.log('beginning search #' + iteration);
    const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'search');

    const loadFunction = function(data) {
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
            let bodyText = '';
            let resultCount = 0;
            const elapsedTime = (new Date().getTime()) - startTime;
            if (PWM_MAIN.JSLibrary.isEmpty(data['data'])) {
                PWM_MAIN.removeCssClass('indicator-noResults','hidden')
                console.log('search #' + iteration + ', 0 results, ' + elapsedTime + 'ms');
            } else {
                PWM_MAIN.addCssClass('indicator-noResults','hidden')
                PWM_MAIN.JSLibrary.forEachInObject(data['data'], function (categoryIter, category) {
                    bodyText += '<div class="panel-searchResultCategory">' + categoryIter + '</div>';
                    PWM_MAIN.JSLibrary.forEachInObject(category, function (settingIter, setting) {
                        const profileID = setting['profile'];
                        const linkID = 'link-' + setting['category'] + '-' + settingIter + (profileID ? profileID : '');
                        const settingID = "search_" + (profileID ? profileID + '_' : '') + settingIter;
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
                        const profileID = setting['profile'];
                        const settingID = "search_" + (profileID ? profileID + '_' : '') + settingKey;
                        const value = setting['value'];
                        let toolBody = '<span style="font-weight: bold">Setting</span>';
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
                        const linkID = 'link-' + setting['category'] + '-' + settingKey + (profileID ? profileID : '');
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
    const validationProps = {};
    validationProps['serviceURL'] = url;
    validationProps['readDataFunction'] = function(){
        resetDisplay();
        PWM_MAIN.removeCssClass('indicator-searching','hidden');

        const value = readSearchTerm();
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
            const settingInfo = PWM_SETTINGS['settings'][settingKey];
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
        let text = PWM_SETTINGS['categories'][category]['menuLocation'];
        if (PWM_SETTINGS['categories'][category]['profiles']) {
            text = text.replace('PROFILE',profile);
        }
        PWM_CFGEDIT.setBreadcrumbText(text);
    }

    const item = {};
    item['id'] = category;
    item['type'] = 'category';

    if (settingKey) {
        setTimeout(function(){
            const settingElement = PWM_CFGEDIT.getSettingValueElement(settingKey);
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

PWM_CFGEDIT.makeChangeLogHtml = function(changeData) {
    let bodyText = '<div class="changeLogViewBox">';
    if (PWM_MAIN.JSLibrary.isEmpty(changeData)) {
        bodyText += '<div class="changeLogValue">No changes.</div>';
    } else {
        PWM_MAIN.JSLibrary.forEachInObject(changeData, function (key) {
            bodyText += '<div class="changeLogKey">' + key + '</div>';
            bodyText += '<div class="changeLogValue">' + changeData[key] + '</div>';
        });
    }
    bodyText += '</div>';
    return bodyText;
};

PWM_CFGEDIT.makeConfigWarningsHtml = function(configWarnings) {
    let bodyText = '<div class="changeLogViewBox">';
    PWM_MAIN.JSLibrary.forEachInObject(configWarnings,function(key){
        bodyText += '<div class="changeLogKey">' + key + '</div>';
        const values = configWarnings[key];
        PWM_MAIN.JSLibrary.forEachInArray(values,function(value){
            bodyText += '<div class="changeLogValue">' + value + '</div>';
        });
    });
    bodyText += '</div>';
    return bodyText;
};

PWM_CFGEDIT.cancelEditing = function() {
    const nextUrl = PWM_GLOBAL['url-context'] + '/private/config/manager';
    const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'readChangeLog');
    PWM_MAIN.showWaitDialog({loadFunction:function(){
            const loadFunction = function(data) {
                if (data['error']) {
                    PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
                } else {
                    if (!PWM_MAIN.JSLibrary.isEmpty(data['data'])) {
                        let bodyText = PWM_CFGEDIT.makeChangeLogHtml(data['data']);
                        bodyText += '<div>';
                        bodyText += PWM_CONFIG.showString('MenuDisplay_CancelConfig');
                        bodyText += '</div>';
                        PWM_MAIN.closeWaitDialog();
                        PWM_MAIN.showConfirmDialog({dialogClass:'wide',showClose:true,allowMove:true,text:bodyText,okAction:
                                function () {
                                    PWM_MAIN.showWaitDialog({loadFunction: function () {
                                            const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'cancelEditing');
                                            PWM_MAIN.ajaxRequest(url,function(){
                                                PWM_MAIN.gotoUrl(nextUrl, {addFormID: true});
                                            });
                                        }});
                                }
                        });
                    } else {
                        PWM_MAIN.gotoUrl(nextUrl, {addFormID: true});
                    }
                }
            };
            PWM_MAIN.ajaxRequest(url, loadFunction);
        }});
};

PWM_CFGEDIT.showMacroHelp = function() {
    const processExampleFunction = function() {
        PWM_MAIN.getObject('panel-testMacroOutput').innerHTML = PWM_MAIN.showString('Display_PleaseWait');
        const sendData = {};
        sendData['input'] = PWM_MAIN.getObject('input-testMacroInput').value;
        const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'testMacro');
        const loadFunction = function(data) {
            PWM_MAIN.getObject('panel-testMacroOutput').innerHTML = data['data'];
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{content:sendData});
    };

    const loadFunction = function() {
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

    const options = {};
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
    const options = {};
    options['title'] = 'Timezones'
    options['id'] = 'id-dialog-timeZoneHelp'
    options['dialogClass'] = 'wide';
    options['dojoStyle'] = 'width: 750px';
    options['showClose'] = true;
    options['href'] = PWM_GLOBAL['url-context'] + "/public/reference/timezones.jsp"
    PWM_MAIN.showDialog( options );
};

PWM_CFGEDIT.showDateTimeFormatHelp = function() {
    const options = {};
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
            let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'ldapHealthCheck');
            url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
            const loadFunction = function(data) {
                PWM_MAIN.closeWaitDialog();
                if (data['error']) {
                    PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
                } else {
                    const bodyText = PWM_ADMIN.makeHealthHtml(data['data'],false,false);
                    const profileName = PWM_CFGEDIT.readCurrentProfile();
                    const titleText = PWM_MAIN.showString('Field_LdapProfile') + ": " + profileName;
                    PWM_MAIN.showDialog({text:bodyText,title:titleText});
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction);
        }});
};

PWM_CFGEDIT.databaseHealthCheck = function() {
    PWM_MAIN.showWaitDialog({title:'Checking database connection...',loadFunction:function(){
            const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'databaseHealthCheck');
            const loadFunction = function(data) {
                PWM_MAIN.closeWaitDialog();
                if (data['error']) {
                    PWM_MAIN.showDialog({title: PWM_MAIN.showString("Title_Error"), text: data['errorMessage']});
                } else {
                    const bodyText = PWM_ADMIN.makeHealthHtml(data['data'],false,false);
                    const titleText = 'Database Connection Status';
                    PWM_MAIN.showDialog({text:bodyText,title:titleText});
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction);
        }});
};

PWM_CFGEDIT.httpsCertificateView = function() {
    PWM_MAIN.showWaitDialog({title:'Parsing...',loadFunction:function(){
            const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'httpsCertificateView');
            const loadFunction = function(data) {
                PWM_MAIN.closeWaitDialog();
                if (data['error']) {
                    PWM_MAIN.showErrorDialog(data);
                } else {
                    const bodyText = '<pre>' + data['data'] + '</pre>';
                    const titleText = 'HTTPS Certificate';
                    PWM_MAIN.showDialog({text:bodyText,title:titleText});
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction);
        }});
};

PWM_CFGEDIT.smsHealthCheck = function() {
    const title = 'Test SMS Settings'

    const dialogFormRows = '<p>' + PWM_CONFIG.showString('Warning_SmsTestData') +'</p>'
    + '<tr><td>To</td><td><input name="to" type="text" value="555-1212"/></td></tr>'
     + '<tr><td>Message</td><td><input name="message" type="text" value="Test Message"/></td></tr>';

    const actionParam = 'smsHealthCheck';

    PWM_CFGEDIT.healthCheckImpl(dialogFormRows,title,actionParam);
};

PWM_CFGEDIT.emailHealthCheck = function() {
    const title =  PWM_CONFIG.showString('Warning_EmailTestData');

    const dialogFormRows = '<tr><td>To</td><td><input name="to" type="text" value="test@example.com"/></td></tr>'
     + '<tr><td>From</td><td><input name="from" type="text" value="@DefaultEmailFromAddress@"/></td></tr>'
     + '<tr><td>Subject</td><td><input name="subject" type="text" value="Test Email"/></td></tr>'
     + '<tr><td>Body</td><td><input name="body" type="text" value="Test Email""/></td></tr>';

    const actionParam = 'emailHealthCheck';

    PWM_CFGEDIT.healthCheckImpl(dialogFormRows,title,actionParam);
};

PWM_CFGEDIT.healthCheckImpl = function(dialogFormRows, title, actionParam) {
    const formBody = '<form id="parametersForm"><table>' + dialogFormRows + '</table></form>';
    PWM_MAIN.showDialog({text:formBody,showCancel:true,title:title,closeOnOk:false,okAction:function(){
            const formElement = PWM_MAIN.getObject("parametersForm");
            const formData = PWM_MAIN.JSLibrary.formToValueMap(formElement);
            let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', actionParam);
            url = PWM_MAIN.addParamToUrl(url,'profile',PWM_CFGEDIT.readCurrentProfile());
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                    const loadFunction = function(data) {
                        if (data['error']) {
                            PWM_MAIN.showErrorDialog(data);
                        } else {
                            const bodyText = '<div class="logViewer">' + data['data'] + '</div>';
                            PWM_MAIN.showDialog({text:bodyText,title:title,showCancel:true,dialogClass:'wide'});
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
                    let url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'setOption');
                    url = PWM_MAIN.addParamToUrl(url, 'template',newTemplate);
                    PWM_MAIN.ajaxRequest(url, function(){ PWM_MAIN.gotoUrl('editor'); });
                }});
        }
    });
};

PWM_CFGEDIT.loadMainPageBody = function() {

    const drawSettingsFunction = function () {
        let dispatched = false;
        const lastSelected = PWM_MAIN.Preferences.readSessionStorage('configEditor-lastSelected', null);
        if (lastSelected) {
            PWM_CFGEDIT.dispatchNavigationItem(lastSelected);
            dispatched = true;
        }

        if (!dispatched)
        {
            const systemSelected = PWM_VAR['selectedDomainId'] === 'system';
            if (systemSelected) {
                PWM_CFGEDIT.dispatchNavigationItem({id: 'DOMAINS', type: 'category', category: 'DOMAINS'});
            } else {
                PWM_CFGEDIT.dispatchNavigationItem({id: 'TEMPLATES', type: 'category', category: 'TEMPLATES'});
            }
        }

        {
            const processActionParam = PWM_MAIN.JSLibrary.getParameterByName('processAction');
            if (processActionParam === 'gotoSetting') {
                PWM_CFGEDIT.gotoSetting(
                    PWM_MAIN.JSLibrary.getParameterByName('category'),
                    PWM_MAIN.JSLibrary.getParameterByName('settingKey'),
                    PWM_MAIN.JSLibrary.getParameterByName('profile') );
                return;
            }
        }
    }

    PWM_CFGEDIT.drawNavigationMenu( drawSettingsFunction );
};

PWM_CFGEDIT.displaySettingsCategory = function(category) {
    const settingsPanel = PWM_MAIN.getObject('settingsPanel');
    settingsPanel.innerHTML = '';
    console.log('loadingSettingsCategory: ' + category);

    if (!category) {
        settingsPanel.innerHTML = '';
        console.log('no selected category');
        return;
    }
    let htmlSettingBody = '';

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
    const settingsPanel = PWM_MAIN.getObject('settingsPanel');
    settingsPanel.innerHTML = '';
    const settingInfo = PWM_SETTINGS['settings'][settingKey];
    console.log('drawing profile-editor for setting-' + settingKey);

    settingsPanel.innerHTML = PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo);
    PWM_CFGEDIT.initSettingDisplay(settingInfo);
};

PWM_CFGEDIT.drawHtmlOutlineForSetting = function(settingInfo, options) {
    options = options === undefined ? {} : options;
    const settingKey = settingInfo['key'];
    const settingLabel = settingInfo['label'];
    let htmlBody = '<div id="outline_' + settingKey + '" class="setting_outline nodisplay">'
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
        const prefs = PWM_MAIN.Preferences.readSessionStorage('helpExpanded',{});
        const expandHelp = settingKey in prefs;
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
    const settingKey = setting['key'];
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

    const syntax = setting['syntax'];
    const syntaxFunction = PWM_CFGEDIT.syntaxFunctionMap[syntax];
    if ( syntaxFunction ) {
        syntaxFunction(settingKey);
    }
};

PWM_CFGEDIT.drawNavigationMenu = function(nextFunction) {
    console.log('drawNavigationMenu')
    PWM_MAIN.getObject('navigationTree').innerHTML = '';
    //PWM_MAIN.setStyle('navigationTreeWrapper','display','none');

    const makeTreeFunction = function(menuTreeData) {
        require(["dojo","dojo/_base/window", "dojo/store/Memory", "dijit/tree/ObjectStoreModel", "dijit/Tree","dijit","dojo/domReady!"],
            function(dojo, win, Memory, ObjectStoreModel, Tree)
            {
                PWM_MAIN.clearDijitWidget('navigationTree');
                // Create test store, adding the getChildren() method required by ObjectStoreModel
                const myStore = new Memory({
                    data: menuTreeData,
                    getChildren: function(object){
                        return this.query({parent: object.id});
                    }
                });

                // Create the model
                const model = new ObjectStoreModel({
                    store: myStore,
                    query: {id: 'ROOT'},
                    mayHaveChildren: function(object){
                        return object.type === 'navigation';
                    }
                });

                // Create the Tree.
                const tree = new Tree({
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
                        const path = tree.get('paths');
                        PWM_MAIN.Preferences.writeSessionStorage('configEditor-path',JSON.stringify(path));
                        PWM_CFGEDIT.dispatchNavigationItem(item);
                    }
                });

                const storedPath = PWM_MAIN.Preferences.readSessionStorage('configEditor-path');
                if (storedPath) {
                    const path = JSON.parse(storedPath);
                    tree.set('paths', path);
                }

                PWM_MAIN.getObject('navigationTree').innerHTML = '';
                tree.placeAt(PWM_MAIN.getObject('navigationTree'));
                tree.startup();
                //PWM_MAIN.setStyle('navigationTreeWrapper','display','inherit');
                PWM_VAR['navigationTree'] = tree; // used for expand/collapse button events;
                console.log('completed menu tree drawing');
            }
        );
    };

    const url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction', 'menuTreeData');
    const filterParams = PWM_CFGEDIT.readNavigationFilters();

    PWM_MAIN.ajaxRequest(url,function(data){
        const menuTreeData = data['data'];
        makeTreeFunction(menuTreeData);
        if (nextFunction) {
            nextFunction();
        }
    },{content:filterParams,preventCache:true});
};

PWM_CFGEDIT.readNavigationFilters = function() {
    const result = {};
    result['modifiedSettingsOnly'] = 'settingFilter_modifiedSettingsOnly' in PWM_VAR ? PWM_VAR['settingFilter_modifiedSettingsOnly'] : false;
    result['level'] = 'settingFilter_level' in PWM_VAR ? PWM_VAR['settingFilter_level'] : 2;
    result['text'] = 'settingFilter_text' in PWM_VAR ? PWM_VAR['settingFilter_text'] : '';
    return result;
};

PWM_CFGEDIT.dispatchNavigationItem = function(item) {
    const currentID = item['id'];
    const type = item['type'];
    if  (type === 'navigation') {
        /* not used, nav tree set to auto-expand */
    } else if (type === 'category') {
        const category = item['category'];
        if (item['profile']) {
            PWM_CFGEDIT.gotoSetting(category,null,item['profile']);
        } else {
            PWM_CFGEDIT.gotoSetting(category);
        }
    } else if (type === 'displayText') {
        const keys = item['keys'];
        PWM_CFGEDIT.setBreadcrumbText('Display Text - ' + item['name']);
        PWM_CFGEDIT.drawDisplayTextPage(currentID,keys);
    } else if (type === 'profile') {
        const category = item['category'];
        PWM_CFGEDIT.gotoSetting(category,null,currentID);
    } else if (type === 'profileDefinition') {
        const profileSettingKey = item['profileSetting'];
        PWM_CFGEDIT.drawProfileEditorPage(profileSettingKey);
    }
};

PWM_CFGEDIT.drawDisplayTextPage = function(settingKey, keys) {
    const settingsPanel = PWM_MAIN.getObject('settingsPanel');
    let remainingLoads = keys.length;
    settingsPanel.innerHTML = '<div id="displaytext-loading-panel" style="width:100%; text-align: center">'
        + PWM_MAIN.showString('Display_PleaseWait') + '&nbsp;<span id="remainingCount"></div>';
    console.log('drawing displaytext-editor for setting-' + settingKey);
    let htmlBody = '<div id="localetext-editor-wrapper" style="display:none">';
    for (const key in keys) {
        const displayKey = 'localeBundle-' + settingKey + '-' + keys[key];
        const settingInfo = {};
        settingInfo['key'] = displayKey;
        settingInfo['label'] = keys[key];
        htmlBody += PWM_CFGEDIT.drawHtmlOutlineForSetting(settingInfo,{showHelp:false});
    }
    settingsPanel.innerHTML = settingsPanel.innerHTML + htmlBody;

    const initSetting = function(keyCounter) {
        if (PWM_VAR['outstandingOperations'] > 5) {
            setTimeout(function () { initSetting(keyCounter); }, 50);
            return;
        }
        const displayKey = 'localeBundle-' + settingKey + '-' + keys[keyCounter];
        const settingInfo = {};
        settingInfo['key'] = displayKey;
        settingInfo['label'] = keys[keyCounter];
        settingInfo['syntax'] = 'NONE';
        PWM_CFGEDIT.initSettingDisplay(settingInfo);
        LocalizedStringValueHandler.init(displayKey,{required:true});
        remainingLoads--;
        PWM_MAIN.getObject('remainingCount').innerHTML = remainingLoads > 0 ? remainingLoads : '';
    };

    let delay = 5;
    for (const key in keys) {
        (function(keyCounter) {
            setTimeout(function(){
                initSetting(keyCounter);
            },delay);
            delay = delay + 5;
        })(key);
    }
    const checkForFinishFunction = function() {
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
    const clientConfigUrl = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','settingData');
    const loadFunction = function(data) {
        if (data['error'] === true) {
            console.error('unable to load ' + clientConfigUrl + ', error: ' + data['errorDetail'])
        } else {
            for (const settingKey in data['data']) {
                PWM_SETTINGS[settingKey] = data['data'][settingKey];
            }
            PWM_VAR['domainIds'] = data['data']['var']['domainIds'];
        }
        console.log('loaded settings data');
        if (nextFunction) nextFunction();
    };
    const errorFunction = function(error) {
        const errorMsg = 'unable to read config settings app-data: ' + error;
        console.log(errorMsg);
        if (!PWM_VAR['initError']) PWM_VAR['initError'] = errorMsg;
        if (nextFunction) nextFunction();
    };
    const filterParams = PWM_CFGEDIT.readNavigationFilters();
    PWM_MAIN.ajaxRequest(clientConfigUrl, loadFunction, {method:'POST',errorFunction:errorFunction,content:filterParams});
};

PWM_CFGEDIT.displaySettingHelp = function(settingKey) {
    console.log('toggle help for ' + settingKey);
    const helpExpandedPrefs = PWM_MAIN.Preferences.readSessionStorage('helpExpanded',{});
    const element = PWM_MAIN.getObject('pane-help-' + settingKey);
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
    const level = PWM_MAIN.Preferences.readSessionStorage('settingFilter_level',2);
    PWM_MAIN.getObject('radio-setting-level-' + level).checked = true;
    PWM_VAR['settingFilter_level'] = level;

    const modified = PWM_MAIN.Preferences.readSessionStorage('settingFilter_modifiedSettingsOnly',false);
    const idSuffix = modified ? 'modified' : 'all';
    PWM_MAIN.getObject('radio-modified-only-' + idSuffix).checked = true;
    PWM_VAR['settingFilter_modifiedSettingsOnly'] = modified;
};

PWM_CFGEDIT.handleSettingsFilterLevelRadioClick = function (){
    const value = parseInt(PWM_MAIN.JSLibrary.readValueOfRadioFormInput('radio-setting-level'));
    PWM_VAR['settingFilter_level'] = value;
    PWM_MAIN.Preferences.writeSessionStorage('settingFilter_level',value);
    PWM_CFGEDIT.loadMainPageBody();
};

PWM_CFGEDIT.handleModifiedSettingsRadioClick = function (){
    const value = PWM_MAIN.JSLibrary.readValueOfRadioFormInput('radio-modified-only') === 'modified';
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
            const linkValue = element.getAttribute('data-gotoSettingLink');
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
    const categoryInfo = PWM_SETTINGS['categories'][settingInfo['category']];
    const macroSupport = PWM_MAIN.JSLibrary.arrayContains(settingInfo['flags'],'MacroSupport');
    const infoPanelElement = PWM_MAIN.getObject('infoPanel');

    let text = '<div class="setting_outline">';
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


