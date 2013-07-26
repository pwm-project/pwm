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

var COLOR_BAR_TOP       = 0x8ced3f;
var COLOR_BAR_BOTTOM    = 0xcc0e3e;

// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validateNewUserForm() {
    var validationProps = new Array();
    validationProps['messageWorking'] = showString('Display_CheckingData');
    validationProps['serviceURL'] = 'NewUser' + "?processAction=validate";
    validationProps['readDataFunction'] = function(){
        return makeFormData();
    };
    validationProps['processResultsFunction'] = function(data){
        updateDisplay(data);
    };

    pwmFormValidator(validationProps);
}

function makeFormData() {
    var paramData = { };

    var newUserForm = getObject('newUserForm');

    for (var i = 0; i < newUserForm.elements.length; i++ ) {
        var loopElement = newUserForm.elements[i];
        paramData[loopElement.name] = loopElement.value;
    }

    return paramData;
}

function updateDisplay(data)
{
    markConfirmationCheck(null);
    markStrength(null);

    if (data['error']) {
        showError(data['errorMessage']);
    } else {
        var resultInfo = data['data'];
        var message = resultInfo["message"];

        if (resultInfo["passed"] == true) {
            if (resultInfo["match"] == "MATCH") {
                getObject("submitBtn").disabled = false;
                showSuccess(message);
            } else {
                getObject("submitBtn").disabled = true;
                showInfo(message);
            }
        } else {
            getObject("submitBtn").disabled = true;
            showError(message);
        }

        markConfirmationCheck(resultInfo["match"]);
        markStrength(resultInfo["strength"]);
    }
}

function markConfirmationCheck(matchStatus) {
    if (getObject("confirmCheckMark") || getObject("confirmCrossMark")) {
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
        strengthLabel = showString('Display_PasswordStrengthHigh');
    } else if (strength > 45) {
        strengthLabel = showString('Display_PasswordStrengthMedium');
    } else {
        strengthLabel = showString('Display_PasswordStrengthLow');
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

