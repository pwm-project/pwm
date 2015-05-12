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

var COLOR_BAR_TOP       = 0x8ced3f;
var COLOR_BAR_BOTTOM    = 0xcc0e3e;

var PWM_NEWUSER = PWM_NEWUSER || {};

// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
PWM_NEWUSER.validateNewUserForm=function() {
    var validationProps = new Array();
    validationProps['messageWorking'] = PWM_MAIN.showString('Display_CheckingData');
    validationProps['serviceURL'] = 'NewUser' + "?processAction=validate";
    validationProps['readDataFunction'] = function(){
        return PWM_NEWUSER.makeFormData();
    };
    validationProps['processResultsFunction'] = function(data){
        PWM_NEWUSER.updateDisplay(data);
    };

    PWM_MAIN.pwmFormValidator(validationProps);
};

PWM_NEWUSER.makeFormData=function() {
    var paramData = { };

    var newUserForm = PWM_MAIN.getObject('newUserForm');

    for (var i = 0; i < newUserForm.elements.length; i++ ) {
        var loopElement = newUserForm.elements[i];
        paramData[loopElement.name] = loopElement.value;
    }

    return paramData;
};

PWM_NEWUSER.updateDisplay=function(data) {
    PWM_NEWUSER.markConfirmationCheck(null);
    PWM_NEWUSER.markStrength(null);

    if (data['error']) {
        PWM_MAIN.showError(data['errorMessage']);
        PWM_MAIN.getObject("submitBtn").disabled = true;
    } else {
        var resultInfo = data['data'];
        var message = resultInfo["message"];

        if (resultInfo["passed"] == true) {
            if (resultInfo["match"] == "MATCH") {
                PWM_MAIN.getObject("submitBtn").disabled = false;
                PWM_MAIN.showSuccess(message);
            } else {
                PWM_MAIN.getObject("submitBtn").disabled = true;
                PWM_MAIN.showInfo(message);
            }
        } else {
            PWM_MAIN.getObject("submitBtn").disabled = true;
            PWM_MAIN.showError(message);
        }

        PWM_NEWUSER.markConfirmationCheck(resultInfo["match"]);
        PWM_NEWUSER.markStrength(resultInfo["strength"]);
    }
};

PWM_NEWUSER.markConfirmationCheck=function(matchStatus) {
    if (PWM_MAIN.getObject("confirmCheckMark") || PWM_MAIN.getObject("confirmCrossMark")) {
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

PWM_NEWUSER.markStrength=function(strength) { //strength meter
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
    if (barObject != null && strength != null) {
        barObject.style.width = strength + '%';
        barObject.style.backgroundColor = '#' + gradColor;
    }

    var labelObject = PWM_MAIN.getObject("strengthLabel");
    if (labelObject != null) {
        labelObject.innerHTML = strengthLabel == null ? "" : strengthLabel;
    }
};


PWM_NEWUSER.refreshCreateStatus=function(refreshInterval) {
    require(["dojo","dijit/registry"],function(dojo,registry){
        var displayStringsUrl = "NewUser?processAction=checkProgress&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
        var completedUrl = "NewUser?processAction=complete&pwmFormID=" + PWM_GLOBAL['pwmFormID'];
        dojo.xhrGet({
            url: displayStringsUrl,
            preventCache: true,
            handleAs: 'json',
            timeout: PWM_GLOBAL['client.ajaxTypingTimeout'],
            headers: { "Accept": "application/json" },
            load: function(data) {
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

                if (data['data']['complete'] == true) {
                    PWM_MAIN.goto(completedUrl,{delay:1000})
                } else {
                    setTimeout(function(){
                        PWM_NEWUSER.refreshCreateStatus(refreshInterval);
                    },refreshInterval);
                }
            },
            error: function(error) {
                console.log('unable to read password change status: ' + error);
                setTimeout(function(){
                    PWM_NEWUSER.refreshCreateStatus(refreshInterval);
                },refreshInterval);
            }
        });
    });
}
