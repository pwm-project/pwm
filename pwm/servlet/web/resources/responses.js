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

var responsesHidden = false;
var PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
var PARAM_QUESTION_PREFIX = "PwmResponse_Q_";

var validationCache = { };
var validationInProgress = false;

// takes password values in the password fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validateResponses() {
    var parameterData = makeValidationKey();
    {
        var cachedResult = validationCache[parameterData.cacheKey];
        if (cachedResult != null) {
            updateDisplay(cachedResult);
            return;
        }
    }

    setTimeout(function(){
        if (validationInProgress) {
            showWorking();
        }
    },200);

    validationInProgress = true;
    dojo.xhrPost({
        url: getObject("Js_SetupResponsesURL").value + "?processAction=validateResponses&pwmFormID=" + getObject('pwmFormID').value,
        postData:  dojo.toJson(parameterData),
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        error: function(errorObj) {
            validationInProgress = false;
            clearError(getObject("Js_Display_CommunicationError").value);
            console.log('error: ' + errorObj);
        },
        load: function(data){
            validationInProgress = false;
            updateDisplay(data);
            validationCache[parameterData.cacheKey] = data;
            if (parameterData.cacheKey != makeValidationKey().cacheKey) {
                setTimeout(function() {validatePasswords();}, 1);
            }
        }
    });
}

function makeValidationKey() {
    var cacheKeyValue = "";
    var paramData = { };

    for (var j = 0; j < document.forms.length; j++) {
        for (var i = 0; i < document.forms[j].length; i++) {
            var current = document.forms[j].elements[i];
            if (current.name.substring(0,PARAM_QUESTION_PREFIX.length) == PARAM_QUESTION_PREFIX || current.name.substring(0,PARAM_RESPONSE_PREFIX.length) == PARAM_RESPONSE_PREFIX) {
                cacheKeyValue = cacheKeyValue + (current.name + '=' + current.value) + '&';
                paramData[current.name] = current.value;
            }
        }
    }

    paramData['cacheKey'] = cacheKeyValue;

    return paramData;
}

function updateDisplay(resultInfo)
{

    var result = resultInfo["message"];

    if (resultInfo["success"] == "true") {
        showSuccess(result);
    } else {
        showError(result);
    }
}

function clearError(message)
{
    getObject("setresponses_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = message;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#FFFFFF' }
    }).play();
}

function showWorking()
{
    getObject("setresponses_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = getObject("Js_Display_CheckingResponses").value;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#FFCD59' }
    }).play();
}

function showError(errorMsg)
{
    getObject("setresponses_button").disabled = true;
    getObject("error_msg").firstChild.nodeValue = errorMsg;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#FFCD59' }
    }).play();
}

function showSuccess(successMsg)
{
    getObject("setresponses_button").disabled = false;
    getObject("error_msg").firstChild.nodeValue = successMsg;
    dojo.animateProperty({
        node:"error_msg",
        duration: 500,
        properties: { backgroundColor:'#EFEFEF' }
    }).play();
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

function startupResponsesPage(fieldsAreHidden)
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


