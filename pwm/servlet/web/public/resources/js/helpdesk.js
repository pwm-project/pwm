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

var PWM_HELPDESK = PWM_HELPDESK || {};
var PWM_VAR = PWM_VAR || {};

PWM_HELPDESK.executeAction = function(actionName) {
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        require(["dojo", "dijit/Dialog"], function (dojo) {
            dojo.xhrGet({
                url: "Helpdesk?pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&processAction=executeAction&name=" + actionName,
                preventCache: true,
                dataType: "json",
                handleAs: "json",
                timeout: PWM_MAIN.ajaxTimeout,
                load: function (data) {
                    PWM_MAIN.closeWaitDialog();
                    if (data['error'] == true) {
                        PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Error'), text: data['errorDetail']});
                    } else {
                        PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Success'), text: data['successMessage'], nextAction: function () {
                            PWM_MAIN.getObject('continueForm').submit();
                        }});
                    }
                },
                error: function (errorObj) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showError('error executing action: ' + errorObj);
                }
            });
        });
    }});
};

PWM_HELPDESK.doResponseClear = function() {
    var username = PWM_VAR['helpdesk_obfuscatedDN'];
    require(["dojo","dijit/Dialog"],function(dojo){
        PWM_MAIN.closeWaitDialog();
        PWM_MAIN.showWaitDialog({loadFunction:function() {
            var inputValues = { 'username': username };
            dojo.xhrDelete({
                url: PWM_GLOBAL['url-restservice'] + "/challenges",
                headers: {"Accept": "application/json", "X-RestClientKey": PWM_GLOBAL['restClientKey']},
                content: inputValues,
                preventCache: true,
                timeout: PWM_MAIN.ajaxTimeout,
                sync: false,
                handleAs: "json",
                load: function (results) {
                    var bodyText = "";
                    if (results['error'] != true) {
                        bodyText += results['successMessage'];
                    } else {
                        bodyText += results['errorMessage'];
                    }
                    bodyText += '<br/><br/><button class="btn" onclick="PWM_MAIN.getObject(\'continueForm\').submit();"> OK </button>';
                    PWM_MAIN.closeWaitDialog();
                    var theDialog = new dijit.Dialog({
                        id: 'dialogPopup',
                        title: 'Clear Responses',
                        style: "width: 450px",
                        content: bodyText,
                        closable: false,
                        hide: function () {
                            PWM_MAIN.clearDijitWidget('result-popup');
                        }
                    });
                    theDialog.show();
                },
                error: function (errorObj) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showError("unexpected clear responses error: " + errorObj);
                }
            });
        }});
    });
};

PWM_HELPDESK.doPasswordChange = function(password, random) {
    var inputValues = {};
    inputValues['username'] = PWM_VAR['helpdesk_obfuscatedDN'];
    if (random) {
        inputValues['random'] = true;
    } else {
        inputValues['password'] = password;
    }
    var htmlBody = PWM_MAIN.showString('Field_NewPassword') + ': <b>' + password + '</b><br/><br/><br/><div id="WaitDialogBlank"/>';
    PWM_MAIN.showWaitDialog({text:htmlBody,loadFunction:function() {
        require(["dojo", "dijit/Dialog"], function (dojo, Dialog) {
            dojo.xhrPost({
                url: PWM_GLOBAL['url-restservice'] + "/setpassword",
                headers: {"Accept": "application/json", "X-RestClientKey": PWM_GLOBAL['restClientKey']},
                content: inputValues,
                preventCache: true,
                timeout: PWM_MAIN.ajaxTimeout,
                sync: false,
                handleAs: "json",
                load: function (results) {
                    var bodyText = "";
                    if (results['error'] == true) {
                        bodyText += results['errorMessage'];
                        bodyText += '<br/><br/>';
                        bodyText += results['errorDetail'];
                    } else {
                        bodyText += '<br/>';
                        bodyText += results['successMessage'];
                        bodyText += '</br></br>';
                        bodyText += PWM_MAIN.showString('Field_NewPassword') + ': <b>' + password + '</b>';
                        bodyText += '<br/>';
                    }
                    bodyText += '<br/><br/><button class="btn" onclick="PWM_MAIN.getObject(\'continueForm\').submit();"> OK </button>';
                    if (PWM_VAR['helpdesk_setting_clearResponses'] == 'ask') {
                        bodyText += '<span style="padding-left: 10px">&nbsp;</span>';
                        bodyText += '<button class="btn" onclick="PWM_HELPDESK.doResponseClear()">';
                        bodyText += 'Clear Responses</button>';
                    }
                    PWM_MAIN.closeWaitDialog();
                    var theDialog = new Dialog({
                        id: 'dialogPopup',
                        title: PWM_MAIN.showString('Title_ChangePassword') + ': ' + PWM_VAR['helpdesk_username'],
                        style: "width: 450px",
                        content: bodyText,
                        closable: false,
                        hide: function () {
                            PWM_MAIN.closeWaitDialog();
                        }
                    });
                    theDialog.show();
                },
                error: function (errorObj) {
                    PWM_MAIN.closeWaitDialog();
                    PWM_MAIN.showError("unexpected set password error: " + errorObj);
                }
            });
        });
    }});
};

PWM_HELPDESK.generatePasswordPopup = function() {
    var dataInput = {};
    dataInput['username'] = PWM_VAR['helpdesk_obfuscatedDN'];
    dataInput['strength'] = 0;

    var randomConfig = {};
    randomConfig['dataInput'] = dataInput;
    randomConfig['finishAction'] = "PWM_MAIN.clearDijitWidget('randomPasswordDialog');PWM_HELPDESK.doPasswordChange(PWM_GLOBAL['SelectedRandomPassword'])";
    PWM_CHANGEPW.doRandomGeneration(randomConfig);
};

PWM_HELPDESK.changePasswordPopup = function() {
    require(["dijit/Dialog"],function(){
        var bodyText = '<span id="message" class="message message-info" style="width: 400">' + PWM_MAIN.showString('Field_NewPassword') + '</span>';
        if (PWM_VAR['helpdesk_setting_PwUiMode'] == 'both') {
            bodyText += '<p>&nbsp;»&nbsp; <a href="#" onclick="PWM_MAIN.clearDijitWidget(\'changepassword-popup\');PWM_HELPDESK.generatePasswordPopup();">' + PWM_MAIN.showString('Display_AutoGeneratedPassword') + '</a></p>';
        }
        bodyText += '<table style="border: 0">';
        bodyText += '<tr style="border: 0"><td style="border: 0"><input type="text" name="password1" id="password1" class="inputfield" style="width: 260px" autocomplete="off" onkeyup="PWM_CHANGEPW.validatePasswords(\'' + PWM_VAR['helpdesk_obfuscatedDN'] + '\');"/></td>';
        if (PWM_GLOBAL['setting-showStrengthMeter']) {
            bodyText += '<td style="border:0"><div id="strengthBox" style="visibility:hidden;">';
            bodyText += '<div id="strengthLabel">' + PWM_MAIN.showString('Display_StrengthMeter') + '</div>';
            bodyText += '<div class="progress-container" style="margin-bottom:10px">';
            bodyText += '<div id="strengthBar" style="width:0">&nbsp;</div></div></div></td>';
        }
        bodyText += '</tr><tr style="border: 0">';
        bodyText += '<td style="border: 0"><input type="text" name="password2" id="password2" class="inputfield" style="width: 260px" autocomplete="off" onkeyup="PWM_CHANGEPW.validatePasswords(\'' + PWM_VAR['helpdesk_obfuscatedDN'] + '\');""/></td>';

        bodyText += '<td style="border: 0"><div style="margin:0;">';
        bodyText += '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/greenCheck.png">';
        bodyText += '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png">';
        bodyText += '</div></td>';

        bodyText += '</tr></table>';
        bodyText += '<button name="change" class="btn" id="password_button" onclick="var pw=PWM_MAIN.getObject(\'password1\').value;PWM_MAIN.clearDijitWidget(\'changepassword-popup\');PWM_HELPDESK.doPasswordChange(pw)" disabled="true"/>' + PWM_MAIN.showString('Button_ChangePassword') + '</button>';
        try { PWM_MAIN.getObject('message').id = "base-message"; } catch (e) {}

        PWM_MAIN.clearDijitWidget('changepassword-popup');
        var theDialog = new dijit.Dialog({
            id: 'dialogPopup',
            title: PWM_MAIN.showString('Title_ChangePassword') + ': ' + PWM_VAR['helpdesk_username'],
            style: "width: 450px",
            content: bodyText,
            hide: function(){
                PWM_MAIN.closeWaitDialog();
                PWM_MAIN.getObject('base-message').id = "message";
            }
        });
        theDialog.show();
        setTimeout(function(){ PWM_MAIN.getObject('password1').focus();},500);
    });
};

PWM_HELPDESK.setRandomPasswordPopup = function() {
    var titleText = PWM_MAIN.showString('Title_ChangePassword') + ': ' + PWM_VAR['helpdesk_username'];
    var body = PWM_MAIN.showString('Display_SetRandomPasswordPrompt');
    var yesAction = function() {
        PWM_HELPDESK.doPasswordChange('[' + PWM_MAIN.showString('Display_Random') +  ']',true);
    };
    PWM_MAIN.showConfirmDialog({title:titleText,text:body,okAction:yesAction});
};

PWM_HELPDESK.loadSearchDetails = function(userKey) {
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        setTimeout(function () {
            PWM_MAIN.getObject("userKey").value = userKey;
            PWM_MAIN.getObject("loadDetailsForm").submit();
        }, 10);
    }});
};

PWM_HELPDESK.processHelpdeskSearch = function() {
    var validationProps = {};
    validationProps['serviceURL'] = "Helpdesk?processAction=search";
    validationProps['showMessage'] = false;
    validationProps['ajaxTimeout'] = 120 * 1000;
    validationProps['usernameField'] = PWM_MAIN.getObject('username').value;
    validationProps['readDataFunction'] = function(){
        PWM_MAIN.getObject('searchIndicator').style.visibility = 'visible';
        return { username:PWM_MAIN.getObject('username').value }
    };
    validationProps['completeFunction'] = function() {
        PWM_MAIN.getObject('searchIndicator').style.visibility = 'hidden';
    };
    validationProps['processResultsFunction'] = function(data) {
        var grid = PWM_VAR['heldesk_search_grid'];
        if (data['error']) {
            PWM_MAIN.showError("error: " + data['errorMessage']);
            grid.refresh();
        } else {
            var gridData = data['data']['searchResults'];
            var sizeExceeded = data['data']['sizeExceeded'];
            grid.refresh();
            grid.renderArray(gridData);
            grid.on(".dgrid-row .dgrid-cell:click", function(evt){
                var row = grid.row(evt);
                PWM_HELPDESK.loadSearchDetails(row.data['userKey']);
            });
            grid.set("sort",1);
            if (sizeExceeded) {
                PWM_MAIN.getObject('maxResultsIndicator').style.visibility = 'visible';
                PWM_MAIN.showTooltip({id:'maxResultsIndicator',position:'below',text:PWM_MAIN.showString('Display_SearchResultsExceeded')})
            } else {
                PWM_MAIN.getObject('maxResultsIndicator').style.visibility = 'hidden';
            }
        }
    };
    PWM_MAIN.pwmFormValidator(validationProps);
    PWM_MAIN.getObject('maxResultsIndicator').style.visibility = 'hidden';
};

PWM_HELPDESK.makeSearchGrid = function(nextAction) {
    require(["dojo/domReady!"],function(){
        require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
            function(dojo,declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){

                var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                PWM_VAR['heldesk_search_grid'] = new CustomGrid({
                    columns: PWM_VAR['helpdesk_search_columns']
                }, "grid");

                if (nextAction) {
                    nextAction();
                }
            });
    });
};

PWM_HELPDESK.initHelpdeskSearchPage = function() {
    PWM_HELPDESK.makeSearchGrid(function(){
        require(["dojo/dom-construct", "dojo/on"], function(domConstruct, on){
            on(PWM_MAIN.getObject('username'), "keyup, input", function(){
                PWM_HELPDESK.processHelpdeskSearch();
            });

            if (PWM_MAIN.getObject('username').value) {
                PWM_MAIN.getObject('username').focus();
                PWM_MAIN.getObject('username').select();
            }

            console.log('yomama!');
            PWM_HELPDESK.processHelpdeskSearch();
        });
    });
};
