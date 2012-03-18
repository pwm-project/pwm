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

var validationCache = { };
var validationInProgress = false;


// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validateNewUserForm() {
    if (validationInProgress) {
        return;
    }

    var parameterData = makeValidationKey();
    {
        var cachedResult = validationCache[parameterData.cacheKey];
        if (cachedResult != null) {
            updateDisplay(cachedResult);
            return;
        }
    }

    setTimeout(function(){ if (validationInProgress) { showInfo(PWM_STRINGS['Display_CheckingData']); }},1000);

    validationInProgress = true;
    dojo.xhrPost({
        url: 'NewUser' + "?processAction=validate&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
        postData:  dojo.toJson(parameterData),
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        error: function(errorObj) {
            validationInProgress = false;
            showSuccess(PWM_STRINGS['Display_CommunicationError']);
            console.log('error: ' + errorObj);
        },
        load: function(data){
            setTimeout(function(){
                validationCache[parameterData.cacheKey] = data;
                validationInProgress = false;
                validateNewUserForm();
            },500);
        }
    });
}

function makeValidationKey() {
    var cacheKeyValue = "";
    var paramData = { };

    var newUserForm = getObject('newUserForm');

    for (var i = 0; i < newUserForm.elements.length; i++ ) {
        var loopElement = newUserForm.elements[i];
        paramData[loopElement.name] = loopElement.value;
        cacheKeyValue = cacheKeyValue + (loopElement.name + '=' + loopElement.value + '&')
    }

    paramData['cacheKey'] = cacheKeyValue;

    return paramData;
}

function updateDisplay(resultInfo)
{
    var result = resultInfo["message"];

    if (resultInfo["success"] == "true") {
        getObject("submitBtn").disabled = false;
        showSuccess(result);
    } else {
        getObject("submitBtn").disabled = true;
        showError(result);
    }
}


