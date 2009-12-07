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

var responsesHidden = false;
var PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
var PARAM_QUESTION_PREFIX = "PwmResponse_Q_";

var SETTING_SHOW_CHECKING_TIMEOUT = 1300;    // show "please wait, checking" if response not received in this time (ms)

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validateResponses() {
    //if the response isnt received quickly, this timeout will cause a "working" message to be displayed
    setTimeout( function() {
        if (validatorAjaxState.busy) {
            showWorking();
        }
    }, SETTING_SHOW_CHECKING_TIMEOUT);

    var key = makeValidationKey();
    doAjaxRequest(validatorAjaxState, key);
}

function makeValidationKey() {
    var output = "";

    for (var j = 0; j < document.forms.length; j++) {
        for (var i = 0; i < document.forms[j].length; i++) {
            var current = document.forms[j].elements[i];
            if (current.name.substring(0,PARAM_QUESTION_PREFIX.length) == PARAM_QUESTION_PREFIX || current.name.substring(0,PARAM_RESPONSE_PREFIX.length) == PARAM_RESPONSE_PREFIX) {
                output = output + '&' + (current.name + '=' + urlEncode(current.value));
            }
        }
    }

    return "processAction=validateResponses" + output;
}

function handleValidationResponse(key, resultString)
{
    if (resultString != null && resultString.length > 0) {
        updateDisplay(resultString);
        validatorAjaxState.cache[key] = resultString;
        if (key != makeValidationKey()) {
            setTimeout(function() {validateResponses();}, 100);
        }
    } else {
        clearError(getObject("Js_Display_CommunicationError").value);
    }
}

function updateDisplay(resultString)
{
    try {
        var resultInfo = JSON.parse(resultString);
    } catch (Exception) {
        clearError(getObject("Js_Display_CommunicationError").value);
        return;
    }
    
    var result = resultInfo["message"];

    if (resultInfo["success"] == "true") {
        showSuccess(result);
    } else {
        showError(result);
    }
}

function showError(errorMsg)
{
    getObject("setresponses_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = errorMsg;
    getObject("error_msg").className = "msg-error";
}

function showSuccess(successMsg)
{
    getObject("setresponses_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = successMsg;
    getObject("error_msg").className = "msg-success";
}

function showWorking()
{
    getObject("setresponses_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = getObject("Js_Display_CheckingResponses").value;
    getObject("error_msg").className = "msg-error";
}

function clearError(message)
{
    getObject("setresponses_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = message;
    getObject("error_msg").className = "msg-info";
}

function toggleHideResponses()
{
    for (var j = 0; j < document.forms.length; j++) {
        for (var i = 0; i < document.forms[j].length; i++) {
            var current = document.forms[j].elements[i];
            if (current.type == "text" || current.type == "password") {
                if (responsesHidden) {
                    changeInputTypeField(current,"text");
                } else {
                    changeInputTypeField(current,"password");
                }
            }
        }
    }

    if (responsesHidden) {
        getObject("hide_responses_button").value = " " + getObject("Js_Button_Hide_Responses").value + " ";
    } else {
        getObject("hide_responses_button").value = getObject("Js_Button_Show_Responses").value;
    }

    responsesHidden = !responsesHidden;
}

function startupPage(fieldsAreHidden)
{
    responsesHidden = fieldsAreHidden;
    try {
        toggleHideResponses();
        toggleHideResponses();
        changeInputTypeField(getObject("hide_responses_button"),"button");
    } catch (e) {
        //alert("can't show hide button: " + e)
    }
}

var returnURLobject = getObject("Js_SetupResponsesURL");
if (returnURLobject != null) {
    var validatorAjaxState = new AjaxRequestorState(returnURLobject.value,handleValidationResponse,makeValidationKey);
}


