/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

// -------------------------- challenge handler ------------------------------------

var ChallengeSettingHandler = {};
ChallengeSettingHandler.defaultItem = {text:'Question',minLength:4,maxLength:200,adminDefined:true,enforceWordlist:true,maxQuestionCharsInAnswer:3};

ChallengeSettingHandler.init = function(settingKey) {
    var parentDiv = "table_setting_" + settingKey;
    console.log('ChallengeSettingHandler init for ' + settingKey);
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.readSetting(settingKey, function(resultValue) {
        PWM_VAR['clientSettingCache'][settingKey] = resultValue;
        if (PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
            var htmlBody = '<button class="btn" id="button-addValue-' + settingKey + '">';
            htmlBody += '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value';
            htmlBody += '</button>';

            var parentDivElement = PWM_MAIN.getObject(parentDiv);
            parentDivElement.innerHTML = htmlBody;

            PWM_MAIN.addEventHandler('button-addValue-' + settingKey,'click',function(){
                PWM_VAR['clientSettingCache'][settingKey] = {};
                PWM_VAR['clientSettingCache'][settingKey][''] = [];
                PWM_VAR['clientSettingCache'][settingKey][''].push(ChallengeSettingHandler.defaultItem);
                ChallengeSettingHandler.write(settingKey,function(){
                    ChallengeSettingHandler.init(settingKey);
                });
            });
        } else {
            ChallengeSettingHandler.draw(settingKey);
        }
    });
};

ChallengeSettingHandler.draw = function(settingKey) {
    var parentDiv = "table_setting_" + settingKey;
    var resultValue = PWM_VAR['clientSettingCache'][settingKey];
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    var bodyText = '<div class="footnote">Click on challenge questions to edit questions and policies</div>';
    PWM_CFGEDIT.clearDivElements(parentDiv, false);
    for (var localeName in resultValue) {
        (function(localeKey) {
            var multiValues = resultValue[localeKey];
            var rowCount = PWM_MAIN.JSLibrary.itemCount(multiValues);

            bodyText += '<table class="noborder"><tr><td>';
            bodyText += '<table class="setting-challenge-question-summary">';
            var localeLabel = localeName === '' ? 'Default Locale' : PWM_GLOBAL['localeInfo'][localeName] + " (" + localeName + ")";
            if (PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1) {
                bodyText += '<tr><td class="title" style="font-size:100%; font-weight:normal">' + localeLabel + '</td></tr>';
            }

            bodyText += '<tr>';
            bodyText += '<td style="width:100%" id="button-edit-' + settingKey + '-' + localeKey + '">';
            if (rowCount > 0) {
                for (var iteration in multiValues) {
                    var id = 'panel-value-' + settingKey + '-' + localeKey + '-' + iteration;
                    bodyText += '<div style="text-overflow:ellipsis; white-space:nowrap; overflow:hidden" id="' + id + '">text</div>';
                    bodyText += '<div style="font-size: 80%; font-style: italic">'
                        + '<span style="padding-left: 10px">Min Length: <span id="' + id + '-minLength"></span></span>'
                        + '<span style="padding-left: 10px">Max Length: <span id="' + id + '-maxLength"></span></span>'
                        + '<span style="padding-left: 10px">Max Question Chars: <span id="' + id + '-maxQuestions"></span></span>'
                        + '<span style="padding-left: 10px">Apply Wordlist: <span id="' + id + '-wordlist"></span></span>'
                        + '</div>';
                }
            } else {
                bodyText += '[No Questions]';
            }
            bodyText += '</td></tr>';

            bodyText += '</table></td><td class="noborder" style="width:20px; vertical-align:top">';
            if (localeName !== '' || PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) < 2) { // add remove locale x
                bodyText += '<div id="button-deleteRow-' + settingKey + '-' + localeKey + '" style="vertical-align:top" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></div>';
            }
            bodyText += '</td></tr></table><br/>';
        }(localeName));
    }
    parentDivElement.innerHTML = bodyText;

    var addLocaleFunction = function(localeValue) {
        if (localeValue in PWM_VAR['clientSettingCache'][settingKey]) {
            PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Error'),text:'Locale <i>' + localeValue + '</i> is already present.'});
        } else {
            PWM_VAR['clientSettingCache'][settingKey][localeValue] = [];
            PWM_VAR['clientSettingCache'][settingKey][localeValue][0] = ChallengeSettingHandler.defaultItem;
            ChallengeSettingHandler.write(settingKey, function(){
                ChallengeSettingHandler.init(settingKey);
            });
        }
    };
    var tableElement = document.createElement("div");
    parentDivElement.appendChild(tableElement);

    UILibrary.addAddLocaleButtonRow(tableElement, settingKey, addLocaleFunction, Object.keys(resultValue));

    for (var localeName in resultValue) {
        (function(localeKey) {
            PWM_MAIN.addEventHandler('button-edit-' + settingKey + '-' + localeKey,'click',function(){
                ChallengeSettingHandler.editLocale(settingKey,localeKey);
            });

            var multiValues = resultValue[localeKey];
            var rowCount = PWM_MAIN.JSLibrary.itemCount(multiValues);
            if (rowCount > 0) {
                for (var iteration in multiValues) {
                    (function (rowKey) {
                        var id = 'panel-value-' + settingKey + '-' + localeKey + '-' + iteration;
                        var questionText = multiValues[rowKey]['text'];
                        var adminDefined = multiValues[rowKey]['adminDefined'];
                        var output = (adminDefined ? questionText : '[User Defined]');
                        UILibrary.addTextValueToElement(id,output);
                        UILibrary.addTextValueToElement(id + '-minLength', multiValues[rowKey]['minLength']);
                        UILibrary.addTextValueToElement(id + '-maxLength', multiValues[rowKey]['maxLength']);
                        UILibrary.addTextValueToElement(id + '-maxQuestions', multiValues[rowKey]['maxQuestionCharsInAnswer']);
                        UILibrary.addTextValueToElement(id + '-wordlist', multiValues[rowKey]['enforceWordlist']);
                    }(iteration));
                }
            }

            PWM_MAIN.addEventHandler('button-deleteRow-' + settingKey + '-' + localeKey,'click',function(){
                ChallengeSettingHandler.deleteLocale(settingKey, localeKey)
            });
        }(localeName));
    }

};

ChallengeSettingHandler.editLocale = function(keyName, localeKey) {
    var localeDisplay = localeKey === "" ? "Default" : localeKey;
    var dialogBody = '<div id="challengeLocaleDialogDiv" style="max-height:500px; overflow-x: auto">';

    var localeName = localeKey;

    var resultValue = PWM_VAR['clientSettingCache'][keyName];

    var multiValues = resultValue[localeName];

    for (var iteration in multiValues) {
        (function(rowKey) {
            dialogBody += '<table class="noborder">';
            dialogBody += '<tr><td style="width: 15px" class="noborder">' + (parseInt(iteration) + 1) + '</td><td class="setting_outline">';
            dialogBody += '<table class="noborder" style="margin:0"><tr>';
            dialogBody += '<td colspan="200" style="border-width: 0;">';

            var inputID = "value-" + keyName + "-" + localeName + "-" + rowKey;
            PWM_MAIN.clearDijitWidget(inputID);

            dialogBody += '<input class="configStringInput" id="' + inputID + '" style="width: 700px" required="required" disabled value="Loading"/>';

            dialogBody += '</td>';
            dialogBody += '</tr>';

            dialogBody += '<tr><td>';

            dialogBody += '<label class="checkboxWrapper"><input type="checkbox" id="value-adminDefined-' + inputID + '" disabled/>Admin Defined</label>';

            dialogBody += '</td><td>';

            dialogBody += '<input type="number" id="button-minLength-' + inputID + '" style="width:50px" class="configNumericInput" min="1" max="255" value="' + multiValues[rowKey]['minLength'] + '"/>';
            dialogBody += '<br/>Min Length';

            dialogBody += '</td><td>';
            dialogBody += '<input type="number" id="button-maxLength-' + inputID + '" style="width:50px" class="configNumericInput" min="1" max="255" value="' + multiValues[rowKey]['maxLength'] + '"/>';
            dialogBody += '<br/>Max Length';

            dialogBody += '</td><td>';
            dialogBody += '<input type="number" id="button-maxQuestionCharsInAnswer-' + inputID + '" style="width:50px" class="configNumericInput" min="0" max="100" value="' + multiValues[rowKey]['maxQuestionCharsInAnswer'] + '"/>';
            dialogBody += '<br/>Max Question Characters';

            dialogBody += '</td><td>';
            dialogBody += '<label class="checkboxWrapper"><input type="checkbox" id="value-wordlist-' + inputID + '" disabled/>Apply Word List</label>';

            dialogBody += '</td></tr>';
            dialogBody += '</table></td><td class="noborder" style="vertical-align: top">';
            if (PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][keyName][localeKey]) > 1) { // add remove locale x

                dialogBody += '<div class="delete-row-icon action-icon pwm-icon pwm-icon-times" id="button-deleteRow-' + inputID + '"/>';
            }

            dialogBody += '</td></tr></table>';
            dialogBody += '<br/>';

        }(iteration));
    }

    dialogBody += '</div>';
    dialogBody += '<br/>';

    var dialogTitle = PWM_SETTINGS['settings'][keyName]['label'] + ' - ' + localeDisplay + ' Locale';
    PWM_MAIN.showDialog({
        title:dialogTitle,
        buttonHtml:'<button class="btn" id="button-addValue"><span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value</button>',
        text:dialogBody,
        showClose:false,
        dialogClass:'wide',
        loadFunction:function(){
            PWM_MAIN.addEventHandler('button-addValue','click',function(){
                ChallengeSettingHandler.addRow(keyName,localeKey);
            });
            for (var iteration in multiValues) {
                (function(rowKey) {
                    var inputID = "value-" + keyName + "-" + localeName + "-" + rowKey;
                    UILibrary.manageNumericInput('button-minLength-' + inputID, function(value){
                        PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['minLength'] = value;
                    })
                    UILibrary.manageNumericInput('button-maxLength-' + inputID, function(value){
                        PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['maxLength'] = value;
                    })
                    UILibrary.manageNumericInput('button-maxQuestionCharsInAnswer-' + inputID, function(value){
                        PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['maxQuestionCharsInAnswer'] = value;
                    })

                    // question text
                    var processQuestion = function() {
                        var isAdminDefined = multiValues[rowKey]['adminDefined'];
                        PWM_MAIN.getObject(inputID).value = isAdminDefined ? multiValues[rowKey]['text'] : '[User Defined]';
                        PWM_MAIN.getObject(inputID).disabled = !isAdminDefined;
                    };
                    processQuestion();
                    PWM_MAIN.addEventHandler(inputID, 'input', function () {
                        //if (!multiValues[rowKey]['adminDefined']) {
                        PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['text'] = PWM_MAIN.getObject(inputID).value;
                        //}
                    });

                    // admin defined checkbox
                    PWM_MAIN.getObject('value-adminDefined-' + inputID).disabled = false;
                    PWM_MAIN.getObject('value-adminDefined-' + inputID).checked = multiValues[rowKey]['adminDefined'];
                    PWM_MAIN.addEventHandler('value-adminDefined-' + inputID,'change',function(){
                        var checked = PWM_MAIN.getObject('value-adminDefined-' + inputID).checked;
                        multiValues[rowKey]['adminDefined'] = checked;
                        processQuestion();
                    });

                    // wordlist checkbox
                    PWM_MAIN.getObject('value-wordlist-' + inputID).disabled = false;
                    PWM_MAIN.getObject('value-wordlist-' + inputID).checked = multiValues[rowKey]['enforceWordlist'];
                    PWM_MAIN.addEventHandler('value-wordlist-' + inputID,'change',function(){
                        var checked = PWM_MAIN.getObject('value-wordlist-' + inputID).checked;
                        multiValues[rowKey]['enforceWordlist'] = checked;
                    });

                    // delete row
                    PWM_MAIN.addEventHandler('button-deleteRow-' + inputID, 'click', function () {
                        ChallengeSettingHandler.deleteRow(keyName, localeKey, rowKey);
                    });

                }(iteration));
            }

        },okAction:function(){
            ChallengeSettingHandler.write(keyName);
            ChallengeSettingHandler.draw(keyName);
        }});


};

ChallengeSettingHandler.deleteLocale = function(keyName,localeKey) {
    PWM_MAIN.showConfirmDialog({
        text: 'Are you sure you want to remove all the questions for the <i>' + localeKey + '</i> locale?',
        okAction:function(){
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                delete PWM_VAR['clientSettingCache'][keyName][localeKey];
                PWM_CFGEDIT.writeSetting(keyName, PWM_VAR['clientSettingCache'][keyName],function(){
                    PWM_MAIN.closeWaitDialog();
                    ChallengeSettingHandler.init(keyName);
                });
            }});
        }
    });
};

ChallengeSettingHandler.toggleAdminDefinedRow = function(toggleElement,inputID,keyName,localeKey,rowKey) {
    require(["dojo","dijit/registry"],function(dojo,registry){
        var currentSetting = toggleElement.checked;
        PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['adminDefined'] = currentSetting;
        var inputElement = registry.byId(inputID);
        if (currentSetting) {
            inputElement.set('disabled',false);
            inputElement.set('value','Question');
        } else {
            inputElement.set('disabled',true);
            inputElement.set('value','[User Defined]');
            PWM_VAR['clientSettingCache'][keyName][localeKey][rowKey]['text'] = '';
        }
    });
};

ChallengeSettingHandler.deleteRow = function(keyName, localeKey, rowName) {
    PWM_MAIN.showConfirmDialog({
        okAction:function(){
            PWM_MAIN.showWaitDialog({loadFunction:function(){
                delete PWM_VAR['clientSettingCache'][keyName][localeKey][rowName];
                ChallengeSettingHandler.write(keyName,function(){
                    ChallengeSettingHandler.editLocale(keyName, localeKey);
                });
            }})
        }
    });
};

ChallengeSettingHandler.addRow = function(keyName, localeKey) {
    PWM_MAIN.showWaitDialog({
        loadFunction:function(){
            var newValues = PWM_MAIN.copyObject(ChallengeSettingHandler.defaultItem);
            PWM_VAR['clientSettingCache'][keyName][localeKey].push(newValues);
            ChallengeSettingHandler.write(keyName,function(){
                ChallengeSettingHandler.editLocale(keyName, localeKey);
            });
        }
    });
};

ChallengeSettingHandler.write = function(keyName, nextFunction) {
    PWM_CFGEDIT.writeSetting(keyName, PWM_VAR['clientSettingCache'][keyName], nextFunction);
};
