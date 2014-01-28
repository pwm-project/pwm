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

// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validateResponses() {
/*
    require(["dojo/dom-form"], function(domForm){
        var serviceUrl = PWM_GLOBAL['url-setupresponses'] + "?processAction=validateResponses";
        if (PWM_GLOBAL['responseMode']) {
            serviceUrl += "&responseMode=" + PWM_GLOBAL['responseMode'];
        }
        var validationProps = {};
        validationProps['messageWorking'] = PWM_MAIN.showString('Display_CheckingResponses');
        validationProps['serviceURL'] = serviceUrl;
        validationProps['readDataFunction'] = function(){
            return domForm.toObject('setupResponses');
        };
        validationProps['processResultsFunction'] = function(data){
            updateDisplay(data);
        };

        PWM_MAIN.pwmFormValidator(validationProps);
    });
*/
}

function updateDisplay(resultInfo)
{
    if (resultInfo == null) {
        PWM_MAIN.getObject("setotpsecret").disabled = false;
        return;
    }

    var result = resultInfo["message"];

    if (resultInfo["success"] == true) {
        PWM_MAIN.getObject("setotpsecret_button").disabled = false;
        PWM_MAIN.showSuccess(result);
    } else {
        PWM_MAIN.getObject("setotpsecret_button").disabled = true;
        PWM_MAIN.showError(result);
    }
}

function startupOtpSecretPage() {
    var initialPrompt = PWM_MAIN.showString('Display_EnterOneTimePasswordPrompt');
    if (initialPrompt != null && initialPrompt.length > 1) {
        var messageElement = PWM_MAIN.getObject("message");
        if (messageElement.firstChild.nodeValue.length < 2) {
            PWM_MAIN.showInfo(initialPrompt);
        }
    }
}


