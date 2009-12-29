/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

var SETTING_SHOW_CHECKING_TIMEOUT = 400;    // show "please wait, checking" if response not received in this time (ms)

var passwordsMasked = true;
var previousP1 = "";

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validatePasswords()
{
    if (getObject("password1").value.length <= 0 && getObject("password2").value.length <= 0) {
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

    if (resultInfo["version"] != "1") {
        showError("[ unexpected version string from server ]");
        return;
    }

    if (resultInfo["passed"] == "true") {
        if (resultInfo["match"] == "MATCH") {
            showSuccess(message);
        } else {
            showConfirm(message);
        }
        markStrength(resultInfo["strength"]);
    } else {
        showError(message);
        markStrength(0);
    }
}

function markStrength(strength) { //strength meter
    if (strength == 9) {
        strength = 100;
    } else {
        strength = strength * 10;
    }

    var objectBar = getObject("graph2");
    if (objectBar != null) {
        objectBar.innerHTML = '<li class="bar" style="height: ' + strength + '%;"><!--comment--></li>';
    }
}

function clearError(message)
{
    getObject("password_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = message;
    getObject("error_msg").className = "msg-info";
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

function toggleHide()
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
    markStrength(0);
}

function startupPage()
{
    /************* enable the hide button only if the toggle works */
    try {
        toggleHide();
        toggleHide();
        changeInputTypeField(getObject("hide_button"),"button");
    } catch (e) {
    }

    /************* show progressbar if browser is capable */
    /* if browser is javascript capable (must be to get here, then show strengthBar */
    var showStrengthMeterBox = true;

    /* if browser is ie 6 or less, then don't show strengthBar, ie6 cant handle the css. */
    if (/MSIE (\d+\.\d+);/.test(navigator.userAgent)){ //test for MSIE x.x;
        var ieversion=new Number(RegExp.$1) // capture x.x portion and store as a number
        if (ieversion<=6) {
            showStrengthMeterBox = false;
        }
    }

    if (showStrengthMeterBox) {
        var strengthMeterBox = getObject('strengthMeterBox');
        try {
            strengthMeterBox.style.display = "inherit";
        } catch (e) {
        }
    }

    validatePasswords();
}

var validatorAjaxState = new AjaxRequestorState(getObject("Js_ChangePasswordURL").value, handleValidationResponse);
var randomAjaxState = new AjaxRequestorState(getObject("Js_ChangePasswordURL").value, handleRandomResponse);