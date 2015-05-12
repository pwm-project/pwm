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

//
// PWM Change Password JavaScript.
//

"use strict";

var COLOR_BAR_TOP       = 0x8ced3f;
var COLOR_BAR_BOTTOM    = 0xcc0e3e;

var PWM_CHANGEPW = PWM_CHANGEPW || {};

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
PWM_CHANGEPW.validatePasswords = function(userDN)
{
    PWM_MAIN.getObject("password_button").disabled = true;
    if (PWM_MAIN.getObject("password1").value.length <= 0 && PWM_MAIN.getObject("password2").value.length <= 0) {
        PWM_CHANGEPW.updateDisplay(null);
        return;
    }

    if (PWM_GLOBAL['previousP1'] != PWM_MAIN.getObject("password1").value) {  // if p1 is changing, then clear out p2.
        PWM_MAIN.getObject("password2").value = "";
        PWM_GLOBAL['previousP1'] = PWM_MAIN.getObject("password1").value;
    }

    var validationProps = new Array();
    validationProps['messageWorking'] = PWM_MAIN.showString('Display_CheckingPassword');
    validationProps['serviceURL'] = PWM_GLOBAL['url-restservice'] + "/checkpassword";
    validationProps['readDataFunction'] = function(){
        var returnObj = {};
        returnObj['password1'] = PWM_MAIN.getObject("password1").value;
        returnObj['password2'] = PWM_MAIN.getObject("password2").value;
        if (userDN) returnObj['username'] = userDN;
        return returnObj;
    };
    validationProps['processResultsFunction'] = function(data){
        if (data) {
            PWM_CHANGEPW.updateDisplay(data['data']);
        } else {
            PWM_CHANGEPW.updateDisplay(null);
        }
    };

    PWM_MAIN.pwmFormValidator(validationProps);
};


PWM_CHANGEPW.updateDisplay = function(resultInfo) {
    if (resultInfo == null) {
        var passwordButton = PWM_MAIN.getObject("password_button");
        if (passwordButton != null) {
            passwordButton.disabled = false;
        }
        PWM_MAIN.showSuccess(PWM_MAIN.showString('Display_PasswordPrompt'));
        PWM_CHANGEPW.markStrength(0);
        PWM_CHANGEPW.markConfirmationCheck(null);
        return;
    }

    var message = resultInfo["message"];

    if (resultInfo["version"] != "2") {
        PWM_MAIN.showError("[ unexpected version string from server ]");
        return;
    }

    if (resultInfo["passed"] == true) {
        //PWM_MAIN.getObject('password2').disabled = false;
        if (resultInfo["match"] == "MATCH") {
            PWM_MAIN.getObject("password_button").disabled = false;
            PWM_MAIN.showSuccess(message);
        } else {
            PWM_MAIN.getObject("password_button").disabled = true;
            PWM_MAIN.showInfo(message);
        }
    } else {
        //PWM_MAIN.getObject('password2').disabled = true;
        PWM_MAIN.getObject("password_button").disabled = true;
        PWM_MAIN.showError(message);
    }

    try {
        PWM_CHANGEPW.markConfirmationCheck(resultInfo["match"]);
    } catch (e) {
        console.log('error updating confirmation check icons: ' + e)
    }

    try {
        PWM_CHANGEPW.markStrength(resultInfo["strength"]);
    } catch (e) {
        console.log('error updating strength icon: ' + e)
    }
};

PWM_CHANGEPW.markConfirmationCheck = function(matchStatus) {
    if (PWM_MAIN.getObject("confirmCheckMark") && PWM_MAIN.getObject("confirmCrossMark")) {
        if (matchStatus == "MATCH") {
            PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'visible';
            PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCheckMark").width = '15';
            PWM_MAIN.getObject("confirmCrossMark").width = '0';
        } else if (matchStatus == "NO_MATCH") {
            PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'visible';
            PWM_MAIN.getObject("confirmCheckMark").width = '0';
            PWM_MAIN.getObject("confirmCrossMark").width = '15';
        } else {
            PWM_MAIN.getObject("confirmCheckMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCrossMark").style.visibility = 'hidden';
            PWM_MAIN.getObject("confirmCheckMark").width = '0';
            PWM_MAIN.getObject("confirmCrossMark").width = '0';
        }
    }
};

PWM_CHANGEPW.markStrength = function(strength) { //strength meter
    if (PWM_MAIN.getObject("strengthBox") == null) {
        return;
    }

    if (PWM_MAIN.getObject("password1").value.length > 0) {
        PWM_MAIN.getObject("strengthBox").style.visibility = 'visible';
    } else {
        PWM_MAIN.getObject("strengthBox").style.visibility = 'hidden';
    }

    var strengthLabel = "";
    var barColor = "";

    if (strength > 70) {
        strengthLabel = PWM_MAIN.showString('Display_PasswordStrengthHigh');
    } else if (strength > 45) {
        strengthLabel = PWM_MAIN.showString('Display_PasswordStrengthMedium');
    } else {
        strengthLabel = PWM_MAIN.showString('Display_PasswordStrengthLow');
    }

    var colorFade = function(h1, h2, p) { return ((h1>>16)+((h2>>16)-(h1>>16))*p)<<16|(h1>>8&0xFF)+((h2>>8&0xFF)-(h1>>8&0xFF))*p<<8|(h1&0xFF)+((h2&0xFF)-(h1&0xFF))*p; }
    var gradColor = colorFade(COLOR_BAR_BOTTOM, COLOR_BAR_TOP, strength / 100).toString(16) + '';

    var barObject = PWM_MAIN.getObject("strengthBar");
    if (barObject != null) {
        barObject.style.width = strength + '%';
        barObject.style.backgroundColor = '#' + gradColor;
    }

    var labelObject = PWM_MAIN.getObject("strengthLabel");
    if (labelObject != null) {
        labelObject.innerHTML = strengthLabel == null ? "" : strengthLabel;
    }
};


PWM_CHANGEPW.copyToPasswordFields = function(text) { // used to copy auto-generated passwords to password field
    if (text.length > 255) {
        text = text.substring(0,255);
    }
    text = PWM_MAIN.trimString(text);


    PWM_MAIN.closeWaitDialog();

    PWM_MAIN.getObject("password1").value = text;
    PWM_CHANGEPW.validatePasswords();
    PWM_MAIN.getObject("password2").focus();

    ShowHidePasswordHandler.show('password1');
};


PWM_CHANGEPW.showPasswordGuide=function() {
    PWM_MAIN.showDialog({
        showClose:true,
        title: PWM_MAIN.showString('Title_PasswordGuide'),
        text: '<div id="passwordGuideTextContent">' + PWM_GLOBAL['passwordGuideText'] + '</div>'
    });
    /*
    PWM_MAIN.clearDijitWidget('dialogPopup');
    require(["dojo","dijit/Dialog"],function(dojo, Dialog){
        var theDialog = new Dialog({
            title: PWM_MAIN.showString('Title_PasswordGuide'),
            style: "border: 2px solid #D4D4D4; min-width: 300px",
            content: '<div id="passwordGuideTextContent">' + PWM_GLOBAL['passwordGuideText'] + '</div>',
            closable: true,
            draggable: true,
            id: "dialogPopup"
        });
        theDialog.show();

        dojo.connect(theDialog, "hide", function(){
            dojo.destroy(PWM_MAIN.getObject("passwordGuideTextContent"));
        });
    });
    */
};

PWM_CHANGEPW.handleChangePasswordSubmit=function() {
    PWM_MAIN.showInfo(PWM_MAIN.showString('Display_PleaseWait'));
    PWM_VAR['dirtyPageLeaveFlag'] = false;
};

PWM_CHANGEPW.doRandomGeneration=function(randomConfig) {
    randomConfig = randomConfig === undefined ? {} : randomConfig;
    var finishAction = 'finishAction' in randomConfig ? randomConfig['finishAction'] : function(password) {
        PWM_CHANGEPW.copyToPasswordFields(password)
    };

    var eventHandlers = [];
    var dialogBody = "";
    if (randomConfig['dialog'] != null && randomConfig['dialog'].length > 0) {
        dialogBody += randomConfig['dialog'];
    } else {
        dialogBody += PWM_MAIN.showString('Display_PasswordGeneration');
    }
    dialogBody += "<br/><br/>";
    dialogBody += '<table style="border: 0">';

    for (var i = 0; i < 20; i++) {
        dialogBody += '<tr style="border: 0">';
        for (var j = 0; j < 2; j++) {
            i = i + j;
            (function(index) {
                var elementID = "randomGen" + index;
                dialogBody += '<td style="border: 0; padding-bottom: 5px;" width="20%"><a style="text-decoration:none; color: black" href="#" id="' + elementID + '">&nbsp;</a></td>';
                eventHandlers.push(function(){
                    PWM_MAIN.addEventHandler(elementID,'click',function(){
                        var value = PWM_MAIN.getObject(elementID).innerHTML;
                        finishAction(value);
                    });
                });
            })(i);
        }
        dialogBody += '</tr>';
    }
    dialogBody += "</table><br/><br/>";

    dialogBody += '<table style="border: 0">';
    dialogBody += '<tr style="border: 0"><td style="border: 0"><button class="btn" id="moreRandomsButton" disabled="true"><span class="btn-icon fa fa-refresh"></span>' + PWM_MAIN.showString('Button_More') + '</button></td>';
    dialogBody += '<td style="border: 0; text-align:right;"><button class="btn" id="cancelRandomsButton"><span class="btn-icon fa fa-times"></span>' + PWM_MAIN.showString('Button_Cancel') + '</button></td></tr>';
    dialogBody += "</table>";

    randomConfig['dialogBody'] = dialogBody;

    eventHandlers.push(function(){
        PWM_MAIN.addEventHandler('cancelRandomsButton','click',function(){
            PWM_MAIN.closeWaitDialog('dialogPopup');
        });
        PWM_MAIN.addEventHandler('moreRandomsButton','click',function(){
            PWM_CHANGEPW.beginFetchRandoms(randomConfig);
        });
    });



    var titleString = randomConfig['title'] == null ? PWM_MAIN.showString('Title_RandomPasswords') : randomConfig['title'];
    PWM_MAIN.showDialog({
        title:titleString,
        dialogClass:'narrow',
        text:dialogBody,
        showOk:false,
        showClose:true,
        loadFunction:function(){
            PWM_CHANGEPW.beginFetchRandoms(randomConfig);
            for (var i = 0; i < eventHandlers.length; i++) {
                eventHandlers[i]();
            }
        }
    });
};

PWM_CHANGEPW.beginFetchRandoms=function(randomConfig) {
    PWM_MAIN.getObject('moreRandomsButton').disabled = true;
    var fetchList = new Array();
    for (var counter = 0; counter < 20; counter++) {
        fetchList[counter] = 'randomGen' + counter;
        var name ='randomGen' + counter;
        var element = PWM_MAIN.getObject(name);
        if (element != null) {
            element.firstChild.nodeValue = '\u00A0';
        }
    }
    fetchList.sort(function() {return 0.5 - Math.random()});
    fetchList.sort(function() {return 0.5 - Math.random()});
    randomConfig['fetchList'] = fetchList;
    PWM_CHANGEPW.fetchRandoms(randomConfig);
};

PWM_CHANGEPW.fetchRandoms=function(randomConfig) {
    if (randomConfig['fetchList'].length < 1) {
        var moreButton = PWM_MAIN.getObject('moreRandomsButton');
        if (moreButton != null) {
            moreButton.disabled = false;
            moreButton.focus();
        }
        return;
    }

    if (randomConfig['fetchList'].length > 0) {
        var successFunction = function(resultInfo) {
            var password = resultInfo['data']["password"];
            var elementID = randomConfig['fetchList'].pop();
            var element = PWM_MAIN.getObject(elementID);
            if (element != null) {
                element.firstChild.nodeValue = password;
            }
            PWM_CHANGEPW.fetchRandoms(randomConfig);
        };

        var url = PWM_GLOBAL['url-restservice'] + "/randompassword";
        var content = randomConfig['dataInput'] == null ? { } : randomConfig['dataInput'];

        PWM_MAIN.ajaxRequest(url,successFunction,{content:content});
    }
};

PWM_CHANGEPW.startupChangePasswordPage=function() {
    //PWM_MAIN.getObject('password2').disabled = true;
    PWM_CHANGEPW.markStrength(0);

    // add handlers for main form
    require(["dojo/on"], function(on){
        var changePasswordForm = PWM_MAIN.getObject('changePasswordForm');
        on(changePasswordForm,"keyup, change",function(){
            PWM_CHANGEPW.validatePasswords(null);
        });
        on(changePasswordForm,"submit",function(event){
            PWM_CHANGEPW.handleChangePasswordSubmit();
            PWM_MAIN.handleFormSubmit(changePasswordForm, event);
            return false;
        });
        on(changePasswordForm,"reset",function(){
            PWM_CHANGEPW.validatePasswords(null);
            PWM_CHANGEPW.setInputFocus();
            return false;
        });
    });

    // show the auto generate password panel
    var autoGenPasswordElement = PWM_MAIN.getObject("autogenerate-icon");
    if (autoGenPasswordElement != null) {
        autoGenPasswordElement.style.visibility = 'visible';
        PWM_MAIN.addEventHandler(autoGenPasswordElement,'click',function(){
            PWM_CHANGEPW.doRandomGeneration();
        });
        PWM_MAIN.showTooltip({
            id: "autogenerate-icon",
            text: PWM_MAIN.showString('Display_AutoGeneratedPassword')
        });
    }

    // add a handler so if the user leaves the page except by submitting the form, then a warning/confirm is shown
    var pageLeaveFunction = function() {
        if (PWM_VAR['dirtyPageLeaveFlag']) {
            var message = PWM_MAIN.showString('Display_LeaveDirtyPasswordPage');
            console.log('changepassword-beforeunload handler invoked');
            if (message.trim().length > 0) {
                return message;
            }
        } else {
            console.log('changepassword-beforeunload handler skipped');
        }
    };
    window.onbeforeunload=pageLeaveFunction;


    PWM_VAR['dirtyPageLeaveFlag'] = true;

    var messageElement = PWM_MAIN.getObject("message");
    if (messageElement.firstChild.nodeValue.length < 2) {
        setTimeout(function(){
            PWM_MAIN.showInfo(PWM_MAIN.showString('Display_PasswordPrompt'));
        },100);
    }

    PWM_MAIN.showTooltip({
        id: "strengthBox",
        text: PWM_MAIN.showString('Tooltip_PasswordStrength'),
        width: 350

    });

    if (PWM_GLOBAL['passwordGuideText'] && PWM_GLOBAL['passwordGuideText'].length > 0) {
        var iconElement = PWM_MAIN.getObject('password-guide-icon');
        if (iconElement) {
            try {iconElement.style.visibility = 'visible';} catch (e) { /* noop */ }
            PWM_MAIN.addEventHandler('password-guide-icon','click',function(){
                PWM_CHANGEPW.showPasswordGuide();
            });
            PWM_MAIN.showTooltip({
                id: ["password-guide-icon"],
                text: PWM_MAIN.showString('Display_ShowPasswordGuide')
            });
        }
    }

    setTimeout(function(){
        PWM_CHANGEPW.setInputFocus();
    },10);
};

PWM_CHANGEPW.setInputFocus=function() {
    var currentPassword = PWM_MAIN.getObject('currentPassword');
    if (currentPassword != null) {
        setTimeout(function() { currentPassword.focus(); },10);
    } else {
        var password1 = PWM_MAIN.getObject('password1');
        setTimeout(function() { password1.focus(); },10);
    }
};

PWM_CHANGEPW.refreshCreateStatus=function(refreshInterval) {
    require(["dojo","dijit/registry"],function(dojo,registry){
        var displayStringsUrl = "ChangePassword?processAction=checkProgress";
        var completedUrl = "ChangePassword?processAction=complete&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
        var loadFunction = function(data) {
            var supportsProgress = (document.createElement('progress').max !== undefined);
            if (supportsProgress) {
                console.log('beginning html5 progress refresh');
                var html5passwordProgressBar = PWM_MAIN.getObject('html5ProgressBar');
                dojo.setAttr(html5passwordProgressBar, "value", data['data']['percentComplete']);
            } else {
                console.log('beginning dojo progress refresh');
                var progressBar = registry.byId('passwordProgressBar');
                progressBar.set("value",data['data']['percentComplete']);
            }

            try {
                var tableBody = '';
                if (data['data']['messages']) {
                    for (var msgItem in data['data']['messages']) {
                        (function(message){
                            if (message['show']) {
                                tableBody += '<tr><td>' + message['label'] + '</td><td>'
                                tableBody += message['complete'] ? "Completed" : "In Progress"
                                tableBody += '</td></tr>'
                            }
                        }(data['data']['messages'][msgItem]));
                    }
                }
                if (PWM_MAIN.getObject('progressMessageTable')) {
                    PWM_MAIN.getObject('progressMessageTable').innerHTML = tableBody;
                }
                if (PWM_MAIN.getObject('estimatedRemainingSeconds')) {
                    PWM_MAIN.getObject('estimatedRemainingSeconds').innerHTML = data['data']['estimatedRemainingSeconds'];
                }
                if (PWM_MAIN.getObject('elapsedSeconds')) {
                    PWM_MAIN.getObject('elapsedSeconds').innerHTML = data['data']['elapsedSeconds'];
                }
            } catch (e) {
                console.log('unable to update progressMessageTable, error: ' + e);
            }

            if (data['data']['complete'] == true) {
                PWM_MAIN.goto(completedUrl,{delay:1000})
            } else {
                setTimeout(function(){
                    PWM_CHANGEPW.refreshCreateStatus(refreshInterval);
                },refreshInterval);
            }
        };
        var errorFunction = function(error) {
            console.log('unable to read password change status: ' + error);
            setTimeout(function(){
                PWM_CHANGEPW.refreshCreateStatus(refreshInterval);
            },refreshInterval);
        };
        PWM_MAIN.ajaxRequest(displayStringsUrl, loadFunction, {errorFunction:errorFunction});
    });
};
