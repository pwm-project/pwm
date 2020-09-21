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

// -------------------------- user permission handler ------------------------------------

var UserPermissionHandler = {};
UserPermissionHandler.defaultFilterValue = {type:'ldapFilter',ldapQuery:"(objectClass=*)",ldapBase:""};
UserPermissionHandler.defaultGroupValue = {type:'ldapGroup',ldapBase:""};
UserPermissionHandler.defaultUserValue = {type:'ldapUser',ldapBase:""};
UserPermissionHandler.defaultAllUserValue = {type:'ldapAllUsers',ldapProfileID:'all'};

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
    if (resultValue.length > 0) {
        for (var iteration in resultValue) {
            (function (rowKey) {
                if (htmlBody.length > 0) {
                    htmlBody += '<br/><br/><div style="clear:both; text-align:center">OR</span></div>'
                }
                htmlBody += UserPermissionHandler.drawRow(keyName, resultValue, rowKey)
            }(iteration));
        }
    } else {
        htmlBody += '<div><p>No users are added.</p></div>';
    }

    htmlBody += '<button class="btn" id="button-' + keyName + '-addPermission">'
        + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>' + PWM_CONFIG.showString('Button_AddPermission') + '</button>';

    var hideMatch = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][keyName]['flags'], 'Permission_HideMatch')
        || resultValue.length === 0;
    if (!hideMatch) {
        htmlBody += '<button id="button-' + keyName + '-viewMatches" class="btn">'
            + '<span class="btn-icon pwm-icon pwm-icon-users"></span>View Matches</button>';
    }

    parentDivElement.innerHTML = htmlBody;

    for (var iteration in resultValue) {
        (function(rowKey) {
            UserPermissionHandler.addRowHandlers(resultValue, keyName, rowKey);
        }(iteration));
    }

    PWM_MAIN.addEventHandler('button-' + keyName + '-viewMatches','click',function(){
        var dataHandler = function(data) {
            var html = PWM_CONFIG.convertListOfIdentitiesToHtml(data['data']);
            PWM_MAIN.showDialog({title:'Matches',text:html,dialogClass:'wide',showOk:false,showClose:true});
        };
        PWM_CFGEDIT.executeSettingFunction(keyName, 'password.pwm.config.function.UserMatchViewerFunction', dataHandler, null)
    });

    PWM_MAIN.addEventHandler('button-' + keyName + '-addPermission','click',function(){
        UserPermissionHandler.addPermission(keyName);
    });
};

UserPermissionHandler.drawRow = function(keyName, resultValue, rowKey) {
    var inputID = "value-" + keyName + "-" + rowKey;
    var type = resultValue[rowKey]['type'];

    var htmlBody = '<div class="setting_item_value_wrapper" style="float:left; width: 560px;">';
    var profileLabelKey = (type === 'ldapAllUsers') ? 'Setting_Permission_Profile_AllUsers' : 'Setting_Permission_Profile';

    htmlBody += '<table class="noborder">'
        + '<tr><td style="width:200px" id="' + inputID + '_profileHeader' + '">' + PWM_CONFIG.showString(profileLabelKey) + '</td>'
        + '<td></td>'
        + '<td><div class="configStringPanel permissionValuePanel">'
        + '<table class="noborder"><tr><td><select name="' + inputID + '-profileSelect" id="' + inputID + '-profileSelect">'
        + '<option value="all">All Profiles</option><option value="specified">Specified Profile</option>'
        + '</select></td><td>'
        + '<div class="" id="' + inputID + '-profileWrapper">'
        + '<input  class="configStringInput permissionProfileValueInput" id="' + inputID + '-profile" list="' + inputID + '-datalist"/>'
        + '</div></td></tr></table>'
        + '<datalist id="' + inputID + '-datalist"/>'
        + '</div>'
        + '</td>'
        + '</tr>';

    if (type !== 'ldapAllUsers') {
        if (type !== 'ldapGroup' && type !== 'ldapUser') {
            htmlBody += '<tr>'
                + '<td><span id="' + inputID + '_FilterHeader' + '">' + PWM_CONFIG.showString('Setting_Permission_Filter') + '</span></td>'
                + '<td id="icon-edit-query-' + inputID + '"><div title="Edit Value" class="btn-icon pwm-icon pwm-icon-edit"></div></td>'
                + '<td><div class="configStringPanel noWrapTextBox border permissionValuePanel" id="' + inputID + '-query"></div></td>'
                + '</tr>';
        }

        var rowLabelKey = (type === 'ldapGroup') ? 'Setting_Permission_Base_Group' :
            (type === 'ldapUser') ? 'Setting_Permission_Base_User' : 'Setting_Permission_Base'
        htmlBody += '<tr>'
            + '<td><span id="' + inputID + '_BaseHeader' + '">'
            + PWM_CONFIG.showString(rowLabelKey)
            + '</span></td>'
            + '<td id="icon-edit-base-' + inputID + '"><div title="Edit Value" class="btn-icon pwm-icon pwm-icon-edit"></div></td>'
            + '<td><div class="configStringPanel noWrapTextBox border permissionValuePanel" id="' + inputID + '-base">&nbsp;</div></td>'
            + '</tr>';
    }

    htmlBody += '</table></div>';
    htmlBody += '<div id="button-' + inputID + '-deleteRow" style="float:right" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></div>';

    return htmlBody;
};

UserPermissionHandler.addRowHandlers = function( resultValue, keyName, rowKey) {
    var inputID = "value-" + keyName + "-" + rowKey;

    var profileDataList = PWM_MAIN.getObject(inputID + "-datalist");
    var profileIdList = PWM_SETTINGS['var']['ldapProfileIds'];
    for (var i in profileIdList) {
        var option = document.createElement('option');
        option.value = profileIdList[i];
        profileDataList.appendChild(option);
    }

    var currentProfile = PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapProfileID'];
    currentProfile = currentProfile === undefined ? '' : currentProfile;
    var profileSelectNewValueFunction = function(newValue, writeNewValue) {
        var allProfilesEnabled = !newValue || newValue === 'all' || newValue === '';
        if (allProfilesEnabled) {
            PWM_MAIN.JSLibrary.setValueOfSelectElement(inputID + '-profileSelect', 'all');
            PWM_MAIN.addCssClass( inputID + '-profileWrapper', 'hidden');
        } else {
            PWM_MAIN.JSLibrary.setValueOfSelectElement(inputID + '-profileSelect', 'specified');
            PWM_MAIN.removeCssClass( inputID + '-profileWrapper', 'hidden');
        }

        if ( writeNewValue ) {
            if (allProfilesEnabled) {
                PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapProfileID'] = '';
            }
            UserPermissionHandler.write(keyName);
        }
    }
    profileSelectNewValueFunction(currentProfile, false);
    PWM_MAIN.getObject(inputID+'-profile').value = currentProfile;

    PWM_MAIN.addEventHandler(inputID + '-profileSelect','change',function(){
        profileSelectNewValueFunction(this.value, true);
    });

    PWM_MAIN.addEventHandler(inputID + '-profile','input',function(){
        PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapProfileID'] = this.value;
        UserPermissionHandler.write(keyName);
    });

    if (resultValue[rowKey]['type'] !== 'ldapGroup') {
        UILibrary.addTextValueToElement(inputID + '-query', PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapQuery']);
        var queryEditor = function(){
            UILibrary.stringEditorDialog({
                value:PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapQuery'],
                completeFunction:function(value) {
                    PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapQuery'] = value;
                    UserPermissionHandler.write(keyName,true);
                }
            });
        };

        PWM_MAIN.addEventHandler(inputID + "-query",'click',function(){
            queryEditor();
        });
        PWM_MAIN.addEventHandler('icon-edit-query-' + inputID,'click',function(){
            queryEditor();
        });
    }

    var currentBaseValue = ('ldapBase' in resultValue[rowKey]) ? resultValue[rowKey]['ldapBase'] : "";
    var baseEditor = function(){
        UILibrary.editLdapDN(function(value, ldapProfileID) {
            PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapProfileID'] = ldapProfileID;
            PWM_VAR['clientSettingCache'][keyName][rowKey]['ldapBase'] = value;
            UserPermissionHandler.write(keyName,true);
        }, {currentDN: currentBaseValue, profile: currentProfile});
    };
    if (currentBaseValue && currentBaseValue.length > 0) {
        UILibrary.addTextValueToElement(inputID + '-base', currentBaseValue);
    }
    PWM_MAIN.addEventHandler(inputID + '-base','click',function(){
        baseEditor();
    });
    PWM_MAIN.addEventHandler('icon-edit-base-' + inputID,'click',function(){
        baseEditor();
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
}

UserPermissionHandler.write = function(settingKey,redraw) {
    var nextFunction = function(){
        if (redraw) {
            UserPermissionHandler.draw(settingKey);
        }
    };
    PWM_CFGEDIT.writeSetting(settingKey, PWM_VAR['clientSettingCache'][settingKey], nextFunction);
};

UserPermissionHandler.addPermission = function(keyName) {
    var bodyHtml = '<div><p>' + PWM_CONFIG.showString('MenuDisplay_Permissions') + '</p></div><table class="">'
    var hideGroup = PWM_MAIN.JSLibrary.arrayContains(PWM_SETTINGS['settings'][keyName]['flags'], 'Permission_HideGroups');
    bodyHtml += '<tr><td><button class="btn" id="button-' + keyName + '-addAllUsersValue">'
        + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>' + PWM_CONFIG.showString('MenuItem_Permission_AllUsers')
        + '</button></td><td>' + PWM_CONFIG.showString('MenuDisplay_Permission_AllUsers') + '</td></tr>';
    bodyHtml += '<tr><td><button class="btn" id="button-' + keyName + '-addUserValue">'
        + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>' + PWM_CONFIG.showString('MenuItem_Permission_LdapUser')
        + '</button></td><td>' + PWM_CONFIG.showString('MenuDisplay_Permission_LdapUser') + '</td></tr>';
    if (!hideGroup) {
        bodyHtml += '<tr><td><button class="btn" id="button-' + keyName + '-addGroupValue">'
            + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>' + PWM_CONFIG.showString('MenuItem_Permission_LdapGroup')
            + '</button></td><td>' + PWM_CONFIG.showString('MenuDisplay_Permission_LdapGroup') + '</td></tr>';
    }
    bodyHtml += '<tr><td><button class="btn" id="button-' + keyName + '-addFilterValue">'
        + '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>' + PWM_CONFIG.showString('MenuItem_Permission_LdapFilter')
        + '</button></td><td>' + PWM_CONFIG.showString('MenuDisplay_Permission_LdapFilter') + '</td></tr>';

    bodyHtml += '</table>';

    var completeAddPermission = function(template) {
        PWM_VAR['clientSettingCache'][keyName].push(PWM_MAIN.copyObject(template));
        PWM_MAIN.closeWaitDialog();
        UserPermissionHandler.write(keyName, true);
    }

    var dialogOptions = {};
    dialogOptions.title = PWM_CONFIG.showString('Button_AddPermission');
    dialogOptions.text = bodyHtml;
    dialogOptions.showCancel = false;
    dialogOptions.showClose = true;
    dialogOptions.showOk = false;
    dialogOptions.loadFunction = function() {
        PWM_MAIN.addEventHandler('button-' + keyName + '-addFilterValue','click',function(){
            completeAddPermission(UserPermissionHandler.defaultFilterValue);
        });

        PWM_MAIN.addEventHandler('button-' + keyName + '-addGroupValue','click',function(){
            completeAddPermission(UserPermissionHandler.defaultGroupValue);
        });

        PWM_MAIN.addEventHandler('button-' + keyName + '-addUserValue','click',function(){
            completeAddPermission(UserPermissionHandler.defaultUserValue);
        });

        PWM_MAIN.addEventHandler('button-' + keyName + '-addAllUsersValue','click',function(){
            completeAddPermission(UserPermissionHandler.defaultAllUserValue);
        });
    };
    PWM_MAIN.showDialog(dialogOptions);
}
