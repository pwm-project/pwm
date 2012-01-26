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

var responsesHidden = true;
var PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
var PARAM_QUESTION_PREFIX = "PwmResponse_Q_";

var validationCache = { };
var validationInProgress = false;

var simpleRandomSelectElements = [];

// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
function validateResponses() {
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

    setTimeout(function(){ if (validationInProgress) { showInfo(PWM_STRINGS['Display_CheckingResponses']); }},500);

    validationInProgress = true;
    dojo.xhrPost({
        url: PWM_STRINGS['url-setupresponses'] + "?processAction=validateResponses&pwmFormID=" + PWM_GLOBAL['pwmFormID'],
        postData:  dojo.toJson(parameterData),
        contentType: "application/json;charset=utf-8",
        dataType: "json",
        handleAs: "json",
        error: function(errorObj) {
            validationInProgress = false;
            getObject("setresponses_button").disabled = false;
            showSuccess(PWM_STRINGS['Display_CommunicationError']);
            console.log('error: ' + errorObj);
        },
        load: function(data){
            validationInProgress = false;
            validationCache[parameterData.cacheKey] = data;
            if (parameterData.cacheKey != makeValidationKey().cacheKey) {
                setTimeout(function() {validateResponses();}, 1);
            } else {
                updateDisplay(data);
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
        getObject("setresponses_button").disabled = false;
        showSuccess(result);
    } else {
        getObject("setresponses_button").disabled = true;
        showError(result);
    }
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
        getObject("hide_responses_button").value = PWM_STRINGS['Button_Hide_Responses'];
    } else {
        getObject("hide_responses_button").value = PWM_STRINGS['Button_Show_Responses'];
    }

    responsesHidden = !responsesHidden;
}


function makeSelectOptionsDistinct() {
    var allPossibleTexts = [];
    var currentlySelectedTexts = [];

    // build list of all possible texts, and currently selected values.
    for (var i1 in simpleRandomSelectElements) {
        var current = simpleRandomSelectElements[i1];
        var currentSelected = current.selectedIndex;
        currentlySelectedTexts[current.id] = current.options[currentSelected].text;

        for (var optionIterator = 0; optionIterator < current.options.length; optionIterator++) {
            var loopText = current.options[optionIterator].text;
            var usedBefore = -1 != dojo.indexOf(allPossibleTexts,loopText);
            if (!usedBefore) {
                allPossibleTexts.push(loopText);
            }
        }
    }

    // ensure no two select lists have another's currently selected value
    var usedTexts = [];
    for (var loopIter in currentlySelectedTexts) {
        var text = currentlySelectedTexts[loopIter];
        if (-1 != dojo.indexOf(usedTexts,text)) {
            for (var i in allPossibleTexts) {
                var loopT = allPossibleTexts[i];
                if (-1 == dojo.indexOf(usedTexts,loopT)) {
                    currentlySelectedTexts[loopIter] = loopT;
                    text = loopT;
                    break;
                }
            }
        }
        usedTexts.push(text);
    }

    // rewrite the options for each of the select lists
    for (var iterID in currentlySelectedTexts) {
        var selectElement = getObject(iterID);
        var selectedText = currentlySelectedTexts[iterID];
        selectElement.options.length = 0;
        var nextOptionCounter = 0;
        for (var optionIter = 0; optionIter < allPossibleTexts.length; optionIter++) {
            var optionText = allPossibleTexts[optionIter];
            var hasBeenUsed = -1 != dojo.indexOf(usedTexts,optionText);
            var isSelected = optionText == selectedText;
            if (isSelected || !hasBeenUsed) {
                selectElement.options[nextOptionCounter] = new Option(optionText,optionText,isSelected,isSelected);
                nextOptionCounter++;
            }
        }
        selectedText = selectElement.options[selectElement.selectedIndex].text;
    }
}

function startupResponsesPage(initialPrompt)
{
    if (PWM_GLOBAL['setting-showHidePasswordFields']) {
        try {
            toggleHideResponses();
            toggleHideResponses();
            changeInputTypeField(getObject("hide_responses_button"),"button");
        } catch (e) {
            //alert("can't show hide button: " + e)
        }
    }

    if (initialPrompt != null && initialPrompt.length > 1) {
        var messageElement = getObject("message");
        if (messageElement.firstChild.nodeValue.length < 2) {
            showInfo(initialPrompt);
        }
    }
}


