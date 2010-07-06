/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

    var passwordData = makeValidationKey();
    {
        var cachedResult = validationCache[passwordData.cacheKey];
        if (cachedResult != null) {
            updateDisplay(cachedResult);
            return;
        }
    }

    setTimeout(function(){
        if (validationInProgress) {
            showWorking();
            //validatePasswords();
        }
    },200);
    validationInProgress = true;
    dojo.xhrPost({
        url: getObject("Js_ChangePasswordURL").value + "?processAction=validate&pwmFormID=" + getObject('pwmFormID').value,
        postData:  dojo.toJson(passwordData),
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        error: function(errorObj) {
            validationInProgress = false;
            clearError(getObject("Js_Display_CommunicationError").value);
            markStrength(0);
            console.log('error: ' + errorObj);
        },
        load: function(data){
            validationInProgress = false;
            updateDisplay(data);
            validationCache[passwordData.cacheKey] = data;
            if (passwordData.cacheKey != makeValidationKey().cacheKey) {
                setTimeout(function() {validatePasswords();}, 1);
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
        clearError('\u00A0');
        markStrength(0);
        return;
    }

    var message = resultInfo["message"];

    if (resultInfo["version"] != "2") {
        showError("[ unexpected version string from server ]");
        return;
    }

    if (resultInfo["passed"] == "true") {
        if (resultInfo["match"] == "MATCH") {
            showSuccess(message);
        } else {
            showConfirm(message);
        }
    } else {
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
    if (getObject("password1").value.length > 0) {
        getObject("strengthBox").style.visibility = 'visible';
    } else {
        getObject("strengthBox").style.visibility = 'hidden';
    }

    var strengthLabel = "";
    var barColor = "";

    if (strength > 70) {
        strengthLabel = getObject("Js_Strength_High").value;
    } else if (strength > 45) {
        strengthLabel = getObject("Js_Strength_Medium").value;
    } else {
        strengthLabel = getObject("Js_Strength_Low").value;
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

function clearError(message)
{
    getObject("password_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = message;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#FFFFFF' }
    }).play();
}

function showWorking()
{
    getObject("password_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = getObject("Js_Display_CheckingPassword").value;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#FFCD59' }
    }).play();
}

function showError(errorMsg)
{
    getObject("password_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = errorMsg;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#FFCD59' }
    }).play();
}

function showConfirm(successMsg)
{
    getObject("password_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = successMsg;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#DDDDDD' }
    }).play();
}

function showSuccess(successMsg)
{
    getObject("password_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = successMsg;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#EFEFEF' }
    }).play();
}

function copyToPasswordFields(text)  // used to copy auto-generated passwords to password field
{
    if (text.length > 255) {
        text = text.substring(0,255);
    }
    text = trimString(text);
    getObject("password1").value = text;
    validatePasswords();
    alert(getObject("Js_Display_PasswordChangedTo").value + "\n\n" + text);
}

function toggleMaskPasswords()
{
    if (passwordsMasked) {
        getObject("hide_button").value = " " + getObject("Js_Button_Hide").value + " ";
        changeInputTypeField(getObject("password1"),"text");
        changeInputTypeField(getObject("password2"),"text");
    } else {
        getObject("hide_button").value = getObject("Js_Button_Show").value;
        changeInputTypeField(getObject("password1"),"password");
        changeInputTypeField(getObject("password2"),"password");
    }
    passwordsMasked = !passwordsMasked;

}

function handleChangePasswordSubmit()
{
    getObject("error_msg").firstChild.nodeValue = getObject("Js_Display_PleaseWait").value;
    getObject("error_msg").className = "notice";
    dirtyPageLeaveFlag = false;
}

function fetchNewRandom()
{
    dojo.xhrPost({
        url: getObject("Js_ChangePasswordURL").value + "?processAction=getrandom&pwmFormID=" + getObject('pwmFormID').value,
        postData: "",
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        error: function(errorObj) {
            showError('[unable to fetch new random password');
        },
        load: function(data){
            handleRandomResponse(data);
        }
    });
}

function handleRandomResponse(resultInfo)
{
    if (resultInfo["version"] != "1") {
        showError("[ unexpected randomgen version string from server ]");
        return;
    }

    var password = resultInfo["password"];

    copyToPasswordFields(password);
    getObject("password2").focus();
}

function clearForm() {
    getObject("password1").value = "";
    getObject("password2").value = "";
    if (getObject("currentPassword") != null) getObject("currentPassword").value = "";
    clearError('\u00A0'); //&nbsp;
    markConfirmationCheck("EMPTY");
    markStrength(0);
    setInputFocus();
}

function startupChangePasswordPage()
{
    /* enable the hide button only if the toggle works */
    try {
        toggleMaskPasswords();
        toggleMaskPasswords();
        changeInputTypeField(getObject("hide_button"),"button");
    } catch (e) {
    }

    /* check if browser is ie6 or less. */
    var isIe6orLess = false;
    if (/MSIE (\d+\.\d+);/.test(navigator.userAgent)){ //test for MSIE x.x;
        var ieversion=new Number(RegExp.$1) // capture x.x portion and store as a number
        if (ieversion<=6) {
            isIe6orLess = false;
        }
    }

    // show the auto generate password panel
    var autoGenPasswordElement = getObject("autoGeneratePassword");
    if (autoGenPasswordElement != null) {
        autoGenPasswordElement.style.visibility = 'visible';
    }

    // show the error panel
    var autoGenPasswordElement = getObject("error_msg");
    if (autoGenPasswordElement != null) {
        autoGenPasswordElement.style.visibility = 'visible';
    }

    // add a handler so if the user leaves the page except by submitting the form, then a warning/confirm is shown
    window.onbeforeunload = function() {
        if (dirtyPageLeaveFlag) {
            var message = getObject("Js_LeaveDirtyPasswordPage").value;
            return message;
        }
    };

    dirtyPageLeaveFlag = true;

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