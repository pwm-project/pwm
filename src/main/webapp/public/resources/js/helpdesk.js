/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
    var body = PWM_VAR['actions'][actionName]['description'];
    body += "<br/><br/>" + PWM_MAIN.showString('Confirm');
    PWM_MAIN.showConfirmDialog({
        title:PWM_MAIN.showString('Button_Confirm') + " " + actionName,
        text:body,
        okAction:function(){
            var inputValues = {};
            inputValues['userKey'] = PWM_VAR['helpdesk_obfuscatedDN'];
            PWM_MAIN.showWaitDialog({loadFunction:function() {
                var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "executeAction");
                url = PWM_MAIN.addParamToUrl(url, "name", actionName);
                var loadFunction = function(data) {
                    PWM_MAIN.closeWaitDialog();
                    if (data['error'] == true) {
                        PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Error'), text: data['errorDetail']});
                    } else {
                        PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Success'), text: data['successMessage'], nextAction: function () {
                            PWM_HELPDESK.refreshDetailPage();
                        }});
                    }
                };
                PWM_MAIN.ajaxRequest(url,loadFunction,{content:inputValues});
            }});
        }
    });
};

PWM_HELPDESK.doResponseClear = function() {
    var username = PWM_VAR['helpdesk_obfuscatedDN'];
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        var url = PWM_GLOBAL['url-restservice'] + "/challenges?username=" + username;
        var loadFunction = function(results) {
            if (results['error'] != true) {
                PWM_MAIN.showDialog({
                    title: PWM_MAIN.showString('Button_ClearResponses'),
                    text: results['successMessage'],
                    okAction:function(){
                        PWM_HELPDESK.refreshDetailPage();
                    }
                });
            } else {
                PWM_MAIN.showErrorDialog(results);
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{method:'DELETE'});
    }});
};

PWM_HELPDESK.doPasswordChange = function(password, random) {
    var inputValues = {};
    inputValues['username'] = PWM_VAR['helpdesk_obfuscatedDN'];
    if (random) {
        inputValues['random'] = true;
    } else {
        inputValues['password'] = password;
    }
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        var url = PWM_GLOBAL['url-restservice'] + "/setpassword";
        var loadFunction = function(results) {
            var bodyText = "";
            if (results['error'] == true) {
                bodyText += results['errorMessage'];
                if (results['errorMessage']) {
                    bodyText += '<br/><br/>';
                    bodyText += results['errorDetail'];
                }
            } else {
                bodyText += '<br/>';
                bodyText += results['successMessage'];
                bodyText += '</br></br>';
                bodyText += PWM_MAIN.showString('Field_NewPassword');

                if (PWM_VAR['helpdesk_setting_maskPasswords']) {
                    bodyText += '<button id="button-password-display" class="btn"><span class="btn-icon pwm-icon pwm-icon-eye"></span>' + PWM_MAIN.showString('Button_Show') + '</button>';
                    bodyText += ' <input id="panel-password-display" style="display:none" class="inputfield" value="' + password + '" readonly/>';
                } else {
                    bodyText += ' <input class="inputfield" value="' + password + '" readonly/>';
                }

                bodyText += '<br/>';
            }
            bodyText += '<br/><br/><button class="btn" id="button-continue">'
                + '<span class="btn-icon pwm-icon pwm-icon-forward"></span>' + PWM_MAIN.showString('Button_OK') + '</button>';
            if (PWM_VAR['helpdesk_setting_clearResponses'] == 'ask') {
                bodyText += '<span style="padding-left: 10px">&nbsp;</span>';
                bodyText += '<button class="btn" id="button-clearResponses">';
                bodyText += '<span class="btn-icon pwm-icon pwm-icon-eraser"></span>' + PWM_MAIN.showString('Button_ClearResponses') + '</button>';
            }
            PWM_MAIN.closeWaitDialog();
            PWM_MAIN.showDialog({
                showOk: false,
                showClose: true,
                allowMove: true,
                id: 'dialogPopup',
                title: PWM_MAIN.showString('Title_ChangePassword') + ' - ' + PWM_VAR['helpdesk_username'],
                text: bodyText,
                loadFunction:function(){
                    PWM_MAIN.addEventHandler('button-continue','click',function(){ PWM_HELPDESK.refreshDetailPage(); });
                    PWM_MAIN.addEventHandler('button-clearResponses','click',function(){ PWM_HELPDESK.doResponseClear(); });

                    if (PWM_VAR['helpdesk_setting_maskPasswords']) {
                        PWM_MAIN.addEventHandler('button-password-display','click',function(){
                            var buttonElement = PWM_MAIN.getObject('button-password-display');
                            buttonElement.parentNode.removeChild(buttonElement);
                            PWM_MAIN.getObject('panel-password-display').style.display = 'inline';
                        });
                    }

                }
            });
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{content:inputValues});
    }});
};

PWM_HELPDESK.generatePasswordPopup = function() {
    var dataInput = {};
    dataInput['username'] = PWM_VAR['helpdesk_obfuscatedDN'];
    dataInput['strength'] = 0;

    var randomConfig = {};
    randomConfig['dataInput'] = dataInput;
    randomConfig['finishAction'] = function(password){
        PWM_MAIN.clearDijitWidget('randomPasswordDialog');
        PWM_HELPDESK.doPasswordChange(password);
    };
    PWM_CHANGEPW.doRandomGeneration(randomConfig);
};

PWM_HELPDESK.changePasswordPopup = function() {
    var bodyText = '';
    bodyText += '<span id="message" class="message message-info" style="width: 400px">' + PWM_MAIN.showString('Display_PasswordPrompt') + '</span>';
    bodyText += '<table style="border: 0"><tr style="border: 0"><td style="border: 0">';
    if (PWM_VAR['helpdesk_setting_maskPasswords']) {
        bodyText += '<input type="password" name="password1" id="password1" class="passwordfield" style="width: 260px" autocomplete="off"/>';
    } else {
        bodyText += '<input type="text" name="password1" id="password1" class="inputfield" style="width: 260px" autocomplete="off"/>';
    }
    bodyText += '</td>';
    if (PWM_GLOBAL['setting-showStrengthMeter']) {
        bodyText += '<td style="border:0"><div id="strengthBox" style="visibility:hidden;">';
        bodyText += '<div id="strengthLabel">' + PWM_MAIN.showString('Display_StrengthMeter') + '</div>';
        bodyText += '<div class="progress-container" style="margin-bottom:10px">';
        bodyText += '<div id="strengthBar" style="width:0">&nbsp;</div></div></div></td>';
    }
    bodyText += '</tr><tr style="border: 0">';

    bodyText += '<td style="border: 0">';
    if (PWM_VAR['helpdesk_setting_maskPasswords']) {
        bodyText += '<input type="password" name="password2" id="password2" class="passwordfield" style="width: 260px" autocomplete="off"/>';
    } else {
        bodyText += '<input type="text" name="password2" id="password2" class="inputfield" style="width: 260px" autocomplete="off"/>';
    }
    bodyText += '</td>';

    bodyText += '<td style="border: 0"><div style="margin:0;">';
    bodyText += '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/greenCheck.png">';
    bodyText += '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="' + PWM_GLOBAL['url-resources'] + '/redX.png">';
    bodyText += '</div></td>';

    bodyText += '</tr></table><br/>';
    bodyText += '<button name="change" class="btn" id="password_button" disabled="true"><span class="btn-icon pwm-icon pwm-icon-key"></span>' + PWM_MAIN.showString('Button_ChangePassword') + '</button>';
    if (PWM_VAR['helpdesk_setting_PwUiMode'] == 'both') {
        bodyText += '<button name="random" class="btn" id="button-autoGeneratePassword"><span class="btn-icon pwm-icon pwm-icon-retweet"></span>' + PWM_MAIN.showString('Title_RandomPasswords') + '</button>';
    }

    try { PWM_MAIN.getObject('message').id = "base-message"; } catch (e) {}

    PWM_MAIN.showDialog({
        title: PWM_MAIN.showString('Title_ChangePassword') + ' - ' + PWM_VAR['helpdesk_username'],
        text: bodyText,
        showOk: false,
        showClose: true,
        allowMove: true,
        loadFunction: function(){
            setTimeout(function(){ PWM_MAIN.getObject('password1').focus();},500);
            PWM_MAIN.addEventHandler('password1','input',function(){
                PWM_CHANGEPW.validatePasswords(PWM_VAR['helpdesk_obfuscatedDN']);
            });
            PWM_MAIN.addEventHandler('password2','input',function(){
                PWM_CHANGEPW.validatePasswords(PWM_VAR['helpdesk_obfuscatedDN']);
            });
            if (PWM_VAR['helpdesk_setting_maskPasswords']) {
                ShowHidePasswordHandler.init('password1');
                ShowHidePasswordHandler.init('password2');
            }

            PWM_MAIN.addEventHandler('password_button','click',function(){
                var pw=PWM_MAIN.getObject('password1').value;
                PWM_HELPDESK.doPasswordChange(pw);
            });
            PWM_MAIN.addEventHandler('button-autoGeneratePassword','click',function(){
                PWM_HELPDESK.generatePasswordPopup();
            })
        }
    });
};

PWM_HELPDESK.initiateChangePasswordDialog = function() {
    if (PWM_VAR['helpdesk_setting_PwUiMode'] == 'autogen') {
        PWM_HELPDESK.generatePasswordPopup();
    } else if (PWM_VAR['helpdesk_setting_PwUiMode'] == 'random') {
        PWM_HELPDESK.setRandomPasswordPopup();
    } else {
        PWM_HELPDESK.changePasswordPopup();
    }
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
    var gotoDetailFunction = function(userKey) {
        PWM_MAIN.showWaitDialog({loadFunction:function() {
            var contents = {};
            contents['userKey'] = userKey;
            if (PWM_MAIN.Preferences.readSessionStorage(PREF_KEY_VERIFICATION_STATE)) {
                contents[PARAM_VERIFICATION_STATE] = PWM_MAIN.Preferences.readSessionStorage(PREF_KEY_VERIFICATION_STATE);
            }
            PWM_MAIN.submitPostAction(window.location.href,'detail',contents);
        }});
    };

    var handleVerificationResult = function(data) {
        if (data['error']) {
            PWM_MAIN.showErrorDialog(data);
        } else {
            if (data['data']['passed']) {
                gotoDetailFunction(userKey);
            } else {
                var verificationMethods = PWM_VAR['verificationMethods']['required'];
                PWM_HELPDESK.sendVerificationToken(userKey,verificationMethods);
            }
        }
    };

    var checkVerificationFunction = function() {
        var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "checkVerification");
        var content = {};
        content['userKey'] = userKey;
        content[PARAM_VERIFICATION_STATE] = PWM_MAIN.Preferences.readSessionStorage(PREF_KEY_VERIFICATION_STATE);
        PWM_MAIN.ajaxRequest(url, handleVerificationResult, {content:content});
    };


    PWM_MAIN.showWaitDialog({loadFunction:checkVerificationFunction});
};

PWM_HELPDESK.showRecentVerifications = function() {
    var handleVerificationResult = function(data) {
        if (data['error']) {
            PWM_MAIN.showErrorDialog(data);
            return;
        } else {
            var records = data['data']['records'];
            var html = '';
            if (PWM_MAIN.JSLibrary.isEmpty(records)) {
                html += PWM_MAIN.showString('Display_SearchResultsNone');
            } else {
                html += '<table>';
                html += '<tr><td class="title">' + PWM_MAIN.showString('Field_LdapProfile') + '</td><td class="title">'
                    + PWM_MAIN.showString('Field_Username') + '</td><td class="title">'
                    + PWM_MAIN.showString('Field_DateTime') + '</td><td class="title">'
                    + PWM_MAIN.showString('Field_Method') + '</td>';
                for (var i in records) {
                    var record = records[i];
                    html += '<tr>';
                    html += '<td>' + record['profile'] + '</td>';
                    html += '<td>' + record['username'] + '</td>';
                    html += '<td class="timestamp">' + record['timestamp'] + '</td>';
                    html += '<td>' + record['method'] + '</td>';
                    html += '</tr>';
                }
            }

            html += '</table>';
            PWM_MAIN.showDialog({'title':PWM_MAIN.showString('Title_RecentVerifications'),'text':html,loadFunction:function(){PWM_MAIN.TimestampHandler.initAllElements()}});
        }
    };

    var loadVerificationsFunction = function() {
        var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "showVerifications");
        var content = {};
        content[PARAM_VERIFICATION_STATE] = PWM_MAIN.Preferences.readSessionStorage(PREF_KEY_VERIFICATION_STATE);
        PWM_MAIN.ajaxRequest(url, handleVerificationResult, {content:content});
    };


    PWM_MAIN.showWaitDialog({loadFunction:loadVerificationsFunction});
};

PWM_HELPDESK.processHelpdeskSearch = function() {
    var validationProps = {};
    validationProps['serviceURL'] = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "search");
    validationProps['showMessage'] = false;
    validationProps['ajaxTimeout'] = 120 * 1000;
    validationProps['usernameField'] = PWM_MAIN.getObject('username').value;
    validationProps['readDataFunction'] = function(){
        PWM_MAIN.getObject('searchIndicator').style.display = 'inherit';
        return { username:PWM_MAIN.getObject('username').value }
    };
    validationProps['completeFunction'] = function() {
        PWM_MAIN.getObject('searchIndicator').style.display = 'none';
    };
    validationProps['processResultsFunction'] = function(data) {
        var grid = PWM_VAR['heldesk_search_grid'];
        if (data['error']) {
            PWM_MAIN.showErrorDialog(data);
            grid.refresh();
        } else {
            var gridData = data['data']['searchResults'];
            var sizeExceeded = data['data']['sizeExceeded'];
            grid.refresh();
            grid.renderArray(gridData);
            var sortState = grid.get("sort");
            grid.set("sort", sortState);

            if (sizeExceeded) {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'inherit';
                PWM_MAIN.showTooltip({id:'maxResultsIndicator',position:'below',text:PWM_MAIN.showString('Display_SearchResultsExceeded')})
            } else if (PWM_MAIN.JSLibrary.isEmpty(data['data']['searchResults']) && validationProps['usernameField'].length > 0) {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'inherit';
                PWM_MAIN.showTooltip({
                    id: 'maxResultsIndicator',
                    position: 'below',
                    text: PWM_MAIN.showString('Display_SearchResultsNone')
                })
            } else {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'none';
            }
        }
    };
    PWM_MAIN.pwmFormValidator(validationProps);
    PWM_MAIN.getObject('maxResultsIndicator').style.display = 'none';
};

PWM_HELPDESK.makeSearchGrid = function(nextAction) {
    require(["dojo/domReady!"],function(){
        require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
            function(dojo,declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){

                var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                PWM_VAR['heldesk_search_grid'] = new CustomGrid({
                    columns: PWM_VAR['helpdesk_search_columns']
                }, "helpdesk-searchResultsGrid");

                if (nextAction) {
                    nextAction();
                }

                PWM_VAR['heldesk_search_grid'].on(".dgrid-row:click", function(evt){
                    PWM_MAIN.stopEvent(evt);
                    var row = PWM_VAR['heldesk_search_grid'].row(evt);
                    PWM_HELPDESK.loadSearchDetails(row.data['userKey']);
                });

            });
    });
};

PWM_HELPDESK.deleteUser = function() {
    PWM_MAIN.showConfirmDialog({
        text:PWM_MAIN.showString('Confirm_DeleteUser'),
        okAction:function(){
            var url = "Helpdesk?processAction=deleteUser&userKey=" + PWM_VAR['helpdesk_obfuscatedDN'];
            var loadFunction = function(data) {
                PWM_MAIN.closeWaitDialog();
                if (data['error'] == true) {
                    PWM_MAIN.showErrorDialog(error);
                } else {
                    PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Success'), text: data['successMessage'], okAction: function () {
                        PWM_MAIN.goto("/private/helpdesk");
                    }});
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction);
        }
    })
};

PWM_HELPDESK.validateOtpCode = function(userKey) {
    var dialogText = PWM_MAIN.showString('Display_HelpdeskOtpValidation');
    PWM_HELPDESK.validateCode({userKey:userKey, processAction:'validateOtpCode', dialogText:dialogText});
};

var PARAM_VERIFICATION_STATE = 'verificationState';
var PREF_KEY_VERIFICATION_STATE = 'verificationState';

PWM_HELPDESK.validateCode = function(options) {
    options = options === undefined ? {} : options;

    var userKey = options['userKey'];
    var processAction = options['processAction'];
    var dialogText = options['dialogText'];
    var extraPayload = options['extraPayload'];
    var showInputField = 'showInputField' in options ? options['showInputField '] : true;

    var validateOtpCodeFunction = function(){
        PWM_MAIN.getObject('icon-working').style.display = 'inherit';
        PWM_MAIN.getObject('icon-cross').style.display = 'none';
        PWM_MAIN.getObject('icon-check').style.display = 'none';
        var content = extraPayload === undefined ? {} : extraPayload();

        content['userKey'] = userKey;
        if (PWM_MAIN.getObject('code')) {
            content['code'] = PWM_MAIN.getObject('code').value;
        }
        content[PARAM_VERIFICATION_STATE] = PWM_MAIN.Preferences.readSessionStorage(PREF_KEY_VERIFICATION_STATE);
        var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", processAction);
        var loadFunction = function(data) {
            PWM_MAIN.getObject('icon-working').style.display = 'none';

            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
                return;
            }

            var verificationState = data['data'][PARAM_VERIFICATION_STATE];
            PWM_MAIN.Preferences.writeSessionStorage(PREF_KEY_VERIFICATION_STATE,verificationState);
            console.log(verificationState);

            var passed =  data['data']['passed'];
            if (passed) {
                PWM_MAIN.getObject('icon-check').style.display = 'inherit';
                PWM_MAIN.getObject('dialog_ok_button').disabled = false;
                PWM_MAIN.getObject('button-checkCode').disabled = true;
            } else {
                PWM_MAIN.getObject('icon-cross').style.display = 'inherit';
                if (PWM_MAIN.getObject('code')) {
                    PWM_MAIN.getObject('code').value = '';
                    PWM_MAIN.getObject('code').focus();
                }
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{content:content});
    };

    var text = '<div>' + dialogText + '</div><br/><div><table class="noborder"><tr>';
    if (showInputField) {
        text += '<td style="width: 100px"><input style="width: 100px" id="code" name="code"/></td>';
    }

    text += '<td style="width:40px"><span style="display:none;color:green" id="icon-check" class="btn-icon pwm-icon pwm-icon-lg pwm-icon-check"></span>'
        + '<span style="display:none;color:red" id="icon-cross" class="btn-icon pwm-icon pwm-icon-lg pwm-icon-times"></span>'
        + '<span style="display:none;" id="icon-working" class="pwm-icon pwm-icon-lg pwm-icon-spin pwm-icon-spinner"></span></td><td>'
        + '<button type="button" class="btn" id="button-checkCode"><span class="btn-icon pwm-icon pwm-icon-check"></span>' + PWM_MAIN.showString('Button_Verify') + '</button>'
        + '</tr></td></table></div>';

    var successFunction = function() {
        if (PWM_MAIN.getObject('application-info').getAttribute('data-jsp-name') == 'helpdesk.jsp') {
            PWM_HELPDESK.loadSearchDetails(userKey);
        }
    };
    PWM_MAIN.showDialog({
        showClose:true,
        allowMove:true,
        title:PWM_MAIN.showString('Title_ValidateCode'),
        text:text,
        loadFunction:function(){
            PWM_MAIN.addEventHandler('button-checkCode','click',function(){
                validateOtpCodeFunction();
            });
            PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        },
        okAction:successFunction
    });
};

PWM_HELPDESK.sendVerificationToken = function(userKey, methods) {
    var sendMethodSetting = PWM_VAR["helpdesk_setting_tokenSendMethod"];
    var tokenChoiceFlag = sendMethodSetting == 'CHOICE_SMS_EMAIL';

    var sendTokenAction = function(choice) {
        var sendContent = {};
        sendContent['userKey'] = userKey;
        if (tokenChoiceFlag && choice) {
            sendContent['method'] = choice;
        }
        PWM_MAIN.showWaitDialog({loadFunction:function(){
            var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "sendVerificationToken");
            var loadFunction = function(data) {
                if (!data['error']) {
                    var text = '<table><tr><td>' + PWM_MAIN.showString('Display_TokenDestination') + '</td><td>' + data['data']['destination'] + '</td></tr></table>';
                    var returnExtraData = function() { return data['data']; };
                    PWM_HELPDESK.validateCode({userKey:userKey, processAction:'verifyVerificationToken',dialogText:text,extraPayload:returnExtraData});
                } else {
                    PWM_MAIN.showErrorDialog(data);
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction,{content:sendContent});
        }});
    };

    var dialogText = '<div>' + PWM_MAIN.showString('Long_Title_VerificationSend') + "</div><br/>";
    dialogText += '<div style="text-align:center"><br/>';
    if (PWM_MAIN.JSLibrary.arrayContains(methods,'ATTRIBUTES')) {
        dialogText += '<br/><br/><button class="btn" type="button" name="attributesChoiceButton" id="attributesChoiceButton">'
            + '<span class="btn-icon pwm-icon pwm-icon-database "></span>' + PWM_MAIN.showString('Button_Attributes') + '</button><br/>';
    }
    if (PWM_MAIN.JSLibrary.arrayContains(methods,'TOKEN')) {
        if (tokenChoiceFlag || sendMethodSetting == 'EMAILONLY') {
            dialogText += '<br/><button class="btn" type="button" name="emailChoiceButton" id="emailChoiceButton">'
                + '<span class="btn-icon pwm-icon pwm-icon-envelope-o"></span>' + PWM_MAIN.showString('Button_Email') + '</button>';
        }
        if (tokenChoiceFlag || sendMethodSetting == 'EMAILONLY') {
            dialogText += '<br/><br/><button class="btn" type="button" name="smsChoiceButton" id="smsChoiceButton">'
                + '<span class="btn-icon pwm-icon pwm-icon-phone"></span>' + PWM_MAIN.showString('Button_SMS') + '</button>';
        }
    }
    if (PWM_MAIN.JSLibrary.arrayContains(methods,'OTP')) {
        dialogText += '<br/><br/><button class="btn" type="button" name="otpChoiceButton" id="otpChoiceButton">'
            + '<span class="btn-icon pwm-icon pwm-icon-qrcode"></span>' + PWM_MAIN.showString('Button_OTP') + '</button>';
    }
    dialogText += '</div>';

    var dialogLoadFunction = function() {
        PWM_MAIN.addEventHandler('attributesChoiceButton','click',function(){PWM_HELPDESK.validateAttributes(userKey)});
        PWM_MAIN.addEventHandler('emailChoiceButton','click',function(){sendTokenAction('email')});
        PWM_MAIN.addEventHandler('smsChoiceButton','click',function(){sendTokenAction('sms')});
        PWM_MAIN.addEventHandler('otpChoiceButton','click',function(){PWM_HELPDESK.validateOtpCode(userKey)});
    };
    PWM_MAIN.showConfirmDialog({
        title:PWM_MAIN.showString('Title_VerificationSend'),
        text:dialogText,
        showOk:false,
        showCancel:false,
        showClose: true,
        okAction:function(){
            sendTokenAction();
        },
        loadFunction:dialogLoadFunction
    });
};

PWM_HELPDESK.validateAttributes = function(userKey) {
    var bodyText = '';
    bodyText += '<div><table>';
    var formItems = PWM_VAR['verificationForm'];

    for (var i in formItems) {
        var name = formItems[i]['name'];
        var label = formItems[i]['label'];
        bodyText += '<tr><td>' + label + '</td><td>' + '<input id="input-' + name + '"/>' + '</td></tr>';
    }
    bodyText += '</table></div>';
    
    var extraData = function() {
        var formData = {};
        for (var i in formItems) {
            (function(item){
                var name = item['name'];
                formData[name] = PWM_MAIN.getObject('input-' + name).value;
            }(formItems[i]));
        }

        return formData;
    };
    
    var options = {
        userKey:userKey,
        processAction:'validateAttributes',
        dialogText:bodyText,
        showInputField:false,
        extraPayload:extraData

    };
    PWM_HELPDESK.validateCode(options);
};

PWM_HELPDESK.initHelpdeskSearchPage = function() {
    PWM_MAIN.addEventHandler('button-show-current-verifications','click',PWM_HELPDESK.showRecentVerifications);
    PWM_HELPDESK.makeSearchGrid(function(){
        PWM_MAIN.addEventHandler('username', "keyup, input", function(){
            PWM_HELPDESK.processHelpdeskSearch();
            try {
                var helpdeskFieldUsername = PWM_MAIN.getObject('username').value;
                PWM_MAIN.Preferences.writeSessionStorage("helpdesk_field_username", helpdeskFieldUsername);
            } catch (e) {
                console.log('error writing username field from sessionStorage: ' + e);
            }
        });

        try {
            var helpdeskFieldUsername = PWM_MAIN.Preferences.readSessionStorage("helpdesk_field_username","");
            PWM_MAIN.getObject('username').value = helpdeskFieldUsername;
        } catch (e) {
            console.log('error reading username field from sessionStorage: ' + e);
        }

        if (PWM_MAIN.getObject('username').value && PWM_MAIN.getObject('username').value.length > 0) {
            PWM_HELPDESK.processHelpdeskSearch();
        }
    });
};

PWM_HELPDESK.initHelpdeskDetailPage = function() {
    require(["dojo/parser","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser){
        dojoParser.parse();
    });
    PWM_MAIN.addEventHandler('button_continue','click',function() {
        PWM_MAIN.goto('helpdesk');
    });
    PWM_MAIN.addEventHandler('button_refresh','click',function(){
        PWM_HELPDESK.refreshDetailPage();
    });
    PWM_MAIN.addEventHandler('helpdesk_ChangePasswordButton','click',function(){
        PWM_HELPDESK.initiateChangePasswordDialog();
    });
    PWM_MAIN.addEventHandler('helpdesk_unlockBtn','click',function(){
        PWM_HELPDESK.unlockIntruder();
    });
    PWM_MAIN.addEventHandler('helpdesk_clearResponsesBtn','click',function(){
        PWM_MAIN.showConfirmDialog({okAction:function(){
            PWM_HELPDESK.doResponseClear();
        }});
    });
    PWM_MAIN.addEventHandler('helpdesk_clearOtpSecretBtn','click',function(){
        PWM_MAIN.showConfirmDialog({okAction:function() {
            PWM_HELPDESK.doOtpClear();
        }});
    });
    PWM_MAIN.addEventHandler('helpdesk_verifyOtpButton','click',function(){
        PWM_HELPDESK.validateOtpCode(PWM_VAR['helpdesk_obfuscatedDN']);
    });
    PWM_MAIN.addEventHandler('sendTokenButton','click',function(){

        var verificationMethods = PWM_VAR['verificationMethods']['optional'];
        PWM_HELPDESK.sendVerificationToken(PWM_VAR['helpdesk_obfuscatedDN'],verificationMethods);
    });
    PWM_MAIN.addEventHandler('helpdesk_deleteUserButton','click',function(){
        PWM_HELPDESK.deleteUser();
    });

};

PWM_HELPDESK.initPage = function() {
    var applicationData = PWM_MAIN.getObject("application-info");
    var jspName = applicationData ? applicationData.getAttribute("data-jsp-name") : "";
    if ("helpdesk.jsp" == jspName || "helpdesk-detail.jsp" == jspName) {
        PWM_MAIN.ajaxRequest("helpdesk?processAction=clientData",function(data){
            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
                return;
            }
            for (var keyName in data['data']) {
                PWM_VAR[keyName] = data['data'][keyName];
            }
            console.log('loaded helpdesk clientData');
            if ("helpdesk.jsp" == jspName) {
                PWM_HELPDESK.initHelpdeskSearchPage();
            }
            if ("helpdesk-detail.jsp" == jspName) {
                PWM_HELPDESK.initHelpdeskDetailPage();
            }
        },{method:"GET"});
    }
};

PWM_HELPDESK.refreshDetailPage = function() {
    var userKey = PWM_VAR['helpdesk_obfuscatedDN'];
    PWM_HELPDESK.loadSearchDetails(userKey);
};

PWM_HELPDESK.unlockIntruder = function() {
    PWM_MAIN.showConfirmDialog({
        title: PWM_MAIN.showString('Button_Unlock'),
        okAction:function() {
            PWM_MAIN.showWaitDialog({
                loadFunction:function(){
                    var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "unlockIntruder");
                    url = PWM_MAIN.addParamToUrl(url, "userKey", PWM_VAR['helpdesk_obfuscatedDN']);
                    var load = function(data) {
                        if (data['error'] == true) {
                            PWM_MAIN.showErrorDialog(error);
                        } else {
                            PWM_MAIN.showDialog({
                                title: PWM_MAIN.showString('Button_Unlock'),
                                text: data['successMessage'],
                                okAction:function(){
                                    PWM_HELPDESK.refreshDetailPage();
                                }
                            });
                        }
                    };
                    PWM_MAIN.ajaxRequest(url, load);
                }
            });
        }
    });
};

PWM_HELPDESK.doOtpClear = function() {
    var inputValues = {};
    inputValues['userKey'] = PWM_VAR['helpdesk_obfuscatedDN'];
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        var url = PWM_MAIN.addParamToUrl(window.location.href,"processAction", "clearOtpSecret");
        var loadFunction = function(results) {
            if (results['error'] != true) {
                PWM_MAIN.showDialog({
                    title: PWM_MAIN.showString('Button_HelpdeskClearOtpSecret'),
                    text: results['successMessage'],
                    okAction:function(){
                        PWM_HELPDESK.refreshDetailPage();
                    }
                });
            } else {
                PWM_MAIN.showErrorDialog(results);
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{content:inputValues});
    }});
};

PWM_HELPDESK.initPage();