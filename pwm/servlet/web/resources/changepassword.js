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

var SETTING_SHOW_CHECKING_TIMEOUT = 1000;    // show "please wait, checking" if response not received in this time (ms)

var passwordsMasked = true;
var previousP1 = "";

var COLOR_BAR_TOP       = 0x8ced3f;
var COLOR_BAR_BOTTOM    = 0xcc0e3e;


// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validatePasswords()
{
    if (getObject("password1").value.length <= 0 && getObject("password2").value.length <= 0) {
        clearForm();
        return;
    }

    if (previousP1 != getObject("password1").value) {
        // if p1 is changing, then clear out p2.
        getObject("password2").value = "";
        previousP1 = getObject("password1").value;
    }

    var key = makeValidationKey();

    //if the response isn't received quickly, this timeout will cause a "working" message to be displayed
    setTimeout( function() {
        if (validatorAjaxState.busy) {
            showWorking();
        }
    }, SETTING_SHOW_CHECKING_TIMEOUT);

    doAjaxRequest(validatorAjaxState, key);
}

function makeValidationKey() {
    var p1enc = urlEncode(getObject("password1").value);
    var p2enc = urlEncode(getObject("password2").value);
    return "processAction=validate&password1=" + p1enc + "&password2=" + p2enc;
}

function handleValidationResponse(key, resultString)
{
    if (resultString != null && resultString.length > 0) {
        validatorAjaxState.cache[key] = resultString;
        if (key != makeValidationKey()) {
            setTimeout(function() {validatePasswords();}, 1);
        } else {
            updateDisplay(resultString);
        }
        return;
    }

    clearError(getObject("Js_Display_CommunicationError").value);
    markStrength(0);
}

function updateDisplay(resultString)
{
    try {
        var resultInfo = JSON.parse(resultString);
    } catch (Exception) {
        clearError(getObject("Js_Display_CommunicationError").value);
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


    markConfirmationMark(resultInfo["match"]);
    markStrength(resultInfo["strength"]);
}

function markConfirmationMark(matchStatus) {
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
    getObject("error_msg").className = "msg-success";
}

function showWorking()
{
    getObject("password_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = getObject("Js_Display_CheckingPassword").value;
    getObject("error_msg").className = "msg-error";
}

function showError(errorMsg)
{
    getObject("password_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = errorMsg;
    getObject("error_msg").className = "msg-error";
}

function showConfirm(successMsg)
{
    getObject("password_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = successMsg;
    getObject("error_msg").className = "msg-success";
}

function showSuccess(successMsg)
{
    getObject("password_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = successMsg;
    getObject("error_msg").className = "msg-success";
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
    var key = "processAction=getrandom";
    doAjaxRequest(randomAjaxState,key);
}

function handleRandomResponse(key, resultString)
{
    try {
        var resultInfo = JSON.parse(resultString);
    } catch (Exception) {
        clearError(getObject("Js_Display_CommunicationError").value);
        return;
    }

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
    clearError('\u00A0'); //&nbsp;
    markConfirmationMark("EMPTY");
    markStrength(0);
    setTimeout( function() { getObject("password1").focus(); }, 10); // hack for IE
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

    validatePasswords();

    // add a handler so if the user leaves the page except by submitting the form, then a warning/confirm is shown
    window.onbeforeunload = function() {
        if (dirtyPageLeaveFlag) {
            var message = getObject("Js_LeaveDirtyPasswordPage").value;
            return message;
        }
    };

    dirtyPageLeaveFlag = true;
}

var validatorAjaxState = new AjaxRequestorState(getObject("Js_ChangePasswordURL").value, handleValidationResponse);
var randomAjaxState = new AjaxRequestorState(getObject("Js_ChangePasswordURL").value, handleRandomResponse);