/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

var passwordsMasked = true;
var previousP1 = "";

var COLOR_BAR_TOP       = 0x8ced3f;
var COLOR_BAR_BOTTOM    = 0xcc0e3e;

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validatePasswords(userDN)
{
    getObject("password_button").disabled = true;
    if (getObject("password1").value.length <= 0 && getObject("password2").value.length <= 0) {
        updateDisplay(null);
        return;
    }

    if (previousP1 != getObject("password1").value) {  // if p1 is changing, then clear out p2.
        getObject("password2").value = "";
        previousP1 = getObject("password1").value;
    }

    var validationProps = new Array();
    validationProps['messageWorking'] = PWM_STRINGS['Display_CheckingPassword'];
    validationProps['serviceURL'] = PWM_GLOBAL['url-restservice'] + "/checkpassword";
    validationProps['readDataFunction'] = function(){
        var returnObj = {};
        returnObj['password1'] = getObject("password1").value;
        returnObj['password2'] = getObject("password2").value;
        if (userDN) returnObj['username'] = userDN;
        return returnObj;
    };
    validationProps['processResultsFunction'] = function(data){
        updateDisplay(data);
    };

    pwmFormValidator(validationProps);
}


function updateDisplay(resultInfo) {
    if (resultInfo == null) {
        var passwordButton = getObject("password_button");
        if (passwordButton != null) {
            passwordButton.disabled = false;
        }
        showSuccess(PWM_STRINGS['Display_PasswordPrompt']);
        markStrength(0);
        markConfirmationCheck(null);
        return;
    }

    var message = resultInfo["message"];

    if (resultInfo["version"] != "2") {
        showError("[ unexpected version string from server ]");
        return;
    }

    if (resultInfo["passed"] == true) {
        if (resultInfo["match"] == "MATCH") {
            getObject("password_button").disabled = false;
            showSuccess(message);
        } else {
            getObject("password_button").disabled = true;
            showInfo(message);
        }
    } else {
        getObject("password_button").disabled = true;
        showError(message);
    }

    try {
        markConfirmationCheck(resultInfo["match"]);
    } catch (e) {
        console.log('error updating confirmation check icons: ' + e)
    }

    try {
        markStrength(resultInfo["strength"]);
    } catch (e) {
        console.log('error updating strength icon: ' + e)
    }
}

function markConfirmationCheck(matchStatus) {
    if (getObject("confirmCheckMark") && getObject("confirmCrossMark")) {
        if (matchStatus == "MATCH") {
            getObject("confirmCheckMark").style.visibility = 'visible';
            getObject("confirmCrossMark").style.visibility = 'hidden';
            getObject("confirmCheckMark").width = '15';
            getObject("confirmCrossMark").width = '0';
        } else if (matchStatus == "NO_MATCH") {
            getObject("confirmCheckMark").style.visibility = 'hidden';
            getObject("confirmCrossMark").style.visibility = 'visible';
            getObject("confirmCheckMark").width = '0';
            getObject("confirmCrossMark").width = '15';
        } else {
            getObject("confirmCheckMark").style.visibility = 'hidden';
            getObject("confirmCrossMark").style.visibility = 'hidden';
            getObject("confirmCheckMark").width = '0';
            getObject("confirmCrossMark").width = '0';
        }
    }
}

function markStrength(strength) { //strength meter
    if (getObject("strengthBox") == null) {
        return;
    }

    if (getObject("password1").value.length > 0) {
        getObject("strengthBox").style.visibility = 'visible';
    } else {
        getObject("strengthBox").style.visibility = 'hidden';
    }

    var strengthLabel = "";
    var barColor = "";

    if (strength > 70) {
        strengthLabel = PWM_STRINGS['Display_PasswordStrengthHigh'];
    } else if (strength > 45) {
        strengthLabel = PWM_STRINGS['Display_PasswordStrengthMedium'];
    } else {
        strengthLabel = PWM_STRINGS['Display_PasswordStrengthLow'];
    }

    var colorFade = function(h1, h2, p) { return ((h1>>16)+((h2>>16)-(h1>>16))*p)<<16|(h1>>8&0xFF)+((h2>>8&0xFF)-(h1>>8&0xFF))*p<<8|(h1&0xFF)+((h2&0xFF)-(h1&0xFF))*p; }
    var gradColor = colorFade(COLOR_BAR_BOTTOM, COLOR_BAR_TOP, strength / 100).toString(16) + '';

    var barObject = getObject("strengthBar");
    if (barObject != null) {
        barObject.style.width = strength + '%';
        barObject.style.backgroundColor = '#' + gradColor;
    }

    var labelObject = getObject("strengthLabel");
    if (labelObject != null) {
        labelObject.innerHTML = strengthLabel == null ? "" : strengthLabel;
    }
}


function copyToPasswordFields(text)  // used to copy auto-generated passwords to password field
{
    if (text.length > 255) {
        text = text.substring(0,255);
    }
    text = trimString(text);


    closeWaitDialog();

    if (passwordsMasked) {
        toggleMaskPasswords();
    }

    getObject("password1").value = text;
    validatePasswords();
    getObject("password2").focus();
}


function showPasswordGuide() {
    clearDijitWidget('dialogPopup');
    require(["dojo","dijit/Dialog"],function(){
        var theDialog = new dijit.Dialog({
            title: PWM_STRINGS['Title_PasswordGuide'],
            style: "border: 2px solid #D4D4D4; style: 300px",
            content: PWM_STRINGS['passwordGuideText'],
            closable: true,
            draggable: true,
            id: "dialogPopup"
        });
        theDialog.show();
    });
}


function showRandomPasswordsDialog(randomConfig) {

    var titleString = randomConfig['title'] == null ? PWM_STRINGS['Title_RandomPasswords'] : randomConfig['title'];

    require(["dojo","dijit/Dialog","dijit/ProgressBar"],function(){
        closeWaitDialog();
        var theDialog = new dijit.Dialog({
            title: titleString,
            style: "width: 300px; border: 2px solid #D4D4D4;",
            content: randomConfig['dialogBody'],
            id: "dialogPopup"
        });
        theDialog.setAttribute('class','nihilo');
        theDialog.show();
        beginFetchRandoms(randomConfig);
    });
}

function toggleMaskPasswords()
{
    if (passwordsMasked) {
        getObject("hide_button").value = PWM_STRINGS['Button_Hide'];
        changeInputTypeField(getObject("password1"),"text");
        changeInputTypeField(getObject("password2"),"text");
    } else {
        getObject("hide_button").value = PWM_STRINGS['Button_Show'];
        changeInputTypeField(getObject("password1"),"password");
        changeInputTypeField(getObject("password2"),"password");
    }
    passwordsMasked = !passwordsMasked;

}

function handleChangePasswordSubmit()
{
    showInfo(PWM_STRINGS['Display_PleaseWait']);
    PWM_GLOBAL['dirtyPageLeaveFlag'] = false;
}

function doRandomGeneration(randomConfig) {
    if (randomConfig == null) {
        randomConfig = { };
    }

    if (randomConfig['finishAction'] == null || randomConfig['finishAction'].length < 1) {
        randomConfig['finishAction'] = "copyToPasswordFields(PWM_GLOBAL['SelectedRandomPassword'])";
    }

    var dialogBody = "";
    if (randomConfig['dialog'] != null && randomConfig['dialog'].length > 0) {
        dialogBody += randomConfig['dialog'];
    } else {
        dialogBody += PWM_STRINGS['Display_PasswordGeneration'];
    }
    dialogBody += "<br/><br/>";
    dialogBody += '<table style="border: 0">';
    for (var i = 0; i < 20; i++) {
        dialogBody += '<tr style="border: 0">';
        for (var j = 0; j < 2; j++) {
            i = i + j;
            var elementID = "randomGen" + i;
            var clickAction = "PWM_GLOBAL['SelectedRandomPassword'] = getObject('" + elementID + "').firstChild.nodeValue;";
            clickAction += randomConfig['finishAction'];
            dialogBody += '<td style="border: 0; padding-bottom: 5px;" width="20%"><a style="text-decoration:none; color: black" href="#" onclick="' + clickAction + '" id="' + elementID + '">&nbsp;</a></td>';
        }
        dialogBody += '</tr>';
    }
    dialogBody += "</table><br/><br/>";

    dialogBody += '<table style="border: 0">';
    dialogBody += '<tr style="border: 0"><td style="border: 0"><button class="btn" id="moreRandomsButton" disabled="true" onclick="beginFetchRandoms(PWM_GLOBAL[\'lastRandomConfig\'])">' + PWM_STRINGS['Button_More'] + '</button></td>';
    dialogBody += '<td style="border: 0; text-align:right;"><button class="btn" onclick="clearDijitWidget(\'dialogPopup\')">' + PWM_STRINGS['Button_Cancel'] + '</button></td></tr>';
    dialogBody += "</table>";
    randomConfig['dialogBody'] = dialogBody;
    PWM_GLOBAL['lastRandomConfig'] = randomConfig;
    showRandomPasswordsDialog(randomConfig);
}

function beginFetchRandoms(randomConfig) {
    getObject('moreRandomsButton').disabled = true;
    var fetchList = new Array();
    for (var counter = 0; counter < 20; counter++) {
        fetchList[counter] = 'randomGen' + counter;
        var name ='randomGen' + counter;
        var element = getObject(name);
        if (element != null) {
            element.firstChild.nodeValue = '\u00A0';
        }
    }
    fetchList.sort(function() {return 0.5 - Math.random()});
    fetchList.sort(function() {return 0.5 - Math.random()});
    randomConfig['fetchList'] = fetchList;
    fetchRandoms(randomConfig);
}

function fetchRandoms(randomConfig) {
    if (randomConfig['fetchList'].length < 1) {
        var moreButton = getObject('moreRandomsButton');
        if (moreButton != null) {
            moreButton.disabled = false;
            moreButton.focus();
        }
        return;
    }

    if (randomConfig['fetchList'].length > 0) {
        var successFunction = function(resultInfo) {
            var password = resultInfo["password"];
            var elementID = randomConfig['fetchList'].pop();
            var element = getObject(elementID);
            if (element != null) {
                element.firstChild.nodeValue = password;
            }
            fetchRandoms(randomConfig);
        };

        var dataInput = randomConfig['dataInput'] == null ? { } : randomConfig['dataInput'];

        require(["dojo"],function(dojo){
            dojo.xhrPost({
                url: PWM_GLOBAL['url-restservice'] + "/randompassword?pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                headers: {"Accept":"application/json"},
                content: dataInput,
                preventCache: true,
                timeout: 15000,
                sync: false,
                handleAs: "json",
                load: successFunction,
                error: function(errorObj){
                    showError("unexpected randomgen version string from server: " + errorObj);
                }
            });
        });
    }
}

function startupChangePasswordPage(initialPrompt)
{
    /* enable the hide button only if the toggle works */
    if (PWM_GLOBAL['setting-showHidePasswordFields']) {
        try {
            toggleMaskPasswords();
            toggleMaskPasswords();
            changeInputTypeField(getObject("hide_button"),"button");
        } catch (e) {
            //alert("can't show hide button: " + e)

        }
    }

    markStrength(0);

    // show the auto generate password panel
    var autoGenPasswordElement = getObject("autoGeneratePassword");
    if (autoGenPasswordElement != null) {
        autoGenPasswordElement.style.visibility = 'visible';
    }

    // show the password guide panel
    var passwordGuideElement = getObject("passwordGuide");
    if (passwordGuideElement != null) {
        var passwordGuideText = PWM_STRINGS['passwordGuideText'];
        if ( passwordGuideText != null && passwordGuideText.length > 0) {
            passwordGuideElement.style.visibility = 'visible';
        }
    }

    // add a handler so if the user leaves the page except by submitting the form, then a warning/confirm is shown
    window.onbeforeunload = function() {
        if (PWM_GLOBAL['dirtyPageLeaveFlag']) {
            var message = PWM_STRINGS['Display_LeaveDirtyPasswordPage'];
            return message;
        }
    };

    PWM_GLOBAL['dirtyPageLeaveFlag'] = true;

    var messageElement = getObject("message");
    if (messageElement.firstChild.nodeValue.length < 2) {
        setTimeout(function(){
            showInfo(initialPrompt);
        },100);
    }

    setInputFocus();
}

function setInputFocus() {
    var currentPassword = getObject('currentPassword');
    if (currentPassword != null) {
        setTimeout(function() { currentPassword.focus(); },10);
    } else {
        var password1 = getObject('password1');
        setTimeout(function() { password1.focus(); },10);
    }
}