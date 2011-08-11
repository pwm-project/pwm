/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

var validationCache = { };
var validationInProgress = false;

var fetchList = new Array();
var outstandingFetches = 0;

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validatePasswords()
{
    if (getObject("password1").value.length <= 0 && getObject("password2").value.length <= 0) {
        updateDisplay(null);
        return;
    }

    if (previousP1 != getObject("password1").value) {  // if p1 is changing, then clear out p2.
        getObject("password2").value = "";
        previousP1 = getObject("password1").value;
    }

    if (validationInProgress) {
        return;
    }

    var passwordData = makeValidationKey();
    {
        var cachedResult = validationCache[passwordData.cacheKey];
        if (cachedResult != null) {
            updateDisplay(cachedResult);
            return;
        }
    }

    setTimeout(function(){ if (validationInProgress) { showInfo(PWM_STRINGS['Display_CheckingPassword']); } },500);

    validationInProgress = true;
    dojo.xhrPost({
        url: PWM_STRINGS['url-changepassword'] + "?processAction=validate&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
        postData: dojo.toJson(passwordData),
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        timeout: 15000,
        error: function(errorObj) {
            validationInProgress = false;
            clearError(PWM_STRINGS['Display_CommunicationError']);
            markStrength(0);
            markConfirmationCheck(null);
            console.log('error: ' + errorObj);
        },
        load: function(data){
            validationInProgress = false;
            validationCache[passwordData.cacheKey] = data;
            if (passwordData.cacheKey != makeValidationKey().cacheKey) {
                setTimeout(function() {validatePasswords();}, 1);
            } else {
                updateDisplay(data);
            }
        }
    });
}

function makeValidationKey() {
    var validationKey = {
        password1:getObject("password1").value,
        password2:getObject("password2").value,
        cacheKey: getObject("password1").value + getObject("password2").value
    };

    if (getObject("currentPassword") != null) {
        validationKey.currentPassword = getObject("currentPassword").value;
        validationKey.cacheKey = getObject("password1").value + getObject("password2").value + getObject("currentPassword").value;
    }

    return validationKey;
}

function updateDisplay(resultInfo) {
    if (resultInfo == null) {
        getObject("password_button").disabled = false;
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

    if (resultInfo["passed"] == "true") {
        getObject("password_button").disabled = false;
        showSuccess(message);
    } else {
        getObject("password_button").disabled = true;
        showError(message);
    }

    markConfirmationCheck(resultInfo["match"]);
    markStrength(resultInfo["strength"]);
}

function markConfirmationCheck(matchStatus) {
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
        strengthLabel = PWM_STRINGS['Strength_High'];
    } else if (strength > 45) {
        strengthLabel = PWM_STRINGS['Strength_Medium'];
    } else {
        strengthLabel = PWM_STRINGS['Strength_Low'];
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


function copyToPasswordFields(elementID)  // used to copy auto-generated passwords to password field
{
    var text = getObject(elementID).firstChild.nodeValue;

    if (text.length > 255) {
        text = text.substring(0,255);
    }
    text = trimString(text);


    closeRandomPasswordsDialog();

    if (passwordsMasked) {
        toggleMaskPasswords();
    }

    getObject("password1").value = text;
    validatePasswords();
    getObject("password2").focus();
}

function showPasswordGuide() {
    closePasswordGuide();
    dojo.require("dijit.Dialog");

    var theDialog = new dijit.Dialog({
        title: PWM_STRINGS['Title_PasswordGuide'],
        style: "border: 2px solid #D4D4D4;",
        content: PWM_STRINGS['passwordGuideText'],
        closable: true,
        draggable: true,
        id: "passwordGuideDialog"
    });
    theDialog.show();
}

function closePasswordGuide() {
    var dialog = dijit.byId('passwordGuideDialog');
    if (dialog != null) {
        dialog.hide();
        dialog.destroyRecursive();
    }
}

function showRandomPasswordsDialog(dialogBody) {
    dojo.require("dijit.Dialog");
    closeRandomPasswordsDialog();

    var theDialog = new dijit.Dialog({
        title: PWM_STRINGS['Title_RandomPasswords'],
        style: "width: 300px; border: 2px solid #D4D4D4;",
        content: dialogBody,
        closable: false,
        draggable: true,
        id: "randomPasswordDialog"

    });
    theDialog.setAttribute('class','tundra');
    theDialog.show();
}

function closeRandomPasswordsDialog() {
    var dialog = dijit.byId('randomPasswordDialog');
    if (dialog != null) {
        dialog.hide();
        dialog.destroyRecursive();
    }
}

function toggleMaskPasswords()
{
    if (passwordsMasked) {
        getObject("hide_button").value = "\u00A0\u00A0\u00A0" + PWM_STRINGS['Button_Hide'] + "\u00A0\u00A0\u00A0";
        changeInputTypeField(getObject("password1"),"text");
        changeInputTypeField(getObject("password2"),"text");
    } else {
        getObject("hide_button").value = "\u00A0\u00A0\u00A0" + PWM_STRINGS['Button_Show'] + "\u00A0\u00A0\u00A0";
        changeInputTypeField(getObject("password1"),"password");
        changeInputTypeField(getObject("password2"),"password");
    }
    passwordsMasked = !passwordsMasked;

}

function handleChangePasswordSubmit()
{
    getObject("error_msg").firstChild.nodeValue = PWM_STRINGS['Display_PleaseWait'];
    PWM_GLOBAL['dirtyPageLeaveFlag'] = false;
}

function doRandomGeneration() {
    var dialogBody = PWM_STRINGS['Display_PasswordGeneration'] + "<br/><br/>";
    dialogBody += '<table style="border: 0">';
    for (var i = 0; i < 20; i++) {
        dialogBody += '<tr style="border: 0"><td style="border: 0; padding-bottom: 5px;" width="20%"><a style="text-decoration:none" href="#" onclick="copyToPasswordFields(\'randomGen' + i + '\')" id="randomGen' + i + '">&nbsp;</a></td>';
        i++;
        dialogBody += '<td style="border: 0; padding-bottom: 5px;" width="20%"><a style="text-decoration:none" href="#" onclick="copyToPasswordFields(\'randomGen' + i + '\')" id="randomGen' + i + '">&nbsp;</a></td></tr>';
    }
    dialogBody += "</table><br/><br/>";

    dialogBody += '<table style="border: 0">';
    dialogBody += '<tr style="border: 0"><td style="border: 0"><button id="moreRandomsButton" disabled="true" onclick="beginFetchRandoms()">' + PWM_STRINGS['Button_More'] + '</button></td>';
    dialogBody += '<td style="border: 0; text-align:right;"><button onclick="closeRandomPasswordsDialog()">' + PWM_STRINGS['Button_Cancel'] + '</button></td></tr>';
    dialogBody += "</table>";
    showRandomPasswordsDialog(dialogBody);
    beginFetchRandoms();
}

function beginFetchRandoms() {
    getObject('moreRandomsButton').disabled = true;
    outstandingFetches = 0;
    fetchList = new Array();
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

    fetchRandoms();
}

function fetchRandoms() {
    if (fetchList.length < 1) {
        var moreButton = getObject('moreRandomsButton');
        if (moreButton != null) {
            moreButton.disabled = false;
            moreButton.focus();
        }
        return;
    }

    if (outstandingFetches > 3) {
        setTimeout(function(){fetchRandoms();},100);
    } else {
        var name = fetchList.splice(0,1);
        outstandingFetches++;
        fetchRandom(function(results) {
                handleRandomResponse(results, name);
                outstandingFetches--;
            },
            function(errorObj) {outstandingFetches--;}
        );
        setTimeout(function(){fetchRandoms();},100);
    }
}

function fetchRandom(successFunction, errorFunction) {
    dojo.xhrGet({
        url: PWM_STRINGS['url-changepassword'] + "?processAction=getrandom&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        timeout: 15000,
        sync: false,
        handleAs: "json",
        load: successFunction,
        error: errorFunction
    });
}

function handleRandomResponse(resultInfo, elementID)
{
    if (resultInfo["version"] != "1") {
        showError("[ unexpected randomgen version string from server ]");
        return;
    }

    var password = resultInfo["password"];

    var element = getObject(elementID);
    if (element != null) {
        element.firstChild.nodeValue = password;
    }
}

function startupChangePasswordPage()
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

    // show the auto generate password panel
    var autoGenPasswordElement = getObject("autoGeneratePassword");
    if (autoGenPasswordElement != null) {
        autoGenPasswordElement.style.visibility = 'visible';
    }

    // show the auto generate password panel
    var passwordGuideElement = getObject("passwordGuide");
    if (passwordGuideElement != null) {
        var passwordGuideText = PWM_STRINGS['passwordGuideText'];
        if ( passwordGuideText != null && passwordGuideText.length > 0) {
            passwordGuideElement.style.visibility = 'visible';
        }
    }

    // show the error panel
    var errorObj = getObject("error_msg");
    if (errorObj != null) {
        errorObj.style.visibility = 'visible';
    }

    // add a handler so if the user leaves the page except by submitting the form, then a warning/confirm is shown
    window.onbeforeunload = function() {
        if (PWM_GLOBAL['dirtyPageLeaveFlag']) {
            var message = PWM_STRINGS['Display_LeaveDirtyPasswordPage'];
            return message;
        }
    };

    PWM_GLOBAL['dirtyPageLeaveFlag'] = true;

    // setup tooltips
    dojo.require("dijit.Tooltip");
    dojo.addOnLoad(function() {
        var strengthTooltip = new dijit.Tooltip({
            connectId: ["strengthBox"],
            label: PWM_STRINGS['Tooltip_PasswordStrength']
        });
        strengthTooltip.setAttribute('style','width: 30em');
    });

    dojo.require("dijit.Dialog");

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