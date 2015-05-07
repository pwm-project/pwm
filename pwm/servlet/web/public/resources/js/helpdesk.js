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

var PWM_HELPDESK = PWM_HELPDESK || {};
var PWM_VAR = PWM_VAR || {};

PWM_HELPDESK.executeAction = function(actionName) {
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        var url = "Helpdesk&processAction=executeAction&name=" + actionName;
        var loadFunction = function(data) {
            PWM_MAIN.closeWaitDialog();
            if (data['error'] == true) {
                PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Error'), text: data['errorDetail']});
            } else {
                PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Success'), text: data['successMessage'], nextAction: function () {
                    PWM_MAIN.getObject('continueForm').submit();
                }});
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction);
    }});
};

PWM_HELPDESK.doResponseClear = function() {
    var username = PWM_VAR['helpdesk_obfuscatedDN'];
    PWM_MAIN.showWaitDialog({loadFunction:function() {
        var inputValues = { 'username': username };
        var url = PWM_GLOBAL['url-restservice'] + "/challenges";
        var loadFunction = function(results) {
            if (results['error'] != true) {
                PWM_MAIN.showDialog({
                    title: PWM_MAIN.showString('Button_ClearResponses'),
                    text: results['successMessage']
                });
            } else {
                PWM_MAIN.showErrorDialog(results);
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{content:inputValues,method:'delete'});
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
                    bodyText += '<button id="button-password-display" class="btn"><span class="btn-icon fa fa-eye"></span>' + PWM_MAIN.showString('Button_Show') + '</button>';
                    bodyText += ' <input id="panel-password-display" style="display:none" class="inputfield" value="' + password + '" readonly/>';
                } else {
                    bodyText += ' <input class="inputfield" value="' + password + '" readonly/>';
                }

                bodyText += '<br/>';
            }
            bodyText += '<br/><br/><button class="btn" id="button-continue">'
                + '<span class="btn-icon fa fa-forward"></span>' + PWM_MAIN.showString('Button_OK') + '</button>';
            if (PWM_VAR['helpdesk_setting_clearResponses'] == 'ask') {
                bodyText += '<span style="padding-left: 10px">&nbsp;</span>';
                bodyText += '<button class="btn" id="button-clearResponses">';
                bodyText += '<span class="btn-icon fa fa-eraser"></span>' + PWM_MAIN.showString('Button_ClearResponses') + '</button>';
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
                    PWM_MAIN.addEventHandler('button-continue','click',function(){ PWM_MAIN.getObject('continueForm').submit(); });
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

    bodyText += '</tr></table>';
    bodyText += '<button name="change" class="btn" id="password_button" disabled="true"><span class="btn-icon fa fa-key"></span>' + PWM_MAIN.showString('Button_ChangePassword') + '</button>';
    if (PWM_VAR['helpdesk_setting_PwUiMode'] == 'both') {
        bodyText += '<button name="random" class="btn" id="button-autoGeneratePassword"><span class="btn-icon fa fa-retweet"></span>' + PWM_MAIN.showString('Display_AutoGeneratedPassword') + '</button>';
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
        PWM_MAIN.getObject('searchIndicator').style.display = 'inherit';
        return { username:PWM_MAIN.getObject('username').value }
    };
    validationProps['completeFunction'] = function() {
        PWM_MAIN.getObject('searchIndicator').style.display = 'none';
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
            var sortState = grid.get("sort");
            grid.set("sort", sortState);

            if (sizeExceeded) {
                PWM_MAIN.getObject('maxResultsIndicator').style.display = 'inherit';
                PWM_MAIN.showTooltip({id:'maxResultsIndicator',position:'below',text:PWM_MAIN.showString('Display_SearchResultsExceeded')})
            } else if (PWM_MAIN.isEmpty(data['data']['searchResults']) && validationProps['usernameField'].length > 0) {
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

PWM_HELPDESK.deleteUser = function(userKey) {
    PWM_MAIN.showConfirmDialog({
        text:PWM_MAIN.showString('Confirm_DeleteUser'),
        okAction:function(){
            require(["dojo", "dijit/Dialog"], function (dojo) {
                dojo.xhrPost({
                    url: "Helpdesk?pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&processAction=deleteUser",
                    preventCache: true,
                    handleAs: "json",
                    timeout: PWM_MAIN.ajaxTimeout,
                    postData: 'userKey=' + userKey,
                    load: function (data) {
                        PWM_MAIN.closeWaitDialog();
                        if (data['error'] == true) {
                            PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Error'), text: data['errorDetail'],okAction: function(){
                                PWM_MAIN.goto("/private/Helpdesk");
                            } });
                        } else {
                            PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Success'), text: data['successMessage'], okAction: function () {
                                PWM_MAIN.goto("/private/Helpdesk");
                            }});
                        }
                    },
                    error: function (errorObj) {
                        PWM_MAIN.showDialog({title: PWM_MAIN.showString('Title_Error'), text: 'error deleting user: ' + errorObj,okAction: function(){
                            PWM_MAIN.goto("/private/Helpdesk");
                        } });
                    }
                });
            });
        }
    })
};

PWM_HELPDESK.validateOtpCode = function(userKey) {
    var validateOtpCodeFunction = function(){
        PWM_MAIN.getObject('icon-working').style.display = 'inherit';
        PWM_MAIN.getObject('icon-cross').style.display = 'none';
        PWM_MAIN.getObject('icon-check').style.display = 'none';
        var content = {
            userKey:userKey,
            code:PWM_MAIN.getObject('otpCode').value
        };
        var url = 'Helpdesk?processAction=validateOtpCode';
        var loadFunction = function(data) {
            PWM_MAIN.getObject('icon-working').style.display = 'none';
            var passed =  data['data'];
            if (passed) {
                PWM_MAIN.getObject('icon-check').style.display = 'inherit';
                PWM_MAIN.getObject('dialog_ok_button').disabled = false;
                PWM_MAIN.getObject('button-checkCode').disabled = true;
            } else {
                PWM_MAIN.getObject('icon-cross').style.display = 'inherit';
            }
        };
        PWM_MAIN.ajaxRequest(url,loadFunction,{content:content});
    };
    var text = '<div></div><table class="noborder"><tr><td style="width: 100px"><input style="width: 100px" id="otpCode" name="otpCode"/></td><td style="width:40px">'
        + '<span style="display:none;color:green" id="icon-check" class="btn-icon fa fa-lg fa-check"></span>'
        + '<span style="display:none;color:red" id="icon-cross" class="btn-icon fa fa-lg fa-times"></span>'
        + '<span style="display:none;" id="icon-working" class="fa fa-lg fa-spin fa-spinner"></span></td><td>'
        + '<button type="button" class="btn" id="button-checkCode"><span class="btn-icon fa fa-check"></span>' + PWM_MAIN.showString('Button_CheckCode') + '</button>'
        + '</td></table></div>';
    PWM_MAIN.showDialog({
        showClose:true,
        allowMove:true,
        title:'Validate OTP Code',
        text:text,
        loadFunction:function(){
            PWM_MAIN.addEventHandler('button-checkCode','click',function(){
                validateOtpCodeFunction();
            });
            PWM_MAIN.getObject('dialog_ok_button').disabled = true;
        }
    });
};

PWM_HELPDESK.sendVerificationToken = function(userKey,choiceFlag) {
    var sendTokenAction = function(choice) {
        var sendContent = {};
        if (choiceFlag && choice) {
            sendContent['method'] = choice;
        }
        PWM_MAIN.showWaitDialog({loadFunction:function(){
            var url = 'Helpdesk?processAction=sendVerificationToken';
            var loadFunction = function(data) {
                if (!data['error']) {
                    var text = '<table><tr><td>Token Destination</td><td>' + data['data']['destination'] + '</td></tr>'
                        + '<tr><td>Token</td><td><pre>' + data['data']['token'] + '</pre></td></tr></table>';

                    PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Success'),text:text,width:600});
                } else {
                    PWM_MAIN.showErrorDialog(data);
                }
            };
            PWM_MAIN.ajaxRequest(url,loadFunction,{content:sendContent});
        }});
    };

    var confirmText = 'Are you sure you want to send the user a verification token?';
    var dialoagLoadFunction = function(){};
    if (choiceFlag) {
        confirmText += '<br/><br/><button class="btn" type="button" name="emailChoiceButton" id="emailChoiceButton">'
            + '<span class="btn-icon fa fa-file-text"></span>' + PWM_MAIN.showString('Button_Email') + '</button>'
            + '<button class="btn" type="button" name="smsChoiceButton" id="smsChoiceButton">'
            + '<span class="btn-icon fa fa-phone"></span>' + PWM_MAIN.showString('Button_SMS') + '</button>';
        dialoagLoadFunction = function() {
            PWM_MAIN.addEventHandler('emailChoiceButton','click',function(){sendTokenAction('email')});
            PWM_MAIN.addEventHandler('smsChoiceButton','click',function(){sendTokenAction('sms')});
        }
    }
    PWM_MAIN.showConfirmDialog({
        text:confirmText,
        showOk: !choiceFlag,
        okAction:function(){
            sendTokenAction();
        },
        loadFunction:dialoagLoadFunction
    });
};

PWM_HELPDESK.initHelpdeskSearchPage = function() {
    PWM_HELPDESK.makeSearchGrid(function(){
        PWM_MAIN.addEventHandler('username', "keyup, input", function(){
            PWM_HELPDESK.processHelpdeskSearch();
        });
        if (PWM_MAIN.getObject('username').value && PWM_MAIN.getObject('username').value.length > 0) {
            PWM_HELPDESK.processHelpdeskSearch();
        }
    });
};

PWM_HELPDESK.initPage = function() {
    var applicationData = PWM_MAIN.getObject("application-info");
    var jspName = applicationData ? applicationData.getAttribute("data-jsp-name") : "";
    if ("helpdesk.jsp" == jspName) {
        PWM_HELPDESK.initHelpdeskSearchPage();
        PWM_MAIN.ajaxRequest("Helpdesk?processAction=clientData",function(data){
            if (data['error']) {
                PWM_MAIN.showErrorDialog(data);
                return;
            }
            for (var keyName in data['data']) {
                PWM_VAR[keyName] = data['data'][keyName];
            }
            console.log('loaded helpdesk clientData');
            PWM_HELPDESK.initHelpdeskSearchPage();
        },{method:"GET"});
    }
};

PWM_HELPDESK.initPage();