/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

"use strict";

var PWM_RESPONSES = PWM_RESPONSES || {};
var PWM_VAR = PWM_VAR || {};
var require;

PWM_VAR['simpleRandomOptions'] = [];


// takes response values in the fields, sends an http request to the servlet
// and then parses (and displays) the response from the servlet.
PWM_RESPONSES.validateResponses=function() {
    var serviceUrl = PWM_MAIN.addParamToUrl(window.location.href,"processAction","validateResponses");
    if (PWM_GLOBAL['responseMode']) {
        serviceUrl += "&responseMode=" + PWM_GLOBAL['responseMode'];
    }
    var validationProps = {};
    validationProps['messageWorking'] = PWM_MAIN.showString('Display_CheckingResponses');
    validationProps['serviceURL'] = serviceUrl;
    validationProps['readDataFunction'] = function(){
        return PWM_MAIN.JSLibrary.formToValueMap(PWM_MAIN.getObject('form-setupResponses'));
    };
    validationProps['processResultsFunction'] = function(data){
        if (data) {
            PWM_RESPONSES.updateDisplay(data['data']);
        } else {
            console.log('did not receive valid response for validation check from server');
            PWM_MAIN.getObject("button-setResponses").disabled = false;
        }
    };

    PWM_MAIN.pwmFormValidator(validationProps);
};

PWM_RESPONSES.updateDisplay=function(resultInfo) {
    if (!resultInfo) {
        PWM_MAIN.getObject("button-setResponses").disabled = false;
        return;
    }

    var result = resultInfo["message"];

    if (resultInfo["success"] === true) {
        PWM_MAIN.getObject("button-setResponses").disabled = false;
        PWM_MAIN.showSuccess(result);
    } else {
        PWM_MAIN.getObject("button-setResponses").disabled = true;
        PWM_MAIN.showError(result);
    }
};

PWM_RESPONSES.makeSelectOptionsDistinct=function() {
    var startTime = (new Date()).getTime();
    console.log('entering makeSelectOptionsDistinct()');

    // all possible random questions (populated by the jsp)
    var allPossibleTexts = PWM_VAR['simpleRandomOptions'];

    // string that is used at the top of unconfigured select list
    var initialChoiceText = PWM_MAIN.showString('Display_SelectionIndicator');

    // the HTML select elements (populated by the jsp)
    var simpleRandomSelectElements = PWM_VAR['simpleRandomSelectElements'];

    // texts that are in use
    var currentlySelectedTexts = [];

    PWM_MAIN.JSLibrary.forEachInArray(simpleRandomSelectElements,function(questionID){
        var selectedElement = PWM_MAIN.getObject(questionID);
        var selectedIndex = selectedElement.selectedIndex;
        var selectedValue = selectedElement.options[selectedIndex].value;
        if ('UNSELECTED' !== selectedValue) {
            currentlySelectedTexts.push(selectedValue);
        }
    });

    // repopulate the select elements
    PWM_MAIN.JSLibrary.forEachInArray(simpleRandomSelectElements,function(questionID){
        var selectedElement = PWM_MAIN.getObject(questionID);
        var selectedIndex = selectedElement.selectedIndex;
        var selectedValue = selectedElement.options[selectedIndex].value;
        var responseID = selectedElement.getAttribute('data-response-id');
        selectedElement.innerHTML = '';
        if (selectedValue === 'UNSELECTED') {
            var unselectedOption = document.createElement('option');
            unselectedOption.value = 'UNSELECTED';
            unselectedOption.innerHTML = '&nbsp;&mdash;&nbsp;' + initialChoiceText + '&nbsp;&mdash;&nbsp;';
            unselectedOption.selected = true;
            selectedElement.appendChild(unselectedOption);
        }

        PWM_MAIN.JSLibrary.forEachInArray(allPossibleTexts,function(loopText){
            var optionElement = document.createElement('option');
            optionElement.value = loopText;
            optionElement.innerHTML = loopText;

            if (loopText === selectedValue || !PWM_MAIN.JSLibrary.arrayContains(currentlySelectedTexts,loopText)) {
                if (loopText === selectedValue) {
                    optionElement.selected = true;
                }
                selectedElement.appendChild(optionElement);
            }
        });
    });

    console.log('exiting makeSelectOptionsDistinct(), duration:' + (((new Date()).getTime()) - startTime) + "ms");
};

PWM_RESPONSES.startupResponsesPage=function() {
    PWM_MAIN.doIfQueryHasResults('#pwm-setupResponsesDiv',function(){
        var initialPrompt = PWM_MAIN.showString('Display_ResponsesPrompt');
        if (initialPrompt !== null && initialPrompt.length > 1) {
            var messageElement = PWM_MAIN.getObject("message");
            if (messageElement.firstChild.nodeValue.length < 2) {
                PWM_MAIN.showInfo(initialPrompt);
            }
        }
        PWM_MAIN.doQuery('input.response',function(result){
            PWM_MAIN.addEventHandler(result,'input',function(){
                console.log('form-setupResponses input event handler');
                PWM_RESPONSES.validateResponses();
            });
        });
        PWM_MAIN.getObject("button-setResponses").disabled = true;
        PWM_RESPONSES.initSimpleRandomElements();
    });
};


PWM_RESPONSES.initSimpleRandomElements = function() {
    console.log('entering initSimpleRandomElements');
    PWM_VAR['simpleRandomSelectElements'] = [];
    PWM_VAR['focusInValues'] = {};

    var updateResponseInputField = function(element) {
        var responseID = element.getAttribute('data-response-id');
        if (element.value === 'UNSELECTED') {
            PWM_MAIN.getObject(responseID).disabled = true;
            PWM_MAIN.getObject(responseID).readonly = true;
        } else {
            PWM_MAIN.getObject(responseID).disabled = false;
            PWM_MAIN.getObject(responseID).readonly = false;
        }

    };

    PWM_MAIN.doQuery('.simpleModeResponseSelection',function(element){
        PWM_VAR['simpleRandomSelectElements'].push(element.id);
        updateResponseInputField(element);
        PWM_MAIN.addEventHandler(element.id,"keypress,keyup,keydown",function(){
            updateResponseInputField(element);
        });
        PWM_MAIN.addEventHandler(element.id,"focusin",function(){
            PWM_VAR['focusInValues'][element.id] = element.selectedIndex;
        });
        PWM_MAIN.addEventHandler(element.id,"click,blur",function(){
            if (PWM_VAR['focusInValues'][element.id] !== element.selectedIndex) {
                var selectedIndex = element.selectedIndex;
                var selectedValue = element.options[selectedIndex].value;
                if (selectedValue !== 'UNSELECTED') {
                    var responseID = element.getAttribute('data-response-id');
                    var responseElement = PWM_MAIN.getObject(responseID);
                    responseElement.value = '';
                    responseElement.disabled = false;
                    responseElement.readonly = false;
                    PWM_RESPONSES.makeSelectOptionsDistinct();

                    require(["dojo/has"], function(has){
                        // ios safari seems to behave poorly when moving the focus away from an open drop down list
                        if(!has("ios") && !has("safari")){
                            responseElement.focus();
                        }
                    });

                }
            }
        });
    });
    PWM_RESPONSES.makeSelectOptionsDistinct();
};
