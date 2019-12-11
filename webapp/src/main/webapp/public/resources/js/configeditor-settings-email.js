/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

// -------------------------- email table handler ------------------------------------

var EmailTableHandler = {};
EmailTableHandler.defaultValue = {
    to:"@User:Email@",
    from:"@DefaultEmailFromAddress@",
    subject:"Subject",
    bodyPlain:"Body",
    bodyHtml:"Body"
};

EmailTableHandler.init = function(keyName) {
    console.log('EmailTableHandler init for ' + keyName);
    PWM_CFGEDIT.readSetting(keyName, function(resultValue) {
        PWM_VAR['clientSettingCache'][keyName] = resultValue;
        EmailTableHandler.draw(keyName);
    });
};

EmailTableHandler.draw = function(settingKey) {
    var resultValue = PWM_VAR['clientSettingCache'][settingKey];
    var parentDiv = 'table_setting_' + settingKey;
    PWM_CFGEDIT.clearDivElements(parentDiv, true);
    PWM_CFGEDIT.clearDivElements(parentDiv, false);

    var htmlBody = '';
    for (var localeName in resultValue) {
        htmlBody += EmailTableHandler.drawRowHtml(settingKey,localeName)
    }
    var parentDivElement = PWM_MAIN.getObject(parentDiv);
    parentDivElement.innerHTML = htmlBody;

    for (var localeName in resultValue) {
        EmailTableHandler.instrumentRow(settingKey,localeName)
    }

    if (PWM_MAIN.JSLibrary.isEmpty(resultValue)) {
        var htmlBody = '<button class="btn" id="button-addValue-' + settingKey + '">';
        htmlBody += '<span class="btn-icon pwm-icon pwm-icon-plus-square"></span>Add Value';
        htmlBody += '</button>';

        var parentDivElement = PWM_MAIN.getObject(parentDiv);
        parentDivElement.innerHTML = htmlBody;

        PWM_MAIN.addEventHandler('button-addValue-' + settingKey,'click',function(){
            PWM_CFGEDIT.resetSetting(settingKey,function(){PWM_CFGEDIT.loadMainPageBody()});
        });

    } else {
        var addLocaleFunction = function(localeValue) {
            if (!PWM_VAR['clientSettingCache'][settingKey][localeValue]) {
                PWM_VAR['clientSettingCache'][settingKey][localeValue] = EmailTableHandler.defaultValue;
                EmailTableHandler.writeSetting(settingKey,true);
            }
        };
        UILibrary.addAddLocaleButtonRow(parentDiv, settingKey, addLocaleFunction, Object.keys(PWM_VAR['clientSettingCache'][settingKey]));
    }
};

EmailTableHandler.drawRowHtml = function(settingKey, localeName) {
    var localeLabel = localeName === '' ? 'Default Locale' : PWM_GLOBAL['localeInfo'][localeName] + " (" + localeName + ")";
    var idPrefix = "setting-" + localeName + "-" + settingKey;
    var htmlBody = '';
    htmlBody += '<table class="noborder" style=""><tr ><td class="noborder" style="max-width: 440px">';
    htmlBody += '<table>';
    if (PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) > 1) {
        htmlBody += '<tr><td colspan="5" class="title" style="font-size:100%; font-weight:normal">' + localeLabel + '</td></tr>';
    }
    var outputFunction = function (labelText, typeText) {
        htmlBody += '<tr><td style="text-align:right; border-width:0;">' + labelText + '</td>';
        htmlBody += '<td id="button-' + typeText + '-' + idPrefix + '" style="border-width:0; width: 15px"><span class="pwm-icon pwm-icon-edit"/></ta>';
        htmlBody += '<td style=""><div class="configStringPanel" id="panel-' + typeText + '-' + idPrefix + '"></div></td>';
        htmlBody += '</tr>';
    };
    outputFunction('To', 'to');
    outputFunction('From', 'from');
    outputFunction('Subject', 'subject');
    outputFunction('Plain Body', 'bodyPlain');
    outputFunction('HTML Body', 'bodyHtml');

    htmlBody += '</table></td><td class="noborder" style="width:20px; vertical-align:top">';
    if (localeName !== '' || PWM_MAIN.JSLibrary.itemCount(PWM_VAR['clientSettingCache'][settingKey]) < 2) { // add remove locale x
        htmlBody += '<div id="button-deleteRow-' + idPrefix + '" style="vertical-align:top" class="delete-row-icon action-icon pwm-icon pwm-icon-times"></div>';
    }
    htmlBody += '</td></tr></table><br/>';
    return htmlBody;
};


EmailTableHandler.instrumentRow = function(settingKey, localeName) {
    var idPrefix = "setting-" + localeName + "-" + settingKey;

    UILibrary.addTextValueToElement('panel-to-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['to']);
    PWM_MAIN.addEventHandler('button-to-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,false,'to',PWM_CONFIG.showString('Instructions_Edit_Email')); });
    PWM_MAIN.addEventHandler('panel-to-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,false,'to',PWM_CONFIG.showString('Instructions_Edit_Email')); });

    UILibrary.addTextValueToElement('panel-from-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['from']);
    PWM_MAIN.addEventHandler('button-from-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,false,'from'); });
    PWM_MAIN.addEventHandler('panel-from-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,false,'from'); });

    UILibrary.addTextValueToElement('panel-subject-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['subject']);
    PWM_MAIN.addEventHandler('button-subject-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,false,'subject'); });
    PWM_MAIN.addEventHandler('panel-subject-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,false,'subject'); });

    UILibrary.addTextValueToElement('panel-bodyPlain-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['bodyPlain']);
    PWM_MAIN.addEventHandler('button-bodyPlain-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,true,'bodyPlain'); });
    PWM_MAIN.addEventHandler('panel-bodyPlain-' + idPrefix,'click',function(){ EmailTableHandler.editor(settingKey,localeName,true,'bodyPlain'); });

    UILibrary.addTextValueToElement('panel-bodyHtml-' + idPrefix,PWM_VAR['clientSettingCache'][settingKey][localeName]['bodyHtml']);
    PWM_MAIN.addEventHandler('button-bodyHtml-' + idPrefix,'click',function(){ EmailTableHandler.htmlEditorChoice(settingKey,localeName,'bodyHtml'); });
    PWM_MAIN.addEventHandler('panel-bodyHtml-' + idPrefix,'click',function(){ EmailTableHandler.htmlEditorChoice(settingKey,localeName,'bodyHtml'); });

    PWM_MAIN.addEventHandler("button-deleteRow-" + idPrefix,"click",function(){
        PWM_MAIN.showConfirmDialog({okAction:function(){
                delete PWM_VAR['clientSettingCache'][settingKey][localeName];
                EmailTableHandler.writeSetting(settingKey,true);
            }});
    });
};

EmailTableHandler.htmlEditorChoice = function(settingKey,localeName,type) {
    var  dialogBody = '';
    dialogBody += '<div>You can use either the HTML or plaintext editor to modify the HTML email body.</div>';
    dialogBody += '<div class="buttonbar"><button class="btn" id="btn-editor-plain">Plain</button>';
    dialogBody += '<button class="btn" id="btn-editor-html">HTML</button></div>';

    var addEventHandlers = function(){
        PWM_MAIN.addEventHandler('btn-editor-plain','click',function(){ EmailTableHandler.editor(settingKey,localeName,true,type); });
        PWM_MAIN.addEventHandler('btn-editor-html','click',function(){ EmailTableHandler.htmlBodyEditor(settingKey,localeName); });
    };

    PWM_MAIN.showDialog({
        title: "HTML Editor Choice",
        text: dialogBody,
        showClose: true,
        showOk: false,
        loadFunction: addEventHandlers
    });
};


EmailTableHandler.editor = function(settingKey, localeName, drawTextArea, type, instructions){
    var settingData = PWM_SETTINGS['settings'][settingKey];
    UILibrary.stringEditorDialog({
        title:'Edit Value - ' + settingData['label'],
        instructions: instructions ? instructions : '',
        textarea:drawTextArea,
        value:PWM_VAR['clientSettingCache'][settingKey][localeName][type],
        completeFunction:function(value){
            PWM_VAR['clientSettingCache'][settingKey][localeName][type] = value;
            PWM_CFGEDIT.writeSetting(settingKey,PWM_VAR['clientSettingCache'][settingKey],function(){
                EmailTableHandler.init(settingKey);
            });
        }
    });
};


EmailTableHandler.htmlBodyEditor = function(keyName, localeName) {
    // Grab the scope from the angular controller we created on the div element with ID: centerbody-config
    var $scope = angular.element(document.getElementById("centerbody-config")).scope();
    var idValue = keyName + "_" + localeName + "_htmlEditor";
    var toolbarButtons =
        "[" +
        "['h1','h2','h3','h4','h5','h6','p','pre','quote']," +
        "['bold','italics','underline','strikeThrough','ul','ol','undo','redo','clear']," +
        "['justifyLeft','justifyCenter','justifyRight','justifyFull','indent','outdent']," +
        "['html','insertImage','insertLink','insertVideo']" +
        "]";

    PWM_MAIN.showDialog({
        title: "HTML Editor",
        text: '<div id="' + idValue + '" text-angular ng-model="htmlText" ta-toolbar="' + toolbarButtons + '" class="html-editor"></div>',
        showClose:true,
        showCancel:true,
        dialogClass: 'wide',
        loadFunction: function(){
            // Put the existing value into the scope, and tell the controller to process the element with ID: idValue
            $scope.htmlText =  PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'];
            $scope.$broadcast("content-added", idValue);
        },
        okAction:function(){
            PWM_VAR['clientSettingCache'][keyName][localeName]['bodyHtml'] = $scope.htmlText;
            EmailTableHandler.writeSetting(keyName,true);
        }
    });
};


EmailTableHandler.writeSetting = function(settingKey, redraw) {
    var currentValues = PWM_VAR['clientSettingCache'][settingKey];
    PWM_CFGEDIT.writeSetting(settingKey, currentValues, function(){
        if (redraw) {
            EmailTableHandler.init(settingKey);
        }
    });
};
